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

import com.starception.submission.shared.media.QuranPlaybackBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPNowPlayingPlaybackStatePaused
import platform.MediaPlayer.MPNowPlayingPlaybackStatePlaying
import platform.MediaPlayer.MPNowPlayingPlaybackStateStopped
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.beginReceivingRemoteControlEvents

/** One Lock Screen and Control Center coordinator for every iOS audio source. */
object NarrationNowPlaying {
    private data class ExternalAudio(
        val token: Any,
        val title: String,
        val albumTitle: String,
        val playing: Boolean,
        val onPlay: () -> Unit,
        val onPause: () -> Unit,
        val onStop: () -> Unit,
    )

    private var installed = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val externalAudio = MutableStateFlow<ExternalAudio?>(null)
    private val commandCenter by lazy { MPRemoteCommandCenter.sharedCommandCenter() }
    private val artwork by lazy {
        UIImage.imageNamed("DeenlyNowPlayingArtwork")?.let(::MPMediaItemArtwork)
    }

    fun install() {
        if (installed) return
        installed = true
        UIApplication.sharedApplication.beginReceivingRemoteControlEvents()
        installRemoteCommands()
        scope.launch {
            combine(NarrationQueue.state, QuranPlaybackBus.state, externalAudio) {
                    narration, quran, external ->
                Triple(narration, quran, external)
            }.collect { (narration, quran, external) ->
                when {
                    narration.hadithId != null -> publishNarration(narration)
                    external != null -> publishExternal(external)
                    quran != null -> publishQuran(quran)
                    else -> clearSystemNowPlaying()
                }
            }
        }
    }

    internal fun showExternalAudio(
        title: String,
        albumTitle: String,
        onPlay: () -> Unit,
        onPause: () -> Unit,
        onStop: () -> Unit,
    ): Any {
        val token = Any()
        externalAudio.value = ExternalAudio(
            token = token,
            title = title,
            albumTitle = albumTitle,
            playing = true,
            onPlay = onPlay,
            onPause = onPause,
            onStop = onStop,
        )
        return token
    }

    internal fun updateExternalAudio(token: Any?, playing: Boolean) {
        val current = externalAudio.value ?: return
        if (current.token === token) externalAudio.value = current.copy(playing = playing)
    }

    internal fun clearExternalAudio(token: Any?) {
        if (externalAudio.value?.token === token) externalAudio.value = null
    }

