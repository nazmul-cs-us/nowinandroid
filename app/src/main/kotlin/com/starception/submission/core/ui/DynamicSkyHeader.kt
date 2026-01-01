/*
 * Copyright 2024 The Android Open Source Project
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

package com.starception.submission.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Time periods for sky rendering
 */
enum class SkyTimePeriod {
    FAJR,       // Pre-dawn (4:00 AM - 5:30 AM)
    SUNRISE,    // Sunrise (5:30 AM - 7:00 AM)
    MORNING,    // Morning (7:00 AM - 10:00 AM)
    DAY,        // Midday (10:00 AM - 4:00 PM)
    ASR,        // Afternoon (4:00 PM - 6:00 PM)
    MAGHRIB,    // Sunset (6:00 PM - 7:30 PM)
    ISHA,       // Night (7:30 PM - 10:00 PM)
    NIGHT       // Late night (10:00 PM - 4:00 AM)
}

/**
 * Determines the current sky time period based on hour of day
 */
fun getCurrentSkyPeriod(): SkyTimePeriod {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 4..5 -> SkyTimePeriod.FAJR
        in 6..6 -> SkyTimePeriod.SUNRISE
        in 7..9 -> SkyTimePeriod.MORNING
        in 10..15 -> SkyTimePeriod.DAY
        in 16..17 -> SkyTimePeriod.ASR
        in 18..19 -> SkyTimePeriod.MAGHRIB
        in 20..21 -> SkyTimePeriod.ISHA
        else -> SkyTimePeriod.NIGHT
    }
}

/**
 * Sky colors for each time period
 */
data class SkyColors(
    val topColor: Color,
    val middleColor: Color,
    val bottomColor: Color,
    val horizonColor: Color
)

fun getSkyColors(period: SkyTimePeriod): SkyColors {
    return when (period) {
        SkyTimePeriod.FAJR -> SkyColors(
            topColor = Color(0xFF1a1a2e),      // Deep navy
            middleColor = Color(0xFF16213e),   // Dark blue
            bottomColor = Color(0xFF4a3f6b),   // Purple hint
            horizonColor = Color(0xFFe94560)   // Pink/red dawn
        )
        SkyTimePeriod.SUNRISE -> SkyColors(
            topColor = Color(0xFF4a90a4),      // Light blue
            middleColor = Color(0xFFf9a825),   // Golden
            bottomColor = Color(0xFFff7043),   // Orange
            horizonColor = Color(0xFFffcc80)   // Light orange
        )
        SkyTimePeriod.MORNING -> SkyColors(
            topColor = Color(0xFF64b5f6),      // Sky blue
            middleColor = Color(0xFF90caf9),   // Light blue
            bottomColor = Color(0xFFbbdefb),   // Pale blue
            horizonColor = Color(0xFFfff8e1)   // Cream
        )
        SkyTimePeriod.DAY -> SkyColors(
            topColor = Color(0xFF1976d2),      // Bright blue
            middleColor = Color(0xFF42a5f5),   // Sky blue
            bottomColor = Color(0xFF90caf9),   // Light blue
            horizonColor = Color(0xFFe3f2fd)   // Very light blue
        )
        SkyTimePeriod.ASR -> SkyColors(
            topColor = Color(0xFF5c8db8),      // Muted blue
            middleColor = Color(0xFFffb74d),   // Warm orange
            bottomColor = Color(0xFFffcc80),   // Light orange
            horizonColor = Color(0xFFffe0b2)   // Pale orange
        )
        SkyTimePeriod.MAGHRIB -> SkyColors(
            topColor = Color(0xFF512da8),      // Deep purple
            middleColor = Color(0xFFe91e63),   // Pink
            bottomColor = Color(0xFFff5722),   // Deep orange
            horizonColor = Color(0xFFffab91)   // Light coral
        )
        SkyTimePeriod.ISHA -> SkyColors(
            topColor = Color(0xFF0d1b2a),      // Very dark blue
            middleColor = Color(0xFF1b263b),   // Dark blue
            bottomColor = Color(0xFF415a77),   // Grayish blue
            horizonColor = Color(0xFF778da9)   // Light grayish blue
        )
        SkyTimePeriod.NIGHT -> SkyColors(
            topColor = Color(0xFF0a0a14),      // Almost black
            middleColor = Color(0xFF0d1b2a),   // Very dark blue
            bottomColor = Color(0xFF1b263b),   // Dark blue
            horizonColor = Color(0xFF2d3a4a)   // Dark grayish
        )
    }
}

