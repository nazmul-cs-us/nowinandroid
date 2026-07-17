package com.starception.submission.feature.prayertimes.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Save
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import android.util.Log
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.drawscope.rotate
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// ============================================================================
// UI SENSOR FOR DYNAMIC HIGHLIGHT (from AndroidLiquidGlass)
// ============================================================================

@Composable
fun rememberUISensor(): UISensor {
    val context = LocalContext.current
    val uiSensor = remember { UISensor(context) }

    DisposableEffect(Unit) {
        uiSensor.start()
        onDispose { uiSensor.stop() }
    }

    return uiSensor
}

class UISensor(context: Context) {
    var gravityAngle: Float by mutableFloatStateOf(45f)
        private set
    var gravity: Offset by mutableStateOf(Offset.Zero)
        private set

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = event.values[0]
                val y = event.values[1]
                val norm = sqrt(x * x + y * y + 9.81f * 9.81f)

                val alpha = 0.5f
                gravityAngle = gravityAngle * (1f - alpha) + atan2(y, x) * (180f / PI).toFloat() * alpha
                gravity = gravity * (1f - alpha) + Offset(x / norm, y / norm) * alpha
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
    }
}

// ============================================================================
// WATCHFACE DIAL STYLE DATA CLASSES (copied from watchface-main project)
// ============================================================================

data class DialStyle(
    val stepsWidth: Float = 1.2f,
    val stepsColor: Color = Color.Black,
    val normalStepsLineHeight: Float = 8f,
    val fiveStepsLineHeight: Float = 16f,
    val stepsTextStyle: TextStyle = TextStyle(),
    val stepsLabelTopPadding: Float = 20f,
)

data class ClockStyle(
    val secondsDialStyle: DialStyle = DialStyle(),
    val minutesDialStyle: DialStyle = DialStyle(),
    val hourLabelStyle: TextStyle = TextStyle(),
    val overlayStrokeWidth: Float = 2f,
    val overlayStrokeColor: Color = Color.Red
)

val PrayerDialStyle = ClockStyle(
    secondsDialStyle = DialStyle(
        stepsTextStyle = TextStyle(
            color = Color.White, // White text for Control Center glass
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        ),
        stepsColor = Color.White.copy(alpha = 0.6f), // Subtle white tick marks
        stepsLabelTopPadding = 18f,
        normalStepsLineHeight = 6f,
        fiveStepsLineHeight = 12f
    ),
    minutesDialStyle = DialStyle(
        stepsTextStyle = TextStyle(
            color = Color.White.copy(alpha = 0.8f), // Slightly transparent white
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        ),
        stepsColor = Color.White.copy(alpha = 0.4f), // More subtle inner tick marks
        stepsLabelTopPadding = 16f,
        normalStepsLineHeight = 4f,
        fiveStepsLineHeight = 10f
    ),
    hourLabelStyle = TextStyle(
        color = Color.White, // White hour label
        fontSize = 56.sp,
        fontWeight = FontWeight.Bold
    ),
    overlayStrokeColor = Color.White.copy(alpha = 0.8f), // White overlay
    overlayStrokeWidth = 1.5f
)

// ============================================================================
// INTERACTIVE PRAYER DIAL COMPOSABLE
// ============================================================================

