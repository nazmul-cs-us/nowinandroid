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
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Voice Search Service using Android's SpeechRecognizer
 *
 * This provides fast, accurate voice-to-text for search queries.
 * Uses Google's cloud-based speech recognition for best results.
 */
class VoiceSearchService(
    private val context: Context
) {
    companion object {
        private const val TAG = "VoiceSearchService"
    }

    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    // Normalized microphone level (0..1) derived from onRmsChanged while
    // listening. Drives the live voice-wave visualization in the search bar.
    private val _voiceLevel = MutableStateFlow(0f)
    val voiceLevel: StateFlow<Float> = _voiceLevel.asStateFlow()

    private val _recognizedText = MutableStateFlow<String?>(null)
    val recognizedText: StateFlow<String?> = _recognizedText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Voice search result
     */
    sealed class VoiceSearchResult {
        data class Success(val text: String) : VoiceSearchResult()
        data class Error(val message: String) : VoiceSearchResult()
        object Cancelled : VoiceSearchResult()
    }

    /**
     * Check if speech recognition is available
     */
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
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
     * Start listening for voice input
     */
    fun startListening(
        onResult: (VoiceSearchResult) -> Unit
    ) {
        if (!isAvailable()) {
            Log.e(TAG, "Speech recognition not available")
            onResult(VoiceSearchResult.Error("Speech recognition not available on this device"))
            return
        }

        if (!hasPermission()) {
            Log.e(TAG, "Microphone permission not granted")
            onResult(VoiceSearchResult.Error("Microphone permission required"))
            return
        }

        if (_isListening.value) {
            Log.w(TAG, "Already listening")
            return
        }

        try {
            _isListening.value = true
            _error.value = null
            _recognizedText.value = null

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Beginning of speech")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // SpeechRecognizer reports roughly -2..10 dB; normalize to 0..1.
                    _voiceLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // Buffer received
                }

                override fun onEndOfSpeech() {
                    Log.d(TAG, "End of speech")
                }

                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Unknown error ($error)"
                    }
                    Log.e(TAG, "Speech recognition error: $errorMessage")
                    _isListening.value = false
                    _error.value = errorMessage

                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        onResult(VoiceSearchResult.Cancelled)
                    } else {
                        onResult(VoiceSearchResult.Error(errorMessage))
                    }

                    cleanup()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""

                    Log.i(TAG, "Speech recognized: $text")
                    _isListening.value = false
                    _recognizedText.value = text

                    if (text.isNotBlank()) {
                        onResult(VoiceSearchResult.Success(text))
                    } else {
                        onResult(VoiceSearchResult.Cancelled)
                    }

                    cleanup()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    Log.d(TAG, "Partial result: '$text'")
                    if (text.isNotBlank()) {
                        _recognizedText.value = text
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Event received
                }
            })

            // Create intent for speech recognition
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                // Set language to English (US) - can be made configurable
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
                // More lenient speech detection
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            }

            speechRecognizer?.startListening(intent)
            Log.i(TAG, "Started listening for voice input")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            _isListening.value = false
            onResult(VoiceSearchResult.Error(e.message ?: "Unknown error"))
            cleanup()
        }
    }

    /**
     * Stop listening
     */
    fun stopListening() {
        _isListening.value = false
        speechRecognizer?.stopListening()
        cleanup()
    }

    /**
     * Cancel listening
     */
    fun cancel() {
        _isListening.value = false
        speechRecognizer?.cancel()
        cleanup()
    }

    private fun cleanup() {
        _voiceLevel.value = 0f
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer", e)
        }
    }

    /**
     * Release resources
     */
    fun release() {
        cancel()
    }
}
