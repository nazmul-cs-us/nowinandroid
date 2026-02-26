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

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Manages voice-based lesson completion flow.
 * Plays voice prompts using offline TTS (Sherpa-ONNX/Coqui VITS),
 * listens for yes/no responses using Whisper,
 * and triggers lesson completion callbacks.
 */
@Singleton
class VoiceCompletionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val whisperService: WhisperVoiceService,
    private val sherpaOnnxTts: SherpaOnnxTtsService
) {
    companion object {
        private const val TAG = "VoiceCompletionManager"

        // Configuration
        const val LISTENING_DURATION_MS = 5000L
        const val PROMPT_DELAY_MS = 500L
        const val UTTERANCE_ID_PROMPT = "voice_completion_prompt"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // TTS for voice prompts
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    // State
    private var isPromptInProgress = false

    /**
     * Prompt the user for voice-based lesson completion.
     *
     * @param courseId The course ID
     * @param lessonId The lesson ID
     * @param lessonTitle The lesson title (for voice prompt)
     * @param onComplete Callback when user says "yes"
     * @param onSkipped Callback when user says "no" or timeout
     * @param onError Callback for errors
     */
    fun promptForCompletion(
        courseId: String,
        lessonId: String,
        lessonTitle: String,
        onComplete: () -> Unit,
        onSkipped: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (isPromptInProgress) {
            Log.w(TAG, "Voice completion already in progress")
            return
        }

        isPromptInProgress = true
        Log.i(TAG, "Starting voice completion prompt for: $lessonTitle")

        scope.launch {
            try {
                // Play voice prompt
                val promptPlayed = playVoicePrompt(lessonTitle)
                if (!promptPlayed) {
                    Log.w(TAG, "Failed to play voice prompt, using fallback")
                }

                // Small delay after prompt
                delay(PROMPT_DELAY_MS)

                // Start listening
                Log.d(TAG, "Starting voice listening...")
                listenForResponse(
                    onYes = {
                        Log.i(TAG, "User said YES - marking lesson complete")
                        playSuccessSound()
                        onComplete()
                        isPromptInProgress = false
                    },
                    onNo = {
                        Log.i(TAG, "User said NO - skipping completion")
                        playSkipSound()
                        onSkipped()
                        isPromptInProgress = false
                    },
                    onError = { error ->
                        Log.e(TAG, "Voice recognition error: $error")
                        onError(error)
                        isPromptInProgress = false
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error in voice completion flow", e)
                onError(e.message ?: "Unknown error")
                isPromptInProgress = false
            }
        }
    }

    /**
     * Play the voice prompt asking user to say yes or no.
     * Uses Sherpa-ONNX offline TTS (Coqui VITS) as primary,
     * falls back to Android TTS if unavailable.
     */
    private suspend fun playVoicePrompt(lessonTitle: String): Boolean {
        val promptText = "Say YES to mark this lesson complete, or NO to skip."

        // Try Sherpa-ONNX offline TTS first
        return try {
            Log.d(TAG, "Attempting Sherpa-ONNX offline TTS...")
            val success = sherpaOnnxTts.speak(promptText)
            if (success) {
                Log.i(TAG, "Successfully played prompt with Sherpa-ONNX TTS")
                return true
            }
            Log.w(TAG, "Sherpa-ONNX TTS failed, falling back to Android TTS")
            playWithAndroidTts(promptText)
        } catch (e: Exception) {
            Log.w(TAG, "Sherpa-ONNX TTS error, falling back to Android TTS", e)
            playWithAndroidTts(promptText)
        }
    }

    /**
     * Fallback to Android system TTS.
     */
    private suspend fun playWithAndroidTts(promptText: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            try {
                // Initialize TTS if needed
                if (textToSpeech == null) {
                    textToSpeech = TextToSpeech(context) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            isTtsInitialized = true
                            textToSpeech?.language = Locale.US

                            // Speak the prompt
                            speakWithAndroidTts(promptText) { success ->
                                if (continuation.isActive) {
                                    continuation.resume(success)
                                }
                            }
                        } else {
                            Log.e(TAG, "Android TTS initialization failed")
                            if (continuation.isActive) {
                                continuation.resume(false)
                            }
                        }
                    }
                } else if (isTtsInitialized) {
                    speakWithAndroidTts(promptText) { success ->
                        if (continuation.isActive) {
                            continuation.resume(success)
                        }
                    }
                } else {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }

                continuation.invokeOnCancellation {
                    textToSpeech?.stop()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error playing voice prompt with Android TTS", e)
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }
    }

    /**
     * Speak using Android system TTS (fallback).
     */
    private fun speakWithAndroidTts(promptText: String, onComplete: (Boolean) -> Unit) {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS prompt started")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS prompt completed")
                onComplete(true)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS prompt error")
                onComplete(false)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS prompt error: $errorCode")
                onComplete(false)
            }
        })

        textToSpeech?.speak(
            promptText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            UTTERANCE_ID_PROMPT
        )
    }

    /**
     * Listen for user's yes/no response using Whisper.
     */
    private suspend fun listenForResponse(
        onYes: () -> Unit,
        onNo: () -> Unit,
        onError: (String) -> Unit
    ) {
        whisperService.startListening(
            durationMs = LISTENING_DURATION_MS,
            callback = object : WhisperVoiceService.VoiceRecognitionCallback {
                override fun onResult(result: WhisperVoiceService.VoiceResult) {
                    when (result) {
                        is WhisperVoiceService.VoiceResult.Yes -> onYes()
                        is WhisperVoiceService.VoiceResult.No -> onNo()
                        is WhisperVoiceService.VoiceResult.Timeout -> {
                            Log.d(TAG, "No speech detected (timeout)")
                            onNo() // Treat timeout as skip
                        }
                        is WhisperVoiceService.VoiceResult.Unrecognized -> {
                            Log.d(TAG, "Unrecognized speech: ${result.text}")
                            speakUnrecognizedFeedback()
                            onNo() // Treat unrecognized as skip
                        }
                        is WhisperVoiceService.VoiceResult.Error -> {
                            onError(result.message)
                        }
                    }
                }

                override fun onListeningStarted() {
                    Log.d(TAG, "Voice listening started")
                }

                override fun onListeningStopped() {
                    Log.d(TAG, "Voice listening stopped")
                }

                override fun onStatusUpdate(message: String) {
                    Log.d(TAG, "Voice status: $message")
                }
            }
        )
    }

    /**
     * Speak feedback when speech is not recognized.
     * Uses Sherpa-ONNX offline TTS with Android TTS fallback.
     */
    private fun speakUnrecognizedFeedback() {
        scope.launch {
            val success = try {
                sherpaOnnxTts.speak("Sorry, I didn't understand. Skipping.")
            } catch (e: Exception) {
                false
            }
            if (!success) {
                textToSpeech?.speak(
                    "Sorry, I didn't understand. Skipping.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "unrecognized_feedback"
                )
            }
        }
    }

    /**
     * Play success feedback when lesson is marked complete.
     * Uses Sherpa-ONNX offline TTS with Android TTS fallback.
     */
    private fun playSuccessSound() {
        scope.launch {
            val success = try {
                sherpaOnnxTts.speak("Lesson marked as complete.")
            } catch (e: Exception) {
                false
            }
            if (!success) {
                textToSpeech?.speak(
                    "Lesson marked as complete.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "success_feedback"
                )
            }
        }
    }

    /**
     * Play skip feedback when lesson is skipped.
     * Silently skips without audio feedback.
     */
    private fun playSkipSound() {
        // No audio feedback for skip - silent skip
        Log.d(TAG, "Lesson skipped - no audio feedback")
    }

    /**
     * Cancel any ongoing voice completion prompt.
     */
    fun cancel() {
        isPromptInProgress = false
        textToSpeech?.stop()
        sherpaOnnxTts.stopSpeaking()
        whisperService.stopListening()
    }

    /**
     * Release all resources.
     */
    fun release() {
        cancel()
        textToSpeech?.shutdown()
        textToSpeech = null
        isTtsInitialized = false
        sherpaOnnxTts.release()
    }

    /**
     * Pre-initialize the offline TTS engine.
     * Call this early to avoid delay on first use.
     */
    fun preInitialize() {
        scope.launch {
            try {
                Log.i(TAG, "Pre-initializing Sherpa-ONNX TTS...")
                sherpaOnnxTts.initialize()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to pre-initialize Sherpa-ONNX TTS", e)
            }
        }
    }
}
