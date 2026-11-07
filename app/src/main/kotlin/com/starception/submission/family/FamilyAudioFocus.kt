package com.starception.submission.family

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Main-thread audio ownership; capture starts only after focus is granted. */
internal class FamilyAudioFocus(private val audio: AudioManager,
    private val scope: CoroutineScope? = null,
    private val telecom: StateFlow<FamilyTelecomAudioState>? = null) {
    class Unavailable : IllegalStateException("call_audio_focus_unavailable")
    private class Lease(val request: AudioFocusRequest?, val gained: CompletableDeferred<Unit>) {
        var acquired = false
        var observer: Job? = null
    }
    private var lease: Lease? = null

    suspend fun acquire(callId: String? = null, onLost: () -> Unit) {
        release()
        if (callId != null && telecom?.value?.let { it.callId == callId && it.managed } == true) {
            acquireTelecom(callId, onLost)
            return
        }
        val gained = CompletableDeferred<Unit>()
        lateinit var current: Lease
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener({ change ->
                if (lease === current) when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> { current.acquired = true; gained.complete(Unit) }
                    AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->
                        if (current.acquired) onLost()
                }
            }, Handler(Looper.getMainLooper())).build()
        current = Lease(request, gained)
        lease = current
        try {
            val acquired = withTimeoutOrNull(3000) {
                var reportedWaiting = false
                while (!current.acquired) {
                    if (lease !== current) throw CancellationException("Call audio acquisition cancelled")
                    when (audio.requestAudioFocus(request)) {
                        AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> current.acquired = true
                        AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                            android.util.Log.i("FamilyCalls", "Audio focus waiting: delayed grant")
                            gained.await()
                        }
                        else -> {
                            // Telecom's ringing/dialling handoff can briefly reject focus.
                            if (!reportedWaiting) {
                                android.util.Log.i("FamilyCalls", "Audio focus waiting: system call handoff")
                                reportedWaiting = true
                            }
                            delay(100)
                        }
                    }
                }
                if (lease !== current) throw CancellationException("Call audio acquisition cancelled")
                true
            }
            if (acquired != true) throw Unavailable()
            android.util.Log.i("FamilyCalls", "Audio focus granted")
        } catch (error: Exception) {
            if (lease === current) release()
            throw error
        }
    }

    private suspend fun acquireTelecom(callId: String, onLost: () -> Unit) {
        val source = checkNotNull(telecom)
        val current = Lease(null, CompletableDeferred())
        lease = current
        android.util.Log.i("FamilyCalls", "Audio focus waiting: Telecom ownership")
        try {
            current.observer = checkNotNull(scope).launch(start = CoroutineStart.UNDISPATCHED) {
                source.collect { state ->
                    if (lease !== current) return@collect
                    if (state.callId != callId) {
                        current.gained.cancel()
                    } else if (!state.managed) {
                        current.gained.completeExceptionally(Unavailable())
                        if (current.acquired) { current.acquired = false; onLost() }
                    } else if (state.accepted && state.granted) {
                        current.acquired = true
                        current.gained.complete(Unit)
                    } else if (current.acquired) {
                        current.acquired = false
                        onLost()
                    }
                }
            }
            val acquired = withTimeoutOrNull(3000) { current.gained.await(); true }
            if (acquired != true) throw Unavailable()
            if (lease !== current) throw CancellationException("Call audio acquisition cancelled")
            // Telecom has granted this ConnectionService call-resource ownership.
            // Another AudioManager request can queue behind Telecom's own locked focus.
            android.util.Log.i("FamilyCalls", "Audio focus granted: owner=Telecom")
        } catch (error: Exception) {
            if (lease === current) release()
            throw error
        }
    }

    fun release() {
        val previous = lease ?: return
        lease = null
        previous.observer?.cancel()
        previous.gained.cancel()
        previous.request?.let { audio.abandonAudioFocusRequest(it) }
    }
}
