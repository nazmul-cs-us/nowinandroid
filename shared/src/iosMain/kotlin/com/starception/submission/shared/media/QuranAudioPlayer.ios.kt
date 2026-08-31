package com.starception.submission.shared.audio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
actual class QuranAudioPlayer actual constructor() {
    private var player: AVPlayer? = null
    private var currentUrl: String? = null

    actual fun play(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, withOptions = 0u, error = null)
        if (url != currentUrl) {
            player = AVPlayer(playerItem = AVPlayerItem(uRL = nsUrl))
            currentUrl = url
        }
        player?.play()
        return player != null
    }

    actual fun pause() {
        player?.pause()
    }

    actual fun stop() {
        player?.pause()
        player = null
        currentUrl = null
        AVAudioSession.sharedInstance().setActive(false, withOptions = 0u, error = null)
    }
}
