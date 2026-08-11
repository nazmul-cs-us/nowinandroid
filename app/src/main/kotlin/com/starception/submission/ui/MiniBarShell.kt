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

/**
 * The single row height every top strip row uses — mini bars and plain status
 * lines alike. PullToSyncContainer derives its standard bar height from this so
 * the strip never changes size as its content changes.
 */
val MiniBarRowHeight = 30.dp

/** Shared, transparent geometry for persistent reading and playback mini bars. */
@Composable
fun MiniBarShell(
    progress: Float,
    modifier: Modifier = Modifier,
    /**
     * Draw the thin track under the row. Callers turn it off where the strip's
     * own full-width sweep already shows the same position, so a Mushaf page
     * isn't sitting under two readings of one number.
     */
    showProgressLine: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f)
    val progressColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniBarRowHeight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                // Clear the track only when it's actually drawn — without it the
                // row gets those 2dp back for text.
                .padding(start = 12.dp, end = 12.dp, bottom = if (showProgressLine) 2.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        if (showProgressLine) {
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
}
