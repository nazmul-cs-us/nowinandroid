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
 * Premium hour markers with enhanced design for the analog watch
 */
@Composable
fun PremiumHourMarkers(watchSize: Dp) {
    // Premium cardinal directions with enhanced design
    val center = watchSize.value / 2f
    val radius = watchSize.value / 2f - 20.dp.value
    
    // Cardinal and intercardinal directions
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
        
        Box(
            modifier = Modifier
                .offset(
                    x = (center + (cos(angleRad) * (radius - 50.dp.value)) - 18.dp.value).dp,
                    y = (center + (sin(angleRad) * (radius - 50.dp.value)) - 18.dp.value).dp
                )
                .size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            // Premium background circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF3B82F6).copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            // Direction text with premium styling
            Text(
                text = direction,
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF60A5FA),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

/**
 * Premium prayer time progress ring
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
    
    Canvas(modifier = Modifier.size(watchSize)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.width / 2 - 50f
        
        // Premium background ring with gradient
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF374151).copy(alpha = 0.4f),
                    Color(0xFF1F2937).copy(alpha = 0.2f)
                )
            ),
            center = center,
            radius = radius,
            style = Stroke(width = 12f, cap = StrokeCap.Round)
        )
        
        // Premium progress arc with multiple effects
        val startAngle = -90f
        val sweepAngle = 360f * progress
        
        // Main progress arc
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFFFF4444),
                    Color(0xFFFF6B6B),
                    Color(0xFFFF8E8E)
                ),
                center = center
            ),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 12f, cap = StrokeCap.Round)
        )
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
 * Premium Qibla compass hand with animation
 */
@Composable
fun PremiumQiblaCompassHand(qiblaDirection: Float, watchSize: Dp) {
    // Animate the rotation for smooth movement
    val animatedDirection by animateFloatAsState(
        targetValue = qiblaDirection,
        animationSpec = tween(300, easing = EaseOutCubic)
    )
    
    Canvas(modifier = Modifier.size(watchSize)) {
        val center = Offset(size.width / 2, size.height / 2)
        val handLength = size.width / 2 - 50f
        // Fix Qibla direction calculation - North should be at top (0°), East at right (90°)
        val angle = Math.toRadians((animatedDirection - 90).toDouble())
        
        val endPoint = Offset(
            (center.x + cos(angle) * handLength).toFloat(),
            (center.y + sin(angle) * handLength).toFloat()
        )
        
        // Draw main compass hand
        drawLine(
            color = Color(0xFFFFD700),
            start = center,
            end = endPoint,
            strokeWidth = 8f,
            cap = StrokeCap.Round
        )
        
        // Draw center dot
        drawCircle(
            color = Color(0xFFFFD700),
            radius = 6f,
            center = center
        )
        
        // Draw arrowhead
        val arrowLength = 25f
        val arrowAngle = PI / 5
        
        val leftArrow = Offset(
            (endPoint.x - cos(angle - arrowAngle) * arrowLength).toFloat(),
            (endPoint.y - sin(angle - arrowAngle) * arrowLength).toFloat()
        )
        
        val rightArrow = Offset(
            (endPoint.x - cos(angle + arrowAngle) * arrowLength).toFloat(),
            (endPoint.y - sin(angle + arrowAngle) * arrowLength).toFloat()
        )
        
        // Draw arrowhead
        drawLine(
            color = Color(0xFFFFD700),
            start = endPoint,
            end = leftArrow,
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )
        
        drawLine(
            color = Color(0xFFFFD700),
            start = endPoint,
            end = rightArrow,
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Premium center design for the analog watch
 */
@Composable
fun PremiumCenterDesign(watchSize: Dp) {
    Box(
        modifier = Modifier.size(watchSize),
        contentAlignment = Alignment.Center
    ) {
        // Real compass center with crosshair
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF374151))
        )
        
        // Crosshair lines
        Canvas(modifier = Modifier.size(40.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            
            // Horizontal line
            drawLine(
                color = Color.White,
                start = Offset(0f, center.y),
                end = Offset(size.width, center.y),
                strokeWidth = 2.dp.value
            )
            
            // Vertical line
            drawLine(
                color = Color.White,
                start = Offset(center.x, 0f),
                end = Offset(center.x, size.height),
                strokeWidth = 2.dp.value
            )
        }
        
        // Center dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/**
 * Premium current time info display
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
    
    Box(
        modifier = Modifier
            .offset(y = (watchSize.value * 0.25f).dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1E293B).copy(alpha = 0.95f),
                        Color(0xFF334155).copy(alpha = 0.9f)
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🕌 $currentPrayer",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFFFD700),
                fontWeight = FontWeight.Bold
            )
            if (timeUntilNext != null) {
                Text(
                    text = "Next in $timeUntilNext",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Floating prayer labels around the watch
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
        
        Box(
            modifier = Modifier
                .offset(
                    x = (cos(angleRad) * radius.value).dp,
                    y = (sin(angleRad) * radius.value).dp
                )
                .size(if (isActive) 70.dp else 50.dp),
            contentAlignment = Alignment.Center
        ) {
            // Floating label background
            Box(
                modifier = Modifier
                    .size(if (isActive) 70.dp else 50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isActive) {
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFF4444).copy(alpha = 0.9f),
                                    Color(0xFFFF6B6B).copy(alpha = 0.8f)
                                )
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF3B82F6).copy(alpha = 0.8f),
                                    Color(0xFF1E40AF).copy(alpha = 0.7f)
                                )
                            )
                        }
                    )
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    fontSize = if (isActive) 14.sp else 12.sp
                )
                Text(
                    text = time.format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp
                )
            }
        }
    }
}