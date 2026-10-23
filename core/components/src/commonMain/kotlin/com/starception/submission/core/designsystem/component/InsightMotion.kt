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

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Home insight motion shared by the Android and iOS presentations. */
object InsightMotion {
    const val ExpansionMillis = 420
    const val EntranceMillis = 360
    const val EntranceDelayMillis = 50L
    val EntranceOffset = 12.dp
    val ReadingEdge = 20.dp
}

/** Fade only overflowing edges, leaving the first and last lines fully readable at rest. */
fun Modifier.insightReadingEdges(
    canScrollBackward: () -> Boolean,
    canScrollForward: () -> Boolean,
): Modifier = graphicsLayer {
    // Isolate the mask to this fixed viewport; never mask the wallpaper or controls.
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    val edge = minOf(InsightMotion.ReadingEdge.toPx(), size.height / 4f)
    if (edge > 0f && canScrollBackward()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = edge,
            ),
            size = Size(size.width, edge),
            blendMode = BlendMode.DstIn,
        )
    }
    if (edge > 0f && canScrollForward()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - edge,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - edge),
            size = Size(size.width, edge),
            blendMode = BlendMode.DstIn,
        )
    }
}

