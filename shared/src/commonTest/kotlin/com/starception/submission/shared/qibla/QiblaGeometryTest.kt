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

package com.starception.submission.shared.qibla

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QiblaGeometryTest {
    @Test
    fun knownCitiesProduceExpectedInitialBearings() {
        assertTrue(abs(qiblaBearing(43.6532, -79.3832) - 54.6) < 1.0)
        assertTrue(abs(qiblaBearing(51.5074, -0.1278) - 118.9) < 1.0)
    }

    @Test
    fun distanceToKaabaIsStableAtDestinationAndAcrossTheDateLine() {
        assertEquals(0.0, qiblaDistanceKm(21.4225, 39.8262), 0.001)
        assertTrue(qiblaDistanceKm(51.5074, -0.1278) in 4700.0..4900.0)
        assertEquals(qiblaDistanceKm(0.0, 179.9), qiblaDistanceKm(0.0, -180.1), 0.001)
    }

    @Test
    fun relativeTurnUsesShortestSignedDirection() {
        assertEquals(20.0, relativeQiblaTurn(10.0, 350.0), 0.001)
        assertEquals(-20.0, relativeQiblaTurn(350.0, 10.0), 0.001)
        assertEquals(0.0, relativeQiblaTurn(90.0, 90.0), 0.001)
    }
}