/**
 * Star data class for consistent star positions
 */
data class Star(
    val x: Float,
    val y: Float,
    val size: Float,
    val alpha: Float
)

/**
 * Shooting star data class for falling star animation
 */
data class ShootingStar(
    val startX: Float,
    val startY: Float,
    val angle: Float,  // Angle in radians
    val length: Float,
    val speed: Float,  // Animation speed multiplier
    val delay: Float   // Delay before appearing (0-1)
)

/**
 * Dynamic Sky Header that changes based on time of day
 */
@Composable
fun DynamicSkyHeader(
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
    period: SkyTimePeriod = getCurrentSkyPeriod()
) {
    val skyColors = getSkyColors(period)

    // Generate consistent stars (using seed for reproducibility)
    val stars = remember {
        val random = Random(42) // Fixed seed for consistent stars
        List(80) { // More stars for richer sky
            Star(
                x = random.nextFloat(),
                y = random.nextFloat() * 0.65f, // Stars only in upper 65%
                size = random.nextFloat() * 2.5f + 0.5f,
                alpha = random.nextFloat() * 0.6f + 0.2f
            )
        }
    }

    // Generate shooting stars
    val shootingStars = remember {
        val random = Random(123)
        List(3) {
            ShootingStar(
                startX = random.nextFloat() * 0.6f + 0.2f, // Middle 60% of screen
                startY = random.nextFloat() * 0.3f + 0.05f, // Upper portion
                angle = (random.nextFloat() * 0.5f + 0.3f) * PI.toFloat(), // Downward angle
                length = random.nextFloat() * 0.08f + 0.06f,
                speed = random.nextFloat() * 0.5f + 0.8f,
                delay = random.nextFloat()
            )
        }
    }

    // Twinkling animation for stars
    val infiniteTransition = rememberInfiniteTransition(label = "starTwinkle")
    val twinkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle"
    )

    // Shooting star animation
    val shootingStarProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shootingStar"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Draw sky gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        skyColors.topColor,
                        skyColors.middleColor,
                        skyColors.bottomColor,
                        skyColors.horizonColor
                    ),
                    startY = 0f,
                    endY = canvasHeight
                )
            )

            // Draw horizon glow for sunrise/sunset
            if (period == SkyTimePeriod.SUNRISE || period == SkyTimePeriod.MAGHRIB) {
                drawHorizonGlow(canvasWidth, canvasHeight, period)
            }

            // Draw stars for night periods
            if (period == SkyTimePeriod.NIGHT || period == SkyTimePeriod.ISHA || period == SkyTimePeriod.FAJR) {
                drawStars(stars, canvasWidth, canvasHeight, twinkleAlpha, period)
            }

            // Draw shooting stars for all periods (subtle during day, prominent at night)
            drawShootingStars(shootingStars, canvasWidth, canvasHeight, shootingStarProgress, period)

            // Draw celestial body (sun or moon)
            drawCelestialBody(canvasWidth, canvasHeight, period)

            // Draw landscape silhouette (mosque/hills)
            drawLandscapeSilhouette(canvasWidth, canvasHeight, period)
        }
    }
}

private fun DrawScope.drawHorizonGlow(width: Float, height: Float, period: SkyTimePeriod) {
    val glowColor = when (period) {
        SkyTimePeriod.SUNRISE -> Color(0xFFFFD54F)
        SkyTimePeriod.MAGHRIB -> Color(0xFFFF7043)
        else -> Color.Transparent
    }

    // Draw radial gradient glow at horizon
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                glowColor.copy(alpha = 0.6f),
                glowColor.copy(alpha = 0.3f),
                Color.Transparent
            ),
            center = Offset(width * 0.5f, height * 0.85f),
            radius = width * 0.5f
        ),
        center = Offset(width * 0.5f, height * 0.85f),
        radius = width * 0.5f
    )
}

