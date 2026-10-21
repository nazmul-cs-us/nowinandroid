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

package com.starception.submission.feature.prayertimes.celestial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.starception.submission.prayer.sky.AltAz
import com.starception.submission.prayer.sky.julianDay
import com.starception.submission.prayer.sky.skySnapshotAt
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Astronomically positions the sun and moon over the Prayer Now scene.
 *
 * The moon mask is the projection of a lit sphere, not a decorative crescent:
 * its area, waxing direction and limb rotation all come from the sky snapshot.
 * The skyline clip keeps both bodies behind the mountains as they cross the horizon.
 */
@Composable
fun PrayerSkyCelestialLayer(
    epochSeconds: Double,
    latitude: Double,
    longitude: Double,
    isNight: Boolean,
    modifier: Modifier = Modifier,
    replayKey: Any? = Unit,
    forceSunForTesting: Boolean = false,
    forceMoonForTesting: Boolean = false,
) {
    // Keep the body at its real current astronomical position. Only the rays
    // have a subtle three-second shimmer; there is no sunrise-to-sunset or
    // moonrise-to-moonset replay when entering the tile.
    val snapshot = remember(epochSeconds, latitude, longitude) {
        skySnapshotAt(
            julianDay = julianDay(epochSeconds),
            latitudeDeg = latitude,
            longitudeEastDeg = longitude,
        )
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize(),
    ) {
        val diameter = (min(maxWidth.value, maxHeight.value) * 0.17f)
            .coerceIn(34f, 60f)
            .dp
        val lightFieldDiameter = diameter * 6f
        val sunPosition = scenicPosition(snapshot.sunAltAz)
        val moonPosition = scenicPosition(snapshot.moonAltAz).let { position ->
            // Keep the visible moon at the mountain horizon. The body is then
            // occluded by PrayerNowSkylineShape while its soft glow can still
            // read above the ridge.
            position.copy(y = position.y.coerceAtLeast(0.42f))
        }
        val fieldWidth = maxWidth
        val fieldHeight = maxHeight

        if ((isNight || forceMoonForTesting) &&
            (forceSunForTesting || forceMoonForTesting ||
                (snapshot.moonAltAz.altitudeDeg > -1.5 &&
                    snapshot.moonPhase.illuminatedFraction > 0.01))
        ) {
            // Light beams are additive: they wash across the whole scene like
            // the reference's crepuscular shafts, while only the body itself
            // is occluded by the traced skyline below.
            if (forceSunForTesting) {
                SunAtmosphericLight(
                    position = moonPosition,
                    bodyDiameter = diameter,
                    color = Color(0xFFFFD76A),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(PrayerNowSkylineShape(isNight)),
            ) {
                if (forceSunForTesting) {
                    SunBody(
                        modifier = Modifier
                            .offset(
                                x = fieldWidth * moonPosition.x - lightFieldDiameter / 2,
                                y = fieldHeight * moonPosition.y - lightFieldDiameter / 2,
                            )
                            .size(lightFieldDiameter),
                        bodyDiameter = diameter,
                    )
                } else {
                    MoonBody(
                        illuminatedFraction = snapshot.moonPhase.illuminatedFraction.toFloat(),
                        waxing = snapshot.moonPhase.waxing,
                        brightLimbRotation = snapshot.moonBrightLimbScreenDeg.toFloat(),
                        modifier = Modifier
                            .offset(
                                x = fieldWidth * moonPosition.x - lightFieldDiameter / 2,
                                y = fieldHeight * moonPosition.y - lightFieldDiameter / 2,
                            )
                            .size(lightFieldDiameter),
                        bodyDiameter = diameter,
                    )
                }
            }
        }
        if (!isNight && !forceMoonForTesting &&
            (forceSunForTesting || snapshot.sunAltAz.altitudeDeg > -1.5)
        ) {
            val displayedSunPosition = if (forceSunForTesting) Offset(0.72f, 0.20f) else sunPosition
            SunAtmosphericLight(
                position = displayedSunPosition,
                bodyDiameter = diameter,
                color = Color(0xFFFFD76A),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(PrayerNowSkylineShape(isNight)),
            ) {
                SunBody(
                    modifier = Modifier
                        .offset(
                                x = fieldWidth * displayedSunPosition.x - lightFieldDiameter / 2,
                                y = fieldHeight * displayedSunPosition.y - lightFieldDiameter / 2,
                        )
                        .size(lightFieldDiameter),
                    bodyDiameter = diameter,
                )
            }
        }
    }
}

private data class MosqueWindowLight(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val phase: Float,
    val speed: Float,
    val depth: Float,
)

private val MOSQUE_WINDOW_LIGHTS = listOf(
    MosqueWindowLight(0.18f, 0.58f, 0.018f, 0.035f, 0.07f, 0.83f, 0.72f),
    MosqueWindowLight(0.24f, 0.63f, 0.014f, 0.029f, 0.41f, 1.17f, 0.56f),
    MosqueWindowLight(0.31f, 0.55f, 0.020f, 0.040f, 0.76f, 0.69f, 0.90f),
    MosqueWindowLight(0.38f, 0.66f, 0.013f, 0.027f, 0.22f, 1.31f, 0.48f),
    MosqueWindowLight(0.46f, 0.59f, 0.017f, 0.034f, 0.58f, 0.91f, 0.68f),
    MosqueWindowLight(0.54f, 0.64f, 0.014f, 0.030f, 0.13f, 1.09f, 0.52f),
    MosqueWindowLight(0.61f, 0.56f, 0.019f, 0.038f, 0.87f, 0.77f, 0.84f),
    MosqueWindowLight(0.69f, 0.63f, 0.013f, 0.028f, 0.33f, 1.23f, 0.50f),
    MosqueWindowLight(0.76f, 0.58f, 0.018f, 0.034f, 0.64f, 0.88f, 0.70f),
    MosqueWindowLight(0.83f, 0.67f, 0.012f, 0.026f, 0.18f, 1.37f, 0.44f),
)

/** Small, deterministic window lights give the mosque a quiet 3D lived-in glow. */
@Composable
private fun MosqueWindowLights(
    isNight: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "mosqueWindowLights")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "mosqueWindowLightPhase",
    )

    Canvas(Modifier.fillMaxSize()) {
        MOSQUE_WINDOW_LIGHTS.forEach { light ->
            val cycle = (phase * light.speed + light.phase) % 1f
            val fade = when {
                cycle < 0.12f -> cycle / 0.12f
                cycle < 0.68f -> 1f
                cycle < 0.84f -> 1f - ((cycle - 0.68f) / 0.16f)
                else -> 0f
            }
            val nightBoost = if (isNight) 1f else 0.42f
            val alpha = (fade * nightBoost * (0.34f + (0.26f * light.depth)))
                .coerceIn(0f, 0.72f)
            if (alpha > 0f) {
                val center = Offset(size.width * light.x, size.height * light.y)
                val glowRadius = size.minDimension * (0.025f + (0.018f * light.depth))
                drawCircle(
                    color = Color(0xFFFFB85C).copy(alpha = alpha * 0.12f),
                    radius = glowRadius,
                    center = center,
                )
                drawRoundRect(
                    color = Color(0xFFFFD27A).copy(alpha = alpha),
                    topLeft = Offset(
                        center.x - (size.width * light.width / 2f),
                        center.y - (size.height * light.height / 2f),
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        size.width * light.width,
                        size.height * light.height,
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        size.width * light.width * 0.22f,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SunBody(
    bodyDiameter: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        PlatformCelestialBody(
            body = CelestialBody.Sun,
            modifier = Modifier
                .size(bodyDiameter)
                .semantics {
                    contentDescription = "Animated procedural sun"
                },
        )
    }
}

@Composable
private fun CelestialGlow(
    position: Offset,
    bodyDiameter: Dp,
    color: Color,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width * position.x, size.height * position.y)
        val radius = bodyDiameter.toPx() * 0.5f
        // A tight corona keeps the light physically connected to the NASA disc;
        // the larger wash is intentionally much softer so it cannot look like a
        // separate ring floating around the sun.
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    0.62f to Color.Transparent,
                    0.78f to color.copy(alpha = 0.24f),
                    0.92f to color.copy(alpha = 0.12f),
                    1.0f to Color.Transparent,
                ),
                center = center,
                radius = radius * 1.42f,
            ),
            center = center,
            radius = radius * 1.42f,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.12f),
                    color.copy(alpha = 0.045f),
                    Color.Transparent,
                ),
                center = center,
                radius = radius * 3.4f,
            ),
            center = center,
            radius = radius * 3.4f,
        )
    }
}

