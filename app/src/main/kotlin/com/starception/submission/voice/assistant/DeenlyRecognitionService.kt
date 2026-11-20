package com.starception.submission.voice.assistant

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import com.starception.submission.feature.search.VoiceSearchService
import com.starception.submission.feature.search.WhisperVoiceService
import com.starception.submission.family.FamilyAudioSession
import kotlinx.coroutines.*

/** Android speech-provider entry. Binding/selecting the provider never starts recording. */
class DeenlyRecognitionService : RecognitionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recognizerDelegate = lazy { WhisperVoiceService(applicationContext, "assistant_speech.wav", logTranscripts = false, useCacheDirectory = true) }
    private val recognizer by recognizerDelegate
    private var listener: Callback? = null
    private var cleanup: Job? = null
    private var generation = 0

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            FamilyAudioSession.state.collect { active ->
                if (active) listener?.let {
                    onCancel(it)
                    runCatching { it.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY) }
                }
            }
        }
    }

    override fun onStartListening(recognizerIntent: Intent?, callback: Callback) {
        if (FamilyAudioSession.isActive || getSystemService(AudioManager::class.java).mode != AudioManager.MODE_NORMAL) {
            runCatching { callback.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY) }
            return
        }
        if (listener != null || cleanup?.isActive == true) { runCatching { callback.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY) }; return }
        if (!recognizer.hasPermission()) { runCatching { callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) }; return }
        if (!recognizer.isModelAvailable()) { runCatching { callback.error(SpeechRecognizer.ERROR_CLIENT) }; return }
        val token = ++generation
        listener = callback
        recognizer.initialize { ready ->
            if (token != generation) return@initialize
            if (!ready) {
                listener = null
                runCatching { callback.error(SpeechRecognizer.ERROR_CLIENT) }
            } else {
                if (runCatching { callback.readyForSpeech(Bundle()) }.isFailure) {
                    listener = null
                    generation++
                    return@initialize
                }
                recognizer.startListening { result ->
                    if (token == generation) {
                        listener = null
                        runCatching {
                            callback.endOfSpeech()
                            when (result) {
                                is VoiceSearchService.VoiceSearchResult.Success -> callback.results(Bundle().apply {
                                    putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(result.text))
                                })
                                is VoiceSearchService.VoiceSearchResult.Error -> callback.error(SpeechRecognizer.ERROR_AUDIO)
                                VoiceSearchService.VoiceSearchResult.Cancelled -> callback.error(SpeechRecognizer.ERROR_NO_MATCH)
                            }
                        }
                        java.io.File(cacheDir, "assistant_speech.wav").delete()
                    }
                }
            }
        }
    }

    override fun onStopListening(callback: Callback) {
        if (listener !== callback) return
        if (recognizer.isListening.value || recognizer.isTranscribing.value) recognizer.stopListening()
        else {
            onCancel(callback)
            runCatching { callback.error(SpeechRecognizer.ERROR_NO_MATCH) }
        }
    }
    override fun onCancel(callback: Callback) {
        if (listener === callback) {
            generation++
            listener = null
            cleanup = scope.launch { recognizer.cancelAndAwait(); java.io.File(cacheDir, "assistant_speech.wav").delete() }
        }
    }
    override fun onDestroy() {
        generation++
        listener = null
        if (recognizerDelegate.isInitialized()) scope.launch {
            recognizer.cancelAndAwait()
            java.io.File(cacheDir, "assistant_speech.wav").delete()
            recognizer.release()
            scope.cancel()
        } else scope.cancel()
        super.onDestroy()
    }
}
