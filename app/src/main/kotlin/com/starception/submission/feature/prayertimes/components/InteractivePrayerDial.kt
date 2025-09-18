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
        // Main card with asymmetrical Material 3 shape
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(
                topStart = 28.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 28.dp
            ),
            color = colorScheme.surface,
            shadowElevation = 12.dp,
            tonalElevation = 2.dp
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
                
                // Main content area with proper spacing
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Prayer name with Material 3 typography and styling
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colorScheme.primaryContainer,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = prayerName,
                            style = typography.headlineMedium,
                            color = colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                    
                    // Material 3 styled interactive dial
                    InteractiveTimeDial(
                        originalTime = originalTime,
                        adjustmentMinutes = timeAdjustment,
                        onAdjustmentChange = { adjustment ->
                            if (adjustment != timeAdjustment) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                timeAdjustment = adjustment
                            }
                        },
                        colorScheme = colorScheme,
                        modifier = Modifier
                            .size(280.dp)
                            .padding(vertical = 16.dp)
                    )
                    
                    // Instruction text when no adjustment
                    if (timeAdjustment == 0) {
                        Text(
                            text = "Drag to adjust time",
                            style = typography.titleMedium,
                            color = colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
                        )
                    } else {
                        // Spacer when adjustment is shown inside dial
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                    
                    // Spacer to push buttons to bottom
                    Spacer(modifier = Modifier.weight(1f))
                }
                
                // Material 3 decorative accent (top-right)
                Surface(
                    shape = RoundedCornerShape(0.dp, 16.dp, 0.dp, 20.dp),
                    color = colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier
                        .size(60.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                // Material 3 action buttons at bottom - properly positioned
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 28.dp, vertical = 24.dp)
                ) {
                    OutlinedButton(
                        onClick = { onCancel() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        border = BorderStroke(1.5.dp, colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            "Cancel",
                            style = typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Button(
                        onClick = { 
                            val adjustedTime = adjustTimeByMinutes(originalTime, timeAdjustment)
                            onTimeAdjustment(prayerName, adjustedTime)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            "Apply",
                            style = typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
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
    onAdjustmentChange: (Int) -> Unit,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var lastHapticValue by remember { mutableIntStateOf(adjustmentMinutes) }
    var isDragging by remember { mutableStateOf(false) }
    
    // Convert adjustment minutes to angle (0-360 degrees, max ±60 minutes for more intuitive range)
    val maxAdjustmentMinutes = 60f
    val angle = remember(adjustmentMinutes) {
        ((adjustmentMinutes + maxAdjustmentMinutes) / (maxAdjustmentMinutes * 2)) * 360f
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
        // Material 3 time display in center with elevated surface
        Surface(
            shape = CircleShape,
            color = colorScheme.surface,
            shadowElevation = 6.dp,
            tonalElevation = 1.dp,
            modifier = Modifier.size(150.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = convertTo12HourFormat(adjustTimeByMinutes(originalTime, adjustmentMinutes)),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    // Show adjustment indicator inside the dial
                    if (adjustmentMinutes != 0) {
                        Text(
                            text = if (adjustmentMinutes > 0) "+${adjustmentMinutes} min" else "${adjustmentMinutes} min",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (adjustmentMinutes > 0) {
                                Color(0xFF00D4AA) // Teal for positive
                            } else {
                                Color(0xFFE74C3C) // Red for negative
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
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dragVector = offset - center
                            val touchAngle = (atan2(dragVector.y, dragVector.x) * (180f / PI.toFloat()) + 450f) % 360f
                            
                            // Convert angle back to adjustment minutes with more intuitive range
                            val newAdjustmentMinutes = ((touchAngle / 360f) * (maxAdjustmentMinutes * 2) - maxAdjustmentMinutes)
                                .roundToInt()
                                .coerceIn(-60, 60)
                            
                            onAdjustmentChange(newAdjustmentMinutes)
                        },
                        onDragEnd = { 
                            isDragging = false
                        },
                    ) { change, _ ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dragVector = change.position - center
                        val touchAngle = (atan2(dragVector.y, dragVector.x) * (180f / PI.toFloat()) + 450f) % 360f
                        
                        // Convert angle back to adjustment minutes with more intuitive range
                        val newAdjustmentMinutes = ((touchAngle / 360f) * (maxAdjustmentMinutes * 2) - maxAdjustmentMinutes)
                            .roundToInt()
                            .coerceIn(-60, 60)
                        
                        // Provide haptic feedback every 5 minutes for better UX
                        if (abs(newAdjustmentMinutes - lastHapticValue) >= 5) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastHapticValue = newAdjustmentMinutes
                        }
                        
                        onAdjustmentChange(newAdjustmentMinutes)
                    }
                }
        ) {
            val center = this.center
            val radius = (size.minDimension / 2.0f) * 0.85f
            val strokeWidth = 12.dp.toPx()
            val arcSize = Size(radius * 2, radius * 2)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            
            // Draw the segmented ring structure exactly like the reference
            val outerRingRadius = radius + 20.dp.toPx()
            val innerRingRadius = radius + 6.dp.toPx()
            val segmentCount = 60
            val segmentAngle = 360f / segmentCount
            
            // Draw the ring background first
            drawCircle(
                color = Color(0xFFE8E8E8), // Light gray ring background
                radius = outerRingRadius,
                center = center,
                style = Stroke(width = 8.dp.toPx())
            )
            
            // Draw individual rectangular segments around the ring
            for (i in 0 until segmentCount) {
                val segmentAngleRad = (i * segmentAngle) - 90f
                
                // Calculate the segment color based on progress
                val segmentColor = if (i < (adjustmentMinutes + 60) * segmentCount / 120) {
                    Color(0xFF00D4AA) // Vibrant teal/aqua from the image
                } else {
                    Color(0xFFE8E8E8) // Light gray for inactive segments
                }
                
                // Draw each segment as a small rectangle positioned on the ring
                val segmentLength = if (i % 5 == 0) 8.dp.toPx() else 4.dp.toPx()
                val segmentWidth = if (i % 5 == 0) 3.dp.toPx() else 2.dp.toPx()
                
                // Calculate segment position on the ring
                val segmentRadius = outerRingRadius - 4.dp.toPx() // Position within the ring
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
            
            // Draw inner ring background for time display
            drawCircle(
                color = Color(0xFFF5F5F5), // Light background for time display
                radius = innerRingRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            
            // Calculate knob position based on current angle (not animated for smooth drag)
            val currentKnobAngle = if (isDragging) angle else animatedAngle
            val knobAngleRad = Math.toRadians(currentKnobAngle - 90.0)
            val knobPosition = Offset(
                x = center.x + innerRingRadius * cos(knobAngleRad).toFloat(),
                y = center.y + innerRingRadius * sin(knobAngleRad).toFloat()
            )
            val knobRadius = 10.dp.toPx() // Smaller knob like in the image
            
            // Draw clean knob with subtle shadow
            // Drop shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.15f),
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
                style = Stroke(width = 1.5.dp.toPx())
            )
            
            // Draw indicator line pointing outward (like in the image)
            val indicatorLength = knobRadius * 0.6f
            val indicatorEnd = knobPosition + Offset(
                x = indicatorLength * cos(knobAngleRad).toFloat(),
                y = indicatorLength * sin(knobAngleRad).toFloat()
            )
            
            drawLine(
                color = Color(0xFF00D4AA), // Same teal color as segments
                start = knobPosition,
                end = indicatorEnd,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            // Center dot
            drawCircle(
                color = Color(0xFF00D4AA), // Same teal color as segments
                radius = 2.dp.toPx(),
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