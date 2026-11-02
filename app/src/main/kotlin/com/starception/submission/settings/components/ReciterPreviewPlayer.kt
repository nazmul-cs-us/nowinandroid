package com.starception.submission.settings.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Sheet-scoped streaming previews; never updates reciter preferences or the media session. */
internal class ReciterPreviewPlayer(context: Context) {
    var activeId: String? by mutableStateOf(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            if (change < 0) stop()
        }.build()
    private var player: MediaPlayer? = null
    private val endPreview = Runnable { stop() }
    private val loadingTimeout = Runnable { fail() }

    fun toggle(id: String, url: String, onStart: () -> Unit) {
        if (activeId == id) {
            stop()
            return
        }
        stop()
        error = null
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            error = "Audio is busy. Try previewing again."
            return
        }
        activeId = id
        loading = true
        try {
            onStart()
            val next = MediaPlayer()
            player = next
            next.setAudioAttributes(attributes)
            next.setOnPreparedListener { prepared ->
                if (player === prepared) {
                    handler.removeCallbacks(loadingTimeout)
                    try {
                        prepared.start()
                        loading = false
                        handler.postDelayed(endPreview, 20_000L)
                    } catch (_: Exception) {
                        fail()
                    }
                }
            }
            next.setOnCompletionListener { completed -> if (player === completed) stop() }
            next.setOnErrorListener { failed, _, _ ->
                if (player === failed) fail()
                true
            }
            next.setDataSource(url)
            next.prepareAsync()
            handler.postDelayed(loadingTimeout, 15_000L)
        } catch (_: Exception) {
            fail()
        }
    }

    private fun fail() {
        stop()
        error = "Preview unavailable. Check your connection and try again."
    }

    fun stop() {
        handler.removeCallbacks(endPreview)
        handler.removeCallbacks(loadingTimeout)
        val previous = player
        player = null
        previous?.setOnPreparedListener(null)
        previous?.setOnCompletionListener(null)
        previous?.setOnErrorListener(null)
        previous?.release()
        audioManager.abandonAudioFocusRequest(focusRequest)
        activeId = null
        loading = false
    }
}
