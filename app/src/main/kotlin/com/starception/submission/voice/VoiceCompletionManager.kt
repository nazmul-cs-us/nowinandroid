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
import com.starception.submission.settings.components.VoiceRecognitionEngine
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
 * listens for yes/no responses using the user-selected engine (Sherpa KWS or Whisper),
 * and triggers lesson completion callbacks.
 */
@Singleton
class VoiceCompletionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val whisperService: WhisperVoiceService,
    private val sherpaKwsService: SherpaOnnxKwsService,
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
                        // Confirmation already spoken in listenForResponse
                        onComplete()
                        isPromptInProgress = false
                    },
                    onNo = {
                        // Confirmation already spoken in listenForResponse
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
     * Uses the user-selected TTS voice from settings.
     */
    private suspend fun playVoicePrompt(lessonTitle: String): Boolean {
        val promptText = "Say YES to mark this lesson complete, or NO to skip."

        // Apply user-selected TTS voice from settings
        applyUserSelectedTtsVoice()

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
     * Get the user-selected voice recognition engine from SharedPreferences.
     */
    private fun getSelectedEngine(): VoiceRecognitionEngine {
        val prefs = context.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)
        val engineName = prefs.getString("voice_engine", VoiceRecognitionEngine.SHERPA_KWS.name)
        return try {
            VoiceRecognitionEngine.valueOf(engineName ?: VoiceRecognitionEngine.SHERPA_KWS.name)
        } catch (e: Exception) {
            VoiceRecognitionEngine.SHERPA_KWS
        }
    }

    /**
     * Apply the user-selected TTS voice from settings.
     * Ensures consistent TTS voice across all prompts and confirmations.
     */
    private fun applyUserSelectedTtsVoice() {
        val ttsPrefs = context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
        val selectedVoiceName = ttsPrefs.getString("selected_voice", null)
        val selectedSpeakerId = ttsPrefs.getInt("selected_speaker_id", 0)

        val voice = if (selectedVoiceName != null) {
            try {
                com.starception.submission.settings.components.TtsVoice.valueOf(selectedVoiceName)
            } catch (e: Exception) {
                com.starception.submission.settings.components.TtsVoice.KOKORO_EN
            }
        } else {
            com.starception.submission.settings.components.TtsVoice.KOKORO_EN
        }

        sherpaOnnxTts.setVoice(voice)
        Log.d(TAG, "🔊 Using TTS voice: ${voice.displayName}, speaker $selectedSpeakerId")
    }

    /**
     * Listen for user's yes/no response using the selected voice recognition engine.
     * Honors the user's selection from Voice Settings (Sherpa KWS or Whisper).
     */
    private suspend fun listenForResponse(
        onYes: () -> Unit,
        onNo: () -> Unit,
        onError: (String) -> Unit
    ) {
        val selectedEngine = getSelectedEngine()
        Log.i(TAG, "🎤 Using voice engine: ${selectedEngine.displayName}")

        when (selectedEngine) {
            VoiceRecognitionEngine.SHERPA_KWS -> {
                listenWithSherpaKws(onYes, onNo, onError)
            }
            VoiceRecognitionEngine.WHISPER -> {
                listenWithWhisper(onYes, onNo, onError)
            }
        }
    }

    /**
     * Listen using Sherpa-ONNX KWS (fast ~100ms keyword spotting)
     */
    private fun listenWithSherpaKws(
        onYes: () -> Unit,
        onNo: () -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            sherpaKwsService.startListening(
                durationMs = LISTENING_DURATION_MS,
                callback = object : SherpaOnnxKwsService.VoiceRecognitionCallback {
                    override fun onResult(result: SherpaOnnxKwsService.VoiceResult) {
                        when (result) {
                            is SherpaOnnxKwsService.VoiceResult.Yes -> {
                                Log.i(TAG, "✅ KWS detected: YES")
                                speakConfirmation("I heard yes. Marking lesson complete.") {
                                    onYes()
                                }
                            }
                            is SherpaOnnxKwsService.VoiceResult.No -> {
                                Log.i(TAG, "❌ KWS detected: NO")
                                speakConfirmation("I heard no. Skipping this lesson.") {
                                    onNo()
                                }
                            }
                            is SherpaOnnxKwsService.VoiceResult.Timeout -> {
                                Log.d(TAG, "⏱️ KWS timeout - no keyword detected")
                                speakConfirmation("No response detected. Skipping.") {
                                    onNo()
                                }
                            }
                            is SherpaOnnxKwsService.VoiceResult.Unrecognized -> {
                                Log.d(TAG, "❓ KWS unrecognized: ${result.text}")
                                speakConfirmation("Sorry, I didn't understand. Skipping.") {
                                    onNo()
                                }
                            }
                            is SherpaOnnxKwsService.VoiceResult.Error -> {
                                Log.e(TAG, "❌ KWS error: ${result.message}")
                                onError(result.message)
                            }
                        }
                    }

                    override fun onListeningStarted() {
                        Log.d(TAG, "KWS listening started")
                    }

                    override fun onListeningStopped() {
                        Log.d(TAG, "KWS listening stopped")
                    }

                    override fun onStatusUpdate(message: String) {
                        Log.d(TAG, "KWS status: $message")
                    }
                }
            )
        }
    }

    /**
     * Listen using Whisper (full transcription ~2s)
     */
    private fun listenWithWhisper(
        onYes: () -> Unit,
        onNo: () -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            whisperService.startListening(
            durationMs = LISTENING_DURATION_MS,
            callback = object : WhisperVoiceService.VoiceRecognitionCallback {
                override fun onResult(result: WhisperVoiceService.VoiceResult) {
                    when (result) {
                        is WhisperVoiceService.VoiceResult.Yes -> {
                            Log.i(TAG, "✅ Whisper recognized: YES")
                            speakConfirmation("I heard yes. Marking lesson complete.") {
                                onYes()
                            }
                        }
                        is WhisperVoiceService.VoiceResult.No -> {
                            Log.i(TAG, "❌ Whisper recognized: NO")
                            speakConfirmation("I heard no. Skipping this lesson.") {
                                onNo()
                            }
                        }
                        is WhisperVoiceService.VoiceResult.Timeout -> {
                            Log.d(TAG, "⏱️ No speech detected (timeout)")
                            speakConfirmation("No response detected. Skipping.") {
                                onNo()
                            }
                        }
                        is WhisperVoiceService.VoiceResult.Unrecognized -> {
                            Log.d(TAG, "❓ Unrecognized speech: ${result.text}")
                            speakConfirmation("Sorry, I didn't understand: ${result.text}. Skipping.") {
                                onNo()
                            }
                        }
                        is WhisperVoiceService.VoiceResult.Error -> {
                            onError(result.message)
                        }
                    }
                }

                override fun onListeningStarted() {
                    Log.d(TAG, "Whisper listening started")
                }

                override fun onListeningStopped() {
                    Log.d(TAG, "Whisper listening stopped")
                }

                override fun onStatusUpdate(message: String) {
                    Log.d(TAG, "Whisper status: $message")
                }
            }
        )
        }
    }

    /**
     * Speak confirmation feedback using offline TTS.
     * Confirms what Whisper heard before taking action.
     * Uses the user-selected TTS voice from settings.
     *
     * @param message The confirmation message to speak
     * @param onComplete Callback after speech completes
     */
    private fun speakConfirmation(message: String, onComplete: () -> Unit) {
        scope.launch {
            Log.i(TAG, "🔊 Speaking confirmation: \"$message\"")

            // Apply user-selected TTS voice from settings
            applyUserSelectedTtsVoice()

            val success = try {
                sherpaOnnxTts.speak(message, onComplete = {
                    onComplete()
                })
            } catch (e: Exception) {
                Log.w(TAG, "Sherpa-ONNX TTS failed, using Android TTS fallback", e)
                false
            }

            if (!success) {
                // Fallback to Android TTS
                speakWithAndroidTtsFallback(message, onComplete)
            }
        }
    }

    /**
     * Fallback to Android system TTS for confirmation.
     */
    private fun speakWithAndroidTtsFallback(message: String, onComplete: () -> Unit) {
        if (textToSpeech == null || !isTtsInitialized) {
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsInitialized = true
                    textToSpeech?.language = Locale.US
                    speakAndroidTtsWithCallback(message, onComplete)
                } else {
                    Log.e(TAG, "Android TTS init failed")
                    onComplete() // Continue even if TTS fails
                }
            }
        } else {
            speakAndroidTtsWithCallback(message, onComplete)
        }
    }

    /**
     * Speak with Android TTS and trigger callback on completion.
     */
    private fun speakAndroidTtsWithCallback(message: String, onComplete: () -> Unit) {
        val utteranceId = "confirmation_${System.currentTimeMillis()}"

        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                Log.d(TAG, "Android TTS started: $id")
            }

            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    Log.d(TAG, "Android TTS completed: $id")
                    onComplete()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    Log.e(TAG, "Android TTS error: $id")
                    onComplete()
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId) {
                    Log.e(TAG, "Android TTS error ($errorCode): $id")
                    onComplete()
                }
            }
        })

        textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Cancel any ongoing voice completion prompt.
     */
    fun cancel() {
        isPromptInProgress = false
        textToSpeech?.stop()
        sherpaOnnxTts.stopSpeaking()
        whisperService.stopListening()
        sherpaKwsService.stopListening()
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

    /**
     * TEST FUNCTION: Run a full voice completion test.
     * Tests the entire flow: TTS prompt → Voice recognition → Confirmation
     * Uses the user-selected voice engine from settings.
     */
    fun runTest() {
        val selectedEngine = getSelectedEngine()
        Log.i(TAG, "🧪 ========== VOICE COMPLETION TEST STARTED ==========")
        Log.i(TAG, "🧪 Using engine: ${selectedEngine.displayName}")
        Log.i(TAG, "🧪 This will test: TTS prompt → Voice recognition → Confirmation")
        Log.i(TAG, "🧪 Say 'YES' or 'NO' when prompted!")

        promptForCompletion(
            courseId = "test_course",
            lessonId = "test_lesson",
            lessonTitle = "Test Lesson",
            onComplete = {
                Log.i(TAG, "🧪 ✅ TEST RESULT: User said YES - lesson would be marked complete")
                Log.i(TAG, "🧪 ========== VOICE COMPLETION TEST FINISHED ==========")
            },
            onSkipped = {
                Log.i(TAG, "🧪 ⏭️ TEST RESULT: User said NO or timeout - lesson skipped")
                Log.i(TAG, "🧪 ========== VOICE COMPLETION TEST FINISHED ==========")
            },
            onError = { error ->
                Log.e(TAG, "🧪 ❌ TEST RESULT: Error occurred - $error")
                Log.i(TAG, "🧪 ========== VOICE COMPLETION TEST FINISHED ==========")
            }
        )
    }
}
