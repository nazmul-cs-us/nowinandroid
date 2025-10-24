package com.starception.submission.feature.prayertimes.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.prayer.model.DayPrayerTimes
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.*

/**
 * Enhanced luxury hour markers with Islamic geometric patterns
 */
@Composable
fun PremiumHourMarkers(watchSize: Dp) {
    val center = watchSize.value / 2f
    val radius = watchSize.value / 2f - 20.dp.value
    
    // Cardinal and intercardinal directions with Islamic compass styling
    val directions = listOf(
        "N" to 0f,
        "NE" to 45f,
        "E" to 90f,
        "SE" to 135f,
        "S" to 180f,
        "SW" to 225f,
        "W" to 270f,
        "NW" to 315f
    )
    
    directions.forEach { (direction, angle) ->
        val angleRad = Math.toRadians(angle.toDouble())
        val isCardinal = angle % 90f == 0f
        
        Box(
            modifier = Modifier
                .offset(
                    x = (center + (cos(angleRad) * (radius - 50.dp.value)) - if (isCardinal) 20.dp.value else 16.dp.value).dp,
                    y = (center + (sin(angleRad) * (radius - 50.dp.value)) - if (isCardinal) 20.dp.value else 16.dp.value).dp
                )
                .size(if (isCardinal) 40.dp else 32.dp),
            contentAlignment = Alignment.Center
        ) {
            // Luxury multi-layer background with Islamic pattern inspiration
            Box(
                modifier = Modifier
                    .size(if (isCardinal) 40.dp else 32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                            ),
                            radius = if (isCardinal) 60f else 48f
                        )
                    )
            )
            
            // Inner glow ring
            Box(
                modifier = Modifier
                    .size(if (isCardinal) 36.dp else 28.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            // Direction text with enhanced Islamic styling
            Text(
                text = direction,
                style = MaterialTheme.typography.labelLarge,
                color = if (isCardinal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                fontSize = if (isCardinal) 20.sp else 16.sp
            )
            
            // Special Qibla indicator for North
            if (direction == "N") {
                Box(
                    modifier = Modifier
                        .offset(y = (-14).dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF10B981),
                                    Color(0xFF059669)
                                )
                            )
                        )
                )
            }
        }
    }
}

/**
 * Luxury Islamic prayer progress ring with enhanced animations
 */
