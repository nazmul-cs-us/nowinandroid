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
import com.whispertflite.asr.Whisper
import com.whispertflite.utils.WaveUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for offline voice recognition using Whisper TFLite model.
 * Provides yes/no detection for hands-free lesson completion.
 */
@Singleton
class WhisperVoiceService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WhisperVoiceService"

        // Audio recording configuration
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BYTES_PER_SAMPLE = 2

        // Model file paths (in assets/whisper/)
        private const val MODEL_FILE = "whisper/whisper-tiny.en.tflite"
        private const val VOCAB_FILE = "whisper/filters_vocab_en.bin"

        // Default listening duration
        const val DEFAULT_LISTENING_DURATION_MS = 5000L
    }

    // Whisper instance
    private var whisper: Whisper? = null
    private var isModelLoaded = false

    // Recording state
    private var audioRecord: AudioRecord? = null
    private var isRecording = false

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
     * Load the Whisper model from assets.
     * Should be called once before using recognition.
     */
    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        if (isModelLoaded) {
            Log.d(TAG, "Model already loaded")
            return@withContext true
        }

        try {
            Log.i(TAG, "Loading Whisper model...")

            // Copy model files from assets to cache directory
            val modelFile = copyAssetToCache(MODEL_FILE, "whisper-tiny.en.tflite")
            val vocabFile = copyAssetToCache(VOCAB_FILE, "filters_vocab_en.bin")

            if (modelFile == null || vocabFile == null) {
                Log.e(TAG, "Failed to copy model files from assets")
                return@withContext false
            }

            // Initialize Whisper
            whisper = Whisper(context).apply {
                loadModel(modelFile, vocabFile, false) // false = English only
            }

            isModelLoaded = true
            Log.i(TAG, "Whisper model loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Whisper model", e)
            isModelLoaded = false
            false
        }
    }

    /**
     * Copy an asset file to the cache directory for loading.
     */
    private fun copyAssetToCache(assetPath: String, fileName: String): File? {
        return try {
            val cacheFile = File(context.cacheDir, fileName)

            // Check if already cached and recent
            if (cacheFile.exists() && cacheFile.length() > 0) {
                Log.d(TAG, "Using cached file: ${cacheFile.absolutePath}")
                return cacheFile
            }

            // Copy from assets
            context.assets.open(assetPath).use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Copied asset to cache: ${cacheFile.absolutePath}")
            cacheFile
        } catch (e: Exception) {
            Log.e(TAG, "Error copying asset: $assetPath", e)
            null
        }
    }

    /**
     * Check if the model is ready for inference.
     */
    fun isModelReady(): Boolean = isModelLoaded && whisper != null

    /**
     * Start listening for voice input and perform recognition.
     *
     * @param durationMs Maximum listening duration in milliseconds
     * @param callback Callback for recognition results
     */
    suspend fun startListening(
        durationMs: Long = DEFAULT_LISTENING_DURATION_MS,
        callback: VoiceRecognitionCallback
    ) = withContext(Dispatchers.IO) {
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

        try {
            callback.onListeningStarted()
            callback.onStatusUpdate("Listening...")

            // Record audio
            val audioFile = recordAudio(durationMs)
            if (audioFile == null) {
                callback.onResult(VoiceResult.Error("Failed to record audio"))
                callback.onListeningStopped()
                return@withContext
            }

            callback.onStatusUpdate("Processing...")

            // Transcribe
            val transcription = transcribeAudio(audioFile)
            callback.onListeningStopped()

            // Parse result
            val result = parseYesNo(transcription)
            Log.i(TAG, "Transcription: '$transcription' -> $result")

            callback.onResult(result)

            // Cleanup audio file
            audioFile.delete()

        } catch (e: Exception) {
            Log.e(TAG, "Error during voice recognition", e)
            callback.onListeningStopped()
            callback.onResult(VoiceResult.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Record audio for the specified duration.
     */
    private suspend fun recordAudio(durationMs: Long): File? = withContext(Dispatchers.IO) {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size: $bufferSize")
                return@withContext null
            }

            @Suppress("MissingPermission")
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized")
                audioRecord?.release()
                audioRecord = null
                return@withContext null
            }

            // Calculate total bytes to record
            val totalSamples = (SAMPLE_RATE * durationMs / 1000).toInt()
            val totalBytes = totalSamples * BYTES_PER_SAMPLE
            val audioData = ByteArray(totalBytes)

            // Start recording
            audioRecord?.startRecording()
            isRecording = true
            Log.d(TAG, "Started recording for ${durationMs}ms")

            // Read audio data
            var bytesRead = 0
            val buffer = ByteArray(bufferSize)

            val startTime = System.currentTimeMillis()
            while (isRecording && bytesRead < totalBytes) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= durationMs) break

                val read = audioRecord?.read(buffer, 0, minOf(buffer.size, totalBytes - bytesRead)) ?: 0
                if (read > 0) {
                    System.arraycopy(buffer, 0, audioData, bytesRead, read)
                    bytesRead += read
                } else if (read < 0) {
                    Log.e(TAG, "AudioRecord read error: $read")
                    break
                }
            }

            // Stop recording
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            isRecording = false

            Log.d(TAG, "Recording complete: $bytesRead bytes")

            // Save as WAV file
            val wavFile = File(context.cacheDir, "voice_input.wav")
            WaveUtil.createWaveFile(wavFile.absolutePath, audioData, SAMPLE_RATE, 1, BYTES_PER_SAMPLE)

            wavFile
        } catch (e: Exception) {
            Log.e(TAG, "Error recording audio", e)
            audioRecord?.release()
            audioRecord = null
            isRecording = false
            null
        }
    }

    /**
     * Transcribe audio file using Whisper.
     */
    private suspend fun transcribeAudio(audioFile: File): String = withContext(Dispatchers.IO) {
        try {
            // Use atomic reference for thread-safe access
            val result = java.util.concurrent.atomic.AtomicReference("")
            val resultReceived = java.util.concurrent.atomic.AtomicBoolean(false)

            whisper?.apply {
                setFilePath(audioFile.absolutePath)
                setAction(Whisper.ACTION_TRANSCRIBE)

                setListener(object : Whisper.WhisperListener {
                    override fun onUpdateReceived(message: String?) {
                        Log.d(TAG, "Whisper update: $message")
                    }

                    override fun onResultReceived(transcription: String?) {
                        result.set(transcription ?: "")
                        resultReceived.set(true)
                        Log.i(TAG, "Whisper result received: '${result.get()}'")
                    }
                })

                start()

                // Wait for either result callback OR timeout (max 60 seconds)
                var waitTime = 0L
                while (!resultReceived.get() && waitTime < 60000) {
                    delay(100)
                    waitTime += 100
                }
                Log.d(TAG, "Transcription wait time: ${waitTime}ms, resultReceived=${resultReceived.get()}")

                // Additional wait after isInProgress completes to catch late callbacks
                if (!resultReceived.get() && !isInProgress) {
                    Log.d(TAG, "Waiting 2s more for late callback...")
                    delay(2000)
                }

                stop()
            }

            Log.i(TAG, "Final transcription result: '${result.get()}'")
            result.get().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error transcribing audio", e)
            ""
        }
    }

    /**
     * Parse transcription text to detect yes/no intent.
     * Uses fuzzy matching for various ways to say yes or no.
     */
    private fun parseYesNo(transcription: String): VoiceResult {
        val text = transcription.lowercase().trim()

        if (text.isBlank()) {
            return VoiceResult.Timeout
        }

        // Check for yes variants
        val yesPatterns = listOf(
            "yes", "yeah", "yep", "yup", "sure", "okay", "ok",
            "affirmative", "correct", "right", "done", "complete",
            "mark it", "mark complete", "finished"
        )

        // Check for no variants
        val noPatterns = listOf(
            "no", "nope", "nah", "not yet", "skip", "later",
            "negative", "cancel", "don't", "stop"
        )

        // Check yes patterns
        for (pattern in yesPatterns) {
            if (text.contains(pattern)) {
                return VoiceResult.Yes
            }
        }

        // Check no patterns
        for (pattern in noPatterns) {
            if (text.contains(pattern)) {
                return VoiceResult.No
            }
        }

        // Unrecognized
        return VoiceResult.Unrecognized(transcription)
    }

    /**
     * Stop any ongoing recording.
     */
    fun stopListening() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    /**
     * Release resources.
     */
    fun release() {
        stopListening()
        whisper?.unloadModel()
        whisper = null
        isModelLoaded = false
    }
}
