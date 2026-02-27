package com.starception.submission.settings.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    val speed: String
) {
    SHERPA_KWS(
        displayName = "Fast Keyword Spotting",
        description = "Real-time yes/no detection using Sherpa-ONNX. Optimized for quick hands-free responses.",
        speed = "~100ms"
    ),
    WHISPER(
        displayName = "Whisper.cpp (Full Transcription)",
        description = "Full speech-to-text using native whisper.cpp. Fast and accurate offline transcription.",
        speed = "~2 seconds"
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
    val amplitude: Float = 0f // Real-time audio amplitude (0.0 to 1.0)
)

/**
 * Voice Settings Section - allows users to select voice recognition engine and test it
 */
@Composable
fun VoiceSettingsSection(
    state: VoiceSettingsState,
    onEngineSelected: (VoiceRecognitionEngine) -> Unit,
    onTestVoice: () -> Unit = {},
    onStopTest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.selectableGroup()) {
        Text(
            text = "Voice Recognition Engine",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        VoiceRecognitionEngine.entries.forEach { engine ->
            VoiceEngineCard(
                engine = engine,
                isSelected = state.selectedEngine == engine,
                onSelect = { onEngineSelected(engine) }
            )
            if (engine != VoiceRecognitionEngine.entries.last()) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interactive Voice Test Section
        VoiceTestCard(
            testState = state.testState,
            testResult = state.testResult,
            testError = state.testError,
            amplitude = state.amplitude,
            onTestVoice = onTestVoice,
            onStopTest = onStopTest
        )
    }
}

// Google colors for the dots
private val GoogleBlue = Color(0xFF4285F4)
private val GoogleRed = Color(0xFFEA4335)
private val GoogleYellow = Color(0xFFFBBC05)
private val GoogleGreen = Color(0xFF34A853)

/**
 * Data class representing a single animated dot
 */
private data class AnimatedDot(
    val baseX: Float,      // Base X position (0-1 normalized)
    val baseY: Float,      // Base Y position (0-1 normalized)
    val size: Float,
    val color: Color,
    val phaseOffset: Float,
    val speedMultiplier: Float
)

/**
 * Google Hum-style animated voice visualization
 * Dots arranged in a 3D globe/sphere pattern that rotates and reacts to voice
 */
@Composable
private fun GoogleHumVisualization(
    isAnimating: Boolean,
    amplitude: Float = 0f,
    modifier: Modifier = Modifier
) {
    val googleColors = listOf(GoogleBlue, GoogleRed, GoogleYellow, GoogleGreen)

    // Generate dots positioned on a sphere surface
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

    // Smooth constant rotation - 10 seconds per full rotation
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

    // Smooth amplitude with slower tween for fluid transitions
    val smoothAmplitude by animateFloatAsState(
        targetValue = amplitude,
        animationSpec = tween(150, easing = LinearEasing),
        label = "smoothAmplitude"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
    ) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val baseRadius = minOf(size.width, size.height) * 0.35f

        // Amplify amplitude for visible effect
        val amp = (smoothAmplitude * 6f).coerceIn(0f, 1f)

        // Globe expands smoothly with amplitude
        val dynamicRadius = baseRadius * (1f + amp * 0.5f)

        dots.forEach { dot ->
            // Constant smooth rotation
            val rotatedTheta = dot.baseX + rotationAngle * dot.speedMultiplier

            // Convert spherical to 3D
            val x3d = sin(dot.baseY) * cos(rotatedTheta)
            val y3d = cos(dot.baseY)
            val z3d = sin(dot.baseY) * sin(rotatedTheta)

            // Project to 2D with perspective
            val perspective = 0.5f + (z3d + 1f) * 0.25f
            val projectedX = centerX + x3d * dynamicRadius
            val projectedY = centerY + y3d * dynamicRadius

            // Size: base + depth + amplitude boost
            val baseSize = dot.size * perspective
            val amplitudeBoost = 1f + amp * 2f
            val finalSize = baseSize * amplitudeBoost

            // Alpha based on depth
            val alpha = (0.4f + perspective * 0.6f).coerceIn(0f, 1f)

            drawCircle(
                color = dot.color.copy(alpha = alpha),
                radius = finalSize,
                center = Offset(projectedX, projectedY)
            )
        }
    }
}

@Composable
private fun VoiceTestCard(
    testState: VoiceTestState,
    testResult: String?,
    testError: String?,
    amplitude: Float,
    onTestVoice: () -> Unit,
    onStopTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = testState == VoiceTestState.LISTENING || testState == VoiceTestState.PROCESSING

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Google Hum visualization - dots scattered across the area
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable(onClick = if (isActive) onStopTest else onTestVoice)
            ) {

                // Animated dots visualization - reactive to voice amplitude
                GoogleHumVisualization(
                    isAnimating = isActive,
                    amplitude = amplitude,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Status text
            AnimatedContent(
                targetState = testState,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "statusText"
            ) { state ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when (state) {
                            VoiceTestState.IDLE -> "Tap to test voice recognition"
                            VoiceTestState.LISTENING -> "Listening..."
                            VoiceTestState.PROCESSING -> "Processing with Whisper.cpp"
                            VoiceTestState.SUCCESS -> if (testResult != null) "\"$testResult\"" else "Recognized"
                            VoiceTestState.ERROR -> testError ?: "Error"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = when (state) {
                            VoiceTestState.ERROR -> GoogleRed
                            VoiceTestState.SUCCESS -> GoogleGreen
                            VoiceTestState.LISTENING -> GoogleBlue
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceEngineCard(
    engine: VoiceRecognitionEngine,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onSelect
            )
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null // handled by selectable
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = engine.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = engine.speed,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (engine == VoiceRecognitionEngine.SHERPA_KWS) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = engine.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
