package com.starception.submission.feature.salah.datacollection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivePrayerRecordingScreen(
    onNavigateToReview: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: LivePrayerRecordingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Prayer Recording") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                // Clear the app's floating bottom navigation — without this
                // the bottom-anchored Start/Stop buttons hide behind the pill.
                .padding(bottom = com.starception.submission.core.designsystem.theme.FloatingNavClearance),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                // Post-recording state
                state.recordedFilePath != null -> {
                    PostRecordingContent(
                        filePath = state.recordedFilePath!!,
                        elapsedSeconds = state.elapsedSeconds,
                        sampleCount = state.sampleCount,
                        rakahCount = state.rakahCount,
                        onNavigateToReview = onNavigateToReview,
                        onDiscard = {
                            // Delete the file and go back
                            try {
                                File(state.recordedFilePath!!).delete()
                            } catch (_: Exception) {}
                            onBack()
                        }
                    )
                }
                // During recording
                state.isRecording -> {
                    RecordingContent(
                        elapsedSeconds = state.elapsedSeconds,
                        sampleCount = state.sampleCount,
                        detectedPosture = state.detectedPosture,
                        detectedConfidence = state.detectedConfidence,
                        rakahCount = state.rakahCount,
                        onStopRecording = { viewModel.stopRecording() }
                    )
                }
                // Pre-recording state
                else -> {
                    PreRecordingContent(
                        onStartRecording = { viewModel.startRecording() }
                    )
                }
            }
        }
    }
}

@Composable
private fun PreRecordingContent(
    onStartRecording: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 3.dp,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header with gradient icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Instructions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Gradient divider
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(vertical = 0.5.dp)
                    )
                }

                // Steps with numbered badges
                InstructionStep(number = 1, text = "Place your phone in your pocket")
                InstructionStep(number = 2, text = "Tap 'Start Recording' below")
                InstructionStep(number = 3, text = "Perform your prayer normally")
                InstructionStep(number = 4, text = "After finishing, pull phone out and tap 'Stop'")
                InstructionStep(number = 5, text = "Review and label the detected postures")
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 2.dp,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header with gradient icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Real-time ML Detection",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "The app will automatically detect and count your prayer postures using on-device ML. You can review and correct any mistakes after recording.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        NiaOutlinedButton(
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onStartRecording()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(
                text = "Start Prayer Recording",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun RecordingContent(
    elapsedSeconds: Int,
    sampleCount: Int,
    detectedPosture: com.starception.submission.ml.SalahPosture?,
    detectedConfidence: Float,
    rakahCount: Int,
    onStopRecording: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Recording indicator
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 6.dp,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    ambientColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    spotColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "● RECORDING",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Elapsed time
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 3.dp,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = formatTime(elapsedSeconds),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Elapsed Time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ML detection results
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 3.dp,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header with gradient icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Live Detection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Gradient divider
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(vertical = 0.5.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Detected Posture:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = detectedPosture?.displayName ?: "Analyzing...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Confidence:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${(detectedConfidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Rak'ahs Counted:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = rakahCount.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Samples Recorded:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = sampleCount.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Stop button
        NiaOutlinedButton(
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onStopRecording()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(
                text = "Stop Recording",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Text(
            text = "Auto-stops after 30 minutes",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PostRecordingContent(
    filePath: String,
    elapsedSeconds: Int,
    sampleCount: Int,
    rakahCount: Int,
    onNavigateToReview: (String) -> Unit,
    onDiscard: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Success message
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "Recording Completed",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Recording stats
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 3.dp,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header with gradient icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Recording Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Duration:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = formatTime(elapsedSeconds),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Samples:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = sampleCount.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Rak'ahs Detected:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = rakahCount.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider()

                Text(
                    text = "File: ${File(filePath).name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Review button
        NiaOutlinedButton(
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onNavigateToReview(filePath)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(
                text = "Review & Label",
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Discard button
        NiaOutlinedButton(
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDiscard()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "Discard Recording",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun InstructionStep(number: Int, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", minutes, secs)
}
