package com.starception.submission.util

import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb

/**
 * Bridge to expose Compose MaterialTheme colors to View-based code (Java).
 * This singleton holds the current theme colors so they can be accessed from anywhere.
 */
object ThemeColorBridge {
    @Volatile
    private var _surfaceColor: Int = 0xFFFFFFFF.toInt() // White default

    @Volatile
    private var _onSurfaceColor: Int = 0xFF000000.toInt() // Black default

    val surfaceColor: Int get() = _surfaceColor
    val onSurfaceColor: Int get() = _onSurfaceColor

    /**
     * Call this from Compose to update the current theme colors.
     * This should be called whenever MaterialTheme changes.
     */
    @Composable
    fun UpdateColors() {
        val colorScheme = MaterialTheme.colorScheme
        val newSurfaceColor = colorScheme.surface.toArgb()
        val newOnSurfaceColor = colorScheme.onSurface.toArgb()

        // Update colors
        _surfaceColor = newSurfaceColor
        _onSurfaceColor = newOnSurfaceColor

        // Log for debugging
        Log.d("ThemeColorBridge", "Colors updated - Surface: ${Integer.toHexString(newSurfaceColor)}, OnSurface: ${Integer.toHexString(newOnSurfaceColor)}")
    }
}
