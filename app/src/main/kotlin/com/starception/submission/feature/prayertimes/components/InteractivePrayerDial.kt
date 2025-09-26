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

        // Central text container with background for better visibility
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(
                    color = Color.White.copy(alpha = 0.95f),
                    shape = CircleShape
                )
                .border(
                    width = 2.dp,
                    color = Color(0xFFE0E0E0),
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
                    color = Color(0xFF2E2E2E), // Dark gray for high contrast
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
                    color = Color(0xFF1A1A1A), // Very dark for maximum contrast
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
                        timeAdjustment > 0 -> Color(0xFF2E7D32) // Darker green for better visibility
                        timeAdjustment < 0 -> Color(0xFFC62828) // Darker red for better visibility
                        else -> Color(0xFF616161) // Darker gray for zero
                    },
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun DrawScope.drawCleanCircularTimer(center: Offset, radius: Float, timeAdjustment: Int, originalTime: LocalTime, isDragging: Boolean, currentDragAngle: Float) {
    // Background circle (light gray track)
    drawCircle(
        color = Color(0xFFE5E5E5),
        radius = radius,
        center = center,
        style = Stroke(width = 12f)
    )
    
    // Calculate actual prayer time (adjusted)
    val adjustedDateTime = LocalDateTime.of(LocalDate.now(), originalTime).plusMinutes(timeAdjustment.toLong())
    val adjustedTime = adjustedDateTime.toLocalTime()
    
    // Convert time to angle (12-hour format for better readability)
    val hourIn12Format = if (adjustedTime.hour % 12 == 0) 12 else adjustedTime.hour % 12
    val timeAngle = ((hourIn12Format * 60 + adjustedTime.minute) / (12 * 60f)) * 360f - 90f // Start from 12 o'clock (top)
    
    // Draw subtle arc showing time position
    drawArc(
        color = Color(0xFF00BCD4).copy(alpha = 0.3f), // Light teal background arc
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = 6f)
    )
    
    // Draw hour markers (12, 3, 6, 9) for clock reference
    for (hour in arrayOf(12, 3, 6, 9)) {
        val hourAngle = ((hour % 12) * 30f - 90f) * PI / 180f // 30 degrees per hour, starting from 12 o'clock
        val tickStartRadius = radius + 20f
        val tickEndRadius = radius + 35f
        
        val tickStart = Offset(
            center.x + tickStartRadius * cos(hourAngle).toFloat(),
            center.y + tickStartRadius * sin(hourAngle).toFloat()
        )
        val tickEnd = Offset(
            center.x + tickEndRadius * cos(hourAngle).toFloat(),
            center.y + tickEndRadius * sin(hourAngle).toFloat()
        )
        
        drawLine(
            color = Color(0xFF444444),
            start = tickStart,
            end = tickEnd,
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
    }
    
    // Draw minute tick marks (subtle)
    for (i in 0 until 12) { // 12 tick marks for hours
        val angle = (i * 30f - 90f) * PI / 180f // Every 30 degrees (hour positions)
        val isMainHour = i % 3 == 0 // 12, 3, 6, 9 already drawn above
        
        if (!isMainHour) {
            val tickStartRadius = radius + 15f
            val tickEndRadius = radius + 25f
            
            val tickStart = Offset(
                center.x + tickStartRadius * cos(angle).toFloat(),
                center.y + tickStartRadius * sin(angle).toFloat()
            )
            val tickEnd = Offset(
                center.x + tickEndRadius * cos(angle).toFloat(),
                center.y + tickEndRadius * sin(angle).toFloat()
            )
            
            drawLine(
                color = Color(0xFFCCCCCC),
                start = tickStart,
                end = tickEnd,
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }
    }
    
    // Draw draggable knob - use drag angle when dragging, otherwise prayer time angle
    val displayAngle = if (isDragging) currentDragAngle else timeAngle
    val knobAngle = displayAngle * PI / 180f
    val knobRadius = 20f // Larger knob
    val knobCenter = Offset(
        center.x + radius * cos(knobAngle.toFloat()).toFloat(),
        center.y + radius * sin(knobAngle.toFloat()).toFloat()
    )
    
    // Large knob shadow for visibility
    drawCircle(
        color = Color.Black.copy(alpha = 0.3f),
        radius = knobRadius + 4f,
        center = Offset(knobCenter.x + 2f, knobCenter.y + 3f)
    )
    
    // Bright colored outer ring for high visibility
    drawCircle(
        color = Color(0xFF00BCD4),
        radius = knobRadius + 2f,
        center = knobCenter
    )
    
    // Main knob circle (bright white)
    drawCircle(
        color = Color.White,
        radius = knobRadius,
        center = knobCenter
    )
    
    // Inner teal circle for contrast
    drawCircle(
        color = Color(0xFF00BCD4),
        radius = knobRadius * 0.6f,
        center = knobCenter
    )
    
    // Center white dot
    drawCircle(
        color = Color.White,
        radius = 4f,
        center = knobCenter
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
