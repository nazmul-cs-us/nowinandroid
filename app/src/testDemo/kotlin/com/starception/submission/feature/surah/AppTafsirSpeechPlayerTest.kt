package com.starception.submission.feature.surah

import com.starception.submission.feature.quran.TafsirSpeechPlayer
import com.starception.submission.feature.quran.TafsirSpeechState
import com.starception.submission.feature.quran.TafsirSpeechStatus
import com.starception.submission.settings.components.TtsVoice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppTafsirSpeechPlayerTest {
    @Test fun englishNarrationUsesTheSavedAppVoiceAndSpeakerAndSupportsPauseResume() = runTest {
        val engine = Engine()
        var selected = SurahTtsSelection(TtsVoice.KOKORO_EN, 3)
        val player = AppVoiceTafsirSpeechPlayer(engine, { selected }, dispatcher = StandardTestDispatcher(testScheduler))
        try {
            player.play("English commentary", "en")
            runCurrent()
            assertEquals(selected, engine.voices.single())
            assertEquals(TafsirSpeechStatus.PLAYING, player.state.value.status)
            player.pause()
            assertEquals(1, engine.pauses)
            assertEquals(TafsirSpeechStatus.PAUSED, player.state.value.status)
            player.resume()
            assertEquals(1, engine.resumes)
            engine.complete.complete(true)
            runCurrent()
            assertTrue(player.state.value.finished)
            selected = SurahTtsSelection(TtsVoice.VITS_VCTK, 108)
            engine.complete = CompletableDeferred()
            player.play("Next passage", "en")
            runCurrent()
            assertEquals(selected, engine.voices.last())
        } finally { player.release() }
    }

    @Test fun releasingDuringPreparationIgnoresLatePlaybackAndCompletionCallbacks() = runTest {
        val engine = Engine(startImmediately = false)
        val player = AppVoiceTafsirSpeechPlayer(engine, { SurahTtsSelection(TtsVoice.KOKORO_EN, 0) },
            dispatcher = StandardTestDispatcher(testScheduler))
        player.play("Commentary", "en")
        runCurrent()
        assertEquals(TafsirSpeechStatus.PREPARING, player.state.value.status)
        player.release()
        engine.onStart?.invoke()
        engine.complete.complete(true)
        runCurrent()
        assertEquals(1, engine.stops)
        assertEquals(TafsirSpeechStatus.IDLE, player.state.value.status)
        assertFalse(player.state.value.finished)
    }

    @Test fun englishModelsAreNeverAskedToReadBengali() = runTest {
        val engine = Engine()
        val player = AppVoiceTafsirSpeechPlayer(engine, { SurahTtsSelection(TtsVoice.KOKORO_EN, 0) },
            dispatcher = StandardTestDispatcher(testScheduler))
        try {
            player.play("বাংলা তাফসীর", "bn")
            runCurrent()
            assertTrue(engine.voices.isEmpty())
            assertEquals(TafsirSpeechStatus.ERROR, player.state.value.status)
        } finally { player.release() }
    }

    @Test fun routerUsesAppVoicesForEnglishAndPreservesTheLanguageEngineForOtherLanguages() = runTest {
        val english = Player()
        val other = Player()
        var englishCreated = 0
        var otherCreated = 0
        val router = TafsirSpeechRouter({ englishCreated++; english }, { otherCreated++; other },
            StandardTestDispatcher(testScheduler))
        try {
            router.play("Commentary", "en-GB")
            runCurrent()
            assertEquals(1, englishCreated)
            assertEquals(0, otherCreated)
            assertEquals("en-GB", english.language)
            router.play("বাংলা তাফসীর", "bn")
            runCurrent()
            assertEquals(1, otherCreated)
            assertEquals(1, english.stops)
            assertEquals("bn", other.language)
            other.state.value = TafsirSpeechState(finished = true)
            runCurrent()
            assertTrue(router.state.value.finished)
        } finally { router.release() }
        assertTrue(english.released)
        assertTrue(other.released)
    }

    @Test fun bengaliRoutingRequiresOptInAndNeverChangesEnglishEngine() = runTest {
        val english = Player()
        val other = Player()
        val bengali = Player()
        var enabled = false
        val router = TafsirSpeechRouter({ english }, { other }, StandardTestDispatcher(testScheduler),
            bengaliVoice = { bengali }, bengaliEnabled = { enabled })
        try {
            router.play("বাংলা তাফসীর", "bn-BD")
            runCurrent()
            assertEquals("bn-BD", other.language)
            assertNull(bengali.language)
            enabled = true
            router.play("বাংলা তাফসীর", "bn-BD")
            runCurrent()
            assertEquals("bn-BD", bengali.language)
            router.pause()
            assertEquals(TafsirSpeechStatus.PAUSED, bengali.state.value.status)
            router.resume()
            assertEquals(TafsirSpeechStatus.PLAYING, bengali.state.value.status)
            router.play("English commentary", "en")
            runCurrent()
            assertEquals("en", english.language)
            assertEquals(1, bengali.stops)
        } finally { router.release() }
        assertTrue(bengali.released)
    }

    @Test fun bengaliAppPlayerAcceptsBengaliSelection() = runTest {
        val engine = Engine()
        val player = AppVoiceTafsirSpeechPlayer(engine, { SurahTtsSelection(TtsVoice.MMS_BENGALI, 0) },
            dispatcher = StandardTestDispatcher(testScheduler))
        try {
            player.play("বাংলা তাফসীর", "bn-BD")
            runCurrent()
            assertEquals(TtsVoice.MMS_BENGALI, engine.voices.single().voice)
            assertEquals(TafsirSpeechStatus.PLAYING, player.state.value.status)
        } finally { player.release() }
    }

    private class Engine(private val startImmediately: Boolean = true) : AppTafsirSpeechEngine {
        val voices = mutableListOf<SurahTtsSelection>()
        var complete = CompletableDeferred<Boolean>()
        var onStart: (() -> Unit)? = null
        var pauses = 0
        var resumes = 0
        var stops = 0
        override suspend fun speak(text: String, voice: TtsVoice, speakerId: Int, onStart: () -> Unit): Boolean {
            voices += SurahTtsSelection(voice, speakerId)
            this.onStart = onStart
            if (startImmediately) onStart()
            return complete.await()
        }
        override fun pause() { pauses++ }
        override fun resume() { resumes++ }
        override fun stop() { stops++ }
    }

    private class Player : TafsirSpeechPlayer {
        override val state = MutableStateFlow(TafsirSpeechState())
        var language: String? = null
        var stops = 0
        var released = false
        override fun play(text: String, language: String) { this.language = language; state.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
        override fun pause() { state.value = TafsirSpeechState(TafsirSpeechStatus.PAUSED) }
        override fun resume() { state.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
        override fun stop() { stops++; state.value = TafsirSpeechState() }
        override fun release() { released = true; stop() }
    }
}
