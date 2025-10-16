/**
 * SWIPEABLE BIG TILES COMPONENT
 *
 * This file contains the main swipeable tiles component for the Prayer Times screen.
 * It provides an interactive horizontal pager with three distinct tiles showing different
 * prayer-related information with Material 3 design and infinite scrolling.
 *
 * WHAT IT DOES:
 * - Creates a horizontal swipeable pager with 3 tiles (infinite scroll enabled)
 * - Shows Next Prayer, Smart Info, and Daily Stats tiles
 * - Displays page indicators and swipe hints for better UX
 * - Uses asymmetrical Material 3 shapes for visual appeal
 * - Provides real-time prayer status and progress tracking
 *
 * WHERE IT'S USED:
 * - PrayerTimesScreen.kt: Main prayer times screen (line ~481-502)
 * - Replaces ~308 lines of inline swipeable tiles code
 * - Called through SwipeableBigTiles() composable function
 *
 * COMPONENTS INCLUDED:
 * - SwipeableBigTiles(): Main composable function (exported)
 * - NextPrayerTile(): Shows current/next prayer with countdown timer
 * - SmartInfoTile(): Context-aware content based on time of day
 * - DailyStatsTile(): Prayer completion progress and statistics
 *
 * FEATURES:
 * - HorizontalPager with infinite scrolling (Int.MAX_VALUE pages)
 * - Material 3 asymmetrical shapes and elevated surfaces
 * - Real-time countdown timers with circular progress indicators
 * - Dynamic content that changes based on current time and prayer status
 * - Professional page indicators and swipe hints
 * - Responsive layout with proper spacing (12dp between elements)
 *
 * DEPENDENCIES:
 * - PrayerTimeHelpers.kt: For prayer time calculations and formatting
 * - SmartContentUtils.kt: For smart content generation and progress tracking
 * - DayPrayerTimes model: Prayer times data structure
 *
 * DESIGN PATTERNS:
 * - Component extraction: Moved from inline code to reusable component
 * - Function parameters: Accepts lambda functions for data access
 * - Material 3 design: Uses elevated cards with custom corner radius
 * - Infinite scrolling: Modulo arithmetic for seamless tile cycling
 */
package com.starception.submission.feature.prayertimes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.starception.submission.R
import androidx.compose.animation.core.*
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.feature.prayertimes.components.CompassProgressIndicator
import com.starception.submission.prayer.service.EnhancedLocationService
import java.time.LocalTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sqrt
import androidx.compose.ui.graphics.StrokeCap




/**
 * Get Arabic calendar information for the current date
 */
private fun getArabicCalendarInfo(): String {
    val today = LocalDate.now()
    
    // Convert Gregorian to Hijri using Umm al-Qura algorithm
    val hijriDate = convertToHijri(today)
    
    // Get Islamic month names
    val islamicMonths = arrayOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الثاني", "جمادى الأولى", "جمادى الثانية",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )
    
    val day = hijriDate.first
    val month = islamicMonths[hijriDate.second - 1]
    val year = hijriDate.third
    
    return "$day $month, $year AH"
}

/**
 * Convert Gregorian date to Hijri date using Umm al-Qura algorithm
 * Returns Triple(day, month, year) in Hijri calendar
 */
