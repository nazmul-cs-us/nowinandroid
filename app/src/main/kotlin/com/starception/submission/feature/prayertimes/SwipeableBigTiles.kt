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
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.feature.prayertimes.components.CompassProgressIndicator
import com.starception.submission.prayer.service.EnhancedLocationService
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.sqrt
import androidx.compose.ui.graphics.StrokeCap




@Composable
fun Modifier.geminiGradientEdge(
    borderWidth: Dp = 2.0.dp,
    topStart: Dp = 16.dp,
    topEnd: Dp = 16.dp,
    bottomStart: Dp = 16.dp,
    bottomEnd: Dp = 16.dp
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
        
        // Create the Gemini gradient colors
        val geminiColors = listOf(
            Color(0xFF4CAF50), // Green
            Color(0xFF2196F3), // Blue
            Color(0xFFE91E63), // Red
            Color(0xFFFFEB3B)  // Yellow
        )
        
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
    bottomEnd: Dp = 20.dp
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
        
        // Divine golden aura with enhanced layers (slightly more prominent)
        val auralayers = listOf(
            Triple(16.dp.toPx(), primaryGlow * 1.1f, Color(0xFFFFD700)),      // Inner gold
            Triple(26.dp.toPx(), primaryGlow * 0.9f, Color(0xFFFFE55C)),      // Mid gold  
            Triple(36.dp.toPx(), primaryGlow * 0.7f, Color(0xFFFFF8DC)),      // Light cream
            Triple(46.dp.toPx(), secondaryPulse * 0.5f, Color(0xFFFFFAF0)),   // Softest outer
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
                        Color(0xFFFFFFFF).copy(alpha = highlightAlpha * 0.8f),
                        Color(0xFFFFE55C).copy(alpha = highlightAlpha * 0.5f),
                        Color(0xFFFFD700).copy(alpha = highlightAlpha * 0.3f),
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
                    getSmartFooter = getSmartFooter
                )
                2 -> DailyStatsTile(
                    getPrayerProgress = getPrayerProgress,
                    getDailyStatsTitle = getDailyStatsTitle,
                    getDailyStatsMessage = getDailyStatsMessage
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prayer info
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp) // Add space between text and compass
                    ) {
                        // Get notification-synchronized content
                        val currentTime = remember { LocalTime.now() }
                        val syncContent = remember(prayerTimes, currentTime) {
                            SmartContentUtils.getNotificationSyncContent(prayerTimes, currentTime)
                        }
                        
                        if (syncContent != null) {
                            // Clean layout with readable fonts and shorter text
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Prayer phase title - now much shorter, can use larger font
                                Text(
                                    text = syncContent.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                // Main prayer time content - readable size
                                Text(
                                    text = syncContent.content,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                // Next prayer info - clean and readable
                                if (syncContent.nextPrayerInfo.isNotEmpty()) {
                                    Text(
                                        text = syncContent.nextPrayerInfo,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else if (mainPrayer != null) {
                            // Fallback to legacy display if sync fails
                            Text(
                                text = mainPrayer.first,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = getPrayerStatus(mainPrayer.first),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = getPrayerTimeDisplay(mainPrayer.first),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (prayerTimes != null) {
                            // Show Fajr as fallback when no current/next prayer is found
                            Text(
                                text = "Fajr",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Tomorrow",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = getPrayerTimeDisplay("Fajr"),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Countdown timer with integrated Qibla compass
                    Box(
                        modifier = Modifier
                            .size(120.dp) // Constrain the compass size to fit within tile padding
                            .clickable { onCompassClick() }
                    ) {
                        CompassProgressIndicator(
                            progress = 0.7f,
                            modifier = Modifier.fillMaxSize(),
                            size = 120.dp,
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
    getSmartFooter: () -> String
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Smart indicator
                SmartIndicator(
                    icon = Icons.Default.Schedule,
                    label = "Prayer Status",
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.align(Alignment.Start)
                )
                
                // Main content section - takes more space
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Dynamic title based on time of day
                    Text(
                        text = getSmartTitle(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Main prayer time content
                    Text(
                        text = getSmartContent(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Bottom section - date context
                Text(
                    text = getCurrentDate(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DailyStatsTile(
    getPrayerProgress: () -> Pair<Int, Int>,
    getDailyStatsTitle: () -> String,
    getDailyStatsMessage: () -> String
) {
    val (completed, total) = getPrayerProgress()
    val progress = if (total > 0) completed.toFloat() / total.toFloat() else 0f
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
            // Smart analytics indicator
            SmartIndicator(
                icon = Icons.Default.AutoAwesome,
                label = "Smart Analytics",
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.align(Alignment.Start)
            )
            
            // Dynamic title based on progress
            Text(
                text = getDailyStatsTitle(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.Medium
            )
            
            // Progress visualization and stats
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Prayer completion progress
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$completed/$total",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "prayers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                
                // Progress bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)
                )
            }
            
            // Contextual message
            Text(
                text = getDailyStatsMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}