package com.starception.submission.shared.media

import com.starception.submission.feature.quran.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TafsirPlaybackBusTest {
    private class Engine : TafsirSpeechPlayer {
        override val state = MutableStateFlow(TafsirSpeechState())
        override fun play(text: String, language: String) { state.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
        override fun pause() { state.value = TafsirSpeechState(TafsirSpeechStatus.PAUSED) }
        override fun resume() { state.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
        override fun stop() { state.value = TafsirSpeechState() }
        override fun release() = stop()
        fun finish() { state.value = TafsirSpeechState(finished = true) }
    }
    @Test fun bannerAndSystemCommandsFollowDetachedSpeechUntilCompletion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = SurahTafsirPlayback.owner
        val engine = Engine()
        val entry = owner.attach(1, "zakaria_bn", "Al-Fatihah", { engine }, { "Grouped commentary" },
            onActivate = QuranPlaybackBus::startTafsir)
        try {
            entry.player.start(); runCurrent(); owner.detach(entry)
            val banner = assertNotNull(QuranPlaybackBus.state.value)
            assertEquals("Al-Fatihah", banner.title)
            assertEquals("Surah tafsir", banner.albumTitle)
            assertEquals(1, banner.readerSurahNumber)
            assertNull(banner.surahNumber) // Recitation controls must not mistake this for Quran audio.
            assertTrue(banner.bannerTitle.contains("Tafsir · Bengali · Ayahs 1–7"))
            QuranPlaybackBus.toggle(); runCurrent()
            assertFalse(QuranPlaybackBus.state.value!!.playing)
            QuranPlaybackBus.toggle(); runCurrent()
            assertTrue(QuranPlaybackBus.state.value!!.playing)
            engine.finish(); runCurrent()
            assertNull(QuranPlaybackBus.state.value)
            assertNull(owner.state.value)
        } finally { QuranPlaybackBus.stop(); owner.stop(); Dispatchers.resetMain() }
    }
    @Test fun replacingSpeechIgnoresOldCompletionsAndSystemStopCancelsTheNewQueue() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = SurahTafsirPlayback.owner
        val firstEngine = Engine(); val secondEngine = Engine()
        val first = owner.attach(1, "zakaria_bn", "Al-Fatihah", { firstEngine }, { "Bengali" }, onActivate = QuranPlaybackBus::startTafsir)
        val second = owner.attach(2, "ibn_kathir_en", "Al-Baqarah", { secondEngine }, { "English" }, onActivate = QuranPlaybackBus::startTafsir)
        try {
            first.player.start(); runCurrent(); owner.detach(first)
            second.player.start(); runCurrent(); owner.detach(second)
            firstEngine.finish(); runCurrent()
            assertEquals("Al-Baqarah", QuranPlaybackBus.state.value!!.title)
            assertTrue(QuranPlaybackBus.state.value!!.playing)
            QuranPlaybackBus.stop(); runCurrent()
            assertNull(QuranPlaybackBus.state.value)
            assertNull(owner.state.value)
        } finally { QuranPlaybackBus.stop(); owner.stop(); Dispatchers.resetMain() }
    }
}
