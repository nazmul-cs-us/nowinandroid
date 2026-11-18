package com.starception.submission.feature.quran

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SurahTafsirPlaybackOwnerTest {
    private class Engine : TafsirSpeechPlayer {
        override val state = MutableStateFlow(TafsirSpeechState())
        val spoken = mutableListOf<String>()
        var released = false
        override fun play(text: String, language: String) { spoken += text; state.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
        override fun pause() { state.value = TafsirSpeechState(TafsirSpeechStatus.PAUSED) }
        override fun resume() { state.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
        override fun stop() { state.value = TafsirSpeechState() }
        override fun release() { released = true; stop() }
        fun finish() { state.value = TafsirSpeechState(finished = true) }
    }
    @Test fun leavingReaderKeepsQueueAndBannerAliveAndReturningReusesPlayback() = runTest {
        val owner = SurahTafsirPlaybackOwner(backgroundScope)
        val engine = Engine()
        val entry = owner.attach(1, "ibn_kathir_bn", "Al-Fatihah", { engine }, { "Ayah $it" })
        entry.player.start(); runCurrent()
        owner.detach(entry); runCurrent()
        assertFalse(engine.released)
        assertEquals("Al-Fatihah", owner.state.value?.entry?.title)
        assertTrue(owner.state.value?.subtitle.orEmpty().contains("Tafsir · Bengali · Ayah 1"))
        engine.finish(); runCurrent()
        assertEquals(listOf("Ayah 1", "Ayah 2"), engine.spoken)
        val reopened = owner.attach(1, "ibn_kathir_bn", "Al-Fatihah", { error("Do not create another engine") }, { error("Do not replace the active reader") })
        assertSame(entry, reopened)
        assertEquals(2..2, reopened.player.currentAyahs.value)
        owner.stop(); owner.detach(reopened)
    }
    @Test fun bannerControlsPauseResumeAndStopAfterReaderHasGone() = runTest {
        val owner = SurahTafsirPlaybackOwner(backgroundScope)
        val engine = Engine()
        val entry = owner.attach(1, "ibn_kathir_en", "Al-Fatihah", { engine }, { "Ayah $it" })
        entry.player.start(); runCurrent(); owner.detach(entry)
        owner.state.value!!.entry.player.pause(); runCurrent()
        assertFalse(owner.state.value!!.playing)
        owner.state.value!!.entry.player.resume(); runCurrent()
        assertTrue(owner.state.value!!.playing)
        owner.stop(); runCurrent()
        assertNull(owner.state.value)
        assertTrue(engine.released)
        engine.finish(); runCurrent()
        assertEquals(1, engine.spoken.size)
    }
    @Test fun visitingAnotherBookDoesNotReplaceAudioButStartingItStopsTheOldQueue() = runTest {
        val owner = SurahTafsirPlaybackOwner(backgroundScope)
        val firstEngine = Engine(); val secondEngine = Engine()
        val first = owner.attach(1, "ibn_kathir_bn", "Al-Fatihah", { firstEngine }, { "Bengali $it" })
        first.player.start(); runCurrent(); owner.detach(first)
        val second = owner.attach(1, "ibn_kathir_en", "Al-Fatihah", { secondEngine }, { "English $it" })
        assertSame(first, owner.state.value!!.entry)
        assertTrue(secondEngine.spoken.isEmpty())
        second.player.start(); runCurrent()
        assertTrue(firstEngine.released)
        firstEngine.finish(); runCurrent()
        assertSame(second, owner.state.value!!.entry)
        assertEquals(listOf("English 1"), secondEngine.spoken)
        owner.stop(); owner.detach(second)
    }
    @Test fun navigationWhileLoadingDoesNotCancelPlaybackAndCompletionClearsTheBanner() = runTest {
        val owner = SurahTafsirPlaybackOwner(backgroundScope)
        val engine = Engine(); val gate = CompletableDeferred<Unit>()
        val entry = owner.attach(1, "ibn_kathir_en", "Al-Fatihah", { engine }, { gate.await(); "Grouped commentary" })
        entry.player.start(); runCurrent(); owner.detach(entry)
        assertEquals(TafsirSpeechStatus.PREPARING, owner.state.value!!.speech.status)
        gate.complete(Unit); runCurrent()
        assertEquals(1..7, owner.state.value!!.ayahs)
        engine.finish(); runCurrent()
        assertNull(owner.state.value)
        assertTrue(engine.released)
        assertEquals(1, engine.spoken.size)
    }
    @Test fun missingAssetsClearTheBannerAndRemainRetryableInTheReader() = runTest {
        val owner = SurahTafsirPlaybackOwner(backgroundScope)
        val engine = Engine(); var available = false
        val entry = owner.attach(1, "ibn_kathir_bn", "Al-Fatihah", { engine }, {
            if (!available) throw PublishedTafsirAssetMissing() else "Commentary"
        })
        entry.player.start(); runCurrent()
        assertNull(owner.state.value)
        assertTrue(entry.player.state.value.assetMissing)
        assertFalse(engine.released)
        available = true; entry.player.start(); runCurrent()
        assertTrue(owner.state.value!!.playing)
        owner.stop(); owner.detach(entry)
    }
}