private fun convertToHijri(gregorianDate: LocalDate): Triple<Int, Int, Int> {
    val year = gregorianDate.year
    val month = gregorianDate.monthValue
    val day = gregorianDate.dayOfMonth
    
    // Umm al-Qura algorithm constants
    val epoch = 227015 // Hijri epoch in days since 1 Jan 1 CE
    val cycleLength = 10631 // Length of 30-year cycle in days
    
    // Convert Gregorian date to days since epoch
    val gregorianDays = gregorianDate.toEpochDay() + epoch
    
    // Calculate 30-year cycles
    val cycles = gregorianDays / cycleLength
    var remainingDays = gregorianDays % cycleLength
    
    // Calculate year within cycle
    var hijriYear = cycles * 30 + 1
    
    // Month lengths in 30-year cycle (1=30 days, 0=29 days)
    val monthLengths = intArrayOf(
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 1
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 2
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 3
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 4
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 5
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 6
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 7
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 8
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 9
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 10
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 11
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 12
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 13
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 14
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 15
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 16
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 17
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 18
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 19
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 20
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 21
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 22
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 23
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 24
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 25
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 26
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 27
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, // Year 28
        1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, // Year 29
        1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0  // Year 30
    )
    
    // Find the year and month
    var hijriMonth = 1
    var hijriDay = 1
    
    for (yearIndex in 0 until 30) {
        val yearDays = monthLengths.sliceArray(yearIndex * 12 until (yearIndex + 1) * 12).sum() + 354
        if (remainingDays < yearDays) {
            hijriYear += yearIndex
            break
        }
        remainingDays -= yearDays
    }
    
    // Find the month within the year
    for (monthIndex in 0 until 12) {
        val monthDays = monthLengths[((hijriYear - 1) % 30 * 12 + monthIndex).toInt()] + 29
        if (remainingDays < monthDays) {
            hijriMonth = monthIndex + 1
            hijriDay = (remainingDays + 1).toInt()
            break
        }
        remainingDays -= monthDays
    }
    
    return Triple(hijriDay, hijriMonth, hijriYear.toInt())
}