@OptIn(ExperimentalTextApi::class)
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
    // TextMeasurer for drawing text on Canvas (from watchface)
    val textMeasurer = rememberTextMeasurer()

    // ========================================================================
    // CONTROL CENTER STYLE LIQUID GLASS CONFIGURATION
    // ========================================================================

    // UI Sensor for dynamic highlight based on device tilt
    val uiSensor = rememberUISensor()

    // Liquid glass backdrop
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val density = LocalDensity.current

    // Control Center style colors
    val containerColor = Color.Black.copy(alpha = 0.05f)  // Very subtle dark tint

    // Glass shape provider for circle
    val glassShape: () -> androidx.compose.ui.graphics.Shape = { CircleShape }

    // Dynamic highlight based on device orientation (like Control Center)
    val glassHighlight: () -> Highlight = {
        Highlight(
            style = HighlightStyle.Default(
                angle = uiSensor.gravityAngle,
                falloff = 2f
            )
        )
    }

    // Glass effects with vibrancy and lens (with depth effect)
    val glassEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = {
        vibrancy()
        lens(
            with(density) { 24.dp.toPx() },
            with(density) { 48.dp.toPx() },
            depthEffect = true
        )
    }

    // Glass surface drawing
    val glassSurface: DrawScope.() -> Unit = {
        drawRect(containerColor)
    }

    // CRITICAL LOGGING: Track when timeAdjustment parameter changes
    LaunchedEffect(timeAdjustment) {
        Log.w("InteractiveDial", "⚠️ PARAMETER CHANGE DETECTED - Prayer: $prayerName")
        Log.w("InteractiveDial", "   📥 NEW timeAdjustment value: $timeAdjustment minutes")
        Log.w("InteractiveDial", "   🔍 This indicates parent recomposed with new value")
        Log.w("InteractiveDial", "   📍 Stack trace: ${Thread.currentThread().stackTrace.take(5).joinToString("\n      ")}")
    }

    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var lastAngle by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var accumulatedAngle by remember { mutableStateOf(0f) }

    // ========================================================================
    // WATCHFACE ROTATION STATES (copied from watchface-main Clock.kt)
    // ========================================================================
    var minuteRotation by remember { mutableStateOf(0f) }
    var secondRotation by remember { mutableStateOf(0f) }
    var hour by remember { mutableStateOf(0) }

    // Initialize rotation from current time
    LaunchedEffect(key1 = true) {
        val calendar = java.util.Calendar.getInstance()
        val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(java.util.Calendar.MINUTE)
        val currentSecond = calendar.get(java.util.Calendar.SECOND)
        secondRotation = -(currentSecond) * 6f
        minuteRotation = -(currentMinute) * 6f
        hour = currentHour
    }

    // Smooth second hand animation (16ms updates for 60fps)
    LaunchedEffect(key1 = true) {
        while (true) {
            kotlinx.coroutines.delay(16)
            secondRotation -= 0.096f
        }
    }

    // Minute hand animation (update every second)
    LaunchedEffect(key1 = true) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            minuteRotation -= 0.1f
        }
    }

    // Hour update (every hour)
    LaunchedEffect(key1 = true) {
        while (true) {
            kotlinx.coroutines.delay(60 * 60 * 1000L)
            hour = (hour + 1) % 24
        }
    }

    // CRITICAL: Store the ORIGINAL offset when dial opens - this is what we reset to
    val originalOffset by remember { mutableStateOf(timeAdjustment) }

    var baseAdjustment by remember { mutableStateOf(timeAdjustment) }
    var currentDragAngle by remember { mutableStateOf(0f) }
    var lastHapticAdjustment by remember { mutableStateOf(timeAdjustment) }

    // Track the current adjustment value internally (survives after drag ends)
    var currentAdjustment by remember { mutableStateOf(timeAdjustment) }

    // Swipe gesture states
    var swipeOffset by remember { mutableStateOf(0f) }
    var isSwipingHorizontally by remember { mutableStateOf(false) }
    val swipeThreshold = 150f // How far to swipe to trigger action

    // Tracks the finger 1:1 while swiping; the spring only smooths the settle after release.
    val animatedSwipeOffset by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = if (isSwipingHorizontally) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "swipeOffset"
    )

    // Log whenever currentAdjustment changes
    LaunchedEffect(currentAdjustment) {
        Log.d("InteractiveDial", "🔄 INTERNAL STATE - Prayer: $prayerName, currentAdjustment: $currentAdjustment minutes")
    }

    // Material 3 expressive animations for enhanced feedback
    val dialScale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
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
    val themeSurfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outline

    // Initialize from timeAdjustment only once when component is first created
    LaunchedEffect(Unit) {
        currentAdjustment = timeAdjustment
        baseAdjustment = timeAdjustment
        // originalOffset is already initialized from timeAdjustment via remember
        Log.d("InteractiveDial", "📌 DIAL OPENED - Prayer: $prayerName, Original offset: $originalOffset minutes")
    }

    Box(
        modifier = modifier
            .aspectRatio(1f) // Perfect circle - this should constrain the size
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
                onDrawSurface = glassSurface
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize() // Use matchParentSize to match Box exactly
                .graphicsLayer {
                    // CRITICAL: Ensure no transformations that could offset center
                    translationX = 0f
                    translationY = 0f
                    scaleX = 1f
                    scaleY = 1f
                    rotationZ = 0f
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

                                // Calculate current prayer time angle to initialize from current position
                                val adjustedDateTime = java.time.LocalDateTime.of(java.time.LocalDate.now(), originalTime).plusMinutes(timeAdjustment.toLong())
                                val adjustedTime = adjustedDateTime.toLocalTime()
                                val hourIn12Format = if (adjustedTime.hour % 12 == 0) 12 else adjustedTime.hour % 12
                                val timeAngle = ((hourIn12Format * 60 + adjustedTime.minute) / (12 * 60f)) * 360f - 90f
                                
                                // Start drag angle from finger position for immediate visual feedback
                                // The knob will snap to finger position when dragging starts
                                currentDragAngle = lastAngle
                                
                                // Calculate initial angle difference to track from the start
                                var initialAngleDiff = lastAngle - timeAngle
                                if (initialAngleDiff > 180f) initialAngleDiff -= 360f
                                if (initialAngleDiff < -180f) initialAngleDiff += 360f
                                accumulatedAngle = initialAngleDiff

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
            // ================================================================
            // WATCHFACE DIAL DRAWING (copied from watchface-main Clock.kt)
            // ================================================================
            val outerRadius = minOf(this.size.width, this.size.height) / 2f
            val innerRadius = outerRadius - 60f // dp converted roughly
            val clockStyle = PrayerDialStyle

            // ================================================================
            // CONTROL CENTER STYLE - Glass handled by drawBackdrop
            // No additional background needed - just the dial elements
            // ================================================================

            // Seconds Dial (outer ring with rotating numbers)
            drawWatchfaceDial(
                radius = outerRadius,
                rotation = secondRotation,
                textMeasurer = textMeasurer,
                dialStyle = clockStyle.secondsDialStyle
            )

            // Minutes Dial (inner ring with rotating numbers)
            drawWatchfaceDial(
                radius = innerRadius,
                rotation = minuteRotation,
                textMeasurer = textMeasurer,
                dialStyle = clockStyle.minutesDialStyle
            )

            // Draw hour label in center
            val hourString = String.format("%02d", hour)
            val hourTextMeasureOutput = textMeasurer.measure(
                text = buildAnnotatedString { append(hourString) },
                style = clockStyle.hourLabelStyle
            )
            val hourTopLeft = Offset(
                x = this.center.x - (hourTextMeasureOutput.size.width / 2),
                y = this.center.y - (hourTextMeasureOutput.size.height / 2),
            )
            drawText(
                textMeasurer = textMeasurer,
                text = hourString,
                topLeft = hourTopLeft,
                style = clockStyle.hourLabelStyle
            )

            // Drawing minute-second overlay indicator (pointing to 3 o'clock)
            val minuteHandOverlayPath = Path().apply {
                val startOffset = Offset(
                    x = center.x + (outerRadius * cos(8f * Math.PI / 180f)).toFloat(),
                    y = center.y - (outerRadius * sin(8f * Math.PI / 180f)).toFloat(),
                )
                val endOffset = Offset(
                    x = center.x + (outerRadius * cos(-8f * Math.PI / 180f)).toFloat(),
                    y = center.y - (outerRadius * sin(-8f * Math.PI / 180f)).toFloat(),
                )
                val overlayRadius = (endOffset.y - startOffset.y) / 2f

                val secondsLabelMaxWidth = textMeasurer.measure(
                    text = buildAnnotatedString { append("60") },
                    style = clockStyle.secondsDialStyle.stepsTextStyle
                ).size.width

                val minutesLabelMaxWidth = textMeasurer.measure(
                    text = buildAnnotatedString { append("60") },
                    style = clockStyle.minutesDialStyle.stepsTextStyle
                ).size.width

                val overlayLineX =
                    size.width - clockStyle.secondsDialStyle.fiveStepsLineHeight - clockStyle.secondsDialStyle.stepsLabelTopPadding - secondsLabelMaxWidth - clockStyle.minutesDialStyle.fiveStepsLineHeight - clockStyle.minutesDialStyle.stepsLabelTopPadding - (minutesLabelMaxWidth / 2f)
                moveTo(x = startOffset.x, y = startOffset.y)
                lineTo(x = overlayLineX, y = startOffset.y)
                cubicTo(
                    x1 = overlayLineX - overlayRadius,
                    y1 = startOffset.y,
                    x2 = overlayLineX - overlayRadius,
                    y2 = endOffset.y,
                    x3 = overlayLineX,
                    y3 = endOffset.y
                )
                lineTo(endOffset.x, endOffset.y)
            }

            drawPath(
                path = minuteHandOverlayPath,
                color = clockStyle.overlayStrokeColor,
                style = Stroke(width = clockStyle.overlayStrokeWidth)
            )
        }

        // Removed Apple Lock Screen style buttons - will be integrated into swipeable area

        // Central text container - clean and minimal
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(
                    color = Color.Transparent,
                    shape = CircleShape
                )
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

                // Small indicator showing adjustment amount - always visible
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
                    else -> "0m"  // Show 0m when there's no adjustment
                }

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

                Spacer(modifier = Modifier.height(12.dp))

                // Dynamic hint based on whether user has adjusted from base
                val hasAdjusted = currentAdjustment != baseAdjustment

                if (hasAdjusted) { // Enhanced swipe UI with circular icons
                    // iPhone lock screen style swipe UI with circular buttons
                    Spacer(modifier = Modifier.height(8.dp))

                    // Swipeable area with integrated circular Reset and Save buttons
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 8.dp)
                    ) {
                        // iOS Frosted Glass Background Track
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(32.dp))
                                // Multi-layer glass effect
                                .background(
                                    // Base layer - semi-transparent with blur effect simulation
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                        )
                                    )
                                )
                        )

                        // Glass border overlay for depth
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(32.dp))
                                .border(
                                    BorderStroke(
                                        width = 1.dp,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.5f),
                                                Color.White.copy(alpha = 0.2f),
                                                Color.White.copy(alpha = 0.3f)
                                            )
                                        )
                                    ),
                                    shape = RoundedCornerShape(32.dp)
                                )
                        )

                        // Inner glass shimmer effect
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(1.dp)
                                .clip(RoundedCornerShape(31.dp))
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.2f),
                                            Color.Transparent
                                        ),
                                        radius = 200f,
                                        center = Offset(0.3f, 0.3f)
                                    )
                                )
                        )

                        // Circular Reset Button on the left
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 6.dp)
                                .size(52.dp)
                                .graphicsLayer {
                                    alpha = if (animatedSwipeOffset < 0) {
                                        (kotlin.math.abs(animatedSwipeOffset) / swipeThreshold).coerceIn(0.7f, 1f)
                                    } else 0.5f
                                    scaleX = if (animatedSwipeOffset < -swipeThreshold * 0.8f) 1.1f else 1f
                                    scaleY = if (animatedSwipeOffset < -swipeThreshold * 0.8f) 1.1f else 1f
                                }
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        ),
                                        radius = 80f
                                    ),
                                    shape = CircleShape
                                )
                                .border(
                                    BorderStroke(
                                        width = 0.5.dp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Reset",
                                modifier = Modifier.size(26.dp),
                                tint = Color(0xFFFF9800)
                            )
                        }

                        // Circular Save Button on the right
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 6.dp)
                                .size(52.dp)
                                .graphicsLayer {
                                    alpha = if (animatedSwipeOffset > 0) {
                                        (animatedSwipeOffset / swipeThreshold).coerceIn(0.7f, 1f)
                                    } else 0.5f
                                    scaleX = if (animatedSwipeOffset > swipeThreshold * 0.8f) 1.1f else 1f
                                    scaleY = if (animatedSwipeOffset > swipeThreshold * 0.8f) 1.1f else 1f
                                }
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        ),
                                        radius = 80f
                                    ),
                                    shape = CircleShape
                                )
                                .border(
                                    BorderStroke(
                                        width = 0.5.dp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Save,
                                contentDescription = "Save",
                                modifier = Modifier.size(26.dp),
                                tint = Color(0xFF4CAF50)
                            )
                        }

                        // Swipeable pill with gesture detection
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(currentAdjustment) {
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            isSwipingHorizontally = true
                                            swipeOffset = 0f
                                        },
                                        onDragEnd = {
                                            // Check if swipe reached threshold
                                            coroutineScope.launch {
                                                when {
                                                    swipeOffset < -swipeThreshold -> {
                                                        // Swipe left - Undo/Reset to ORIGINAL value when dial was opened
                                                        Log.d("InteractiveDial", "↶ SWIPE LEFT - RESET - Prayer: $prayerName")
                                                        Log.d("InteractiveDial", "   Resetting to original offset: $originalOffset minutes")
                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        currentAdjustment = originalOffset
                                                        onTimeAdjusted(originalOffset)
                                                        baseAdjustment = originalOffset
                                                        accumulatedAngle = 0f

                                                        // Animate back smoothly
                                                        delay(200) // Wait for animation
                                                    }
                                                    swipeOffset > swipeThreshold -> {
                                                        // Swipe right - Save
                                                        Log.d("InteractiveDial", "✓ SWIPE RIGHT - SAVE - Prayer: $prayerName, Adjustment: ${currentAdjustment}m")
                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                                                        // Let the swipe animation complete before closing
                                                        delay(200) // Wait for animation to complete
                                                        onSaveAdjustment(prayerName, currentAdjustment)
                                                    }
                                                }
                                                // Reset swipe state after animation completes
                                                isSwipingHorizontally = false
                                                swipeOffset = 0f
                                            }
                                        }
                                    ) { change, dragAmount ->
                                        // Update swipe offset within bounds
                                        swipeOffset = (swipeOffset + dragAmount).coerceIn(-180f, 180f)

                                        // Haptic feedback at threshold points
                                        if (kotlin.math.abs(swipeOffset) >= swipeThreshold &&
                                            kotlin.math.abs(swipeOffset - dragAmount) < swipeThreshold) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Swipeable pill indicator - iPhone style with frosted glass effect
                            // Constrain movement to avoid overlapping buttons
                            val maxOffset = 35f // Limit movement to prevent overlap
                            val constrainedOffset = (animatedSwipeOffset / 3f).coerceIn(-maxOffset, maxOffset)

                            // iOS Frosted Glass Pill with proper layering
                            Box(
                                modifier = Modifier
                                    .size(width = 90.dp, height = 50.dp)
                                    .offset(x = constrainedOffset.dp)
                                    .clip(RoundedCornerShape(25.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                // Layer 1: Base glass background
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(
                                            when {
                                                animatedSwipeOffset < -swipeThreshold ->
                                                    Color(0xFFFF9800).copy(alpha = 0.3f) // Orange tint
                                                animatedSwipeOffset > swipeThreshold ->
                                                    Color(0xFF4CAF50).copy(alpha = 0.3f) // Green tint
                                                else ->
                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f) // Neutral
                                            }
                                        )
                                )

                                // Layer 2: Blur effect simulation with gradient
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.4f),
                                                    Color.White.copy(alpha = 0.2f),
                                                    Color.White.copy(alpha = 0.25f)
                                                )
                                            )
                                        )
                                )

                                // Layer 3: Glass shine effect
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.White.copy(alpha = 0.1f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )

                                // Layer 4: Border for definition
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .border(
                                            BorderStroke(
                                                width = 0.8.dp,
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.6f),
                                                        Color.White.copy(alpha = 0.2f)
                                                    )
                                                )
                                            ),
                                            shape = RoundedCornerShape(25.dp)
                                        )
                                )

                                // Text content on top of all glass layers
                                Text(
                                    text = when {
                                        animatedSwipeOffset < -swipeThreshold -> "Reset"
                                        animatedSwipeOffset > swipeThreshold -> "Save"
                                        animatedSwipeOffset < -20 -> "←"
                                        animatedSwipeOffset > 20 -> "→"
                                        else -> "Swipe"
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when {
                                        animatedSwipeOffset < -swipeThreshold -> Color.White.copy(alpha = 0.95f)
                                        animatedSwipeOffset > swipeThreshold -> Color.White.copy(alpha = 0.95f)
                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    },
                                    modifier = Modifier.graphicsLayer {
                                        // Add subtle shadow for text readability
                                        shadowElevation = 2f
                                    }
                                )
                            }
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
    // Ensure perfect centering - use the exact center parameter directly
    // CRITICAL: Both circles MUST use the exact same center point
    val exactCenter = center
    
    val outerRingRadius = radius * 1.15f // Outer ring radius
    val centralKnobRadius = radius * 0.35f // Central knob radius (smaller, no dark center)

    // Colors matching the reference design
    val lightGreyBackground = Color(0xFFF5F5F5) // Very light grey/off-white
    val tealColor = Color(0xFF26C6DA) // Vibrant teal/aqua for all segments (uniform color like reference)

    // Draw outer shadow for depth (soft shadow beneath the entire dial) - centered
    // Subtle grey shadow instead of black for a more natural look
    drawCircle(
        color = Color(0xFF000000).copy(alpha = 0.1f), // Subtle shadow with low opacity
        radius = outerRingRadius + 6f,
        center = exactCenter // Shadow centered, not offset
    )

    // Draw outer segmented ring background (recessed appearance) - perfectly centered
    drawCircle(
        color = lightGreyBackground, // Light grey background for the outer ring
        radius = outerRingRadius,
        center = exactCenter
    )

    // Draw segmented outer ring with markers ON the track
    // Markers are drawn on the ring track itself, not extending outward
    // Matching reference: markers on track, grey or teal colored, uniform size
    val segmentCount = 120 // Number of markers around the circle
    val markerLength = 12f // Length of each marker (drawn on the track)
    val markerWidth = 2.5f // Uniform width for all markers (teal and grey)
    val trackWidth = 16f // Width of the track/ring where markers are drawn
    val inactiveGrey = Color(0xFFE0E0E0) // Light grey for inactive markers - fully opaque
    
    // Calculate actual prayer time (adjusted) for angle calculation
    val adjustedDateTime = LocalDateTime.of(LocalDate.now(), originalTime).plusMinutes(timeAdjustment.toLong())
    val adjustedTime = adjustedDateTime.toLocalTime()

    // Convert time to angle (starting from top - 12 o'clock position)
    val hourIn12Format = if (adjustedTime.hour % 12 == 0) 12 else adjustedTime.hour % 12
    val timeAngle = ((hourIn12Format * 60 + adjustedTime.minute) / (12 * 60f)) * 360f - 90f

    // Draw markers on the track with progress indication
    // Active markers (teal) from 12 o'clock clockwise, inactive markers (grey) for the rest
        val currentAngle = if (isDragging) currentDragAngle else timeAngle
    val normalizedCurrentAngle = ((currentAngle + 90f) % 360 + 360) % 360 // Normalize relative to top (0° = 12 o'clock)

    for (i in 0 until segmentCount) {
        val markerAngle = (i * (360.0 / segmentCount) - 90.0) * PI / 180.0 // Start from top
        val normalizedMarkerAngle = ((i * (360.0 / segmentCount)) % 360).toFloat()

        // Determine if this marker is active (within progress from 12 o'clock clockwise)
        // Sharp transition: teal if <= current angle, grey otherwise
        val isActive = normalizedMarkerAngle <= normalizedCurrentAngle

        // Marker position - drawn ON the track (ring), fully contained within track boundaries
        // Position markers on the track itself, ensuring they stay within track width
        val trackInnerRadius = outerRingRadius - trackWidth / 2f
        val trackOuterRadius = outerRingRadius + trackWidth / 2f
        
        // Draw marker as a vertical dash ON the track, fully contained within track boundaries
        // Marker should be within the track, with some padding from edges
        val markerPadding = 1f // Small padding to ensure markers stay within track
        val markerStartRadius = trackInnerRadius + markerPadding
        val markerEndRadius = trackOuterRadius - markerPadding
        
        val markerStart = Offset(
            exactCenter.x + markerStartRadius * cos(markerAngle.toFloat()).toFloat(),
            exactCenter.y + markerStartRadius * sin(markerAngle.toFloat()).toFloat()
        )
        val markerEnd = Offset(
            exactCenter.x + markerEndRadius * cos(markerAngle.toFloat()).toFloat(),
            exactCenter.y + markerEndRadius * sin(markerAngle.toFloat()).toFloat()
        )

        // Draw marker - uniform width for both teal and grey, sharp color transition
        // Markers are drawn ON the track, not extending beyond it
        drawLine(
            color = if (isActive) {
                tealColor // Vibrant teal for active markers - fully opaque
            } else {
                inactiveGrey // Light grey for inactive markers - fully opaque
            },
            start = markerStart,
            end = markerEnd,
            strokeWidth = markerWidth, // Uniform width for all markers
            cap = StrokeCap.Round
        )
    }

    // Draw teal indicator knob - draggable and follows current time/drag angle
    // Position it on the outer ring based on current time angle
    val indicatorAngleRad = (currentAngle * PI / 180f).toFloat() // Convert to radians

    // FIX: Position knob properly within the track, not overlapping the center
    // Place it exactly in the middle of the track (between inner and outer edges)
    val indicatorRadius = outerRingRadius - trackWidth/2f // Center of the track

    val indicatorCenter = Offset(
        exactCenter.x + indicatorRadius * cos(indicatorAngleRad),
        exactCenter.y + indicatorRadius * sin(indicatorAngleRad)
    )

    // Draw circular knob indicator (larger and more visible for dragging)
    val knobIndicatorRadius = if (isDragging) 14f * knobScale else 10f // Slightly smaller to fit within track

    // Add a subtle white border/ring around the knob for better visibility (draw first)
    drawCircle(
        color = Color.White,
        radius = knobIndicatorRadius + 2f,
        center = indicatorCenter,
        style = Stroke(width = 2f)
    )

    // Draw the knob itself
    drawCircle(
        color = tealColor,
        radius = knobIndicatorRadius,
        center = indicatorCenter
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

// ============================================================================
// WATCHFACE DIAL DRAWING FUNCTION (copied from watchface-main Dial.kt)
// ============================================================================

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawWatchfaceDial(
    radius: Float,
    rotation: Float,
    textMeasurer: TextMeasurer,
    dialStyle: DialStyle = DialStyle()
) {
    var stepsAngle = 0

    // This will draw 60 steps around the dial
    repeat(60) { steps ->

        // fiveStep lineHeight > normalStep lineHeight
        val stepsHeight = if (steps % 5 == 0) {
            dialStyle.fiveStepsLineHeight
        } else {
            dialStyle.normalStepsLineHeight
        }

        // Calculate steps start and end offset
        val stepsStartOffset = Offset(
            x = center.x + (radius * cos((stepsAngle + rotation) * (Math.PI / 180f))).toFloat(),
            y = center.y - (radius * sin((stepsAngle + rotation) * (Math.PI / 180))).toFloat()
        )
        val stepsEndOffset = Offset(
            x = center.x + (radius - stepsHeight) * cos(
                (stepsAngle + rotation) * (Math.PI / 180)
            ).toFloat(),
            y = center.y - (radius - stepsHeight) * sin(
                (stepsAngle + rotation) * (Math.PI / 180)
            ).toFloat()
        )

        // Draw step line
        drawLine(
            color = dialStyle.stepsColor,
            start = stepsStartOffset,
            end = stepsEndOffset,
            strokeWidth = dialStyle.stepsWidth,
            cap = StrokeCap.Round
        )

        // Draw steps labels (every 5th step)
        if (steps % 5 == 0) {
            // Measure the given label width and height
            val stepsLabel = String.format("%02d", steps)
            val stepsLabelTextLayout = textMeasurer.measure(
                text = buildAnnotatedString { append(stepsLabel) },
                style = dialStyle.stepsTextStyle
            )

            // Calculate the offset for label position
            val stepsLabelOffset = Offset(
                x = center.x + (radius - stepsHeight - dialStyle.stepsLabelTopPadding) * cos(
                    (stepsAngle + rotation) * (Math.PI / 180)
                ).toFloat(),
                y = center.y - (radius - stepsHeight - dialStyle.stepsLabelTopPadding) * sin(
                    (stepsAngle + rotation) * (Math.PI / 180)
                ).toFloat()
            )

            // Subtract the label width and height to position label at the center of the step
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

