package com.starception.submission.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/** Shared, transparent geometry for persistent reading and playback mini bars. */
@Composable
fun MiniBarShell(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f)
    val progressColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(1.5.dp),
        ) {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(trackColor, size = size, cornerRadius = radius)
            drawRoundRect(
                progressColor,
                size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
                cornerRadius = radius,
            )
        }
    }
}
