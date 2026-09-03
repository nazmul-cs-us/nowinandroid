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

package com.starception.submission.feature.course

import android.Manifest
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import com.starception.submission.core.designsystem.component.NiaTextButton
import com.starception.submission.core.designsystem.component.NiaBottomSheetDefaults
import com.starception.submission.core.designsystem.component.NiaBottomSheetFrame
import com.starception.submission.core.designsystem.component.NiaBottomSheetTheme
import com.starception.submission.core.designsystem.component.NiaBottomSheetDragHandle
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bottom sheet for confirming lesson completion with optional voice recording.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun LessonCompletionBottomSheet(
    lessonTitle: String,
    courseId: String,
    lessonId: String,
    onComplete: (hasRecording: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Log.d("BottomSheet_TRACE", "🎬 LessonCompletionBottomSheet COMPOSING: lesson=$lessonId, title=$lessonTitle")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Log sheet state
    Log.d("BottomSheet_TRACE", "📊 Sheet state - isVisible: ${sheetState.isVisible}, currentValue: ${sheetState.currentValue}, targetValue: ${sheetState.targetValue}")

    // Recording state
    val recordingManager = remember { VoiceRecordingManager(context) }
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var hasRecording by remember { mutableStateOf(recordingManager.hasRecording(courseId, lessonId)) }

    // Permission state
    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // Timer for recording duration
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    // Clean up on dispose
    DisposableEffect(Unit) {
        Log.d("BottomSheet_TRACE", "🟢 DisposableEffect STARTED for lesson: $lessonId")
        onDispose {
            Log.d("BottomSheet_TRACE", "🔴 DisposableEffect DISPOSED - composable removed from tree! lesson: $lessonId")
            if (isRecording) {
                recordingManager.cancelRecording()
            }
            if (isPlaying) {
                recordingManager.stopPlayback()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            Log.d("BottomSheet_TRACE", "🚪 onDismissRequest TRIGGERED for lesson: $lessonId")
            if (isRecording) {
                Log.d("BottomSheet_TRACE", "🎤 Canceling recording...")
                recordingManager.cancelRecording()
            }
            Log.d("BottomSheet_TRACE", "📤 Calling onDismiss callback...")
            onDismiss()
        },
        sheetState = sheetState,
        shape = NiaBottomSheetDefaults.FloatingShape,
        containerColor = Color.Transparent,
        contentColor = NiaBottomSheetDefaults.contentColor(),
        scrimColor = NiaBottomSheetDefaults.scrimColor(),
        tonalElevation = 0.dp,
        dragHandle = null,
        modifier = modifier,
    ) {
        NiaBottomSheetTheme {
            NiaBottomSheetFrame {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NiaBottomSheetDragHandle()

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                                FlaticonIcon(
                                    glyph = FlaticonIcons.COMPLETED,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 23.sp,
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Complete lesson",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Confirm your progress. A voice note is optional.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                    FlaticonIcon(
                                        glyph = FlaticonIcons.BOOK,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        fontSize = 19.sp,
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Lesson",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = lessonTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    AnimatedContent(
                        targetState = isRecording,
                        transitionSpec = {
                            fadeIn(tween(300, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(tween(200, easing = FastOutSlowInEasing))
                        },
                        label = "recording_state",
                    ) { recording ->
                        if (recording) {
                            VoiceNoteStateSurface {
                                RecordingIndicator(
                                    seconds = recordingSeconds,
                                    onStop = {
                                        recordingManager.stopRecording()
                                        isRecording = false
                                        hasRecording = true
                                    },
                                    onCancel = {
                                        recordingManager.cancelRecording()
                                        isRecording = false
                                    },
                                )
                            }
                        } else if (hasRecording) {
                            VoiceNoteStateSurface {
                                RecordingPlayback(
                                    isPlaying = isPlaying,
                                    onPlay = {
                                        isPlaying = recordingManager.startPlayback(courseId, lessonId) {
                                            isPlaying = false
                                        }
                                    },
                                    onStop = {
                                        recordingManager.stopPlayback()
                                        isPlaying = false
                                    },
                                    onDelete = {
                                        recordingManager.deleteRecording(courseId, lessonId)
                                        hasRecording = false
                                    },
                                )
                            }
                        } else {
                            RecordButton(
                                hasPermission = audioPermissionState.status.isGranted,
                                onRequestPermission = {
                                    audioPermissionState.launchPermissionRequest()
                                },
                                onStartRecording = {
                                    if (recordingManager.startRecording(courseId, lessonId)) {
                                        isRecording = true
                                    }
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    NiaOutlinedButton(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onComplete(hasRecording)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Text(
                            text = "Mark as complete",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    NiaTextButton(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                    ) {
                        Text("Keep learning")
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordButton(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStartRecording: () -> Unit,
) {
    Surface(
        onClick = {
            if (hasPermission) {
                onStartRecording()
            } else {
                onRequestPermission()
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.MICROPHONE,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 18.sp,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Voice note",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Optional reflection or proof of completion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Add",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun VoiceNoteStateSurface(
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun RecordingIndicator(
    seconds: Int,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Recording indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.Red.copy(alpha = pulseAlpha)),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Recording...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Red,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Timer
        Text(
            text = formatDuration(seconds),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Cancel button
            NiaOutlinedButton(
                onClick = onCancel,
            ) {
                Text("Cancel")
            }

            // Stop button
            NiaOutlinedButton(
                onClick = onStop,
            ) {
                Text("Stop Recording")
            }
        }
    }
}

@Composable
private fun RecordingPlayback(
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Recording saved",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Playback controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Play/Stop button
            NiaOutlinedButton(
                onClick = if (isPlaying) onStop else onPlay,
            ) {
                Text(if (isPlaying) "Stop Playback" else "Play Recording")
            }

            // Delete button
            NiaOutlinedButton(
                onClick = onDelete,
            ) {
                Text("Remove")
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}

/**
 * Dialog-based lesson completion UI that is more stable during navigation transitions.
 * Unlike ModalBottomSheet, AlertDialog doesn't get dismissed during predictive back gestures.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LessonCompletionDialog(
    lessonTitle: String,
    courseId: String,
    lessonId: String,
    onComplete: (hasRecording: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Log.d("CompletionDialog_TRACE", "🎬 LessonCompletionDialog COMPOSING: lesson=$lessonId")
    val context = LocalContext.current

    // Recording state
    val recordingManager = remember { VoiceRecordingManager(context) }
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var hasRecording by remember { mutableStateOf(recordingManager.hasRecording(courseId, lessonId)) }

    // Permission state
    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // Timer for recording duration
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    // Clean up on dispose
    DisposableEffect(Unit) {
        Log.d("CompletionDialog_TRACE", "🟢 DisposableEffect STARTED")
        onDispose {
            Log.d("CompletionDialog_TRACE", "🔴 DisposableEffect DISPOSED")
            if (isRecording) {
                recordingManager.cancelRecording()
            }
            if (isPlaying) {
                recordingManager.stopPlayback()
            }
        }
    }

    val recordingSurfaceColor = when {
        isRecording -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
        hasRecording -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    AlertDialog(
        onDismissRequest = {
            Log.d("CompletionDialog_TRACE", "🚪 onDismissRequest")
            if (isRecording) {
                recordingManager.cancelRecording()
            }
            onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(16.dp),
        title = null,
        text = {
            val recordingUiState = when {
                isRecording -> "recording"
                hasRecording -> "saved"
                else -> "idle"
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Complete Lesson",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Confirm this lesson and optionally save a short voice note.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Lesson",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = lessonTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Did you complete this lesson?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = recordingSurfaceColor,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (hasRecording || isRecording) Icons.Filled.Mic else Icons.Outlined.Mic,
                                    contentDescription = null,
                                    tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Voice note",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (hasRecording) {
                                        "Recording saved for this lesson."
                                    } else {
                                        "Optional: add a short reflection or proof of completion."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 116.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedContent(
                                targetState = recordingUiState,
                                label = "recording_state",
                            ) { state ->
                                when (state) {
                                    "recording" -> RecordingIndicator(
                                        seconds = recordingSeconds,
                                        onStop = {
                                            recordingManager.stopRecording()
                                            isRecording = false
                                            hasRecording = true
                                        },
                                        onCancel = {
                                            recordingManager.cancelRecording()
                                            isRecording = false
                                        },
                                    )

                                    "saved" -> RecordingPlayback(
                                        isPlaying = isPlaying,
                                        onPlay = {
                                            isPlaying = recordingManager.startPlayback(courseId, lessonId) {
                                                isPlaying = false
                                            }
                                        },
                                        onStop = {
                                            recordingManager.stopPlayback()
                                            isPlaying = false
                                        },
                                        onDelete = {
                                            recordingManager.deleteRecording(courseId, lessonId)
                                            hasRecording = false
                                        },
                                    )

                                    else -> RecordButton(
                                        hasPermission = audioPermissionState.status.isGranted,
                                        onRequestPermission = {
                                            audioPermissionState.launchPermissionRequest()
                                        },
                                        onStartRecording = {
                                            if (recordingManager.startRecording(courseId, lessonId)) {
                                                isRecording = true
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NiaOutlinedButton(
                        onClick = {
                            Log.d("CompletionDialog_TRACE", "✅ Completed clicked")
                            onComplete(hasRecording)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Text(
                            text = "Mark Complete",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    NiaOutlinedButton(
                        onClick = {
                            Log.d("CompletionDialog_TRACE", "❌ Not Yet clicked")
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                    ) {
                        Text(
                            text = "Not Now",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}