@Composable
private fun SunAtmosphericLight(
    position: Offset,
    bodyDiameter: Dp,
    color: Color,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width * position.x, size.height * position.y)
        val radius = bodyDiameter.toPx() * 0.5f
        val target = Offset(size.width * 0.54f, size.height * 0.82f)
        val towardHorizon = atan2(target.y - center.y, target.x - center.x)

        // The atmosphere reveals a handful of broad shafts through cloud gaps.
        // They share a direction instead of forming a radial starburst.
        val shaftCount = 4
        val shaftSpread = 0.18f
        for (index in 0 until shaftCount) {
            val positionAcross = if (shaftCount == 1) 0.5f else index.toFloat() / (shaftCount - 1)
            val angle = towardHorizon + (positionAcross - 0.5f) * shaftSpread
            val direction = Offset(cos(angle), sin(angle))
            val perpendicular = Offset(-direction.y, direction.x)
            val start = center + direction * (radius * 0.92f)
            val reach = size.maxDimension * (0.72f + index * 0.045f)
            val end = start + direction * reach
            val innerWidth = size.width * 0.006f
            val outerWidth = size.width * (0.055f + index * 0.007f)
            val alpha = 0.028f + (1f - kotlin.math.abs(positionAcross - 0.5f) * 2f) * 0.014f

            fun drawShaft(innerExtra: Float, outerExtra: Float, alphaScale: Float) {
                val path = Path().apply {
                    moveTo(start.x + perpendicular.x * (innerWidth + innerExtra), start.y + perpendicular.y * (innerWidth + innerExtra))
                    lineTo(end.x + perpendicular.x * (outerWidth + outerExtra), end.y + perpendicular.y * (outerWidth + outerExtra))
                    lineTo(end.x - perpendicular.x * (outerWidth + outerExtra), end.y - perpendicular.y * (outerWidth + outerExtra))
                    lineTo(start.x - perpendicular.x * (innerWidth + innerExtra), start.y - perpendicular.y * (innerWidth + innerExtra))
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to color.copy(alpha = alpha * alphaScale),
                            0.18f to color.copy(alpha = alpha * alphaScale * 0.65f),
                            0.72f to color.copy(alpha = alpha * alphaScale * 0.12f),
                            1.0f to Color.Transparent,
                        ),
                        start = start,
                        end = end,
                    ),
                    blendMode = BlendMode.Screen,
                )
            }

            drawShaft(size.width * 0.018f, size.width * 0.025f, 0.22f)
            drawShaft(size.width * 0.008f, size.width * 0.012f, 0.42f)
            drawShaft(0f, 0f, 0.72f)
        }

        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    0.62f to Color.Transparent,
                    0.78f to color.copy(alpha = 0.14f),
                    0.92f to color.copy(alpha = 0.06f),
                    1.0f to Color.Transparent,
                ),
                center = center,
                radius = radius * 1.42f,
            ),
            center = center,
            radius = radius * 1.42f,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.08f),
                    color.copy(alpha = 0.028f),
                    Color.Transparent,
                ),
                center = center,
                radius = radius * 3.4f,
            ),
            center = center,
            radius = radius * 3.4f,
        )
    }
}

