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

package com.starception.submission.feature.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.whispercpp.media.decodeWaveFile
import com.whispercpp.recorder.Recorder
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Whisper Voice Service using whisper.cpp (native C++ implementation)
 *
 * This provides offline, on-device speech-to-text for search queries.
 * Uses the whisper.cpp library with GGML models for fast, accurate transcription.
 *
 * Benefits over cloud-based recognition:
 * - Works completely offline
 * - Privacy-preserving (audio never leaves device)
 * - No network latency
 * - Works in airplane mode
 * - Native C++ performance
 */
class WhisperVoiceService(
    private val context: Context
) {
    companion object {
        private const val TAG = "WhisperVoiceService"
        private const val MODEL_ASSET_PATH = "models/ggml-tiny.en.bin"
        private const val RECORDING_FILE = "whisper_recording.wav"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var initJob: Job? = null
    private var recordJob: Job? = null

    private var whisperContext: WhisperContext? = null
    private var recorder: Recorder? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    // Normalized microphone amplitude (0..1) while recording, ~60 updates/sec.
    // Drives the live voice-wave visualization in the search bar.
    private val _voiceLevel = MutableStateFlow(0f)
    val voiceLevel: StateFlow<Float> = _voiceLevel.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private val _recognizedText = MutableStateFlow<String?>(null)
    val recognizedText: StateFlow<String?> = _recognizedText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var currentCallback: ((VoiceSearchService.VoiceSearchResult) -> Unit)? = null

    /**
     * Initialize the Whisper model
     * This loads the GGML model from assets.
     * Should be called before starting transcription.
     */
    fun initialize(onComplete: ((Boolean) -> Unit)? = null) {
        if (_isInitialized.value) {
            onComplete?.invoke(true)
            return
        }

        if (_isInitializing.value) {
            return
        }

        initJob = scope.launch {
            _isInitializing.value = true
            _statusMessage.value = "Loading Whisper model..."

            try {
                val success = withContext(Dispatchers.IO) {
                    initializeWhisper()
                }

                _isInitialized.value = success
                _statusMessage.value = if (success) "Whisper ready" else "Failed to load model"
                onComplete?.invoke(success)

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Whisper", e)
                _error.value = "Failed to initialize: ${e.message}"
                _statusMessage.value = "Initialization failed"
                onComplete?.invoke(false)
            } finally {
                _isInitializing.value = false
            }
        }
    }

    private fun initializeWhisper(): Boolean {
        try {
            Log.i(TAG, "Initializing whisper.cpp from asset: $MODEL_ASSET_PATH")
            Log.i(TAG, "System info: ${WhisperContext.getSystemInfo()}")

            // Load model directly from assets using whisper.cpp
            whisperContext = WhisperContext.createContextFromAsset(
                context.assets,
                MODEL_ASSET_PATH
            )

            // Initialize recorder
            recorder = Recorder()

            Log.i(TAG, "whisper.cpp initialized successfully")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error in initializeWhisper", e)
            return false
        }
    }

    private fun getRecordingFile(): File {
        return File(context.filesDir, RECORDING_FILE)
    }

    /**
     * Check if microphone permission is granted
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if Whisper is available and initialized
     */
    fun isAvailable(): Boolean {
        return _isInitialized.value
    }

    /**
     * Start listening for voice input using Whisper
     * Records audio and transcribes using the local whisper.cpp model.
     */
    fun startListening(onResult: (VoiceSearchService.VoiceSearchResult) -> Unit) {
        if (!hasPermission()) {
            Log.e(TAG, "Microphone permission not granted")
            onResult(VoiceSearchService.VoiceSearchResult.Error("Microphone permission required"))
            return
        }

        if (!_isInitialized.value) {
            Log.w(TAG, "Whisper not initialized, initializing now...")
            initialize { success ->
                if (success) {
                    startListeningInternal(onResult)
                } else {
                    onResult(VoiceSearchService.VoiceSearchResult.Error("Failed to initialize Whisper"))
                }
            }
            return
        }

        startListeningInternal(onResult)
    }

    private fun startListeningInternal(onResult: (VoiceSearchService.VoiceSearchResult) -> Unit) {
        if (_isListening.value || _isTranscribing.value) {
            Log.w(TAG, "Already listening or transcribing")
            return
        }

        currentCallback = onResult
        _error.value = null
        _recognizedText.value = null
        _isListening.value = true
        _statusMessage.value = "Listening..."

        // Start recording using coroutine-based recorder
        recordJob = scope.launch {
            try {
                val recordingFile = getRecordingFile()
                Log.i(TAG, "Starting recording to: ${recordingFile.absolutePath}")

                recorder?.startRecordingWithAmplitude(
                    recordingFile,
                    onError = { error ->
                        Log.e(TAG, "Recording error", error)
                        scope.launch(Dispatchers.Main) {
                            _isListening.value = false
                            _voiceLevel.value = 0f
                            _error.value = "Recording error: ${error.message}"
                            currentCallback?.invoke(
                                VoiceSearchService.VoiceSearchResult.Error("Recording failed: ${error.message}")
                            )
                            currentCallback = null
                        }
                    },
                    onAmplitude = { amplitude ->
                        _voiceLevel.value = amplitude
                    },
                )

                Log.i(TAG, "Recording started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
                _isListening.value = false
                _error.value = "Failed to start recording: ${e.message}"
                currentCallback?.invoke(
                    VoiceSearchService.VoiceSearchResult.Error("Failed to start recording: ${e.message}")
                )
                currentCallback = null
            }
        }

        Log.i(TAG, "Started listening for voice input (whisper.cpp)")
    }

    /**
     * Stop listening and start transcription
     */
    fun stopListening() {
        if (_isListening.value) {
            _statusMessage.value = "Processing..."

            scope.launch {
                try {
                    // Stop recording
                    recorder?.stopRecording()
                    _isListening.value = false
                    _voiceLevel.value = 0f

                    // Start transcription
                    startTranscription()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping recorder", e)
                    _isListening.value = false
                    _error.value = "Error stopping recording: ${e.message}"
                    currentCallback?.invoke(
                        VoiceSearchService.VoiceSearchResult.Error("Error: ${e.message}")
                    )
                    currentCallback = null
                }
            }
        }
    }

    private suspend fun startTranscription() {
        _isTranscribing.value = true
        _statusMessage.value = "Transcribing..."

        try {
            val recordingFile = getRecordingFile()

            if (!recordingFile.exists() || recordingFile.length() == 0L) {
                Log.e(TAG, "Recording file missing or empty")
                handleTranscriptionResult(null)
                return
            }

            Log.i(TAG, "Transcribing file: ${recordingFile.absolutePath} (${recordingFile.length()} bytes)")

            // Decode WAV to float array
            val audioData = withContext(Dispatchers.IO) {
                decodeWaveFile(recordingFile)
            }

            Log.i(TAG, "Audio decoded: ${audioData.size} samples (${audioData.size / 16000.0}s)")

            // Transcribe using whisper.cpp
            val result = whisperContext?.transcribeData(audioData, printTimestamp = false)

            handleTranscriptionResult(result)

        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription", e)
            _isTranscribing.value = false
            _error.value = "Transcription error: ${e.message}"
            currentCallback?.invoke(
                VoiceSearchService.VoiceSearchResult.Error("Transcription failed: ${e.message}")
            )
            currentCallback = null
        }
    }

    private fun handleTranscriptionResult(result: String?) {
        // Clean up result - remove leading/trailing whitespace and common artifacts
        val text = result?.trim()
            ?.replace(Regex("^\\[.*?\\]\\s*"), "") // Remove timestamp prefixes like [00:00.000 --> 00:03.000]
            ?.replace(Regex("\\s+"), " ") // Normalize whitespace
            ?.trim()
            ?: ""

        val callback = currentCallback
        currentCallback = null

        // Invoke callback on main thread for UI updates
        scope.launch(Dispatchers.Main) {
            _isTranscribing.value = false
            _recognizedText.value = text

            if (text.isNotBlank()) {
                Log.i(TAG, "Transcription result: $text")
                _statusMessage.value = "Done"
                callback?.invoke(VoiceSearchService.VoiceSearchResult.Success(text))
            } else {
                Log.w(TAG, "Empty transcription result")
                _statusMessage.value = "No speech detected"
                callback?.invoke(VoiceSearchService.VoiceSearchResult.Cancelled)
            }
        }
    }

    /**
     * Cancel listening and transcription
     */
    fun cancel() {
        scope.launch {
            _isListening.value = false
            _isTranscribing.value = false

            try {
                recorder?.stopRecording()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping recorder during cancel", e)
            }

            currentCallback?.invoke(VoiceSearchService.VoiceSearchResult.Cancelled)
            currentCallback = null
        }
    }

    /**
     * Release all resources
     */
    fun release() {
        scope.launch {
            cancel()
            initJob?.cancel()
            recordJob?.cancel()

            try {
                whisperContext?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing whisper context", e)
            }

            whisperContext = null
            recorder = null
            _isInitialized.value = false
        }
    }

    /**
     * Test method: Records 3 seconds and transcribes.
     * Use to verify whisper.cpp is working properly.
     */
    fun testMicrophonePlayback() {
        Log.i(TAG, "Test not implemented for whisper.cpp recorder")
        _statusMessage.value = "Test not available"
    }
}
