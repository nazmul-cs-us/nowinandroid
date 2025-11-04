package com.starception.submission.feature.prayertimes.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.clip
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
    // CRITICAL LOGGING: Track when timeAdjustment parameter changes
    LaunchedEffect(timeAdjustment) {
        Log.w("InteractiveDial", "⚠️ PARAMETER CHANGE DETECTED - Prayer: $prayerName")
        Log.w("InteractiveDial", "   📥 NEW timeAdjustment value: $timeAdjustment minutes")
        Log.w("InteractiveDial", "   🔍 This indicates parent recomposed with new value")
        Log.w("InteractiveDial", "   📍 Stack trace: ${Thread.currentThread().stackTrace.take(5).joinToString("\n      ")}")
    }

    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    var lastAngle by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var accumulatedAngle by remember { mutableStateOf(0f) }
    var baseAdjustment by remember { mutableStateOf(timeAdjustment) }
    var currentDragAngle by remember { mutableStateOf(0f) }
    var lastHapticAdjustment by remember { mutableStateOf(timeAdjustment) }

    // Track the current adjustment value internally (survives after drag ends)
    var currentAdjustment by remember { mutableStateOf(timeAdjustment) }

    // Log whenever currentAdjustment changes
    LaunchedEffect(currentAdjustment) {
        Log.d("InteractiveDial", "🔄 INTERNAL STATE - Prayer: $prayerName, currentAdjustment: $currentAdjustment minutes")
    }

    // Material 3 expressive animations for enhanced feedback
    val dialScale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "dialScale"
    )

    val knobScale by animateFloatAsState(
        targetValue = if (isDragging) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "knobScale"
    )

    val progressArcGlow by animateFloatAsState(
        targetValue = if (isDragging) 1.5f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "progressArcGlow"
    )

    // Get Material 3 theme colors for dark/light mode support
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outline

    // Initialize from timeAdjustment only once when component is first created
    LaunchedEffect(Unit) {
        currentAdjustment = timeAdjustment
        baseAdjustment = timeAdjustment
    }

    Box(
        modifier = modifier
            .aspectRatio(1f) // Perfect circle - this should constrain the size
            .graphicsLayer {
                scaleX = dialScale
                scaleY = dialScale
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize() // Use matchParentSize instead of fillMaxSize for better centering
                .graphicsLayer {
                    // Ensure proper centering - no translation
                    translationX = 0f
                    translationY = 0f
                }
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
                            Log.d("InteractiveDial", "🏁 DRAG END - Prayer: $prayerName, Final adjustment: ${currentAdjustment}m")
                            Log.d("InteractiveDial", "📊 Final accumulated angle: ${accumulatedAngle}°")
                            Log.d("InteractiveDial", "🔵 Drag ended - Save button will appear for user to confirm")
                            isDragging = false

                            // Light haptic feedback to indicate drag ended
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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

                            if (newAdjustment != currentAdjustment) {
                                Log.d("InteractiveDial", "✅ ADJUSTMENT APPLIED - Old: ${currentAdjustment}m → New: ${newAdjustment}m")

                                // Update internal state
                                currentAdjustment = newAdjustment

                                // Strong haptic feedback for every minute change
                                if (newAdjustment != lastHapticAdjustment) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    lastHapticAdjustment = newAdjustment
                                    Log.d("InteractiveDial", "💥 STRONG HAPTIC FEEDBACK - Adjustment: ${newAdjustment}m")
                                }

                                // Notify parent of the change (for real-time UI update)
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
            // Ensure perfect centering - use exact center of canvas
            // Use the actual center of the drawScope (which is already centered)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = kotlin.math.min(size.width, size.height) * 0.4f
            
            // Double-check that center is truly at the middle
            // This ensures the dial is perfectly centered regardless of canvas size
            
            // Use Material 3 theme colors passed from Composable
            
            // Draw clean circular timer design with theme colors
            drawCleanCircularTimer(
                center = center,
                radius = radius,
                timeAdjustment = currentAdjustment,
                originalTime = originalTime,
                isDragging = isDragging,
                currentDragAngle = currentDragAngle,
                surfaceColor = surfaceColor,
                onSurfaceColor = onSurfaceColor,
                outlineColor = outlineColor,
                knobScale = knobScale,
                progressArcGlow = progressArcGlow
            )
        }

        // Central text container - clean and minimal like reference
        var swipeOffset by remember { mutableStateOf(0f) }
        var isSwiping by remember { mutableStateOf(false) }

        // Animated swipe progress
        val swipeProgress by animateFloatAsState(
            targetValue = when {
                swipeOffset > 0 -> (swipeOffset / 100f).coerceIn(0f, 1f)
                swipeOffset < 0 -> (kotlin.math.abs(swipeOffset) / 100f).coerceIn(0f, 1f)
                else -> 0f
            },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessHigh
            ),
            label = "swipeProgress"
        )

        Box(
            modifier = Modifier
                .size(200.dp)
                .background(
                    color = Color.Transparent,
                    shape = CircleShape
                )
                .pointerInput(baseAdjustment, currentAdjustment) {
                    // Detect horizontal swipes in the center (not on the knob)
                    detectDragGestures(
                        onDragStart = {
                            swipeOffset = 0f
                            isSwiping = true
                        },
                        onDragEnd = {
                            val hasAdjusted = currentAdjustment != baseAdjustment
                            if (hasAdjusted) {
                                if (swipeOffset > 100f) {
                                    // Swipe right - Save
                                    Log.d("InteractiveDial", "→ SWIPE RIGHT - SAVE - Prayer: $prayerName, Adjustment: ${currentAdjustment}m, Offset: ${swipeOffset}px")
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSaveAdjustment(prayerName, currentAdjustment)
                                } else if (swipeOffset < -100f) {
                                    // Swipe left - Undo
                                    Log.d("InteractiveDial", "← SWIPE LEFT - UNDO - Prayer: $prayerName, Offset: ${swipeOffset}px")
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentAdjustment = baseAdjustment
                                    onTimeAdjusted(baseAdjustment)
                                    onSaveAdjustment(prayerName, baseAdjustment) // Close dial
                                }
                            }
                            isSwiping = false
                            swipeOffset = 0f
                        }
                    ) { change, dragAmount ->
                        // Accumulate horizontal movement
                        swipeOffset += dragAmount.x
                        change.consume()
                    }
                }
                .pointerInput(baseAdjustment, currentAdjustment) {
                    // Detect tap to close when no adjustment has been made
                    detectTapGestures(
                        onTap = {
                            val hasAdjusted = currentAdjustment != baseAdjustment
                            if (!hasAdjusted) {
                                // No adjustment made - just close the dial
                                Log.d("InteractiveDial", "👆 TAP TO CLOSE - Prayer: $prayerName, No adjustment made")
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSaveAdjustment(prayerName, baseAdjustment) // Close dial
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Swipe indicators - left (undo) and right (save)
            if (isSwiping && currentAdjustment != baseAdjustment) {
                // Left swipe indicator (Undo)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (-30).dp)
                        .graphicsLayer {
                            alpha = if (swipeOffset < 0) swipeProgress else 0f
                            scaleX = if (swipeOffset < 0) swipeProgress else 0.5f
                            scaleY = if (swipeOffset < 0) swipeProgress else 0.5f
                        }
                ) {
                    Text(
                        text = "↶",
                        fontSize = 32.sp,
                        color = Color(0xFFFF9800), // Orange for undo
                        fontWeight = FontWeight.Bold
                    )
                }

                // Right swipe indicator (Save)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 30.dp)
                        .graphicsLayer {
                            alpha = if (swipeOffset > 0) swipeProgress else 0f
                            scaleX = if (swipeOffset > 0) swipeProgress else 0.5f
                            scaleY = if (swipeOffset > 0) swipeProgress else 0.5f
                        }
                ) {
                    Text(
                        text = "✓",
                        fontSize = 36.sp,
                        color = Color(0xFF4CAF50), // Green for save
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Prayer name display at the top
                Text(
                    text = prayerName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = Color(0xFF26C6DA), // Teal color to match the theme
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Large time display in 00:00 format like reference
                val adjustedTime = adjustTimeByMinutesForDisplay(originalTime, currentAdjustment)
                Text(
                    text = adjustedTime,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 44.sp,
                        letterSpacing = (-1.sp) // Tight spacing for digital clock look
                    ),
                    color = Color(0xFF37474F), // Dark blue-gray for better contrast
                    textAlign = TextAlign.Center
                )

                // Small indicator showing adjustment amount
                if (currentAdjustment != 0) {
                    Spacer(modifier = Modifier.height(6.dp))

                    val adjustmentText = when {
                        currentAdjustment > 0 -> {
                            val hours = currentAdjustment / 60
                            val minutes = currentAdjustment % 60
                            when {
                                hours > 0 && minutes > 0 -> "+${hours}h ${minutes}m"
                                hours > 0 -> "+${hours}h"
                                else -> "+${minutes}m"
                            }
                        }
                        currentAdjustment < 0 -> {
                            val totalMinutes = kotlin.math.abs(currentAdjustment)
                            val hours = totalMinutes / 60
                            val minutes = totalMinutes % 60
                            when {
                                hours > 0 && minutes > 0 -> "-${hours}h ${minutes}m"
                                hours > 0 -> "-${hours}h"
                                else -> "${currentAdjustment}m"
                            }
                        }
                        else -> ""
                    }

                    if (adjustmentText.isNotEmpty()) {
                        Text(
                            text = adjustmentText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color(0xFF26C6DA), // Teal color
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Dynamic hint based on whether user has adjusted from base
                val hasAdjusted = currentAdjustment != baseAdjustment

                if (hasAdjusted) {
                    // iPhone-style swipe slider for undo/save
                    Spacer(modifier = Modifier.height(8.dp))

                    // Swipe slider container
                    Box(
                        modifier = Modifier
                            .width(280.dp)
                            .height(56.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFFF9800).copy(alpha = 0.15f),
                                        Color(0xFFEEEEEE),
                                        Color(0xFF4CAF50).copy(alpha = 0.15f)
                                    )
                                ),
                                shape = RoundedCornerShape(28.dp)
                            )
                            .border(
                                width = 1.5.dp,
                                color = Color(0xFFE0E0E0),
                                shape = RoundedCornerShape(28.dp)
                            )
                    ) {
                        // Left label - Undo
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "↶",
                                fontSize = 20.sp,
                                color = Color(0xFFFF9800).copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Undo",
                                fontSize = 13.sp,
                                color = Color(0xFFFF9800).copy(alpha = 0.7f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Right label - Save
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Save",
                                fontSize = 13.sp,
                                color = Color(0xFF4CAF50).copy(alpha = 0.7f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "✓",
                                fontSize = 20.sp,
                                color = Color(0xFF4CAF50).copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Draggable slider knob (iPhone-style)
                        var sliderOffset by remember { mutableStateOf(0f) }
                        val sliderWidth = 280.dp.value * density.density
                        val knobSize = 48.dp

                        Box(
                            modifier = Modifier
                                .offset { IntOffset((sliderOffset * density.density).toInt(), 0) }
                                .align(Alignment.CenterStart)
                                .padding(4.dp)
                                .size(knobSize)
                                .aspectRatio(1f) // Force perfect circle
                                .clip(CircleShape) // Clip to circle before background
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFF26C6DA), // Teal center
                                            Color(0xFF00ACC1)  // Darker teal edge
                                        )
                                    )
                                )
                                .border(
                                    width = 3.dp,
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White,
                                            Color.White.copy(alpha = 0.9f)
                                        )
                                    ),
                                    shape = CircleShape
                                )
                                .graphicsLayer {
                                    shadowElevation = 8.dp.toPx()
                                    shape = CircleShape
                                    clip = true
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = {
                                            sliderOffset = 0f
                                        },
                                        onDragEnd = {
                                            val maxOffset = sliderWidth - knobSize.toPx()

                                            when {
                                                sliderOffset < -80f -> {
                                                    // Swiped left - Undo
                                                    Log.d(
                                                        "InteractiveDial",
                                                        "← SLIDE LEFT - UNDO - Prayer: $prayerName"
                                                    )
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    currentAdjustment = baseAdjustment
                                                    onTimeAdjusted(baseAdjustment)
                                                    onSaveAdjustment(prayerName, baseAdjustment)
                                                }
                                                sliderOffset > 80f -> {
                                                    // Swiped right - Save
                                                    Log.d(
                                                        "InteractiveDial",
                                                        "→ SLIDE RIGHT - SAVE - Prayer: $prayerName, Adjustment: ${currentAdjustment}m"
                                                    )
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onSaveAdjustment(prayerName, currentAdjustment)
                                                }
                                            }
                                            // Reset slider
                                            sliderOffset = 0f
                                        }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        val maxOffset = sliderWidth - knobSize.toPx()
                                        sliderOffset =
                                            (sliderOffset + dragAmount.x / density.density).coerceIn(
                                                -100f,
                                                100f
                                            )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Chevron arrows showing swipe direction
                            Text(
                                text = when {
                                    sliderOffset < -20f -> "←"
                                    sliderOffset > 20f -> "→"
                                    else -> "⇄"
                                },
                                fontSize = 22.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Show hints when no adjustment has been made
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Drag knob to adjust",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                letterSpacing = 0.3.sp
                            ),
                            color = Color(0xFF607D8B).copy(alpha = 0.6f), // Subtle gray
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap to close",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                letterSpacing = 0.3.sp
                            ),
                            color = Color(0xFF607D8B).copy(alpha = 0.5f), // Slightly more visible
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawCleanCircularTimer(
    center: Offset,
    radius: Float,
    timeAdjustment: Int,
    originalTime: LocalTime,
    isDragging: Boolean,
    currentDragAngle: Float,
    surfaceColor: Color,
    onSurfaceColor: Color,
    outlineColor: Color,
    knobScale: Float = 1f,
    progressArcGlow: Float = 1f
) {
    // Design matching the reference image: central dial with outer segmented ring
    // Ensure perfect centering - use exact center coordinates
    val exactCenter = Offset(center.x, center.y)
    val outerRingRadius = radius * 1.15f // Outer ring radius
    val centralDialRadius = radius * 0.65f // Central dial radius (smaller, inside the ring)

    // Colors matching the reference design
    val lightGreyBackground = Color(0xFFF5F5F5) // Very light grey/off-white
    val tealColor = Color(0xFF26C6DA) // Vibrant teal/aqua for all segments (uniform color like reference)
    val shadowColor = Color.Black.copy(alpha = 0.08f)
    val highlightColor = Color.White.copy(alpha = 0.3f)

    // Draw outer shadow for depth (soft shadow beneath the entire dial) - centered
    drawCircle(
        color = shadowColor,
        radius = outerRingRadius + 6f,
        center = exactCenter // Shadow centered, not offset
    )

    // Draw outer segmented ring background (recessed appearance) - perfectly centered
    drawCircle(
        color = lightGreyBackground.copy(alpha = 0.5f),
        radius = outerRingRadius,
        center = exactCenter
    )

    // Draw segmented outer ring with vertical dashes - all uniform teal color like reference
    val segmentCount = 120 // Number of segments around the circle
    val segmentLength = 20f // Length of each segment
    val segmentWidth = 2.5f // Width of segments (uniform)
    val segmentGap = 2f // Gap between segments
    
    // Calculate actual prayer time (adjusted) for angle calculation
    val adjustedDateTime = LocalDateTime.of(LocalDate.now(), originalTime).plusMinutes(timeAdjustment.toLong())
    val adjustedTime = adjustedDateTime.toLocalTime()

    // Convert time to angle (starting from top - 12 o'clock position)
    val hourIn12Format = if (adjustedTime.hour % 12 == 0) 12 else adjustedTime.hour % 12
    val timeAngle = ((hourIn12Format * 60 + adjustedTime.minute) / (12 * 60f)) * 360f - 90f

    // Draw segmented ring - all segments uniform teal color (matching reference image)
    // All segments are the same teal color, no distinction between active/inactive
    for (i in 0 until segmentCount) {
        val segmentAngle = (i * (360.0 / segmentCount) - 90.0) * PI / 180.0 // Start from top

        // Segment position - vertical dashes at the outer edge with gaps
        val segmentRadius = outerRingRadius - segmentLength / 2f
        val segmentStart = Offset(
            exactCenter.x + segmentRadius * cos(segmentAngle.toFloat()).toFloat(),
            exactCenter.y + segmentRadius * sin(segmentAngle.toFloat()).toFloat()
        )
        val segmentEnd = Offset(
            exactCenter.x + (segmentRadius + segmentLength) * cos(segmentAngle.toFloat()).toFloat(),
            exactCenter.y + (segmentRadius + segmentLength) * sin(segmentAngle.toFloat()).toFloat()
        )

        // Draw segment - all segments are uniform teal color (matching reference)
        drawLine(
            color = tealColor.copy(alpha = 0.8f),
            start = segmentStart,
            end = segmentEnd,
            strokeWidth = segmentWidth,
            cap = StrokeCap.Round
        )
    }

    // Draw central dial with 3D effect (light grey/off-white with shadows and highlights)
    // Ensure it's perfectly centered - all elements use exactCenter
    // Main central dial background (light grey/off-white) - perfectly centered
    drawCircle(
        color = lightGreyBackground,
        radius = centralDialRadius,
        center = exactCenter
    )

    // Subtle highlight on top-left for 3D effect
    val highlightRadius = centralDialRadius * 0.7f
    val highlightCenter = Offset(exactCenter.x - centralDialRadius * 0.3f, exactCenter.y - centralDialRadius * 0.3f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                highlightColor,
                Color.Transparent
            ),
            center = highlightCenter,
            radius = highlightRadius
        ),
        radius = highlightRadius,
        center = highlightCenter
    )

    // Subtle shadow on bottom-right for depth
    val shadowRadius = centralDialRadius * 0.7f
    val shadowCenter = Offset(exactCenter.x + centralDialRadius * 0.3f, exactCenter.y + centralDialRadius * 0.3f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                shadowColor.copy(alpha = 0.15f)
            ),
            center = shadowCenter,
            radius = shadowRadius
        ),
        radius = shadowRadius,
        center = shadowCenter
    )

    // Draw small teal horizontal indicator mark at 12 o'clock position
    // Position it on the central dial, directly above where the time text will be displayed
    // This matches the reference design where the indicator is on the central dial itself
    val indicatorAngle = -90f * PI / 180f // 12 o'clock position
    val indicatorRadius = centralDialRadius - 20f // Position on the central dial, near the top edge
    val indicatorCenter = Offset(
        exactCenter.x + indicatorRadius * cos(indicatorAngle.toFloat()).toFloat(),
        exactCenter.y + indicatorRadius * sin(indicatorAngle.toFloat()).toFloat()
    )

    // Small horizontal rectangular indicator (teal pill shape) - positioned on central dial at 12 o'clock
    val indicatorWidth = 24f * knobScale // Small horizontal rectangle
    val indicatorHeight = 4f * knobScale // Thin horizontal mark

    drawRoundRect(
        color = tealColor,
        topLeft = Offset(
            indicatorCenter.x - indicatorWidth / 2f,
            indicatorCenter.y - indicatorHeight / 2f
        ),
        size = Size(indicatorWidth, indicatorHeight),
        cornerRadius = CornerRadius(indicatorHeight / 2f, indicatorHeight / 2f)
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

// Format time in 12-hour format with AM/PM
private fun adjustTimeByMinutesForDisplay(originalTime: LocalTime, minutes: Int): String {
    val adjustedDateTime = LocalDateTime.of(
        LocalDate.now(),
        originalTime
    ).plusMinutes(minutes.toLong())

    val adjustedTime = adjustedDateTime.toLocalTime()

    // DEBUG: Log the time conversion
    Log.d("InteractiveDial", "🕐 TIME CONVERSION DEBUG:")
    Log.d("InteractiveDial", "   📥 Original time: $originalTime (hour=${originalTime.hour}, minute=${originalTime.minute})")
    Log.d("InteractiveDial", "   ➕ Adjustment: ${minutes}m")
    Log.d("InteractiveDial", "   ⏰ Adjusted 24h: $adjustedTime (hour=${adjustedTime.hour}, minute=${adjustedTime.minute})")

    // Correct 12-hour conversion logic
    val hour12 = when {
        adjustedTime.hour == 0 -> 12  // Midnight (00:00) → 12 AM
        adjustedTime.hour <= 12 -> adjustedTime.hour  // 1-12 → stay the same
        else -> adjustedTime.hour - 12  // 13-23 → subtract 12
    }
    val amPm = if (adjustedTime.hour < 12) "AM" else "PM"

    val result = String.format("%02d:%02d %s", hour12, adjustedTime.minute, amPm)
    Log.d("InteractiveDial", "   🎯 Final 12h format: $result")

    return result
}
