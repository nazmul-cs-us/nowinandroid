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

class PrayerSceneGeometryTest {
    @Test
    fun uncutArtworkKeepsTracedMountainCoordinates() {
        val scene = PrayerSceneGeometry(1086f, 1448f, false)
        assertEquals(.448f, scene.ridgeY(.55f), .0001f)
    }

    @Test
    fun wideTileAppliesVerticalCropAndIosAlignmentToRidge() {
        val centered = PrayerSceneGeometry(360f, 200f, false)
        val ios = PrayerSceneGeometry(360f, 200f, false, .4f)
        assertEquals((.448f * 480f - 140f) / 200f, centered.ridgeY(.55f), .0001f)
        assertEquals((.448f * 480f - 196f) / 200f, ios.ridgeY(.55f), .0001f)
    }

    @Test
    fun tallTileCropsHorizontalRidgeCoordinates() {
        val scene = PrayerSceneGeometry(300f, 600f, false)
        assertEquals((.55f * 450f - 75f) / 300f, scene.ridge[10].x, .0001f)
        assertEquals(.448f, scene.ridge[10].y, .0001f)
    }

    @Test
    fun highBodiesStayWithinOneRadiusOfClearSkyAcrossTileSizes() {
        for (height in listOf(180f, 320f, 600f)) {
            for (night in listOf(false, true)) {
                val scene = PrayerSceneGeometry(360f, height, night)
                val ridge = scene.ridgeY(.37f)
                val center = scene.bodyCenterY(.37f, 90.0, 40f)
                val gapBelowDisc = (ridge - center) * height - 20f
                assertEquals(20f, gapBelowDisc, .001f)
            }
        }
    }

    @Test
    fun settingBodiesDisappearBehindTheirLocalRidge() {
        val scene = PrayerSceneGeometry(360f, 220f, true)
        val center = scene.bodyCenterY(.65f, 0.0, 40f)
        assertTrue(abs((center - scene.ridgeY(.65f)) * 220f - 20f) < .001f)
        assertEquals(center, scene.bodyCenterY(.65f, -1.5, 40f))
    }
}
