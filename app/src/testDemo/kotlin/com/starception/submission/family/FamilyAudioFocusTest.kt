package com.starception.submission.family

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
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
}
