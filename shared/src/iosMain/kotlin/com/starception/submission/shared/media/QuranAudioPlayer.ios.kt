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

package com.starception.submission.shared.audio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.pause
import platform.AVFoundation.play
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
