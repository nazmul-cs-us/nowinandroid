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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Quiet bottom-edge grab bar with a larger, invisible drag target. */
@Composable
fun InsightResizeHandle(
    expansionExtent: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isActive by remember { mutableStateOf(false) }
    // Keep the pointer coroutine alive across layout changes, while using the
    // current resize range and callbacks for every event of the gesture.
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val activeProgress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(140),
        label = "insightGripPress",
    )
    Canvas(
        modifier = modifier
            .size(width = 80.dp, height = 48.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isActive = true
                        currentOnDragStart()
                    },
                    onDragEnd = {
                        isActive = false
                        currentOnDragEnd()
                    },
                    onDragCancel = {
                        isActive = false
                        currentOnDragEnd()
                    },
                ) { change, delta ->
                    change.consume()
                    currentOnDrag(delta.y)
                }
            }
            .semantics {
                contentDescription = "Resize insight height"
                stateDescription = "${(expansionExtent * 100).roundToInt()} percent expanded"
                customActions = listOf(
                    CustomAccessibilityAction("Expand insight") { onExpand(); true },
                    CustomAccessibilityAction("Collapse insight") { onCollapse(); true },
                )
            },
    ) {
        val halfWidth = (16f + 4f * activeProgress).dp.toPx()
        val y = size.height - 12.dp.toPx()
        val start = Offset(size.width / 2f - halfWidth, y)
        val end = Offset(size.width / 2f + halfWidth, y)
        drawLine(Color.Black.copy(alpha = .24f), start, end, 6.dp.toPx(), StrokeCap.Round)
        drawLine(
            color = lerp(Color.White.copy(alpha = .65f), Color.White, activeProgress),
            start = start,
            end = end,
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Give expanded content a broad reading surface before it becomes tall. */
fun insightWidthExpansionProgress(expansion: Float): Float {
    val remaining = 1f - expansion.coerceIn(0f, 1f)
    return 1f - remaining * remaining * remaining
}
