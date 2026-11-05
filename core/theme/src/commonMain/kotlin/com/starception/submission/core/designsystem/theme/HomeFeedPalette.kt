package com.starception.submission.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp

/** The photographic Home feed keeps light copy, with ink and glows from the selected theme. */
data class HomeFeedPalette(
    val background: Color,
    val primaryGlow: Color,
    val secondaryGlow: Color,
    val primarySurface: Color,
    val secondarySurface: Color,
)

fun homeFeedPalette(scheme: ColorScheme): HomeFeedPalette {
    // Use a dark tonal base in both display modes so the photographic feed's
    // white text stays legible. The hue still follows the current Material roles.
    val background = scheme.primary.copy(alpha = .18f).compositeOver(scheme.scrim)
    return HomeFeedPalette(
        background = background,
        primaryGlow = scheme.primary.copy(alpha = .60f).compositeOver(scheme.scrim),
        secondaryGlow = scheme.secondary.copy(alpha = .60f).compositeOver(scheme.scrim),
        primarySurface = scheme.primary.copy(alpha = .22f).compositeOver(background),
        secondarySurface = scheme.secondary.copy(alpha = .20f).compositeOver(background),
    )
}

/** Shared Android/iOS painting, captured from the outer app theme before any feed-local theme. */
fun DrawScope.drawHomeFeedGlow(palette: HomeFeedPalette, coverBottom: Float) {
    val glowY = coverBottom + 118.dp.toPx()
    val glowTop = (coverBottom - 220.dp.toPx()).coerceAtLeast(0f)
    val glowBottom = (coverBottom + 450.dp.toPx()).coerceAtMost(size.height)
    if (glowBottom > glowTop) clipRect(top = glowTop, bottom = glowBottom) {
        drawRect(Brush.radialGradient(
            0f to palette.primaryGlow.copy(alpha = .48f),
            .32f to palette.primaryGlow.copy(alpha = .20f),
            .74f to Color.Transparent,
            center = Offset(size.width * .08f, glowY),
            radius = size.width * 1.08f,
        ))
        drawRect(Brush.radialGradient(
            0f to palette.secondaryGlow.copy(alpha = .34f),
            .38f to palette.secondaryGlow.copy(alpha = .12f),
            .76f to Color.Transparent,
            center = Offset(size.width * .82f, glowY + 26.dp.toPx()),
            radius = size.width * 1.08f,
        ))
    }
    val feedStart = coverBottom + 420.dp.toPx()
    if (size.height > feedStart) drawRect(
        brush = Brush.verticalGradient(
            0f to palette.background,
            .42f to palette.primarySurface,
            .76f to palette.secondarySurface,
            1f to palette.background,
            startY = feedStart,
            endY = size.height,
        ),
        topLeft = Offset(0f, feedStart),
        size = Size(size.width, size.height - feedStart),
    )
}
