package com.starception.submission.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide progress for long tasks that are not CDN downloads.
 *
 * The pull-to-sync banner already renders determinate progress for asset downloads. Work
 * like synthesising a guided session's voice lines takes just as long and used to be
 * invisible outside the screen that started it. Posting here puts it in the same banner,
 * so it stays visible after navigating away.
 *
 * Real downloads win when both are active — [AppTaskProgressBus] is the fallback source.
 * Always pair [update] with [clear] (including on failure and cancellation), or the banner
 * sticks around forever.
 */
object AppTaskProgressBus {

    data class TaskProgress(
        val label: String,
        /** 0f..1f. Reported as at least [MIN_VISIBLE] so the banner shows before the first unit completes. */
        val progress: Float,
    )

    /** The banner treats 0f as "nothing happening", so a starting task must report above it. */
    private const val MIN_VISIBLE = 0.001f

    private val _state = MutableStateFlow<TaskProgress?>(null)
    val state: StateFlow<TaskProgress?> = _state.asStateFlow()

    fun update(label: String, done: Int, total: Int) {
        val fraction = if (total > 0) done.toFloat() / total else 0f
        _state.value = TaskProgress(label, fraction.coerceIn(MIN_VISIBLE, 1f))
    }

    fun clear() {
        _state.value = null
    }
}
