/*
 * Copyright 2024 Starception
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

package com.starception.submission.settings.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.component.NiaBottomSheetDefaults
import com.starception.submission.core.designsystem.component.NiaBottomSheetFrame
import com.starception.submission.core.designsystem.component.NiaBottomSheetTheme
import com.starception.submission.voice.SherpaOnnxTtsService
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Shared TTS model and speaker picker for detail screens that narrate content.
 * Changes are owned and persisted by the caller so the sheet remains reusable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsVoiceSelectionSheet(
    selectedVoice: TtsVoice,
    selectedSpeakerId: Int,
    supportingText: String,
    ttsService: SherpaOnnxTtsService,
    isVoiceAvailable: (TtsVoice) -> Boolean,
    onVoiceSelected: (TtsVoice) -> Unit,
    onSpeakerChanged: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var previewJob by remember { mutableStateOf<Job?>(null) }
    var isPreviewing by remember { mutableStateOf(false) }
    var hasPreviewStarted by remember { mutableStateOf(false) }
    val selectedVoiceAvailable = isVoiceAvailable(selectedVoice)

    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        ttsService.stopSpeaking()
        isPreviewing = false
        hasPreviewStarted = false
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isPreviewing) {
                previewJob?.cancel()
                ttsService.stopSpeaking()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = NiaBottomSheetDefaults.FloatingShape,
        containerColor = Color.Transparent,
        contentColor = NiaBottomSheetDefaults.contentColor(),
        scrimColor = NiaBottomSheetDefaults.scrimColor(),
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        NiaBottomSheetTheme {
            NiaBottomSheetFrame {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    Text(
                        text = "Voice",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    TtsVoice.entries.forEach { voice ->
                        val selected = voice == selectedVoice
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (voice != selectedVoice) stopPreview()
                                    onVoiceSelected(voice)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = voice.icon,
                                contentDescription = null,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = voice.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Text(
                                    text = if (isVoiceAvailable(voice)) {
                                        voice.description
                                    } else {
                                        "Not downloaded — tap play to download"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    if (selectedVoice.isMultiSpeaker) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Speaker",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = "${selectedVoice.totalSpeakers} voices available",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val total = selectedVoice.totalSpeakers
                            IconButton(
                                onClick = {
                                    stopPreview()
                                    onSpeakerChanged((selectedSpeakerId - 1 + total) % total)
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Previous speaker",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "$selectedSpeakerId",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            IconButton(
                                onClick = {
                                    stopPreview()
                                    onSpeakerChanged((selectedSpeakerId + 1) % total)
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next speaker",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    TtsVoicePreviewButton(
                        isPreparing = isPreviewing && !hasPreviewStarted,
                        isPlaying = isPreviewing && hasPreviewStarted,
                        isVoiceAvailable = selectedVoiceAvailable,
                        onClick = {
                            if (isPreviewing) {
                                stopPreview()
                            } else {
                                previewJob = scope.launch {
                                    isPreviewing = true
                                    hasPreviewStarted = false
                                    ttsService.stopSpeaking()
                                    ttsService.setVoice(selectedVoice)
                                    try {
                                        ttsService.speak(
                                            text = TTS_VOICE_SAMPLE_TEXT,
                                            speakerId = selectedSpeakerId,
                                            onPlaybackStart = {
                                                scope.launch { hasPreviewStarted = true }
                                            },
                                        )
                                    } finally {
                                        isPreviewing = false
                                        hasPreviewStarted = false
                                        previewJob = null
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Returns whether a Sherpa TTS model is available from extraction, CDN, or assets. */
fun isTtsVoiceModelAvailable(context: Context, voice: TtsVoice): Boolean {
    val modelFile = voice.modelFile
    return try {
        val extractedFile = File(File(context.filesDir, "tts_model"), modelFile)
        if (extractedFile.exists() && extractedFile.length() > 1024) return true

        val cdnFile = File(File(context.filesDir, "cdn_assets"), "models/tts/$modelFile")
        if (cdnFile.exists() && cdnFile.length() > 1024) return true

        context.assets.open("tts/$modelFile").use { it.available() > 0 }
    } catch (_: Exception) {
        false
    }
}
