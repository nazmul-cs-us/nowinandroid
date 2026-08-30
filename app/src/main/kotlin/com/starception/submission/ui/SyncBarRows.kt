/*
 * Copyright 2022 The Android Open Source Project
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

package com.starception.submission.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import com.starception.submission.feature.prayertimes.wobble.SyncBarRow
import com.starception.submission.feature.surah.MushafMiniBar
import com.starception.submission.feature.surah.MushafMiniBarState
import com.starception.submission.media.MediaAction
import com.starception.submission.media.MediaControllerUiState
import com.starception.submission.media.MediaMiniBar

/**
 * Android's rows for the shared [PullToSyncContainer][com.starception.submission
 * .feature.prayertimes.wobble.PullToSyncContainer].
 *
 * The container moved to :core:components so iOS uses the same pull, wobble and
 * banner behaviour. Media playback and the Mushaf reader are Android-only
 * features, so what fills the bar row is passed in from here rather than
 * imported by the container.
 */
@Composable
fun mediaSyncBarRow(
    state: MediaControllerUiState,
    onAction: (MediaAction) -> Unit,
    onTitleClick: () -> Unit,
    isTtsPreparing: Boolean,
): SyncBarRow {
    val hapticFeedback = LocalHapticFeedback.current
    return SyncBarRow(
        isVisible = state.isVisible,
        progress = if (state.playback.duration > 0) {
            (state.playback.currentPosition.toFloat() / state.playback.duration.toFloat())
                .coerceIn(0f, 1f)
        } else {
            0f
        },
    ) { statusText ->
        // Pull-up-to-dismiss is scoped to the title inside MediaMiniBar
        // (via titleDragModifier) so playback button taps are not swallowed.
        MediaMiniBar(
            state = state,
            onAction = onAction,
            onTitleClick = onTitleClick,
            preparingAudio = isTtsPreparing,
            statusText = statusText,
            // The strip's own sweep is the playback position, so a track under
            // the row would state it a second time.
            showProgressLine = false,
            titleDragModifier = Modifier.pointerInput(Unit) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        if (totalDrag < -80f) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAction(MediaAction.Dismiss)
                        }
                    },
                )
            },
        )
    }
}

@Composable
fun mushafSyncBarRow(
    state: MushafMiniBarState?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenInfo: () -> Unit,
    onJumpToPage: (Int) -> Unit,
): SyncBarRow? {
    if (state == null) return null
    return SyncBarRow(
        isVisible = true,
        progress = if (state.totalPages > 0) {
            (state.currentPage.toFloat() / state.totalPages.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        },
    ) { statusText ->
        MushafMiniBar(
            state = state,
            onPrevious = onPrevious,
            onNext = onNext,
            onOpenInfo = onOpenInfo,
            onJumpToPage = onJumpToPage,
            statusText = statusText,
            // The strip's sweep is already the page position; drawing the same
            // fraction twice reads as clutter.
            showProgressLine = false,
        )
    }
}
