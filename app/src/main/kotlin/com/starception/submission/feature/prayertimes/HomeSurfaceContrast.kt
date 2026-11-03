package com.starception.submission.feature.prayertimes

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

private val ReflectionInk = Color(0xFF0B1020)
private const val ReflectionOpaqueStart = .35f
private const val ReflectionOpaqueEnd = .68f

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
        ReflectionOpaqueStart to ReflectionInk.copy(alpha = .94f),
        ReflectionOpaqueEnd to ReflectionInk,
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
    isDarkTheme: Boolean,
    feedBackground: Color,
    readerExpanded: Boolean,
): Boolean {
    if (readerExpanded) return true
    if (coverHeight <= 0f) return coverUsesLightText
    val artworkTone = if (coverUsesLightText) Color(0xFF102033) else Color(0xFFDDE4E8)
    var background = lerp(
        artworkTone,
        if (isDarkTheme) Color(0xFF080B14) else Color.White,
        homeBackdropFeedOpacity(scrollOffset, coverHeight),
    )
    if (!isDarkTheme) {
        val reflectionStart = (coverHeight + 8f - 150f).coerceAtLeast(0f)
        val p = ((scrollOffset - reflectionStart) / 450f).coerceIn(0f, 1f)
        val overlay = when {
            p < ReflectionOpaqueStart -> lerp(Color.Transparent, ReflectionInk.copy(alpha = .94f), p / ReflectionOpaqueStart)
            p < ReflectionOpaqueEnd -> lerp(ReflectionInk.copy(alpha = .94f), ReflectionInk,
                (p - ReflectionOpaqueStart) / (ReflectionOpaqueEnd - ReflectionOpaqueStart))
            else -> lerp(ReflectionInk, feedBackground, (p - ReflectionOpaqueEnd) / (1f - ReflectionOpaqueEnd))
        }
        background = overlay.compositeOver(background)
    }
    return background.luminance() < .179f
}
