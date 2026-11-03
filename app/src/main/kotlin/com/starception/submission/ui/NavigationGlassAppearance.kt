package com.starception.submission.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Stable
internal class NavigationGlassAppearance(initialDark: Boolean) {
    var dark by mutableStateOf(initialDark)
        internal set
    internal var coordinates: LayoutCoordinates? = null
    internal var recorded = false
    var samplingModifier: Modifier = Modifier
        internal set
}

/** Hysteresis prevents text flickering when mixed content crosses the bar. */
internal fun navigationUsesDarkGlass(luminance: Float, currentlyDark: Boolean): Boolean =
    if (currentlyDark) luminance < .48f else luminance < .32f

/**
 * Samples only 48 pixels of the underlying page, never the navigation itself.
 * The retained layer references the live backdrop; no full-window bitmap is read
 * back. Sampling is capped at four times a second and stops offscreen/paused.
 */
@Composable
internal fun rememberNavigationGlassAppearance(
    backdrop: Backdrop,
    initialDark: Boolean,
): NavigationGlassAppearance {
    val appearance = remember(backdrop) { NavigationGlassAppearance(initialDark) }
    val sample = rememberGraphicsLayer()
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    appearance.samplingModifier = Modifier
        .onGloballyPositioned { appearance.coordinates = it }
        .drawWithContent {
            val coordinates = appearance.coordinates
            if (coordinates?.isAttached == true && size.width > 0f && size.height > 0f) {
                val scaleX = 12f / size.width
                val scaleY = 4f / size.height
                sample.record(size = IntSize(12, 4)) {
                    scale(scaleX, scaleY, pivot = Offset.Zero) {
                        with(backdrop) { drawBackdrop(density, coordinates) }
                    }
                }
                appearance.recorded = true
            }
            drawContent()
        }
    LaunchedEffect(sample, lifecycle, appearance) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                if (appearance.recorded && appearance.coordinates?.isAttached == true) {
                    val pixels = sample.toImageBitmap().toPixelMap()
                    var luminance = 0f
                    var count = 0
                    for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                        val color = pixels[x, y]
                        if (color.alpha > .9f) {
                            luminance += color.luminance()
                            count++
                        }
                    }
                    if (count > 0) {
                        appearance.dark = navigationUsesDarkGlass(luminance / count, appearance.dark)
                    }
                }
                delay(250)
            }
        }
    }
    return appearance
}