@Composable
private fun MoonBody(
    illuminatedFraction: Float,
    waxing: Boolean,
    brightLimbRotation: Float,
    bodyDiameter: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val phaseAngle = acos((1f - 2f * illuminatedFraction).coerceIn(-1f, 1f)) /
        (2f * PI.toFloat())
    val lunarCyclePhase = if (waxing) phaseAngle else 1f - phaseAngle
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        PlatformCelestialBody(
            body = CelestialBody.Moon,
            lunarCyclePhase = lunarCyclePhase,
            playing = true,
            modifier = Modifier
                .size(bodyDiameter)
                .graphicsLayer {
                    rotationZ = brightLimbRotation
                },
        )
    }
}

@Composable
private fun CelestialRays(
    position: Offset,
    bodyDiameter: Dp,
    color: Color,
    beamCount: Int,
    intensity: Float,
    surrounding: Boolean,
) {
    var motionPhase by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        while (true) {
            val elapsedSeconds = (withFrameNanos { it } - startNanos) / 1_000_000_000f
            motionPhase = elapsedSeconds * (PI.toFloat() * 2f / 3f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width * position.x, size.height * position.y)
        for (index in 0 until beamCount) {
            // Real crepuscular light reads as broad, soft, nearly parallel
            // washes streaming out of the sun's glow area — never a torch
            // cone from a single point. Wide origins, staggered reach, and
            // additive Screen blending so the shafts land as light on the
            // landscape instead of painted triangles.
            val progress = if (beamCount == 1) 0.5f else index.toFloat() / (beamCount - 1)
            val baseAngle = if (surrounding) {
                // Surround the disc with soft, separated shafts rather than
                // sending all light into one torch-like lower fan.
                progress * PI.toFloat() * 2f
            } else {
                0.63f + progress * 0.24f
            }
            val beamPhase = motionPhase + index * 0.75f
            val angle = baseAngle + sin(beamPhase.toDouble()).toFloat() * 0.028f
            // Start at the visible disc edge. The body itself is centered in
            // a larger light field, so using the field radius leaves a clear
            // gap between the disc and the first ray.
            val innerRadius = bodyDiameter.toPx() * 0.49f
            val outerRadius = size.maxDimension * (
                0.88f + (index * 17 % 20) / 100f +
                    sin((beamPhase * 0.8f).toDouble()).toFloat() * 0.035f
            )
            val innerHalfWidth = size.width * (0.028f + (index % 2) * 0.006f)
            val outerHalfWidth = size.width * (0.052f + (index * 11 % 17) / 600f)
            val rayAlpha = intensity * (
                0.52f + (index * 13 % 21) / 120f +
                    sin((beamPhase * 0.65f).toDouble()).toFloat() * 0.035f
            )
            val direction = Offset(cos(angle), sin(angle))
            val perpendicular = Offset(-direction.y, direction.x)
            val start = center + direction * innerRadius
            val end = center + direction * outerRadius

            fun rayPath(inner: Float, outer: Float): Path = Path().apply {
                moveTo(start.x + perpendicular.x * inner, start.y + perpendicular.y * inner)
                lineTo(end.x + perpendicular.x * outer, end.y + perpendicular.y * outer)
                lineTo(end.x - perpendicular.x * outer, end.y - perpendicular.y * outer)
                lineTo(start.x - perpendicular.x * inner, start.y - perpendicular.y * inner)
                close()
            }

            fun drawRay(inner: Float, outer: Float, alpha: Float) {
                drawPath(
                    path = rayPath(inner, outer),
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to color.copy(alpha = alpha),
                            0.5f to color.copy(alpha = alpha * 0.35f),
                            1.0f to Color.Transparent,
                        ),
                        start = start,
                        end = end,
                    ),
                    blendMode = BlendMode.Screen,
                )
            }

            // Feather the sides with two broad, nearly transparent layers.
            // This removes the hard polygon silhouette that made the shafts
            // look like torch beams against the sky.
            drawRay(innerHalfWidth + size.width * 0.018f, outerHalfWidth + size.width * 0.028f, rayAlpha * 0.10f)
            drawRay(innerHalfWidth + size.width * 0.009f, outerHalfWidth + size.width * 0.014f, rayAlpha * 0.16f)
            drawRay(innerHalfWidth, outerHalfWidth, rayAlpha * 0.34f)
        }
    }
}

