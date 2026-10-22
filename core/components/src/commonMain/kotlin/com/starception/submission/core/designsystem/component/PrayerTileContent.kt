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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainHeight

/** Measure names and time at natural height before distributing the remaining space. */
@Composable
fun PrayerTileContent(
    modifier: Modifier = Modifier,
    verticalPadding: Dp,
    onMinimumHeightChange: (Dp) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val gap = with(density) { 6.dp.roundToPx() }
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val naturalConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val names = measurables[0].measure(naturalConstraints)
        val time = measurables[1].measure(naturalConstraints)
        val minimumHeight = names.height + gap + time.height
        onMinimumHeightChange(with(density) { minimumHeight.toDp() } + verticalPadding)
        val height = constraints.constrainHeight(minimumHeight)
        layout(constraints.maxWidth, height) {
            names.placeRelative(0, 0)
            time.placeRelative(0, maxOf(names.height + gap, height - time.height))
        }
    }
}
