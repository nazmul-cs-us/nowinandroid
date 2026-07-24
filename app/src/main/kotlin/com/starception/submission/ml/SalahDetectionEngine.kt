package com.starception.submission.ml

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TFLite inference engine for salah posture detection.
 *
 * Maintains a circular buffer of sensor windows, extracts features,
 * normalizes using training statistics, and runs the 1D CNN model.
 *
 * Usage:
 *   val engine = SalahDetectionEngine(context)
 *   // For each 100ms sensor window:
 *   val result = engine.addSampleAndClassify(sample)
 *   if (result != null) {
 *       // result.posture is the detected posture
 *       // result.confidence is the softmax probability
 *   }
 *   engine.close()
 */
class SalahDetectionEngine(context: Context) : Closeable {

    companion object {
        private const val TAG = "SalahDetectionEngine"
        private const val MODEL_FILE = "salah_detector.tflite"
        private const val NORM_PARAMS_FILE = "salah_norm_params.json"

        // Model version - increment when feature vector changes (e.g., feature 24-25 fix)
        const val MODEL_VERSION = 2
        const val EXPECTED_FEATURES = 30

        // Per-posture confidence thresholds: lowered to improve recall.
        // The sequence validator provides additional filtering, so thresholds here
        // should favor passing detections through rather than suppressing them.
        private val POSTURE_CONFIDENCE_THRESHOLDS = mapOf(
            SalahPosture.QIYAM to 0.35f,          // Standing is distinctive (upright orientation)
            SalahPosture.RUKU to 0.35f,            // Bowing is distinctive (forward lean)
            SalahPosture.GOING_TO_SUJUD to 0.40f,  // Brief transition
            SalahPosture.SUJUD to 0.35f,           // Prostration is very distinctive (inverted)
            SalahPosture.JALSA to 0.40f,           // Sitting between sujuds
            SalahPosture.TASHAHHUD to 0.40f,       // Final sitting (similar to JALSA)
            SalahPosture.QIYAM_RISING to 0.40f     // Brief transition
        )
        private const val DEFAULT_CONFIDENCE = 0.40f

        // EMA smoothing factor: higher = more responsive, lower = smoother
        // Increased from 0.3 to 0.6 so new detections carry more weight than history
        private const val EMA_ALPHA = 0.6f

    }

    data class ClassificationResult(
        val posture: SalahPosture,
        val confidence: Float,
        val allProbabilities: FloatArray,
        val isPartialSequence: Boolean = false  // true when using < sequenceLength windows
    )

    private val interpreter: Interpreter
    private val featureMean: FloatArray
    private val featureStd: FloatArray
    private val sequenceLength: Int
    private val featuresPerWindow: Int

    // Circular buffer of feature vectors
    private val featureBuffer: Array<FloatArray>
    private var bufferIndex = 0
    private var bufferFilled = 0

    // EMA smoothed confidence scores (one per posture class)
    private val smoothedConfidences = FloatArray(SalahPosture.classificationLabels.size)

    init {
        // Load normalization parameters
        val normJson = context.assets.open(NORM_PARAMS_FILE).bufferedReader().readText()
        val params = JSONObject(normJson)

        val meanArray = params.getJSONArray("mean")
        val stdArray = params.getJSONArray("std")
        sequenceLength = params.getInt("sequence_length")
        featuresPerWindow = params.getInt("features_per_window")

        // Treat the model, normalization parameters, and Kotlin feature extractor as
        // one versioned artifact. A warning is unsafe here: a mismatched order or shape
        // can still produce plausible-looking but meaningless classifications.
        val modelVersion = params.optInt("model_version", 1)
        require(modelVersion == MODEL_VERSION) {
            "Model version $modelVersion does not match code version $MODEL_VERSION"
        }
        require(sequenceLength > 0) { "sequence_length must be positive" }
        require(featuresPerWindow == EXPECTED_FEATURES) {
            "Model expects $featuresPerWindow features; code extracts $EXPECTED_FEATURES"
        }
        require(meanArray.length() == featuresPerWindow && stdArray.length() == featuresPerWindow) {
            "Normalization arrays must each contain $featuresPerWindow values"
        }
        val modelLabels = params.getJSONArray("posture_labels").let { labels ->
            List(labels.length()) { labels.getString(it) }
        }
        val codeLabels = SalahPosture.classificationLabels.map { it.name }
        require(modelLabels == codeLabels) {
            "Model label order $modelLabels does not match code label order $codeLabels"
        }

        featureMean = FloatArray(meanArray.length()) { meanArray.getDouble(it).toFloat() }
        featureStd = FloatArray(stdArray.length()) { i ->
            val s = stdArray.getDouble(i).toFloat()
            if (s < 1e-7f) 1.0f else s // Avoid division by zero (matching Python)
        }
        require(featureMean.all { it.isFinite() } && featureStd.all { it.isFinite() && it > 0f }) {
            "Normalization parameters must be finite with positive standard deviations"
        }

        // Initialize circular buffer
        featureBuffer = Array(sequenceLength) { FloatArray(featuresPerWindow) }

        // Load TFLite model
        val modelBuffer = loadModelFile(context)
        val options = Interpreter.Options().apply {
            setNumThreads(2)
        }
        interpreter = Interpreter(modelBuffer, options)

        val expectedInputShape = intArrayOf(1, sequenceLength, featuresPerWindow)
        val expectedOutputShape = intArrayOf(1, SalahPosture.classificationLabels.size)
        val actualInputShape = interpreter.getInputTensor(0).shape()
        val actualOutputShape = interpreter.getOutputTensor(0).shape()
        if (!actualInputShape.contentEquals(expectedInputShape) ||
            !actualOutputShape.contentEquals(expectedOutputShape)
        ) {
            interpreter.close()
            error(
                "TFLite tensor mismatch: input=${actualInputShape.contentToString()} " +
                    "output=${actualOutputShape.contentToString()}",
            )
        }

        Log.d(TAG, "Initialized: seq=$sequenceLength, features=$featuresPerWindow, " +
                "model_version=$modelVersion, " +
                "input=${interpreter.getInputTensor(0).shape().contentToString()}, " +
                "output=${interpreter.getOutputTensor(0).shape().contentToString()}")
    }

