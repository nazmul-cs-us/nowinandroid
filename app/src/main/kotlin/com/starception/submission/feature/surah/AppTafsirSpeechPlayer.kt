package com.starception.submission.feature.surah

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.starception.submission.feature.quran.QuranPlaybackService
import com.starception.submission.feature.quran.SurahTafsirPlayback
import com.starception.submission.feature.quran.TafsirSpeechPlayer
import com.starception.submission.feature.quran.TafsirSpeechState
import com.starception.submission.feature.quran.TafsirSpeechStatus
import com.starception.submission.services.ChapterRecitationService
import com.starception.submission.services.ChapterRecitationState
import com.starception.submission.settings.components.TtsVoice
import com.starception.submission.voice.SherpaOnnxTtsEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface AppTafsirSpeechEngine {
    suspend fun speak(text: String, voice: TtsVoice, speakerId: Int, onStart: () -> Unit): Boolean
    fun pause()
    fun resume()
    fun stop()
}

/** Reads the app's current voice/speaker for every new English passage. */
internal class AppVoiceTafsirSpeechPlayer(
    private val engine: AppTafsirSpeechEngine,
    private val selection: () -> SurahTtsSelection,
    private val beforePlay: () -> Unit = {},
    private val afterPlayback: () -> Unit = {},
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : TafsirSpeechPlayer {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(TafsirSpeechState())
    override val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    private var ownsAudio = false
    private var released = false

    override fun play(text: String, language: String) {
        if (released) return
        stop()
        if (text.isBlank()) return
        if (!language.substringBefore('-').equals(selection().voice.languageCode, ignoreCase = true)) {
            mutableState.value = TafsirSpeechState(TafsirSpeechStatus.ERROR, "The selected app voice does not support this language.")
            return
        }
        val token = ++generation
        mutableState.value = TafsirSpeechState(TafsirSpeechStatus.PREPARING)
        try { beforePlay() } catch (error: Exception) {
            afterPlayback()
            mutableState.value = TafsirSpeechState(TafsirSpeechStatus.ERROR, error.message ?: "Audio is busy. Try again.")
            return
        }
        ownsAudio = true
        job = scope.launch {
            try {
                val voice = selection()
                val completed = engine.speak(text, voice.voice, voice.speakerId) {
                    scope.launch {
                        if (token == generation && state.value.status == TafsirSpeechStatus.PREPARING)
                            mutableState.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING)
                    }
                }
                if (token == generation) {
                    ownsAudio = false
                    afterPlayback()
                    mutableState.value = if (completed) TafsirSpeechState(finished = true) else
                        TafsirSpeechState(TafsirSpeechStatus.ERROR, "Could not play the selected app voice. Check Text to speech settings and its voice download.")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (token == generation) {
                    ownsAudio = false
                    engine.stop()
                    afterPlayback()
                    mutableState.value = TafsirSpeechState(TafsirSpeechStatus.ERROR, error.message ?: "Could not play tafsir.")
                }
            }
        }
    }

    override fun pause() {
        if (state.value.status != TafsirSpeechStatus.PLAYING) return
        engine.pause()
        afterPlayback()
        mutableState.value = TafsirSpeechState(TafsirSpeechStatus.PAUSED)
    }

    override fun resume() {
        if (released || state.value.status != TafsirSpeechStatus.PAUSED) return
        try {
            beforePlay()
            engine.resume()
            mutableState.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING)
        } catch (error: Exception) {
            stop()
            mutableState.value = TafsirSpeechState(TafsirSpeechStatus.ERROR, error.message ?: "Could not resume tafsir.")
        }
    }

    override fun stop() {
        generation++
        job?.cancel()
        job = null
        if (ownsAudio) { ownsAudio = false; engine.stop(); afterPlayback() }
        mutableState.value = TafsirSpeechState()
    }

    override fun release() { released = true; stop(); scope.cancel() }
}

