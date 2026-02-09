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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.starception.submission.R
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
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
 * Composable version that returns NIGHT when dark mode is enabled,
 * otherwise returns the time-based sky period.
 * This respects both system dark mode AND app's own dark mode setting from Settings -> Appearance.
 */
@Composable
fun getCurrentSkyPeriodForTheme(): SkyTimePeriod {
    val isDarkMode = LocalDarkTheme.current
    return if (isDarkMode) {
        SkyTimePeriod.NIGHT
    } else {
        getCurrentSkyPeriod()
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
 * Now supports dynamic regeneration for varied positions
 */
data class ShootingStar(
    val startX: Float,
    val startY: Float,
    val angle: Float,  // Angle in radians
    val length: Float,
    val speed: Float,  // Animation speed multiplier
    val delay: Float,  // Delay before appearing (0-1)
    val seed: Int = 0  // Seed for this shooting star instance
)

/**
 * Generate a shooting star with random position based on seed
 */
private fun generateShootingStar(seed: Int): ShootingStar {
    val random = Random(seed)
    return ShootingStar(
        startX = random.nextFloat() * 0.8f + 0.1f, // 10% to 90% of width
        startY = random.nextFloat() * 0.25f + 0.15f, // 15% to 40% of height (below toolbar)
        angle = (random.nextFloat() * 0.6f + 0.2f) * PI.toFloat(), // Varied downward angles
        length = random.nextFloat() * 0.1f + 0.05f,
        speed = random.nextFloat() * 0.4f + 0.7f,
        delay = random.nextFloat(),
        seed = seed
    )
}

/**
 * Dynamic Sky Header that changes based on time of day
 * Uses the Masjid-e-Nabawi silhouette from SVG vector drawable
 */
@Composable
fun DynamicSkyHeader(
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
    period: SkyTimePeriod = getCurrentSkyPeriod()
) {
    val skyColors = getSkyColors(period)

    // Load the Masjid-e-Nabawi silhouette vector drawable
    val silhouettePainter = painterResource(id = R.drawable.ic_masjid_nabawi_silhouette)

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

    // Generate multiple sets of shooting stars with different seeds
    // Each set appears at different times during the animation cycle
    val shootingStarSets = remember {
        List(3) { setIndex -> // 3 sets of shooting stars
            List(2) { starIndex -> // 2 stars per set
                generateShootingStar(42 + setIndex * 100 + starIndex * 37)
            }
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

    // Shooting star animation - smooth continuous animation
    val shootingStarProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing), // Slower for smoother movement
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
            // Each set appears at staggered intervals (0-33%, 33-66%, 66-100% of cycle)
            shootingStarSets.forEachIndexed { setIndex, starSet ->
                val setOffset = setIndex / 3f // 0, 0.33, 0.66
                val adjustedProgress = (shootingStarProgress + setOffset) % 1f
                drawShootingStars(starSet, canvasWidth, canvasHeight, adjustedProgress, period)
            }

            // Draw celestial body (sun or moon)
            drawCelestialBody(canvasWidth, canvasHeight, period)

            // Draw landscape silhouette using vector drawable from SVG
            drawMasjidSilhouette(silhouettePainter, canvasWidth, canvasHeight, period)

            // Draw colored overlay elements (green dome, crescents, lit windows)
            drawSilhouetteOverlays(canvasWidth, canvasHeight, period)
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
    // NOTE: Toolbar occupies top ~25% of header (100dp out of 420dp)
    // Celestial bodies positioned to avoid overlap with toolbar
    when (period) {
        SkyTimePeriod.SUNRISE -> {
            // Rising sun near horizon
            drawSun(
                center = Offset(width * 0.45f, height * 0.68f),
                radius = width * 0.08f,
                glowRadius = width * 0.15f
            )
        }
        SkyTimePeriod.MORNING -> {
            // Sun rising higher - moved left and up
            drawSun(
                center = Offset(width * 0.72f, height * 0.38f),
                radius = width * 0.065f,
                glowRadius = width * 0.11f
            )
        }
        SkyTimePeriod.DAY -> {
            // Sun in sky - moved left and up
            drawSun(
                center = Offset(width * 0.75f, height * 0.32f),
                radius = width * 0.055f,
                glowRadius = width * 0.09f
            )
        }
        SkyTimePeriod.ASR -> {
            // Sun descending - moved slightly left
            drawSun(
                center = Offset(width * 0.15f, height * 0.42f),
                radius = width * 0.065f,
                glowRadius = width * 0.11f
            )
        }
        SkyTimePeriod.MAGHRIB -> {
            // Setting sun near horizon - moved left
            drawSun(
                center = Offset(width * 0.45f, height * 0.70f),
                radius = width * 0.09f,
                glowRadius = width * 0.18f,
                isSettingSun = true
            )
        }
        SkyTimePeriod.ISHA, SkyTimePeriod.NIGHT -> {
            // Crescent moon - moved left and up
            drawCrescentMoon(
                center = Offset(width * 0.72f, height * 0.32f),
                radius = width * 0.055f
            )
        }
        SkyTimePeriod.FAJR -> {
            // Fading moon - moved slightly left and up
            drawCrescentMoon(
                center = Offset(width * 0.12f, height * 0.35f),
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

/**
 * Draw the Masjid-e-Nabawi silhouette using the vector drawable from SVG
 */
private fun DrawScope.drawMasjidSilhouette(
    painter: Painter,
    width: Float,
    height: Float,
    period: SkyTimePeriod
) {
    // Determine silhouette color based on time period
    val silhouetteColor = when (period) {
        SkyTimePeriod.NIGHT, SkyTimePeriod.ISHA -> Color(0xFF080810)
        SkyTimePeriod.FAJR -> Color(0xFF151525)
        SkyTimePeriod.MAGHRIB -> Color(0xFF251515)
        else -> Color(0xFF1a1a1a)
    }

    val silhouetteAlpha = when (period) {
        SkyTimePeriod.DAY, SkyTimePeriod.MORNING -> 0.35f
        SkyTimePeriod.ASR -> 0.45f
        else -> 1f
    }

    // Calculate size to fit the silhouette at the bottom of the canvas
    // The vector drawable has aspect ratio 240:162 (about 1.48:1)
    // Use full width and calculate proportional height
    val aspectRatio = 240f / 162f
    val finalWidth = width
    val finalHeight = finalWidth / aspectRatio

    // Align to bottom, full width
    val left = 0f
    val top = height - finalHeight

    // Draw the silhouette with tinting
    translate(left = left, top = top) {
        with(painter) {
            draw(
                size = Size(finalWidth, finalHeight),
                colorFilter = ColorFilter.tint(
                    color = silhouetteColor.copy(alpha = silhouetteAlpha),
                    blendMode = BlendMode.SrcIn
                )
            )
        }
    }
}

/**
 * Draw colored overlay elements on top of the silhouette
 * (gold crescents, lit windows)
 */
private fun DrawScope.drawSilhouetteOverlays(
    width: Float,
    height: Float,
    period: SkyTimePeriod
) {
    // Position calculations based on silhouette placement
    val baseY = height  // At very bottom
    val buildingBaseHeight = height * 0.08f

    // ===== MINARET CRESCENTS =====
    val minaretWidth = width * 0.016f
    val minaretHeight = height * 0.38f
    val minaretPositions = listOf(
        width * 0.02f to minaretHeight,
        width * 0.15f to minaretHeight * 0.85f,
        width * 0.30f to minaretHeight * 0.9f,
        width * 0.70f to minaretHeight * 0.9f,
        width * 0.85f to minaretHeight * 0.85f,
        width * 0.98f to minaretHeight
    )

    val crescentColor = Color(0xFFFFD700)
    val crescentAlpha = when (period) {
        SkyTimePeriod.NIGHT, SkyTimePeriod.ISHA -> 0.8f
        SkyTimePeriod.FAJR -> 0.5f
        else -> 0.6f
    }

    minaretPositions.forEach { (x, h) ->
        val minaretTop = baseY - buildingBaseHeight - h - height * 0.02f
        drawCircle(
            color = crescentColor.copy(alpha = crescentAlpha),
            radius = width * 0.006f,
            center = Offset(x, minaretTop)
        )
    }

    // ===== ARCHED WINDOWS (lit at night) =====
    val windowColor = when (period) {
        SkyTimePeriod.NIGHT, SkyTimePeriod.ISHA -> Color(0xFFFFE082).copy(alpha = 0.5f)
        SkyTimePeriod.FAJR -> Color(0xFFFFE082).copy(alpha = 0.3f)
        SkyTimePeriod.MAGHRIB -> Color(0xFFFF8A65).copy(alpha = 0.4f)
        else -> Color.Transparent
    }

    if (windowColor != Color.Transparent) {
        for (i in 0..8) {
            val windowX = width * 0.08f + i * width * 0.1f
            drawArc(
                color = windowColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(windowX - width * 0.012f, baseY - height * 0.055f),
                size = Size(width * 0.024f, height * 0.025f)
            )
        }
    }
}

private fun DrawScope.drawLandscapeSilhouette(width: Float, height: Float, period: SkyTimePeriod) {
    val silhouetteColor = when (period) {
        SkyTimePeriod.NIGHT, SkyTimePeriod.ISHA -> Color(0xFF080810)
        SkyTimePeriod.FAJR -> Color(0xFF151525)
        SkyTimePeriod.MAGHRIB -> Color(0xFF251515)
        else -> Color(0xFF1a1a1a)
    }

    val silhouetteAlpha = when (period) {
        SkyTimePeriod.DAY, SkyTimePeriod.MORNING -> 0.35f
        SkyTimePeriod.ASR -> 0.45f
        else -> 1f
    }

    // ===== AL MASJID-E-NABAWI ACCURATE SILHOUETTE =====
    // Based on actual architecture: 10 minarets, Green Dome, 24 sliding domes, Ottoman style
    // Reference: https://en.wikipedia.org/wiki/Prophet's_Mosque

    // Position silhouette in bottom portion only - leave top 55% clear for sky/content
    val baseY = height * 1.0f  // At very bottom
    val silhouetteTopLimit = height * 0.55f  // Silhouette stays below this line
    val centerX = width * 0.5f

    // ===== MAIN BUILDING BASE - Extended horizontal Ottoman structure =====
    val buildingBaseHeight = height * 0.08f
    val buildingPath = Path().apply {
        moveTo(0f, baseY)
        lineTo(0f, baseY - buildingBaseHeight)
        lineTo(width, baseY - buildingBaseHeight)
        lineTo(width, baseY)
        close()
    }
    drawPath(
        path = buildingPath,
        color = silhouetteColor.copy(alpha = silhouetteAlpha),
        style = Fill
    )

    // ===== MINARETS - 6 visible from this angle (cylindrical top, octagonal middle, square base) =====
    val minaretWidth = width * 0.016f
    val minaretHeight = height * 0.38f

    // Draw 6 minarets across the width
    val minaretPositions = listOf(
        width * 0.02f to minaretHeight,
        width * 0.15f to minaretHeight * 0.85f,
        width * 0.30f to minaretHeight * 0.9f,
        width * 0.70f to minaretHeight * 0.9f,
        width * 0.85f to minaretHeight * 0.85f,
        width * 0.98f to minaretHeight
    )

    minaretPositions.forEach { (x, h) ->
        val minaretPath = Path().apply {
            drawMinaretAccurate(this, x, baseY - buildingBaseHeight, minaretWidth, h)
        }
        drawPath(
            path = minaretPath,
            color = silhouetteColor.copy(alpha = silhouetteAlpha),
            style = Fill
        )
    }

    // ===== SLIDING DOMES ROW - 24 domes on flat roof (showing ~8 visible) =====
    val smallDomeWidth = width * 0.04f
    val smallDomeHeight = height * 0.055f

    for (i in 0..7) {
        val domeX = width * 0.08f + i * width * 0.11f
        // Skip where Green Dome is
        if (domeX < centerX - width * 0.12f || domeX > centerX + width * 0.08f) {
            val domePath = Path().apply {
                moveTo(domeX - smallDomeWidth, baseY - buildingBaseHeight)
                quadraticBezierTo(
                    domeX, baseY - buildingBaseHeight - smallDomeHeight,
                    domeX + smallDomeWidth, baseY - buildingBaseHeight
                )
                close()
            }
            drawPath(
                path = domePath,
                color = silhouetteColor.copy(alpha = silhouetteAlpha),
                style = Fill
            )
        }
    }

    // ===== THE FAMOUS GREEN DOME - South-east corner, painted green since 1837 =====
    val greenDomeX = centerX - width * 0.02f
    val greenDomeWidth = width * 0.11f
    val greenDomeHeight = height * 0.16f
    val greenDomeBaseHeight = height * 0.04f

    // Draw green dome base (drum)
    val greenDomeBasePath = Path().apply {
        moveTo(greenDomeX - greenDomeWidth, baseY - buildingBaseHeight)
        lineTo(greenDomeX - greenDomeWidth, baseY - buildingBaseHeight - greenDomeBaseHeight)
        lineTo(greenDomeX + greenDomeWidth, baseY - buildingBaseHeight - greenDomeBaseHeight)
        lineTo(greenDomeX + greenDomeWidth, baseY - buildingBaseHeight)
        close()
    }
    drawPath(
        path = greenDomeBasePath,
        color = silhouetteColor.copy(alpha = silhouetteAlpha),
        style = Fill
    )

    // Draw the actual GREEN dome with color!
    val greenDomeColor = Color(0xFF228B22)  // Forest green - the actual green dome color
    val greenDomePath = Path().apply {
        val domeBottom = baseY - buildingBaseHeight - greenDomeBaseHeight
        moveTo(greenDomeX - greenDomeWidth, domeBottom)
        // Semi-circular dome shape (not onion)
        cubicTo(
            greenDomeX - greenDomeWidth, domeBottom - greenDomeHeight * 0.8f,
            greenDomeX - greenDomeWidth * 0.3f, domeBottom - greenDomeHeight,
            greenDomeX, domeBottom - greenDomeHeight
        )
        cubicTo(
            greenDomeX + greenDomeWidth * 0.3f, domeBottom - greenDomeHeight,
            greenDomeX + greenDomeWidth, domeBottom - greenDomeHeight * 0.8f,
            greenDomeX + greenDomeWidth, domeBottom
        )
        close()
    }
    // Draw green dome with actual green color
    val greenDomeAlpha = when (period) {
        SkyTimePeriod.NIGHT, SkyTimePeriod.ISHA -> 0.7f
        SkyTimePeriod.FAJR -> 0.6f
        SkyTimePeriod.MAGHRIB -> 0.75f
        else -> 0.85f
    }
    drawPath(
        path = greenDomePath,
        color = greenDomeColor.copy(alpha = greenDomeAlpha),
        style = Fill
    )

    // ===== SECONDARY DOME (smaller, next to green dome) =====
    val secondDomeX = centerX + width * 0.18f
    val secondDomeWidth = width * 0.07f
    val secondDomeHeight = height * 0.10f

    val secondDomePath = Path().apply {
        moveTo(secondDomeX - secondDomeWidth, baseY - buildingBaseHeight)
        quadraticBezierTo(
            secondDomeX, baseY - buildingBaseHeight - secondDomeHeight,
            secondDomeX + secondDomeWidth, baseY - buildingBaseHeight
        )
        close()
    }
    drawPath(
        path = secondDomePath,
        color = silhouetteColor.copy(alpha = silhouetteAlpha),
        style = Fill
    )

    // ===== FINIAL AND CRESCENT ON GREEN DOME =====
    val greenDomeTop = baseY - buildingBaseHeight - greenDomeBaseHeight - greenDomeHeight

    // Gold finial pole
    val poleColor = Color(0xFFFFD700)
    drawLine(
        color = poleColor.copy(alpha = greenDomeAlpha),
        start = Offset(greenDomeX, greenDomeTop),
        end = Offset(greenDomeX, greenDomeTop - height * 0.03f),
        strokeWidth = width * 0.004f
    )

    // Gold crescent on top
    drawCircle(
        color = poleColor.copy(alpha = greenDomeAlpha),
        radius = width * 0.012f,
        center = Offset(greenDomeX, greenDomeTop - height * 0.04f)
    )
    // Crescent cutout
    drawCircle(
        color = greenDomeColor.copy(alpha = greenDomeAlpha),
        radius = width * 0.009f,
        center = Offset(greenDomeX + width * 0.004f, greenDomeTop - height * 0.042f)
    )

    // ===== MINARET CRESCENTS =====
    val crescentColor = Color(0xFFFFD700)
    val crescentAlpha = when (period) {
        SkyTimePeriod.NIGHT, SkyTimePeriod.ISHA -> 0.8f
        SkyTimePeriod.FAJR -> 0.5f
        else -> 0.6f
    }

    minaretPositions.forEach { (x, h) ->
        val minaretTop = baseY - buildingBaseHeight - h - height * 0.02f
        drawCircle(
            color = crescentColor.copy(alpha = crescentAlpha),
            radius = width * 0.006f,
            center = Offset(x, minaretTop)
        )
    }

    // ===== ARCHED WINDOWS (lit at night) =====
    val windowColor = when (period) {
        SkyTimePeriod.NIGHT, SkyTimePeriod.ISHA -> Color(0xFFFFE082).copy(alpha = 0.5f)
        SkyTimePeriod.FAJR -> Color(0xFFFFE082).copy(alpha = 0.3f)
        SkyTimePeriod.MAGHRIB -> Color(0xFFFF8A65).copy(alpha = 0.4f)
        else -> Color.Transparent
    }

    if (windowColor != Color.Transparent) {
        for (i in 0..8) {
            val windowX = width * 0.08f + i * width * 0.1f
            drawArc(
                color = windowColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(windowX - width * 0.012f, baseY - height * 0.055f),
                size = androidx.compose.ui.geometry.Size(width * 0.024f, height * 0.025f)
            )
        }
    }
}

// Helper function to draw accurate minaret (cylindrical top, octagonal middle, square base)
private fun drawMinaretAccurate(path: Path, x: Float, baseY: Float, width: Float, height: Float) {
    path.apply {
        // Square base (bottom third)
        val baseWidth = width * 1.4f
        moveTo(x - baseWidth, baseY)
        lineTo(x - baseWidth, baseY - height * 0.35f)

        // Transition to octagonal middle
        lineTo(x - width * 1.1f, baseY - height * 0.35f)
        lineTo(x - width * 0.9f, baseY - height * 0.65f)

        // Balcony
        lineTo(x - width * 1.3f, baseY - height * 0.65f)
        lineTo(x - width * 1.3f, baseY - height * 0.68f)
        lineTo(x - width * 0.7f, baseY - height * 0.68f)

        // Cylindrical top section tapering to point
        lineTo(x - width * 0.5f, baseY - height * 0.92f)
        lineTo(x, baseY - height - height * 0.05f)  // Pointed top
        lineTo(x + width * 0.5f, baseY - height * 0.92f)

        // Right side (mirror)
        lineTo(x + width * 0.7f, baseY - height * 0.68f)
        lineTo(x + width * 1.3f, baseY - height * 0.68f)
        lineTo(x + width * 1.3f, baseY - height * 0.65f)
        lineTo(x + width * 0.9f, baseY - height * 0.65f)
        lineTo(x + width * 1.1f, baseY - height * 0.35f)
        lineTo(x + baseWidth, baseY - height * 0.35f)
        lineTo(x + baseWidth, baseY)
        close()
    }
}

// Helper function to draw a minaret
private fun drawMinaret(path: Path, x: Float, baseY: Float, width: Float, height: Float) {
    path.apply {
        // Minaret body with slight taper
        moveTo(x - width * 1.1f, baseY)
        lineTo(x - width * 0.9f, baseY - height * 0.65f)
        // Balcony
        lineTo(x - width * 1.3f, baseY - height * 0.65f)
        lineTo(x - width * 1.3f, baseY - height * 0.68f)
        lineTo(x - width * 0.7f, baseY - height * 0.68f)
        // Upper section
        lineTo(x - width * 0.6f, baseY - height * 0.92f)
        // Pointed top
        lineTo(x, baseY - height - height * 0.08f)
        lineTo(x + width * 0.6f, baseY - height * 0.92f)
        // Right side
        lineTo(x + width * 0.7f, baseY - height * 0.68f)
        lineTo(x + width * 1.3f, baseY - height * 0.68f)
        lineTo(x + width * 1.3f, baseY - height * 0.65f)
        lineTo(x + width * 0.9f, baseY - height * 0.65f)
        lineTo(x + width * 1.1f, baseY)
        close()
    }
}

/**
 * Composable effect that enables immersive full-screen mode by hiding the status bar.
 * The status bar is restored when leaving the screen (if restoreOnDispose is true).
 *
 * Usage: Call this at the top of your screen composable to enable immersive mode.
 *
 * @param restoreOnDispose If true, restores status bar when leaving composition. Set to false
 *                         when navigating between screens that should all be immersive.
 */
@Composable
fun ImmersiveFullScreenEffect(restoreOnDispose: Boolean = true) {
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
            // Only restore status bar if requested
            if (restoreOnDispose) {
                insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            }
        }
    }
}
