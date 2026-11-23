package com.starception.submission.voice

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.starception.submission.ui.search.SearchPrefillBus
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ForegroundVoiceCaptureObserverTest {
    private val owner = object : LifecycleOwner { override val lifecycle = LifecycleRegistry(this) }
    @After fun clearFlags() {
        SearchPrefillBus.setListening(false)
        SearchPrefillBus.setProcessing(false)
        SearchPrefillBus.setVoiceLevel(0f)
    }

    @Test fun closingTheUiStopsInteractiveCaptureAndUnblocksBackgroundWakeListening() {
        var stops = 0
        val observer = ForegroundVoiceCaptureObserver { stops++ }
        SearchPrefillBus.setListening(true)
        SearchPrefillBus.setProcessing(true)
        SearchPrefillBus.setVoiceLevel(.6f)
        observer.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        assertEquals(1, stops)
        assertFalse(SearchPrefillBus.listening.value)
        assertFalse(SearchPrefillBus.processing.value)
        assertEquals(0f, SearchPrefillBus.voiceLevel.value, 0f)
    }

    @Test fun aPermissionDialogPauseDoesNotCancelTheInteractiveRequest() {
        var stops = 0
        val observer = ForegroundVoiceCaptureObserver { stops++ }
        SearchPrefillBus.setListening(true)
        observer.onStateChanged(owner, Lifecycle.Event.ON_PAUSE)
        assertEquals(0, stops)
        assertTrue(SearchPrefillBus.listening.value)
    }

    @Test fun busyFlagsAreClearedEvenIfCaptureCleanupFails() {
        SearchPrefillBus.setListening(true)
        val result = runCatching {
            ForegroundVoiceCaptureObserver { error("Failed to stop") }.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        }
        assertTrue(result.isFailure)
        assertFalse(SearchPrefillBus.listening.value)
    }
}