/** Do not send Bengali/Arabic commentary through an English-only Kokoro/VCTK model. */
internal class TafsirSpeechRouter(
    private val appVoice: () -> TafsirSpeechPlayer,
    private val otherLanguages: () -> TafsirSpeechPlayer,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val bengaliVoice: (() -> TafsirSpeechPlayer)? = null,
    private val bengaliEnabled: () -> Boolean = { false },
) : TafsirSpeechPlayer {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(TafsirSpeechState())
    override val state = mutableState.asStateFlow()
    private var english: TafsirSpeechPlayer? = null
    private var other: TafsirSpeechPlayer? = null
    private var bengali: TafsirSpeechPlayer? = null
    private var current: TafsirSpeechPlayer? = null
    private var observer: Job? = null
    private var released = false

    override fun play(text: String, language: String) {
        if (released) return
        val next = when {
            language.substringBefore('-').equals("en", ignoreCase = true) -> english ?: appVoice().also { english = it }
            language.substringBefore('-').equals("bn", ignoreCase = true) && bengaliEnabled() && bengaliVoice != null ->
                bengali ?: bengaliVoice.invoke().also { bengali = it }
            else -> other ?: otherLanguages().also { other = it }
        }
        if (current !== next) {
            current?.stop()
            observer?.cancel()
            current = next
            observer = scope.launch { next.state.collect { mutableState.value = it } }
        }
        next.play(text, language)
        mutableState.value = next.state.value
    }

    override fun pause() { current?.pause(); current?.let { mutableState.value = it.state.value } }
    override fun resume() { current?.resume(); current?.let { mutableState.value = it.state.value } }
    override fun stop() { current?.stop(); mutableState.value = TafsirSpeechState() }
    override fun release() {
        released = true
        observer?.cancel()
        setOfNotNull(english, other, bengali).forEach { it.release() }
        scope.cancel()
        mutableState.value = TafsirSpeechState()
    }
}

internal fun createAppTafsirSpeechPlayer(context: Context, fullSurah: Boolean = false): TafsirSpeechPlayer {
    val app = context.applicationContext
    fun voicePlayer(bengali: Boolean): TafsirSpeechPlayer {
        val entry = EntryPointAccessors.fromApplication(app, SherpaOnnxTtsEntryPoint::class.java)
        val speech = if (bengali) entry.bengaliMmsTtsService() else entry.sherpaOnnxTtsService()
        val audio = app.getSystemService(AudioManager::class.java)
        lateinit var player: AppVoiceTafsirSpeechPlayer
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener {
                if (it < 0) {
                    if (player.state.value.status == TafsirSpeechStatus.PREPARING) player.stop() else player.pause()
                }
            }.build()
        player = AppVoiceTafsirSpeechPlayer(
            engine = object : AppTafsirSpeechEngine {
                override suspend fun speak(text: String, voice: TtsVoice, speakerId: Int, onStart: () -> Unit): Boolean {
                    if (bengali && !withContext(Dispatchers.IO) {
                            com.starception.submission.settings.components.isTtsVoiceModelAvailable(app, voice)
                        }) {
                        MissingBengaliVoice.requested.value = true
                        return false
                    }
                    speech.setVoice(voice)
                    return speech.speak(text, speakerId = speakerId, onPlaybackStart = onStart)
                }
                override fun pause() = speech.pauseSpeaking()
                override fun resume() = speech.resumeSpeaking()
                override fun stop() = speech.stopSpeaking()
            },
            selection = { if (bengali) SurahTtsSelection(TtsVoice.MMS_BENGALI, 0)
                else readSurahTtsSelection(app.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)) },
            beforePlay = {
                check(audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Audio is busy. Try again." }
                QuranPlaybackService.pauseActive()
                if (!fullSurah) {
                    SurahTafsirPlayback.owner.stop()
                    if (ChapterRecitationState.isPlaying) ChapterRecitationService.toggle(app)
                }
            },
            afterPlayback = { audio.abandonAudioFocusRequest(focus) },
        )
        return player
    }
    return TafsirSpeechRouter(appVoice = { voicePlayer(false) },
        otherLanguages = { AndroidTafsirSpeechPlayer(app, fullSurah) },
        bengaliVoice = { voicePlayer(true) },
        bengaliEnabled = { com.starception.submission.BuildConfig.BENGALI_MMS_PREVIEW &&
            app.getSharedPreferences("tts_settings", Context.MODE_PRIVATE).getBoolean("bengali_mms_enabled", false) })
}
