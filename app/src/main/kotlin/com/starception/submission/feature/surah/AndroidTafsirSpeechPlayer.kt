package com.starception.submission.feature.surah

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.starception.submission.feature.quran.*
import com.starception.submission.services.ChapterRecitationService
import com.starception.submission.services.ChapterRecitationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Reader-scoped system speech. Pause resumes at the current word, or chunk on older engines. */
internal class AndroidTafsirSpeechPlayer(context: Context) : TafsirSpeechPlayer {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(TafsirSpeechState())
    override val state = mutableState.asStateFlow()
    private var engine: TextToSpeech? = null
    private var ready = false
    private var released = false
    private var request = 0
    private var chunks = emptyList<String>()
    private var chunk = 0
    private var offset = 0
    private var utteranceBase = 0
    private var language = "en"
    private var utteranceId = ""
    private val audio = this.context.getSystemService(AudioManager::class.java)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener({ change ->
            if (change < 0 && state.value.status == TafsirSpeechStatus.PLAYING) pause()
        }, main).build()

    override fun play(text: String, language: String) {
        if (released) return
        stop()
        chunks = tafsirSpeechChunks(normalizeTafsirSpeechText(text, language), minOf(3000, TextToSpeech.getMaxSpeechInputLength()))
        if (chunks.isEmpty()) return
        this.language = language
        chunk = 0; offset = 0
        mutableState.value = TafsirSpeechState(TafsirSpeechStatus.PREPARING)
        if (ready) startChunk() else if (engine == null) {
            engine = TextToSpeech(context) { status -> main.post {
                if (released) return@post
                ready = status == TextToSpeech.SUCCESS
                if (!ready) {
                    engine?.shutdown(); engine = null
                    fail("Could not start text-to-speech. Check your device’s speech settings.")
                } else {
                    engine?.setOnUtteranceProgressListener(listener)
                    if (state.value.status == TafsirSpeechStatus.PREPARING) startChunk()
                }
            } }
        }
    }

    private fun startChunk() {
        val tts = engine ?: return
        val locale = Locale.forLanguageTag(language)
        if (tts.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) {
            fail("No ${PublishedTafsirs.forLanguage(language)?.englishLanguageName ?: if (language == "ar") "Arabic" else language} speech voice is available. Install a matching voice in your device’s text-to-speech settings.")
            return
        }
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            fail("Audio is busy. Try playing tafsir again."); return
        }
        if (ChapterRecitationState.isPlaying) ChapterRecitationService.toggle(context)
        utteranceBase = offset
        utteranceId = "tafsir-${++request}"
        mutableState.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING)
        if (tts.speak(chunks[chunk].substring(offset), TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.ERROR)
            fail("Could not play this tafsir. Check your installed speech voice and try again.")
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(id: String?) = Unit
        override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) { main.post {
            if (id == utteranceId && state.value.status == TafsirSpeechStatus.PLAYING) offset = utteranceBase + start
        } }
        override fun onDone(id: String?) { main.post {
            if (id != utteranceId || state.value.status != TafsirSpeechStatus.PLAYING) return@post
            chunk++; offset = 0
            if (chunk < chunks.size) startChunk() else {
                stop(); mutableState.value = TafsirSpeechState(finished = true)
            }
        } }
        @Deprecated("Android callback")
        override fun onError(id: String?) { main.post {
            if (id == utteranceId) fail("Speech playback failed. Check your installed voice and try again.")
        } }
    }

    override fun pause() {
        if (state.value.status != TafsirSpeechStatus.PLAYING) return
        utteranceId = ""; engine?.stop(); audio.abandonAudioFocusRequest(focus)
        mutableState.value = TafsirSpeechState(TafsirSpeechStatus.PAUSED)
    }
    override fun resume() { if (!released && state.value.status == TafsirSpeechStatus.PAUSED) startChunk() }
    override fun stop() {
        utteranceId = ""; engine?.stop(); audio.abandonAudioFocusRequest(focus)
        mutableState.value = TafsirSpeechState()
    }
    private fun fail(message: String) { stop(); mutableState.value = TafsirSpeechState(TafsirSpeechStatus.ERROR, message) }
    override fun release() {
        released = true; stop(); engine?.shutdown(); engine = null
    }
}
