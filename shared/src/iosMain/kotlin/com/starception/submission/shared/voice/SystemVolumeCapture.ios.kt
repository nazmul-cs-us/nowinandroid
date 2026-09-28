/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.shared.voice

import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

/**
 * Activates a playback audio session with near-silent looping audio —
 * this makes the HARDWARE VOLUME BUTTONS surface the iOS system volume
 * HUD while the session is active (the iOS counterpart of Android's
 * system volume bar). The user adjusts with hardware buttons; the popup
 * in the tile also carries a manual slider for direct control.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual class SystemVolumeCapture actual constructor() {
    private var player: AVPlayer? = null
    private var active = false

    actual fun start(onVolumeChanged: (percent: Int) -> Unit): Boolean {
        stop()
        val path = NSBundle.mainBundle.pathForResource("adhan_volume_silence", ofType = "caf")
            ?: return false
        val url = NSURL.fileURLWithPath(path)
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, withOptions = 0u, error = null)
        val prepared = AVPlayer(uRL = url)
        prepared.play()
        player = prepared
        active = true
        return true
    }

    actual fun stop() {
        if (!active) return
        player?.pause()
        player = null
        AVAudioSession.sharedInstance().setActive(false, withOptions = 0u, error = null)
        active = false
    }
}
