package com.starception.submission.feature.prayertimes

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

private const val ReflectionOpaqueStart = .35f
private const val ReflectionOpaqueEnd = .68f

/** Continue the reflection surface underneath floating navigation, not the photograph. */
internal fun DrawScope.drawHomeNavigationBackground(background: Color, clearance: Float) {
    val height = clearance.coerceIn(0f, size.height)
    if (height > 0f) drawRect(background, topLeft = Offset(0f, size.height - height),
        size = Size(size.width, height))
}

/** Fade artwork/glows to the same footer tone before the scroll viewport's clipping edge. */
internal fun DrawScope.drawHomeNavigationFade(background: Color, viewportBottom: Float) {
    val bottom = viewportBottom.coerceIn(0f, size.height)
    val top = (bottom - 96.dp.toPx()).coerceAtLeast(0f)
    if (bottom > top) drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, background), startY = top, endY = bottom),
        topLeft = Offset(0f, top), size = Size(size.width, bottom - top),
    )
}

/** PullToSyncContainer's tertiary strip is fully opaque at a .15 reveal. */
internal fun homeBannerUsesLightIcons(reveal: Float, bannerBackground: Color): Boolean? =
    if (reveal >= .15f) bannerBackground.luminance() < .179f else null

/** Shared with the painted home backdrop so status icons follow its fade to the feed. */
internal fun homeBackdropFeedOpacity(scrollOffset: Float, coverHeight: Float): Float =
    if (coverHeight <= 0f) 0f else
        ((scrollOffset - coverHeight * .35f) / (coverHeight * .50f)).coerceIn(0f, 1f)

internal fun homeReflectionScrim(startY: Float, endY: Float, feedBackground: Color): Brush =
    Brush.verticalGradient(
        0f to Color.Transparent,
        ReflectionOpaqueStart to feedBackground.copy(alpha = .94f),
        ReflectionOpaqueEnd to feedBackground,
        1f to feedBackground,
        startY = startY,
        endY = endY,
    )

/**
 * The home photograph is independent of the app's light/dark preference. Use
 * the cover's known ink contrast, then composite the same feed/reflection
 * scrims that pass beneath the status bar. Dimensions here are in dp.
 */
internal fun homeStatusBarUsesLightIcons(
    coverUsesLightText: Boolean,
    scrollOffset: Float,
    coverHeight: Float,
    feedBackground: Color,
    readerExpanded: Boolean,
): Boolean {
    if (readerExpanded) return true
    if (coverHeight <= 0f) return coverUsesLightText
    val artworkTone = if (coverUsesLightText) Color(0xFF102033) else Color(0xFFDDE4E8)
    var background = lerp(
        artworkTone,
        feedBackground,
        homeBackdropFeedOpacity(scrollOffset, coverHeight),
    )
    val reflectionStart = (coverHeight + 8f - 150f).coerceAtLeast(0f)
    val p = ((scrollOffset - reflectionStart) / 450f).coerceIn(0f, 1f)
    val overlay = when {
        p < ReflectionOpaqueStart -> lerp(Color.Transparent, feedBackground.copy(alpha = .94f), p / ReflectionOpaqueStart)
        p < ReflectionOpaqueEnd -> lerp(feedBackground.copy(alpha = .94f), feedBackground,
            (p - ReflectionOpaqueStart) / (ReflectionOpaqueEnd - ReflectionOpaqueStart))
        else -> feedBackground
    }
    background = overlay.compositeOver(background)
    return background.luminance() < .179f
}
