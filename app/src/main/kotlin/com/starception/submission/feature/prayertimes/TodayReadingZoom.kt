package com.starception.submission.feature.prayertimes

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

internal const val TODAY_READING_ZOOM_KEY = "today_reading_zoom"

/** A Home-only reading preference, independent of the full Surah reader's font size. */
@Composable
internal fun rememberTodayReadingZoom(preferences: SharedPreferences): MutableFloatState {
    val zoom = remember(preferences) {
        val saved = preferences.getFloat(TODAY_READING_ZOOM_KEY, 1f)
        mutableFloatStateOf(if (saved.isFinite()) saved.coerceIn(.75f, 3f) else 1f)
    }
    LaunchedEffect(preferences, zoom.floatValue) {
        // Avoid a disk write for every pinch frame.
        delay(350)
        preferences.edit().putFloat(TODAY_READING_ZOOM_KEY, zoom.floatValue).apply()
    }
    DisposableEffect(preferences, zoom) {
        // Closing immediately after a gesture must not lose the final zoom.
        onDispose { preferences.edit().putFloat(TODAY_READING_ZOOM_KEY, zoom.floatValue).apply() }
    }
    return zoom
}