@Composable
fun PremiumPrayerProgressRing(prayerTimes: DayPrayerTimes, watchSize: Dp) {
    val currentTime = LocalTime.now()
    val prayers = listOf(
        prayerTimes.fajr,
        prayerTimes.dhuhr,
        prayerTimes.asr,
        prayerTimes.maghrib,
        prayerTimes.isha
    )
    
    // Find current prayer period
    val currentPrayerIndex = prayers.indexOfFirst { time ->
        val nextPrayerTime = prayers.getOrNull(prayers.indexOf(time) + 1) ?: prayers[0]
        currentTime >= time && (if (nextPrayerTime > time) currentTime < nextPrayerTime else true)
    }.let { if (it == -1) 0 else it }
    
    val currentPrayerTime = prayers[currentPrayerIndex]
    val nextPrayerTime = prayers.getOrNull(currentPrayerIndex + 1) ?: prayers[0]
    
    val progress = if (nextPrayerTime > currentPrayerTime) {
        val totalDuration = Duration.between(currentPrayerTime, nextPrayerTime)
        val elapsed = Duration.between(currentPrayerTime, currentTime)
        (elapsed.toMinutes().toFloat() / totalDuration.toMinutes().toFloat()).coerceIn(0f, 1f)
    } else {
        val totalDuration = Duration.between(currentPrayerTime, nextPrayerTime.plusHours(24))
        val elapsed = Duration.between(currentPrayerTime, currentTime)
        (elapsed.toMinutes().toFloat() / totalDuration.toMinutes().toFloat()).coerceIn(0f, 1f)
    }
    
    // Smooth animation for progress
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = EaseOutCubic)
    )
    
    Canvas(modifier = Modifier.size(watchSize)) {
        val center = Offset(size.width / 2, size.height / 2)
        val outerRadius = size.width / 2 - 40f
        val innerRadius = size.width / 2 - 60f
        
        // Multi-layer background rings for depth
        // Outer shadow ring
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF000000).copy(alpha = 0.2f),
                    Color.Transparent
                ),
                radius = outerRadius + 10f
            ),
            center = center,
            radius = outerRadius + 5f,
            style = Stroke(width = 20f, cap = StrokeCap.Round)
        )
        
        // Main background ring with Islamic pattern colors
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFF1E293B).copy(alpha = 0.8f),
                    Color(0xFF334155).copy(alpha = 0.6f),
                    Color(0xFF475569).copy(alpha = 0.7f),
                    Color(0xFF1E293B).copy(alpha = 0.8f)
                ),
                center = center
            ),
            center = center,
            radius = outerRadius,
            style = Stroke(width = 18f, cap = StrokeCap.Round)
        )
        
        // Inner highlight ring
        drawCircle(
            color = Color(0xFFFFD700).copy(alpha = 0.3f),
            center = center,
            radius = innerRadius,
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )
        
        // Enhanced progress arc with Islamic green and gold gradient
        val startAngle = -90f
        val sweepAngle = 360f * animatedProgress
        
        if (sweepAngle > 0f) {
            // Progress glow effect
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF10B981).copy(alpha = 0.3f),
                        Color(0xFFFFD700).copy(alpha = 0.5f),
                        Color(0xFF059669).copy(alpha = 0.3f)
                    ),
                    center = center
                ),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius - 5f, center.y - outerRadius - 5f),
                size = Size((outerRadius + 5f) * 2, (outerRadius + 5f) * 2),
                style = Stroke(width = 28f, cap = StrokeCap.Round)
            )
            
            // Main progress arc with Islamic colors
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF10B981),
                        Color(0xFFFFD700),
                        Color(0xFF059669)
                    ),
                    center = center
                ),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = Size(outerRadius * 2, outerRadius * 2),
                style = Stroke(width = 18f, cap = StrokeCap.Round)
            )
            
            // Inner bright highlight
            drawArc(
                color = Color(0xFFFFFFFF).copy(alpha = 0.8f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                size = Size(innerRadius * 2, innerRadius * 2),
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Premium prayer indicators showing prayer time positions
 */
@Composable
fun PremiumPrayerIndicators(prayerTimes: DayPrayerTimes, watchSize: Dp) {
    val prayers = listOf(
        "Fajr" to prayerTimes.fajr,
        "Dhuhr" to prayerTimes.dhuhr,
        "Asr" to prayerTimes.asr,
        "Maghrib" to prayerTimes.maghrib,
        "Isha" to prayerTimes.isha
    )
    
    val currentTime = LocalTime.now()
    
    prayers.forEach { (name, time) ->
        val hour = time.hour
        val minute = time.minute
        val timeInHours = hour + minute / 60f
        val angle = (timeInHours * 15f) - 90f
        val radius = (watchSize.value * 0.36f).dp
        val angleRad = Math.toRadians(angle.toDouble())
        
        val isActive = currentTime >= time && 
            prayers.find { it.second > time }?.let { currentTime < it.second } ?: true
        
        Box(
            modifier = Modifier
                .offset(
                    x = (cos(angleRad) * radius.value).dp,
                    y = (sin(angleRad) * radius.value).dp
                )
                .size(if (isActive) 20.dp else 12.dp),
            contentAlignment = Alignment.Center
        ) {
            // Premium background with glow
            Box(
                modifier = Modifier
                    .size(if (isActive) 20.dp else 12.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) {
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFF4444),
                                    Color(0xFFFF6B6B)
                                )
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF60A5FA),
                                    Color(0xFF3B82F6)
                                )
                            )
                        }
                    )
            )
        }
    }
}

/**
 * Luxury Islamic Qibla compass hand with enhanced sacred styling
 */
