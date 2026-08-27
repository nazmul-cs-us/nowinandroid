package com.starception.submission.settings.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import android.Manifest
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Voice recognition engine options
 */
enum class VoiceRecognitionEngine(
    val displayName: String,
    val description: String,
    val speed: String,
    val iconGlyph: String
) {
    SHERPA_KWS(
        displayName = "Fast Keywords",
        description = "Real-time yes/no detection, optimized for hands-free responses",
        speed = "~100ms",
        iconGlyph = FlaticonIcons.QUICK_ACTION
    ),
    WHISPER(
        displayName = "Full Transcription",
        description = "Complete speech-to-text using Whisper.cpp for accurate offline transcription",
        speed = "~2 sec",
        iconGlyph = FlaticonIcons.VOICE
    )
}

/**
 * Voice test state
 */
enum class VoiceTestState {
    IDLE,
    LISTENING,
    PROCESSING,
    SUCCESS,
    ERROR
}

/**
 * Voice settings state
 */
data class VoiceSettingsState(
    val selectedEngine: VoiceRecognitionEngine = VoiceRecognitionEngine.SHERPA_KWS,
    val testState: VoiceTestState = VoiceTestState.IDLE,
    val testResult: String? = null,
    val testError: String? = null,
    val amplitude: Float = 0f,
    val needsDownload: Boolean = false,
    val downloadCategory: String? = null,
)

// Google colors for the dots
private val GoogleBlue = Color(0xFF4285F4)
private val GoogleRed = Color(0xFFEA4335)
private val GoogleYellow = Color(0xFFFBBC05)
private val GoogleGreen = Color(0xFF34A853)

/**
 * Modern Voice Settings Section with Material 3 design
 */
@Composable
fun VoiceSettingsSection(
    state: VoiceSettingsState,
    onEngineSelected: (VoiceRecognitionEngine) -> Unit,
    onTestVoice: () -> Unit = {},
    onStopTest: () -> Unit = {},
    downloadManager: com.starception.submission.download.AssetDownloadManager? = null,
    onDownloadComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // Permission launcher for RECORD_AUDIO
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onTestVoice()
        }
    }

    // Wrapper that checks/requests permission before starting voice test
    val onTestVoiceWithPermission: () -> Unit = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            onTestVoice()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Keep the engine choice compact so the voice test remains the focus.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Recognition mode",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VoiceRecognitionEngine.entries.forEach { engine ->
                    ModernEngineCard(
                        engine = engine,
                        isSelected = state.selectedEngine == engine,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEngineSelected(engine)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            AnimatedContent(
                targetState = state.selectedEngine,
                transitionSpec = {
                    fadeIn(tween(260)) togetherWith fadeOut(tween(180))
                },
                label = "selectedVoiceEngineDescription",
            ) { engine ->
                Text(
                    text = engine.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        // Download prompt when model is missing
        if (state.needsDownload && state.downloadCategory != null && downloadManager != null) {
            com.starception.submission.download.MissingContentCard(
                resourceName = when (state.selectedEngine) {
                    VoiceRecognitionEngine.WHISPER -> "Whisper STT Engine"
                    VoiceRecognitionEngine.SHERPA_KWS -> "Keyword Detection Model"
                },
                category = state.downloadCategory,
                description = when (state.selectedEngine) {
                    VoiceRecognitionEngine.WHISPER -> "Download the speech recognition model for full transcription"
                    VoiceRecognitionEngine.SHERPA_KWS -> "Download the keyword detection model for fast voice commands"
                },
                downloadManager = downloadManager,
                onDownloadComplete = onDownloadComplete,
                modifier = Modifier.padding(horizontal = 0.dp)
            )
        } else {
            // Voice test section (only show when model is available)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Test Voice Recognition",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                ModernVoiceTestCard(
                    selectedEngine = state.selectedEngine,
                    testState = state.testState,
                    testResult = state.testResult,
                    testError = state.testError,
                    amplitude = state.amplitude,
                    onTestVoice = onTestVoiceWithPermission,
                    onStopTest = onStopTest
                )
            }
        }
    }
}

