package com.starception.submission.feature.prayertimes.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.starception.submission.R
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sin
import android.util.Log

// Progress converter from catalog app
private fun convertProgress(progress: Float): Float {
    return (1f - exp(-abs(progress))) * progress.sign
}

/**
 * Control Center style popup - Full screen watch face for prayer time adjustment
 * Uses live app content as backdrop for glass blur effect
 */
@Composable
fun ControlCenterPrayerPopup(
    prayerName: String,
    prayerTime: String,
    originalTime: LocalTime,
    currentOffset: Int,
    onDismiss: () -> Unit,
    onSaveAdjustment: (String, Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val hapticFeedback = LocalHapticFeedback.current

    val isLightTheme = !isSystemInDarkTheme()
    val containerColor = Color.Black.copy(0.05f)

    val animationScope = rememberCoroutineScope()
    // Start at 0 for smooth enter animation
    val enterProgressAnimation = remember { Animatable(0f) }
    val safeEnterProgressAnimation = remember { Animatable(0f) }
    val progress by remember {
        derivedStateOf {
            val p = enterProgressAnimation.value
            when {
                p < 0f -> convertProgress(p)
                p <= 1f -> p
                else -> 1f + convertProgress(p - 1f)
            }
        }
    }
    val maxDragHeight = 1000f

    // Smooth enter animation when Control Center appears
    LaunchedEffect(Unit) {
        animationScope.launch {
            enterProgressAnimation.animateTo(
                1f,
                spring(dampingRatio = 0.7f, stiffness = 300f)
            )
        }
        animationScope.launch {
            safeEnterProgressAnimation.animateTo(
                1f,
                spring(dampingRatio = 0.8f, stiffness = 400f)
            )
        }
    }

    val uiSensor = rememberUISensor()

    // Layer backdrop to capture dial content for magnification (like Slider)
    val dialBackdrop = rememberLayerBackdrop()

    val glassShape: () -> androidx.compose.ui.graphics.Shape = { CircleShape }
    val glassHighlight = {
        Highlight(
            style = HighlightStyle.Default(
                angle = uiSensor.gravityAngle,
                falloff = 2f
            )
        )
    }
    val glassLayer: GraphicsLayerScope.() -> Unit = {
        val p = progress
        val safeProgress = safeEnterProgressAnimation.value
        translationY = -48f.dp.toPx() * (1f - p)
        alpha = EaseIn.transform(safeProgress)
        scaleX /= 1f + 0.1f * (p - 1f).fastCoerceAtLeast(0f)
        scaleY *= 1f + 0.1f * (p - 1f).fastCoerceAtLeast(0f)
    }
    val glassSurface: DrawScope.() -> Unit = { drawRect(containerColor) }
    val glassEffects: BackdropEffectScope.() -> Unit = {
        val p = safeEnterProgressAnimation.value
        vibrancy()
        lens(
            24f.dp.toPx() * p,
            48f.dp.toPx() * p,
            depthEffect = true
        )
    }

    // ========================================================================
    // PRAYER ADJUSTMENT STATE
    // ========================================================================
    var timeAdjustment by remember { mutableIntStateOf(currentOffset) }
    val originalOffset by remember { mutableIntStateOf(currentOffset) }
    var baseAdjustment by remember { mutableIntStateOf(currentOffset) }
    var accumulatedAngle by remember { mutableFloatStateOf(0f) }
    var lastAngle by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var currentDragAngle by remember { mutableFloatStateOf(0f) }
    var lastHapticAdjustment by remember { mutableIntStateOf(currentOffset) }


    // Watch face rotation states - derived from adjusted prayer time
    val adjustedPrayerTime by remember(originalTime, timeAdjustment) {
        derivedStateOf {
            LocalDateTime.of(LocalDate.now(), originalTime).plusMinutes(timeAdjustment.toLong()).toLocalTime()
        }
    }

    // Calculate rotations based on adjusted prayer time
    val hourRotation by remember(adjustedPrayerTime) {
        derivedStateOf {
            // Hour dial: 12 hours = 360 degrees, so 30 degrees per hour + minute contribution
            -((adjustedPrayerTime.hour % 12) * 30f + adjustedPrayerTime.minute * 0.5f)
        }
    }
    val minuteRotation by remember(adjustedPrayerTime) {
        derivedStateOf {
            // Minute dial: 60 minutes = 360 degrees, so 6 degrees per minute
            -(adjustedPrayerTime.minute * 6f)
        }
    }
    // Second dial: static position based on adjusted prayer time seconds (no animation for performance)
    val secondRotation by remember(adjustedPrayerTime) {
        derivedStateOf {
            // Second dial shows the seconds of the adjusted time
            -(adjustedPrayerTime.second * 6f)
        }
    }

    val dialScale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "dialScale"
    )

    // Main layout
    Box(
        modifier
            .fillMaxSize()
            .draggable(
                rememberDraggableState { delta ->
                    val targetProgress = enterProgressAnimation.value + delta / maxDragHeight
                    animationScope.launch {
                        launch {
                            enterProgressAnimation.snapTo(targetProgress)
                        }
                        launch {
                            safeEnterProgressAnimation.snapTo(targetProgress.fastCoerceIn(0f, 1f))
                        }
                    }
                },
                Orientation.Vertical,
                onDragStopped = { velocity ->
                    val targetProgress = when {
                        velocity < 0f -> 0f
                        velocity > 0f -> 1f
                        else -> if (enterProgressAnimation.value < 0.5f) 0f else 1f
                    }

                    if (targetProgress == 0f) {
                        onDismiss()
                    }

                    animationScope.launch {
                        launch {
                            enterProgressAnimation.animateTo(
                                targetProgress,
                                if (targetProgress > 0.5f) {
                                    spring(0.5f, 300f, 0.5f / maxDragHeight)
                                } else {
                                    spring(1f, 300f, 0.01f)
                                },
                                velocity / maxDragHeight
                            )
                        }
                        launch {
                            safeEnterProgressAnimation.animateTo(
                                targetProgress,
                                spring(1f, 300f, 0.01f)
                            )
                        }
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Full screen watch face with prayer adjustment
        Column(
            Modifier
                .systemBarsPadding()
                .displayCutoutPadding()
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Full screen circular watch face dial with glass effect
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .aspectRatio(1f) // Force perfect circle
                    .graphicsLayer {
                        scaleX = dialScale
                        scaleY = dialScale
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = glassShape,
                        effects = glassEffects,
                        highlight = glassHighlight,
                        shadow = null,
                        layerBlock = glassLayer,
                        onDrawSurface = glassSurface
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val distanceFromCenter = kotlin.math.sqrt(
                                    (offset.x - center.x) * (offset.x - center.x) +
                                    (offset.y - center.y) * (offset.y - center.y)
                                )
                                val outerRadius = kotlin.math.min(size.width, size.height) * 0.5f

                                if (distanceFromCenter <= outerRadius) {
                                    isDragging = true
                                    lastAngle = atan2(
                                        offset.y - center.y,
                                        offset.x - center.x
                                    ) * 180f / PI.toFloat()
                                    accumulatedAngle = 0f

                                    val adjustedDateTime = LocalDateTime.of(LocalDate.now(), originalTime).plusMinutes(timeAdjustment.toLong())
                                    val adjustedTime = adjustedDateTime.toLocalTime()
                                    val hourIn12Format = if (adjustedTime.hour % 12 == 0) 12 else adjustedTime.hour % 12
                                    val timeAngle = ((hourIn12Format * 60 + adjustedTime.minute) / (12 * 60f)) * 360f - 90f

                                    currentDragAngle = lastAngle

                                    var initialAngleDiff = lastAngle - timeAngle
                                    if (initialAngleDiff > 180f) initialAngleDiff -= 360f
                                    if (initialAngleDiff < -180f) initialAngleDiff += 360f
                                    accumulatedAngle = initialAngleDiff

                                    Log.d("ControlCenter", "🚀 DRAG START - Prayer: $prayerName")
                                }
                            },
                            onDragEnd = {
                                Log.d("ControlCenter", "🏁 DRAG END - Adjustment: ${timeAdjustment}m")
                                isDragging = false
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        ) { change, _ ->
                            if (isDragging) {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val fingerAngle = atan2(
                                    change.position.y - center.y,
                                    change.position.x - center.x
                                ) * 180f / PI.toFloat()

                                var angleDiff = fingerAngle - lastAngle
                                if (angleDiff > 180f) angleDiff -= 360f
                                if (angleDiff < -180f) angleDiff += 360f

                                currentDragAngle += angleDiff
                                if (currentDragAngle < 0f) currentDragAngle += 360f
                                if (currentDragAngle >= 360f) currentDragAngle -= 360f

                                accumulatedAngle += angleDiff
                                val newAdjustment = baseAdjustment + (accumulatedAngle / 3f).toInt()

                                if (newAdjustment != timeAdjustment) {
                                    timeAdjustment = newAdjustment
                                    if (newAdjustment != lastHapticAdjustment) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        lastHapticAdjustment = newAdjustment
                                    }
                                }
                                lastAngle = fingerAngle
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Watch face dial canvas - wrapped in layerBackdrop for date window magnification
                Box(Modifier.fillMaxSize().layerBackdrop(dialBackdrop)) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                    val outerRadius = minOf(size.width, size.height) / 2f - 4.dp.toPx()
                    val middleRadius = outerRadius - 38.dp.toPx()
                    val innerRadius = middleRadius - 38.dp.toPx()
                    val clockStyle = ControlCenterClockStyle

                    // Seconds Dial (outer)
                    drawWatchDial(
                        radius = outerRadius,
                        rotation = secondRotation,
                        textMeasurer = textMeasurer,
                        dialStyle = clockStyle.secondsDialStyle,
                        showLabels = true
                    )

                    // Minutes Dial (middle) - with labels like reference
                    drawWatchDial(
                        radius = middleRadius,
                        rotation = minuteRotation,
                        textMeasurer = textMeasurer,
                        dialStyle = clockStyle.minutesDialStyle,
                        showLabels = true
                    )

                    // Hours Dial (inner) - 12 hour marks
                    drawHourDial(
                        radius = innerRadius,
                        rotation = hourRotation,
                        textMeasurer = textMeasurer,
                        dialStyle = clockStyle.hoursDialStyle
                    )

                    // Date window is now handled by liquid glass composable overlay only
                    }
                }

                // Liquid glass magnifying lens for date window (like Slider implementation)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 4.dp, y = 0.dp)
                        .size(width = 128.dp, height = 50.dp)
                        .drawBackdrop(
                            backdrop = rememberCombinedBackdrop(
                                backdrop,
                                rememberBackdrop(dialBackdrop) { drawBackdrop ->
                                    // Draw the dial content through the lens
                                    drawBackdrop()
                                }
                            ),
                            shape = { RoundedCornerShape(topStart = 25.dp, bottomStart = 25.dp, topEnd = 0.dp, bottomEnd = 0.dp) },
                            effects = {
                                // Magnifying lens effect like slider
                                lens(
                                    14f.dp.toPx(),
                                    20f.dp.toPx(),
                                    chromaticAberration = true,
                                    depthEffect = true
                                )
                            },
                            highlight = glassHighlight,
                            shadow = null,
                            layerBlock = { alpha = 0.95f },
                            onDrawSurface = {
                                // Subtle tint for glass appearance
                                drawRect(Color.White.copy(alpha = 0.03f))
                            }
                        )
                )

                // Center content - prayer info and controls (reference style)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Prayer name - small label above time
                    Text(
                        text = prayerName,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp
                        ),
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Digital time display - 12-hour format
                    val adjustedDateTime = LocalDateTime.of(LocalDate.now(), originalTime).plusMinutes(timeAdjustment.toLong())
                    val adjustedLocalTime = adjustedDateTime.toLocalTime()
                    val hour12 = when {
                        adjustedLocalTime.hour == 0 -> 12
                        adjustedLocalTime.hour > 12 -> adjustedLocalTime.hour - 12
                        else -> adjustedLocalTime.hour
                    }
                    val amPm = if (adjustedLocalTime.hour < 12) "AM" else "PM"
                    val hourDisplay = String.format("%d", hour12)
                    val minuteDisplay = String.format("%02d", adjustedLocalTime.minute)

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hour
                        Text(
                            text = hourDisplay,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 56.sp
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        // Colon separator
                        Text(
                            text = ":",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 48.sp
                            ),
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        // Minute
                        Text(
                            text = minuteDisplay,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 56.sp
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        // AM/PM indicator
                        Text(
                            text = amPm,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp
                            ),
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Offset indicator
                    val adjustmentText = when {
                        timeAdjustment > 0 -> {
                            val hours = timeAdjustment / 60
                            val mins = timeAdjustment % 60
                            when {
                                hours > 0 && mins > 0 -> "+${hours}h ${mins}m"
                                hours > 0 -> "+${hours}h"
                                else -> "+${mins}m"
                            }
                        }
                        timeAdjustment < 0 -> {
                            val totalMins = abs(timeAdjustment)
                            val hours = totalMins / 60
                            val mins = totalMins % 60
                            when {
                                hours > 0 && mins > 0 -> "-${hours}h ${mins}m"
                                hours > 0 -> "-${hours}h"
                                else -> "${timeAdjustment}m"
                            }
                        }
                        else -> "0m"
                    }

                    Text(
                        text = adjustmentText,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        color = Color(0xFF26C6DA),
                        textAlign = TextAlign.Center
                    )

                    val hasAdjusted = timeAdjustment != baseAdjustment

                    // Fixed height container to prevent layout jumps
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier.height(60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = hasAdjusted,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
                            ) + fadeIn(animationSpec = spring(stiffness = 300f)),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                            ) + fadeOut(animationSpec = spring(stiffness = 400f))
                        ) {
                            // Liquid Glass Bottom Tabs UI (exact copy from catalog app)
                            var selectedTabIndex by remember { mutableIntStateOf(0) }

                            LiquidBottomTabs(
                                selectedTabIndex = { selectedTabIndex },
                                onTabSelected = { index ->
                                    selectedTabIndex = index
                                    when (index) {
                                        0 -> {
                                            // Reset
                                            Log.d("ControlCenter", "↶ RESET to original: $originalOffset")
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            timeAdjustment = originalOffset
                                            baseAdjustment = originalOffset
                                            accumulatedAngle = 0f
                                        }
                                        1 -> {
                                            // Save
                                            Log.d("ControlCenter", "✓ SAVE adjustment: $timeAdjustment")
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onSaveAdjustment(prayerName, timeAdjustment)
                                        }
                                    }
                                },
                                backdrop = backdrop,
                                tabsCount = 2,
                                modifier = Modifier.fillMaxWidth(0.7f)
                            ) {
                                // Reset Tab
                                LiquidBottomTab(
                                    onClick = {
                                        selectedTabIndex = 0
                                        Log.d("ControlCenter", "↶ RESET to original: $originalOffset")
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        timeAdjustment = originalOffset
                                        baseAdjustment = originalOffset
                                        accumulatedAngle = 0f
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Reset",
                                        modifier = Modifier.size(24.dp),
                                        tint = Color.White.copy(alpha = 0.9f)
                                    )
                                    Text(
                                        text = "Reset",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }

                                // Save Tab
                                LiquidBottomTab(
                                    onClick = {
                                        selectedTabIndex = 1
                                        Log.d("ControlCenter", "✓ SAVE adjustment: $timeAdjustment")
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSaveAdjustment(prayerName, timeAdjustment)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Save",
                                        modifier = Modifier.size(24.dp),
                                        tint = Color(0xFF26C6DA)
                                    )
                                    Text(
                                        text = "Save",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF26C6DA)
                                    )
                                }
                            }
                        }

                        // Hint text when not adjusted (slides down to exit, up to enter)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !hasAdjusted,
                            enter = slideInVertically(
                                initialOffsetY = { -it },
                                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
                            ) + fadeIn(animationSpec = spring(stiffness = 300f)),
                            exit = slideOutVertically(
                                targetOffsetY = { -it },
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                            ) + fadeOut(animationSpec = spring(stiffness = 400f))
                        ) {
                            Text(
                                text = "Rotate dial to adjust time",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// Helper function to format adjusted time for display
private fun adjustTimeForDisplay(originalTime: LocalTime, minutes: Int): String {
    val adjustedDateTime = LocalDateTime.of(LocalDate.now(), originalTime).plusMinutes(minutes.toLong())
    val adjustedTime = adjustedDateTime.toLocalTime()
    val hour12 = when {
        adjustedTime.hour == 0 -> 12
        adjustedTime.hour <= 12 -> adjustedTime.hour
        else -> adjustedTime.hour - 12
    }
    val amPm = if (adjustedTime.hour < 12) "AM" else "PM"
    return String.format("%d:%02d %s", hour12, adjustedTime.minute, amPm)
}

// Draw watch dial function - Helix/Timex inspired styling
private fun DrawScope.drawWatchDial(
    radius: Float,
    rotation: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    dialStyle: WatchDialStyle,
    showLabels: Boolean = true
) {
    var stepsAngle = 0

    repeat(60) { steps ->
        val isMajorStep = steps % 5 == 0

        // Skip minor ticks if not showing them
        if (!isMajorStep && !dialStyle.showMinorTicks) {
            stepsAngle += 6
            return@repeat
        }

        val stepsHeight = if (isMajorStep) {
            dialStyle.fiveStepsLineHeight.toPx()
        } else {
            dialStyle.normalStepsLineHeight.toPx()
        }

        // Major markers (with numbers) are thicker than minor markers
        val strokeWidth = if (isMajorStep) {
            dialStyle.majorStepsWidth.toPx()
        } else {
            dialStyle.stepsWidth.toPx()
        }

        val color = if (isMajorStep) {
            dialStyle.majorStepsColor
        } else {
            dialStyle.stepsColor
        }

        // All markers start from the same outer edge
        val stepsStartOffset = Offset(
            x = center.x + (radius * cos((stepsAngle + rotation) * (Math.PI / 180f))).toFloat(),
            y = center.y - (radius * sin((stepsAngle + rotation) * (Math.PI / 180))).toFloat()
        )
        val stepsEndOffset = Offset(
            x = center.x + (radius - stepsHeight) * cos((stepsAngle + rotation) * (Math.PI / 180)).toFloat(),
            y = center.y - (radius - stepsHeight) * sin((stepsAngle + rotation) * (Math.PI / 180)).toFloat()
        )

        drawLine(
            color = color,
            start = stepsStartOffset,
            end = stepsEndOffset,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Draw red accent marks at major positions (like Helix watch) - inside the dial
        if (isMajorStep && dialStyle.showAccentMarks) {
            val accentHeight = 4.dp.toPx()
            // Red accent starts at same outer edge as main marker
            val accentStartOffset = Offset(
                x = center.x + (radius * cos((stepsAngle + rotation) * (Math.PI / 180f))).toFloat(),
                y = center.y - (radius * sin((stepsAngle + rotation) * (Math.PI / 180))).toFloat()
            )
            val accentEndOffset = Offset(
                x = center.x + ((radius - accentHeight) * cos((stepsAngle + rotation) * (Math.PI / 180))).toFloat(),
                y = center.y - ((radius - accentHeight) * sin((stepsAngle + rotation) * (Math.PI / 180))).toFloat()
            )
            drawLine(
                color = dialStyle.accentColor,
                start = accentStartOffset,
                end = accentEndOffset,
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        if (showLabels && isMajorStep) {
            val stepsLabel = String.format("%02d", steps)
            val stepsLabelTextLayout = textMeasurer.measure(
                text = buildAnnotatedString { append(stepsLabel) },
                style = dialStyle.stepsTextStyle
            )

            val stepsLabelOffset = Offset(
                x = center.x + (radius - stepsHeight - dialStyle.stepsLabelTopPadding.toPx()) * cos((stepsAngle + rotation) * (Math.PI / 180)).toFloat(),
                y = center.y - (radius - stepsHeight - dialStyle.stepsLabelTopPadding.toPx()) * sin((stepsAngle + rotation) * (Math.PI / 180)).toFloat()
            )

            val stepsLabelTopLeft = Offset(
                stepsLabelOffset.x - ((stepsLabelTextLayout.size.width) / 2f),
                stepsLabelOffset.y - (stepsLabelTextLayout.size.height / 2f)
            )

            drawText(
                textMeasurer = textMeasurer,
                text = stepsLabel,
                topLeft = stepsLabelTopLeft,
                style = dialStyle.stepsTextStyle
            )
        }
        stepsAngle += 6
    }
}

// Draw hour dial function (12 hour marks + minor ticks) - Helix/Timex inspired with all 12 numbers
private fun DrawScope.drawHourDial(
    radius: Float,
    rotation: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    dialStyle: WatchDialStyle
) {
    // 60 tick marks like seconds/minutes, with hour positions (every 5th) being major
    repeat(60) { tickIndex ->
        val tickAngle = tickIndex * 6f
        val isHourPosition = tickIndex % 5 == 0

        // Skip minor ticks if not showing them
        if (!isHourPosition && !dialStyle.showMinorTicks) {
            return@repeat
        }

        val stepsHeight = if (isHourPosition) {
            dialStyle.fiveStepsLineHeight.toPx()
        } else {
            dialStyle.normalStepsLineHeight.toPx()
        }

        val strokeWidth = if (isHourPosition) {
            dialStyle.majorStepsWidth.toPx()
        } else {
            dialStyle.stepsWidth.toPx()
        }

        val color = if (isHourPosition) {
            dialStyle.majorStepsColor
        } else {
            dialStyle.stepsColor
        }

        val stepsStartOffset = Offset(
            x = center.x + (radius * cos((tickAngle + rotation) * (Math.PI / 180f))).toFloat(),
            y = center.y - (radius * sin((tickAngle + rotation) * (Math.PI / 180))).toFloat()
        )
        val stepsEndOffset = Offset(
            x = center.x + (radius - stepsHeight) * cos((tickAngle + rotation) * (Math.PI / 180)).toFloat(),
            y = center.y - (radius - stepsHeight) * sin((tickAngle + rotation) * (Math.PI / 180)).toFloat()
        )

        drawLine(
            color = color,
            start = stepsStartOffset,
            end = stepsEndOffset,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Hour labels only at hour positions (every 5th tick = 30 degrees)
        if (isHourPosition) {
            val hourIndex = tickIndex / 5
            val hourLabel = if (hourIndex == 0) "12" else hourIndex.toString()
            val hourLabelTextLayout = textMeasurer.measure(
                text = buildAnnotatedString { append(hourLabel) },
                style = dialStyle.stepsTextStyle
            )

            val hourLabelOffset = Offset(
                x = center.x + (radius - stepsHeight - dialStyle.stepsLabelTopPadding.toPx()) * cos((tickAngle + rotation) * (Math.PI / 180)).toFloat(),
                y = center.y - (radius - stepsHeight - dialStyle.stepsLabelTopPadding.toPx()) * sin((tickAngle + rotation) * (Math.PI / 180)).toFloat()
            )

            val hourLabelTopLeft = Offset(
                hourLabelOffset.x - ((hourLabelTextLayout.size.width) / 2f),
                hourLabelOffset.y - (hourLabelTextLayout.size.height / 2f)
            )

            drawText(
                textMeasurer = textMeasurer,
                text = hourLabel,
                topLeft = hourLabelTopLeft,
                style = dialStyle.stepsTextStyle
            )
        }
    }
}

