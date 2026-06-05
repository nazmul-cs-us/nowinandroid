package com.starception.submission.util

import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import com.starception.submission.core.model.data.ThemeBrand

/**
 * Bridge to expose Compose MaterialTheme colors to View-based code (Java).
 * This singleton holds the current theme colors so they can be accessed from anywhere.
 *
 * Also holds the active ThemeBrand/darkTheme/disableDynamicTheming so an inner
 * ComposeView (e.g. inside an AndroidView/CoordinatorLayout) can re-invoke
 * NiaTheme with the same brand the user picked in Settings — otherwise the
 * inner NiaTheme falls back to its COASTAL default and the body renders with
 * the wrong palette.
 */
object ThemeColorBridge {
    @Volatile
    private var _surfaceColor: Int = 0xFFFFFFFF.toInt() // White default

    @Volatile
    private var _onSurfaceColor: Int = 0xFF000000.toInt() // Black default

    val surfaceColor: Int get() = _surfaceColor
    val onSurfaceColor: Int get() = _onSurfaceColor

    // Observable state so inner ComposeViews re-compose when the user switches brand.
    var themeBrand by mutableStateOf(ThemeBrand.COASTAL)
        private set
    var darkTheme by mutableStateOf(false)
        private set
    var disableDynamicTheming by mutableStateOf(true)
        private set

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

    fun updateThemeConfig(
        brand: ThemeBrand,
        darkTheme: Boolean,
        disableDynamicTheming: Boolean,
    ) {
        this.themeBrand = brand
        this.darkTheme = darkTheme
        this.disableDynamicTheming = disableDynamicTheming
    }
}
