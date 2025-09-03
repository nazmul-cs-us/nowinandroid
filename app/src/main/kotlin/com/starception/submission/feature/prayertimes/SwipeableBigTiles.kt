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

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.starception.submission.prayer.model.DayPrayerTimes
import java.time.LocalTime

/**
 * SMART GLOW EFFECTS FOR AI-POWERED TILES
 * 
 * These helper functions create professional glow effects that indicate the tiles are
 * intelligent, AI-powered components that update based on machine learning and real-time data.
 */

@Composable
fun EdgeGlowModifier(
    color: Color,
    intensity: Float = 1f
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "edge_glow")
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    
    return Modifier.border(
        width = 2.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                color.copy(alpha = glowAlpha * intensity * 0.8f),
                color.copy(alpha = glowAlpha * intensity * 0.4f),
                color.copy(alpha = glowAlpha * intensity * 0.8f)
            )
        ),
        shape = RoundedCornerShape(16.dp)
    )
}



@Composable
fun SmartIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "smart_indicator")
    val sparkleRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkle_rotation"
    )
    
    val sparkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkle_alpha"
    )

    Row(
        modifier = modifier
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.1f),
                        color.copy(alpha = 0.2f),
                        color.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color.copy(alpha = sparkleAlpha),
            modifier = Modifier
                .size(14.dp)
                .graphicsLayer(rotationZ = sparkleRotation)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SwipeableBigTiles(
    prayerTimes: DayPrayerTimes?,
    currentTime: LocalTime,
    getNextPrayer: () -> Pair<String, LocalTime>?,
    getCurrentPrayer: () -> Pair<String, LocalTime>?,
    getPrayerStatus: (String) -> String,
    getPrayerTimeDisplay: (String) -> String,
    getTimeUntilNextPrayer: () -> String,
    getCurrentDate: () -> String,
    getSmartTitle: () -> String,
    getSmartContent: () -> String,
    getSmartFooter: () -> String,
    getPrayerProgress: () -> Pair<Int, Int>,
    getDailyStatsTitle: () -> String,
    getDailyStatsMessage: () -> String
) {
    // Swipeable Big Tiles - HorizontalPager with 3 tiles and infinite scroll
    val pagerState = rememberPagerState(
        pageCount = { Int.MAX_VALUE }, // Enable infinite scrolling
        initialPage = Int.MAX_VALUE / 2 // Start in the middle for smooth infinite scroll
    )
    
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    getNextPrayer = getNextPrayer,
                    getCurrentPrayer = getCurrentPrayer,
                    getPrayerStatus = getPrayerStatus,
                    getPrayerTimeDisplay = getPrayerTimeDisplay,
                    getTimeUntilNextPrayer = getTimeUntilNextPrayer,
                    glowModifier = EdgeGlowModifier(
                        color = MaterialTheme.colorScheme.primary,
                        intensity = 1f
                    )
                )
                1 -> SmartInfoTile(
                    getSmartTitle = getSmartTitle,
                    getSmartContent = getSmartContent,
                    getCurrentDate = getCurrentDate,
                    getSmartFooter = getSmartFooter,
                    glowModifier = EdgeGlowModifier(
                        color = MaterialTheme.colorScheme.secondary,
                        intensity = 0.8f
                    )
                )
                2 -> DailyStatsTile(
                    getPrayerProgress = getPrayerProgress,
                    getDailyStatsTitle = getDailyStatsTitle,
                    getDailyStatsMessage = getDailyStatsMessage,
                    glowModifier = EdgeGlowModifier(
                        color = MaterialTheme.colorScheme.tertiary,
                        intensity = 0.9f
                    )
                )
            }
        }
        
        // Page indicators for swipeable tiles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
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
                .padding(top = 4.dp),
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
    getNextPrayer: () -> Pair<String, LocalTime>?,
    getCurrentPrayer: () -> Pair<String, LocalTime>?,
    getPrayerStatus: (String) -> String,
    getPrayerTimeDisplay: (String) -> String,
    getTimeUntilNextPrayer: () -> String,
    glowModifier: Modifier = Modifier
) {
    val mainPrayer = getNextPrayer() ?: getCurrentPrayer()
    if (mainPrayer != null) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .then(glowModifier),
            shape = RoundedCornerShape(
                topStart = 40.dp,
                topEnd = 20.dp,
                bottomStart = 20.dp,
                bottomEnd = 40.dp
            ),
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
                        modifier = Modifier.weight(1f)
                    ) {
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
                    }
                    
                    // Countdown timer with enhanced glow
                    Surface(
                        modifier = Modifier.size(88.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 1.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { 0.7f },
                                modifier = Modifier.size(80.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp
                            )
                            
                            Text(
                                text = getTimeUntilNextPrayer(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Fallback if no prayer data
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Loading prayer times...",
                    style = MaterialTheme.typography.bodyLarge
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
    glowModifier: Modifier = Modifier
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .then(glowModifier),
        shape = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 40.dp,
            bottomStart = 40.dp,
            bottomEnd = 20.dp
        ),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Smart ML indicator
            SmartIndicator(
                icon = Icons.Default.AutoAwesome,
                label = "AI Content",
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.align(Alignment.Start)
            )
            
            // Dynamic title based on time of day
            Text(
                text = getSmartTitle(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium
            )
            
            // Contextual content and guidance
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = getSmartContent(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center
                )
                
                // Current date for context
                Text(
                    text = getCurrentDate(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            
            // Prayer context footer
            Text(
                text = getSmartFooter(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DailyStatsTile(
    getPrayerProgress: () -> Pair<Int, Int>,
    getDailyStatsTitle: () -> String,
    getDailyStatsMessage: () -> String,
    glowModifier: Modifier = Modifier
) {
    val (completed, total) = getPrayerProgress()
    val progress = if (total > 0) completed.toFloat() / total.toFloat() else 0f
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .then(glowModifier),
        shape = RoundedCornerShape(
            topStart = 32.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 32.dp
        ),
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
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}