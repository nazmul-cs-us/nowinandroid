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

package com.starception.submission.core.model.util

import kotlin.math.abs
import kotlin.math.exp

/** Frame-rate independent, non-overshooting motion along the shortest compass arc. */
fun smoothCompassHeading(current: Float, target: Float, elapsedSeconds: Float): Float {
    if (!target.isFinite() || !elapsedSeconds.isFinite() || elapsedSeconds <= 0f) return current
    if (!current.isFinite()) return ((target % 360f) + 360f) % 360f
    val delta = (((target - current) % 360f + 540f) % 360f) - 180f
    if (abs(delta) <= 0.35f) return current
    val blend = 1f - exp(-elapsedSeconds.coerceAtMost(0.064f) / 0.14f)
    return ((current + delta * blend) % 360f + 360f) % 360f
}
