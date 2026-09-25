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

package com.starception.submission.ui.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide phrase rotation for the SearchBar hint.
 *
 * Why a singleton: every top-level destination wraps itself in its own
 * [com.starception.submission.ui.TopLevelTopBarScaffold], so switching pages
 * tears down and reinflates the SearchBar each time. If the typewriter ran
 * inside the composable it would restart from the first phrase on every nav
 * change. Lifting it here lets every freshly-composed bar read from the same
 * [hintText] flow and resume mid-cycle without flicker.
 *
 * The coroutine self-pauses when no one is subscribed (the StateFlow has no
 * collectors), so an unused process doesn't burn cycles either.
 */
object SearchHintAnimator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _hintText = MutableStateFlow(SearchHints.hintFor())
    val hintText: StateFlow<String> = _hintText.asStateFlow()

    private val startMutex = Mutex()
    private var started = false

    /**
     * Idempotently kicks off the typewriter loop. Safe to call from every
     * composition of every [com.starception.submission.ui.AppTopSearchBar];
     * only the first call actually starts the coroutine.
     */
    fun ensureStarted() {
        if (started) return
        scope.launch {
            startMutex.withLock {
                if (started) return@launch
                started = true
            }
            runLoop()
        }
    }

    private suspend fun runLoop() {
        while (true) {
            // Hold each complete phrase long enough to stay readable. The view
            // layer owns the smooth vertical fade between phrases; emitting
            // character substrings here made every update look like a reset.
            delay(180_000)

            // Re-pick the slot's hints on each cycle so the rotation follows a
            // prayer-time band change without restarting the process singleton.
            val hints = SearchHints.rotatingHintsFor()
            val currentIndex = hints.indexOf(_hintText.value)
            val nextIndex = if (currentIndex >= 0) {
                (currentIndex + 1) % hints.size
            } else {
                0
            }
            _hintText.value = hints[nextIndex]
        }
    }
}
