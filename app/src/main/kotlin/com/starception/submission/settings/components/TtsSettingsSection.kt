package com.starception.submission.settings.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * TTS test state
 */
enum class TtsTestState {
    IDLE,
    INITIALIZING,
    SPEAKING,
    SUCCESS,
    ERROR
}

/**
 * TTS model type
 */
enum class TtsModelType {
    VITS,   // Uses OfflineTtsVitsModelConfig
    KOKORO  // Uses OfflineTtsKokoroModelConfig
}

/**
 * Available TTS voice models
 */
enum class TtsVoice(
    val displayName: String,
    val description: String,
    val isMultiSpeaker: Boolean,
    val totalSpeakers: Int,
    val modelType: TtsModelType,
    val modelFile: String,
    val tokensFile: String,
    val dataDir: String,  // For espeak-ng models
    val lexiconFile: String,  // For lexicon-based models (VITS)
    val voicesFile: String  // For Kokoro models
) {
    KOKORO_EN(
        displayName = "Kokoro (10 Voices)",
        description = "High-quality English TTS with 10 natural voices",
        isMultiSpeaker = true,
        totalSpeakers = 10,
        modelType = TtsModelType.KOKORO,
        modelFile = "kokoro-int8-en-v0_19/model.int8.onnx",
        tokensFile = "kokoro-int8-en-v0_19/tokens.txt",
        dataDir = "kokoro-int8-en-v0_19/espeak-ng-data",
        lexiconFile = "",
        voicesFile = "kokoro-int8-en-v0_19/voices.bin"
    ),
    VITS_VCTK(
        displayName = "VCTK (109 Speakers)",
        description = "Multi-speaker model with 109 English voices (British accents)",
        isMultiSpeaker = true,
        totalSpeakers = 109,
        modelType = TtsModelType.VITS,
        modelFile = "vits-vctk/vits-vctk.int8.onnx",
        tokensFile = "vits-vctk/tokens.txt",
        dataDir = "",
        lexiconFile = "vits-vctk/lexicon.txt",
        voicesFile = ""
    )
}

/**
 * TTS settings state
 */
data class TtsSettingsState(
    val testState: TtsTestState = TtsTestState.IDLE,
    val testError: String? = null,
    val selectedVoice: TtsVoice = TtsVoice.KOKORO_EN,
    val selectedSpeakerId: Int = 0,
    // Kokoro (high quality) and VITS-VCTK (109 speakers) available
    val availableVoices: List<TtsVoice> = listOf(TtsVoice.KOKORO_EN, TtsVoice.VITS_VCTK)
)

// Warm gradient colors for TTS visualization
private val WarmOrange = Color(0xFFFF6B35)
private val WarmCoral = Color(0xFFFF8A65)
private val WarmYellow = Color(0xFFFFB74D)
private val WarmPink = Color(0xFFFF7043)

/**
 * TTS Settings Section - allows users to test text-to-speech and select voice/speaker
 */
