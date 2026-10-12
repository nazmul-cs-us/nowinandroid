/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.shared.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter

/**
 * Lock-screen and Control Center controls for the detached hadith narration
 * queue: play/pause toggles the current narration, next/previous jump
 * hadiths, and the Now Playing card shows the narrated hadith. Installed once
 * from the shared UI bootstrap; the queue keeps running when the hadith screen
 * is gone, so the controls stay wired for the whole app lifetime.
 */
object NarrationNowPlaying {
    private var installed = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun install() {
        if (installed) return
        installed = true
        val center = MPRemoteCommandCenter.sharedCommandCenter()
        center.togglePlayPauseCommand.addTargetWithHandler {
            NarrationQueue.togglePause()
            platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
        }
        center.pauseCommand.addTargetWithHandler {
            if (NarrationQueue.state.value.playing) NarrationQueue.togglePause()
            platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
        }
        center.playCommand.addTargetWithHandler {
            if (NarrationQueue.state.value.paused) NarrationQueue.togglePause()
            platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
        }
        center.nextTrackCommand.addTargetWithHandler {
            NarrationQueue.skipNext()
            platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
        }
        center.previousTrackCommand.addTargetWithHandler {
            NarrationQueue.skipPrevious()
            platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
        }
        scope.launch {
            NarrationQueue.state.collect { state ->
                val infoCenter = MPNowPlayingInfoCenter.defaultCenter()
                if (state.hadithId == null) {
                    infoCenter.nowPlayingInfo = null
                    return@collect
                }
                infoCenter.nowPlayingInfo = mapOf(
                    MPMediaItemPropertyTitle to "Sahih al-Bukhari · Hadith ${state.hadithId}",
                    MPMediaItemPropertyArtist to "Deenly",
                    MPMediaItemPropertyAlbumTitle to "Hadith ${state.index + 1} of ${state.queueSize}",
                    MPNowPlayingInfoPropertyPlaybackRate to if (state.playing) 1.0 else 0.0,
                )
            }
        }
    }
}
