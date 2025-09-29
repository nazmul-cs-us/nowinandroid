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
                            
                            // Allow dragging if touch is anywhere within the circular dial
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

        // Central text container with clean background like reference
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(
                    color = Color.Transparent, // Transparent to blend with dial background
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Prayer name - improved typography
                Text(
                    text = prayerName,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        letterSpacing = 0.15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Adjusted time display - enhanced with better typography
                val adjustedTime = adjustTimeByMinutes(originalTime, timeAdjustment)
                Text(
                    text = adjustedTime,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp,
                        letterSpacing = (-0.5).sp // Tighter spacing for time display
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // Adjustment amount with improved styling (hours and minutes format)
                val adjustmentText = when {
                    timeAdjustment > 0 -> {
                        val hours = timeAdjustment / 60
                        val minutes = timeAdjustment % 60
                        when {
                            hours > 0 && minutes > 0 -> "+${hours}h ${minutes}m"
                            hours > 0 -> "+${hours}h"
                            else -> "+${minutes}m"
                        }
                    }
                    timeAdjustment < 0 -> {
                        val totalMinutes = kotlin.math.abs(timeAdjustment)
                        val hours = totalMinutes / 60
                        val minutes = totalMinutes % 60
                        when {
                            hours > 0 && minutes > 0 -> "-${hours}h ${minutes}m"
                            hours > 0 -> "-${hours}h"
                            else -> "${timeAdjustment}m" // Keep negative sign
                        }
                    }
                    else -> "±0m"
                }
                
                Text(
                    text = adjustmentText,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        letterSpacing = 0.1.sp
                    ),
                    color = when {
                        timeAdjustment > 0 -> MaterialTheme.colorScheme.primary
                        timeAdjustment < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outline
                    },
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Professional guidance hint - refined
                Text(
                    text = "Drag to adjust",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 10.sp,
                        letterSpacing = 1.0.sp
                    ),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun DrawScope.drawCleanCircularTimer(center: Offset, radius: Float, timeAdjustment: Int, originalTime: LocalTime, isDragging: Boolean, currentDragAngle: Float) {
    // Clean circular design matching the reference image - no document background
    val timerRadius = radius // Use full radius for pure circular design
    
    // Outer shadow for depth (like the reference)
            drawCircle(
        color = Color.Black.copy(alpha = 0.08f),
        radius = timerRadius + 6f,
        center = Offset(center.x + 1f, center.y + 2f)
    )
    
    // Main light gray circle background (like the reference)
            drawCircle(
        color = Color(0xFFF5F5F5), // Very light gray background
        radius = timerRadius,
        center = center
    )
    
    // Subtle inner gradient for depth
    val innerGradientColors = listOf(
        Color(0xFFFFFFFF).copy(alpha = 0.8f), // White highlight at top
        Color(0xFFF0F0F0), // Light gray
        Color(0xFFE8E8E8)  // Slightly darker gray at bottom
    )
    
            drawCircle(
                brush = Brush.radialGradient(
            colors = innerGradientColors,
            center = Offset(center.x, center.y - radius * 0.3f), // Offset upward for lighting effect
            radius = timerRadius * 0.9f
        ),
        radius = timerRadius,
        center = center
    )
    
    // Subtle border like the reference
            drawCircle(
        color = Color(0xFFDDDDDD), // Light gray border
        radius = timerRadius,
        center = center,
        style = Stroke(width = 1.5f)
    )
    
    // Draw outer border with individual tick marks like the reference
    val outerRadius = timerRadius + 15f // Closer to main circle
    val tickCount = 120 // Much denser tick marks like reference
    
    // Calculate actual prayer time (adjusted) for angle calculation
    val adjustedDateTime = LocalDateTime.of(LocalDate.now(), originalTime).plusMinutes(timeAdjustment.toLong())
    val adjustedTime = adjustedDateTime.toLocalTime()
    
    // Convert time to angle (12-hour format for better readability)
    val hourIn12Format = if (adjustedTime.hour % 12 == 0) 12 else adjustedTime.hour % 12
    val timeAngle = ((hourIn12Format * 60 + adjustedTime.minute) / (12 * 60f)) * 360f - 90f // Start from 12 o'clock (top)
    
    // Draw individual tick marks around the outer circle
    for (i in 0 until tickCount) {
        val markerAngle = i * (360.0 / tickCount) * PI / 180.0 // Evenly spaced
        
        // Determine if this tick should be highlighted (teal) based on current progress
        val currentAngle = if (isDragging) currentDragAngle else timeAngle
        val normalizedCurrentAngle = ((currentAngle % 360 + 360) % 360) // Normalize to 0-360
        val normalizedTickAngle = (i * (360.0 / tickCount)) // Tick angle in degrees
        
        // Check if this tick is within the progress arc (from 0 to current position)
        val isHighlighted = normalizedTickAngle <= normalizedCurrentAngle
        
        // Tick positions - longer and more elegant like reference
        val tickOuterRadius = outerRadius + 12f  // Much longer extending outward
        val tickInnerRadius = outerRadius - 3f   // Slightly inward for better connection
        
        val tickStart = Offset(
            center.x + tickInnerRadius * cos(markerAngle.toFloat()).toFloat(),
            center.y + tickInnerRadius * sin(markerAngle.toFloat()).toFloat()
        )
        val tickEnd = Offset(
            center.x + tickOuterRadius * cos(markerAngle.toFloat()).toFloat(),
            center.y + tickOuterRadius * sin(markerAngle.toFloat()).toFloat()
        )
        
        // Draw tick mark with appropriate color - much thinner and longer like reference
        drawLine(
            color = if (isHighlighted) Color(0xFF10B981) else Color(0xFFE0E0E0), // Compass green for highlighted, light gray for others
            start = tickStart,
            end = tickEnd,
            strokeWidth = 0.8f, // Much thinner strokes like reference
            cap = StrokeCap.Round
        )
    }
    
    // No inner tick marks needed - using outer border design now
    
    // Draw teal pill-shaped indicator positioned inside the dial (like your reference design)
    val displayAngle = if (isDragging) currentDragAngle else timeAngle
    val indicatorAngle = displayAngle * PI / 180f
    val indicatorRadius = timerRadius - 30f // Position inside the dial, closer to the center
    
    val indicatorCenter = Offset(
        center.x + indicatorRadius * cos(indicatorAngle.toFloat()).toFloat(),
        center.y + indicatorRadius * sin(indicatorAngle.toFloat()).toFloat()
    )
    
    // Draw teal pill-shaped indicator (2x bigger for maximum user visibility)
    val pillWidth = 40f  // 2x bigger width - impossible to miss
    val pillHeight = 16f // 2x bigger height - very prominent
    
    // Calculate pill orientation (perpendicular to radius)
    val perpAngle = indicatorAngle + PI / 2
    
    // Calculate pill corner positions
    val halfWidth = pillWidth / 2f
    val halfHeight = pillHeight / 2f
    
    // Create rounded rectangle path for the pill
    val pillPath = Path().apply {
        // Calculate the four corners of the pill rectangle
        val corner1 = Offset(
            indicatorCenter.x - halfWidth * cos(perpAngle.toFloat()) - halfHeight * cos(indicatorAngle.toFloat()),
            indicatorCenter.y - halfWidth * sin(perpAngle.toFloat()) - halfHeight * sin(indicatorAngle.toFloat())
        )
        val corner2 = Offset(
            indicatorCenter.x + halfWidth * cos(perpAngle.toFloat()) - halfHeight * cos(indicatorAngle.toFloat()),
            indicatorCenter.y + halfWidth * sin(perpAngle.toFloat()) - halfHeight * sin(indicatorAngle.toFloat())
        )
        val corner3 = Offset(
            indicatorCenter.x + halfWidth * cos(perpAngle.toFloat()) + halfHeight * cos(indicatorAngle.toFloat()),
            indicatorCenter.y + halfWidth * sin(perpAngle.toFloat()) + halfHeight * sin(indicatorAngle.toFloat())
        )
        val corner4 = Offset(
            indicatorCenter.x - halfWidth * cos(perpAngle.toFloat()) + halfHeight * cos(indicatorAngle.toFloat()),
            indicatorCenter.y - halfWidth * sin(perpAngle.toFloat()) + halfHeight * sin(indicatorAngle.toFloat())
        )
        
        moveTo(corner1.x, corner1.y)
        lineTo(corner2.x, corner2.y)
        lineTo(corner3.x, corner3.y)
        lineTo(corner4.x, corner4.y)
        close()
    }
    
    // Draw the compass green pill indicator
    drawPath(
        path = pillPath,
        color = Color(0xFF10B981)
    )
    
    // Add rounded end caps to make it pill-like
    val capRadius = pillHeight / 2f
    
    // Left cap
    val leftCapCenter = Offset(
        indicatorCenter.x - halfWidth * cos(perpAngle.toFloat()),
        indicatorCenter.y - halfWidth * sin(perpAngle.toFloat())
    )
    drawCircle(
        color = Color(0xFF10B981),
        radius = capRadius,
        center = leftCapCenter
    )
    
    // Right cap
    val rightCapCenter = Offset(
        indicatorCenter.x + halfWidth * cos(perpAngle.toFloat()),
        indicatorCenter.y + halfWidth * sin(perpAngle.toFloat())
    )
    drawCircle(
        color = Color(0xFF10B981),
        radius = capRadius,
        center = rightCapCenter
    )
}

private fun DrawScope.drawPNGDocumentBackground(center: Offset, radius: Float) {
    val documentSize = radius * 1.8f // Document size larger than the circular dial
    val cornerSize = radius * 0.4f // Size of the folded corner
    
    // Main document shadow for depth
    drawRect(
        color = Color.Black.copy(alpha = 0.15f),
        topLeft = Offset(
            center.x - documentSize/2 + 3f,
            center.y - documentSize/2 + 4f
        ),
        size = Size(documentSize, documentSize)
    )
    
    // Main document body (light gray like PNG file)
    val documentPath = Path().apply {
        // Start from top-left, create document shape with folded corner
        moveTo(center.x - documentSize/2, center.y - documentSize/2 + 16f)
        
        // Top edge up to fold
        lineTo(center.x + documentSize/2 - cornerSize, center.y - documentSize/2)
        
        // Folded corner diagonal
        lineTo(center.x + documentSize/2, center.y - documentSize/2 + cornerSize)
        
        // Right edge
        lineTo(center.x + documentSize/2, center.y + documentSize/2 - 16f)
        
        // Bottom-right corner (rounded)
        quadraticTo(
            center.x + documentSize/2, center.y + documentSize/2,
            center.x + documentSize/2 - 16f, center.y + documentSize/2
        )
        
        // Bottom edge
        lineTo(center.x - documentSize/2 + 16f, center.y + documentSize/2)
        
        // Bottom-left corner (rounded)
        quadraticTo(
            center.x - documentSize/2, center.y + documentSize/2,
            center.x - documentSize/2, center.y + documentSize/2 - 16f
        )
        
        // Left edge
        lineTo(center.x - documentSize/2, center.y - documentSize/2 + 16f)
        
        close()
    }
    
    // Draw main document with light gray gradient
    drawPath(
        path = documentPath,
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFF8F8F8), // Very light gray at top
                Color(0xFFF0F0F0), // Light gray at bottom
            ),
            start = Offset(center.x, center.y - documentSize/2),
            end = Offset(center.x, center.y + documentSize/2)
        )
    )
    
    // Draw the folded corner triangle (slightly darker for depth)
    val foldPath = Path().apply {
        moveTo(center.x + documentSize/2 - cornerSize, center.y - documentSize/2)
        lineTo(center.x + documentSize/2 - cornerSize, center.y - documentSize/2 + cornerSize)
        lineTo(center.x + documentSize/2, center.y - documentSize/2 + cornerSize)
        close()
    }
    
    drawPath(
        path = foldPath,
        color = Color(0xFFE0E0E0) // Slightly darker for the fold
    )
    
    // Document border
    drawPath(
        path = documentPath,
        color = Color(0xFFD0D0D0),
        style = Stroke(width = 1.5f)
    )
    
    // Fold line
    drawLine(
        color = Color(0xFFD0D0D0),
        start = Offset(center.x + documentSize/2 - cornerSize, center.y - documentSize/2),
        end = Offset(center.x + documentSize/2, center.y - documentSize/2 + cornerSize),
        strokeWidth = 1f
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
