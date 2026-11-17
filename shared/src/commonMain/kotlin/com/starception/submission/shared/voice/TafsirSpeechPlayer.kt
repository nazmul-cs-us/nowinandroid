package com.starception.submission.shared.voice

import com.starception.submission.feature.quran.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** iOS uses the existing audio-session-aware system synthesizer. */
internal class SystemTafsirSpeechPlayer(private val onStart: () -> Unit) : TafsirSpeechPlayer {
    private val synthesizer = PlatformSpeechSynthesizer()
    private val mutableState = MutableStateFlow(TafsirSpeechState())
    override val state = mutableState.asStateFlow()
    override fun play(text: String, language: String) {
        stop()
        onStart()
        mutableState.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING)
        val started = synthesizer.speak(text, language = language, onComplete = { error ->
            mutableState.value = if (error == null) TafsirSpeechState()
                else TafsirSpeechState(TafsirSpeechStatus.ERROR, error)
        })
        if (!started) mutableState.value = TafsirSpeechState(TafsirSpeechStatus.ERROR,
            "No ${PublishedTafsirs.forLanguage(language)?.englishLanguageName ?: if (language == "ar") "Arabic" else language} speech voice is available. Check your device’s spoken-content voice settings.")
    }
    override fun pause() { synthesizer.pause(); mutableState.value = TafsirSpeechState(TafsirSpeechStatus.PAUSED) }
    override fun resume() { onStart(); synthesizer.resume(); mutableState.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
    override fun stop() { synthesizer.stop(); mutableState.value = TafsirSpeechState() }
    override fun release() = stop()
}
