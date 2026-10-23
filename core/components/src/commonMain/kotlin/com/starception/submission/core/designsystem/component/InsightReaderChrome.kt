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
import androidx.compose.animation.core.tween
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

val LocalInsightReaderChromeProgress = staticCompositionLocalOf { 0f }

/** Observes reader scrolling without consuming gestures or changing the chosen tile size. */
@Stable
class InsightReaderChromeState(private val threshold: Float) {
    var hidden by mutableStateOf(false)
        private set
    private var distance = 0f
    private var edgeDistance = 0f
    fun showControls() {
        hidden = false
        distance = 0f
        edgeDistance = 0f
    }
    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput) {
                distance += abs(consumed.y)
                if (distance >= threshold) hidden = true
                // Pulling past the start restores the controls naturally as well.
                edgeDistance = if (consumed.y == 0f && available.y > 0f) edgeDistance + available.y else 0f
                if (edgeDistance >= threshold) showControls()
            }
            return Offset.Zero
        }
    }
}

@Composable
fun rememberInsightReaderChrome(enabled: Boolean): InsightReaderChromeState {
    val density = LocalDensity.current
    val threshold = with(density) { 32.dp.toPx() }
    val state = remember(threshold) { InsightReaderChromeState(threshold) }
    LaunchedEffect(enabled) { state.showControls() }
    return state
}

@Composable
fun insightReaderChromeProgress(state: InsightReaderChromeState, enabled: Boolean): Float {
    val progress by animateFloatAsState(
        targetValue = if (enabled && state.hidden) 1f else 0f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "insightReaderChrome",
    )
    return progress
}

/** Measure the full chrome for automatic sizing, but reclaim its visible space while reading. */
fun Modifier.insightChromeVisibility(progress: Float): Modifier = then(
    if (progress >= 0.999f) Modifier.clearAndSetSemantics {} else Modifier,
).clipToBounds().layout { measurable, constraints ->
    val child = measurable.measure(constraints)
    layout(child.width, (child.height * (1f - progress.coerceIn(0f, 1f))).roundToInt()) {
        child.placeRelative(0, 0)
    }
}.graphicsLayer { alpha = 1f - progress.coerceIn(0f, 1f) }

@Composable
fun InsightReaderControlsButton(state: InsightReaderChromeState, modifier: Modifier = Modifier) {
    if (state.hidden) {
        TextButton(
            onClick = state::showControls,
            modifier = modifier,
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        ) {
            Text("Show controls")
        }
    }
}
