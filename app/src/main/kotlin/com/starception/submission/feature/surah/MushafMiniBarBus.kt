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
