package com.starception.submission.feature.prayertimes.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                        },
                        onDragEnd = { 
                            isDragging = false
                        }
                    ) { change, _ ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val currentAngle = kotlin.math.atan2(
                            change.position.y - center.y,
                            change.position.x - center.x
                        ) * 180f / PI.toFloat()
                        val angleDiff = currentAngle - lastAngle
                        val newAdjustment = (timeAdjustment + (angleDiff / 6f).toInt()).coerceIn(-30, 30)
                        if (newAdjustment != timeAdjustment) {
                            onTimeAdjusted(newAdjustment)
                        }
                        lastAngle = currentAngle
                    }
                }
        ) {
            val center = this.center
            val radius = (size.minDimension / 2.0f) * 0.85f
            
            // Drop shadow layers for elevation
            for (i in 3 downTo 1) {
                val shadowOffset = i * 3f
                val shadowAlpha = (0.2f / i)
                drawCircle(
                    color = Color.Black.copy(alpha = shadowAlpha),
                    radius = radius + i * 2f,
                    center = Offset(center.x + shadowOffset, center.y + shadowOffset)
                )
            }
            
            // Outer rim with strong 3D gradient
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFffffff), // Top highlight
                        Color(0xFFe8e8e8), // Right light
                        Color(0xFF999999), // Bottom shadow
                        Color(0xFF999999), // Left shadow
                        Color(0xFFffffff)  // Back to top
                    ),
                    center = center
                ),
                radius = radius,
                center = center,
                style = Stroke(width = radius * 0.12f)
            )
            
            // Inner rim shadow for depth
            drawCircle(
                color = Color(0xFFc0c0c0),
                radius = radius * 0.88f,
                center = center,
                style = Stroke(width = 2f)
            )
            
            // Main surface with radial highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFffffff), // Bright center
                        Color(0xFFf8f8f8), // Light
                        Color(0xFFf0f0f0)  // Slightly darker edge
                    ),
                    center = Offset(center.x - radius * 0.2f, center.y - radius * 0.2f),
                    radius = radius * 0.6f
                ),
                radius = radius * 0.85f,
                center = center
            )
            
            // Small teal indicator line at current position
            drawTealIndicator(center, radius * 0.95f, timeAdjustment)
        }

        // Simple time display
        val adjustedTime = adjustTimeByMinutes(originalTime, timeAdjustment)
        Text(
            text = adjustedTime,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            ),
            color = Color.Black,
            textAlign = TextAlign.Center
        )
    }
}

private fun DrawScope.drawTealIndicator(center: Offset, radius: Float, timeAdjustment: Int) {
    val indicatorAngle = (timeAdjustment * 6f - 90f) * PI / 180f // Convert to radians, start from top
    
    // Small teal line at the edge (exactly like your reference)
    val startRadius = radius * 0.85f
    val endRadius = radius * 0.95f
    
    val startX = center.x + startRadius * cos(indicatorAngle).toFloat()
    val startY = center.y + startRadius * sin(indicatorAngle).toFloat()
    val endX = center.x + endRadius * cos(indicatorAngle).toFloat()
    val endY = center.y + endRadius * sin(indicatorAngle).toFloat()
    
    drawLine(
        color = Color(0xFF4FC3F7), // Light teal like your reference
        start = Offset(startX, startY),
        end = Offset(endX, endY),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )
}

private fun adjustTimeByMinutes(originalTime: LocalTime, minutes: Int): String {
    val adjustedDateTime = LocalDateTime.of(
        LocalDate.now(),
        originalTime
    ).plusMinutes(minutes.toLong())

    val adjustedHours = adjustedDateTime.hour
    val adjustedMinutes = adjustedDateTime.minute

    return String.format("%02d:%02d", adjustedHours, adjustedMinutes)
}
