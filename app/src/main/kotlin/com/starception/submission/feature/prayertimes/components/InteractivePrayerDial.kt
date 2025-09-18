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
 * PNG File-style card with interactive dial for prayer time adjustment
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
    
    // Professional, clean colors inspired by PNG file icons
    val BackgroundWhite = Color(0xFFFFFFFF)
    val ShadowGray = Color(0xFFE0E6ED)
    val BorderGray = Color(0xFFD1D9E6)
    val TextDark = Color(0xFF2C3E50)
    val AccentBlue = Color(0xFF3498DB)
    val ProgressGreen = Color(0xFF27AE60)
    val WarningOrange = Color(0xFFE67E22)
    val KnobSilver = Color(0xFFBDC3C7)
    val KnobHighlight = Color(0xFFECF0F1)
    
    // File icon style interface - clean white document design
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA)) // Very light background
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Document-style card with folded corner (like PNG/JPEG file icons)
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Main content area
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Prayer name at top
                    Text(
                        text = prayerName,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color(0xFF6B7280),
                        fontWeight = FontWeight.Medium
                    )
                    
                    // Clean, minimal dial
                    InteractiveTimeDial(
                        originalTime = originalTime,
                        adjustmentMinutes = timeAdjustment,
                        onAdjustmentChange = { adjustment ->
                            if (adjustment != timeAdjustment) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                timeAdjustment = adjustment
                            }
                        },
                        backgroundWhite = BackgroundWhite,
                        shadowGray = ShadowGray,
                        borderGray = BorderGray,
                        textDark = TextDark,
                        accentBlue = AccentBlue,
                        progressGreen = ProgressGreen,
                        warningOrange = WarningOrange,
                        knobSilver = KnobSilver,
                        knobHighlight = KnobHighlight,
                        modifier = Modifier
                            .size(280.dp)
                            .padding(vertical = 16.dp)
                    )
                    
                    // File-style adjustment text at bottom
                    Text(
                        text = if (timeAdjustment != 0) {
                            if (timeAdjustment > 0) "+${timeAdjustment} min" else "${timeAdjustment} min"
                        } else {
                            "Drag to adjust"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFF6B7280),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Folded corner effect (top-right)
                Canvas(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.TopEnd)
                ) {
                    val cornerSize = size.width * 0.8f
                    drawPath(
                        path = Path().apply {
                            moveTo(size.width - cornerSize, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width, cornerSize)
                            close()
                        },
                        color = Color(0xFFE5E7EB), // Light gray for fold
                    )
                    drawPath(
                        path = Path().apply {
                            moveTo(size.width - cornerSize, 0f)
                            lineTo(size.width - cornerSize * 0.3f, cornerSize * 0.3f)
                            lineTo(size.width, cornerSize)
                            close()
                        },
                        color = Color(0xFFD1D5DB), // Darker gray for shadow
                    )
                }
                
                // Action buttons at bottom
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                ) {
                    OutlinedButton(
                        onClick = { onCancel() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        border = BorderStroke(1.dp, Color(0xFFD1D5DB)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF6B7280)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Cancel",
                            style = MaterialTheme.typography.titleMedium,
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
                            containerColor = Color(0xFF6B7280)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Apply",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Beautiful circular timer for prayer time adjustment with smooth dragging
 */
@Composable
private fun InteractiveTimeDial(
    originalTime: String,
    adjustmentMinutes: Int,
    onAdjustmentChange: (Int) -> Unit,
    backgroundWhite: Color,
    shadowGray: Color,
    borderGray: Color,
    textDark: Color,
    accentBlue: Color,
    progressGreen: Color,
    warningOrange: Color,
    knobSilver: Color,
    knobHighlight: Color,
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
        // Professional time display in center with clean background
        Surface(
            shape = CircleShape,
            color = backgroundWhite,
            shadowElevation = 4.dp,
            modifier = Modifier.size(140.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = adjustTimeByMinutes(originalTime, adjustmentMinutes),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textDark,
                    textAlign = TextAlign.Center
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
            
            // Draw professional outer ring with shadow effect
            drawCircle(
                color = shadowGray,
                radius = radius + 8.dp.toPx(),
                center = center + Offset(4.dp.toPx(), 4.dp.toPx())
            )
            
            // Draw main track background 
            drawCircle(
                color = backgroundWhite,
                radius = radius + 6.dp.toPx(),
                center = center,
                style = Stroke(width = strokeWidth + 12.dp.toPx())
            )
            
            drawCircle(
                color = borderGray,
                radius = radius + 6.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            
            // Draw progress track
            drawCircle(
                color = Color(0xFFF8F9FA),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth)
            )
            
            // Draw progress arc based on adjustment
            val progressAngle = (adjustmentMinutes + 60f) / 120f * 360f
            val progressColor = when {
                adjustmentMinutes > 20 -> warningOrange
                adjustmentMinutes > 0 -> progressGreen  
                adjustmentMinutes < -20 -> Color(0xFFE74C3C) // Red for significant negative
                adjustmentMinutes < 0 -> warningOrange
                else -> accentBlue
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
            
            // Draw minimal hour markers  
            for (i in 0 until 12) {
                val tickAngle = (i * 30f) - 90f
                val tickRadius = radius + 3.dp.toPx()
                val tickPosition = center + Offset(
                    x = tickRadius * cos(Math.toRadians(tickAngle.toDouble())).toFloat(),
                    y = tickRadius * sin(Math.toRadians(tickAngle.toDouble())).toFloat()
                )
                
                drawCircle(
                    color = borderGray,
                    radius = 2.dp.toPx(),
                    center = tickPosition
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
            
            // Draw professional knob with proper shadow and highlight
            // Drop shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.15f),
                radius = knobRadius + 2.dp.toPx(),
                center = knobPosition + Offset(3.dp.toPx(), 3.dp.toPx())
            )
            
            // Main knob body
            drawCircle(
                color = backgroundWhite,
                radius = knobRadius,
                center = knobPosition
            )
            
            // Knob border
            drawCircle(
                color = borderGray,
                radius = knobRadius,
                center = knobPosition,
                style = Stroke(width = 2.dp.toPx())
            )
            
            // Inner circle with metallic appearance
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        knobHighlight,
                        knobSilver,
                        borderGray
                    ),
                    center = knobPosition - Offset(knobRadius * 0.3f, knobRadius * 0.3f),
                    radius = knobRadius * 0.7f
                ),
                radius = knobRadius * 0.6f,
                center = knobPosition
            )
            
            // Top highlight for 3D effect
            drawCircle(
                color = Color.White.copy(alpha = 0.7f),
                radius = knobRadius * 0.3f,
                center = knobPosition - Offset(knobRadius * 0.25f, knobRadius * 0.25f)
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