private fun DrawScope.drawStars(
    stars: List<Star>,
    width: Float,
    height: Float,
    twinkleAlpha: Float,
    period: SkyTimePeriod
) {
    val starVisibility = when (period) {
        SkyTimePeriod.NIGHT -> 1f
        SkyTimePeriod.ISHA -> 0.9f
        SkyTimePeriod.FAJR -> 0.5f
        else -> 0f
    }

    stars.forEach { star ->
        val adjustedAlpha = star.alpha * starVisibility *
            if (star.size > 2f) twinkleAlpha else 1f

        // Draw star glow for larger stars
        if (star.size > 1.5f) {
            drawCircle(
                color = Color.White.copy(alpha = adjustedAlpha * 0.3f),
                radius = star.size * 2f,
                center = Offset(star.x * width, star.y * height)
            )
        }

        drawCircle(
            color = Color.White.copy(alpha = adjustedAlpha),
            radius = star.size,
            center = Offset(star.x * width, star.y * height)
        )
    }
}

private fun DrawScope.drawShootingStars(
    shootingStars: List<ShootingStar>,
    width: Float,
    height: Float,
    progress: Float,
    period: SkyTimePeriod
) {
    val visibility = when (period) {
        SkyTimePeriod.NIGHT -> 1f
        SkyTimePeriod.ISHA -> 0.9f
        SkyTimePeriod.FAJR -> 0.6f
        SkyTimePeriod.MAGHRIB -> 0.5f
        SkyTimePeriod.SUNRISE -> 0.4f
        SkyTimePeriod.DAY -> 0.35f
        SkyTimePeriod.MORNING -> 0.3f
        SkyTimePeriod.ASR -> 0.35f
    }

    shootingStars.forEach { star ->
        // Calculate progress for this star based on its delay
        val adjustedProgress = ((progress * star.speed + star.delay) % 1f)

        // Only draw if in the visible phase (0.0 to 0.3 of cycle)
        if (adjustedProgress < 0.3f) {
            val starProgress = adjustedProgress / 0.3f

            // Calculate position along the path
            val startX = star.startX * width
            val startY = star.startY * height
            val moveDistance = width * 0.15f * starProgress

            val currentX = startX + cos(star.angle) * moveDistance
            val currentY = startY + sin(star.angle) * moveDistance

            // Trail length decreases as star fades
            val trailLength = star.length * width * (1f - starProgress * 0.5f)
            val trailEndX = currentX - cos(star.angle) * trailLength
            val trailEndY = currentY - sin(star.angle) * trailLength

            // Fade in then out
            val alpha = when {
                starProgress < 0.2f -> starProgress / 0.2f
                starProgress > 0.7f -> (1f - starProgress) / 0.3f
                else -> 1f
            } * visibility

            // Draw the shooting star trail with gradient
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = alpha),
                        Color.White.copy(alpha = alpha * 0.5f),
                        Color.Transparent
                    ),
                    start = Offset(currentX, currentY),
                    end = Offset(trailEndX, trailEndY)
                ),
                start = Offset(currentX, currentY),
                end = Offset(trailEndX, trailEndY),
                strokeWidth = 2.5f
            )

            // Bright head of shooting star
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = 3f,
                center = Offset(currentX, currentY)
            )

            // Glow around head
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.4f),
                radius = 6f,
                center = Offset(currentX, currentY)
            )
        }
    }
}

