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

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

data class PrayerScenePoint(val x: Float, val y: Float)

/** Projects the 1086 x 1448 artwork through the same centered Crop as the image. */
class PrayerSceneGeometry(
    width: Float,
    height: Float,
    isNight: Boolean,
    verticalBias: Float = 0f,
) {
    private val viewportWidth = width.coerceAtLeast(1f)
    private val viewportHeight = height.coerceAtLeast(1f)
    private val drawnWidth = max(viewportWidth, viewportHeight * .75f)
    private val drawnHeight = drawnWidth / .75f
    private val left = (viewportWidth - drawnWidth) / 2f
    private val top = (viewportHeight - drawnHeight) * (1f + verticalBias.coerceIn(-1f, 1f)) / 2f

    // Traced in source-image coordinates. Both the clip and the body anchor use this ridge.
    val ridge: List<PrayerScenePoint> = (if (isNight) NIGHT_RIDGE else DAY_RIDGE).map { point ->
        PrayerScenePoint(
            (left + point.x * drawnWidth) / viewportWidth,
            (top + point.y * drawnHeight) / viewportHeight,
        )
    }

    fun ridgeY(x: Float): Float {
        val segment = ridge.zipWithNext().firstOrNull { (a, b) -> x in a.x..b.x }
            ?: return if (x < ridge.first().x) ridge.first().y else ridge.last().y
        val (a, b) = segment
        return a.y + (b.y - a.y) * ((x - a.x) / (b.x - a.x))
    }

    /** Scenic placement: horizon occlusion to at most half a disc of clear sky above the ridge. */
    fun bodyCenterY(x: Float, altitudeDegrees: Double, diameter: Float): Float {
        val radius = diameter.coerceAtLeast(0f) / 2f
        val lift = sin(altitudeDegrees.coerceIn(0.0, 90.0) * PI / 180.0).toFloat()
        return ridgeY(x) + radius * (1f - 3f * lift) / viewportHeight
    }
}

private val DAY_RIDGE = listOf(
    PrayerScenePoint(0f, .494f), PrayerScenePoint(.08f, .490f),
    PrayerScenePoint(.16f, .484f), PrayerScenePoint(.20f, .482f),
    PrayerScenePoint(.25f, .496f), PrayerScenePoint(.30f, .504f),
    PrayerScenePoint(.36f, .493f), PrayerScenePoint(.42f, .484f),
    PrayerScenePoint(.48f, .475f), PrayerScenePoint(.52f, .467f),
    PrayerScenePoint(.55f, .448f), PrayerScenePoint(.59f, .462f),
    PrayerScenePoint(.64f, .479f), PrayerScenePoint(.68f, .481f),
    PrayerScenePoint(.72f, .484f), PrayerScenePoint(.78f, .483f),
    PrayerScenePoint(.81f, .473f), PrayerScenePoint(.86f, .480f),
    PrayerScenePoint(.92f, .483f), PrayerScenePoint(1f, .480f),
)

private val NIGHT_RIDGE = listOf(
    PrayerScenePoint(0f, .442f), PrayerScenePoint(.08f, .447f),
    PrayerScenePoint(.16f, .446f), PrayerScenePoint(.20f, .438f),
    PrayerScenePoint(.24f, .435f), PrayerScenePoint(.29f, .448f),
    PrayerScenePoint(.34f, .463f), PrayerScenePoint(.40f, .452f),
    PrayerScenePoint(.46f, .442f), PrayerScenePoint(.52f, .425f),
    PrayerScenePoint(.55f, .421f), PrayerScenePoint(.58f, .416f),
    PrayerScenePoint(.63f, .428f), PrayerScenePoint(.68f, .440f),
    PrayerScenePoint(.73f, .447f), PrayerScenePoint(.78f, .435f),
    PrayerScenePoint(.81f, .430f), PrayerScenePoint(.86f, .443f),
    PrayerScenePoint(.90f, .436f), PrayerScenePoint(.94f, .425f),
    PrayerScenePoint(1f, .414f),
)
