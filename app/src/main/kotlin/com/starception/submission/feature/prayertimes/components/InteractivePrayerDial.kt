package com.starception.submission.feature.prayertimes.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

/**
 * Material 3 expressive design card with interactive dial for prayer time adjustment
 * Features asymmetrical shapes, layered backgrounds, and sophisticated styling
 */
@Composable
fun InteractivePrayerTimeCard(
    prayerName: String,
    originalTime: String,
    onTimeAdjustment: (String, String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var timeAdjustment by remember { mutableStateOf(0) } // Minutes adjustment
    val hapticFeedback = LocalHapticFeedback.current
    
    // Material 3 theme colors for consistent design
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    
    // Material 3 expressive design with layered backgrounds
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colorScheme.background,
                        colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        // More compact card like PNG file icon
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.82f),
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.surface,
            shadowElevation = 8.dp,
            tonalElevation = 1.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Layered background with subtle gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    colorScheme.primaryContainer.copy(alpha = 0.05f),
                                    Color.Transparent
                                ),
                                center = Offset(0.3f, 0.3f),
                                radius = 0.8f
                            )
                        )
                )
                
                // Compact content area with dial taking most space
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Compact interactive dial (prayer name will be inside)
                    InteractiveTimeDial(
                        originalTime = originalTime,
                        adjustmentMinutes = timeAdjustment,
                        prayerName = prayerName,
                        onAdjustmentChange = { adjustment ->
                            if (adjustment != timeAdjustment) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                timeAdjustment = adjustment
                            }
                        },
                        colorScheme = colorScheme,
                        modifier = Modifier
                            .size(280.dp)
                            .weight(1f)
                    )
                }
                
                // Compact corner accent like PNG file icon
                Surface(
                    shape = RoundedCornerShape(0.dp, 16.dp, 0.dp, 16.dp),
                    color = colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                // Compact action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    OutlinedButton(
                        onClick = { onCancel() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        border = BorderStroke(1.dp, colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Cancel",
                            style = typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Button(
                        onClick = { 
                            val adjustedTime = adjustTimeByMinutes(originalTime, timeAdjustment)
                            onTimeAdjustment(prayerName, adjustedTime)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Apply",
                            style = typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Material 3 styled circular timer for prayer time adjustment with smooth dragging
 */
@Composable
private fun InteractiveTimeDial(
    originalTime: String,
    adjustmentMinutes: Int,
    prayerName: String,
    onAdjustmentChange: (Int) -> Unit,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var lastHapticValue by remember { mutableIntStateOf(adjustmentMinutes) }
    var isDragging by remember { mutableStateOf(false) }
    
    // Convert adjustment minutes to angle - unlimited rotation
    // Clockwise = positive adjustment, Counter-clockwise = negative adjustment
    val angle = remember(adjustmentMinutes) {
        // Direct angle calculation: 6 degrees per minute (360°/60min = 6°/min)
        -adjustmentMinutes * 6f // Negative for clockwise = positive time
    }
    
    // Only animate when not dragging for smooth interaction
    val animatedAngle by animateFloatAsState(
        targetValue = if (isDragging) angle else angle,
        animationSpec = if (isDragging) snap() else tween(durationMillis = 200),
        label = "angleAnimation"
    )
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Compact center display with prayer name and time
        Surface(
            shape = CircleShape,
            color = colorScheme.surface,
            shadowElevation = 4.dp,
            tonalElevation = 1.dp,
            modifier = Modifier.size(180.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(8.dp)
                ) {
                    // Prayer name at top - larger and theme-colored
                    Text(
                        text = prayerName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    
                    // Time in center - much larger
                    Text(
                        text = convertTo12HourFormat(adjustTimeByMinutes(originalTime, adjustmentMinutes)),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    // Adjustment indicator at bottom - larger and theme-colored
                    if (adjustmentMinutes != 0) {
                        Text(
                            text = if (adjustmentMinutes > 0) "+${adjustmentMinutes}m" else "${adjustmentMinutes}m",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (adjustmentMinutes > 0) {
                                colorScheme.primary // Theme primary for positive
                            } else {
                                colorScheme.error // Theme error for negative
                            },
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var lastAngle = 0f
                    var isDraggingLocal = false
                    var initialAdjustment = 0
                    var totalRotation = 0f
                    
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            isDraggingLocal = true
                            initialAdjustment = adjustmentMinutes
                            totalRotation = 0f
                            
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dragVector = offset - center
                            lastAngle = atan2(dragVector.y, dragVector.x) * (180f / PI.toFloat())
                        },
                        onDragEnd = { 
                            isDragging = false
                            isDraggingLocal = false
                        }
                    ) { change, _ ->
                        if (!isDraggingLocal) return@detectDragGestures
                        
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dragVector = change.position - center
                        val currentAngle = atan2(dragVector.y, dragVector.x) * (180f / PI.toFloat())
                        
                        // Calculate angle difference
                        var angleDiff = currentAngle - lastAngle
                        
                        // Handle wrap-around
                        if (angleDiff > 180f) angleDiff -= 360f
                        if (angleDiff < -180f) angleDiff += 360f
                        
                        // Accumulate total rotation
                        totalRotation += angleDiff
                        
                        // Convert to minutes: 6 degrees = 1 minute
                        // Negative because clockwise should increase time
                        val minuteChange = (-totalRotation / 6f).roundToInt()
                        val newAdjustment = initialAdjustment + minuteChange
                        
                        // Update the adjustment
                        if (newAdjustment != adjustmentMinutes) {
                            onAdjustmentChange(newAdjustment)
                            
                            // Haptic feedback
                            if (abs(newAdjustment - lastHapticValue) >= 5) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastHapticValue = newAdjustment
                            }
                        }
                        
                        lastAngle = currentAngle
                    }
                }
        ) {
            val center = this.center
            val radius = (size.minDimension / 2.0f) * 0.85f
            val strokeWidth = 12.dp.toPx()
            val arcSize = Size(radius * 2, radius * 2)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            
            // Draw the segmented ring structure closer to center
            val outerRingRadius = radius - 10.dp.toPx()
            val innerRingRadius = radius - 25.dp.toPx()
            val segmentCount = 60
            val segmentAngle = 360f / segmentCount
            
            // Draw the ring background first - theme-aware
            drawCircle(
                color = colorScheme.outline.copy(alpha = 0.2f), // Theme-aware background
                radius = outerRingRadius,
                center = center,
                style = Stroke(width = 8.dp.toPx())
            )
            
            // Draw individual rectangular segments around the ring
            for (i in 0 until segmentCount) {
                val segmentAngleRad = (i * segmentAngle) - 90f
                
                // Calculate the segment color based on current progress (unlimited range)
                val progressAngle = (-adjustmentMinutes * 6f + 90f) % 360f
                val segmentAngleNormalized = (segmentAngleRad + 90f + 360f) % 360f
                
                val segmentColor = if (adjustmentMinutes >= 0) {
                    // Positive adjustment - show progress clockwise from top
                    if (segmentAngleNormalized <= progressAngle) {
                        colorScheme.primary.copy(alpha = 0.8f) // More visible primary color
                    } else {
                        colorScheme.outline.copy(alpha = 0.4f) // More visible neutral color
                    }
                } else {
                    // Negative adjustment - show progress counter-clockwise from top
                    if (segmentAngleNormalized >= progressAngle) {
                        colorScheme.error.copy(alpha = 0.8f) // More visible error color
                    } else {
                        colorScheme.outline.copy(alpha = 0.4f) // More visible neutral color
                    }
                }
                
                // Draw each segment as a small rectangle positioned on the ring
                val segmentLength = if (i % 5 == 0) 8.dp.toPx() else 4.dp.toPx()
                val segmentWidth = if (i % 5 == 0) 3.dp.toPx() else 2.dp.toPx()
                
                // Calculate segment position on the ring track  
                val segmentRadius = (outerRingRadius + innerRingRadius) / 2f // Middle of the ring track
                val segmentPosition = center + Offset(
                    x = segmentRadius * cos(Math.toRadians(segmentAngleRad.toDouble())).toFloat(),
                    y = segmentRadius * sin(Math.toRadians(segmentAngleRad.toDouble())).toFloat()
                )
                
                // Draw the segment rectangle rotated to point outward
                val rotationAngle = segmentAngleRad + 90f
                
                drawIntoCanvas { canvas ->
                    canvas.save()
                    canvas.translate(segmentPosition.x, segmentPosition.y)
                    canvas.rotate(rotationAngle)
                    canvas.drawRect(
                        -segmentWidth / 2f,
                        -segmentLength / 2f,
                        segmentWidth / 2f,
                        segmentLength / 2f,
                        androidx.compose.ui.graphics.Paint().apply {
                            color = segmentColor
                            isAntiAlias = true
                        }
                    )
                    canvas.restore()
                }
            }
            
            // Draw inner ring background for time display - theme-aware
            drawCircle(
                color = colorScheme.outline.copy(alpha = 0.1f), // Theme-aware light background
                radius = innerRingRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            
            // Calculate knob position based on current adjustment value for accurate positioning
            val knobAngle = -adjustmentMinutes * 6f // 6 degrees per minute, negative for clockwise
            val knobAngleRad = Math.toRadians(knobAngle - 90.0) // -90 to start from top
            val knobTrackRadius = (outerRingRadius + innerRingRadius) / 2f // Middle of the ring
            val knobPosition = Offset(
                x = center.x + knobTrackRadius * cos(knobAngleRad).toFloat(),
                y = center.y + knobTrackRadius * sin(knobAngleRad).toFloat()
            )
            val knobRadius = 12.dp.toPx() // Slightly larger knob for better interaction
            
            // Draw clean knob with subtle shadow - theme-aware
            // Drop shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.2f),
                radius = knobRadius + 2.dp.toPx(),
                center = knobPosition + Offset(2.dp.toPx(), 2.dp.toPx())
            )
            
            // Main knob body
            drawCircle(
                color = colorScheme.surface,
                radius = knobRadius,
                center = knobPosition
            )
            
            // Knob border
            drawCircle(
                color = colorScheme.outline,
                radius = knobRadius,
                center = knobPosition,
                style = Stroke(width = 2.dp.toPx())
            )
            
            // Draw indicator line pointing outward - theme-colored
            val indicatorLength = knobRadius * 0.7f
            val indicatorEnd = knobPosition + Offset(
                x = indicatorLength * cos(knobAngleRad).toFloat(),
                y = indicatorLength * sin(knobAngleRad).toFloat()
            )
            
            drawLine(
                color = if (adjustmentMinutes >= 0) colorScheme.primary else colorScheme.error,
                start = knobPosition,
                end = indicatorEnd,
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            // Center dot - theme-colored with stronger visibility
            drawCircle(
                color = if (adjustmentMinutes >= 0) colorScheme.primary else colorScheme.error,
                radius = 3.dp.toPx(),
                center = knobPosition
            )
        }
    }
}

/**
 * Helper function to adjust time by minutes
 */
private fun adjustTimeByMinutes(timeString: String, minutesToAdd: Int): String {
    return try {
        val parts = timeString.split(":")
        val hours = parts[0].toInt()
        val minutes = parts[1].toInt()
        
        val totalMinutes = hours * 60 + minutes + minutesToAdd
        val adjustedHours = ((totalMinutes / 60) % 24 + 24) % 24 // Handle negative hours
        val adjustedMinutes = ((totalMinutes % 60) + 60) % 60 // Handle negative minutes
        
        String.format("%02d:%02d", adjustedHours, adjustedMinutes)
    } catch (e: Exception) {
        timeString // Return original time if parsing fails
    }
}

/**
 * Helper function to convert 24-hour format to 12-hour format
 */
private fun convertTo12HourFormat(timeString: String): String {
    return try {
        val parts = timeString.split(":")
        val hours = parts[0].toInt()
        val minutes = parts[1].toInt()
        
        val displayHours = when {
            hours == 0 -> 12
            hours > 12 -> hours - 12
            else -> hours
        }
        
        val amPm = if (hours < 12) "AM" else "PM"
        String.format("%d:%02d %s", displayHours, minutes, amPm)
    } catch (e: Exception) {
        timeString // Return original time if parsing fails
    }
}