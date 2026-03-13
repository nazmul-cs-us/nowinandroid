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
        private const val MIN_CONFIDENCE = 0.60f
    }

    data class ClassificationResult(
        val posture: SalahPosture,
        val confidence: Float,
        val allProbabilities: FloatArray
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

    init {
        // Load normalization parameters
        val normJson = context.assets.open(NORM_PARAMS_FILE).bufferedReader().readText()
        val params = JSONObject(normJson)

        val meanArray = params.getJSONArray("mean")
        val stdArray = params.getJSONArray("std")
        sequenceLength = params.getInt("sequence_length")
        featuresPerWindow = params.getInt("features_per_window")

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
                "input=${interpreter.getInputTensor(0).shape().contentToString()}, " +
                "output=${interpreter.getOutputTensor(0).shape().contentToString()}")
    }

    /**
     * Add a new sensor sample and run classification if the buffer is full.
     * Returns null if not enough windows accumulated yet or confidence is too low.
     */
    fun addSampleAndClassify(sample: SalahDataSample): ClassificationResult? {
        // Extract features from this window
        val features = SalahFeatureExtractor.extractFeatures(sample)

        // Add to circular buffer
        features.copyInto(featureBuffer[bufferIndex])
        bufferIndex = (bufferIndex + 1) % sequenceLength
        bufferFilled = minOf(bufferFilled + 1, sequenceLength)

        // Need full sequence before classifying
        if (bufferFilled < sequenceLength) return null

        return classify()
    }

    /**
     * Run classification on the current buffer contents.
     */
    private fun classify(): ClassificationResult? {
        // Build input tensor [1, sequenceLength, featuresPerWindow]
        val inputBuffer = ByteBuffer.allocateDirect(
            4 * sequenceLength * featuresPerWindow
        ).order(ByteOrder.nativeOrder())

        // Read from circular buffer in correct order
        for (i in 0 until sequenceLength) {
            val idx = (bufferIndex + i) % sequenceLength
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

        val probabilities = output[0]

        // Find best class
        var bestIndex = 0
        var bestProb = probabilities[0]
        for (i in 1 until numClasses) {
            if (probabilities[i] > bestProb) {
                bestProb = probabilities[i]
                bestIndex = i
            }
        }

        if (bestProb < MIN_CONFIDENCE) return null

        val posture = SalahPosture.fromIndex(bestIndex)
        return ClassificationResult(posture, bestProb, probabilities)
    }

    fun reset() {
        bufferIndex = 0
        bufferFilled = 0
        for (row in featureBuffer) row.fill(0f)
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