/**
 * Finds the most recent epoch at which the visible body (sun by day, moon by
 * night) climbed past the ridge-line horizon, so the recap sweep can start
 * where the body actually rose. Returns null when the body has been up too
 * long to make a sweep meaningful.
 */
private fun mostRecentRiseEpoch(
    nowEpochSeconds: Double,
    latitudeDeg: Double,
    longitudeEastDeg: Double,
    isNight: Boolean,
): Double? {
    val horizonDeg = 0.75
    val stepSeconds = 600.0
    val searchSpanSeconds = (if (isNight) 30.0 else 17.0) * 3_600.0

    fun altitudeAt(t: Double): Double {
        val snap = skySnapshotAt(
            julianDay = julianDay(t),
            latitudeDeg = latitudeDeg,
            longitudeEastDeg = longitudeEastDeg,
        )
        return if (isNight) snap.moonAltAz.altitudeDeg else snap.sunAltAz.altitudeDeg
    }

    var newer = nowEpochSeconds
    var newerAlt = altitudeAt(newer)
    var t = nowEpochSeconds - stepSeconds
    val limit = nowEpochSeconds - searchSpanSeconds
    while (t >= limit) {
        val alt = altitudeAt(t)
        if (alt < horizonDeg && newerAlt >= horizonDeg) {
            // The body rose between t (below) and newer (above); bisect.
            var low = t
            var high = newer
            repeat(8) {
                val mid = (low + high) / 2.0
                if (altitudeAt(mid) >= horizonDeg) high = mid else low = mid
            }
            return high
        }
        newer = t
        newerAlt = alt
        t -= stepSeconds
    }
    return null
}

