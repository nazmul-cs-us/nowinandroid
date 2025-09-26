package com.starception.submission.feature.prayertimes.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import android.util.Log

@Composable
fun InteractivePrayerDial(
    modifier: Modifier = Modifier,
    prayerName: String,
    originalTime: LocalTime,
    timeAdjustment: Int,
    onTimeAdjusted: (Int) -> Unit,
    onSaveAdjustment: (String, Int) -> Unit,
    onResetAdjustment: () -> Unit
) {
    var lastAngle by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var accumulatedAngle by remember { mutableStateOf(0f) }
    var baseAdjustment by remember { mutableStateOf(timeAdjustment) }
    var currentDragAngle by remember { mutableStateOf(0f) }
    
    // Reset base when timeAdjustment changes externally
    LaunchedEffect(timeAdjustment) {
        if (!isDragging) {
            baseAdjustment = timeAdjustment
            accumulatedAngle = 0f
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f) // Perfect circle
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            
                            // Start dragging anywhere within the dial area - much more lenient
                            val distanceFromCenter = kotlin.math.sqrt(
                                (offset.x - center.x) * (offset.x - center.x) + 
                                (offset.y - center.y) * (offset.y - center.y)
                            )
                            
                            // Allow dragging if touch is anywhere within the outer radius
                            val outerRadius = kotlin.math.min(size.width, size.height) * 0.5f
                            if (distanceFromCenter <= outerRadius) {
                                isDragging = true
                                lastAngle = kotlin.math.atan2(
                                    offset.y - center.y,
                                    offset.x - center.x
                                ) * 180f / PI.toFloat()
                                accumulatedAngle = 0f // Reset accumulated angle
                                
                                // Calculate current prayer time angle to start from current position
                                val adjustedDateTime = java.time.LocalDateTime.of(java.time.LocalDate.now(), originalTime).plusMinutes(timeAdjustment.toLong())
                                val adjustedTime = adjustedDateTime.toLocalTime()
                                val hourIn12Format = if (adjustedTime.hour % 12 == 0) 12 else adjustedTime.hour % 12
                                currentDragAngle = ((hourIn12Format * 60 + adjustedTime.minute) / (12 * 60f)) * 360f - 90f
                                
                                Log.d("InteractiveDial", "🚀 DRAG START - Prayer: $prayerName")
                                Log.d("InteractiveDial", "📍 Touch: (${offset.x.toInt()}, ${offset.y.toInt()}), Center: (${center.x.toInt()}, ${center.y.toInt()})")
                                Log.d("InteractiveDial", "📏 Distance: ${distanceFromCenter.toInt()}dp, Radius: ${outerRadius.toInt()}dp")
                                Log.d("InteractiveDial", "🎯 Initial angle: ${lastAngle.toInt()}°, Prayer time angle: ${currentDragAngle.toInt()}°, Current adjustment: ${timeAdjustment}m")
                            } else {
                                Log.d("InteractiveDial", "❌ Touch outside radius - Distance: ${distanceFromCenter.toInt()}, Radius: ${outerRadius.toInt()}")
                            }
                        },
                        onDragEnd = { 
                            Log.d("InteractiveDial", "🏁 DRAG END - Prayer: $prayerName, Final adjustment: ${timeAdjustment}m")
                            Log.d("InteractiveDial", "📊 Final accumulated angle: ${accumulatedAngle}°")
                            isDragging = false
                        }
                    ) { change, _ ->
                        if (isDragging) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val fingerAngle = kotlin.math.atan2(
                                change.position.y - center.y,
                                change.position.x - center.x
                            ) * 180f / PI.toFloat()
                            
                            var angleDiff = fingerAngle - lastAngle
                            if (angleDiff > 180f) angleDiff -= 360f
                            if (angleDiff < -180f) angleDiff += 360f
                            
                            // Update current drag angle to follow finger
                            currentDragAngle += angleDiff
                            // Keep angle in 0-360 range
                            if (currentDragAngle < 0f) currentDragAngle += 360f
                            if (currentDragAngle >= 360f) currentDragAngle -= 360f
                            
                            // Use accumulated angle changes to calculate adjustment
                            // Accumulate the angle changes from dragging
                            accumulatedAngle += angleDiff
                            
                            // Convert accumulated angle to minutes: clockwise = positive, anti-clockwise = negative
                            val newAdjustment = baseAdjustment + (accumulatedAngle / 3f).toInt()
                            
                            Log.d("InteractiveDial", "🔄 DRAG UPDATE - Finger: (${change.position.x.toInt()}, ${change.position.y.toInt()})")
                            Log.d("InteractiveDial", "📐 Angles - Finger: ${fingerAngle.toInt()}°, Drag: ${currentDragAngle.toInt()}°, Diff: ${angleDiff.toInt()}°")
                            Log.d("InteractiveDial", "⏱️  Adjustment calc - Base: ${baseAdjustment}m, Accumulated: ${accumulatedAngle.toInt()}°, New: ${newAdjustment}m")
                            
                            if (newAdjustment != timeAdjustment) {
                                Log.d("InteractiveDial", "✅ ADJUSTMENT APPLIED - Old: ${timeAdjustment}m → New: ${newAdjustment}m")
                                onTimeAdjusted(newAdjustment)
                            } else {
                                Log.d("InteractiveDial", "📍 Same adjustment value: ${newAdjustment}m")
                            }
                            
                            // Always update lastAngle to prevent accumulation issues
                            lastAngle = fingerAngle
                        } else {
                            Log.d("InteractiveDial", "❌ Drag event but isDragging=false")
                        }
                    }
                }
        ) {
            val center = this.center
            val radius = kotlin.math.min(size.width, size.height) * 0.4f
            
            // Draw clean circular timer design
            drawCleanCircularTimer(center, radius, timeAdjustment, originalTime, isDragging, currentDragAngle)
        }

        // Central text container with dark background to match design
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(
                    color = Color(0xFF2A2A2A).copy(alpha = 0.95f), // Dark background
                    shape = CircleShape
                )
                .border(
                    width = 2.dp,
                    color = Color(0xFF00BCD4), // Teal border
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Prayer name
                Text(
                    text = prayerName,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = Color.White, // White text on dark background
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // Adjusted time display
                val adjustedTime = adjustTimeByMinutes(originalTime, timeAdjustment)
                Text(
                    text = adjustedTime,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp
                    ),
                    color = Color(0xFF00BCD4), // Teal color to match design
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Adjustment amount with +/- indicator
                val adjustmentText = when {
                    timeAdjustment > 0 -> "+${timeAdjustment}m"
                    timeAdjustment < 0 -> "${timeAdjustment}m"
                    else -> "0m"
                }
                
                Text(
                    text = adjustmentText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = when {
                        timeAdjustment > 0 -> Color(0xFF4CAF50) // Brighter green for dark background
                        timeAdjustment < 0 -> Color(0xFFFF5252) // Brighter red for dark background
                        else -> Color(0xFFCCCCCC) // Light gray for zero
                    },
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun DrawScope.drawCleanCircularTimer(center: Offset, radius: Float, timeAdjustment: Int, originalTime: LocalTime, isDragging: Boolean, currentDragAngle: Float) {
    // Dark background shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.8f),
        radius = radius + 20f,
        center = Offset(center.x + 4f, center.y + 6f)
    )
    
    // Main metallic circle with radial gradient effect
    val gradientColors = listOf(
        Color(0xFFE8E8E8), // Light metallic
        Color(0xFFC0C0C0), // Mid metallic  
        Color(0xFF808080), // Dark metallic
        Color(0xFF404040)  // Very dark metallic
    )
    
    drawCircle(
        brush = Brush.radialGradient(
            colors = gradientColors,
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
    
    // Teal border around the circle
    drawCircle(
        color = Color(0xFF00BCD4),
        radius = radius,
        center = center,
        style = Stroke(width = 4f)
    )
    
    // Calculate actual prayer time (adjusted)
    val adjustedDateTime = LocalDateTime.of(LocalDate.now(), originalTime).plusMinutes(timeAdjustment.toLong())
    val adjustedTime = adjustedDateTime.toLocalTime()
    
    // Convert time to angle (12-hour format for better readability)
    val hourIn12Format = if (adjustedTime.hour % 12 == 0) 12 else adjustedTime.hour % 12
    val timeAngle = ((hourIn12Format * 60 + adjustedTime.minute) / (12 * 60f)) * 360f - 90f // Start from 12 o'clock (top)
    
    // Draw minimal PNG file-style tick marks - extremely subtle like document guidelines
    val currentIndicatorAngle = if (isDragging) currentDragAngle else timeAngle
    val normalizedIndicatorAngle = ((currentIndicatorAngle + 90f) % 360f + 360f) % 360f // Normalize to 0-360
    
    // Only draw major hour marks (12 total) - very minimal like PNG document
    for (i in 0 until 12) { // Only 12 major marks (every 30 degrees)
        val tickAngleDegrees = i * 30f // Every 30 degrees (hour positions)
        val angle = (tickAngleDegrees - 90f) * PI / 180f // Convert to radians
        
        // Calculate if this tick should be lit up
        val shouldLightUp = if (isDragging) {
            val tickNormalizedAngle = tickAngleDegrees
            val indicatorNormalizedAngle = normalizedIndicatorAngle
            
            // Check if tick is between 0 and indicator position
            if (indicatorNormalizedAngle >= tickNormalizedAngle) {
                tickNormalizedAngle <= indicatorNormalizedAngle
            } else {
                // Handle wrap around
                tickNormalizedAngle <= indicatorNormalizedAngle || tickNormalizedAngle >= indicatorNormalizedAngle + 360f - 360f
            }
        } else false
        
        // Extremely subtle marks - almost invisible like PNG document guidelines
        val tickStartRadius = radius + 10f
        val tickEndRadius = radius + 18f
        
        val tickStart = Offset(
            center.x + tickStartRadius * cos(angle).toFloat(),
            center.y + tickStartRadius * sin(angle).toFloat()
        )
        val tickEnd = Offset(
            center.x + tickEndRadius * cos(angle).toFloat(),
            center.y + tickEndRadius * sin(angle).toFloat()
        )
        
        drawLine(
            color = if (shouldLightUp && isDragging) {
                Color(0xFF00BCD4) // Teal when lit
            } else {
                Color(0xFFE8E8E8).copy(alpha = 0.08f) // Almost invisible when not lit - like PNG document guidelines
            },
            start = tickStart,
            end = tickEnd,
            strokeWidth = 0.8f, // Very thin
            cap = StrokeCap.Square
        )
    }
    
    // Draw rounded pill-shaped indicator
    val displayAngle = if (isDragging) currentDragAngle else timeAngle
    val indicatorAngle = displayAngle * PI / 180f
    val indicatorRadius = radius + 12f
    
    val indicatorCenter = Offset(
        center.x + indicatorRadius * cos(indicatorAngle.toFloat()).toFloat(),
        center.y + indicatorRadius * sin(indicatorAngle.toFloat()).toFloat()
    )
    
    // Draw rounded pill-shaped indicator (like a rounded rectangle)
    val pillWidth = 20f
    val pillHeight = 8f
    
    // Calculate the pill rectangle aligned with the radius
    val perpAngle = indicatorAngle + PI / 2 // Perpendicular to radius for width direction
    
    // Calculate pill corner positions
    val halfWidth = pillWidth / 2f
    val halfHeight = pillHeight / 2f
    
    // Four corners of the pill rectangle
    val corner1 = Offset(
        indicatorCenter.x - halfHeight * cos(indicatorAngle.toFloat()).toFloat() - halfWidth * cos(perpAngle.toFloat()).toFloat(),
        indicatorCenter.y - halfHeight * sin(indicatorAngle.toFloat()).toFloat() - halfWidth * sin(perpAngle.toFloat()).toFloat()
    )
    val corner2 = Offset(
        indicatorCenter.x - halfHeight * cos(indicatorAngle.toFloat()).toFloat() + halfWidth * cos(perpAngle.toFloat()).toFloat(),
        indicatorCenter.y - halfHeight * sin(indicatorAngle.toFloat()).toFloat() + halfWidth * sin(perpAngle.toFloat()).toFloat()
    )
    val corner3 = Offset(
        indicatorCenter.x + halfHeight * cos(indicatorAngle.toFloat()).toFloat() + halfWidth * cos(perpAngle.toFloat()).toFloat(),
        indicatorCenter.y + halfHeight * sin(indicatorAngle.toFloat()).toFloat() + halfWidth * sin(perpAngle.toFloat()).toFloat()
    )
    val corner4 = Offset(
        indicatorCenter.x + halfHeight * cos(indicatorAngle.toFloat()).toFloat() - halfWidth * cos(perpAngle.toFloat()).toFloat(),
        indicatorCenter.y + halfHeight * sin(indicatorAngle.toFloat()).toFloat() - halfWidth * sin(perpAngle.toFloat()).toFloat()
    )
    
    // Create rounded rectangle path
    val pillPath = Path().apply {
        moveTo(corner1.x, corner1.y)
        lineTo(corner2.x, corner2.y)
        lineTo(corner3.x, corner3.y)
        lineTo(corner4.x, corner4.y)
        close()
    }
    
    // Draw the pill indicator with rounded corners
    drawPath(
        path = pillPath,
        color = Color(0xFF00BCD4)
    )
    
    // Add rounded end caps to make it more pill-like
    val capRadius = pillHeight / 2f
    
    // Left cap
    val leftCapCenter = Offset(
        indicatorCenter.x - halfHeight * cos(indicatorAngle.toFloat()).toFloat(),
        indicatorCenter.y - halfHeight * sin(indicatorAngle.toFloat()).toFloat()
    )
    drawCircle(
        color = Color(0xFF00BCD4),
        radius = capRadius,
        center = leftCapCenter
    )
    
    // Right cap
    val rightCapCenter = Offset(
        indicatorCenter.x + halfHeight * cos(indicatorAngle.toFloat()).toFloat(),
        indicatorCenter.y + halfHeight * sin(indicatorAngle.toFloat()).toFloat()
    )
    drawCircle(
        color = Color(0xFF00BCD4),
        radius = capRadius,
        center = rightCapCenter
    )
}

private fun adjustTimeByMinutes(originalTime: LocalTime, minutes: Int): String {
    val adjustedDateTime = LocalDateTime.of(
        LocalDate.now(),
        originalTime
    ).plusMinutes(minutes.toLong())

    val adjustedTime = adjustedDateTime.toLocalTime()
    val hour12 = if (adjustedTime.hour == 0) 12 
                else if (adjustedTime.hour > 12) adjustedTime.hour - 12 
                else adjustedTime.hour
    val amPm = if (adjustedTime.hour < 12) "AM" else "PM"

    return String.format("%d:%02d %s", hour12, adjustedTime.minute, amPm)
}
