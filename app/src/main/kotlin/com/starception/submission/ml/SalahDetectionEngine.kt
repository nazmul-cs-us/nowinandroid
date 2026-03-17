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

        // Per-posture confidence thresholds: distinctive postures can use lower thresholds,
        // brief transitions need higher confidence to avoid false positives
        private val POSTURE_CONFIDENCE_THRESHOLDS = mapOf(
            SalahPosture.QIYAM to 0.50f,          // Standing is distinctive (upright orientation)
            SalahPosture.RUKU to 0.55f,            // Bowing is distinctive (forward lean)
            SalahPosture.GOING_TO_SUJUD to 0.65f,  // Brief transition, needs higher confidence
            SalahPosture.SUJUD to 0.50f,           // Prostration is very distinctive (inverted)
            SalahPosture.JALSA to 0.60f,           // Sitting between sujuds
            SalahPosture.TASHAHHUD to 0.60f,       // Final sitting (similar to JALSA)
            SalahPosture.QIYAM_RISING to 0.65f     // Brief transition
        )
        private const val DEFAULT_CONFIDENCE = 0.60f

        // EMA smoothing factor: higher = more responsive, lower = smoother
        private const val EMA_ALPHA = 0.3f

        // Minimum windows before first inference (half of sequence length for faster start)
        private const val MIN_WINDOWS_FOR_INFERENCE = 10
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

        // Model version validation: prevent crashes from mismatched model/feature vectors
        val modelVersion = params.optInt("model_version", 1)
        if (featuresPerWindow != EXPECTED_FEATURES) {
            Log.w(TAG, "Feature count mismatch: model expects $featuresPerWindow, code expects $EXPECTED_FEATURES")
        }
        if (modelVersion < MODEL_VERSION) {
            Log.w(TAG, "Model version $modelVersion is older than code version $MODEL_VERSION - retrain recommended")
        }

        featureMean = FloatArray(meanArray.length()) { meanArray.getDouble(it).toFloat() }
        featureStd = FloatArray(stdArray.length()) { i ->
            val s = stdArray.getDouble(i).toFloat()
            if (s < 1e-7f) 1.0f else s // Avoid division by zero (matching Python)
        }

        // Initialize circular buffer
        featureBuffer = Array(sequenceLength) { FloatArray(featuresPerWindow) }

        // Load TFLite model
        val modelBuffer = loadModelFile(context)
        val options = Interpreter.Options().apply {
            setNumThreads(2)
        }
        interpreter = Interpreter(modelBuffer, options)

        Log.d(TAG, "Initialized: seq=$sequenceLength, features=$featuresPerWindow, " +
                "model_version=$modelVersion, " +
                "input=${interpreter.getInputTensor(0).shape().contentToString()}, " +
                "output=${interpreter.getOutputTensor(0).shape().contentToString()}")
    }

    /**
     * Add a new sensor sample and run classification.
     *
     * Supports partial inference: after MIN_WINDOWS_FOR_INFERENCE windows (1 second),
     * runs inference with zero-padded remaining slots for faster initial predictions.
     * Full-sequence inference runs once sequenceLength windows have accumulated.
     *
     * Returns null if not enough windows accumulated yet or confidence is too low.
     */
    fun addSampleAndClassify(sample: SalahDataSample): ClassificationResult? {
        // Extract features from this window
        val features = SalahFeatureExtractor.extractFeatures(sample)

        // Add to circular buffer
        features.copyInto(featureBuffer[bufferIndex])
        bufferIndex = (bufferIndex + 1) % sequenceLength
        bufferFilled = minOf(bufferFilled + 1, sequenceLength)

        // Allow partial inference after MIN_WINDOWS_FOR_INFERENCE (1 second)
        if (bufferFilled < MIN_WINDOWS_FOR_INFERENCE) return null

        val isPartial = bufferFilled < sequenceLength
        return classify(isPartial)
    }

    /**
     * Run classification on the current buffer contents.
     *
     * When [isPartialSequence] is true, only bufferFilled windows contain real data;
     * remaining slots are zero-padded. Partial predictions apply a confidence penalty.
     */
    private fun classify(isPartialSequence: Boolean = false): ClassificationResult? {
        // Build input tensor [1, sequenceLength, featuresPerWindow]
        val inputBuffer = ByteBuffer.allocateDirect(
            4 * sequenceLength * featuresPerWindow
        ).order(ByteOrder.nativeOrder())

        // For partial sequences, pad the beginning with zeros and place real data at end
        val realWindows = if (isPartialSequence) bufferFilled else sequenceLength
        val paddingWindows = sequenceLength - realWindows

        // Write zero padding first
        for (i in 0 until paddingWindows) {
            for (j in 0 until featuresPerWindow) {
                inputBuffer.putFloat(0f) // Zero = mean after normalization offset
            }
        }

        // Write real data from circular buffer in correct order
        val startIdx = if (isPartialSequence) {
            // For partial: read the last `realWindows` entries
            (bufferIndex - realWindows + sequenceLength) % sequenceLength
        } else {
            bufferIndex // Full sequence: oldest entry is at bufferIndex
        }

        for (i in 0 until realWindows) {
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

        // Apply confidence penalty for partial sequences (less data = less certain)
        if (isPartialSequence) {
            val fillRatio = bufferFilled.toFloat() / sequenceLength
            for (i in probabilities.indices) {
                probabilities[i] *= fillRatio
            }
        }

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
        if (bestProb < threshold) return null

        return ClassificationResult(posture, bestProb, probabilities, isPartialSequence)
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

    val isReady: Boolean get() = bufferFilled >= MIN_WINDOWS_FOR_INFERENCE

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