    /**
     * Add a new sensor sample and run classification.
     *
     * Returns null until the complete sequence length used during training has
     * accumulated, or when model confidence is too low. The model was not trained on
     * zero-padded partial sequences, so running those would be out-of-distribution.
     */
    fun addSampleAndClassify(sample: SalahDataSample): ClassificationResult? {
        // Extract features from this window
        val features = SalahFeatureExtractor.extractFeatures(sample)

        // Add to circular buffer
        features.copyInto(featureBuffer[bufferIndex])
        bufferIndex = (bufferIndex + 1) % sequenceLength
        bufferFilled = minOf(bufferFilled + 1, sequenceLength)

        if (bufferFilled < sequenceLength) return null
        return classify()
    }

    /**
     * Run classification on the current buffer contents.
     *
     * The buffer is full here, matching the sequence shape seen during training.
     */
    private fun classify(): ClassificationResult? {
        // Build input tensor [1, sequenceLength, featuresPerWindow]
        val inputBuffer = ByteBuffer.allocateDirect(
            4 * sequenceLength * featuresPerWindow
        ).order(ByteOrder.nativeOrder())

        // Full sequence: the oldest entry is at bufferIndex.
        val startIdx = bufferIndex

        for (i in 0 until sequenceLength) {
            val idx = (startIdx + i) % sequenceLength
            val features = featureBuffer[idx]
            for (j in 0 until featuresPerWindow) {
                // Z-score normalize
                val normalized = (features[j] - featureMean[j]) / featureStd[j]
                inputBuffer.putFloat(normalized)
            }
        }
        inputBuffer.rewind()

        // Output tensor [1, NUM_CLASSES]
        val numClasses = SalahPosture.classificationLabels.size
        val output = Array(1) { FloatArray(numClasses) }

        // Run inference
        interpreter.run(inputBuffer, output)

        val rawProbabilities = output[0]

        // Apply EMA smoothing to reduce jitter between frames
        for (i in rawProbabilities.indices) {
            smoothedConfidences[i] = EMA_ALPHA * rawProbabilities[i] +
                (1 - EMA_ALPHA) * smoothedConfidences[i]
        }

        val probabilities = smoothedConfidences.copyOf()

        // Find best class
        var bestIndex = 0
        var bestProb = probabilities[0]
        for (i in 1 until numClasses) {
            if (probabilities[i] > bestProb) {
                bestProb = probabilities[i]
                bestIndex = i
            }
        }

        // Use per-posture confidence threshold
        val posture = SalahPosture.fromIndex(bestIndex)
        val threshold = POSTURE_CONFIDENCE_THRESHOLDS[posture] ?: DEFAULT_CONFIDENCE

        // Debug: log raw ML output vs smoothed output for diagnosis
        val labels = SalahPosture.classificationLabels
        val rawStr = rawProbabilities.mapIndexed { i, p -> "${labels.getOrElse(i){"?"}}: %.2f".format(p) }.joinToString(" | ")
        val smoothStr = probabilities.mapIndexed { i, p -> "${labels.getOrElse(i){"?"}}: %.2f".format(p) }.joinToString(" | ")
        Log.d(TAG, "RAW[$rawStr] EMA[$smoothStr] best=${posture.displayName} conf=%.3f thr=%.2f ${if (bestProb < threshold) "REJECTED" else "ACCEPTED"}".format(bestProb, threshold))

        if (bestProb < threshold) return null

        return ClassificationResult(posture, bestProb, probabilities, isPartialSequence = false)
    }

    /**
     * Apply EMA smoothing to externally-provided probabilities.
     * Useful for live recording mode where the caller manages the buffer.
     */
    fun smoothConfidences(rawOutput: FloatArray): FloatArray {
        for (i in rawOutput.indices) {
            smoothedConfidences[i] = EMA_ALPHA * rawOutput[i] +
                (1 - EMA_ALPHA) * smoothedConfidences[i]
        }
        return smoothedConfidences.copyOf()
    }

    fun reset() {
        bufferIndex = 0
        bufferFilled = 0
        for (row in featureBuffer) row.fill(0f)
        smoothedConfidences.fill(0f)
    }

    val isReady: Boolean get() = bufferFilled >= sequenceLength

    override fun close() {
        interpreter.close()
        Log.d(TAG, "Closed")
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_FILE)
        val inputStream = fd.createInputStream()
        val channel = inputStream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }
}
