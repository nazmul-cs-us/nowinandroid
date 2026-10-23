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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import com.starception.submission.core.model.util.smoothCompassHeading
import kotlinx.coroutines.flow.first

/** Sensor updates change the target without restarting or snapping the animation. */
@Composable
fun rememberSmoothCompassHeading(heading: Float?): State<Float?> {
    val target by rememberUpdatedState(heading?.takeIf { it.isFinite() })
    val displayed = remember { mutableStateOf(target) }
    LaunchedEffect(target != null) {
        displayed.value = target
        if (target == null) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (true) {
            val current = displayed.value ?: target ?: return@LaunchedEffect
            if (smoothCompassHeading(current, target ?: current, 1f / 60f) == current) {
                // Stop requesting display frames once settled; a meaningful
                // sensor change wakes the same animation from its current angle.
                snapshotFlow { target }.first { next ->
                    next != null && smoothCompassHeading(current, next, 1f / 60f) != current
                }
                previousFrame = withFrameNanos { it }
            }
            withFrameNanos { frame ->
                val next = target ?: return@withFrameNanos
                val elapsed = (frame - previousFrame) / 1_000_000_000f
                previousFrame = frame
                displayed.value = smoothCompassHeading(displayed.value ?: next, next, elapsed)
            }
        }
    }
    return displayed
}
