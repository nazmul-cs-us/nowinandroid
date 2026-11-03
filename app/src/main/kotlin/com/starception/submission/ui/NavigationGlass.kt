package com.starception.submission.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

/** Live page pixels, blurred beneath a light tint, rather than a fixed opaque fill. */
internal fun Modifier.navigationGlass(backdrop: Backdrop, shape: Shape, darkAppearance: Boolean): Modifier =
    drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = { blur(20.dp.toPx()) },
        highlight = { null },
        shadow = { null },
        innerShadow = { null },
        onDrawSurface = {
            // Keep light-mode pages dark enough for the navigation's white
            // labels; dark artwork needs much less tint to preserve its colors.
            drawRect(Color(0xFF141925).copy(alpha = if (darkAppearance) .30f else .65f))
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = .055f),
                    1f to Color.White.copy(alpha = .02f),
                ),
            )
        },
    )
