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
                    
                    // Material 3 adjustment indicator
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (timeAdjustment != 0) {
                            when {
                                timeAdjustment > 0 -> colorScheme.tertiaryContainer
                                else -> colorScheme.errorContainer
                            }
                        } else {
                            colorScheme.surfaceVariant
                        },
                        modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
                    ) {
                        Text(
                            text = if (timeAdjustment != 0) {
                                if (timeAdjustment > 0) "+${timeAdjustment} min" else "${timeAdjustment} min"
                            } else {
                                "Drag to adjust time"
                            },
                            style = typography.titleMedium,
                            color = if (timeAdjustment != 0) {
                                when {
                                    timeAdjustment > 0 -> colorScheme.onTertiaryContainer
                                    else -> colorScheme.onErrorContainer
                                }
                            } else {
                                colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
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
                Text(
                    text = adjustTimeByMinutes(originalTime, adjustmentMinutes),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall
                )
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
            
            // Draw clean circular track background
            drawCircle(
                color = colorScheme.surfaceVariant,
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth)
            )
            
            // Draw progress arc based on adjustment
            val progressAngle = (adjustmentMinutes + 60f) / 120f * 360f
            val progressColor = when {
                adjustmentMinutes > 20 -> colorScheme.tertiary
                adjustmentMinutes > 0 -> colorScheme.primary  
                adjustmentMinutes < -20 -> colorScheme.error
                adjustmentMinutes < 0 -> colorScheme.secondary
                else -> colorScheme.primary
            }
            
            if (adjustmentMinutes != 0) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = progressAngle - 180f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = arcSize,
                    topLeft = arcTopLeft
                )
            }
            
            // Draw clean hour markers (12 positions)
            for (i in 0 until 12) {
                val tickAngle = (i * 30f) - 90f
                val tickRadius = radius + 4.dp.toPx()
                val tickPosition = center + Offset(
                    x = tickRadius * cos(Math.toRadians(tickAngle.toDouble())).toFloat(),
                    y = tickRadius * sin(Math.toRadians(tickAngle.toDouble())).toFloat()
                )
                
                // Draw tick marks as small rectangles
                val tickLength = 6.dp.toPx()
                val tickWidth = 2.dp.toPx()
                val tickRect = androidx.compose.ui.geometry.Rect(
                    center = tickPosition,
                    radius = tickWidth / 2f
                )
                
                drawRect(
                    color = colorScheme.outline.copy(alpha = 0.7f),
                    topLeft = Offset(tickPosition.x - tickWidth/2, tickPosition.y - tickLength/2),
                    size = androidx.compose.ui.geometry.Size(tickWidth, tickLength)
                )
            }
            
            // Calculate knob position based on current angle (not animated for smooth drag)
            val currentKnobAngle = if (isDragging) angle else animatedAngle
            val knobAngleRad = Math.toRadians(currentKnobAngle - 90.0)
            val knobPosition = Offset(
                x = center.x + radius * cos(knobAngleRad).toFloat(),
                y = center.y + radius * sin(knobAngleRad).toFloat()
            )
            val knobRadius = 16.dp.toPx()
            
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
            
            // Simple center dot indicator
            drawCircle(
                color = colorScheme.primary,
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