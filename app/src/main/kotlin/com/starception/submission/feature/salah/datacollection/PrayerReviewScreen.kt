package com.starception.submission.feature.salah.datacollection

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import com.starception.submission.ml.SalahPosture
import java.io.File
import com.starception.submission.core.designsystem.theme.FloatingNavClearance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerReviewScreen(
    filePath: String,
    onBack: () -> Unit,
    viewModel: PrayerReviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Load file on first composition
    LaunchedEffect(filePath) {
        viewModel.loadFile(filePath)
    }

    // Navigate back when saved
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review & Label") },
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
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // File info
                Text(
                    text = "File: ${File(filePath).name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Summary card
                SummaryCard(
                    totalSamples = state.totalSamples,
                    postureCounts = state.postureCounts
                )

                // Model-vs-label quality analysis
                DataQualityCard(
                    isAnalyzing = state.isAnalyzing,
                    analysisProgress = state.analysisProgress,
                    totalSamples = state.totalSamples,
                    analysis = state.analysis,
                    onAnalyze = viewModel::analyzeQuality,
                    onFlagTap = viewModel::selectSegmentAtWindow
                )

                // Instruction text
                Text(
                    text = "Tap a segment to change its posture",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                // Timeline
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
                                    imageVector = Icons.Default.Timeline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "Posture Timeline",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .height(80.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            state.segments.forEachIndexed { index, segment ->
                                PostureSegmentBox(
                                    segment = segment,
                                    isSelected = state.selectedSegmentIndex == index,
                                    flaggedWindows = state.flaggedPerSegment[index] ?: 0,
                                    onClick = { viewModel.selectSegment(index) }
                                )
                            }
                        }
                    }
                }

                // Posture picker (shown when segment is selected)
                state.selectedSegmentIndex?.let { selectedIndex ->
                    PosturePicker(
                        currentPosture = state.segments[selectedIndex].posture,
                        onPostureSelected = { newPosture ->
                            viewModel.changeSegmentPosture(selectedIndex, newPosture)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Save button
                val saveHapticFeedback = LocalHapticFeedback.current
                NiaOutlinedButton(
                    onClick = {
                        saveHapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.saveLabels()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !state.isSaving
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "Save Labels",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                // Discard button
                val discardHapticFeedback = LocalHapticFeedback.current
                NiaOutlinedButton(
                    onClick = {
                        discardHapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.discardRecording()
                        onBack()
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

                Spacer(modifier = Modifier.height(FloatingNavClearance))
            }
        }
    }
}

@Composable
private fun SummaryCard(
    totalSamples: Int,
    postureCounts: Map<SalahPosture, Int>
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                    text = "Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Samples:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = totalSamples.toString(),
                    style = MaterialTheme.typography.bodyMedium,
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

            Text(
                text = "Posture Counts:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )

            postureCounts.forEach { (posture, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            getPostureColor(posture),
                                            getPostureColor(posture).copy(alpha = 0.7f)
                                        )
                                    ),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                )
                        )
                        Text(
                            text = posture.displayName,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PostureSegmentBox(
    segment: PostureSegment,
    isSelected: Boolean,
    onClick: () -> Unit,
    flaggedWindows: Int = 0
) {
    val width = ((segment.endIndex - segment.startIndex + 1) * 2).dp.coerceAtLeast(20.dp)
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "segmentScale"
    )
    val hapticFeedback = LocalHapticFeedback.current

    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        flaggedWindows > 0 -> MaterialTheme.colorScheme.error
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .scale(scale)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        getPostureColor(segment.posture),
                        getPostureColor(segment.posture).copy(alpha = 0.7f)
                    )
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isSelected) 3.dp else if (flaggedWindows > 0) 2.dp else 0.dp,
                color = borderColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .clickable(onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            })
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            flaggedWindows > 0 -> Text(
                text = "!",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            segment.wasEdited -> Text(
                text = "✓",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Model-vs-label quality analysis: how much the deployed model agrees with this
 * file's labels. Low agreement means mislabeled data (fix here) or hard examples
 * (keep — they improve the next training run).
 */
@Composable
private fun DataQualityCard(
    isAnalyzing: Boolean,
    analysisProgress: Int,
    totalSamples: Int,
    analysis: com.starception.submission.ml.SalahBatchInference.BatchResult?,
    onAnalyze: () -> Unit,
    onFlagTap: (Int) -> Unit
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                    text = "Model vs Labels",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            when {
                isAnalyzing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Analyzing… $analysisProgress / $totalSamples windows",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                analysis == null -> {
                    NiaOutlinedButton(
                        onClick = onAnalyze,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Analyze data quality")
                    }
                }

                else -> {
                    val agreementPct = (analysis.overallAgreement * 100).toInt()
                    val agreementColor = when {
                        agreementPct >= 85 -> Color(0xFF2E7D32)
                        agreementPct >= 70 -> Color(0xFFF9A825)
                        else -> MaterialTheme.colorScheme.error
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "$agreementPct%",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = agreementColor
                            )
                            Text(
                                text = "model agrees with labels",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${analysis.flaggedSegments.size} flagged",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (analysis.flaggedSegments.isEmpty()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                            Text(
                                text = "${analysis.unclassifiedWindows} unclassified",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Top confusion pairs (label -> what the model saw instead)
                    val mismatchPairs = analysis.confusion.entries
                        .flatMap { (label, row) ->
                            row.entries
                                .filter { it.key != label }
                                .map { Triple(label, it.key, it.value) }
                        }
                        .sortedByDescending { it.third }
                        .take(3)
                    if (mismatchPairs.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            mismatchPairs.forEach { (label, predicted, count) ->
                                Text(
                                    text = "${label.displayName} read as ${predicted.displayName} · $count windows",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Flagged runs — tap to select that part of the timeline
                    analysis.flaggedSegments.take(5).forEach { flag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                                .clickable { onFlagTap(flag.startIndex) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${flag.label.displayName} → ${flag.predicted.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${flag.windowCount}w · ${(flag.avgConfidence * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TextButton(onClick = onAnalyze) { Text("Re-analyze") }
                }
            }
        }
    }
}

@Composable
private fun PosturePicker(
    currentPosture: SalahPosture,
    onPostureSelected: (SalahPosture) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "Change Posture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Text(
                text = "Current: ${currentPosture.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

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

            // Posture chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SalahPosture.classificationLabels.forEach { posture ->
                    FilterChip(
                        selected = posture == currentPosture,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPostureSelected(posture)
                        },
                        label = {
                            Text(
                                text = posture.displayName,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                getPostureColor(posture),
                                                getPostureColor(posture).copy(alpha = 0.7f)
                                            )
                                        ),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun getPostureColor(posture: SalahPosture): Color {
    return when (posture) {
        SalahPosture.QIYAM -> Color(0xFF4CAF50) // Green
        SalahPosture.RUKU -> Color(0xFF2196F3) // Blue
        SalahPosture.SUJUD -> Color(0xFF9C27B0) // Purple
        SalahPosture.JALSA -> Color(0xFFFF9800) // Orange
        SalahPosture.TASHAHHUD -> Color(0xFFFFEB3B) // Yellow
        SalahPosture.GOING_TO_SUJUD -> Color(0xFF009688) // Teal
        SalahPosture.QIYAM_RISING -> Color(0xFF00BCD4) // Cyan
        else -> Color.Gray
    }
}
