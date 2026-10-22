/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Folded-corner grip. The host clips both grips to the focused card's shape. */
@Composable
fun InsightResizeCornerHandle(
    isStart: Boolean,
    expansionExtent: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isActive by remember { mutableStateOf(false) }
    val activeProgress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(140),
        label = "insightCornerGripPress",
    )
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val foldColor = if (dark) Color(0xFF354232) else Color(0xFFDDE6D5)
    val pressedColor = if (dark) Color(0xFF46583E) else Color(0xFFC9D8BD)
    val strokeColor = if (dark) Color(0xFFBDD0AE) else Color(0xFF61785B)
    Canvas(
        modifier = modifier
            .size(56.dp)
            .pointerInput(isStart) {
                detectDragGestures(
                    onDragStart = { isActive = true; onDragStart() },
                    onDragEnd = { isActive = false; onDragEnd() },
                    onDragCancel = { isActive = false; onDragEnd() },
                ) { change, delta ->
                    change.consume()
                    val outwardX = if (isStart) -delta.x else delta.x
                    onDrag((delta.y + outwardX) * 0.70710677f)
                }
            }
            .semantics {
                contentDescription = if (isStart) {
                    "Resize insight from bottom left"
                } else {
                    "Resize insight from bottom right"
                }
                stateDescription = "${(expansionExtent * 100).roundToInt()} percent expanded"
                customActions = listOf(
                    CustomAccessibilityAction("Expand insight") { onExpand(); true },
                    CustomAccessibilityAction("Collapse insight") { onCollapse(); true },
                )
            },
    ) {
        // Mirroring the same geometry keeps both folds and strokes symmetric.
        fun point(x: Float, y: Float) = Offset(
            if (isStart) x.dp.toPx() else size.width - x.dp.toPx(),
            size.height - y.dp.toPx(),
        )
        val fold = Path().apply {
            val corner = point(0f, 0f)
            moveTo(corner.x, corner.y)
            val top = point(0f, 56f)
            lineTo(top.x, top.y)
            val inner = point(56f, 0f)
            lineTo(inner.x, inner.y)
            close()
        }
        drawPath(fold, lerp(foldColor, pressedColor, activeProgress))
        drawLine(strokeColor, point(15f, 17f), point(21f, 11f), 1.8.dp.toPx(), StrokeCap.Round)
        drawLine(strokeColor, point(19f, 22f), point(27f, 14f), 1.8.dp.toPx(), StrokeCap.Round)
    }
}
