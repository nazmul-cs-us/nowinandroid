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
 * Process-wide typewriter loop for the SearchBar hint.
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
        var idx = 0
        while (true) {
            // Re-pick the slot's hints on each cycle so the rotation tracks
            // the current time of day (slots roll over mid-loop without a restart).
            val hints = SearchHints.rotatingHintsFor()
            val target = hints[idx % hints.size]
            // Type out — frame-aligned (~16ms ≈ one display frame) so each
            // character lands on its own frame and the cadence feels even
            // instead of micro-stuttering between irregular delays. Three
            // frames per char (~48ms) reads as a natural typing speed.
            for (i in 1..target.length) {
                _hintText.value = target.substring(0, i)
                delay(48)
            }
            // Hold for 3 minutes — long enough that the hint feels stable
            // instead of flickering, short enough that an idle user still
            // sees a fresh suggestion if they linger.
            delay(180_000)
            // Erase — two frames per char (~32ms) so it clears noticeably
            // faster than it was typed, matching the natural rhythm.
            for (i in target.length - 1 downTo 0) {
                _hintText.value = target.substring(0, i)
                delay(32)
            }
            // Beat between hints so the next type-in doesn't slam in.
            delay(600)
            idx++
        }
    }
}