private fun DrawScope.drawCelestialBody(width: Float, height: Float, period: SkyTimePeriod) {
    when (period) {
        SkyTimePeriod.SUNRISE -> {
            // Rising sun near horizon
            drawSun(
                center = Offset(width * 0.5f, height * 0.75f),
                radius = width * 0.08f,
                glowRadius = width * 0.15f
            )
        }
        SkyTimePeriod.MORNING -> {
            // Sun rising higher
            drawSun(
                center = Offset(width * 0.7f, height * 0.35f),
                radius = width * 0.07f,
                glowRadius = width * 0.12f
            )
        }
        SkyTimePeriod.DAY -> {
            // Sun high in sky
            drawSun(
                center = Offset(width * 0.75f, height * 0.2f),
                radius = width * 0.06f,
                glowRadius = width * 0.1f
            )
        }
        SkyTimePeriod.ASR -> {
            // Sun descending
            drawSun(
                center = Offset(width * 0.3f, height * 0.4f),
                radius = width * 0.07f,
                glowRadius = width * 0.12f
            )
        }
        SkyTimePeriod.MAGHRIB -> {
            // Setting sun
            drawSun(
                center = Offset(width * 0.5f, height * 0.8f),
                radius = width * 0.09f,
                glowRadius = width * 0.18f,
                isSettingSun = true
            )
        }
        SkyTimePeriod.ISHA, SkyTimePeriod.NIGHT -> {
            // Crescent moon
            drawCrescentMoon(
                center = Offset(width * 0.75f, height * 0.25f),
                radius = width * 0.06f
            )
        }
        SkyTimePeriod.FAJR -> {
            // Fading moon
            drawCrescentMoon(
                center = Offset(width * 0.2f, height * 0.3f),
                radius = width * 0.05f,
                alpha = 0.6f
            )
        }
    }
}

private fun DrawScope.drawSun(
    center: Offset,
    radius: Float,
    glowRadius: Float,
    isSettingSun: Boolean = false
) {
    val sunColor = if (isSettingSun) Color(0xFFFF6B35) else Color(0xFFFFD54F)
    val glowColor = if (isSettingSun) Color(0xFFFF8A65) else Color(0xFFFFE082)

    // Outer glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                glowColor.copy(alpha = 0.4f),
                glowColor.copy(alpha = 0.1f),
                Color.Transparent
            ),
            center = center,
            radius = glowRadius
        ),
        center = center,
        radius = glowRadius
    )

    // Sun body
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White,
                sunColor,
                sunColor.copy(alpha = 0.8f)
            ),
            center = center,
            radius = radius
        ),
        center = center,
        radius = radius
    )
}

private fun DrawScope.drawCrescentMoon(
    center: Offset,
    radius: Float,
    alpha: Float = 1f
) {
    val moonColor = Color(0xFFFFFDE7).copy(alpha = alpha)
    val shadowColor = Color(0xFF1a1a2e)

    // Moon glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                moonColor.copy(alpha = 0.3f * alpha),
                Color.Transparent
            ),
            center = center,
            radius = radius * 2f
        ),
        center = center,
        radius = radius * 2f
    )

    // Full moon circle
    drawCircle(
        color = moonColor,
        radius = radius,
        center = center
    )

    // Shadow circle to create crescent effect
    drawCircle(
        color = shadowColor,
        radius = radius * 0.85f,
        center = Offset(center.x + radius * 0.4f, center.y - radius * 0.1f)
    )
}

