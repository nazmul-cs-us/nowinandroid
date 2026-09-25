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

package com.starception.submission.feature.surah

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Snapshot of the Mushaf reader visible to the app-level PullToSyncContainer.
 * Null entries mean "no mushaf on screen".
 */
data class MushafMiniBarState(
    val surahNumber: Int,
    val surahNameArabic: String,
    val surahNameEnglish: String,
    val currentPage: Int,
    val totalPages: Int,
)

/**
 * Bus the Mushaf reader pushes its state through so the strip can render a
 * page-aware mini-bar when no MediaMiniBar is up.
 */
object MushafMiniBarBus {
    val state = MutableStateFlow<MushafMiniBarState?>(null)
    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
    var onOpenInfo: (() -> Unit)? = null
    var onJumpToPage: ((Int) -> Unit)? = null
    private var activeOwner: Any? = null

    fun bind(
        owner: Any,
        next: () -> Unit,
        previous: () -> Unit,
        openInfo: () -> Unit,
        jumpToPage: (Int) -> Unit,
    ) {
        activeOwner = owner
        onNext = next
        onPrevious = previous
        onOpenInfo = openInfo
        onJumpToPage = jumpToPage
    }

    fun publish(owner: Any, newState: MushafMiniBarState) {
        if (activeOwner === owner) {
            state.value = newState
        }
    }

    fun unbind(owner: Any) {
        // AnimatedContent briefly keeps the outgoing and incoming Surah readers
        // composed together. The outgoing reader may dispose after the incoming
        // reader has already bound; it must not clear its replacement's strip.
        if (activeOwner !== owner) return
        activeOwner = null
        onNext = null
        onPrevious = null
        onOpenInfo = null
        onJumpToPage = null
        state.value = null
    }
}
