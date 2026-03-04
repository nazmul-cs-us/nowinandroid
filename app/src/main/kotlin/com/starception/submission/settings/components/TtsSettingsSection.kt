package com.starception.submission.settings.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
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
    VITS,
    KOKORO
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
    val dataDir: String,
    val lexiconFile: String,
    val voicesFile: String,
    val icon: ImageVector
) {
    KOKORO_EN(
        displayName = "Kokoro",
        description = "High-quality English TTS with 10 natural voices",
        isMultiSpeaker = true,
        totalSpeakers = 10,
        modelType = TtsModelType.KOKORO,
        modelFile = "kokoro-int8-en-v0_19/model.int8.onnx",
        tokensFile = "kokoro-int8-en-v0_19/tokens.txt",
        dataDir = "kokoro-int8-en-v0_19/espeak-ng-data",
        lexiconFile = "",
        voicesFile = "kokoro-int8-en-v0_19/voices.bin",
        icon = Icons.Outlined.GraphicEq
    ),
    VITS_VCTK(
        displayName = "VCTK British",
        description = "Multi-speaker model with 109 British accent voices",
        isMultiSpeaker = true,
        totalSpeakers = 109,
        modelType = TtsModelType.VITS,
        modelFile = "vits-vctk/vits-vctk.int8.onnx",
        tokensFile = "vits-vctk/tokens.txt",
        dataDir = "",
        lexiconFile = "vits-vctk/lexicon.txt",
        voicesFile = "",
        icon = Icons.Outlined.RecordVoiceOver
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
    val availableVoices: List<TtsVoice> = listOf(TtsVoice.KOKORO_EN, TtsVoice.VITS_VCTK),
    val amplitude: Float = 0f  // Real-time audio amplitude (0.0 to 1.0)
)

// Subtle accent colors for TTS visualization
private val SuccessGreen = Color(0xFF34A853)
private val ErrorRed = Color(0xFFEA4335)

/**
 * Modern TTS Settings Section with Material 3 design
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
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Voice model selection section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Voice Model",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            // Voice Selection Cards
            state.availableVoices.forEach { voice ->
                ModernVoiceCard(
                    voice = voice,
                    isSelected = state.selectedVoice == voice,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onVoiceChanged(voice)
                    }
                )
            }
        }

        // Speaker Selection (only for multi-speaker models)
        if (state.selectedVoice.isMultiSpeaker) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Speaker Voice",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                ModernSpeakerSelector(
                    selectedSpeaker = state.selectedSpeakerId,
                    totalSpeakers = state.selectedVoice.totalSpeakers,
                    onSpeakerChanged = { speaker ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSpeakerChanged(speaker)
                    }
                )
            }
        }

        // Test TTS section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Test Voice Output",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            ModernTtsTestCard(
                testState = state.testState,
                testError = state.testError,
                amplitude = state.amplitude,
                onTestTts = onTestTts,
                onStopTts = onStopTts
            )
        }
    }
}

@Composable
private fun ModernVoiceCard(
    voice: TtsVoice,
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
                Icon(
                    imageVector = voice.icon,
                    contentDescription = null,
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = voice.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                    // Speaker count badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = "${voice.totalSpeakers} voices",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = voice.description,
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
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernSpeakerSelector(
    selectedSpeaker: Int,
    totalSpeakers: Int,
    onSpeakerChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var previousValue by remember { mutableStateOf(selectedSpeaker) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with chevron navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Select Speaker",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Navigation row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Previous button
                    Surface(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(
                                enabled = selectedSpeaker > 0,
                                onClick = {
                                    if (selectedSpeaker > 0) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSpeakerChanged(selectedSpeaker - 1)
                                    }
                                }
                            ),
                        shape = CircleShape,
                        color = if (selectedSpeaker > 0)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Previous",
                                tint = if (selectedSpeaker > 0)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Speaker badge
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "Speaker ${selectedSpeaker + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    // Next button
                    Surface(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(
                                enabled = selectedSpeaker < totalSpeakers - 1,
                                onClick = {
                                    if (selectedSpeaker < totalSpeakers - 1) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSpeakerChanged(selectedSpeaker + 1)
                                    }
                                }
                            ),
                        shape = CircleShape,
                        color = if (selectedSpeaker < totalSpeakers - 1)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Next",
                                tint = if (selectedSpeaker < totalSpeakers - 1)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Slider
            Slider(
                value = selectedSpeaker.toFloat(),
                onValueChange = { newValue ->
                    val newIntValue = newValue.toInt()
                    if (newIntValue != previousValue) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        previousValue = newIntValue
                    }
                    onSpeakerChanged(newIntValue)
                },
                valueRange = 0f..(totalSpeakers - 1).toFloat(),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )

            // Range labels
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
 * Mirrored waveform visualization for TTS - bars extend up and down from center
 * Responds to real audio amplitude, with fallback animation when no amplitude data
 */
@Composable
private fun EqualizerVisualization(
    amplitude: Float,  // 0.0 to 1.0 based on actual audio
    isPlaying: Boolean = false,  // Fallback for when no amplitude data
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val numBars = 50

    // Pre-calculated random offsets for natural variation
    val barOffsets = remember {
        val random = Random(42)
        List(numBars) { index ->
            // Create natural variation pattern
            val edgeFactor = 1f - kotlin.math.abs(index - numBars / 2f) / (numBars / 2f)
            val randomVariation = 0.4f + random.nextFloat() * 0.6f
            Pair(edgeFactor, randomVariation)
        }
    }

    // Fallback animation when playing but no amplitude
    val infiniteTransition = rememberInfiniteTransition(label = "waveformFallback")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "animPhase"
    )

    // Smooth the amplitude for visual appeal
    val smoothedAmplitude by animateFloatAsState(
        targetValue = amplitude,
        animationSpec = tween(durationMillis = 50, easing = LinearEasing),
        label = "smoothAmplitude"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerY = size.height / 2
        val barWidth = size.width / (numBars * 1.6f)
        val gap = (size.width - barWidth * numBars) / (numBars + 1)
        val maxBarHeight = size.height * 0.45f
        val minBarHeight = size.height * 0.03f
        val cornerRadius = barWidth / 2

        val hasAmplitude = smoothedAmplitude > 0.01f
        val isActive = hasAmplitude || isPlaying

        for (i in 0 until numBars) {
            val x = gap + i * (barWidth + gap)
            val (edgeFactor, randomVariation) = barOffsets[i]
            val normalizedPos = i.toFloat() / numBars

            val heightMultiplier = when {
                hasAmplitude -> {
                    // Use real amplitude data
                    val baseHeight = smoothedAmplitude * edgeFactor * randomVariation
                    (0.1f + baseHeight * 0.9f).coerceIn(0.05f, 1f)
                }
                isPlaying -> {
                    // Fallback animated wave when playing without amplitude
                    val wave1 = sin(normalizedPos * PI.toFloat() * 3 + animPhase)
                    val wave2 = sin(normalizedPos * PI.toFloat() * 5 + animPhase * 1.3f) * 0.4f
                    ((wave1 + wave2 + 1.4f) / 2.8f * edgeFactor * randomVariation).coerceIn(0.1f, 0.9f)
                }
                else -> {
                    // Static idle pattern
                    (edgeFactor * randomVariation * 0.5f).coerceIn(0.08f, 0.5f)
                }
            }

            val halfBarHeight = (minBarHeight + (maxBarHeight - minBarHeight) * heightMultiplier)
                .coerceIn(minBarHeight, maxBarHeight)

            val barAlpha = if (isActive) 0.85f else 0.5f

            // Draw mirrored bar (extends both up and down from center)
            drawRoundRect(
                color = primaryColor.copy(alpha = barAlpha),
                topLeft = Offset(x, centerY - halfBarHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, halfBarHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
            )

            drawRoundRect(
                color = primaryColor.copy(alpha = barAlpha),
                topLeft = Offset(x, centerY),
                size = androidx.compose.ui.geometry.Size(barWidth, halfBarHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
            )
        }
    }
}

@Composable
private fun ModernTtsTestCard(
    testState: TtsTestState,
    testError: String?,
    amplitude: Float,
    onTestTts: () -> Unit,
    onStopTts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = testState == TtsTestState.INITIALIZING || testState == TtsTestState.SPEAKING
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primary)
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (isActive) onStopTts() else onTestTts()
            },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Play/Pause button
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isActive) "Stop" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Waveform visualization - responds to actual audio amplitude
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                contentAlignment = Alignment.Center
            ) {
                EqualizerVisualization(
                    amplitude = amplitude,
                    isPlaying = isActive,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
