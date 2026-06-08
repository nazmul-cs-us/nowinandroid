package com.starception.submission.feature.surah

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Snapshot of the Mushaf reader visible to the app-level PullToSyncContainer.
 * Null entries mean "no mushaf on screen".
 */
data class MushafMiniBarState(
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

    fun bind(next: () -> Unit, previous: () -> Unit) {
        onNext = next
        onPrevious = previous
    }

    fun unbind() {
        onNext = null
        onPrevious = null
        state.value = null
    }
}