@Composable
fun PremiumQiblaCompassHand(qiblaDirection: Float, watchSize: Dp) {
    // Smooth animated rotation with Islamic aesthetic
    val animatedDirection by animateFloatAsState(
        targetValue = qiblaDirection,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
    )
    
    Canvas(modifier = Modifier.size(watchSize)) {
        val center = Offset(size.width / 2, size.height / 2)
        val handLength = size.width / 2 - 60f
        val angle = Math.toRadians((animatedDirection - 90).toDouble())
        
        val endPoint = Offset(
            (center.x + cos(angle) * handLength).toFloat(),
            (center.y + sin(angle) * handLength).toFloat()
        )
        
        // Sacred Qibla direction indicator with multiple layers
        
        // Shadow/glow effect for the main hand
        drawLine(
            color = Color(0xFF10B981).copy(alpha = 0.3f),
            start = center,
            end = endPoint,
            strokeWidth = 16f,
            cap = StrokeCap.Round
        )
        
        // Main sacred hand with Islamic green and gold gradient effect
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFD700),
                    Color(0xFF10B981),
                    Color(0xFFFFD700)
                ),
                start = center,
                end = endPoint
            ),
            start = center,
            end = endPoint,
            strokeWidth = 10f,
            cap = StrokeCap.Round
        )
        
        // Inner highlight line
        drawLine(
            color = Color(0xFFFFFFFF).copy(alpha = 0.9f),
            start = center,
            end = endPoint,
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
        
        // Enhanced center design with Islamic geometric pattern
        // Outer glow ring
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFD700).copy(alpha = 0.4f),
                    Color.Transparent
                ),
                radius = 20f
            ),
            radius = 15f,
            center = center
        )
        
        // Main center circle with gradient
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFD700),
                    Color(0xFF10B981),
                    Color(0xFF1E293B)
                )
            ),
            radius = 10f,
            center = center
        )
        
        // Inner bright center
        drawCircle(
            color = Color(0xFFFFFFFF).copy(alpha = 0.9f),
            radius = 4f,
            center = center
        )
        
        // Sacred Kaaba-inspired arrowhead design
        val arrowLength = 35f
        val arrowAngle = PI / 4.5  // Slightly narrower for elegance
        
        val leftArrow = Offset(
            (endPoint.x - cos(angle - arrowAngle) * arrowLength).toFloat(),
            (endPoint.y - sin(angle - arrowAngle) * arrowLength).toFloat()
        )
        
        val rightArrow = Offset(
            (endPoint.x - cos(angle + arrowAngle) * arrowLength).toFloat(),
            (endPoint.y - sin(angle + arrowAngle) * arrowLength).toFloat()
        )
        
        // Arrowhead shadow/glow
        drawLine(
            color = Color(0xFF10B981).copy(alpha = 0.3f),
            start = endPoint,
            end = leftArrow,
            strokeWidth = 10f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF10B981).copy(alpha = 0.3f),
            start = endPoint,
            end = rightArrow,
            strokeWidth = 10f,
            cap = StrokeCap.Round
        )
        
        // Main arrowhead with Islamic colors
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFD700),
                    Color(0xFF10B981)
                )
            ),
            start = endPoint,
            end = leftArrow,
            strokeWidth = 7f,
            cap = StrokeCap.Round
        )
        
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFD700),
                    Color(0xFF10B981)
                )
            ),
            start = endPoint,
            end = rightArrow,
            strokeWidth = 7f,
            cap = StrokeCap.Round
        )
        
        // Bright arrowhead highlights
        drawLine(
            color = Color(0xFFFFFFFF).copy(alpha = 0.8f),
            start = endPoint,
            end = leftArrow,
            strokeWidth = 1.5f,
            cap = StrokeCap.Round
        )
        
        drawLine(
            color = Color(0xFFFFFFFF).copy(alpha = 0.8f),
            start = endPoint,
            end = rightArrow,
            strokeWidth = 1.5f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Luxury Islamic geometric center design for the analog watch
 */
@Composable
fun PremiumCenterDesign(watchSize: Dp) {
    Box(
        modifier = Modifier.size(watchSize),
        contentAlignment = Alignment.Center
    ) {
        // Multi-layered center design inspired by Islamic architecture
        
        // Outer shadow ring
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF000000).copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        // Main center with Islamic geometric pattern colors
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            Color(0xFF334155),
                            Color(0xFF475569)
                        )
                    )
                )
        )
        
        // Inner golden ring
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFD700).copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        // Islamic compass crosshair with enhanced design
        Canvas(modifier = Modifier.size(50.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val crossLength = size.width / 2 - 5f
            
            // Horizontal crosshair with gradient
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFFFD700).copy(alpha = 0.8f),
                        Color.Transparent
                    )
                ),
                start = Offset(center.x - crossLength, center.y),
                end = Offset(center.x + crossLength, center.y),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            
            // Vertical crosshair with gradient
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFFFD700).copy(alpha = 0.8f),
                        Color.Transparent
                    )
                ),
                start = Offset(center.x, center.y - crossLength),
                end = Offset(center.x, center.y + crossLength),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            
            // Diagonal crosshairs for 8-point Islamic star pattern
            val diagonalLength = crossLength * 0.7f
            
            // NE-SW diagonal
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF10B981).copy(alpha = 0.6f),
                        Color.Transparent
                    )
                ),
                start = Offset(center.x - diagonalLength * cos(PI / 4).toFloat(), center.y - diagonalLength * sin(PI / 4).toFloat()),
                end = Offset(center.x + diagonalLength * cos(PI / 4).toFloat(), center.y + diagonalLength * sin(PI / 4).toFloat()),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
            
            // NW-SE diagonal
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF10B981).copy(alpha = 0.6f),
                        Color.Transparent
                    )
                ),
                start = Offset(center.x - diagonalLength * cos(PI / 4).toFloat(), center.y + diagonalLength * sin(PI / 4).toFloat()),
                end = Offset(center.x + diagonalLength * cos(PI / 4).toFloat(), center.y - diagonalLength * sin(PI / 4).toFloat()),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }
        
        // Sacred center dot with Islamic styling
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFD700),
                            Color(0xFF10B981),
                            Color(0xFF1E293B)
                        )
                    )
                )
        )
        
        // Inner bright center
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFFFFF).copy(alpha = 0.9f))
        )
    }
}

