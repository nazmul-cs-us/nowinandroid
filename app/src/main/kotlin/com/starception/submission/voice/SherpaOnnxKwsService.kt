/*
 * Copyright 2024 Starception
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fast keyword spotting service using Sherpa-ONNX.
 * Optimized for quick yes/no detection (~100ms) compared to Whisper (~26 seconds).
 *
 * Uses the zipformer-gigaspeech-3.3M English KWS model (int8 quantized, ~5MB).
 *
 * Keywords file format (in assets/kws/keywords.txt):
 * ```
 * ▁YES @yes
 * ▁YEAH @yes
 * ▁YEP @yes
 * ▁NO @no
 * ▁NOPE @no
 * ▁STOP @no
 * ▁SKIP @no
 * ```
 */
@Singleton
class SherpaOnnxKwsService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SherpaOnnxKwsService"

        // Audio recording configuration (must match model's expected sample rate)
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Model files in assets/kws/
        private const val ENCODER_FILE = "encoder.int8.onnx"
        private const val DECODER_FILE = "decoder.int8.onnx"
        private const val JOINER_FILE = "joiner.int8.onnx"
        private const val TOKENS_FILE = "tokens.txt"
        private const val KEYWORDS_FILE = "keywords.txt"

        // Default listening duration
        const val DEFAULT_LISTENING_DURATION_MS = 5000L

        // Streaming chunk size (process audio in chunks for real-time detection)
        private const val CHUNK_SIZE_SAMPLES = 1600 // 100ms chunks at 16kHz
    }

    // KWS instance
    private var kws: KeywordSpotter? = null
    private var isModelLoaded = false

    // Recording state
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var isListening = false  // Prevent concurrent listening

    // Cache directory for extracted model files
    private val modelDir: File by lazy {
        File(context.filesDir, "kws_model").also { it.mkdirs() }
    }

    /**
     * Voice recognition result types
     */
    sealed class VoiceResult {
        object Yes : VoiceResult()
        object No : VoiceResult()
        object Timeout : VoiceResult()
        data class Unrecognized(val text: String) : VoiceResult()
        data class Error(val message: String) : VoiceResult()
    }

    /**
     * Callback interface for voice recognition results
     */
    interface VoiceRecognitionCallback {
        fun onResult(result: VoiceResult)
        fun onListeningStarted()
        fun onListeningStopped()
        fun onStatusUpdate(message: String)
    }

    /**
     * Load the KWS model from assets.
     * Should be called once before using recognition.
     */
    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        if (isModelLoaded) {
            Log.d(TAG, "Model already loaded")
            return@withContext true
        }

        try {
            Log.i(TAG, "Loading Sherpa-ONNX KWS model...")
            val startTime = System.currentTimeMillis()

            // Extract model files from assets
            val encoderPath = extractAssetFile(ENCODER_FILE)
            val decoderPath = extractAssetFile(DECODER_FILE)
            val joinerPath = extractAssetFile(JOINER_FILE)
            val tokensPath = extractAssetFile(TOKENS_FILE)
            val keywordsPath = extractAssetFile(KEYWORDS_FILE)

            if (encoderPath == null || decoderPath == null || joinerPath == null ||
                tokensPath == null || keywordsPath == null) {
                Log.e(TAG, "Failed to extract model files")
                return@withContext false
            }

            Log.d(TAG, "Extracted files:")
            Log.d(TAG, "  Encoder: $encoderPath")
            Log.d(TAG, "  Decoder: $decoderPath")
            Log.d(TAG, "  Joiner: $joinerPath")
            Log.d(TAG, "  Tokens: $tokensPath")
            Log.d(TAG, "  Keywords: $keywordsPath")

            // Configure transducer model (encoder, decoder, joiner)
            val transducerConfig = OnlineTransducerModelConfig(
                encoder = encoderPath,
                decoder = decoderPath,
                joiner = joinerPath
            )

            // Configure online model
            val modelConfig = OnlineModelConfig(
                transducer = transducerConfig,
                tokens = tokensPath,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )

            // Configure feature extraction
            val featConfig = FeatureConfig(
                sampleRate = SAMPLE_RATE,
                featureDim = 80
            )

            // Configure keyword spotter
            // Note: Lower threshold = more sensitive detection (may have more false positives)
            // Higher score = keyword needs stronger confidence to be detected
            val kwsConfig = KeywordSpotterConfig(
                featConfig = featConfig,
                modelConfig = modelConfig,
                keywordsFile = keywordsPath,
                keywordsThreshold = 0.1f,  // Lowered from 0.25 for better sensitivity
                keywordsScore = 1.0f,       // Lowered from 1.5 for easier detection
                maxActivePaths = 4,
                numTrailingBlanks = 1       // Reduced from 2 for faster detection
            )

            // Create keyword spotter (not using asset manager since we extracted files)
            kws = KeywordSpotter(
                assetManager = null,
                config = kwsConfig
            )

            isModelLoaded = true
            val loadTime = System.currentTimeMillis() - startTime
            Log.i(TAG, "Sherpa-ONNX KWS model loaded successfully in ${loadTime}ms")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error loading KWS model", e)
            isModelLoaded = false
            false
        }
    }

    /**
     * Extract a file from assets to internal storage.
     */
    private fun extractAssetFile(fileName: String): String? {
        val outputFile = File(modelDir, fileName)

        // Check if already extracted
        if (outputFile.exists() && outputFile.length() > 0) {
            return outputFile.absolutePath
        }

        return try {
            outputFile.parentFile?.mkdirs()

            context.assets.open("kws/$fileName").use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Extracted: $fileName -> ${outputFile.absolutePath}")
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract asset: $fileName", e)
            null
        }
    }

    /**
     * Check if the model is ready for inference.
     */
    fun isModelReady(): Boolean = isModelLoaded && kws != null

    /**
     * Start listening for voice input and perform real-time keyword spotting.
     * Much faster than Whisper as it streams audio and detects keywords in real-time.
     *
     * @param durationMs Maximum listening duration in milliseconds
     * @param callback Callback for recognition results
     */
    suspend fun startListening(
        durationMs: Long = DEFAULT_LISTENING_DURATION_MS,
        callback: VoiceRecognitionCallback
    ) = withContext(Dispatchers.IO) {
        // Prevent concurrent listening
        if (isListening) {
            Log.w(TAG, "Already listening, ignoring new request")
            callback.onResult(VoiceResult.Error("Already listening"))
            return@withContext
        }

        // Check model is loaded
        if (!isModelReady()) {
            Log.e(TAG, "Model not loaded, attempting to load...")
            if (!loadModel()) {
                callback.onResult(VoiceResult.Error("Failed to load speech recognition model"))
                return@withContext
            }
        }

        // Check audio permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Audio recording permission not granted")
            callback.onResult(VoiceResult.Error("Microphone permission not granted"))
            return@withContext
        }

        isListening = true

        try {
            callback.onListeningStarted()
            callback.onStatusUpdate("Listening...")

            // Create a fresh stream for this session
            val stream = kws?.createStream() ?: run {
                Log.e(TAG, "Failed to create stream")
                callback.onResult(VoiceResult.Error("Failed to create audio stream"))
                isListening = false
                return@withContext
            }

            // Stream audio and detect keywords in real-time
            val detectedKeyword = streamAndDetect(durationMs, callback, stream)

            callback.onListeningStopped()

            // Parse result
            val result = parseKeyword(detectedKeyword)
            Log.i(TAG, "Detection result: '$detectedKeyword' -> $result")

            callback.onResult(result)

            // Release the stream
            stream.release()

        } catch (e: Exception) {
            Log.e(TAG, "Error during voice recognition", e)
            callback.onListeningStopped()
            callback.onResult(VoiceResult.Error(e.message ?: "Unknown error"))
        } finally {
            isListening = false
        }
    }

    /**
     * Stream audio and detect keywords in real-time.
     * Returns the first detected keyword or empty string if timeout.
     */
    private fun streamAndDetect(durationMs: Long, callback: VoiceRecognitionCallback, stream: OnlineStream): String {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size: $bufferSize")
                return ""
            }

            @Suppress("MissingPermission")
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize.coerceAtLeast(CHUNK_SIZE_SAMPLES * 2)
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized")
                audioRecord?.release()
                audioRecord = null
                return ""
            }

            // Start recording
            audioRecord?.startRecording()
            isRecording = true
            Log.d(TAG, "Started streaming audio for keyword detection...")

            val shortBuffer = ShortArray(CHUNK_SIZE_SAMPLES)
            val floatBuffer = FloatArray(CHUNK_SIZE_SAMPLES)

            val startTime = System.currentTimeMillis()
            var totalSamplesRead = 0
            var isReadyCount = 0
            var decodeCount = 0

            while (isRecording) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= durationMs) {
                    Log.d(TAG, "Listening timeout after ${durationMs}ms")
                    Log.d(TAG, "📊 Stats: totalSamples=$totalSamplesRead, isReadyCalls=$isReadyCount, decodeCalls=$decodeCount")
                    break
                }

                // Read audio chunk
                val read = audioRecord?.read(shortBuffer, 0, CHUNK_SIZE_SAMPLES) ?: 0
                if (read <= 0) {
                    Log.w(TAG, "AudioRecord read error: $read")
                    continue
                }
                totalSamplesRead += read

                // Calculate audio level for debugging (RMS)
                var sumSquares = 0.0
                for (i in 0 until read) {
                    sumSquares += shortBuffer[i].toDouble() * shortBuffer[i].toDouble()
                }
                val rms = kotlin.math.sqrt(sumSquares / read)

                // Log audio level every ~1 second (every 10 chunks at 100ms each)
                if ((totalSamplesRead / CHUNK_SIZE_SAMPLES) % 10 == 0) {
                    Log.d(TAG, "🎙️ Audio chunk: samples=$read, RMS=${rms.toInt()}, total=${totalSamplesRead}")
                }

                // Convert short samples to float [-1.0, 1.0]
                for (i in 0 until read) {
                    floatBuffer[i] = shortBuffer[i].toFloat() / Short.MAX_VALUE
                }

                // Feed to KWS stream
                stream.acceptWaveform(floatBuffer.copyOf(read), SAMPLE_RATE)

                // Check for keyword detection
                var localReadyCount = 0
                while (kws?.isReady(stream) == true) {
                    isReadyCount++
                    localReadyCount++
                    kws?.decode(stream)
                    decodeCount++
                    val result = kws?.getResult(stream)
                    val keyword = result?.keyword ?: ""
                    val tokens = result?.tokens ?: emptyArray()

                    // Log what we're getting from the model (even if no keyword yet)
                    if (decodeCount % 5 == 0 || keyword.isNotBlank()) {
                        Log.d(TAG, "🔍 Decode #$decodeCount: keyword='$keyword', tokens=${tokens.contentToString()}")
                    }

                    if (keyword.isNotBlank()) {
                        Log.i(TAG, "🎯 Keyword detected: '$keyword' after ${elapsed}ms")
                        callback.onStatusUpdate("Detected: $keyword")

                        // Stop recording and return immediately
                        stopRecording()
                        return keyword
                    }
                }

                // Log isReady status periodically
                if (localReadyCount > 0 && (totalSamplesRead / CHUNK_SIZE_SAMPLES) % 10 == 0) {
                    Log.d(TAG, "🔄 isReady was true $localReadyCount times this chunk")
                }
            }

            stopRecording()
            return ""

        } catch (e: Exception) {
            Log.e(TAG, "Error in streamAndDetect", e)
            stopRecording()
            return ""
        }
    }

    /**
     * Stop recording.
     */
    private fun stopRecording() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        }
    }

    /**
     * Parse detected keyword to yes/no result.
     * Keywords file maps multiple variants to @yes or @no tags.
     */
    private fun parseKeyword(keyword: String): VoiceResult {
        if (keyword.isBlank()) {
            return VoiceResult.Timeout
        }

        val lowerKeyword = keyword.lowercase().trim()

        // Check for yes variants
        if (lowerKeyword.contains("@yes") ||
            lowerKeyword.contains("yes") ||
            lowerKeyword.contains("yeah") ||
            lowerKeyword.contains("yep") ||
            lowerKeyword.contains("yup") ||
            lowerKeyword.contains("sure") ||
            lowerKeyword.contains("okay") ||
            lowerKeyword.contains("ok")) {
            return VoiceResult.Yes
        }

        // Check for no variants
        if (lowerKeyword.contains("@no") ||
            lowerKeyword.contains("no") ||
            lowerKeyword.contains("nope") ||
            lowerKeyword.contains("nah") ||
            lowerKeyword.contains("stop") ||
            lowerKeyword.contains("skip")) {
            return VoiceResult.No
        }

        // Unrecognized keyword
        return VoiceResult.Unrecognized(keyword)
    }

    /**
     * Stop any ongoing recording.
     */
    fun stopListening() {
        isRecording = false
        isListening = false
        stopRecording()
    }

    /**
     * Release resources.
     */
    fun release() {
        stopListening()
        kws?.release()
        kws = null
        isModelLoaded = false
    }

    /**
     * Run a test of the KWS service.
     * Say "YES" or "NO" within 5 seconds!
     */
    suspend fun runTest() {
        Log.i(TAG, "========== KWS TEST STARTED ==========")
        Log.i(TAG, "Say 'YES' or 'NO' within 5 seconds!")

        startListening(
            durationMs = 5000L,
            callback = object : VoiceRecognitionCallback {
                override fun onResult(result: VoiceResult) {
                    when (result) {
                        is VoiceResult.Yes -> Log.i(TAG, "✅ TEST: Detected YES!")
                        is VoiceResult.No -> Log.i(TAG, "❌ TEST: Detected NO!")
                        is VoiceResult.Timeout -> Log.i(TAG, "⏱️ TEST: Timeout - no keyword detected")
                        is VoiceResult.Unrecognized -> Log.i(TAG, "❓ TEST: Unrecognized: ${result.text}")
                        is VoiceResult.Error -> Log.e(TAG, "💥 TEST: Error: ${result.message}")
                    }
                    Log.i(TAG, "========== KWS TEST COMPLETED ==========")
                }

                override fun onListeningStarted() {
                    Log.i(TAG, "🎤 Listening started...")
                }

                override fun onListeningStopped() {
                    Log.i(TAG, "🎤 Listening stopped")
                }

                override fun onStatusUpdate(message: String) {
                    Log.i(TAG, "📢 Status: $message")
                }
            }
        )
    }
}
