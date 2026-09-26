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

package com.starception.submission.shared.travel

import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.darwin.NSObjectProtocol

/**
 * Plays the bundled travel_dua.caf through AVPlayer (the same engine the
 * shared Quran player uses), keeping the playback session alive so the dua
 * continues in the background like the Android foreground-service chain.
 */
@kotlinx.cinterop.ExperimentalForeignApi
actual class TravelDuaPlayer actual constructor() {
    private var player: AVPlayer? = null
    private var observer: NSObjectProtocol? = null
    private var onComplete: (() -> Unit)? = null

    actual fun play(onComplete: () -> Unit) {
        stop()
        val path = platform.Foundation.NSBundle.mainBundle.pathForResource("travel_dua", ofType = "caf")
            ?: return onComplete()
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, withOptions = 0u, error = null)
        this.onComplete = onComplete
        val url = NSURL.fileURLWithPath(path)
        val item = AVPlayerItem(uRL = url)
        val avPlayer = AVPlayer(playerItem = item)
        observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
            usingBlock = { _ -> complete() },
        )
        avPlayer.play()
        player = avPlayer
    }

    actual fun stop() {
        observer?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observer = null
        player?.pause()
        player = null
        AVAudioSession.sharedInstance().setActive(false, withOptions = 0u, error = null)
        complete()
    }

    private fun complete() {
        val callback = onComplete
        onComplete = null
        callback?.invoke()
    }
}
