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

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
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
        const val LISTENING_DURATION_RETRY_MS = 6000L  // Longer duration for retries
        // FAST TRANSITION: Mic works, minimize delays to catch user's immediate response
        // Previous 300ms + 1500ms delays caused "yes" to be completely missed
        const val PROMPT_DELAY_MS = 50L  // Tiny delay - mic is ready immediately
        const val BLUETOOTH_EXTRA_DELAY_MS = 50L  // Minimal extra for Bluetooth
        // Note: The mic is explicitly set to phone's built-in mic via setPreferredDevice()
        const val UTTERANCE_ID_PROMPT = "voice_completion_prompt"

        // Retry configuration for noisy environments
        const val MAX_RETRY_ATTEMPTS = 2  // Will try up to 3 times total (1 initial + 2 retries)
    }

    // Retry state
    private var currentRetryCount = 0

    // Audio manager for Bluetooth handling
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
                // Check Bluetooth status before starting
                val isBluetoothConnected = isBluetoothAudioConnected()
                if (isBluetoothConnected) {
                    Log.i(TAG, "🔵 BLUETOOTH MODE: Phone connected to Bluetooth audio")
                    Log.i(TAG, "🔵 Will use phone's built-in microphone for clearer voice recognition")
                }

                // Play voice prompt
                val promptPlayed = playVoicePrompt(lessonTitle)
                if (!promptPlayed) {
                    Log.w(TAG, "Failed to play voice prompt, using fallback")
                }

                // CRITICAL: Stop TTS explicitly to release all audio resources
                // This is essential after long TTS playback chains
                Log.d(TAG, "🔊 Stopping TTS to release audio resources...")
                sherpaOnnxTts.stopSpeaking()

                // Delay after prompt - extra delay for Bluetooth
                val totalDelay = if (isBluetoothConnected) {
                    PROMPT_DELAY_MS + BLUETOOTH_EXTRA_DELAY_MS
                } else {
                    PROMPT_DELAY_MS
                }
                Log.i(TAG, "⏳ Waiting ${totalDelay}ms after TTS before listening (Bluetooth: $isBluetoothConnected)...")
                delay(totalDelay)

                // Start listening
                Log.i(TAG, "🎤 Starting voice listening...")
                listenForResponse(
                    onYes = {
                        // Restore audio state after recording
                        restoreAudioState()
                        // Confirmation already spoken in listenForResponse
                        onComplete()
                        isPromptInProgress = false
                    },
                    onNo = {
                        // Restore audio state after recording
                        restoreAudioState()
                        // Confirmation already spoken in listenForResponse
                        onSkipped()
                        isPromptInProgress = false
                    },
                    onError = { error ->
                        // Restore audio state after recording
                        restoreAudioState()
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

        // Cancel any ongoing background TTS pre-generation to get priority
        // This is critical - background hadith generation can take 20+ seconds
        // and would block the voice prompt if we don't cancel it
        Log.i(TAG, "🎤 Cancelling background TTS work for voice prompt priority...")
        sherpaOnnxTts.cancelBackgroundWork()

        // Check if background work is still in progress
        // If so, use Android TTS to avoid waiting for mutex
        if (sherpaOnnxTts.isBackgroundWorkInProgress()) {
            Log.w(TAG, "🎤 Background TTS in progress, using Android TTS to avoid delay")
            return playWithAndroidTts(promptText)
        }

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
     * Check if Bluetooth audio is connected (A2DP for media or HFP for calls).
     * When connected to car Bluetooth, we need special handling:
     * - TTS plays through car speakers (A2DP)
     * - Recording might try to use car's microphone (HFP) which is far and noisy
     * - We want to force use of phone's built-in microphone instead
     */
    private fun isBluetoothAudioConnected(): Boolean {
        return try {
            // Check for Bluetooth audio output devices
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val hasBluetooth = devices.any { device ->
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
                if (hasBluetooth) {
                    Log.d(TAG, "🔵 Bluetooth audio output detected")
                }
                hasBluetooth
            } else {
                // Fallback for older devices
                @Suppress("DEPRECATION")
                audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Bluetooth status", e)
            false
        }
    }

    // Store original audio state to restore after recording
    private var originalAudioMode: Int = AudioManager.MODE_NORMAL
    private var wasBluetoothScoOn: Boolean = false
    private var wasSpeakerphoneOn: Boolean = false

    /**
     * Disable Bluetooth SCO and configure audio routing to force recording
     * through phone's built-in microphone instead of car's far-away microphone.
     *
     * Key steps:
     * 1. Disable Bluetooth SCO (stops using car's HFP microphone)
     * 2. Set audio mode to MODE_IN_COMMUNICATION (signals voice recording intent)
     * 3. Turn off speakerphone (ensures phone's primary mic is used)
     *
     * Note: The actual mic selection is done via setPreferredDevice() in the
     * recording services. MODE_IN_COMMUNICATION helps signal our intent but
     * may not be strictly necessary on all devices.
     */
    private fun forcePhoneMicrophone() {
        try {
            // Save original state for restoration
            originalAudioMode = audioManager.mode
            wasBluetoothScoOn = audioManager.isBluetoothScoOn
            wasSpeakerphoneOn = audioManager.isSpeakerphoneOn

            Log.i(TAG, "🎤 Audio state before: mode=${getModeString(originalAudioMode)}, SCO=$wasBluetoothScoOn, speaker=$wasSpeakerphoneOn")

            // Step 1: Disable Bluetooth SCO to stop using car's microphone
            if (audioManager.isBluetoothScoOn) {
                Log.i(TAG, "🎤 Disabling Bluetooth SCO to use phone microphone")
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }

            // Step 2: DO NOT change audio mode - it causes silent microphone on some devices!
            // MODE_IN_COMMUNICATION was causing amplitude=0.0000 (silent mic)
            // setPreferredDevice() in recording services handles mic selection properly
            // if (audioManager.mode != AudioManager.MODE_IN_COMMUNICATION) {
            //     Log.i(TAG, "🎤 Setting audio mode to MODE_IN_COMMUNICATION for voice recording")
            //     audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            // }
            Log.i(TAG, "🎤 Keeping audio mode as ${getModeString(audioManager.mode)} (not changing to avoid silent mic)")

            // Step 3: Turn off speakerphone to use primary phone microphone
            if (audioManager.isSpeakerphoneOn) {
                Log.i(TAG, "🎤 Turning off speakerphone to use primary mic")
                audioManager.isSpeakerphoneOn = false
            }

            Log.i(TAG, "🎤 Audio state after: mode=${getModeString(audioManager.mode)}, SCO=${audioManager.isBluetoothScoOn}, speaker=${audioManager.isSpeakerphoneOn}")

        } catch (e: Exception) {
            Log.w(TAG, "Error forcing phone microphone", e)
        }
    }

    /**
     * Restore audio state after recording is complete.
     */
    private fun restoreAudioState() {
        try {
            Log.d(TAG, "🎤 Restoring original audio state")
            audioManager.mode = originalAudioMode
            // Don't restore SCO automatically - let system handle it
        } catch (e: Exception) {
            Log.w(TAG, "Error restoring audio state", e)
        }
    }

    private fun getModeString(mode: Int): String = when (mode) {
        AudioManager.MODE_NORMAL -> "NORMAL"
        AudioManager.MODE_RINGTONE -> "RINGTONE"
        AudioManager.MODE_IN_CALL -> "IN_CALL"
        AudioManager.MODE_IN_COMMUNICATION -> "IN_COMMUNICATION"
        else -> "UNKNOWN($mode)"
    }

    /**
     * Prepare audio routing for voice recording.
     * MINIMAL DELAYS - mic is working, we just need quick transition.
     * Previous 2+ second delays caused user's "yes" to be missed entirely.
     */
    private suspend fun prepareAudioForRecording(): Long {
        val isBluetoothConnected = isBluetoothAudioConnected()

        // FAST AUDIO TRANSITION - mic works, minimize delay to catch user's response
        Log.i(TAG, "🔄 FAST AUDIO PREP: Stopping TTS...")
        sherpaOnnxTts.stopSpeaking()

        Log.i(TAG, "🔄 FAST AUDIO PREP: Requesting audio focus...")
        try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
            Log.i(TAG, "🎤 Audio focus: $result")
        } catch (e: Exception) {
            Log.w(TAG, "Error requesting audio focus", e)
        }

        // Minimal delay - just enough for audio system transition
        delay(100)

        if (isBluetoothConnected) {
            Log.i(TAG, "🔵 Bluetooth - forcing phone microphone")
            forcePhoneMicrophone()
            delay(100)
            return 200L
        }

        return 100L
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
     * Implements retry mechanism for noisy environments (driving mode).
     */
    private suspend fun listenForResponse(
        onYes: () -> Unit,
        onNo: () -> Unit,
        onError: (String) -> Unit
    ) {
        // Reset retry count at start of new listening session
        currentRetryCount = 0
        listenForResponseInternal(onYes, onNo, onError)
    }

    /**
     * Internal listen function that handles retries.
     * Prepares audio routing (forces phone mic when Bluetooth connected) before listening.
     */
    private suspend fun listenForResponseInternal(
        onYes: () -> Unit,
        onNo: () -> Unit,
        onError: (String) -> Unit
    ) {
        val selectedEngine = getSelectedEngine()
        val isRetry = currentRetryCount > 0
        val listeningDuration = if (isRetry) LISTENING_DURATION_RETRY_MS else LISTENING_DURATION_MS

        Log.i(TAG, "🎤 Using voice engine: ${selectedEngine.displayName}${if (isRetry) " (retry $currentRetryCount)" else ""}")

        // Prepare audio routing - forces phone mic when Bluetooth is connected
        // This prevents using the car's far-away, noisy microphone
        val extraDelay = prepareAudioForRecording()
        if (extraDelay > 0) {
            Log.d(TAG, "🎤 Added ${extraDelay}ms delay for Bluetooth audio routing")
        }

        when (selectedEngine) {
            VoiceRecognitionEngine.SHERPA_KWS -> {
                listenWithSherpaKws(onYes, onNo, onError, listeningDuration)
            }
            VoiceRecognitionEngine.WHISPER -> {
                listenWithWhisper(onYes, onNo, onError, listeningDuration)
            }
        }
    }

    /**
     * Handle retry logic for unrecognized or timeout results.
     * Plays back the captured audio for debugging before retrying.
     * Returns true if a retry was initiated, false if max retries reached.
     */
    private fun handleRetry(
        reason: String,
        onYes: () -> Unit,
        onNo: () -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        if (currentRetryCount < MAX_RETRY_ATTEMPTS) {
            currentRetryCount++
            Log.i(TAG, "🔄 Retrying voice recognition (attempt ${currentRetryCount + 1}/${MAX_RETRY_ATTEMPTS + 1}): $reason")

            // Play back what was captured for debugging, then retry
            scope.launch {
                // Play back the captured audio so user can hear what was recorded
                playBackCapturedAudioForDebug {
                    // After playback, speak retry prompt and listen again
                    speakRetryPrompt {
                        scope.launch {
                            delay(PROMPT_DELAY_MS)
                            listenForResponseInternal(onYes, onNo, onError)
                        }
                    }
                }
            }
            return true
        } else {
            Log.i(TAG, "🛑 Max retries reached ($MAX_RETRY_ATTEMPTS), giving up: $reason")
            // Play back final failed recording for debugging
            scope.launch {
                playBackCapturedAudioForDebug {}
            }
            return false
        }
    }

    /**
     * Play back the captured audio for debugging purposes.
     * This helps diagnose what Whisper actually recorded when it fails.
     */
    private fun playBackCapturedAudioForDebug(onComplete: () -> Unit) {
        scope.launch {
            val selectedEngine = getSelectedEngine()
            val hasKwsRecording = sherpaKwsService.hasRecordingForPlayback()
            val hasWhisperRecording = whisperService.hasRecordingForPlayback()

            val playbackLabel = when {
                selectedEngine == VoiceRecognitionEngine.SHERPA_KWS && hasKwsRecording -> "KWS"
                selectedEngine == VoiceRecognitionEngine.WHISPER && hasWhisperRecording -> "Whisper"
                hasKwsRecording -> "KWS"
                hasWhisperRecording -> "Whisper"
                else -> null
            }

            if (playbackLabel != null) {
                Log.i(TAG, "🔊 DEBUG: Playing back captured audio from $playbackLabel...")
                // First announce we're playing back
                val announcementPlayed = try {
                    sherpaOnnxTts.speak("Here is what I heard:", onComplete = {})
                } catch (e: Exception) {
                    false
                }

                delay(if (announcementPlayed) 1500L else 500L)

                // Play the actual recording
                when (playbackLabel) {
                    "KWS" -> sherpaKwsService.playLastRecording {
                        Log.i(TAG, "🔊 DEBUG: KWS playback complete")
                        scope.launch {
                            delay(500)
                            onComplete()
                        }
                    }
                    else -> whisperService.playLastRecording {
                        Log.i(TAG, "🔊 DEBUG: Whisper playback complete")
                        scope.launch {
                            delay(500)
                            onComplete()
                        }
                    }
                }
            } else {
                Log.d(TAG, "🔊 DEBUG: No recording available for playback")
                onComplete()
            }
        }
    }

    /**
     * Speak a shorter retry prompt for noisy environments.
     */
    private fun speakRetryPrompt(onComplete: () -> Unit) {
        scope.launch {
            val retryPrompt = when (currentRetryCount) {
                1 -> "Sorry, I didn't catch that. Please say YES or NO more clearly."
                else -> "One more try. Say YES or NO loudly."
            }

            Log.i(TAG, "🔊 Retry prompt: $retryPrompt")

            // Check if background work is still in progress
            // If so, use Android TTS to avoid waiting for mutex
            if (sherpaOnnxTts.isBackgroundWorkInProgress()) {
                Log.w(TAG, "🎤 Background TTS in progress, using Android TTS for retry prompt")
                speakWithAndroidTtsFallback(retryPrompt, onComplete)
                return@launch
            }

            applyUserSelectedTtsVoice()

            val success = try {
                sherpaOnnxTts.speak(retryPrompt, onComplete = { onComplete() })
            } catch (e: Exception) {
                Log.w(TAG, "Sherpa-ONNX TTS failed for retry prompt", e)
                false
            }

            if (!success) {
                speakWithAndroidTtsFallback(retryPrompt, onComplete)
            }
        }
    }

    /**
     * Listen using Sherpa-ONNX KWS (fast ~100ms keyword spotting)
     * Implements retry logic for noisy environments.
     */
    private fun listenWithSherpaKws(
        onYes: () -> Unit,
        onNo: () -> Unit,
        onError: (String) -> Unit,
        durationMs: Long = LISTENING_DURATION_MS
    ) {
        scope.launch {
            sherpaKwsService.startListening(
                durationMs = durationMs,
                callback = object : SherpaOnnxKwsService.VoiceRecognitionCallback {
                    override fun onResult(result: SherpaOnnxKwsService.VoiceResult) {
                        when (result) {
                            is SherpaOnnxKwsService.VoiceResult.Yes -> {
                                Log.i(TAG, "✅ KWS detected: YES")
                                currentRetryCount = 0  // Reset on success
                                speakConfirmation("I heard yes. Marking lesson complete.") {
                                    onYes()
                                }
                            }
                            is SherpaOnnxKwsService.VoiceResult.No -> {
                                Log.i(TAG, "❌ KWS detected: NO")
                                currentRetryCount = 0  // Reset on success
                                speakConfirmation("I heard no. Skipping this lesson.") {
                                    onNo()
                                }
                            }
                            is SherpaOnnxKwsService.VoiceResult.Timeout -> {
                                Log.d(TAG, "⏱️ KWS timeout - no keyword detected")
                                // Try retry before giving up
                                val retried = handleRetry("timeout - no speech detected", onYes, onNo, onError)
                                if (!retried) {
                                    Log.i(TAG, "🔁 KWS retries exhausted, falling back to Whisper once")
                                    listenWithWhisper(onYes, onNo, onError, durationMs)
                                }
                            }
                            is SherpaOnnxKwsService.VoiceResult.Unrecognized -> {
                                Log.d(TAG, "❓ KWS unrecognized: ${result.text}")
                                // Try retry before giving up
                                val retried = handleRetry("unrecognized: ${result.text}", onYes, onNo, onError)
                                if (!retried) {
                                    Log.i(TAG, "🔁 KWS unrecognized after retries, falling back to Whisper once")
                                    listenWithWhisper(onYes, onNo, onError, durationMs)
                                }
                            }
                            is SherpaOnnxKwsService.VoiceResult.Error -> {
                                Log.e(TAG, "❌ KWS error: ${result.message}")
                                onError(result.message)
                            }
                        }
                    }

                    override fun onListeningStarted() {
                        Log.d(TAG, "KWS listening started (duration: ${durationMs}ms)")
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
     * Implements retry logic for noisy environments.
     */
    private fun listenWithWhisper(
        onYes: () -> Unit,
        onNo: () -> Unit,
        onError: (String) -> Unit,
        durationMs: Long = LISTENING_DURATION_MS
    ) {
        scope.launch {
            whisperService.startListening(
            durationMs = durationMs,
            callback = object : WhisperVoiceService.VoiceRecognitionCallback {
                override fun onResult(result: WhisperVoiceService.VoiceResult) {
                    when (result) {
                        is WhisperVoiceService.VoiceResult.Yes -> {
                            Log.i(TAG, "✅ Whisper recognized: YES")
                            currentRetryCount = 0  // Reset on success
                            speakConfirmation("I heard yes. Marking lesson complete.") {
                                onYes()
                            }
                        }
                        is WhisperVoiceService.VoiceResult.No -> {
                            Log.i(TAG, "❌ Whisper recognized: NO")
                            currentRetryCount = 0  // Reset on success
                            speakConfirmation("I heard no. Skipping this lesson.") {
                                onNo()
                            }
                        }
                        is WhisperVoiceService.VoiceResult.Timeout -> {
                            Log.d(TAG, "⏱️ No speech detected (timeout)")
                            // Try retry before giving up
                            val retried = handleRetry("timeout - no speech detected", onYes, onNo, onError)
                            if (!retried) {
                                speakConfirmation("No response detected after multiple tries. Skipping.") {
                                    onNo()
                                }
                            }
                        }
                        is WhisperVoiceService.VoiceResult.Unrecognized -> {
                            Log.d(TAG, "❓ Unrecognized speech: ${result.text}")
                            // Try retry before giving up
                            val retried = handleRetry("unrecognized: ${result.text}", onYes, onNo, onError)
                            if (!retried) {
                                speakConfirmation("Sorry, I couldn't understand. Skipping.") {
                                    onNo()
                                }
                            }
                        }
                        is WhisperVoiceService.VoiceResult.Error -> {
                            onError(result.message)
                        }
                    }
                }

                override fun onListeningStarted() {
                    Log.d(TAG, "Whisper listening started (duration: ${durationMs}ms)")
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

            // Check if background work is still in progress
            // If so, use Android TTS to avoid waiting for mutex
            if (sherpaOnnxTts.isBackgroundWorkInProgress()) {
                Log.w(TAG, "🎤 Background TTS in progress, using Android TTS for confirmation")
                speakWithAndroidTtsFallback(message, onComplete)
                return@launch
            }

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
