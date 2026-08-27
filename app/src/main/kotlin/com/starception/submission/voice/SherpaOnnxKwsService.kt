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
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.starception.submission.download.AssetRepository
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
    @ApplicationContext private val context: Context,
    private val assetRepository: AssetRepository,
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
    private var lastRecordingData: FloatArray? = null
    private var debugAudioTrack: AudioTrack? = null

    // Audio enhancement effects for noisy environments (driving mode)
    private var noiseSuppressor: NoiseSuppressor? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var automaticGainControl: AutomaticGainControl? = null

    // Cache directory for extracted model files
    private val modelDir: File by lazy {
        File(context.filesDir, "kws_model").also { it.mkdirs() }
    }

    // Audio manager for device selection
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
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

            val inputStream = assetRepository.openAsset("models/kws/$fileName")
                ?: context.assets.open("kws/$fileName")
            inputStream.use { input ->
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
     *
     * Uses Android's built-in noise suppression for better detection in noisy environments (driving).
     */
    private fun streamAndDetect(durationMs: Long, callback: VoiceRecognitionCallback, stream: OnlineStream): String {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size: $bufferSize")
                return ""
            }

            // Check if we're in Bluetooth mode - this affects audio source selection
            val isBluetoothMode = audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
            Log.i(TAG, "🎤 Audio mode: ${if (isBluetoothMode) "IN_COMMUNICATION (Bluetooth)" else "NORMAL"}")

            // Create AudioRecord with explicit device selection for phone's built-in mic
            audioRecord = createAudioRecordWithPreferredDevice(bufferSize, isBluetoothMode)

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized")
                audioRecord?.release()
                audioRecord = null
                return ""
            }

            // Log which device is being used
            logAudioRecordingDevice()

            // Attach audio enhancement effects for noisy driving environments
            val audioSessionId = audioRecord?.audioSessionId ?: 0
            attachAudioEffects(audioSessionId)

            // Start recording
            audioRecord?.startRecording()
            isRecording = true
            Log.i(TAG, "🎤 Started streaming audio for keyword detection (duration: ${durationMs}ms)")

            val shortBuffer = ShortArray(CHUNK_SIZE_SAMPLES)
            val floatBuffer = FloatArray(CHUNK_SIZE_SAMPLES)
            val capturedAudio = mutableListOf<Float>()

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

                // Log audio level every 500ms (every 5 chunks at 100ms each) - use INFO level for visibility
                if ((totalSamplesRead / CHUNK_SIZE_SAMPLES) % 5 == 0) {
                    val dbLevel = if (rms > 0) 20 * kotlin.math.log10(rms) else -100.0
                    Log.i(TAG, "🎙️ AUDIO LEVEL: RMS=${rms.toInt()}, dB=${dbLevel.toInt()}, samples=$totalSamplesRead")
                }

                // Convert short samples to float [-1.0, 1.0]
                for (i in 0 until read) {
                    floatBuffer[i] = shortBuffer[i].toFloat() / Short.MAX_VALUE
                }
                capturedAudio.addAll(floatBuffer.copyOf(read).asList())

                // DISABLED: High-pass filter was too aggressive, feed raw audio for testing
                // val filteredAudio = applyHighPassFilter(floatBuffer.copyOf(read))
                // stream.acceptWaveform(filteredAudio, SAMPLE_RATE)

                // Feed raw audio to KWS stream (no filtering for voice capture testing)
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
            if (capturedAudio.isNotEmpty()) {
                lastRecordingData = capturedAudio.toFloatArray()
            }
            return ""

        } catch (e: Exception) {
            Log.e(TAG, "Error in streamAndDetect", e)
            stopRecording()
            return ""
        }
    }

    /**
     * Attach audio enhancement effects for noisy environments.
     * DISABLED FOR TESTING: NoiseSuppressor and AcousticEchoCanceler were too aggressive.
     */
    private fun attachAudioEffects(audioSessionId: Int) {
        // DISABLED: NoiseSuppressor was too aggressive, suppressing user's voice
        // if (NoiseSuppressor.isAvailable()) {
        //     try {
        //         noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
        //             enabled = true
        //             Log.i(TAG, "✅ NoiseSuppressor enabled (session: $audioSessionId)")
        //         }
        //     } catch (e: Exception) {
        //         Log.w(TAG, "⚠️ Failed to create NoiseSuppressor: ${e.message}")
        //     }
        // }
        Log.i(TAG, "⚠️ NoiseSuppressor DISABLED for voice capture testing")

        // DISABLED: AcousticEchoCanceler may also be interfering with voice capture
        // if (AcousticEchoCanceler.isAvailable()) {
        //     try {
        //         acousticEchoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
        //             enabled = true
        //             Log.i(TAG, "✅ AcousticEchoCanceler enabled (session: $audioSessionId)")
        //         }
        //     } catch (e: Exception) {
        //         Log.w(TAG, "⚠️ Failed to create AcousticEchoCanceler: ${e.message}")
        //     }
        // }
        Log.i(TAG, "⚠️ AcousticEchoCanceler DISABLED for voice capture testing")

        // DISABLED: AGC might be interfering with audio capture on some devices
        // Keeping all audio effects disabled until we confirm mic is working
        // if (AutomaticGainControl.isAvailable()) {
        //     try {
        //         automaticGainControl = AutomaticGainControl.create(audioSessionId)?.apply {
        //             enabled = true
        //             Log.i(TAG, "✅ AutomaticGainControl enabled (session: $audioSessionId)")
        //         }
        //     } catch (e: Exception) {
        //         Log.w(TAG, "⚠️ Failed to create AutomaticGainControl: ${e.message}")
        //     }
        // } else {
        //     Log.w(TAG, "⚠️ AutomaticGainControl not available on this device")
        // }
        Log.i(TAG, "⚠️ AutomaticGainControl DISABLED for voice capture testing")
    }

    /**
     * Release audio enhancement effects.
     */
    private fun releaseAudioEffects() {
        try {
            noiseSuppressor?.release()
            noiseSuppressor = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing NoiseSuppressor", e)
        }

        try {
            acousticEchoCanceler?.release()
            acousticEchoCanceler = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AcousticEchoCanceler", e)
        }

        try {
            automaticGainControl?.release()
            automaticGainControl = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AutomaticGainControl", e)
        }
    }

    /**
     * Apply simple high-pass filter to remove low-frequency road noise.
     * Road/engine noise is typically below 300Hz, while speech is 300Hz-3400Hz.
     *
     * Uses a simple first-order IIR high-pass filter with cutoff ~200Hz.
     */
    private var previousSample = 0f
    private var filteredSample = 0f

    private fun applyHighPassFilter(samples: FloatArray): FloatArray {
        // High-pass filter coefficient (alpha)
        // For cutoff frequency fc and sample rate fs:
        // alpha = 1 / (1 + 2*pi*fc/fs)
        // With fc=200Hz and fs=16000Hz: alpha ≈ 0.926
        val alpha = 0.926f

        val filtered = FloatArray(samples.size)
        for (i in samples.indices) {
            // First-order high-pass: y[n] = alpha * (y[n-1] + x[n] - x[n-1])
            filteredSample = alpha * (filteredSample + samples[i] - previousSample)
            previousSample = samples[i]
            filtered[i] = filteredSample
        }
        return filtered
    }

    /**
     * Stop recording.
     */
    @Synchronized
    private fun stopRecording() {
        isRecording = false
        val recorder = audioRecord
        audioRecord = null
        try {
            if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
            recorder?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        }

        // Release audio effects
        releaseAudioEffects()

        // Reset filter state
        previousSample = 0f
        filteredSample = 0f
    }

    /**
     * Create AudioRecord with preferred device selection.
     * On Android 12+ (API 31+), explicitly selects the phone's built-in microphone
     * to avoid using car Bluetooth microphone which is far away and noisy.
     *
     * @param bufferSize Minimum buffer size
     * @param isBluetoothMode If true, uses MIC source instead of VOICE_RECOGNITION for better compatibility
     */
    @Suppress("MissingPermission")
    private fun createAudioRecordWithPreferredDevice(bufferSize: Int, isBluetoothMode: Boolean = false): AudioRecord {
        val actualBufferSize = bufferSize.coerceAtLeast(CHUNK_SIZE_SAMPLES * 4)

        // ALWAYS use MIC audio source for now
        // VOICE_RECOGNITION was returning silent audio (amplitude=0.0000) on some devices
        // MIC provides raw microphone input without system-level preprocessing
        Log.i(TAG, "🎤 Using MIC audio source (VOICE_RECOGNITION was returning silence)")
        val audioSource = MediaRecorder.AudioSource.MIC

        // On Android 6.0+ (API 23+), use AudioRecord.Builder for better control
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val builder = AudioRecord.Builder()
                .setAudioSource(audioSource)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .setEncoding(AUDIO_FORMAT)
                        .build()
                )
                .setBufferSizeInBytes(actualBufferSize)

            // On Android 12+ (API 31+), explicitly set preferred device to built-in mic
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val builtInMic = findBuiltInMicrophone()
                if (builtInMic != null) {
                    Log.i(TAG, "🎤 Setting preferred device to built-in mic: ${builtInMic.productName}")
                    builder.setContext(context)
                    // Note: setPreferredDevice is set after build on the AudioRecord instance
                }
            }

            val record = builder.build()

            // Set preferred device after building (Android 6.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val builtInMic = findBuiltInMicrophone()
                if (builtInMic != null) {
                    val success = record.setPreferredDevice(builtInMic)
                    Log.i(TAG, "🎤 setPreferredDevice(${builtInMic.productName}): $success")
                }
            }

            return record
        } else {
            // Fallback for older devices
            return AudioRecord(
                audioSource,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                actualBufferSize
            )
        }
    }

    /**
     * Find the phone's built-in microphone device.
     * Returns null if not found or on older Android versions.
     */
    private fun findBuiltInMicrophone(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null
        }

        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        Log.d(TAG, "🎤 Available input devices: ${devices.size}")

        for (device in devices) {
            val typeName = getDeviceTypeName(device.type)
            Log.d(TAG, "  - ${device.productName} (type=$typeName, id=${device.id})")

            // Prefer TYPE_BUILTIN_MIC (phone's main microphone)
            if (device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                Log.i(TAG, "🎤 Found built-in microphone: ${device.productName}")
                return device
            }
        }

        // If no built-in mic found, return first non-Bluetooth device
        for (device in devices) {
            if (device.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO &&
                device.type != AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                Log.i(TAG, "🎤 Using fallback device: ${device.productName}")
                return device
            }
        }

        Log.w(TAG, "🎤 No suitable microphone found!")
        return null
    }

    /**
     * Log which audio device is actually being used for recording.
     */
    private fun logAudioRecordingDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val routedDevice = audioRecord?.routedDevice
            if (routedDevice != null) {
                Log.i(TAG, "🎤 RECORDING DEVICE: ${routedDevice.productName} (type=${getDeviceTypeName(routedDevice.type)})")
            } else {
                Log.w(TAG, "🎤 RECORDING DEVICE: Unknown (routedDevice is null)")
            }
        }
    }

    /**
     * Get human-readable name for audio device type.
     */
    private fun getDeviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "BUILTIN_MIC"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        else -> "UNKNOWN($type)"
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
     * Check if KWS has a captured recording available for debug playback.
     */
    fun hasRecordingForPlayback(): Boolean {
        val data = lastRecordingData
        return data != null && data.isNotEmpty()
    }

    /**
     * Play back the last captured KWS recording for debugging.
     */
    suspend fun playLastRecording(onComplete: () -> Unit = {}) = withContext(Dispatchers.IO) {
        val audioData = lastRecordingData
        if (audioData == null || audioData.isEmpty()) {
            Log.w(TAG, "🔊 No KWS recording to play back")
            withContext(Dispatchers.Main) { onComplete() }
            return@withContext
        }

        try {
            Log.i(TAG, "🔊 Playing back KWS recording (${audioData.size} samples, ${audioData.size / SAMPLE_RATE.toDouble()}s)")
            stopDebugPlayback()

            val pcmData = ShortArray(audioData.size)
            for (i in audioData.indices) {
                val sample = audioData[i].coerceIn(-1f, 1f)
                pcmData[i] = (sample * Short.MAX_VALUE).toInt().toShort()
            }

            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            debugAudioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize.coerceAtLeast(pcmData.size * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            debugAudioTrack?.write(pcmData, 0, pcmData.size)
            debugAudioTrack?.play()

            val durationMs = (audioData.size * 1000L) / SAMPLE_RATE
            Thread.sleep(durationMs + 100)
            stopDebugPlayback()

            withContext(Dispatchers.Main) { onComplete() }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing back KWS recording", e)
            stopDebugPlayback()
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    private fun stopDebugPlayback() {
        try {
            debugAudioTrack?.stop()
            debugAudioTrack?.release()
            debugAudioTrack = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping KWS debug playback", e)
        }
    }

    /**
     * Release resources.
     */
    fun release() {
        stopListening()
        stopDebugPlayback()
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