private fun scenicPosition(position: AltAz): Offset {
    val azimuthRadians = (position.azimuthDeg - 180.0) * PI / 180.0
    // The scene faces south, so west is on the viewer's left and east is on the right.
    val x = 0.37f - sin(azimuthRadians).toFloat() * 0.29f
    // The current wallpaper's mountain ridge sits around y 0.55. Map a
    // low-altitude body to that horizon so the skyline clip can occlude it.
    val altitudeRadians = position.altitudeDeg.coerceIn(0.0, 90.0) * PI / 180.0
    // Give the disc enough lower clearance to disappear completely behind the
    // ridge at sunset; the separate atmospheric light remains visible.
    val y = 0.68f - sin(altitudeRadians).toFloat() * 0.42f
    return Offset(x, y)
}

private fun illuminatedDiscPath(
    size: Size,
    illuminatedFraction: Float,
    rotationClockwiseFromUp: Float,
): Path {
    val radius = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val phaseCosine = illuminatedFraction.coerceIn(0f, 1f) * 2f - 1f
    val rotation = rotationClockwiseFromUp * PI.toFloat() / 180f
    val lightAxis = Offset(sin(rotation), -cos(rotation))
    val crossAxis = Offset(cos(rotation), sin(rotation))
    val steps = 64

    fun point(light: Float, cross: Float): Offset = Offset(
        x = center.x + radius * (light * lightAxis.x + cross * crossAxis.x),
        y = center.y + radius * (light * lightAxis.y + cross * crossAxis.y),
    )

    return Path().apply {
        for (step in 0..steps) {
            val cross = -1f + 2f * step / steps
            val edge = sqrt((1f - cross * cross).coerceAtLeast(0f))
            val p = point(edge, cross)
            if (step == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        for (step in steps downTo 0) {
            val cross = -1f + 2f * step / steps
            val edge = sqrt((1f - cross * cross).coerceAtLeast(0f))
            val p = point(-phaseCosine * edge, cross)
            lineTo(p.x, p.y)
        }
        close()
    }
}

private class PrayerNowSkylineShape(private val isNight: Boolean) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(
        Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            val ridge = if (isNight) NIGHT_RIDGE else DAY_RIDGE
            for (index in ridge.indices.reversed()) {
                lineTo(ridge[index].x * size.width, ridge[index].y * size.height)
            }
            close()
        },
    )
}

// Traced from the current Prayer Now wallpaper's mountain ridge. The celestial
// body is clipped to the sky above this path, so a low sun/moon disappears
// naturally behind the mountains instead of floating at the top of the card.
private val DAY_RIDGE = listOf(
    Offset(0.00f, 0.56f),
    Offset(0.16f, 0.54f),
    Offset(0.34f, 0.50f),
    Offset(0.52f, 0.43f),
    Offset(0.66f, 0.49f),
    Offset(0.82f, 0.56f),
    Offset(1.00f, 0.55f),
)

private val NIGHT_RIDGE = listOf(
    Offset(0.00f, 0.55f),
    Offset(0.16f, 0.53f),
    Offset(0.34f, 0.49f),
    Offset(0.52f, 0.42f),
    Offset(0.66f, 0.48f),
    Offset(0.82f, 0.55f),
    Offset(1.00f, 0.54f),
)
