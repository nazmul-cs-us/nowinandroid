package com.starception.submission.settings.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
        // Engine selection section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Recognition Engine",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            // Modern engine selection cards
            VoiceRecognitionEngine.entries.forEach { engine ->
                ModernEngineCard(
                    engine = engine,
                    isSelected = state.selectedEngine == engine,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEngineSelected(engine)
                    }
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
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "cardBackground"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "cardBorder"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primary),
                onClick = onClick
            ),
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon with gradient background
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = if (isSelected) listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            ) else listOf(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                FlaticonIcon(
                    glyph = engine.iconGlyph,
                    contentDescription = null,
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 24.sp,
                )
            }

            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = engine.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                    // Speed badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (engine == VoiceRecognitionEngine.SHERPA_KWS)
                            GoogleGreen.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = engine.speed,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (engine == VoiceRecognitionEngine.SHERPA_KWS)
                                GoogleGreen
                            else
                                MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = engine.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Selection indicator
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.CHECK,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 16.sp,
                    )
                }
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

    val smoothAmplitude by animateFloatAsState(
        targetValue = amplitude,
        animationSpec = tween(150, easing = LinearEasing),
        label = "smoothAmplitude"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val baseRadius = minOf(size.width, size.height) * 0.35f
        val amp = (smoothAmplitude * 6f).coerceIn(0f, 1f)
        val dynamicRadius = baseRadius * (1f + amp * 0.5f)

        dots.forEach { dot ->
            val rotatedTheta = dot.baseX + rotationAngle * dot.speedMultiplier
            val x3d = sin(dot.baseY) * cos(rotatedTheta)
            val y3d = cos(dot.baseY)
            val z3d = sin(dot.baseY) * sin(rotatedTheta)
            val perspective = 0.5f + (z3d + 1f) * 0.25f
            val projectedX = centerX + x3d * dynamicRadius
            val projectedY = centerY + y3d * dynamicRadius
            val baseSize = dot.size * perspective
            val amplitudeBoost = 1f + amp * 2f
            val finalSize = baseSize * amplitudeBoost
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Full test area as one capsule
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primary)
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isActive) onStopTest() else onTestVoice()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    BubbleSpeakerPad(
                        isActive = isActive,
                        amplitude = amplitude,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Status text inside card
            Spacer(modifier = Modifier.height(12.dp))

            AnimatedContent(
                targetState = testState,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "statusText"
            ) { state ->
                Text(
                    text = when (state) {
                        VoiceTestState.IDLE -> "Tap to test voice recognition"
                        VoiceTestState.LISTENING -> "Listening... Tap to stop"
                        VoiceTestState.PROCESSING -> "Processing..."
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
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
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
    val infiniteTransition = rememberInfiniteTransition(label = "speakerPad")
    val bubblePulse by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bubblePulse"
    )
    val level by animateFloatAsState(
        targetValue = if (isActive) (0.6f + amplitude * 0.25f).coerceIn(0.5f, 0.9f) else 0.35f,
        animationSpec = tween(180),
        label = "level"
    )

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF0D355B),
                        Color(0xFF101A24)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(maxWidth * 0.34f)
                .padding(end = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            GoogleHumVisualization(
                isAnimating = isActive,
                amplitude = amplitude,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width((maxWidth * level).coerceAtMost(maxWidth * 0.72f))
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF0B4B83),
                            Color(0xFF0E3A67)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp)
                .size(94.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            GoogleHumVisualization(
                isAnimating = isActive,
                amplitude = amplitude,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF112A43).copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                DotPlayPauseGlyph(
                    isPause = isActive,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-86).dp, y = (-30).dp)
                .size((8f * bubblePulse).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.75f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-78).dp, y = (-12).dp)
                .size((6f * bubblePulse).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.68f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-70).dp, y = (8).dp)
                .size((5f * bubblePulse).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.6f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-92).dp, y = 24.dp)
                .size((5f * bubblePulse).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.5f))
        )
    }
}

@Composable
private fun DotPlayPauseGlyph(
    isPause: Boolean,
    modifier: Modifier = Modifier
) {
    val dotColor = Color.White
    val dotSize = 4.dp

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isPause) {
            listOf((-7).dp, 7.dp).forEach { xOffset ->
                listOf((-7).dp, 0.dp, 7.dp).forEach { yOffset ->
                    Box(
                        modifier = Modifier
                            .offset(x = xOffset, y = yOffset)
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }
        } else {
            listOf(
                Pair((-8).dp, 0.dp),
                Pair((-2).dp, (-4).dp),
                Pair((-2).dp, 4.dp),
                Pair(4.dp, (-8).dp),
                Pair(4.dp, 0.dp),
                Pair(4.dp, 8.dp)
            ).forEach { (xOffset, yOffset) ->
                Box(
                    modifier = Modifier
                        .offset(x = xOffset, y = yOffset)
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}
