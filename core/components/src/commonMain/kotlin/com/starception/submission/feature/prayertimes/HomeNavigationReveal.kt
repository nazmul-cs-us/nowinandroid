package com.starception.submission.feature.prayertimes

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val HomeNavigationRevealHeight = 44.dp

/** Give scrolling content a soft edge immediately above the floating navigation. */
fun Modifier.homeNavigationReveal(background: Color): Modifier = drawWithCache {
    val height = HomeNavigationRevealHeight.toPx().coerceAtMost(size.height)
    val top = size.height - height
    val reveal = Brush.verticalGradient(
        0f to background.copy(alpha = 0f),
        .5f to background.copy(alpha = .64f),
        .82f to background,
        1f to background,
        startY = top,
        endY = size.height,
    )
    onDrawWithContent {
        drawContent()
        if (height > 0f) drawRect(reveal, topLeft = Offset(0f, top), size = Size(size.width, height))
    }
}
