package com.starception.submission.family

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Main-thread audio ownership; capture starts only after focus is granted. */
internal class FamilyAudioFocus(private val audio: AudioManager) {
    class Unavailable : IllegalStateException("call_audio_focus_unavailable")
    private class Lease(val request: AudioFocusRequest, val gained: CompletableDeferred<Unit>) {
        var acquired = false
    }
    private var lease: Lease? = null

    suspend fun acquire(onLost: () -> Unit) {
        release()
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

    fun release() {
        val previous = lease ?: return
        lease = null
        previous.gained.cancel()
        audio.abandonAudioFocusRequest(previous.request)
    }
}
