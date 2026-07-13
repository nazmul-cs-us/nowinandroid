package com.starception.submission.ui.search

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide bus for "open the search bar pre-filled with this query" requests.
 *
 * Used by ambient surfaces that live outside [com.starception.submission.ui.AppTopSearchBar]
 * (e.g. the Islamic-event banner in PullToSyncContainer) so they can hand off
 * to the same search experience without needing a direct reference to the
 * SearchView. AppTopSearchBar collects from this bus and reacts by opening
 * its SearchView and filling the EditText.
 *
 * Uses [MutableSharedFlow] (not StateFlow) so re-emitting the same query
 * re-opens the search bar, and one buffered slot guarantees a tap is never
 * dropped while no collectors are active during a recomposition.
 */
object SearchPrefillBus {
    private val _requests = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val requests: SharedFlow<String> = _requests.asSharedFlow()

    fun requestSearch(query: String) {
        _requests.tryEmit(query)
    }

    // "Start voice search" requests from ambient surfaces (e.g. the home
    // screen's bottom Ask bar) — AppTopSearchBar collects and runs the same
    // mic flow as its own mic button (permission check, model download page,
    // Whisper capture).
    private val _voiceRequests = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val voiceRequests: SharedFlow<Unit> = _voiceRequests.asSharedFlow()

    fun requestVoiceSearch() {
        _voiceRequests.tryEmit(Unit)
    }

    // Whether the search overlay (AppTopSearchBar's SearchView) is currently
    // open — ambient surfaces like the home Ask bar hide themselves while the
    // user is inside search.
    private val _isSearchOpen = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isSearchOpen: kotlinx.coroutines.flow.StateFlow<Boolean> = _isSearchOpen

    fun setSearchOpen(open: Boolean) {
        _isSearchOpen.value = open
    }
}