private fun DrawScope.drawLandscapeSilhouette(width: Float, height: Float, period: SkyTimePeriod) {
    val silhouetteColor = when (period) {
        SkyTimePeriod.NIGHT, SkyTimePeriod.ISHA -> Color(0xFF080810)
        SkyTimePeriod.FAJR -> Color(0xFF151525)
        SkyTimePeriod.MAGHRIB -> Color(0xFF251515)
        else -> Color(0xFF152515).copy(alpha = 0.35f)
    }

    val silhouetteAlpha = when (period) {
        SkyTimePeriod.DAY, SkyTimePeriod.MORNING -> 0.22f
        SkyTimePeriod.ASR -> 0.32f
        else -> 1f
    }

    val baseY = height * 0.94f
    val centerX = width * 0.5f

    // ===== BACKGROUND LAYER - Distant cityscape =====
    val distantPath = Path().apply {
        moveTo(0f, baseY - height * 0.02f)
        // Gentle hills/buildings in background
        lineTo(width * 0.1f, baseY - height * 0.05f)
        lineTo(width * 0.15f, baseY - height * 0.03f)
        lineTo(width * 0.25f, baseY - height * 0.06f)
        lineTo(width * 0.35f, baseY - height * 0.04f)
        lineTo(width * 0.45f, baseY - height * 0.02f)
        lineTo(width * 0.55f, baseY - height * 0.02f)
        lineTo(width * 0.65f, baseY - height * 0.04f)
        lineTo(width * 0.75f, baseY - height * 0.06f)
        lineTo(width * 0.85f, baseY - height * 0.03f)
        lineTo(width * 0.9f, baseY - height * 0.05f)
        lineTo(width, baseY - height * 0.02f)
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }
    drawPath(
        path = distantPath,
        color = silhouetteColor.copy(alpha = silhouetteAlpha * 0.5f),
        style = Fill
    )

    // ===== MAIN MOSQUE STRUCTURE =====
    val mosquePath = Path().apply {
        // ===== LEFT TALL MINARET =====
        val leftMinaretX = width * 0.12f
        val minaretWidth = width * 0.025f
        val leftMinaretHeight = height * 0.38f

        // Minaret body with taper
        moveTo(leftMinaretX - minaretWidth * 1.2f, baseY)
        lineTo(leftMinaretX - minaretWidth, baseY - leftMinaretHeight * 0.7f)
        // Upper balcony
        lineTo(leftMinaretX - minaretWidth * 1.5f, baseY - leftMinaretHeight * 0.7f)
        lineTo(leftMinaretX - minaretWidth * 1.5f, baseY - leftMinaretHeight * 0.72f)
        lineTo(leftMinaretX - minaretWidth * 0.8f, baseY - leftMinaretHeight * 0.72f)
        lineTo(leftMinaretX - minaretWidth * 0.7f, baseY - leftMinaretHeight * 0.95f)
        // Pointed cap with finial
        quadraticBezierTo(
            leftMinaretX, baseY - leftMinaretHeight - height * 0.03f,
            leftMinaretX + minaretWidth * 0.7f, baseY - leftMinaretHeight * 0.95f
        )
        lineTo(leftMinaretX + minaretWidth * 0.8f, baseY - leftMinaretHeight * 0.72f)
        lineTo(leftMinaretX + minaretWidth * 1.5f, baseY - leftMinaretHeight * 0.72f)
        lineTo(leftMinaretX + minaretWidth * 1.5f, baseY - leftMinaretHeight * 0.7f)
        lineTo(leftMinaretX + minaretWidth, baseY - leftMinaretHeight * 0.7f)
        lineTo(leftMinaretX + minaretWidth * 1.2f, baseY)
        close()

        // ===== LEFT SECONDARY DOME =====
        val leftDomeX = width * 0.26f
        val smallDomeWidth = width * 0.07f
        val smallDomeHeight = height * 0.10f
        val smallDomeBase = height * 0.05f

        moveTo(leftDomeX - smallDomeWidth, baseY)
        lineTo(leftDomeX - smallDomeWidth, baseY - smallDomeBase)
        cubicTo(
            leftDomeX - smallDomeWidth, baseY - smallDomeBase - smallDomeHeight * 0.5f,
            leftDomeX - smallDomeWidth * 0.3f, baseY - smallDomeBase - smallDomeHeight,
            leftDomeX, baseY - smallDomeBase - smallDomeHeight - height * 0.01f
        )
        cubicTo(
            leftDomeX + smallDomeWidth * 0.3f, baseY - smallDomeBase - smallDomeHeight,
            leftDomeX + smallDomeWidth, baseY - smallDomeBase - smallDomeHeight * 0.5f,
            leftDomeX + smallDomeWidth, baseY - smallDomeBase
        )
        lineTo(leftDomeX + smallDomeWidth, baseY)
        close()

        // ===== MAIN CENTRAL DOME =====
        val mainDomeWidth = width * 0.15f
        val mainDomeHeight = height * 0.25f
        val mainDomeBase = height * 0.10f

        moveTo(centerX - mainDomeWidth - width * 0.02f, baseY)
        lineTo(centerX - mainDomeWidth - width * 0.02f, baseY - mainDomeBase * 0.5f)
        lineTo(centerX - mainDomeWidth, baseY - mainDomeBase * 0.5f)
        lineTo(centerX - mainDomeWidth, baseY - mainDomeBase)
        // Elegant onion dome curve
        cubicTo(
            centerX - mainDomeWidth * 0.95f, baseY - mainDomeBase - mainDomeHeight * 0.4f,
            centerX - mainDomeWidth * 0.35f, baseY - mainDomeBase - mainDomeHeight * 1.05f,
            centerX, baseY - mainDomeBase - mainDomeHeight - height * 0.015f
        )
        cubicTo(
            centerX + mainDomeWidth * 0.35f, baseY - mainDomeBase - mainDomeHeight * 1.05f,
            centerX + mainDomeWidth * 0.95f, baseY - mainDomeBase - mainDomeHeight * 0.4f,
            centerX + mainDomeWidth, baseY - mainDomeBase
        )
        lineTo(centerX + mainDomeWidth, baseY - mainDomeBase * 0.5f)
        lineTo(centerX + mainDomeWidth + width * 0.02f, baseY - mainDomeBase * 0.5f)
        lineTo(centerX + mainDomeWidth + width * 0.02f, baseY)
        close()

        // ===== RIGHT SECONDARY DOME =====
        val rightDomeX = width * 0.74f

        moveTo(rightDomeX - smallDomeWidth, baseY)
        lineTo(rightDomeX - smallDomeWidth, baseY - smallDomeBase)
        cubicTo(
            rightDomeX - smallDomeWidth, baseY - smallDomeBase - smallDomeHeight * 0.5f,
            rightDomeX - smallDomeWidth * 0.3f, baseY - smallDomeBase - smallDomeHeight,
            rightDomeX, baseY - smallDomeBase - smallDomeHeight - height * 0.01f
        )
        cubicTo(
            rightDomeX + smallDomeWidth * 0.3f, baseY - smallDomeBase - smallDomeHeight,
            rightDomeX + smallDomeWidth, baseY - smallDomeBase - smallDomeHeight * 0.5f,
            rightDomeX + smallDomeWidth, baseY - smallDomeBase
        )
        lineTo(rightDomeX + smallDomeWidth, baseY)
        close()

        // ===== RIGHT TALL MINARET =====
        val rightMinaretX = width * 0.88f

        moveTo(rightMinaretX - minaretWidth * 1.2f, baseY)
        lineTo(rightMinaretX - minaretWidth, baseY - leftMinaretHeight * 0.7f)
        lineTo(rightMinaretX - minaretWidth * 1.5f, baseY - leftMinaretHeight * 0.7f)
        lineTo(rightMinaretX - minaretWidth * 1.5f, baseY - leftMinaretHeight * 0.72f)
        lineTo(rightMinaretX - minaretWidth * 0.8f, baseY - leftMinaretHeight * 0.72f)
        lineTo(rightMinaretX - minaretWidth * 0.7f, baseY - leftMinaretHeight * 0.95f)
        quadraticBezierTo(
            rightMinaretX, baseY - leftMinaretHeight - height * 0.03f,
            rightMinaretX + minaretWidth * 0.7f, baseY - leftMinaretHeight * 0.95f
        )
        lineTo(rightMinaretX + minaretWidth * 0.8f, baseY - leftMinaretHeight * 0.72f)
        lineTo(rightMinaretX + minaretWidth * 1.5f, baseY - leftMinaretHeight * 0.72f)
        lineTo(rightMinaretX + minaretWidth * 1.5f, baseY - leftMinaretHeight * 0.7f)
        lineTo(rightMinaretX + minaretWidth, baseY - leftMinaretHeight * 0.7f)
        lineTo(rightMinaretX + minaretWidth * 1.2f, baseY)
        close()

        // ===== GROUND/BASE =====
        moveTo(0f, baseY)
        lineTo(width, baseY)
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }

    drawPath(
        path = mosquePath,
        color = silhouetteColor.copy(alpha = silhouetteAlpha),
        style = Fill
    )

    // ===== ARCHED WINDOWS ON MAIN STRUCTURE =====
    val windowColor = when (period) {
        SkyTimePeriod.NIGHT, SkyTimePeriod.ISHA -> Color(0xFFFFE082).copy(alpha = 0.4f) // Warm light
        SkyTimePeriod.FAJR -> Color(0xFFFFE082).copy(alpha = 0.2f)
        SkyTimePeriod.MAGHRIB -> Color(0xFFFF8A65).copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    if (windowColor != Color.Transparent) {
        // Left window
        drawArc(
            color = windowColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(centerX - width * 0.12f, baseY - height * 0.08f),
            size = androidx.compose.ui.geometry.Size(width * 0.04f, height * 0.04f)
        )
        // Center window
        drawArc(
            color = windowColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(centerX - width * 0.02f, baseY - height * 0.09f),
            size = androidx.compose.ui.geometry.Size(width * 0.04f, height * 0.05f)
        )
        // Right window
        drawArc(
            color = windowColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(centerX + width * 0.08f, baseY - height * 0.08f),
            size = androidx.compose.ui.geometry.Size(width * 0.04f, height * 0.04f)
        )
    }

    // ===== FINIALS AND CRESCENTS =====
    val mainDomeTop = baseY - height * 0.10f - height * 0.25f - height * 0.015f
    val leftMinaretTop = baseY - height * 0.38f - height * 0.03f
    val rightMinaretTop = leftMinaretTop

    // Finial poles
    val poleColor = silhouetteColor.copy(alpha = silhouetteAlpha)
    drawLine(
        color = poleColor,
        start = Offset(centerX, mainDomeTop),
        end = Offset(centerX, mainDomeTop - height * 0.04f),
        strokeWidth = width * 0.004f
    )

    // Crescent colors
    val crescentColor = if (period == SkyTimePeriod.DAY || period == SkyTimePeriod.MORNING || period == SkyTimePeriod.ASR) {
        silhouetteColor.copy(alpha = silhouetteAlpha)
    } else {
        Color(0xFFFFD700).copy(alpha = 0.9f)
    }

    // Main dome crescent
    drawCircle(
        color = crescentColor,
        radius = width * 0.016f,
        center = Offset(centerX, mainDomeTop - height * 0.05f)
    )
    if (period != SkyTimePeriod.DAY && period != SkyTimePeriod.MORNING && period != SkyTimePeriod.ASR) {
        drawCircle(
            color = silhouetteColor,
            radius = width * 0.012f,
            center = Offset(centerX + width * 0.006f, mainDomeTop - height * 0.052f)
        )
    }

    // Minaret crescents (night only)
    if (period == SkyTimePeriod.NIGHT || period == SkyTimePeriod.ISHA || period == SkyTimePeriod.FAJR) {
        val minaretCrescentAlpha = if (period == SkyTimePeriod.FAJR) 0.5f else 0.8f
        // Left minaret
        drawCircle(
            color = Color(0xFFFFD700).copy(alpha = minaretCrescentAlpha),
            radius = width * 0.007f,
            center = Offset(width * 0.12f, leftMinaretTop - height * 0.01f)
        )
        // Right minaret
        drawCircle(
            color = Color(0xFFFFD700).copy(alpha = minaretCrescentAlpha),
            radius = width * 0.007f,
            center = Offset(width * 0.88f, rightMinaretTop - height * 0.01f)
        )

        // Small dome finials
        drawCircle(
            color = Color(0xFFFFD700).copy(alpha = minaretCrescentAlpha * 0.6f),
            radius = width * 0.005f,
            center = Offset(width * 0.26f, baseY - height * 0.05f - height * 0.10f - height * 0.02f)
        )
        drawCircle(
            color = Color(0xFFFFD700).copy(alpha = minaretCrescentAlpha * 0.6f),
            radius = width * 0.005f,
            center = Offset(width * 0.74f, baseY - height * 0.05f - height * 0.10f - height * 0.02f)
        )
    }
}

/**
 * Composable effect that enables immersive full-screen mode by hiding the status bar.
 * The status bar is restored when leaving the screen.
 *
 * Usage: Call this at the top of your screen composable to enable immersive mode.
 */
@Composable
fun ImmersiveFullScreenEffect() {
    val view = androidx.compose.ui.platform.LocalView.current

    androidx.compose.runtime.DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val insetsController = window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, view)
        }

        // Hide status bar for immersive experience
        insetsController?.apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            // Restore status bar when leaving the screen
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }
    }
}
