package com.starception.submission.feature.prayertimes.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
    onTimeAdjustment: (String, Int) -> Unit, // prayer name, offset in minutes
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
        // More compact popup window with less empty space
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.75f),
            shape = RoundedCornerShape(20.dp),
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
                
                // Compact content area with less padding
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Compact interactive dial
                    InteractivePrayerDial(
                        originalTime = originalTime,
                        timeAdjustment = timeAdjustment,
                        prayerName = prayerName,
                        onAdjustmentChange = { adjustment ->
                            if (adjustment != timeAdjustment) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                timeAdjustment = adjustment
                            }
                        },
                        modifier = Modifier.size(260.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Compact action buttons moved inside column
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { onCancel() },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            border = BorderStroke(1.dp, colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Cancel",
                                style = typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Button(
                            onClick = { 
                                android.util.Log.d("InteractivePrayerDial", "🔘 APPLY BUTTON CLICKED:")
                                android.util.Log.d("InteractivePrayerDial", "   📝 Prayer: $prayerName")
                                android.util.Log.d("InteractivePrayerDial", "   ⏰ Original Time: $originalTime")
                                android.util.Log.d("InteractivePrayerDial", "   ⏱️ Adjustment: $timeAdjustment minutes")
                                android.util.Log.d("InteractivePrayerDial", "   🎯 Calling onTimeAdjustment callback...")
                                onTimeAdjustment(prayerName, timeAdjustment)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Apply",
                                style = typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onPrimary
                            )
                        }
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
fun InteractivePrayerDial(
    originalTime: String,
    timeAdjustment: Int,
    prayerName: String,
    onAdjustmentChange: (Int) -> Unit,
    onSave: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var lastHapticValue by remember { mutableIntStateOf(timeAdjustment) }
    var isDragging by remember { mutableStateOf(false) }
    
    // Track current adjustment locally for save operations
    var currentAdjustment by remember { mutableIntStateOf(timeAdjustment) }
    
    // Update local adjustment when external timeAdjustment changes
    LaunchedEffect(timeAdjustment) {
        currentAdjustment = timeAdjustment
        android.util.Log.v("InteractivePrayerDial", "📥 EXTERNAL UPDATE: $prayerName adjustment updated to $timeAdjustment")
    }
    
    // Convert adjustment minutes to angle - unlimited rotation
    // Clockwise = positive adjustment, Counter-clockwise = negative adjustment
    val angle = remember(timeAdjustment) {
        // Direct angle calculation: 6 degrees per minute (standard timer)
        timeAdjustment * 6f // Positive for clockwise = positive time
    }
    
    // Live feedback during dragging - no animation when dragging for immediate response
    val displayAngle = if (isDragging) angle else angle // Always show current angle for live feedback
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Clean center background circle for text overlay
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 2.dp,
            modifier = Modifier.size(140.dp)
        ) {
            // Empty content - just provides background for text overlay
        }
        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Long press detection for saving  
                    detectTapGestures(
                        onLongPress = {
                            android.util.Log.d("InteractivePrayerDial", "👆 LONG PRESS SAVE:")
                            android.util.Log.d("InteractivePrayerDial", "   📝 Prayer: $prayerName")
                            android.util.Log.d("InteractivePrayerDial", "   ⏱️ Current Local Adjustment: $currentAdjustment minutes")
                            android.util.Log.d("InteractivePrayerDial", "   📊 Parameter Adjustment: $timeAdjustment minutes")
                            android.util.Log.d("InteractivePrayerDial", "   💾 Calling onSave callback with: $currentAdjustment")
                            onSave?.let { 
                                it(currentAdjustment)
                                android.util.Log.d("InteractivePrayerDial", "   ✅ onSave callback completed")
                            } ?: android.util.Log.w("InteractivePrayerDial", "   ⚠️ onSave callback is null - save not performed")
                        }
                    )
                }
                .pointerInput(Unit) {
                    var lastAngle = 0f
                    var isDraggingLocal = false
                    var initialAdjustment = 0
                    var totalRotation = 0f
                    
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDraggingLocal = true
                            initialAdjustment = currentAdjustment
                            totalRotation = 0f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            lastAngle = kotlin.math.atan2(
                                offset.y - center.y,
                                offset.x - center.x
                            ) * 180f / kotlin.math.PI.toFloat()
                            android.util.Log.d("InteractivePrayerDial", "🎯 DRAG START: $prayerName, initial adjustment: $initialAdjustment")
                        },
                        onDragEnd = { 
                            isDraggingLocal = false
                        }
                    ) { change, _ ->
                        if (!isDraggingLocal) return@detectDragGestures
                        
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val currentAngle = kotlin.math.atan2(
                            change.position.y - center.y,
                            change.position.x - center.x
                        ) * 180f / kotlin.math.PI.toFloat()
                        
                        var deltaAngle = currentAngle - lastAngle
                        if (deltaAngle > 180f) deltaAngle -= 360f
                        if (deltaAngle < -180f) deltaAngle += 360f
                        
                        totalRotation += deltaAngle
                        
                        // Convert rotation to minute adjustment (6 degrees per minute)
                        val minuteChange = (totalRotation / 6f).toInt()
                        val newAdjustment = initialAdjustment + minuteChange
                        
                        if (newAdjustment != currentAdjustment) {
                            android.util.Log.v("InteractivePrayerDial", "🔄 DRAGGING: $prayerName adjustment changed from $currentAdjustment to $newAdjustment minutes")
                            currentAdjustment = newAdjustment  // Update local state
                            onAdjustmentChange(newAdjustment)   // Notify parent
                            
                            if (kotlin.math.abs(newAdjustment - lastHapticValue) >= 3) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastHapticValue = newAdjustment
                                android.util.Log.v("InteractivePrayerDial", "📳 HAPTIC FEEDBACK: $prayerName at $newAdjustment minutes")
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
            
            // PNG File Icon Aesthetic - Document style with folded corner
            val outerRadius = radius * 0.95f
            val trackRadius = outerRadius * 0.85f
            
            // Drop shadow like PNG file icon
            drawCircle(
                color = Color.Black.copy(alpha = 0.12f),
                radius = outerRadius + 3.dp.toPx(),
                center = center + Offset(2.dp.toPx(), 3.dp.toPx())
            )
            
            // Main document background - clean white like PNG icon
            drawCircle(
                color = Color.White,
                radius = outerRadius,
                center = center
            )
            
            // Subtle document border for definition
            drawCircle(
                color = Color(0xFFE8E8E8),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 0.5.dp.toPx())
            )
            
            // Folded corner effect (top-right quadrant)
            val foldSize = outerRadius * 0.2f
            val foldCenter = center + Offset(outerRadius * 0.7f, -outerRadius * 0.7f)
            
            // Folded corner shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.06f),
                radius = foldSize,
                center = foldCenter + Offset(1.dp.toPx(), 1.dp.toPx())
            )
            
            // Folded corner highlight
            drawCircle(
                color = Color(0xFFF5F5F5),
                radius = foldSize,
                center = foldCenter
            )
            
            // Properly aligned tick marks - 60 evenly spaced
            val tickCount = 60
            val tickAngle = 360f / tickCount
            val tickTrackRadius = trackRadius - 2.dp.toPx() // Align with progress track
            
            for (i in 0 until tickCount) {
                val currentTickAngle = (i * tickAngle) - 90f // Start from top (12 o'clock)
                val angleRad = Math.toRadians(currentTickAngle.toDouble())
                
                // Different sizes for major/minor ticks
                val isMajorTick = i % 5 == 0
                val tickLength = if (isMajorTick) 10.dp.toPx() else 6.dp.toPx()
                val tickWidth = if (isMajorTick) 2.dp.toPx() else 1.dp.toPx()
                
                // Align ticks properly with the progress track
                val tickInnerRadius = tickTrackRadius - tickLength / 2f
                val tickOuterRadius = tickTrackRadius + tickLength / 2f
                
                val tickStart = center + Offset(
                    x = tickInnerRadius * cos(angleRad).toFloat(),
                    y = tickInnerRadius * sin(angleRad).toFloat()
                )
                val tickEnd = center + Offset(
                    x = tickOuterRadius * cos(angleRad).toFloat(),
                    y = tickOuterRadius * sin(angleRad).toFloat()
                )
                
                // Clean aligned tick marks
                drawLine(
                    color = Color(0xFFD1D5DB),
                    start = tickStart,
                    end = tickEnd,
                    strokeWidth = tickWidth,
                    cap = StrokeCap.Round
                )
            }
            
            // Live progress arc with immediate feedback during dragging
            if (timeAdjustment != 0) {
                val progressAngle = abs(displayAngle) // Use live display angle for immediate feedback
                val startAngle = -90f // Start from top (12 o'clock)
                val progressRadius = tickTrackRadius // Same radius as tick marks for perfect alignment
                
                // Outer glow effect - updates live during drag
                drawArc(
                    color = Color(0xFF10B981).copy(alpha = 0.25f), // Slightly more visible during drag
                    startAngle = startAngle,
                    sweepAngle = if (timeAdjustment > 0) progressAngle else -progressAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - progressRadius, center.y - progressRadius),
                    size = Size(progressRadius * 2, progressRadius * 2),
                    style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                )
                
                // Main progress arc - live feedback
                drawArc(
                    color = Color(0xFF10B981), // Professional teal-green
                    startAngle = startAngle,
                    sweepAngle = if (timeAdjustment > 0) progressAngle else -progressAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - progressRadius, center.y - progressRadius),
                    size = Size(progressRadius * 2, progressRadius * 2),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
                
                // Live knob positioning - follows drag immediately
                val knobAngle = displayAngle
                val knobAngleRad = Math.toRadians(knobAngle - 90.0) // Start from top
                val knobPosition = Offset(
                    x = center.x + progressRadius * cos(knobAngleRad).toFloat(),
                    y = center.y + progressRadius * sin(knobAngleRad).toFloat()
                )
                
                // Enhanced knob for better visibility during drag
                drawCircle(
                    color = Color.Black.copy(alpha = if (isDragging) 0.15f else 0.1f),
                    radius = if (isDragging) 8.dp.toPx() else 7.dp.toPx(),
                    center = knobPosition + Offset(0.5.dp.toPx(), 1.dp.toPx())
                )
                
                drawCircle(
                    color = Color(0xFF10B981),
                    radius = if (isDragging) 7.dp.toPx() else 6.dp.toPx(), // Slightly larger when dragging
                    center = knobPosition
                )
                
                // Inner highlight on knob
                drawCircle(
                    color = Color.White.copy(alpha = 0.4f),
                    radius = if (isDragging) 4.dp.toPx() else 3.dp.toPx(),
                    center = knobPosition - Offset(1.dp.toPx(), 1.dp.toPx())
                )
            }
            
            // Large professional center circle
            val centerRadius = trackRadius * 0.65f
            
            // Center shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.04f),
                radius = centerRadius + 1.dp.toPx(),
                center = center + Offset(0.5.dp.toPx(), 1.dp.toPx())
            )
            
            // Clean white center
            drawCircle(
                color = Color.White,
                radius = centerRadius,
                center = center
            )
            
            // Professional border
            drawCircle(
                color = Color(0xFFE5E7EB),
                radius = centerRadius,
                center = center,
                style = Stroke(width = 0.5.dp.toPx())
            )
        }
        
        // Prayer name and offset display in center
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Prayer name
                Text(
                    text = prayerName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF666666), // Medium gray
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Adjusted time display
                val adjustedTime = convertTo12HourFormat(adjustTimeByMinutes(originalTime, timeAdjustment))
                Text(
                    text = adjustedTime,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF333333), // Dark gray like reference
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp, // Slightly smaller to fit with prayer name
                    letterSpacing = (-0.5).sp,
                    textAlign = TextAlign.Center
                )
                
                // Offset display
                if (timeAdjustment != 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (timeAdjustment > 0) "+${timeAdjustment}m" else "${timeAdjustment}m",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF10B981), // Teal color matching the progress arc
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Helper function to adjust time by minutes
 */
fun adjustTimeByMinutes(timeString: String, minutesToAdd: Int): String {
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
fun convertTo12HourFormat(timeString: String): String {
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