@Composable
fun Modifier.geminiGradientEdge(
    borderWidth: Dp = 2.0.dp,
    topStart: Dp = 16.dp,
    topEnd: Dp = 16.dp,
    bottomStart: Dp = 16.dp,
    bottomEnd: Dp = 16.dp,
    gradientColors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer
    )
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "geminiGradient")

    // Create a moving shine animation around the perimeter
    val shinePosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shinePosition"
    )

    return this.drawWithContent {
        // Draw the content first
        drawContent()

        // Calculate corner radii in pixels
        val topStartPx = topStart.toPx()
        val topEndPx = topEnd.toPx()
        val bottomStartPx = bottomStart.toPx()
        val bottomEndPx = bottomEnd.toPx()
        val borderWidthPx = borderWidth.toPx()
        val avgCornerRadius = (topStartPx + topEndPx + bottomStartPx + bottomEndPx) / 4f

        // Calculate perimeter and shine position
        val perimeter = 2 * (size.width + size.height)
        val shineProgress = shinePosition * perimeter
        val shineSize = 80f // Size of the traveling shine

        // Use the gradient colors passed as parameters
        val geminiColors = gradientColors

        // Draw a glowing edge that travels around the rounded perimeter
        when {
            shineProgress <= size.width -> {
                // Top edge (left to right) - follow rounded corners
                val x = shineProgress
                val y = if (x < avgCornerRadius) {
                    // Left rounded corner - calculate y position on circle
                    avgCornerRadius - sqrt(avgCornerRadius * avgCornerRadius - (avgCornerRadius - x) * (avgCornerRadius - x))
                } else if (x > size.width - avgCornerRadius) {
                    // Right rounded corner - calculate y position on circle
                    avgCornerRadius - sqrt(avgCornerRadius * avgCornerRadius - (x - (size.width - avgCornerRadius)) * (x - (size.width - avgCornerRadius)))
                } else {
                    // Straight top edge
                    0f
                }

                val currentColor = when {
                    x < size.width * 0.25f -> geminiColors[0] // Green
                    x < size.width * 0.5f -> geminiColors[1]  // Blue
                    x < size.width * 0.75f -> geminiColors[2] // Red
                    else -> geminiColors[3] // Yellow
                }

                // Draw glowing circle at current position
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 1.0f),
                            currentColor.copy(alpha = 0.9f),
                            currentColor.copy(alpha = 0.6f),
                            Color.Transparent
                        ),
                        radius = 60f
                    ),
                    radius = 60f,
                    center = Offset(x, y)
                )
            }
            shineProgress <= size.width + size.height -> {
                // Right edge (top to bottom) - follow rounded corners
                val y = shineProgress - size.width
                val x = if (y < avgCornerRadius) {
                    // Top rounded corner - calculate x position on circle
                    size.width - avgCornerRadius + sqrt(avgCornerRadius * avgCornerRadius - (avgCornerRadius - y) * (avgCornerRadius - y))
                } else if (y > size.height - avgCornerRadius) {
                    // Bottom rounded corner - calculate x position on circle
                    size.width - avgCornerRadius + sqrt(avgCornerRadius * avgCornerRadius - (y - (size.height - avgCornerRadius)) * (y - (size.height - avgCornerRadius)))
                } else {
                    // Straight right edge
                    size.width
                }

                val currentColor = when {
                    y < size.height * 0.25f -> geminiColors[1] // Blue
                    y < size.height * 0.5f -> geminiColors[2]  // Red
                    y < size.height * 0.75f -> geminiColors[3] // Yellow
                    else -> geminiColors[0] // Green
                }

                // Draw glowing circle at current position
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 1.0f),
                            currentColor.copy(alpha = 0.9f),
                            currentColor.copy(alpha = 0.6f),
                            Color.Transparent
                        ),
                        radius = 60f
                    ),
                    radius = 60f,
                    center = Offset(x, y)
                )
            }
            shineProgress <= 2 * size.width + size.height -> {
                // Bottom edge (right to left) - follow rounded corners
                val x = size.width - (shineProgress - size.width - size.height)
                val y = if (x < avgCornerRadius) {
                    // Left rounded corner - calculate y position on circle
                    size.height - avgCornerRadius + sqrt(avgCornerRadius * avgCornerRadius - (avgCornerRadius - x) * (avgCornerRadius - x))
                } else if (x > size.width - avgCornerRadius) {
                    // Right rounded corner - calculate y position on circle
                    size.height - avgCornerRadius + sqrt(avgCornerRadius * avgCornerRadius - (x - (size.width - avgCornerRadius)) * (x - (size.width - avgCornerRadius)))
                } else {
                    // Straight bottom edge
                    size.height
                }

                val currentColor = when {
                    x > size.width * 0.75f -> geminiColors[2] // Red
                    x > size.width * 0.5f -> geminiColors[3]  // Yellow
                    x > size.width * 0.25f -> geminiColors[0] // Green
                    else -> geminiColors[1] // Blue
                }

                // Draw glowing circle at current position
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 1.0f),
                            currentColor.copy(alpha = 0.9f),
                            currentColor.copy(alpha = 0.6f),
                            Color.Transparent
                        ),
                        radius = 60f
                    ),
                    radius = 60f,
                    center = Offset(x, y)
                )
            }
            else -> {
                // Left edge (bottom to top) - follow rounded corners
                val y = size.height - (shineProgress - 2 * size.width - size.height)
                val x = if (y < avgCornerRadius) {
                    // Top rounded corner - calculate x position on circle
                    avgCornerRadius - sqrt(avgCornerRadius * avgCornerRadius - (avgCornerRadius - y) * (avgCornerRadius - y))
                } else if (y > size.height - avgCornerRadius) {
                    // Bottom rounded corner - calculate x position on circle
                    avgCornerRadius - sqrt(avgCornerRadius * avgCornerRadius - (y - (size.height - avgCornerRadius)) * (y - (size.height - avgCornerRadius)))
                } else {
                    // Straight left edge
                    0f
                }

                val currentColor = when {
                    y > size.height * 0.75f -> geminiColors[3] // Yellow
                    y > size.height * 0.5f -> geminiColors[0]  // Green
                    y > size.height * 0.25f -> geminiColors[1] // Blue
                    else -> geminiColors[2] // Red
                }

                // Draw glowing circle at current position
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 1.0f),
                            currentColor.copy(alpha = 0.9f),
                            currentColor.copy(alpha = 0.6f),
                            Color.Transparent
                        ),
                        radius = 60f
                    ),
                    radius = 60f,
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
fun Modifier.sunshineAura(
    topStart: Dp = 20.dp,
    topEnd: Dp = 20.dp,
    bottomStart: Dp = 20.dp,
    bottomEnd: Dp = 20.dp,
    auraColors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surfaceVariant
    )
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "sunshineAura")

    val primaryGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "primaryGlow"
    )

    val secondaryPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "secondaryPulse"
    )

    val divineShimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * kotlin.math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "divineShimmer"
    )

    return this.drawBehind {
        // Convert custom corner radii to pixels
        val topStartPx = topStart.toPx()
        val topEndPx = topEnd.toPx()
        val bottomStartPx = bottomStart.toPx()
        val bottomEndPx = bottomEnd.toPx()

        // Divine aura with theme-aware colors
        val auralayers = listOf(
            Triple(16.dp.toPx(), primaryGlow * 1.1f, auraColors.getOrElse(0) { auraColors.first() }),      // Inner
            Triple(26.dp.toPx(), primaryGlow * 0.9f, auraColors.getOrElse(1) { auraColors.first() }),      // Mid
            Triple(36.dp.toPx(), primaryGlow * 0.7f, auraColors.getOrElse(2) { auraColors.first() }),      // Light
            Triple(46.dp.toPx(), secondaryPulse * 0.5f, auraColors.getOrElse(3) { auraColors.first() }),   // Outer
        )

        // Draw each aura layer
        auralayers.forEachIndexed { index, (glowSize, intensity, baseColor) ->
            val shimmerBoost = kotlin.math.sin(divineShimmer + index * 1.5f) * 0.15f + 0.85f
            val finalAlpha = intensity * shimmerBoost

            if (finalAlpha > 0.05f) {
                // Create more sophisticated gradient
                val gradientColors = listOf(
                    baseColor.copy(alpha = finalAlpha * 0.9f),
                    baseColor.copy(alpha = finalAlpha * 0.6f),
                    baseColor.copy(alpha = finalAlpha * 0.3f),
                    baseColor.copy(alpha = finalAlpha * 0.1f),
                    Color.Transparent
                )

                // Create custom rounded rect path with asymmetric corners
                val glowRect = androidx.compose.ui.geometry.Rect(
                    offset = Offset(-glowSize / 2, -glowSize / 2),
                    size = androidx.compose.ui.geometry.Size(
                        width = size.width + glowSize,
                        height = size.height + glowSize
                    )
                )

                val glowPath = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(
                        roundRect = androidx.compose.ui.geometry.RoundRect(
                            rect = glowRect,
                            topLeft = androidx.compose.ui.geometry.CornerRadius(
                                x = topStartPx + glowSize / 8,
                                y = topStartPx + glowSize / 8
                            ),
                            topRight = androidx.compose.ui.geometry.CornerRadius(
                                x = topEndPx + glowSize / 8,
                                y = topEndPx + glowSize / 8
                            ),
                            bottomLeft = androidx.compose.ui.geometry.CornerRadius(
                                x = bottomStartPx + glowSize / 8,
                                y = bottomStartPx + glowSize / 8
                            ),
                            bottomRight = androidx.compose.ui.geometry.CornerRadius(
                                x = bottomEndPx + glowSize / 8,
                                y = bottomEndPx + glowSize / 8
                            )
                        )
                    )
                }

                drawPath(
                    path = glowPath,
                    brush = Brush.radialGradient(
                        colors = gradientColors,
                        center = Offset(size.width / 2, size.height / 2),
                        radius = glowSize + (kotlin.math.sin(divineShimmer * 0.7f + index) * 8.dp.toPx())
                    )
                )
            }
        }

        // Divine highlights with celestial sparkles (more visible)
        val sparklePhase = kotlin.math.sin(divineShimmer * 1.3f) * 0.5f + 0.5f
        val highlightAlpha = primaryGlow * sparklePhase * 0.45f

        if (highlightAlpha > 0.05f) {
            val highlightSize = 6.dp.toPx()
            val highlightRect = androidx.compose.ui.geometry.Rect(
                offset = Offset(-highlightSize, -highlightSize),
                size = androidx.compose.ui.geometry.Size(
                    width = size.width + highlightSize * 2,
                    height = size.height + highlightSize * 2
                )
            )

            val highlightPath = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(
                    roundRect = androidx.compose.ui.geometry.RoundRect(
                        rect = highlightRect,
                        topLeft = androidx.compose.ui.geometry.CornerRadius(
                            x = topStartPx + highlightSize,
                            y = topStartPx + highlightSize
                        ),
                        topRight = androidx.compose.ui.geometry.CornerRadius(
                            x = topEndPx + highlightSize,
                            y = topEndPx + highlightSize
                        ),
                        bottomLeft = androidx.compose.ui.geometry.CornerRadius(
                            x = bottomStartPx + highlightSize,
                            y = bottomStartPx + highlightSize
                        ),
                        bottomRight = androidx.compose.ui.geometry.CornerRadius(
                            x = bottomEndPx + highlightSize,
                            y = bottomEndPx + highlightSize
                        )
                    )
                )
            }

            drawPath(
                path = highlightPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        auraColors.getOrElse(0) { auraColors.first() }.copy(alpha = highlightAlpha * 0.8f),
                        auraColors.getOrElse(1) { auraColors.first() }.copy(alpha = highlightAlpha * 0.5f),
                        auraColors.getOrElse(2) { auraColors.first() }.copy(alpha = highlightAlpha * 0.3f),
                        Color.Transparent
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
            )
        }
    }
}

