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

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Transparent corner grip; only the two diagonal strokes are painted. */
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
    isResizing: Boolean = false,
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
        label = "insightCornerGripPress",
    )
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val strokeColor = if (dark) Color(0xFFBDD0AE) else Color(0xFF61785B)
    // Two gentle outward/downward nudges, then a long rest. The hint pauses
    // during either corner's drag and when there is no more room to expand.
    val hintProgress = if (!isActive && !isResizing && expansionExtent > 0f && expansionExtent < 0.98f) {
        val transition = rememberInfiniteTransition(label = "insightResizeHint")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 8_000
                    0f at 0
                    0f at 5_000 using FastOutSlowInEasing
                    1f at 5_600 using FastOutSlowInEasing
                    0f at 6_200 using FastOutSlowInEasing
                    1f at 6_800 using FastOutSlowInEasing
                    0f at 7_400
                    0f at 8_000
                },
            ),
            label = "insightResizeHintOffset",
        )
        progress
    } else {
        0f
    }
    Canvas(
        modifier = modifier
            .size(56.dp)
            .pointerInput(isStart) {
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
                    val outwardX = if (isStart) -delta.x else delta.x
                    currentOnDrag((delta.y + outwardX) * 0.70710677f)
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
        // A compact, graduated corner grip: the shorter outer stroke nests
        // against the longer inner one, with aligned ends and a fine gap.
        val nudge = hintProgress * 1.25f
        fun point(x: Float, y: Float) = Offset(
            if (isStart) (x - nudge).dp.toPx() else size.width - (x - nudge).dp.toPx(),
            size.height - (y - nudge).dp.toPx(),
        )
        val stroke = (1.35f + activeProgress * 0.15f).dp.toPx()
        val ink = strokeColor.copy(alpha = 0.8f + activeProgress * 0.2f)
        drawLine(ink, point(16f, 17f), point(21f, 12f), stroke, StrokeCap.Round)
        drawLine(ink, point(16f, 21f), point(25f, 12f), stroke, StrokeCap.Round)
    }
}

/** Give expanded content a broad reading surface before it becomes tall. */
fun insightWidthExpansionProgress(expansion: Float): Float {
    val remaining = 1f - expansion.coerceIn(0f, 1f)
    return 1f - remaining * remaining * remaining
}
