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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.prayer_celestial_moon_nasa
import com.starception.submission.core.images.resources.prayer_celestial_sun_3d
import com.starception.submission.prayer.sky.AltAz
import com.starception.submission.prayer.sky.julianDay
import com.starception.submission.prayer.sky.skySnapshotAt
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
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
        val moonPosition = scenicPosition(snapshot.moonAltAz)
        val fieldWidth = maxWidth
        val fieldHeight = maxHeight

        if (isNight &&
            (forceSunForTesting ||
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
            } else {
                CelestialRays(
                    position = moonPosition,
                    bodyDiameter = diameter,
                    color = Color(0xFFE8F4FF),
                    beamCount = 8,
                    intensity = 0.14f,
                    surrounding = true,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize(),
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
        if (!isNight && snapshot.sunAltAz.altitudeDeg > -1.5) {
            SunAtmosphericLight(
                position = sunPosition,
                bodyDiameter = diameter,
                color = Color(0xFFFFD76A),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                SunBody(
                    modifier = Modifier
                        .offset(
                            x = fieldWidth * sunPosition.x - lightFieldDiameter / 2,
                            y = fieldHeight * sunPosition.y - lightFieldDiameter / 2,
                        )
                        .size(lightFieldDiameter),
                    bodyDiameter = diameter,
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
        // The real photographic sun, drawn above the time-of-day washes so its
        // texture reads at full warmth instead of the old muddy olive ball.
        Image(
            painter = painterResource(Res.drawable.prayer_celestial_sun_3d),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(bodyDiameter).clip(CircleShape),
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
    brightLimbRotation: Float,
    bodyDiameter: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // A trace of earthshine keeps the unlit sphere dimensional without
        // misrepresenting the illuminated lunar phase.
        Image(
            painter = painterResource(Res.drawable.prayer_celestial_moon_nasa),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(bodyDiameter).clip(CircleShape).graphicsLayer { alpha = 0.10f },
        )
        Image(
            painter = painterResource(Res.drawable.prayer_celestial_moon_nasa),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(bodyDiameter)
                .clip(CircleShape)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawPath(
                        path = illuminatedDiscPath(
                            size = size,
                            illuminatedFraction = illuminatedFraction,
                            rotationClockwiseFromUp = brightLimbRotation,
                        ),
                        color = Color.White,
                        blendMode = BlendMode.DstIn,
                    )
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
    // The artwork's open sky is only the band above its treeline (~y 0.19),
    // so altitude compresses into that strip; a low body settles at the trees
    // exactly where the reference image's sun glows through the foliage.
    val altitudeRadians = position.altitudeDeg.coerceIn(0.0, 90.0) * PI / 180.0
    val y = 0.19f - sin(altitudeRadians).toFloat() * 0.16f
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

// Traced from the artwork's mosque treeline (~y 0.19). The tall foliage at the
// left edge is deliberately excluded: like the reference image, a low western
// sun should glow among those trees rather than be clipped by them.
private val DAY_RIDGE = listOf(
    Offset(0.00f, 0.185f),
    Offset(0.12f, 0.185f),
    Offset(0.25f, 0.190f),
    Offset(0.38f, 0.200f),
    Offset(0.50f, 0.190f),
    Offset(0.69f, 0.187f),
    Offset(0.81f, 0.190f),
    Offset(1.00f, 0.195f),
)

private val NIGHT_RIDGE = listOf(
    Offset(0.00f, 0.180f),
    Offset(0.12f, 0.180f),
    Offset(0.25f, 0.185f),
    Offset(0.38f, 0.195f),
    Offset(0.50f, 0.185f),
    Offset(0.69f, 0.182f),
    Offset(0.81f, 0.185f),
    Offset(1.00f, 0.190f),
)