@Composable
fun SparklingStars(
    sparkleAnimation: Float,
    color: Color,
    containerSize: Dp
) {
    // Create 4 star shapes around the icon
    val sparklePositions = listOf(
        Pair(-0.35f, -0.35f), // Top-left
        Pair(0.35f, -0.35f),  // Top-right
        Pair(-0.35f, 0.35f),  // Bottom-left
        Pair(0.35f, 0.35f)    // Bottom-right
    )

    sparklePositions.forEachIndexed { index, (offsetX, offsetY) ->
        // Stagger the sparkle timing for each star
        val staggeredAlpha = ((sparkleAnimation + index * 0.25f) % 1f).coerceIn(0f, 1f)
        val sparkleAlpha = if (staggeredAlpha < 0.5f) staggeredAlpha * 2f else (1f - staggeredAlpha) * 2f
        val sparkleScale = 0.3f + sparkleAlpha * 0.7f

        Canvas(
            modifier = Modifier
                .offset(
                    x = (containerSize.value * offsetX).dp,
                    y = (containerSize.value * offsetY).dp
                )
                .size(6.dp)
                .graphicsLayer {
                    scaleX = sparkleScale
                    scaleY = sparkleScale
                    alpha = sparkleAlpha * 0.9f
                    rotationZ = sparkleAnimation * 360f + index * 45f
                }
        ) {
            // Draw a 4-pointed star shape
            val centerX = size.width / 2
            val centerY = size.height / 2
            val outerRadius = size.width / 2
            val innerRadius = outerRadius * 0.4f

            val starPath = androidx.compose.ui.graphics.Path().apply {
                // Create 4-pointed star
                moveTo(centerX, centerY - outerRadius) // Top point
                lineTo(centerX + innerRadius * 0.3f, centerY - innerRadius * 0.3f)
                lineTo(centerX + outerRadius, centerY) // Right point
                lineTo(centerX + innerRadius * 0.3f, centerY + innerRadius * 0.3f)
                lineTo(centerX, centerY + outerRadius) // Bottom point
                lineTo(centerX - innerRadius * 0.3f, centerY + innerRadius * 0.3f)
                lineTo(centerX - outerRadius, centerY) // Left point
                lineTo(centerX - innerRadius * 0.3f, centerY - innerRadius * 0.3f)
                close()
            }

            drawPath(
                path = starPath,
                color = color.copy(alpha = sparkleAlpha * 0.8f)
            )
        }
    }
}