@Composable
fun TtsSettingsSection(
    state: TtsSettingsState,
    onTestTts: () -> Unit = {},
    onStopTts: () -> Unit = {},
    onVoiceChanged: (TtsVoice) -> Unit = {},
    onSpeakerChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Text-to-Speech Engine",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Voice Selection Cards
        state.availableVoices.forEach { voice ->
            val isSelected = state.selectedVoice == voice
            VoiceOptionCard(
                voice = voice,
                isSelected = isSelected,
                onClick = { onVoiceChanged(voice) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Speaker Selection Card (only for multi-speaker models)
        if (state.selectedVoice.isMultiSpeaker) {
            Spacer(modifier = Modifier.height(8.dp))
            SpeakerSelectionCard(
                selectedSpeaker = state.selectedSpeakerId,
                totalSpeakers = state.selectedVoice.totalSpeakers,
                onSpeakerChanged = onSpeakerChanged
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interactive TTS Test Card
        TtsTestCard(
            testState = state.testState,
            testError = state.testError,
            onTestTts = onTestTts,
            onStopTts = onStopTts
        )
    }
}

/**
 * Voice option card for selecting TTS voice
 */
@Composable
private fun VoiceOptionCard(
    voice: TtsVoice,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Radio indicator
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .padding(2.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Outer circle
                    drawCircle(
                        color = if (isSelected) WarmOrange else Color.Gray.copy(alpha = 0.5f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                    // Inner filled circle when selected
                    if (isSelected) {
                        drawCircle(
                            color = WarmOrange,
                            radius = size.minDimension / 4
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = voice.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = voice.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Speaker selection card with slider and +/- buttons
 */
@Composable
private fun SpeakerSelectionCard(
    selectedSpeaker: Int,
    totalSpeakers: Int,
    onSpeakerChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Voice",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (selectedSpeaker > 0) onSpeakerChanged(selectedSpeaker - 1)
                        },
                        enabled = selectedSpeaker > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous speaker",
                            tint = if (selectedSpeaker > 0)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }

                    Text(
                        text = "Speaker ${selectedSpeaker + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(100.dp),
                        textAlign = TextAlign.Center
                    )

                    IconButton(
                        onClick = {
                            if (selectedSpeaker < totalSpeakers - 1) onSpeakerChanged(selectedSpeaker + 1)
                        },
                        enabled = selectedSpeaker < totalSpeakers - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next speaker",
                            tint = if (selectedSpeaker < totalSpeakers - 1)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Slider for quick selection
            Slider(
                value = selectedSpeaker.toFloat(),
                onValueChange = { onSpeakerChanged(it.toInt()) },
                valueRange = 0f..(totalSpeakers - 1).toFloat(),
                steps = totalSpeakers - 2,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "1",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$totalSpeakers",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Data class representing a single animated wave particle
 */
private data class WaveParticle(
    val baseX: Float,
    val baseY: Float,
    val size: Float,
    val color: Color,
    val phaseOffset: Float,
    val speedMultiplier: Float
)

/**
 * Animated sound wave visualization for TTS
 */
@Composable
private fun SoundWaveVisualization(
    isAnimating: Boolean,
    modifier: Modifier = Modifier
) {
    val warmColors = listOf(WarmOrange, WarmCoral, WarmYellow, WarmPink)

    // Generate particles in a wave pattern
    val particles = remember {
        val random = Random(123)
        val numParticles = 50

        List(numParticles) { index ->
            val normalizedX = index.toFloat() / numParticles
            WaveParticle(
                baseX = normalizedX,
                baseY = 0.5f,
                size = 6f + random.nextFloat() * 10f,
                color = warmColors[index % warmColors.size],
                phaseOffset = normalizedX * 4f * PI.toFloat(),
                speedMultiplier = 0.8f + random.nextFloat() * 0.4f
            )
        }
    }

    // Continuous wave animation
    val infiniteTransition = rememberInfiniteTransition(label = "waveAnimation")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val centerY = size.height / 2
        val amplitude = if (isAnimating) size.height * 0.3f else size.height * 0.05f

        particles.forEach { particle ->
            val x = particle.baseX * size.width

            // Calculate wave offset
            val waveOffset = if (isAnimating) {
                sin(particle.phaseOffset + wavePhase * particle.speedMultiplier) * amplitude
            } else {
                sin(particle.phaseOffset) * amplitude * 0.2f
            }

            val y = centerY + waveOffset

            // Size pulses with wave
            val sizeMod = if (isAnimating) {
                1f + 0.5f * sin(particle.phaseOffset + wavePhase * particle.speedMultiplier)
            } else {
                1f
            }

            val alpha = if (isAnimating) 0.8f else 0.4f

            drawCircle(
                color = particle.color.copy(alpha = alpha),
                radius = particle.size * sizeMod,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
private fun TtsTestCard(
    testState: TtsTestState,
    testError: String?,
    onTestTts: () -> Unit,
    onStopTts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = testState == TtsTestState.INITIALIZING || testState == TtsTestState.SPEAKING

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
            // Sound wave visualization
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clickable(onClick = if (isActive) onStopTts else onTestTts)
            ) {
                SoundWaveVisualization(
                    isAnimating = isActive,
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
                            TtsTestState.IDLE -> "Tap to test text-to-speech"
                            TtsTestState.INITIALIZING -> "Initializing TTS engine..."
                            TtsTestState.SPEAKING -> "Speaking..."
                            TtsTestState.SUCCESS -> "Test completed successfully"
                            TtsTestState.ERROR -> testError ?: "Error"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = when (state) {
                            TtsTestState.ERROR -> Color(0xFFEA4335)
                            TtsTestState.SUCCESS -> Color(0xFF34A853)
                            TtsTestState.SPEAKING -> WarmOrange
                            TtsTestState.INITIALIZING -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
