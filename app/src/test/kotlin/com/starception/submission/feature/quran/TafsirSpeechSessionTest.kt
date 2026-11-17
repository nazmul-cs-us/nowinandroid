package com.starception.submission.feature.quran

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class TafsirSpeechSessionTest {
    private class Player : TafsirSpeechPlayer {
        override val state = MutableStateFlow(TafsirSpeechState())
        var stopped = 0
        override fun play(text: String, language: String) = Unit
        override fun pause() = Unit
        override fun resume() = Unit
        override fun stop() { stopped++ }
        override fun release() = stop()
    }
    @Test fun anotherAyahStopsThePreviousNarrationAndOldDisposalCannotStopTheNewOne() {
        val session = TafsirSpeechSession()
        val first = Player(); val second = Player()
        val a = session.createPlayer { first }; val b = session.createPlayer { second }
        a.play("One", "en"); b.play("Two", "en")
        assertEquals(1, first.stopped)
        a.release()
        assertEquals(0, second.stopped)
        session.stop()
        assertEquals(1, second.stopped)
    }
}
