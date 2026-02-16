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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = "Complete Lesson",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Lesson name
            Text(
                text = lessonTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Question
            Text(
                text = "Did you complete this lesson?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Recording Section
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedContent(
                        targetState = isRecording,
                        label = "recording_state",
                    ) { recording ->
                        if (recording) {
                            // Recording UI
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
                        } else if (hasRecording) {
                            // Playback UI
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
                        } else {
                            // Start recording UI
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
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Not Yet button
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Not Yet")
                }

                // Completed button
                Button(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onComplete(hasRecording)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Completed")
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FilledTonalButton(
            onClick = {
                if (hasPermission) {
                    onStartRecording()
                } else {
                    onRequestPermission()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Record Your Recitation")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "(Optional - verify your understanding)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            animation = tween(500, easing = LinearEasing),
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
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cancel")
            }

            // Stop button
            Button(
                onClick = onStop,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Stop")
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
            FilledTonalButton(
                onClick = if (isPlaying) onStop else onPlay,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isPlaying) "Stop" else "Play")
            }

            // Delete button
            OutlinedButton(
                onClick = onDelete,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete")
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
            dismissOnClickOutside = false, // Prevent accidental dismissal
            usePlatformDefaultWidth = false,
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(16.dp),
        title = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "Complete Lesson",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Lesson name
                Text(
                    text = lessonTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Question
                Text(
                    text = "Did you complete this lesson?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Recording Section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AnimatedContent(
                            targetState = isRecording,
                            label = "recording_state",
                        ) { recording ->
                            if (recording) {
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
                            } else if (hasRecording) {
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
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    Log.d("CompletionDialog_TRACE", "✅ Completed clicked")
                    onComplete(hasRecording)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Completed")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    Log.d("CompletionDialog_TRACE", "❌ Not Yet clicked")
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Not Yet")
            }
        },
    )
}
