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
import com.whispertflite.asr.Recorder
import com.whispertflite.asr.Whisper
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
import java.io.FileOutputStream

/**
 * Whisper Voice Service using OpenAI's Whisper model via TensorFlow Lite
 *
 * This provides offline, on-device speech-to-text for search queries.
 * Uses the whisper-tiny.en model for fast, accurate English transcription.
 *
 * Benefits over cloud-based recognition:
 * - Works completely offline
 * - Privacy-preserving (audio never leaves device)
 * - No network latency
 * - Works in airplane mode
 */
class WhisperVoiceService(
    private val context: Context
) {
    companion object {
        private const val TAG = "WhisperVoiceService"
        private const val MODEL_FILE = "whisper-tiny.en.tflite"
        private const val VOCAB_FILE = "filters_vocab_en.bin"
        private const val ASSETS_WHISPER_DIR = "whisper"
        private const val RECORDING_FILE = "whisper_recording.wav"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var initJob: Job? = null

    private var whisper: Whisper? = null
    private var recorder: Recorder? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

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
     * This copies model files from assets and loads the model.
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
            // Copy model files from assets to files directory
            val modelFile = copyAssetToFiles("$ASSETS_WHISPER_DIR/$MODEL_FILE", MODEL_FILE)
            val vocabFile = copyAssetToFiles("$ASSETS_WHISPER_DIR/$VOCAB_FILE", VOCAB_FILE)

            if (!modelFile.exists() || !vocabFile.exists()) {
                Log.e(TAG, "Model files not found after copy")
                return false
            }

            Log.i(TAG, "Model file: ${modelFile.absolutePath} (${modelFile.length() / 1024}KB)")
            Log.i(TAG, "Vocab file: ${vocabFile.absolutePath} (${vocabFile.length() / 1024}KB)")

            // Initialize Whisper engine
            whisper = Whisper(context).apply {
                setListener(object : Whisper.WhisperListener {
                    override fun onUpdateReceived(message: String?) {
                        Log.d(TAG, "Whisper update: $message")
                        _statusMessage.value = message
                    }

                    override fun onResultReceived(result: String?) {
                        Log.i(TAG, "Whisper result: $result")
                        // Only handle results when transcribing (not during recording)
                        if (_isTranscribing.value) {
                            handleTranscriptionResult(result)
                        }
                    }
                })

                // Load the model (isMultilingual = false for English-only model)
                loadModel(modelFile, vocabFile, false)
                setAction(Whisper.ACTION_TRANSCRIBE)
            }

            // Initialize recorder
            recorder = Recorder(context).apply {
                setFilePath(getRecordingFilePath())
                setListener(object : Recorder.RecorderListener {
                    override fun onUpdateReceived(message: String?) {
                        Log.d(TAG, "Recorder update: $message")
                        when (message) {
                            Recorder.MSG_RECORDING -> {
                                _statusMessage.value = "Listening..."
                            }
                            Recorder.MSG_RECORDING_DONE -> {
                                _statusMessage.value = "Processing..."
                                _isListening.value = false
                                startTranscription()
                            }
                            else -> {
                                _statusMessage.value = message
                            }
                        }
                    }

                    override fun onDataReceived(samples: FloatArray?) {
                        // We use file-based transcription, not buffer-based
                        // Do NOT call writeBuffer() here as it conflicts with file transcription
                    }
                })
            }

            Log.i(TAG, "Whisper initialized successfully")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error in initializeWhisper", e)
            return false
        }
    }

    private fun copyAssetToFiles(assetPath: String, fileName: String): File {
        val outFile = File(context.filesDir, fileName)

        // Only copy if file doesn't exist or is empty
        if (outFile.exists() && outFile.length() > 0) {
            Log.d(TAG, "File already exists: ${outFile.absolutePath}")
            return outFile
        }

        context.assets.open(assetPath).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }

        Log.i(TAG, "Copied $assetPath to ${outFile.absolutePath}")
        return outFile
    }

    private fun getRecordingFilePath(): String {
        return File(context.filesDir, RECORDING_FILE).absolutePath
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
     * Records audio and transcribes using the local Whisper model.
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

        // Start recording
        recorder?.setFilePath(getRecordingFilePath())
        recorder?.start()

        Log.i(TAG, "Started listening for voice input (Whisper)")
    }

    /**
     * Stop listening and start transcription
     */
    fun stopListening() {
        if (_isListening.value) {
            _statusMessage.value = "Stopping..."
            // Run on background thread because recorder.stop() blocks
            scope.launch(Dispatchers.IO) {
                try {
                    recorder?.stop()
                    // Recording done callback will trigger transcription
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping recorder", e)
                }
            }
        }
    }

    private fun startTranscription() {
        _isTranscribing.value = true
        _statusMessage.value = "Transcribing..."

        whisper?.setFilePath(getRecordingFilePath())
        whisper?.start()
    }

    private fun handleTranscriptionResult(result: String?) {
        val text = result?.trim() ?: ""
        val callback = currentCallback
        currentCallback = null

        // Invoke callback on main thread for UI updates
        scope.launch(Dispatchers.Main) {
            _isTranscribing.value = false
            _recognizedText.value = text

            if (text.isNotBlank()) {
                Log.i(TAG, "Transcription result: $text")
                _statusMessage.value = "Done"
                Log.d(TAG, "Invoking callback with result: $text")
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
        _isListening.value = false
        _isTranscribing.value = false

        recorder?.stop()
        whisper?.stop()

        currentCallback?.invoke(VoiceSearchService.VoiceSearchResult.Cancelled)
        currentCallback = null
    }

    /**
     * Release all resources
     */
    fun release() {
        cancel()
        initJob?.cancel()
        whisper?.unloadModel()
        whisper = null
        recorder = null
        _isInitialized.value = false
    }
}