    private fun installRemoteCommands() {
        commandCenter.togglePlayPauseCommand.addTargetWithHandler {
            when {
                NarrationQueue.state.value.hadithId != null -> NarrationQueue.togglePause()
                externalAudio.value != null -> toggleExternal()
                QuranPlaybackBus.state.value != null -> QuranPlaybackBus.toggle()
                else -> return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            }
            MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.pauseCommand.addTargetWithHandler {
            when {
                NarrationQueue.state.value.hadithId != null -> {
                    if (NarrationQueue.state.value.playing) NarrationQueue.togglePause()
                }
                externalAudio.value != null -> {
                    if (externalAudio.value?.playing == true) pauseExternal()
                }
                QuranPlaybackBus.state.value != null -> {
                    if (QuranPlaybackBus.state.value?.playing == true) QuranPlaybackBus.toggle()
                }
                else -> return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            }
            MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.playCommand.addTargetWithHandler {
            when {
                NarrationQueue.state.value.hadithId != null -> {
                    if (NarrationQueue.state.value.paused) NarrationQueue.togglePause()
                }
                externalAudio.value != null -> {
                    if (externalAudio.value?.playing == false) playExternal()
                }
                QuranPlaybackBus.state.value != null -> {
                    if (QuranPlaybackBus.state.value?.playing == false) QuranPlaybackBus.toggle()
                }
                else -> return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            }
            MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.stopCommand.addTargetWithHandler {
            when {
                NarrationQueue.state.value.hadithId != null -> NarrationQueue.stop()
                externalAudio.value != null -> stopExternal()
                QuranPlaybackBus.state.value != null -> QuranPlaybackBus.stop()
                else -> return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            }
            MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.nextTrackCommand.addTargetWithHandler {
            if (NarrationQueue.state.value.hadithId == null) {
                return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            }
            NarrationQueue.skipNext()
            MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.previousTrackCommand.addTargetWithHandler {
            if (NarrationQueue.state.value.hadithId == null) {
                return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            }
            NarrationQueue.skipPrevious()
            MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val position = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime
                ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            if (NarrationQueue.state.value.hadithId != null ||
                externalAudio.value != null ||
                QuranPlaybackBus.state.value == null
            ) {
                return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            }
            QuranPlaybackBus.seekTo(position.toFloat())
            MPRemoteCommandHandlerStatusSuccess
        }
    }

    private fun publishNarration(state: NarrationQueue.NarrationState) {
        val collection = when (state.collection) {
            NarrationQueue.Collection.BUKHARI -> "Sahih al-Bukhari"
            NarrationQueue.Collection.SHAMAYEL -> "Ash-Shama'il al-Muhammadiyah"
        }
        publish(
            title = "$collection · Hadith ${state.hadithId}",
            albumTitle = if (state.queueSize > 1) {
                "Hadith ${state.index + 1} of ${state.queueSize}"
            } else {
                collection
            },
            playing = state.playing,
        )
        configureCommands(hasAudio = true, canSkip = state.queueSize > 1, canSeek = false)
    }

    private fun publishQuran(state: QuranPlaybackBus.PlaybackState) {
        publish(
            title = state.title,
            albumTitle = if (state.surahNumber != null) "Quran recitation" else "Dua & remembrance",
            playing = state.playing,
            elapsedSeconds = state.positionSeconds.toDouble(),
            durationSeconds = state.durationSeconds.toDouble(),
        )
        configureCommands(hasAudio = true, canSkip = false, canSeek = state.durationSeconds > 0f)
    }

    private fun publishExternal(state: ExternalAudio) {
        publish(state.title, state.albumTitle, state.playing)
        configureCommands(hasAudio = true, canSkip = false, canSeek = false)
    }

    private fun publish(
        title: String,
        albumTitle: String,
        playing: Boolean,
        elapsedSeconds: Double? = null,
        durationSeconds: Double? = null,
    ) {
        val info = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to title,
            MPMediaItemPropertyArtist to "Deenly",
            MPMediaItemPropertyAlbumTitle to albumTitle,
            MPNowPlayingInfoPropertyPlaybackRate to if (playing) 1.0 else 0.0,
        )
        artwork?.let { info[MPMediaItemPropertyArtwork] = it }
        elapsedSeconds?.let { info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = it }
        durationSeconds?.takeIf { it > 0.0 }?.let { info[MPMediaItemPropertyPlaybackDuration] = it }
        MPNowPlayingInfoCenter.defaultCenter().apply {
            nowPlayingInfo = info
            playbackState = if (playing) {
                MPNowPlayingPlaybackStatePlaying
            } else {
                MPNowPlayingPlaybackStatePaused
            }
        }
    }

    private fun clearSystemNowPlaying() {
        configureCommands(hasAudio = false, canSkip = false, canSeek = false)
        MPNowPlayingInfoCenter.defaultCenter().apply {
            nowPlayingInfo = null
            playbackState = MPNowPlayingPlaybackStateStopped
        }
    }

    private fun configureCommands(hasAudio: Boolean, canSkip: Boolean, canSeek: Boolean) {
        commandCenter.togglePlayPauseCommand.enabled = hasAudio
        commandCenter.playCommand.enabled = hasAudio
        commandCenter.pauseCommand.enabled = hasAudio
        commandCenter.stopCommand.enabled = hasAudio
        commandCenter.nextTrackCommand.enabled = canSkip
        commandCenter.previousTrackCommand.enabled = canSkip
        commandCenter.changePlaybackPositionCommand.enabled = canSeek
    }

    private fun toggleExternal() {
        if (externalAudio.value?.playing == true) pauseExternal() else playExternal()
    }

    private fun playExternal() {
        val current = externalAudio.value ?: return
        current.onPlay()
        updateExternalAudio(current.token, playing = true)
    }

    private fun pauseExternal() {
        val current = externalAudio.value ?: return
        current.onPause()
        updateExternalAudio(current.token, playing = false)
    }

    private fun stopExternal() {
        val current = externalAudio.value ?: return
        current.onStop()
        clearExternalAudio(current.token)
    }
}
