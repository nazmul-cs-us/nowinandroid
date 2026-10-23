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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompassMotionTest {
    @Test
    fun northCrossingTakesShortArcInBothDirections() {
        val clockwise = smoothCompassHeading(359f, 1f, 1f / 60f)
        val counterclockwise = smoothCompassHeading(1f, 359f, 1f / 60f)
        assertTrue(clockwise > 359f)
        assertTrue(counterclockwise < 1f)
    }

    @Test
    fun sensorRetargetingContinuesFromDisplayedPositionWithoutSnapping() {
        val current = smoothCompassHeading(20f, 90f, 1f / 60f)
        val next = smoothCompassHeading(current, 110f, 1f / 60f)
        assertTrue(next > current && next < 110f)
        assertTrue(abs(next - current) < 15f)
    }

    @Test
    fun equivalentElapsedTimeAt60And120HzProducesTheSameMotion() {
        var slow = 0f
        var fast = 0f
        repeat(30) { slow = smoothCompassHeading(slow, 90f, 1f / 60f) }
        repeat(60) { fast = smoothCompassHeading(fast, 90f, 1f / 120f) }
        assertEquals(slow, fast, 0.002f)
        assertTrue(slow in 87f..90f)
    }

    @Test
    fun stationaryNoiseAndInvalidReadingsDoNotMoveTheCompass() {
        assertEquals(90f, smoothCompassHeading(90f, 90.2f, 1f / 60f))
        assertEquals(359.9f, smoothCompassHeading(359.9f, 0.1f, 1f / 60f))
        assertEquals(90f, smoothCompassHeading(90f, Float.NaN, 1f / 60f))
        assertEquals(90f, smoothCompassHeading(90f, 180f, Float.NaN))
    }

    @Test
    fun settlingDoesNotOvershootOrBounce() {
        var heading = 20f
        repeat(120) {
            val next = smoothCompassHeading(heading, 80f, 1f / 60f)
            assertTrue(next >= heading && next <= 80f)
            heading = next
        }
        assertTrue(abs(heading - 80f) <= 0.35f)
    }
}
