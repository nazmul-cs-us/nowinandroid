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

import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Natural text height at the reader's actual width, bounded to avoid measuring whole books. */
data class InsightTextBlock(val text: String, val style: TextStyle, val spacing: Dp = 0.dp)

@Composable
fun insightTextHeight(blocks: List<InsightTextBlock>, width: Dp, limit: Dp = 720.dp): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(blocks, width, limit, density.density, density.fontScale) {
      with(density) {
        val widthPx = width.roundToPx().coerceAtLeast(1)
        val limitPx = limit.roundToPx()
        var height = 0
        for (block in blocks) {
            height += measurer.measure(
                text = block.text,
                style = block.style,
                maxLines = 64,
                constraints = Constraints(maxWidth = widthPx),
            ).size.height + block.spacing.roundToPx()
            if (height >= limitPx) break
        }
        height.coerceAtMost(limitPx).toDp()
      }
    }
}

/** Content determines the target; the viewport bounds long passages to a scrollable reader. */
fun contentSizedInsightExtent(
    page: Int,
    resting: Dp,
    maximum: Dp,
    naturalHeight: Dp?,
    isLoading: Boolean = false,
): Float {
    val growth = (maximum - resting).value
    if (growth <= 0f) return 0f
    val readingLimit = when (page) {
        0 -> 500.dp
        1 -> 620.dp
        2 -> 720.dp
        3 -> 560.dp
        else -> 660.dp
    }
    // A modest loading size avoids opening an almost full-screen tile before text arrives.
    // Retain a distinct expanded state without reserving reading space for a loader.
    val minimumGrowth = if (isLoading) 8.dp else 88.dp
    val initialHeight = if (isLoading) resting + minimumGrowth else when (page) {
        0 -> resting + 88.dp
        2 -> 480.dp
        3 -> 480.dp
        else -> 420.dp
    }
    val height = (naturalHeight ?: initialHeight).coerceAtMost(readingLimit)
        .coerceIn(minOf(resting + minimumGrowth, maximum), maximum)
    // Automatic sizing preserves Home navigation; immersive mode is an explicit resize.
    return ((height - resting).value / growth).coerceIn(0f, 0.98f)
}

/** Tile bounds use the host viewport after its visible navigation has been reserved. */
fun insightMaximumTileHeight(viewport: Dp, header: Dp, spacing: Dp = 16.dp): Dp =
    (viewport - header - spacing).coerceAtLeast(1.dp)
