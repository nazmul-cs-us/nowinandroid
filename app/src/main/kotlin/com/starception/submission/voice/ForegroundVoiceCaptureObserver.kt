package com.starception.submission.voice

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.starception.submission.ui.search.SearchPrefillBus

/** Interactive search must not leave stale busy flags blocking the separate wake listener. */
internal class ForegroundVoiceCaptureObserver(private val stopCapture: () -> Unit) : LifecycleEventObserver {
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event != Lifecycle.Event.ON_STOP) return
        try { stopCapture() } finally {
            SearchPrefillBus.setListening(false)
            SearchPrefillBus.setProcessing(false)
            SearchPrefillBus.setVoiceLevel(0f)
        }
    }
}
