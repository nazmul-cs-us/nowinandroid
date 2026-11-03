package com.starception.submission.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow

@Immutable
internal data class NavigationGlassColors(
    val surface: Color,
    val outline: Color,
    val content: Color,
    val active: Color,
    val selection: Color,
)

internal fun navigationGlassColors(darkFraction: Float) = NavigationGlassColors(
    surface = lerp(Color(0xFFF8F8FA).copy(alpha = .55f), Color(0xFF161820).copy(alpha = .32f), darkFraction),
    outline = lerp(Color.Black.copy(alpha = .12f), Color.White.copy(alpha = .12f), darkFraction),
    content = lerp(Color(0xFF242428), Color(0xFFE6E6EB), darkFraction),
    active = lerp(Color(0xFF006BD6), Color(0xFF5CB4FF), darkFraction),
    selection = lerp(Color.Black.copy(alpha = .075f), Color.White.copy(alpha = .12f), darkFraction),
)

/** One interruptible transition keeps both controls and all foregrounds in sync. */
@Composable
internal fun animatedNavigationGlassColors(darkAppearance: Boolean): NavigationGlassColors {
    val darkFraction by animateFloatAsState(
        targetValue = if (darkAppearance) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "navigationGlassAppearance",
    )
    return navigationGlassColors(darkFraction)
}

/** Live frosted color with a crisp outline and no bright, fading edge. */
internal fun Modifier.navigationGlass(backdrop: Backdrop, shape: Shape, colors: NavigationGlassColors): Modifier =
    drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(10.dp.toPx())
        },
        highlight = { null },
        // Backdrop masks this shadow out of the interior before drawing the glass.
        // An elevated transparent Surface drawn AFTER the glass instead shades it.
        shadow = { Shadow(radius = 6.dp, color = Color.Black.copy(alpha = .10f)) },
        innerShadow = { null },
        onDrawSurface = {
            drawRect(colors.surface)
        },
    ).border(
        width = 1.dp,
        color = colors.outline,
        shape = shape,
    )
