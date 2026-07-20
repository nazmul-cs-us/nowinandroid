package com.starception.submission.feature.prayertimes.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.*
import kotlin.random.Random

/**
 * Enhanced Interactive Prayer Dial with modern visual effects
 * Features:
 * - Glass morphism effects with blur and transparency
 * - Dynamic gradient backgrounds that change based on prayer time
 * - Multi-layered shadow effects for depth perception
 * - Animated glow effects for active elements
 * - Elastic spring animations for knob movement
 * - Ripple effects for touch feedback
 * - Particle effects when adjusting time
 * - Magnetic snapping to 5-minute intervals
 * - Visual time preview with ghost indicator
 * - Quick preset buttons for common adjustments
 * - Auto-save timer after 3 seconds of inactivity
 * - Optimized rendering for 60fps performance
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedInteractivePrayerDial(
    modifier: Modifier = Modifier,
    prayerName: String,
    originalTime: LocalTime,
    timeAdjustment: Int,
    onTimeAdjusted: (Int) -> Unit,
    onSaveAdjustment: (String, Int) -> Unit,
    onResetAdjustment: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // State management
    var isDragging by remember { mutableStateOf(false) }
    var currentAdjustment by remember { mutableStateOf(timeAdjustment) }
    var baseAdjustment by remember { mutableStateOf(timeAdjustment) }
    var lastAngle by remember { mutableStateOf(0f) }
    var currentDragAngle by remember { mutableStateOf(0f) }
    var accumulatedAngle by remember { mutableStateOf(0f) }
    var lastHapticTime by remember { mutableStateOf(0L) }
    var showGhostPreview by remember { mutableStateOf(false) }
    var ghostAngle by remember { mutableStateOf(0f) }

    // Auto-save timer
    var autoSaveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Particle system state
    var particles by remember { mutableStateOf(listOf<Particle>()) }

    // Ripple effect state
    var rippleState by remember { mutableStateOf<RippleState?>(null) }

    // Initialize from timeAdjustment
    LaunchedEffect(Unit) {
        currentAdjustment = timeAdjustment
        baseAdjustment = timeAdjustment
    }

    // Auto-save after 3 seconds of inactivity
    LaunchedEffect(currentAdjustment) {
        if (currentAdjustment != baseAdjustment) {
            autoSaveJob?.cancel()
            autoSaveJob = coroutineScope.launch {
                delay(3000)
                onSaveAdjustment(prayerName, currentAdjustment)
            }
        }
    }

    // Dynamic gradient based on prayer time
    val prayerGradient = remember(prayerName) {
        when (prayerName.lowercase()) {
            "fajr" -> listOf(
                Color(0xFF1A237E), // Deep blue night
                Color(0xFF3949AB), // Morning blue
                Color(0xFF7986CB)  // Light morning
            )
            "dhuhr" -> listOf(
                Color(0xFFFFB300), // Bright sun
                Color(0xFFFFD54F), // Noon yellow
                Color(0xFFFFF59D)  // Light yellow
            )
            "asr" -> listOf(
                Color(0xFFFF6F00), // Afternoon orange
                Color(0xFFFF8F00), // Golden
                Color(0xFFFFB300)  // Light golden
            )
            "maghrib" -> listOf(
                Color(0xFFD32F2F), // Sunset red
                Color(0xFFE64A19), // Orange sunset
                Color(0xFFFF6E40)  // Light sunset
            )
            "isha" -> listOf(
                Color(0xFF1A237E), // Night blue
                Color(0xFF283593), // Deep night
                Color(0xFF303F9F)  // Dark blue
            )
            else -> listOf(
                Color(0xFF00BCD4), // Default cyan
                Color(0xFF00ACC1),
                Color(0xFF0097A7)
            )
        }
    }

    // Enhanced animations with elastic spring effects
    val dialScale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.03f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dialScale"
    )

    val knobScale by animateFloatAsState(
        targetValue = if (isDragging) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "knobScale"
    )

    // Pulsating glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Magnetic snapping helper
    fun snapToInterval(adjustment: Int, interval: Int = 5): Int {
        val remainder = adjustment % interval
        return when {
            abs(remainder) < interval / 2 -> adjustment - remainder
            remainder > 0 -> adjustment + (interval - remainder)
            else -> adjustment - (interval + remainder)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = dialScale
                scaleY = dialScale
                // Add subtle shadow for elevation effect
                shadowElevation = if (isDragging) 24.dp.toPx() else 8.dp.toPx()
                shape = CircleShape
                clip = true
            }
            // Glass morphism background
            .drawBehind {
                // Multi-layered shadows for depth
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.1f),
                            Color.Transparent
                        ),
                        radius = size.minDimension / 2 * 1.2f
                    ),
                    radius = size.minDimension / 2
                )
            }
            .blur(radius = if (isDragging) 0.dp else 0.dp) // Optional blur for background
            .background(
                brush = Brush.radialGradient(
                    colors = prayerGradient.map { it.copy(alpha = 0.15f) },
                    radius = 400f
                ),
                shape = CircleShape
            )
            .border(
                width = 1.dp,
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.6f),
                        Color.White.copy(alpha = 0.2f),
                        Color.White.copy(alpha = 0.6f)
                    )
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        // Main dial canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val distanceFromCenter = sqrt(
                                (offset.x - center.x).pow(2) + (offset.y - center.y).pow(2)
                            )

                            val outerRadius = min(size.width, size.height) * 0.5f
                            if (distanceFromCenter <= outerRadius) {
                                isDragging = true
                                showGhostPreview = true
                                lastAngle = atan2(
                                    offset.y - center.y,
                                    offset.x - center.x
                                ) * 180f / PI.toFloat()
                                accumulatedAngle = 0f

                                // Create ripple effect at touch point
                                rippleState = RippleState(
                                    center = offset,
                                    startTime = System.currentTimeMillis()
                                )

                                // Cancel auto-save timer
                                autoSaveJob?.cancel()

                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDragEnd = {
                            if (isDragging) {
                                isDragging = false
                                showGhostPreview = false

                                // Snap to 5-minute interval with haptic feedback
                                val snappedAdjustment = snapToInterval(currentAdjustment)
                                if (snappedAdjustment != currentAdjustment) {
                                    currentAdjustment = snappedAdjustment
                                    onTimeAdjusted(snappedAdjustment)
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }

                                // Generate celebration particles if significant change
                                if (abs(currentAdjustment - baseAdjustment) > 0) {
                                    particles = generateParticles(20)
                                }

                                // Start auto-save timer
                                autoSaveJob = coroutineScope.launch {
                                    delay(3000)
                                    onSaveAdjustment(prayerName, currentAdjustment)
                                }
                            }
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

                            // Calculate new adjustment with finer control
                            val newAdjustment = baseAdjustment + (accumulatedAngle / 3f).toInt()

                            // Update ghost preview angle
                            ghostAngle = currentDragAngle + 15f // Preview slightly ahead

                            // Haptic feedback for every 5-minute change
                            val currentTime = System.currentTimeMillis()
                            if (newAdjustment != currentAdjustment &&
                                currentTime - lastHapticTime > 50) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastHapticTime = currentTime

                                // Generate small particles on adjustment
                                if (newAdjustment % 5 == 0) {
                                    particles = particles + generateParticles(3)
                                }
                            }

                            currentAdjustment = newAdjustment
                            onTimeAdjusted(newAdjustment)
                            lastAngle = fingerAngle
                        }
                    }
                }
        ) {
            val center = this.center
            val radius = min(size.width, size.height) * 0.4f

            // Draw enhanced dial with all visual effects
            drawEnhancedDial(
                center = center,
                radius = radius,
                timeAdjustment = currentAdjustment,
                originalTime = originalTime,
                isDragging = isDragging,
                currentDragAngle = currentDragAngle,
                knobScale = knobScale,
                glowAlpha = glowAlpha,
                prayerGradient = prayerGradient,
                showGhostPreview = showGhostPreview,
                ghostAngle = ghostAngle,
                particles = particles,
                rippleState = rippleState
            )
        }

        // Update particles
        LaunchedEffect(particles) {
            if (particles.isNotEmpty()) {
                delay(50)
                particles = particles
                    .map { it.update() }
                    .filter { it.alpha > 0 }
            }
        }

        // Update ripple
        LaunchedEffect(rippleState) {
            if (rippleState != null) {
                delay(50)
                val elapsed = System.currentTimeMillis() - rippleState!!.startTime
                if (elapsed > 1000) {
                    rippleState = null
                } else {
                    rippleState = rippleState!!.copy()
                }
            }
        }

        // Central display with glass morphism
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.1f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.3f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Prayer name with gradient text
                Text(
                    text = prayerName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        brush = Brush.linearGradient(
                            colors = prayerGradient
                        )
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Time display with shadow effect
                val adjustedTime = adjustTimeByMinutesForDisplay(originalTime, currentAdjustment)
                Text(
                    text = adjustedTime,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 48.sp,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.3f),
                            offset = Offset(2f, 2f),
                            blurRadius = 4f
                        )
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                // Adjustment indicator with animation
                AnimatedVisibility(
                    visible = currentAdjustment != 0,
                    enter = expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(200, easing = LinearOutSlowInEasing)),
                    exit = shrinkVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
                ) {
                    Text(
                        text = formatAdjustment(currentAdjustment),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        ),
                        color = when {
                            currentAdjustment > 0 -> Color(0xFF4CAF50)
                            currentAdjustment < 0 -> Color(0xFFFF5252)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Quick preset buttons
        if (!isDragging) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Preset adjustment buttons
                listOf(-15, -5, 0, +5, +15).forEach { preset ->
                    NiaOutlinedButton(
                        onClick = {
                            // Undo button (preset == 0) should restore the original offset (baseAdjustment)
                            val newAdjustment = if (preset == 0) baseAdjustment else baseAdjustment + preset
                            currentAdjustment = newAdjustment
                            onTimeAdjusted(newAdjustment)
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                            // Generate particles for feedback
                            particles = generateParticles(10)

                            // Auto-save after preset selection
                            autoSaveJob?.cancel()
                            autoSaveJob = coroutineScope.launch {
                                delay(1500)
                                onSaveAdjustment(prayerName, newAdjustment)
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        if (preset == 0) {
                            Icon(
                                Icons.AutoMirrored.Default.Undo,
                                contentDescription = "Undo - Restore original offset",
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = "${if (preset > 0) "+" else ""}$preset",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// Enhanced dial drawing with visual effects
private fun DrawScope.drawEnhancedDial(
    center: Offset,
    radius: Float,
    timeAdjustment: Int,
    originalTime: LocalTime,
    isDragging: Boolean,
    currentDragAngle: Float,
    knobScale: Float,
    glowAlpha: Float,
    prayerGradient: List<Color>,
    showGhostPreview: Boolean,
    ghostAngle: Float,
    particles: List<Particle>,
    rippleState: RippleState?
) {
    val outerRadius = radius * 1.15f

    // Draw ripple effect if active
    rippleState?.let {
        val elapsed = (System.currentTimeMillis() - it.startTime) / 1000f
        val rippleRadius = elapsed * 200f
        val rippleAlpha = (1f - elapsed).coerceIn(0f, 1f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    prayerGradient[0].copy(alpha = rippleAlpha * 0.3f),
                    Color.Transparent
                ),
                radius = rippleRadius
            ),
            radius = rippleRadius,
            center = it.center
        )
    }

    // Glass morphism outer ring with multiple layers
    // Layer 1: Shadow layer
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.3f),
                Color.Black.copy(alpha = 0.15f),
                Color.Transparent
            ),
            radius = outerRadius + 10f
        ),
        radius = outerRadius + 10f,
        center = center
    )

    // Layer 2: Glass background
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.2f),
                Color.White.copy(alpha = 0.1f)
            ),
            radius = outerRadius
        ),
        radius = outerRadius,
        center = center
    )

    // Layer 3: Glass border with gradient
    drawCircle(
        brush = Brush.sweepGradient(
            colors = prayerGradient.map { it.copy(alpha = 0.5f) } + prayerGradient.first().copy(alpha = 0.5f),
            center = center
        ),
        radius = outerRadius,
        center = center,
        style = Stroke(width = 2f)
    )

    // Calculate time angle
    val adjustedDateTime = LocalDateTime.of(LocalDate.now(), originalTime).plusMinutes(timeAdjustment.toLong())
    val adjustedTime = adjustedDateTime.toLocalTime()
    val hourIn12Format = if (adjustedTime.hour % 12 == 0) 12 else adjustedTime.hour % 12
    val timeAngle = ((hourIn12Format * 60 + adjustedTime.minute) / (12 * 60f)) * 360f - 90f
    val currentAngle = if (isDragging) currentDragAngle else timeAngle

    // Draw tick marks with gradient colors
    val tickCount = 120
    for (i in 0 until tickCount) {
        val tickAngle = (i * 360f / tickCount - 90f) * PI / 180f
        val normalizedAngle = ((i * 360f / tickCount) % 360).toFloat()
        val normalizedCurrentAngle = ((currentAngle + 90f) % 360 + 360) % 360

        val isActive = normalizedAngle <= normalizedCurrentAngle
        val tickColor = if (isActive) {
            val progress = normalizedAngle / normalizedCurrentAngle
            val colorIndex = (progress * (prayerGradient.size - 1)).toInt()
            val colorProgress = (progress * (prayerGradient.size - 1)) % 1

            lerp(
                prayerGradient[colorIndex],
                prayerGradient.getOrElse(colorIndex + 1) { prayerGradient.last() },
                colorProgress
            )
        } else {
            Color.Gray.copy(alpha = 0.3f)
        }

        val tickLength = when {
            i % 30 == 0 -> 20f // Hour marks
            i % 10 == 0 -> 15f // 10-minute marks
            i % 5 == 0 -> 12f  // 5-minute marks
            else -> 8f         // Minute marks
        }

        val tickWidth = when {
            i % 30 == 0 -> 3f
            i % 10 == 0 -> 2f
            else -> 1f
        }

        val innerRadius = outerRadius - 20f
        val startRadius = innerRadius
        val endRadius = innerRadius + tickLength

        drawLine(
            color = tickColor,
            start = Offset(
                center.x + startRadius * cos(tickAngle).toFloat(),
                center.y + startRadius * sin(tickAngle).toFloat()
            ),
            end = Offset(
                center.x + endRadius * cos(tickAngle).toFloat(),
                center.y + endRadius * sin(tickAngle).toFloat()
            ),
            strokeWidth = tickWidth,
            cap = StrokeCap.Round
        )
    }

    // Draw progress arc with animated glow
    drawArc(
        brush = Brush.sweepGradient(
            colors = prayerGradient + prayerGradient.first(),
            center = center
        ),
        startAngle = -90f,
        sweepAngle = ((currentAngle + 90f) % 360),
        useCenter = false,
        style = Stroke(
            width = 8f + if (isDragging) 4f else 0f,
            cap = StrokeCap.Round
        ),
        alpha = glowAlpha
    )

    // Draw ghost preview if dragging
    if (showGhostPreview && isDragging) {
        val ghostAngleRad = (ghostAngle * PI / 180f).toFloat()
        val ghostRadius = outerRadius - 15f
        val ghostCenter = Offset(
            center.x + ghostRadius * cos(ghostAngleRad),
            center.y + ghostRadius * sin(ghostAngleRad)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    prayerGradient[0].copy(alpha = 0.3f),
                    Color.Transparent
                ),
                radius = 20f
            ),
            radius = 20f,
            center = ghostCenter
        )
    }

    // Draw main knob indicator with enhanced effects
    val indicatorAngleRad = (currentAngle * PI / 180f).toFloat()
    val indicatorRadius = outerRadius - 15f
    val indicatorCenter = Offset(
        center.x + indicatorRadius * cos(indicatorAngleRad),
        center.y + indicatorRadius * sin(indicatorAngleRad)
    )

    // Knob glow effect
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                prayerGradient[0].copy(alpha = glowAlpha * 0.5f),
                prayerGradient[1].copy(alpha = glowAlpha * 0.3f),
                Color.Transparent
            ),
            radius = 30f * knobScale
        ),
        radius = 30f * knobScale,
        center = indicatorCenter
    )

    // Main knob with gradient
    val knobRadius = if (isDragging) 18f * knobScale else 14f
    drawCircle(
        brush = Brush.radialGradient(
            colors = prayerGradient,
            radius = knobRadius
        ),
        radius = knobRadius,
        center = indicatorCenter
    )

    // Knob border
    drawCircle(
        color = Color.White,
        radius = knobRadius + 2f,
        center = indicatorCenter,
        style = Stroke(width = 2f)
    )

    // Inner shine
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.8f),
                Color.White.copy(alpha = 0.4f),
                Color.Transparent
            ),
            radius = knobRadius * 0.7f,
            center = indicatorCenter - Offset(knobRadius * 0.2f, knobRadius * 0.2f)
        ),
        radius = knobRadius * 0.5f,
        center = indicatorCenter - Offset(knobRadius * 0.2f, knobRadius * 0.2f)
    )

    // Draw particles
    particles.forEach { particle ->
        drawCircle(
            color = particle.color.copy(alpha = particle.alpha),
            radius = particle.size,
            center = Offset(particle.x, particle.y)
        )
    }
}

// Helper classes and functions
data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var color: Color,
    var alpha: Float,
    var life: Float
) {
    fun update(): Particle {
        x += vx
        y += vy
        vy += 0.5f // gravity
        alpha = (life * 0.8f).coerceIn(0f, 1f)
        life -= 0.05f
        size *= 0.98f
        return this
    }
}

data class RippleState(
    val center: Offset,
    val startTime: Long
)

fun generateParticles(count: Int): List<Particle> {
    return List(count) {
        Particle(
            x = 200f + Random.nextFloat() * 100f - 50f,
            y = 200f,
            vx = Random.nextFloat() * 10f - 5f,
            vy = Random.nextFloat() * -10f - 5f,
            size = Random.nextFloat() * 5f + 2f,
            color = listOf(
                Color(0xFF26C6DA),
                Color(0xFF00ACC1),
                Color(0xFF4CAF50),
                Color(0xFFFFB300)
            ).random(),
            alpha = 1f,
            life = 1f
        )
    }
}

fun formatAdjustment(adjustment: Int): String {
    return when {
        adjustment > 0 -> {
            val hours = adjustment / 60
            val minutes = adjustment % 60
            when {
                hours > 0 && minutes > 0 -> "+${hours}h ${minutes}m"
                hours > 0 -> "+${hours}h"
                else -> "+${minutes}m"
            }
        }
        adjustment < 0 -> {
            val totalMinutes = abs(adjustment)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            when {
                hours > 0 && minutes > 0 -> "-${hours}h ${minutes}m"
                hours > 0 -> "-${hours}h"
                else -> "${adjustment}m"
            }
        }
        else -> ""
    }
}

fun adjustTimeByMinutesForDisplay(originalTime: LocalTime, minutes: Int): String {
    val adjustedDateTime = LocalDateTime.of(
        LocalDate.now(),
        originalTime
    ).plusMinutes(minutes.toLong())

    val adjustedTime = adjustedDateTime.toLocalTime()
    val hour12 = when {
        adjustedTime.hour == 0 -> 12
        adjustedTime.hour <= 12 -> adjustedTime.hour
        else -> adjustedTime.hour - 12
    }
    val amPm = if (adjustedTime.hour < 12) "AM" else "PM"

    return String.format("%02d:%02d %s", hour12, adjustedTime.minute, amPm)
}

// Color interpolation helper
fun lerp(start: Color, end: Color, fraction: Float): Color {
    return Color(
        red = start.red + (end.red - start.red) * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue = start.blue + (end.blue - start.blue) * fraction,
        alpha = start.alpha + (end.alpha - start.alpha) * fraction
    )
}