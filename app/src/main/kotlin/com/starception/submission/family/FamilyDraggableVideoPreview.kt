package com.starception.submission.family

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal fun videoPreviewOffset(position: Offset, bounds: Size, preview: Size): Offset = Offset(
    position.x.coerceIn(0f, 1f) * (bounds.width - preview.width).coerceAtLeast(0f),
    position.y.coerceIn(0f, 1f) * (bounds.height - preview.height).coerceAtLeast(0f),
)

/** Layout offsets move the existing video surface; dragging never recreates the camera renderer. */
@Composable
internal fun FamilyDraggableVideoPreview(
    callId: String,
    preferredWidth: Dp,
    preferredHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var horizontal by rememberSaveable(callId) { mutableFloatStateOf(1f) }
    var vertical by rememberSaveable(callId) { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier.fillMaxSize().testTag("familyVideoPreviewBounds")) {
        if (maxWidth <= 0.dp || maxHeight <= 0.dp) return@BoxWithConstraints
        val width = minOf(preferredWidth, maxWidth)
        val height = minOf(preferredHeight, maxHeight)
        val density = LocalDensity.current
        val bounds = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val preview = with(density) { Size(width.toPx(), height.toPx()) }
        val travel = Size((bounds.width - preview.width).coerceAtLeast(0f), (bounds.height - preview.height).coerceAtLeast(0f))
        Box(Modifier
            .offset {
                val offset = videoPreviewOffset(Offset(horizontal, vertical), bounds, preview)
                IntOffset(offset.x.roundToInt(), offset.y.roundToInt())
            }
            .size(width, height)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = .5f), RoundedCornerShape(16.dp))
            .testTag("familyVideoSelfPreview")
            .semantics {
                contentDescription = "Your video preview"
                customActions = listOf(
                    CustomAccessibilityAction("Move preview left") { horizontal = 0f; true },
                    CustomAccessibilityAction("Move preview right") { horizontal = 1f; true },
                    CustomAccessibilityAction("Move preview up") { vertical = (vertical - .25f).coerceAtLeast(0f); true },
                    CustomAccessibilityAction("Move preview down") { vertical = (vertical + .25f).coerceAtMost(1f); true },
                )
            }
            .pointerInput(callId, travel) {
                detectDragGestures(
                    onDragEnd = { horizontal = if (horizontal < .5f) 0f else 1f },
                    onDrag = { change, amount ->
                        change.consume()
                        if (travel.width > 0f) horizontal = (horizontal + amount.x / travel.width).coerceIn(0f, 1f)
                        if (travel.height > 0f) vertical = (vertical + amount.y / travel.height).coerceIn(0f, 1f)
                    },
                )
            },
        ) { content() }
    }
}
