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
                                
                                Log.d("InteractiveDial", "🚀 DRAG START - Prayer: $prayerName")
                                Log.d("InteractiveDial", "📍 Touch: (${offset.x.toInt()}, ${offset.y.toInt()}), Center: (${center.x.toInt()}, ${center.y.toInt()})")
                                Log.d("InteractiveDial", "📏 Distance: ${distanceFromCenter.toInt()}dp, Radius: ${outerRadius.toInt()}dp")
                                Log.d("InteractiveDial", "🎯 Initial angle: ${lastAngle.toInt()}°, Current adjustment: ${timeAdjustment}m")
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
                            val currentAngle = kotlin.math.atan2(
                                change.position.y - center.y,
                                change.position.x - center.x
                            ) * 180f / PI.toFloat()
                            
                            var angleDiff = currentAngle - lastAngle
                            if (angleDiff > 180f) angleDiff -= 360f
                            if (angleDiff < -180f) angleDiff += 360f
                            
                            // Accumulate the angle changes
                            val previousAccumulated = accumulatedAngle
                            accumulatedAngle += angleDiff
                            
                            // Calculate target adjustment from base + accumulated angle
                            val minutesFromAngle = (accumulatedAngle / 3f).toInt()
                            val targetAdjustment = baseAdjustment + minutesFromAngle
                            val newAdjustment = targetAdjustment.coerceIn(-180, 180)
                            
                            Log.d("InteractiveDial", "🔄 DRAG UPDATE - Position: (${change.position.x.toInt()}, ${change.position.y.toInt()})")
                            Log.d("InteractiveDial", "📐 Angles - Current: ${currentAngle.toInt()}°, Last: ${lastAngle.toInt()}°, Diff: ${angleDiff.toInt()}°")
                            Log.d("InteractiveDial", "📈 Accumulated - Previous: ${previousAccumulated}°, Current: ${accumulatedAngle}°")
                            Log.d("InteractiveDial", "⏱️  Calculation - Base: ${baseAdjustment}m, Angle minutes: ${minutesFromAngle}m, Target: ${targetAdjustment}m")
                            
                            if (newAdjustment != timeAdjustment) {
                                Log.d("InteractiveDial", "✅ ADJUSTMENT APPLIED - Old: ${timeAdjustment}m → New: ${newAdjustment}m")
                                onTimeAdjusted(newAdjustment)
                            } else {
                                Log.d("InteractiveDial", "📍 Same adjustment value: ${newAdjustment}m")
                            }
                            
                            // Always update lastAngle to prevent accumulation issues
                            lastAngle = currentAngle
                        } else {
                            Log.d("InteractiveDial", "❌ Drag event but isDragging=false")
                        }
                    }
                }
        ) {
            val center = this.center
            val radius = kotlin.math.min(size.width, size.height) * 0.4f
            
            // Draw clean circular timer design
            drawCleanCircularTimer(center, radius, timeAdjustment)
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

private fun DrawScope.drawCleanCircularTimer(center: Offset, radius: Float, timeAdjustment: Int) {
    // Background circle (light gray track)
    drawCircle(
        color = Color(0xFFE5E5E5),
        radius = radius,
        center = center,
        style = Stroke(width = 12f)
    )
    
    // Progress arc (teal/cyan colored based on adjustment)
    val progressPercentage = (timeAdjustment + 180f) / 360f // Map -180 to 180 range to 0-1
    val sweepAngle = progressPercentage * 360f
    val startAngle = -90f // Start from top
    
    // Draw the progress arc with beautiful teal color
    drawArc(
        color = Color(0xFF00BCD4), // Teal/cyan color from your reference
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = 12f, cap = StrokeCap.Round)
    )
    
    // Draw tick marks around the circle (minimal and clean)
    for (i in 0 until 60) { // 60 tick marks (every 6 degrees)
        val angle = (i * 6f - 90f) * PI / 180f
        val isMajorTick = i % 5 == 0 // Every 30 degrees
        
        val tickStartRadius = radius + 15f
        val tickEndRadius = if (isMajorTick) radius + 25f else radius + 20f
        val tickWidth = if (isMajorTick) 3f else 1.5f
        val tickColor = if (isMajorTick) Color(0xFF666666) else Color(0xFFCCCCCC)
        
        val tickStart = Offset(
            center.x + tickStartRadius * cos(angle).toFloat(),
            center.y + tickStartRadius * sin(angle).toFloat()
        )
        val tickEnd = Offset(
            center.x + tickEndRadius * cos(angle).toFloat(),
            center.y + tickEndRadius * sin(angle).toFloat()
        )
        
        drawLine(
            color = tickColor,
            start = tickStart,
            end = tickEnd,
            strokeWidth = tickWidth,
            cap = StrokeCap.Round
        )
    }
    
    // Draw draggable knob at current position - MUCH MORE VISIBLE
    val knobAngle = (startAngle + sweepAngle) * PI / 180f
    val knobRadius = 20f // Larger knob
    val knobCenter = Offset(
        center.x + radius * cos(knobAngle).toFloat(),
        center.y + radius * sin(knobAngle).toFloat()
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
