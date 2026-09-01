/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import com.starception.submission.config.TravelDuaSettings
import com.starception.submission.shared.settings.VoiceRecognitionMode
import com.starception.submission.shared.voice.NarrationVoice

enum class VoiceTestState {
    IDLE,
    LISTENING,
    SUCCESS,
    ERROR,
}

data class AudioSettingsState(
    val travelDua: TravelDuaSettings = TravelDuaSettings(),
    val isTravelDuaPlaying: Boolean = false,
    val recognitionMode: VoiceRecognitionMode = VoiceRecognitionMode.KEYWORDS,
    val recognitionTestState: VoiceTestState = VoiceTestState.IDLE,
    val recognitionTestText: String? = null,
    val narrationVoices: List<NarrationVoice> = emptyList(),
    val selectedNarrationVoiceIdentifier: String? = null,
    val selectedNarrationSpeakerId: Int = 0,
    val isNarrationSpeaking: Boolean = false,
    val narrationError: String? = null,
)

data class AudioSettingsActions(
    val onTravelDuaChange: (TravelDuaSettings) -> Unit = {},
    val onTestTravelDua: () -> Unit = {},
    val onStopTravelDua: () -> Unit = {},
    val onRecognitionModeSelected: (VoiceRecognitionMode) -> Unit = {},
    val onStartRecognitionTest: () -> Unit = {},
    val onStopRecognitionTest: () -> Unit = {},
    val onNarrationVoiceSelected: (NarrationVoice) -> Unit = {},
    val onNarrationSpeakerSelected: (Int) -> Unit = {},
    val onPreviewNarration: () -> Unit = {},
    val onStopNarration: () -> Unit = {},
)

@Composable
fun VoiceRecognitionSettingsSection(
    selectedMode: VoiceRecognitionMode,
    testState: VoiceTestState,
    testText: String?,
    onModeSelected: (VoiceRecognitionMode) -> Unit,
    onStartTest: () -> Unit,
    onStopTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Recognition mode",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecognitionModeCard(
                title = "Keywords",
                detail = "Fast yes/no",
                icon = FlaticonIcons.QUICK_ACTION,
                selected = selectedMode == VoiceRecognitionMode.KEYWORDS,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onModeSelected(VoiceRecognitionMode.KEYWORDS)
                },
                modifier = Modifier.weight(1f),
            )
            RecognitionModeCard(
                title = "Transcription",
                detail = "Full speech",
                icon = FlaticonIcons.VOICE,
                selected = selectedMode == VoiceRecognitionMode.TRANSCRIPTION,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onModeSelected(VoiceRecognitionMode.TRANSCRIPTION)
                },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = if (selectedMode == VoiceRecognitionMode.KEYWORDS) {
                "Uses the offline keyword model and returns as soon as yes or no is heard."
            } else {
                "Uses the offline speech model to return the complete spoken response."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NiaOutlinedButton(
            onClick = if (testState == VoiceTestState.LISTENING) onStopTest else onStartTest,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            FlaticonIcon(
                glyph = if (testState == VoiceTestState.LISTENING) {
                    FlaticonIcons.PAUSE
                } else {
                    FlaticonIcons.MICROPHONE
                },
                contentDescription = null,
                fontSize = 19.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(if (testState == VoiceTestState.LISTENING) "Stop listening" else "Test Voice Recognition")
        }
        if (testState != VoiceTestState.IDLE || testText != null) {
            StatusCard(
                title = when (testState) {
                    VoiceTestState.IDLE -> "Ready"
                    VoiceTestState.LISTENING -> "Listening..."
                    VoiceTestState.SUCCESS -> "Recognized"
                    VoiceTestState.ERROR -> "Recognition unavailable"
                },
                detail = testText,
                error = testState == VoiceTestState.ERROR,
            )
        }
    }
}

@Composable
private fun RecognitionModeCard(
    title: String,
    detail: String,
    icon: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            onClick = onClick,
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(30.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    FlaticonIcon(
                        glyph = icon,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                    )
                }
            }
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NarrationSettingsSection(
    voices: List<NarrationVoice>,
    selectedIdentifier: String?,
    selectedSpeakerId: Int,
    isSpeaking: Boolean,
    error: String?,
    onVoiceSelected: (NarrationVoice) -> Unit,
    onSpeakerSelected: (Int) -> Unit,
    onPreview: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Voice",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        voices.forEach { voice ->
            val selected = selectedIdentifier == voice.identifier ||
                (selectedIdentifier == null && voice == voices.firstOrNull())
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onVoiceSelected(voice)
                },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    if (selected) 1.5.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.VOLUME,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        fontSize = 22.sp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(voice.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(voice.language, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selected) {
                        FlaticonIcon(
                            glyph = FlaticonIcons.CHECK,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            fontSize = 17.sp,
                        )
                    }
                }
            }
        }
        val selectedVoice = voices.firstOrNull { it.identifier == selectedIdentifier }
            ?: voices.firstOrNull()
        if (selectedVoice != null && selectedVoice.totalSpeakers > 1) {
            Text(
                text = "Speaker ${selectedSpeakerId + 1} of ${selectedVoice.totalSpeakers}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Slider(
                value = selectedSpeakerId.coerceIn(0, selectedVoice.totalSpeakers - 1).toFloat(),
                onValueChange = { onSpeakerSelected(it.toInt()) },
                valueRange = 0f..(selectedVoice.totalSpeakers - 1).toFloat(),
                steps = (selectedVoice.totalSpeakers - 2).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        NiaOutlinedButton(
            onClick = if (isSpeaking) onStop else onPreview,
            enabled = voices.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            FlaticonIcon(
                glyph = if (isSpeaking) FlaticonIcons.PAUSE else FlaticonIcons.VOLUME,
                contentDescription = null,
                fontSize = 19.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isSpeaking) "Stop voice sample" else "Play voice sample")
        }
        if (error != null) StatusCard("Narration unavailable", error, error = true)
    }
}

@Composable
private fun StatusCard(title: String, detail: String?, error: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
