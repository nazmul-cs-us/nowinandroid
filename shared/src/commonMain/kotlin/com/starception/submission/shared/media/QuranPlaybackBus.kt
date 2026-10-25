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

package com.starception.submission.shared.media

import com.starception.submission.shared.audio.QuranAudioPlayer
import com.starception.submission.shared.audio.quranAudioUrl
import com.starception.submission.feature.quran.QuranData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * App-wide Quran/Fortress playback state — the shared counterpart of
 * Android's MediaMiniBar bus. Screens report playback as they start it; the
 * bus then owns the truth: it polls the player, so pause, resume, and natural
 * end-of-track all surface here without every screen reporting them. The
 * home strip renders the playing item from [state], matching Android's
 * pull-to-sync mini-bar.
 */
object QuranPlaybackBus {
    data class PlaybackState(
        /** Surah number for Quran recitations; null for Fortress invocations. */
        val surahNumber: Int? = null,
        val title: String,
        val playing: Boolean,
        val positionSeconds: Float,
        val durationSeconds: Float,
    )

    private const val POLL_MILLIS = 500L
    private const val END_EPSILON_SECONDS = 1.5f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<PlaybackState?>(null)
    private var ticker: Job? = null
    private var activePlayer: QuranAudioPlayer? = null
    private var activeUrl: String? = null

    /** The playing item, or null when nothing plays. The home strip's data source. */
    val state: StateFlow<PlaybackState?> = _state.asStateFlow()

    /** Reports a surah recitation that just started; resolves its name async. */
    fun startSurah(
        player: QuranAudioPlayer,
        surahNumber: Int,
        audioUrl: String = quranAudioUrl(surahNumber),
    ) {
        val title = QuranData.surahs
            .firstOrNull { it.number == surahNumber }
            ?.nameEnglish
            ?.takeIf(String::isNotBlank)
            ?: "Surah $surahNumber"
        begin(player, audioUrl, title, surahNumber)
    }

    /** Reports a Fortress invocation that just started. */
    fun startFortress(player: QuranAudioPlayer, audioUrl: String, title: String) {
        begin(player, audioUrl, title.ifBlank { "Fortress of the Muslim" }, surahNumber = null)
    }

    /** Pauses when playing and resumes when paused — the mini-bar's button. */
    fun toggle() {
        val player = activePlayer ?: return
        val current = _state.value ?: return
        if (current.playing) {
            player.pause()
            _state.value = current.copy(playing = false)
        } else {
            val url = activeUrl ?: return
            if (player.play(url)) {
                _state.value = current.copy(playing = true)
            }
        }
    }

    /** Seeks the active recitation from the system Now Playing scrubber. */
    fun seekTo(seconds: Float) {
        val player = activePlayer ?: return
        val current = _state.value ?: return
        val target = seconds.coerceIn(0f, current.durationSeconds.coerceAtLeast(0f))
        player.seekTo(target)
        _state.value = current.copy(positionSeconds = target)
    }

    /** Stops the active player and removes its system and in-app playback state. */
    fun stop() {
        activePlayer?.stop()
        clear()
    }

    /** Clears the row — explicit stops and natural end-of-track. */
    fun clear() {
        ticker?.cancel()
        ticker = null
        activePlayer = null
        activeUrl = null
        _state.value = null
    }

    private fun begin(
        player: QuranAudioPlayer,
        url: String,
        fallbackTitle: String,
        surahNumber: Int?,
    ) {
        activePlayer = player
        activeUrl = url
        _state.value = PlaybackState(
            surahNumber = surahNumber,
            title = fallbackTitle,
            playing = true,
            positionSeconds = 0f,
            durationSeconds = player.durationSeconds(),
        )
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(POLL_MILLIS)
                val boundPlayer = activePlayer ?: return@launch
                val playing = boundPlayer.isPlaying()
                val position = boundPlayer.positionSeconds()
                val duration = boundPlayer.durationSeconds()
                val current = _state.value ?: return@launch
                // A finished track reports paused with the playhead at the
                // end; a user pause reports paused mid-track and keeps the row
                // until an explicit stop clears it.
                val finished = !playing && duration > 0f &&
                    position >= duration - END_EPSILON_SECONDS
                if (finished) {
                    boundPlayer.stop()
                    clear()
                    return@launch
                }
                _state.value = current.copy(
                    playing = playing,
                    positionSeconds = position,
                    durationSeconds = duration,
                )
            }
        }
    }
}
