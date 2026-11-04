package com.starception.submission.shared.audio

import com.starception.submission.shared.platform.IosAudioSessionController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.Foundation.NSURL

/** A sheet-scoped streaming player; it never changes reciter preferences or Now Playing. */
@OptIn(ExperimentalForeignApi::class)
internal class IosReciterPreviewPlayback : ReciterPreviewPlayback {
    private var player: AVPlayer? = null
    private var started = false
    override fun play(url: String): Boolean {
        stop()
        val source = NSURL.URLWithString(url) ?: return false
        if (!IosAudioSessionController.activatePlayback()) return false
        player = AVPlayer(playerItem = AVPlayerItem(uRL = source)).also { it.play() }
        return true
    }
    override fun phase(): ReciterPreviewPlayback.Phase {
        val current = player ?: return ReciterPreviewPlayback.Phase.ENDED
        if (current.currentItem?.status == AVPlayerItemStatusFailed) return ReciterPreviewPlayback.Phase.FAILED
        if (current.timeControlStatus == AVPlayerTimeControlStatusPlaying) {
            started = true
            return ReciterPreviewPlayback.Phase.PLAYING
        }
        return if (started && current.timeControlStatus == AVPlayerTimeControlStatusPaused)
            ReciterPreviewPlayback.Phase.ENDED else ReciterPreviewPlayback.Phase.BUFFERING
    }
    override fun stop() {
        val old = player
        player = null
        started = false
        old?.pause()
        if (old != null) IosAudioSessionController.deactivate()
    }
}
