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

package com.starception.submission.media

import com.starception.submission.feature.quran.AudioLanguage

/**
 * Represents the source of the currently playing media.
 */
sealed class MediaSource {
    /** Quran surah playback via QuranPlaybackService */
    data class Quran(
        val surahIndex: Int = 0,
        val audioLanguage: AudioLanguage = AudioLanguage.ARABIC_ONLY,
    ) : MediaSource()

    /** Driving mode audio chain via DrivingAudioService */
    data class DrivingMode(
        val phase: String = "",
    ) : MediaSource()

    /** Hadith playback from HadithDetailScreen */
    data class Hadith(
        val hadithNumber: Int = 0,
        val collectionName: String = "Hadith",
    ) : MediaSource()

    /** Fortress-of-the-Muslim chapter recitation from a news card (ChapterAudioController) */
    data class Fortress(
        val title: String = "",
    ) : MediaSource()

    /** No active media source */
    data object None : MediaSource()
}

/**
 * Unified playback state for any audio source.
 */
data class MediaPlaybackState(
    val isPlaying: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val currentPosition: Int = 0,
    val duration: Int = 0,
    val source: MediaSource = MediaSource.None,
)

/**
 * Sealed class for media controller actions dispatched from UI.
 */
sealed class MediaAction {
    data object Play : MediaAction()
    data object Pause : MediaAction()
    data object SkipNext : MediaAction()
    data object SkipPrevious : MediaAction()
    data class SeekTo(val position: Int) : MediaAction()
    data class SetVolume(val volume: Float) : MediaAction()
    data object ToggleLanguage : MediaAction()
    data object Dismiss : MediaAction()
}

/**
 * UI state for the global media controller.
 */
data class MediaControllerUiState(
    val isVisible: Boolean = false,
    val playback: MediaPlaybackState = MediaPlaybackState(),
    val volume: Float = 1f,
    val hasLanguageToggle: Boolean = false,
    val currentLanguage: AudioLanguage = AudioLanguage.ARABIC_ONLY,
)
