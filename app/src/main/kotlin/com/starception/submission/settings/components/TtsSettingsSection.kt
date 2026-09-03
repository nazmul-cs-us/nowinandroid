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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
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
    val icon: ImageVector,
    val iconGlyph: String
) {
    KOKORO_EN(
        displayName = "Kokoro",
        description = "High-quality English TTS with 11 natural voices",
        isMultiSpeaker = true,
        totalSpeakers = 11,
        modelType = TtsModelType.KOKORO,
        modelFile = "kokoro-int8-en-v0_19/model.int8.onnx",
        tokensFile = "kokoro-int8-en-v0_19/tokens.txt",
        dataDir = "kokoro-int8-en-v0_19/espeak-ng-data",
        lexiconFile = "",
        voicesFile = "kokoro-int8-en-v0_19/voices.bin",
        icon = Icons.Outlined.GraphicEq,
        iconGlyph = FlaticonIcons.VOICE
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
        icon = Icons.Outlined.RecordVoiceOver,
        iconGlyph = FlaticonIcons.VOLUME
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
    val amplitude: Float = 0f,  // Real-time audio amplitude (0.0 to 1.0)
    val needsDownload: Boolean = false,
    val downloadCategory: String? = null,
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
    downloadManager: com.starception.submission.download.AssetDownloadManager? = null,
    onDownloadComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Voice model selection section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Voice",
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

        // Download prompt when model is missing
        if (state.needsDownload && state.downloadCategory != null && downloadManager != null) {
            com.starception.submission.download.MissingContentCard(
                resourceName = when (state.selectedVoice) {
                    TtsVoice.KOKORO_EN -> "Kokoro TTS Engine"
                    TtsVoice.VITS_VCTK -> "VITS TTS Engine"
                },
                category = state.downloadCategory,
                description = when (state.selectedVoice) {
                    TtsVoice.KOKORO_EN -> "Download the Kokoro neural voice model for high-quality speech"
                    TtsVoice.VITS_VCTK -> "Download the VITS multi-speaker voice model"
                },
                downloadManager = downloadManager,
                onDownloadComplete = onDownloadComplete,
                modifier = Modifier.padding(horizontal = 0.dp)
            )
        } else {
            // Speaker Selection (only for multi-speaker models)
            if (state.selectedVoice.isMultiSpeaker) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Speaker",
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
                    text = "Preview",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                TtsVoicePreviewButton(
                    isPreparing = state.testState == TtsTestState.INITIALIZING,
                    isPlaying = state.testState == TtsTestState.SPEAKING,
                    isVoiceAvailable = !state.needsDownload,
                    onClick = {
                        if (
                            state.testState == TtsTestState.INITIALIZING ||
                            state.testState == TtsTestState.SPEAKING
                        ) {
                            onStopTts()
                        } else {
                            onTestTts()
                        }
                    },
                )
                if (state.testState == TtsTestState.ERROR && state.testError != null) {
                    Text(
                        text = state.testError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
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
                width = 1.dp,
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ),
                contentAlignment = Alignment.Center
            ) {
                FlaticonIcon(
                    glyph = voice.iconGlyph,
                    contentDescription = null,
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 20.sp,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = voice.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = "${voice.totalSpeakers} voices · ${voice.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isSelected) {
                FlaticonIcon(
                    glyph = FlaticonIcons.CHECK,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp,
                )
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Speaker ${selectedSpeaker + 1}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${selectedSpeaker + 1} of $totalSpeakers voices",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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
                            FlaticonIcon(
                                glyph = FlaticonIcons.ANGLE_LEFT,
                                contentDescription = "Previous",
                                tint = if (selectedSpeaker > 0)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                fontSize = 20.sp,
                            )
                        }
                    }

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
                            FlaticonIcon(
                                glyph = FlaticonIcons.ANGLE_RIGHT,
                                contentDescription = "Next",
                                tint = if (selectedSpeaker < totalSpeakers - 1)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                fontSize = 20.sp,
                            )
                        }
                    }
                }
            }

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

        }
    }
}