@Composable
fun SmartIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aiWorking")

    // Different animations based on the type of AI work
    val (iconAnimation, backgroundAnimation) = when (label) {
        "Smart Prediction" -> {
            // Gentle pulsing to show prediction processing
            val pulse by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "predictionPulse"
            )
            val bgPulse by infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "predictionBgPulse"
            )
            Pair(pulse, bgPulse)
        }
        "AI Content" -> {
            // Sparkling effect - slower, more elegant fade
            val sparkle1 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "contentSparkle1"
            )
            val bgPulse by infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 0.18f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "contentBgPulse"
            )
            Pair(sparkle1, bgPulse)
        }
        else -> { // Smart Analytics
            // Sparkling effect - slower, more elegant twinkling stars
            val sparkle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "analyticsSparkle"
            )
            val bgPulse by infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 0.16f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "analysisBgPulse"
            )
            Pair(sparkle, bgPulse)
        }
    }

    Row(
        modifier = modifier
            .background(
                color = color.copy(alpha = backgroundAnimation),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Animated icon showing AI is working
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    color = color.copy(alpha = 0.15f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Main icon
            Icon(
                imageVector = icon,
                contentDescription = "$label - AI Working",
                tint = color,
                modifier = Modifier
                    .size(12.dp)
                    .graphicsLayer {
                        when (label) {
                            "Smart Prediction" -> {
                                scaleX = iconAnimation
                                scaleY = iconAnimation
                                alpha = iconAnimation
                            }
                            "AI Content", "Smart Analytics" -> {
                                // Keep main icon stable for sparkling effects
                                alpha = 1f
                            }
                        }
                    }
            )

            // Sparkling effects for AI Content and Smart Analytics
            if (label == "AI Content" || label == "Smart Analytics") {
                SparklingStars(
                    sparkleAnimation = iconAnimation,
                    color = color,
                    containerSize = 20.dp
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = color
        )
    }
}

@Composable
fun SwipeableBigTiles(
    prayerTimes: DayPrayerTimes?,
    currentTime: LocalTime,
    locationService: EnhancedLocationService,
    getNextPrayer: () -> Pair<String, LocalTime>?,
    getCurrentPrayer: () -> Pair<String, LocalTime>?,
    getPrayerStatus: (String) -> String,
    getPrayerTimeDisplay: (String) -> String,
    getTimeUntilNextPrayer: () -> String,
    getCurrentDate: () -> String,
    getSmartTitle: () -> String,
    getSmartContent: () -> String,
    getSmartFooter: () -> String,
    getTimeSinceCurrentPrayer: () -> String,
    getPrayerProgress: () -> Pair<Int, Int>,
    getDailyStatsTitle: () -> String,
    getDailyStatsMessage: () -> String,
    getPrayed: () -> Int = { 0 },
    getCurrentActivity: () -> String,
    onCompassClick: () -> Unit
) {
    // Swipeable Big Tiles - HorizontalPager with 3 tiles and infinite scroll
    val pagerState = rememberPagerState(
        pageCount = { Int.MAX_VALUE }, // Enable infinite scrolling
        initialPage = Int.MAX_VALUE / 2 // Start in the middle for smooth infinite scroll
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            pageSpacing = 16.dp,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) { page ->
            val actualPage = page % 3 // Map infinite pages to our 3 actual tiles
            when (actualPage) {
                0 -> NextPrayerTile(
                    prayerTimes = prayerTimes,
                    currentTime = currentTime,
                    locationService = locationService,
                    getNextPrayer = getNextPrayer,
                    getCurrentPrayer = getCurrentPrayer,
                    getPrayerStatus = getPrayerStatus,
                    getPrayerTimeDisplay = getPrayerTimeDisplay,
                    getTimeUntilNextPrayer = getTimeUntilNextPrayer,
                    getTimeSinceCurrentPrayer = getTimeSinceCurrentPrayer,
                    onCompassClick = onCompassClick
                )
                1 -> SmartInfoTile(
                    getSmartTitle = getSmartTitle,
                    getSmartContent = getSmartContent,
                    getCurrentDate = getCurrentDate,
                    getSmartFooter = getSmartFooter,
                    getCurrentActivity = getCurrentActivity
                )
                2 -> DailyStatsTile(
                    getPrayerProgress = getPrayerProgress,
                    getDailyStatsTitle = getDailyStatsTitle,
                    getDailyStatsMessage = getDailyStatsMessage,
                    getPrayed = getPrayed
                )
            }
        }

        // Page indicators for swipeable tiles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 1.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                val isSelected = (pagerState.currentPage % 3) == index
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                )
                if (index < 2) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }

        // Professional swipe hint
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Swipe left",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Swipe for more insights",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Swipe right",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun NextPrayerTile(
    prayerTimes: DayPrayerTimes?,
    currentTime: LocalTime,
    locationService: EnhancedLocationService,
    getNextPrayer: () -> Pair<String, LocalTime>?,
    getCurrentPrayer: () -> Pair<String, LocalTime>?,
    getPrayerStatus: (String) -> String,
    getPrayerTimeDisplay: (String) -> String,
    getTimeUntilNextPrayer: () -> String,
    getTimeSinceCurrentPrayer: () -> String,
    onCompassClick: () -> Unit
) {
    val mainPrayer = getNextPrayer() ?: getCurrentPrayer()
    // Show prayer tile if we have prayer data, even if mainPrayer logic fails
    if (mainPrayer != null || prayerTimes != null) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .geminiGradientEdge(
                    borderWidth = 1.5.dp,
                    topStart = 32.dp,
                    topEnd = 32.dp,
                    bottomStart = 32.dp,
                    bottomEnd = 32.dp
                ),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Smart AI indicator
                SmartIndicator(
                    icon = Icons.Default.Psychology,
                    label = "Smart Prediction",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prayer info
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp) // Minimal padding for maximum text space
                    ) {
                        // Get notification-synchronized content using the SAME currentTime that updates every minute
                        val syncContent = remember(prayerTimes, currentTime) {
                            SmartContentUtils.getNotificationSyncContent(prayerTimes, currentTime)
                        }

                        if (syncContent != null) {
                            // Clean layout with readable fonts and optimized spacing
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Prayer phase title - compact and readable
                                Text(
                                    text = syncContent.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Main prayer time content - sized to fit longest text "59 minutes since Maghrib"
                                Text(
                                    text = syncContent.content,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = 22.sp,
                                        letterSpacing = (-0.4).sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 24.sp
                                )

                                // Next prayer info - enhanced with prominent chip styling
                                if (syncContent.nextPrayerInfo.isNotEmpty()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text(
                                            text = syncContent.nextPrayerInfo,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontSize = 15.sp,
                                                letterSpacing = (-0.2).sp
                                            ),
                                            color = MaterialTheme.colorScheme.tertiary,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        } else if (mainPrayer != null) {
                            // Fallback: Show upcoming prayer in notification style
                            val prayerName = mainPrayer.first
                            val prayerStatus = getPrayerStatus(prayerName)
                            val prayerTime = getPrayerTimeDisplay(prayerName)

                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Title - notification style
                                Text(
                                    text = "Next Prayer: $prayerName",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Main content - prayer time with status
                                Text(
                                    text = "$prayerStatus • $prayerTime",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = 22.sp,
                                        letterSpacing = (-0.4).sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 24.sp
                                )
                            }
                        } else if (prayerTimes != null) {
                            // Show tomorrow's Fajr in notification style
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Title - notification style
                                Text(
                                    text = "Next Prayer: Fajr",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Main content - tomorrow's Fajr time
                                Text(
                                    text = "Tomorrow • ${getPrayerTimeDisplay("Fajr")}",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = 22.sp,
                                        letterSpacing = (-0.4).sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 24.sp
                                )
                            }
                        }
                    }

                    // Material 3 expressive compass with enhanced interaction feedback
                    var isPressed by remember { mutableStateOf(false) }

                    val compassScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.95f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessHigh
                        ),
                        label = "compassPressScale"
                    )

                    val compassElevation by animateDpAsState(
                        targetValue = if (isPressed) 2.dp else 6.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "compassElevation"
                    )

                    Box(
                        modifier = Modifier
                            .size(100.dp) // Reduced size to give more space for text
                            .graphicsLayer {
                                scaleX = compassScale
                                scaleY = compassScale
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isPressed = true
                                        tryAwaitRelease()
                                        isPressed = false
                                    },
                                    onTap = {
                                        onCompassClick()
                                    }
                                )
                            }
                    ) {
                        CompassProgressIndicator(
                            progress = 0.7f,
                            modifier = Modifier.fillMaxSize(),
                            size = 100.dp,
                            locationService = locationService
                        )
                    }
                }
            }
        }
    } else {
        // Fallback if no prayer data - Beautiful loading state
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Beautiful loading indicator
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Calculating Prayer Times",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Getting your location...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SmartInfoTile(
    getSmartTitle: () -> String,
    getSmartContent: () -> String,
    getCurrentDate: () -> String,
    getSmartFooter: () -> String,
    getCurrentActivity: () -> String
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .geminiGradientEdge(
                borderWidth = 1.5.dp,
                topStart = 32.dp,
                topEnd = 32.dp,
                bottomStart = 32.dp,
                bottomEnd = 32.dp
            ),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Smart indicator
            SmartIndicator(
                icon = Icons.Default.Psychology,
                label = "Smart Tracking",
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.align(Alignment.Start)
            )

            // Main content - Side-by-side layout with proper alignment
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp, bottom = 8.dp), // Balanced vertical padding
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Labels row - ensures they're on the same horizontal line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Prayer Time",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content row with separator - ensures proper vertical alignment
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top // Changed from CenterVertically to Top to allow multi-line
                ) {
                    // Left: Prayer Time content
                    Text(
                        text = getSmartContent(),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 22.sp,
                            letterSpacing = (-0.4).sp,
                            lineHeight = 26.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )

                    // Vertical divider with gradient effect
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(80.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f),
                                        Color.Transparent
                                    )
                                ),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )

                    // Right: Activity content
                    val currentActivity = getCurrentActivity()
                    Text(
                        text = currentActivity,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 22.sp,
                            letterSpacing = (-0.3).sp,
                            lineHeight = 26.sp
                        ),
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                }
            }

            // Footer with padding
            Text(
                text = "🔊 Beeps when activity changes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun DailyStatsTile(
    getPrayerProgress: () -> Pair<Int, Int>,
    getDailyStatsTitle: () -> String,
    getDailyStatsMessage: () -> String,
    getPrayed: () -> Int = { 0 }
) {
    val (completed, total) = getPrayerProgress()
    val progress = if (total > 0) completed.toFloat() / total.toFloat() else 0f
    val remaining = total - completed
    val prayed = getPrayed()
    val progressPercentage = (progress * 100).toInt()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .geminiGradientEdge(
                borderWidth = 1.5.dp,
                topStart = 32.dp,
                topEnd = 32.dp,
                bottomStart = 32.dp,
                bottomEnd = 32.dp
            ),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            SmartIndicator(
                icon = Icons.Default.AutoAwesome,
                label = "Smart Analytics",
                color = MaterialTheme.colorScheme.tertiary
            )

            // Main Content
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Circular progress indicator
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.size(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f),
                            strokeWidth = 11.dp,
                            trackColor = Color.Transparent,
                            strokeCap = StrokeCap.Round
                        )
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.tertiary,
                            strokeWidth = 11.dp,
                            trackColor = Color.Transparent,
                            strokeCap = StrokeCap.Round
                        )
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "$progressPercentage%",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 22.sp,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$completed/$total",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Right Column: Stats breakdown
                Column(
                    modifier = Modifier
                        .weight(2f)
                        .padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Prayed stat
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = Color(0xFF4CAF50), // Green for prayed
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = "Prayed •",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$prayed prayers",
                            style = MaterialTheme.typography.titleSmall.copy(
                                letterSpacing = (-0.1).sp
                            ),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Completed stat
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.tertiary,
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = "Completed •",
                            style = MaterialTheme.typography.titleSmall,
                            // *** THIS IS THE FIX ***
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$completed prayers",
                            style = MaterialTheme.typography.titleSmall.copy(
                                letterSpacing = (-0.1).sp
                            ),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Remaining stat
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = "Remaining •",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$remaining prayers",
                            style = MaterialTheme.typography.titleSmall.copy(
                                letterSpacing = (-0.1).sp
                            ),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
//                    .background(
//                        color = MaterialTheme.colorScheme.secondaryContainer,
//                        shape = RoundedCornerShape(8.dp)
//                    )
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.calendar_month_24),
                    contentDescription = "Calendar",
                    modifier = Modifier.padding(top = 16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "21 Rabi' al-thani, 1447 AH",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}