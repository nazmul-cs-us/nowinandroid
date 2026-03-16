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
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.starception.submission.download.AssetRepository
import com.starception.submission.feature.search.VoiceSearchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Sherpa-ONNX Voice Service for fast offline speech recognition
 *
 * Uses the streaming Zipformer model for real-time transcription.
 * Much faster than Whisper TFLite due to optimized ONNX runtime.
 *
 * Model: sherpa-onnx-streaming-zipformer-en-20M (42MB int8)
 */
class SherpaVoiceService(
    private val context: Context,
    private val assetRepository: AssetRepository? = null,
) {
    companion object {
        private const val TAG = "SherpaVoiceService"
        private const val SAMPLE_RATE = 16000
        private const val ASSETS_DIR = "sherpa"
        private const val ENCODER_FILE = "encoder.int8.onnx"
        private const val DECODER_FILE = "decoder.int8.onnx"
        private const val JOINER_FILE = "joiner.int8.onnx"
        private const val TOKENS_FILE = "tokens.txt"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var initJob: Job? = null
    private var recordingJob: Job? = null

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _recognizedText = MutableStateFlow<String?>(null)
    val recognizedText: StateFlow<String?> = _recognizedText.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var currentCallback: ((VoiceSearchService.VoiceSearchResult) -> Unit)? = null

    /**
     * Initialize Sherpa-ONNX recognizer
     */
    fun initialize(onComplete: ((Boolean) -> Unit)? = null) {
        if (_isInitialized.value) {
            onComplete?.invoke(true)
            return
        }

        initJob = scope.launch {
            _statusMessage.value = "Loading Sherpa-ONNX..."

            try {
                val success = withContext(Dispatchers.IO) {
                    initializeRecognizer()
                }

                _isInitialized.value = success
                _statusMessage.value = if (success) "Sherpa ready" else "Failed to load"
                onComplete?.invoke(success)

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Sherpa-ONNX", e)
                _statusMessage.value = "Init failed"
                onComplete?.invoke(false)
            }
        }
    }

    private fun initializeRecognizer(): Boolean {
        try {
            // Copy model files from assets
            val encoderPath = copyAssetToFiles("$ASSETS_DIR/$ENCODER_FILE", ENCODER_FILE)
            val decoderPath = copyAssetToFiles("$ASSETS_DIR/$DECODER_FILE", DECODER_FILE)
            val joinerPath = copyAssetToFiles("$ASSETS_DIR/$JOINER_FILE", JOINER_FILE)
            val tokensPath = copyAssetToFiles("$ASSETS_DIR/$TOKENS_FILE", TOKENS_FILE)

            Log.i(TAG, "Model files copied: encoder=${encoderPath.length()/1024}KB")

            // Create transducer model config
            val transducerConfig = OnlineTransducerModelConfig(
                encoder = encoderPath.absolutePath,
                decoder = decoderPath.absolutePath,
                joiner = joinerPath.absolutePath,
            )

            // Create model config
            val modelConfig = OnlineModelConfig(
                transducer = transducerConfig,
                tokens = tokensPath.absolutePath,
                numThreads = Runtime.getRuntime().availableProcessors(),
                debug = false,
            )

            // Feature config (16kHz, 80 mel bins)
            val featureConfig = FeatureConfig(
                sampleRate = SAMPLE_RATE,
                featureDim = 80,
            )

            // Endpoint detection config
            val endpointConfig = EndpointConfig(
                rule1 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 2.4f, minUtteranceLength = 0f),
                rule2 = EndpointRule(mustContainNonSilence = true, minTrailingSilence = 1.2f, minUtteranceLength = 0f),
                rule3 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 0f, minUtteranceLength = 20f),
            )

            // Create recognizer config
            val config = OnlineRecognizerConfig(
                modelConfig = modelConfig,
                featConfig = featureConfig,
                endpointConfig = endpointConfig,
                enableEndpoint = true,
                decodingMethod = "greedy_search",
            )

            // Create recognizer - pass null for assetManager since we're using file paths
            recognizer = OnlineRecognizer(
                assetManager = null,
                config = config,
            )

            Log.i(TAG, "Sherpa-ONNX initialized successfully")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Sherpa-ONNX", e)
            return false
        }
    }

    private fun copyAssetToFiles(assetPath: String, fileName: String): File {
        val outFile = File(context.filesDir, "sherpa_$fileName")

        if (outFile.exists() && outFile.length() > 0) {
            return outFile
        }

        // Convert sherpa/... path to models/sherpa/... CDN key
        val cdnKey = "models/$assetPath"
        val inputStream = assetRepository?.openAsset(cdnKey)
            ?: context.assets.open(assetPath)
        inputStream.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }

        Log.d(TAG, "Copied $assetPath to ${outFile.absolutePath}")
        return outFile
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isAvailable(): Boolean = _isInitialized.value

    /**
     * Start real-time speech recognition
     */
    fun startListening(onResult: (VoiceSearchService.VoiceSearchResult) -> Unit) {
        if (!hasPermission()) {
            onResult(VoiceSearchService.VoiceSearchResult.Error("Microphone permission required"))
            return
        }

        if (!_isInitialized.value) {
            initialize { success ->
                if (success) {
                    startListeningInternal(onResult)
                } else {
                    onResult(VoiceSearchService.VoiceSearchResult.Error("Failed to initialize Sherpa"))
                }
            }
            return
        }

        startListeningInternal(onResult)
    }

    private fun startListeningInternal(onResult: (VoiceSearchService.VoiceSearchResult) -> Unit) {
        if (_isListening.value) return

        currentCallback = onResult
        _isListening.value = true
        _recognizedText.value = ""
        _statusMessage.value = "Listening..."

        // Create new stream for this recognition session
        stream = recognizer?.createStream()

        // Start recording and processing
        recordingJob = scope.launch(Dispatchers.IO) {
            recordAndRecognize()
        }
    }

    private suspend fun recordAndRecognize() {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            audioRecord?.startRecording()
            Log.d(TAG, "Started recording")

            val buffer = ShortArray(bufferSize / 2)
            val floatBuffer = FloatArray(buffer.size)

            while (_isListening.value && scope.isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (read > 0) {
                    // Convert to float samples
                    for (i in 0 until read) {
                        floatBuffer[i] = buffer[i] / 32768.0f
                    }

                    // Feed to recognizer
                    stream?.let { s ->
                        s.acceptWaveform(floatBuffer.copyOf(read), SAMPLE_RATE)

                        while (recognizer?.isReady(s) == true) {
                            recognizer?.decode(s)
                        }

                        // Get partial result
                        val result = recognizer?.getResult(s)
                        if (!result?.text.isNullOrBlank()) {
                            withContext(Dispatchers.Main) {
                                _recognizedText.value = result?.text
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Recording error", e)
        } finally {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        }
    }

    /**
     * Stop listening and get final result
     */
    fun stopListening() {
        if (!_isListening.value) return

        _isListening.value = false
        _statusMessage.value = "Processing..."

        scope.launch {
            // Wait for recording to stop
            recordingJob?.join()

            // Get final result
            val finalText = _recognizedText.value?.trim() ?: ""

            withContext(Dispatchers.Main) {
                val callback = currentCallback
                currentCallback = null

                // Reset stream for next use
                stream?.let { recognizer?.reset(it) }

                if (finalText.isNotBlank()) {
                    Log.i(TAG, "Final result: $finalText")
                    _statusMessage.value = "Done"
                    callback?.invoke(VoiceSearchService.VoiceSearchResult.Success(finalText))
                } else {
                    _statusMessage.value = "No speech detected"
                    callback?.invoke(VoiceSearchService.VoiceSearchResult.Cancelled)
                }
            }
        }
    }

    fun cancel() {
        _isListening.value = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        currentCallback?.invoke(VoiceSearchService.VoiceSearchResult.Cancelled)
        currentCallback = null
    }

    fun release() {
        cancel()
        initJob?.cancel()
        recognizer?.release()
        recognizer = null
        _isInitialized.value = false
    }
}
