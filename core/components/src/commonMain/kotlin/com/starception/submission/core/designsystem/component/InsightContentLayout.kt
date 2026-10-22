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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Reserve readable space for the stage after measuring the naturally wrapping footer. */
fun insightStageHeight(
    viewportHeight: Int,
    metadataHeight: Int,
    spacing: Int,
    minimumStageHeight: Int,
): Int = maxOf(minimumStageHeight, viewportHeight - metadataHeight - spacing)

/**
 * Expanded content must never compete with its footer for a zero-height weighted slot.
 * Keep the stage bounded (including its nested reader), and scroll the whole composition
 * when the resized viewport cannot fit both the stage and its naturally measured metadata.
 */
@Composable
fun InsightContentLayout(
    modifier: Modifier = Modifier,
    spacing: Dp = 14.dp,
    minimumStageHeight: Dp = 240.dp,
    stage: @Composable () -> Unit,
    metadata: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val minimumStagePx = with(density) {
        (minimumStageHeight * fontScale.coerceAtLeast(1f)).roundToPx()
    }
    val spacingPx = with(density) { spacing.roundToPx() }
    BoxWithConstraints(modifier) {
        val viewportHeight = constraints.maxHeight
        Layout(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            content = {
                Box(propagateMinConstraints = true) { stage() }
                Box { metadata() }
            },
        ) { measurables, constraints ->
            val metadataPlaceable = measurables[1].measure(
                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
            )
            val gap = if (metadataPlaceable.height > 0) spacingPx else 0
            val stageHeight = insightStageHeight(
                viewportHeight, metadataPlaceable.height, gap, minimumStagePx,
            )
            val stagePlaceable = measurables[0].measure(
                constraints.copy(minHeight = stageHeight, maxHeight = stageHeight),
            )
            layout(constraints.maxWidth, stageHeight + gap + metadataPlaceable.height) {
                stagePlaceable.placeRelative(0, 0)
                metadataPlaceable.placeRelative(0, stageHeight + gap)
            }
        }
    }
}
