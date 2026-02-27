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
import android.util.Log
import androidx.core.content.ContextCompat
import com.whispercpp.media.decodeWaveFile
import com.whispercpp.recorder.Recorder
import com.whispercpp.whisper.WhisperContext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for offline voice recognition using whisper.cpp (native C++ implementation).
 * Provides yes/no detection for hands-free lesson completion.
 *
 * Uses GGML-based whisper.cpp for fast, accurate transcription.
 */
@Singleton
class WhisperVoiceService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WhisperVoiceService"

        // Model file path (in assets/)
        private const val MODEL_ASSET_PATH = "models/ggml-tiny.en.bin"

        // Recording file
        private const val RECORDING_FILE = "voice_completion.wav"

        // Default listening duration
        const val DEFAULT_LISTENING_DURATION_MS = 5000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // whisper.cpp context
    private var whisperContext: WhisperContext? = null
    private var isModelLoaded = false

    // Recorder
    private var recorder: Recorder? = null
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
        fun onAmplitudeUpdate(amplitude: Float) {} // Default empty implementation
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
            Log.i(TAG, "Loading whisper.cpp model from: $MODEL_ASSET_PATH")
            Log.i(TAG, "System info: ${WhisperContext.getSystemInfo()}")

            // Load model directly from assets using whisper.cpp
            whisperContext = WhisperContext.createContextFromAsset(
                context.assets,
                MODEL_ASSET_PATH
            )

            // Initialize recorder
            recorder = Recorder()

            isModelLoaded = true
            Log.i(TAG, "whisper.cpp model loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading whisper.cpp model", e)
            isModelLoaded = false
            false
        }
    }

    /**
     * Check if the model is ready for inference.
     */
    fun isModelReady(): Boolean = isModelLoaded && whisperContext != null

    private fun getRecordingFile(): File {
        return File(context.cacheDir, RECORDING_FILE)
    }

    /**
     * Start listening for voice input and perform recognition.
     *
     * @param durationMs Maximum listening duration in milliseconds
     * @param callback Callback for recognition results
     */
    suspend fun startListening(
        durationMs: Long = DEFAULT_LISTENING_DURATION_MS,
        callback: VoiceRecognitionCallback
    ) = withContext(Dispatchers.Main) {
        // Check model is loaded
        if (!isModelReady()) {
            Log.e(TAG, "Model not loaded, attempting to load...")
            callback.onStatusUpdate("Loading speech model...")
            val loaded = withContext(Dispatchers.IO) { loadModel() }
            if (!loaded) {
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

            // Record audio for the specified duration
            val recordingFile = getRecordingFile()
            Log.i(TAG, "Starting recording to: ${recordingFile.absolutePath} for ${durationMs}ms")

            // Start recording with amplitude callback
            isRecording = true
            recorder?.startRecordingWithAmplitude(
                recordingFile,
                onError = { error ->
                    Log.e(TAG, "Recording error", error)
                    scope.launch(Dispatchers.Main) {
                        isRecording = false
                        callback.onResult(VoiceResult.Error("Recording failed: ${error.message}"))
                    }
                },
                onAmplitude = { amplitude ->
                    // Forward amplitude to callback on main thread
                    scope.launch(Dispatchers.Main) {
                        callback.onAmplitudeUpdate(amplitude)
                    }
                }
            )

            // Wait for the specified duration
            delay(durationMs)

            // Stop recording
            isRecording = false
            recorder?.stopRecording()
            Log.i(TAG, "Recording stopped after ${durationMs}ms")

            callback.onStatusUpdate("Processing...")

            // Transcribe
            val transcription = withContext(Dispatchers.IO) {
                transcribeAudio(recordingFile)
            }

            callback.onListeningStopped()

            // Parse result
            val result = parseYesNo(transcription)
            Log.i(TAG, "Transcription: '$transcription' -> $result")

            callback.onResult(result)

        } catch (e: Exception) {
            Log.e(TAG, "Error during voice recognition", e)
            callback.onListeningStopped()
            callback.onResult(VoiceResult.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Transcribe audio file using whisper.cpp.
     */
    private suspend fun transcribeAudio(audioFile: File): String = withContext(Dispatchers.IO) {
        try {
            if (!audioFile.exists() || audioFile.length() == 0L) {
                Log.e(TAG, "Audio file missing or empty")
                return@withContext ""
            }

            Log.i(TAG, "Transcribing file: ${audioFile.absolutePath} (${audioFile.length()} bytes)")

            // Decode WAV to float array
            val audioData = decodeWaveFile(audioFile)
            Log.i(TAG, "Audio decoded: ${audioData.size} samples (${audioData.size / 16000.0}s)")

            // Transcribe using whisper.cpp
            val result = whisperContext?.transcribeData(audioData, printTimestamp = false)
                ?.trim()
                ?.replace(Regex("^\\[.*?\\]\\s*"), "") // Remove timestamp prefixes
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?: ""

            Log.i(TAG, "Transcription result: '$result'")

            // Cleanup audio file
            audioFile.delete()

            result
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
        scope.launch {
            try {
                recorder?.stopRecording()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping recorder", e)
            }
        }
    }

    /**
     * Release resources.
     */
    fun release() {
        stopListening()
        scope.launch {
            try {
                whisperContext?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing whisper context", e)
            }
            whisperContext = null
            recorder = null
            isModelLoaded = false
        }
    }
}