/**
 * Luxury Islamic current time display with Material 3 enhancements
 */
@Composable
fun PremiumCurrentTimeInfo(prayerTimes: DayPrayerTimes, timeUntilNext: String?, watchSize: Dp) {
    val currentTime = LocalTime.now()
    val prayers = listOf(
        "Fajr" to prayerTimes.fajr,
        "Dhuhr" to prayerTimes.dhuhr,
        "Asr" to prayerTimes.asr,
        "Maghrib" to prayerTimes.maghrib,
        "Isha" to prayerTimes.isha
    )
    
    val currentPrayer = prayers.find { (_, time) ->
        val nextPrayer = prayers.find { it.second > time }
        currentTime >= time && (nextPrayer?.let { currentTime < it.second } ?: true)
    }?.first ?: "Fajr"
    
    // Enhanced Material 3 styled container
    Surface(
        modifier = Modifier
            .offset(y = (watchSize.value * 0.25f).dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1E293B).copy(alpha = 0.98f),
                            Color(0xFF334155).copy(alpha = 0.95f),
                            Color(0xFF475569).copy(alpha = 0.92f)
                        )
                    )
                )
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Current prayer with enhanced Islamic styling
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Islamic geometric accent
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFFFD700),
                                        Color(0xFF10B981)
                                    )
                                )
                            )
                    )
                    
                    Text(
                        text = "🕌 $currentPrayer",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFFFD700),
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Matching accent
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFFFD700),
                                        Color(0xFF10B981)
                                    )
                                )
                            )
                    )
                }
                
                if (timeUntilNext != null) {
                    // Enhanced time display with subtle background
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Color(0xFF10B981).copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Next in $timeUntilNext",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Luxury floating prayer labels with Material 3 elevation and Islamic styling
 */
@Composable
fun FloatingPrayerLabels(prayerTimes: DayPrayerTimes, watchSize: Dp) {
    val prayers = listOf(
        "Fajr" to prayerTimes.fajr,
        "Dhuhr" to prayerTimes.dhuhr,
        "Asr" to prayerTimes.asr,
        "Maghrib" to prayerTimes.maghrib,
        "Isha" to prayerTimes.isha
    )
    
    val currentTime = LocalTime.now()
    
    prayers.forEach { (name, time) ->
        val hour = time.hour
        val minute = time.minute
        val timeInHours = hour + minute / 60f
        val angle = (timeInHours * 15f) - 90f
        val radius = (watchSize.value * 0.28f).dp
        val angleRad = Math.toRadians(angle.toDouble())
        
        val isActive = currentTime >= time && 
            prayers.find { it.second > time }?.let { currentTime < it.second } ?: true
        
        // Enhanced Material 3 Surface with elevation
        Surface(
            modifier = Modifier
                .offset(
                    x = (cos(angleRad) * radius.value).dp,
                    y = (sin(angleRad) * radius.value).dp
                )
                .size(if (isActive) 80.dp else 60.dp),
            shape = RoundedCornerShape(20.dp),
            shadowElevation = if (isActive) 12.dp else 6.dp,
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .background(
                        if (isActive) {
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF10B981).copy(alpha = 0.95f),
                                    Color(0xFF059669).copy(alpha = 0.9f),
                                    Color(0xFF047857).copy(alpha = 0.85f)
                                )
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF1E293B).copy(alpha = 0.9f),
                                    Color(0xFF334155).copy(alpha = 0.8f),
                                    Color(0xFF475569).copy(alpha = 0.7f)
                                )
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Prayer name with enhanced typography
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isActive) Color.White else Color(0xFFFFD700),
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = if (isActive) 16.sp else 13.sp
                    )
                    
                    // Time display with subtle background
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isActive) {
                                    Color.White.copy(alpha = 0.2f)
                                } else {
                                    Color(0xFFFFD700).copy(alpha = 0.2f)
                                }
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = time.format(DateTimeFormatter.ofPattern("h:mm a")),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isActive) Color.White else Color(0xFFFFD700),
                            fontWeight = FontWeight.Medium,
                            fontSize = if (isActive) 12.sp else 10.sp
                        )
                    }
                }
            }
        }
    }
}