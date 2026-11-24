package com.starception.submission.feature.prayertimes

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Give scrolling content a soft edge immediately above the floating navigation. */
fun Modifier.homeNavigationReveal(background: Color, clearance: Dp): Modifier = drawWithCache {
    val height = (clearance + 12.dp).toPx().coerceAtMost(size.height)
    val top = size.height - height
    val reveal = Brush.verticalGradient(
        0f to background.copy(alpha = 0f),
        .48f to background.copy(alpha = .28f),
        .80f to background.copy(alpha = .78f),
        1f to background,
        startY = top,
        endY = size.height,
    )
    onDrawWithContent {
        drawContent()
        if (height > 0f) drawRect(reveal, topLeft = Offset(0f, top), size = Size(size.width, height))
    }
}
