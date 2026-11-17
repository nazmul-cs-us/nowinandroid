package com.starception.submission.feature.quran

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SurahTafsirSpeechTest {
    private class Engine : TafsirSpeechPlayer {
        override val state = MutableStateFlow(TafsirSpeechState())
        val spoken = mutableListOf<String>()
        override fun play(text: String, language: String) { spoken += "$language:$text"; state.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
        override fun pause() { if (state.value.status == TafsirSpeechStatus.PLAYING) state.value = TafsirSpeechState(TafsirSpeechStatus.PAUSED) }
        override fun resume() { if (state.value.status == TafsirSpeechStatus.PAUSED) state.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
        override fun stop() { state.value = TafsirSpeechState() }
        override fun release() = stop()
        fun finish() { state.value = TafsirSpeechState(finished = true) }
    }
    @Test fun readsTheWholeSurahInOrderWithoutRepeatingAdjacentGroupedCommentary() = runTest {
        val engine = Engine(); val session = TafsirSpeechSession()
        val queue = SurahTafsirSpeechPlayer(this, engine, 5, "bn", { listOf("A", "A", "B", "B", "C")[it - 1] }, session::activate)
        queue.start(); runCurrent()
        assertEquals(1..2, queue.currentAyahs.value)
        engine.finish(); runCurrent()
        assertEquals(3..4, queue.currentAyahs.value)
        engine.finish(); runCurrent()
        assertEquals(5..5, queue.currentAyahs.value)
        engine.finish(); runCurrent()
        assertEquals(listOf("bn:A", "bn:B", "bn:C"), engine.spoken)
        assertTrue(queue.state.value.finished)
        queue.release()
    }
    @Test fun pauseAtAnUtteranceBoundaryDoesNotStartTheNextAyahUntilResume() = runTest {
        val engine = Engine()
        val queue = SurahTafsirSpeechPlayer(this, engine, 2, "en", { "Ayah $it" }, {})
        queue.start(); runCurrent()
        engine.finish()
        queue.pause(); runCurrent()
        assertEquals(listOf("en:Ayah 1"), engine.spoken)
        assertEquals(TafsirSpeechStatus.PAUSED, queue.state.value.status)
        queue.resume(); runCurrent()
        assertEquals(listOf("en:Ayah 1", "en:Ayah 2"), engine.spoken)
        queue.stop(); runCurrent()
    }
    @Test fun startingAnIndividualAyahCancelsTheSurahQueue() = runTest {
        val engine = Engine(); val session = TafsirSpeechSession()
        val queue = SurahTafsirSpeechPlayer(this, engine, 3, "en", { "Ayah $it" }, session::activate)
        queue.start(); runCurrent()
        val individual = session.createPlayer { Engine() }
        individual.play("Individual", "en"); runCurrent()
        engine.finish(); runCurrent()
        assertEquals(listOf("en:Ayah 1"), engine.spoken)
        assertEquals(TafsirSpeechStatus.IDLE, queue.state.value.status)
        individual.release(); queue.release()
    }
    @Test fun missingContentShowsAnAssetPromptAndNeverStartsSpeech() = runTest {
        val engine = Engine()
        val queue = SurahTafsirSpeechPlayer(this, engine, 7, "en", { throw PublishedTafsirAssetMissing() }, {})
        queue.start(); runCurrent()
        assertTrue(queue.state.value.assetMissing)
        assertTrue(engine.spoken.isEmpty())
        queue.release()
    }
    @Test fun aVoiceErrorStopsTheQueueWithoutSkippingToTheNextAyah() = runTest {
        val engine = Engine()
        val queue = SurahTafsirSpeechPlayer(this, engine, 3, "en", { "Ayah $it" }, {})
        queue.start(); runCurrent()
        engine.state.value = TafsirSpeechState(TafsirSpeechStatus.ERROR, "Missing voice"); runCurrent()
        assertEquals("Missing voice", queue.state.value.error)
        assertEquals(1, engine.spoken.size)
        queue.release()
    }
    @Test fun stopWhileLoadingPreventsDelayedSpeechFromStarting() = runTest {
        val engine = Engine(); val gate = CompletableDeferred<Unit>()
        val queue = SurahTafsirSpeechPlayer(this, engine, 3, "en", { gate.await(); "Ayah $it" }, {})
        queue.start(); runCurrent(); queue.stop(); gate.complete(Unit); runCurrent()
        assertTrue(engine.spoken.isEmpty())
        assertEquals(TafsirSpeechStatus.IDLE, queue.state.value.status)
        queue.release()
    }
}
