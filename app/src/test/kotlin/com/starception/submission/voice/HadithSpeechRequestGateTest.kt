package com.starception.submission.voice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HadithSpeechRequestGateTest {
    @Test
    fun oldSynthesisCannotPlayOrClearTheSuccessor() = runTest {
        val gate = SpeechRequestGate()
        val oldSynthesis = CompletableDeferred<Unit>()
        val played = mutableListOf<String>()
        val a = gate.begin()
        val worker = launch {
            oldSynthesis.await()
            gate.withCurrent(a) { played += "A" }
            gate.withCurrent(a) { played.clear() }
        }
        val b = gate.begin()
        gate.withCurrent(b) { played += "B" }
        oldSynthesis.complete(Unit)
        worker.join()
        assertEquals(listOf("B"), played)
    }

    @Test
    fun stopAndDelayedInterruptionCallbacksNeverRevalidateAnOldRequest() {
        val gate = SpeechRequestGate()
        val old = gate.begin()
        gate.begin() // stop invalidates outstanding work
        val current = gate.begin()
        var started = false
        gate.withCurrent(old) { started = true }
        assertFalse(started)
        assertTrue(gate.isCurrent(current))
    }

    @Test
    fun acceptedApplicationOwnedPlaybackSurvivesOriginScopeDisposal() = runTest {
        val origin = CoroutineScope(coroutineContext + Job())
        val application = CoroutineScope(coroutineContext + Job())
        val gate = SpeechRequestGate()
        val request = gate.begin()
        val synthesized = CompletableDeferred<Unit>()
        var played = false
        val playback = application.launch {
            synthesized.await()
            gate.withCurrent(request) { played = true }
        }
        origin.cancel()
        synthesized.complete(Unit)
        playback.join()
        assertTrue(played)
        application.cancel()
    }
}
