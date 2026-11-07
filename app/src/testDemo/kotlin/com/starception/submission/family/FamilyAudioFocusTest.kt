package com.starception.submission.family

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class FamilyAudioFocusTest {
    private val audio = ApplicationProvider.getApplicationContext<Context>().getSystemService(AudioManager::class.java)
    private val shadow = shadowOf(audio)
    private val focus = FamilyAudioFocus(audio)
    @After fun release() { focus.release() }

    @Test fun immediateGrantAllowsStartup() = runTest {
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        focus.acquire {}
        val request = shadow.lastAudioFocusRequest.audioFocusRequest
        assertTrue(request.acceptsDelayedFocusGain())
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, request.focusGain)
    }

    @Test fun delayedGrantWaitsForTheSystemAndDoesNotTreatWaitingAsInterruption() = runTest {
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_DELAYED)
        var started = false
        var interrupted = false
        val job = launch { focus.acquire { interrupted = true }; started = true }
        runCurrent()
        val listener = shadow.lastAudioFocusRequest.listener
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        runCurrent()
        assertFalse(started)
        assertFalse(interrupted)
        assertTrue(job.isActive)
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        runCurrent()
        assertTrue(started)
        assertTrue(job.isCompleted)
    }

    @Test fun briefHandoffDenialCanRecoverWithoutEndingTheCall() = runTest {
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        var started = false
        launch { focus.acquire {}; started = true }
        runCurrent()
        assertFalse(started)
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        advanceTimeBy(100)
        runCurrent()
        assertTrue(started)
    }

    @Test fun permanentDenialTimesOutAndAbandonsTheRequest() = runTest {
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        val failure = async { runCatching { focus.acquire {} }.exceptionOrNull() }
        advanceUntilIdle()
        assertIs<FamilyAudioFocus.Unavailable>(failure.await())
        assertNotNull(shadow.lastAbandonedAudioFocusRequest)
    }

    @Test fun cancellingStartupAbandonsFocusAndIgnoresLateCallbacks() = runTest {
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_DELAYED)
        var started = false
        var interrupted = false
        val job = launch { focus.acquire { interrupted = true }; started = true }
        runCurrent()
        val listener = shadow.lastAudioFocusRequest.listener
        job.cancelAndJoin()
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        runCurrent()
        assertFalse(started)
        assertFalse(interrupted)
        assertNotNull(shadow.lastAbandonedAudioFocusRequest)
    }

    @Test fun endingTheCallCancelsAPendingGrant() = runTest {
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_DELAYED)
        var started = false
        val job = launch { focus.acquire {}; started = true }
        runCurrent()
        focus.release()
        runCurrent()
        assertTrue(job.isCancelled)
        assertFalse(started)
    }

    @Test fun oldRequestCannotGrantFocusToANewCall() = runTest {
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_DELAYED)
        val first = launch { focus.acquire {} }
        runCurrent()
        val oldListener = shadow.lastAudioFocusRequest.listener
        focus.release()
        first.join()
        var started = false
        launch { focus.acquire {}; started = true }
        runCurrent()
        oldListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        runCurrent()
        assertFalse(started)
        shadow.lastAudioFocusRequest.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        runCurrent()
        assertTrue(started)
    }

    @Test fun genuineFocusLossAfterAcquisitionStillInterruptsTheCall() = runTest {
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        var interruptions = 0
        focus.acquire { interruptions++ }
        val listener = shadow.lastAudioFocusRequest.listener
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertEquals(1, interruptions)
        focus.release()
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertEquals(1, interruptions)
    }

    @Test fun telecomGrantUsesSystemOwnershipEvenWhenAnotherAudioManagerRequestWouldBeDenied() = runTest {
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        val state = MutableStateFlow(FamilyTelecomAudioState("call", true, true, true))
        val gate = FamilyAudioFocus(audio, backgroundScope, state)
        val before = shadow.lastAudioFocusRequest
        try {
            gate.acquire("call") {}
            assertSame(before, shadow.lastAudioFocusRequest)
        } finally { gate.release() }
    }

    @Test fun telecomCaptureWaitsForTheResourceGrant() = runTest {
        val state = MutableStateFlow(FamilyTelecomAudioState("call", true, true, false))
        val gate = FamilyAudioFocus(audio, backgroundScope, state)
        var started = false
        try {
            launch { gate.acquire("call") {}; started = true }
            runCurrent()
            assertFalse(started)
            state.value = state.value.copy(granted = true)
            runCurrent()
            assertTrue(started)
        } finally { gate.release() }
    }

    @Test fun telecomFocusAloneDoesNotAuthorizeAnUnacceptedCall() = runTest {
        val state = MutableStateFlow(FamilyTelecomAudioState("call", true, false, true))
        val gate = FamilyAudioFocus(audio, backgroundScope, state)
        var started = false
        try {
            launch { gate.acquire("call") {}; started = true }
            runCurrent()
            assertFalse(started)
            state.value = state.value.copy(accepted = true)
            runCurrent()
            assertTrue(started)
        } finally { gate.release() }
    }

    @Test fun telecomFocusLossInterruptsItsCurrentCall() = runTest {
        val state = MutableStateFlow(FamilyTelecomAudioState("call", true, true, true))
        val gate = FamilyAudioFocus(audio, backgroundScope, state)
        var interruptions = 0
        try {
            gate.acquire("call") { interruptions++ }
            state.value = state.value.copy(granted = false)
            runCurrent()
            assertEquals(1, interruptions)
            gate.release()
            state.value = state.value.copy(granted = true)
            runCurrent()
            assertEquals(1, interruptions)
        } finally { gate.release() }
    }

    @Test fun endingTelecomStartupIgnoresALateGrant() = runTest {
        val state = MutableStateFlow(FamilyTelecomAudioState("call", true, true, false))
        val gate = FamilyAudioFocus(audio, backgroundScope, state)
        var started = false
        val job = launch { gate.acquire("call") {}; started = true }
        runCurrent()
        gate.release()
        state.value = state.value.copy(granted = true)
        runCurrent()
        assertTrue(job.isCancelled)
        assertFalse(started)
    }

    @Test fun aRemovedTelecomCallCancelsPendingCapture() = runTest {
        val state = MutableStateFlow(FamilyTelecomAudioState("call", true, true, false))
        val gate = FamilyAudioFocus(audio, backgroundScope, state)
        var started = false
        try {
            val job = launch { gate.acquire("call") {}; started = true }
            runCurrent()
            state.value = FamilyTelecomAudioState()
            runCurrent()
            assertTrue(job.isCancelled)
            assertFalse(started)
        } finally { gate.release() }
    }

    @Test fun missingTelecomGrantFailsWithoutStartingCaptureOrRequestingCompetingFocus() = runTest {
        val state = MutableStateFlow(FamilyTelecomAudioState("call", true, true, false))
        val gate = FamilyAudioFocus(audio, backgroundScope, state)
        val before = shadow.lastAudioFocusRequest
        try {
            val failure = async { runCatching { gate.acquire("call") {} }.exceptionOrNull() }
            advanceUntilIdle()
            assertIs<FamilyAudioFocus.Unavailable>(failure.await())
            assertSame(before, shadow.lastAudioFocusRequest)
        } finally { gate.release() }
    }

    @Test fun unmanagedCallsStillRequestTheirOwnFocus() = runTest {
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        val state = MutableStateFlow(FamilyTelecomAudioState("call", false))
        val gate = FamilyAudioFocus(audio, backgroundScope, state)
        try {
            gate.acquire("call") {}
            assertNotNull(shadow.lastAudioFocusRequest)
        } finally { gate.release() }
    }
}