@Composable
private fun ModernEngineCard(
    engine: VoiceRecognitionEngine,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "cardBackground"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
        else
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "cardBorder"
    )

    Surface(
        modifier = modifier
            .height(82.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primary),
                onClick = onClick
            ),
        color = backgroundColor,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    FlaticonIcon(
                        glyph = engine.iconGlyph,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (isSelected) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.CHECK,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (engine == VoiceRecognitionEngine.SHERPA_KWS) {
                        "Keywords"
                    } else {
                        "Transcription"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = engine.speed,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Data class representing a single animated dot
 */
private data class AnimatedDot(
    val baseX: Float,
    val baseY: Float,
    val size: Float,
    val color: Color,
    val phaseOffset: Float,
    val speedMultiplier: Float
)

/**
 * Google Hum-style animated voice visualization
 */
@Composable
private fun GoogleHumVisualization(
    isAnimating: Boolean,
    amplitude: Float = 0f,
    modifier: Modifier = Modifier
) {
    val googleColors = listOf(GoogleBlue, GoogleRed, GoogleYellow, GoogleGreen)

    val dots = remember {
        val random = Random(42)
        val numDots = 60
        val goldenRatio = (1.0 + kotlin.math.sqrt(5.0)) / 2.0

        List(numDots) { index ->
            val i = index.toFloat()
            val theta = 2f * PI.toFloat() * i / goldenRatio.toFloat()
            val phi = kotlin.math.acos(1f - 2f * (i + 0.5f) / numDots).toFloat()

            AnimatedDot(
                baseX = theta,
                baseY = phi,
                size = 4f + random.nextFloat() * 8f,
                color = googleColors[index % googleColors.size],
                phaseOffset = random.nextFloat() * 2f * PI.toFloat(),
                speedMultiplier = 0.9f + random.nextFloat() * 0.2f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "globeRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    val activityLevel by animateFloatAsState(
        targetValue = if (isAnimating) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "globeActivityLevel",
    )

    val smoothAmplitude by animateFloatAsState(
        targetValue = amplitude,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "smoothAmplitude"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val baseRadius = minOf(size.width, size.height) * 0.35f
        val amp = (smoothAmplitude * 6f).coerceIn(0f, 1f)
        val dynamicRadius = baseRadius * (1f + amp * 0.5f)

        dots.forEach { dot ->
            // The primary rotation must advance by exactly one full turn so the
            // 2π -> 0 loop boundary is visually identical. Multiplying the angle
            // by each dot's speed caused the previous ten-second reset snap.
            val organicDrift = sin(rotationAngle + dot.phaseOffset) *
                (dot.speedMultiplier - 1f) *
                (0.5f + activityLevel * 0.4f)
            val rotatedTheta = dot.baseX + rotationAngle + organicDrift
            val x3d = sin(dot.baseY) * cos(rotatedTheta)
            val y3d = cos(dot.baseY)
            val z3d = sin(dot.baseY) * sin(rotatedTheta)
            val perspective = 0.5f + (z3d + 1f) * 0.25f
            val projectedX = centerX + x3d * dynamicRadius
            val projectedY = centerY + y3d * dynamicRadius
            val baseSize = dot.size * perspective
            val amplitudeBoost = 1f + amp * 2f
            val particlePulse = 1f +
                sin(rotationAngle * 2f + dot.phaseOffset) * 0.035f * activityLevel
            val finalSize = baseSize * amplitudeBoost * particlePulse
            val alpha = (0.4f + perspective * 0.6f).coerceIn(0f, 1f)

            drawCircle(
                color = dot.color.copy(alpha = alpha),
                radius = finalSize,
                center = Offset(projectedX, projectedY)
            )
        }
    }
}

/**
 * Animated mic button with dynamic shape morphing based on state
 * - Idle: Rounded square with subtle pulse
 * - Listening: Morphs to circle with expanding rings
 * - Processing: Rotating gradient
 */
@Composable
private fun AnimatedMicButton(
    isActive: Boolean,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")

    // Corner radius animation (square to circle morph)
    val cornerRadius by animateFloatAsState(
        targetValue = if (isActive) 50f else 30f,  // 30% rounded when idle, full circle when active
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cornerRadius"
    )

    // Scale animation
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    // Pulsing animation for idle state
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idlePulse"
    )

    // Rotation for processing state
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Ring animations for listening
    val ring1Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1"
    )
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1Alpha"
    )
    val ring2Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing, delayMillis = 400),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2"
    )
    val ring2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing, delayMillis = 400),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2Alpha"
    )

    // Dynamic amplitude response
    val amplitudeScale by animateFloatAsState(
        targetValue = 1f + (amplitude * 0.2f),
        animationSpec = tween(50),
        label = "amplitudeScale"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isActive) 1.08f + (amplitude * 0.08f) else 1f,
        animationSpec = tween(120),
        label = "iconScale"
    )

    val buttonSize = 60.dp
    val finalScale = if (isActive) scale * amplitudeScale else idlePulse

    // Use theme colors
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier.size(90.dp),
        contentAlignment = Alignment.Center
    ) {
        // Expanding rings when active
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(buttonSize * ring1Scale)
                    .clip(RoundedCornerShape(cornerRadius.toInt()))
                    .background(primaryColor.copy(alpha = ring1Alpha))
            )
            Box(
                modifier = Modifier
                    .size(buttonSize * ring2Scale)
                    .clip(RoundedCornerShape(cornerRadius.toInt()))
                    .background(secondaryColor.copy(alpha = ring2Alpha))
            )
        }

        // Main button with dynamic shape
        Box(
            modifier = Modifier
                .size(buttonSize * finalScale)
                .clip(RoundedCornerShape(cornerRadius.toInt()))
                .background(
                    brush = Brush.sweepGradient(
                        colors = if (isActive)
                            listOf(primaryColor, secondaryColor, tertiaryColor, primaryColor)
                        else
                            listOf(primaryColor, secondaryColor, primaryColor),
                        center = Offset(0.5f, 0.5f)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Inner circle for better visual
            Box(
                modifier = Modifier
                    .size((buttonSize.value * 0.85f).dp)
                    .clip(RoundedCornerShape((cornerRadius * 0.9f).toInt()))
                    .background(
                        if (isActive)
                            Color.White.copy(alpha = 0.15f)
                        else
                            Color.White.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(contentAlignment = Alignment.Center) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.ANNOUNCEMENT,
                        contentDescription = if (isActive) "Listening" else "Tap to speak",
                        tint = Color.White,
                        fontSize = (28f * iconScale).sp,
                    )

                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = 5.dp, y = (-5).dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = (0.25f + ring1Alpha).coerceIn(0f, 1f)))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = 9.dp, y = 4.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = (0.2f + ring2Alpha).coerceIn(0f, 1f)))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernVoiceTestCard(
    selectedEngine: VoiceRecognitionEngine,
    testState: VoiceTestState,
    testResult: String?,
    testError: String?,
    amplitude: Float,
    onTestVoice: () -> Unit,
    onStopTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = testState == VoiceTestState.LISTENING || testState == VoiceTestState.PROCESSING
    val haptic = LocalHapticFeedback.current
    val statusColor = when (testState) {
        VoiceTestState.ERROR -> MaterialTheme.colorScheme.error
        VoiceTestState.SUCCESS -> MaterialTheme.colorScheme.tertiary
        VoiceTestState.LISTENING -> MaterialTheme.colorScheme.primary
        VoiceTestState.PROCESSING -> MaterialTheme.colorScheme.secondary
        VoiceTestState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusTitle = when (testState) {
        VoiceTestState.IDLE -> "Ready to listen"
        VoiceTestState.LISTENING -> "Listening"
        VoiceTestState.PROCESSING -> "Creating transcript"
        VoiceTestState.SUCCESS -> "Voice recognized"
        VoiceTestState.ERROR -> "Try that again"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f),
                shape = RoundedCornerShape(22.dp),
            ),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = if (selectedEngine == VoiceRecognitionEngine.WHISPER) {
                            "Offline · Full"
                        } else {
                            "Offline · Fast"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(
                            bounded = true,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (isActive) onStopTest() else onTestVoice()
                    },
                contentAlignment = Alignment.Center,
            ) {
                BubbleSpeakerPad(
                    isActive = isActive,
                    amplitude = amplitude,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AnimatedContent(
                targetState = testState,
                transitionSpec = {
                    fadeIn(tween(300, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(200))
                },
                label = "statusText",
            ) { state ->
                Text(
                    text = when (state) {
                        VoiceTestState.IDLE -> "Tap play, then speak naturally"
                        VoiceTestState.LISTENING -> "Speak now · Tap again to stop"
                        VoiceTestState.PROCESSING -> "Processing securely on this device"
                        VoiceTestState.SUCCESS -> if (testResult != null) "\"$testResult\"" else "Recognized"
                        VoiceTestState.ERROR -> testError ?: "Error occurred"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = when (state) {
                        VoiceTestState.ERROR -> MaterialTheme.colorScheme.error
                        VoiceTestState.SUCCESS -> MaterialTheme.colorScheme.primary
                        VoiceTestState.LISTENING -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BubbleSpeakerPad(
    isActive: Boolean,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val stageStart = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
    val stageEnd = MaterialTheme.colorScheme.surfaceContainerHigh
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        stageStart,
                        stageEnd,
                    ),
                )
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(maxWidth * 0.43f)
                .padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            GoogleHumVisualization(
                isAnimating = isActive,
                amplitude = amplitude,
                modifier = Modifier.fillMaxSize()
            )
        }

        VoiceStartStopButton(
            isActive = isActive,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp),
        )
    }
}

@Composable
private fun VoiceStartStopButton(
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val ringColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "voiceControlRingColor",
    )
    val coreColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "voiceControlCoreColor",
    )
    val glyphColor = if (isActive) {
        MaterialTheme.colorScheme.onSecondary
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    val iconMorphProgress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "voiceControlIconMorph",
    )
    val density = LocalDensity.current
    val shadowBlurPx = with(density) { 5.dp.toPx() }
    val shadowPaint = remember(shadowBlurPx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(48, 0, 0, 0)
            maskFilter = BlurMaskFilter(shadowBlurPx, BlurMaskFilter.Blur.NORMAL)
        }
    }

    Canvas(modifier = modifier.size(82.dp)) {
        val outerRadius = 32.dp.toPx()
        val coreRadius = 25.dp.toPx()

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawCircle(
                center.x,
                center.y + 3.dp.toPx(),
                outerRadius,
                shadowPaint,
            )
        }
        drawCircle(color = ringColor, radius = outerRadius)
        drawCircle(color = coreColor, radius = coreRadius)
        drawArc(
            color = Color.White.copy(alpha = 0.16f),
            startAngle = 205f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(center.x - coreRadius, center.y - coreRadius),
            size = Size(coreRadius * 2f, coreRadius * 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
        )

        val playAlpha = (1f - iconMorphProgress * 1.45f).coerceIn(0f, 1f)
        if (playAlpha > 0f) {
            val playPath = Path().apply {
                moveTo(center.x - 6.dp.toPx(), center.y - 10.dp.toPx())
                quadraticTo(
                    center.x - 6.dp.toPx(),
                    center.y - 12.dp.toPx(),
                    center.x - 3.5.dp.toPx(),
                    center.y - 10.5.dp.toPx(),
                )
                lineTo(center.x + 11.dp.toPx(), center.y - 1.5.dp.toPx())
                quadraticTo(
                    center.x + 13.dp.toPx(),
                    center.y,
                    center.x + 11.dp.toPx(),
                    center.y + 1.5.dp.toPx(),
                )
                lineTo(center.x - 3.5.dp.toPx(), center.y + 10.5.dp.toPx())
                quadraticTo(
                    center.x - 6.dp.toPx(),
                    center.y + 12.dp.toPx(),
                    center.x - 6.dp.toPx(),
                    center.y + 10.dp.toPx(),
                )
                close()
            }
            drawPath(playPath, color = glyphColor, alpha = playAlpha)
        }

        val pauseAlpha = ((iconMorphProgress - 0.18f) / 0.82f).coerceIn(0f, 1f)
        if (pauseAlpha > 0f) {
            val barWidth = 5.dp.toPx()
            val barHeight = (8.dp + 13.dp * pauseAlpha).toPx()
            val gap = 4.dp.toPx()
            listOf(center.x - gap / 2f - barWidth, center.x + gap / 2f).forEach { left ->
                drawRoundRect(
                    color = glyphColor,
                    topLeft = Offset(left, center.y - barHeight / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(2.5.dp.toPx()),
                    alpha = pauseAlpha,
                )
            }
        }
    }
}

@Suppress("unused")
@Composable
private fun LegacyVoiceStartStopButton(
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val outerColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFF5872AE) else Color(0xFF4A6299),
        animationSpec = tween(180),
        label = "voiceControlRing",
    )
    val iconMorphProgress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "voiceControlIconMorph",
    )
    val density = LocalDensity.current
    val ambientShadowBlurPx = with(density) { 4.5.dp.toPx() }
    val spotShadowBlurPx = with(density) { 8.dp.toPx() }
    val innerShadowBlurPx = with(density) { 3.dp.toPx() }
    val shadowOffsetPx = with(density) { 4.5.dp.toPx() }
    val innerShadowOffsetPx = with(density) { 1.5.dp.toPx() }
    val ambientShadowPaint = remember(ambientShadowBlurPx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(82, 0, 0, 0)
            maskFilter = BlurMaskFilter(ambientShadowBlurPx, BlurMaskFilter.Blur.NORMAL)
        }
    }
    val spotShadowPaint = remember(spotShadowBlurPx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(168, 0, 0, 0)
            maskFilter = BlurMaskFilter(spotShadowBlurPx, BlurMaskFilter.Blur.NORMAL)
        }
    }
    val innerShadowPaint = remember(innerShadowBlurPx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(112, 0, 0, 0)
            maskFilter = BlurMaskFilter(innerShadowBlurPx, BlurMaskFilter.Blur.NORMAL)
        }
    }

    Canvas(
        modifier = modifier
            .size(108.dp),
    ) {
        val controlRadius = 40.dp.toPx()
        val ringWidth = 8.dp.toPx()
        val innerRadius = controlRadius - ringWidth

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawCircle(
                center.x,
                center.y,
                controlRadius - 0.5.dp.toPx(),
                ambientShadowPaint,
            )
            canvas.nativeCanvas.drawCircle(
                center.x,
                center.y + shadowOffsetPx,
                controlRadius - 1.dp.toPx(),
                spotShadowPaint,
            )
        }

        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF5A72AD),
                    outerColor.copy(alpha = 0.96f),
                    Color(0xFF2D426F),
                ),
                start = Offset(center.x, center.y - controlRadius),
                end = Offset(center.x, center.y + controlRadius),
            ),
            radius = controlRadius,
        )
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawCircle(
                center.x,
                center.y + innerShadowOffsetPx,
                innerRadius - 0.5.dp.toPx(),
                innerShadowPaint,
            )
        }
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF38393F), Color(0xFF303137), Color(0xFF292B30)),
                start = Offset(center.x, center.y - innerRadius),
                end = Offset(center.x, center.y + innerRadius),
            ),
            radius = innerRadius,
        )
        val innerDiscTopLeft = Offset(center.x - innerRadius, center.y - innerRadius)
        val innerDiscSize = Size(innerRadius * 2f, innerRadius * 2f)
        drawArc(
            color = Color.White.copy(alpha = 0.10f),
            startAngle = 195f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = innerDiscTopLeft,
            size = innerDiscSize,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx()),
        )
        drawArc(
            color = Color.Black.copy(alpha = 0.18f),
            startAngle = 15f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = innerDiscTopLeft,
            size = innerDiscSize,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
        )

        val glyphBrush = Brush.linearGradient(
            colors = listOf(Color(0xFF79A4F2), Color(0xFF527BD0)),
            start = Offset(center.x, center.y - 18.dp.toPx()),
            end = Offset(center.x, center.y + 18.dp.toPx()),
        )

        val playAlpha = (1f - iconMorphProgress * 1.55f).coerceIn(0f, 1f)
        if (playAlpha > 0f) {
            val playScale = 1f - iconMorphProgress * 0.12f
            val left = center.x - 7.5.dp.toPx() * playScale
            val top = center.y - 11.5.dp.toPx() * playScale
            val bottom = center.y + 11.5.dp.toPx() * playScale
            val tip = center.x + 14.dp.toPx() * playScale
            val cornerInset = 3.dp.toPx() * playScale
            val playPath = Path().apply {
                moveTo(left, top + cornerInset)
                quadraticTo(left, top, left + cornerInset, top + cornerInset * 0.55f)
                lineTo(tip - cornerInset, center.y - cornerInset * 0.7f)
                quadraticTo(tip, center.y, tip - cornerInset, center.y + cornerInset * 0.7f)
                lineTo(left + cornerInset, bottom - cornerInset * 0.55f)
                quadraticTo(left, bottom, left, bottom - cornerInset)
                close()
            }
            // A soft two-stage shadow and a faint upper rim lift the glyph away
            // from the dark inner disc without making it look outlined.
            translate(top = 2.dp.toPx()) {
                drawPath(path = playPath, color = Color.Black.copy(alpha = 0.18f * playAlpha))
            }
            translate(top = 1.dp.toPx()) {
                drawPath(path = playPath, color = Color.Black.copy(alpha = 0.30f * playAlpha))
            }
            drawPath(path = playPath, brush = glyphBrush, alpha = playAlpha)
            translate(top = (-0.6).dp.toPx()) {
                drawPath(
                    path = playPath,
                    color = Color.White.copy(alpha = 0.10f * playAlpha),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.8.dp.toPx()),
                )
            }
        }

        val pauseAlpha = ((iconMorphProgress - 0.18f) / 0.82f).coerceIn(0f, 1f)
        if (pauseAlpha > 0f) {
            val barWidth = 5.5.dp.toPx()
            val barHeight = (7.dp + 16.dp * pauseAlpha).toPx()
            val barGap = (1.dp + 3.dp * pauseAlpha).toPx()
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.30f * pauseAlpha),
                topLeft = Offset(
                    center.x - barGap / 2f - barWidth,
                    center.y - barHeight / 2f + 1.5.dp.toPx(),
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.30f * pauseAlpha),
                topLeft = Offset(
                    center.x + barGap / 2f,
                    center.y - barHeight / 2f + 1.5.dp.toPx(),
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            drawRoundRect(
                brush = glyphBrush,
                topLeft = Offset(
                    center.x - barGap / 2f - barWidth,
                    center.y - barHeight / 2f,
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx()),
                alpha = pauseAlpha,
            )
            drawRoundRect(
                brush = glyphBrush,
                topLeft = Offset(
                    center.x + barGap / 2f,
                    center.y - barHeight / 2f,
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx()),
                alpha = pauseAlpha,
            )
        }
    }
}
