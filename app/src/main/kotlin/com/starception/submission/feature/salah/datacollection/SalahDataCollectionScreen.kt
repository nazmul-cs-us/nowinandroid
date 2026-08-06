package com.starception.submission.feature.salah.datacollection

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ripple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starception.submission.feature.salah.visualization.CurrentSampleCard
import com.starception.submission.feature.salah.visualization.DataQualitySummary
import com.starception.submission.feature.salah.visualization.VisualizationModePicker
import com.starception.submission.feature.salah.visualization.VisualizationPlaybackDeck
import com.starception.submission.feature.salah.visualization.PosePlaybackSource
import com.starception.submission.feature.salah.visualization.Visualization3DView
import com.starception.submission.feature.salah.visualization.VisualizationControls
import com.starception.submission.feature.salah.visualization.VisualizationState
import com.starception.submission.core.designsystem.animation.NiaMotion
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import com.starception.submission.ml.SalahDataSample
import com.starception.submission.ml.SalahPosture
import java.text.SimpleDateFormat
import kotlin.math.abs
import java.util.Date
import java.util.Locale
import com.starception.submission.core.designsystem.theme.FloatingNavClearance

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SalahDataCollectionScreen(
    onBackClick: () -> Unit,
    onNavigateToLiveRecording: () -> Unit = {},
    onNavigateToReview: (String) -> Unit = {},
    viewModel: SalahDataCollectionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val vizState by viewModel.vizState.collectAsState()
    val allSamples by viewModel.allSamples.collectAsState()
    val fileQuality by viewModel.fileQuality.collectAsState()
    val deployedModel by viewModel.deployedModel.collectAsState()
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showDeleteFileDialog by remember { mutableStateOf<String?>(null) }
    var showVisualization by remember { mutableStateOf(false) }
    var isVizFullscreen by remember { mutableStateOf(false) }

    // Load one recording and open it fullscreen, so the result is visible immediately
    // rather than landing in a collapsed card further up the list.
    val visualizeFile: (String) -> Unit = { fileName ->
        viewModel.loadSamplesForFile(fileName)
        showVisualization = true
        isVizFullscreen = true
    }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Salah Training",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.isCountingDown) {
                            Text(
                                text = "Starting in ${uiState.countdownSeconds}s...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        } else if (uiState.isRecording) {
                            Text(
                                text = "Recording ${uiState.currentPosture.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                text = "Build reliable prayer detection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    Surface(
                        modifier = Modifier
                            .padding(start = 8.dp, end = 8.dp)
                            .size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        IconButton(onClick = onBackClick) {
                            FlaticonIcon(
                                glyph = FlaticonIcons.ARROW_BACK,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                fontSize = 20.sp,
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.dataFiles.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            FlaticonIcon(
                                glyph = FlaticonIcons.DELETE,
                                contentDescription = "Delete all data",
                                tint = MaterialTheme.colorScheme.error,
                                fontSize = 21.sp,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Left: Controls + Postures
                LazyColumn(
                    modifier = Modifier.weight(0.5f),
                    contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = FloatingNavClearance),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Readiness and the primary capture action stay above diagnostics.
                    item {
                        CollectionReadinessCard(
                            uiState = uiState,
                            onSelectPosture = { viewModel.setPosture(it) },
                        )
                    }
                    item { RecordingHero(uiState, viewModel) }
                    item { PostureSelector(uiState, viewModel) }
                    // Capture quality feedback during recording
                    if (uiState.isRecording && uiState.lastSample != null) {
                        item { CaptureQualityCard(uiState) }
                    }
                    // Guided recording card
                    item { GuidedRecordingCard(uiState, viewModel) }
                    // Live prayer recording (always visible when not recording)
                    if (!uiState.isRecording && !uiState.isCountingDown) {
                        item { LivePrayerRecordingCard(onNavigateToLiveRecording) }
                    }
                    item { TrainingProgress(uiState) }
                    item { deployedModel?.let { DeployedModelCard(it) } }
                    // 3D Visualization Card
                    item {
                        Visualization3DCard(
                            allSamples = allSamples,
                            vizState = vizState,
                            showVisualization = showVisualization,
                            onToggleVisualization = {
                                showVisualization = it
                                if (it) viewModel.loadAllSamples()
                            },
                            onVizStateChange = { viewModel.updateVizState(it) },
                            isFullscreen = isVizFullscreen,
                            onFullscreenChange = { isVizFullscreen = it },
                            sourceFileName = uiState.vizSourceFile,
                            onAnalyzePredictions = { viewModel.analyzeVizPredictions() },
                            onPlaybackTick = viewModel::onVizPlaybackTick
                        )
                    }
                }
                // Right: Files + Stats
                LazyColumn(
                    modifier = Modifier.weight(0.5f),
                    contentPadding = PaddingValues(start = 8.dp, end = 16.dp, top = 8.dp, bottom = FloatingNavClearance),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { SessionStats(uiState) }
                    item { SensorPreview(uiState) }
                    item { DataFilesHeader(uiState) }
                    items(uiState.dataFiles, key = { it.name }) { file ->
                        SwipeToDismissFileItem(
                            file = file,
                            onDelete = { showDeleteFileDialog = file.name },
                            quality = fileQuality[file.name],
                            onAnalyze = { viewModel.analyzeFileQuality(file) },
                            onReview = { onNavigateToReview(viewModel.filePathFor(file.name)) },
                            onVisualize = { visualizeFile(file.name) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = FloatingNavClearance),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Workflow order: readiness → capture → fine-tune posture → review.
                item {
                    CollectionReadinessCard(
                        uiState = uiState,
                        onSelectPosture = { viewModel.setPosture(it) },
                    )
                }
                item { RecordingHero(uiState, viewModel) }
                item { PostureSelector(uiState, viewModel) }
                // Keep onboarding available without placing it before the primary action.
                if (!uiState.isRecording && !uiState.isCountingDown && uiState.dataFiles.isEmpty()) {
                    item { QuickGuide() }
                }
                // Capture quality feedback during recording
                if (uiState.isRecording && uiState.lastSample != null) {
                    item { CaptureQualityCard(uiState) }
                }
                // Guided recording card
                item { GuidedRecordingCard(uiState, viewModel) }
                // Live prayer recording (always visible when not recording)
                if (!uiState.isRecording && !uiState.isCountingDown) {
                    item { LivePrayerRecordingCard(onNavigateToLiveRecording) }
                }
                item { SessionStats(uiState) }
                item { TrainingProgress(uiState) }
                item { deployedModel?.let { DeployedModelCard(it) } }
                // 3D Visualization Card
                item {
                    Visualization3DCard(
                        allSamples = allSamples,
                        vizState = vizState,
                        showVisualization = showVisualization,
                        onToggleVisualization = {
                            showVisualization = it
                            if (it) viewModel.loadAllSamples()
                        },
                        onVizStateChange = { viewModel.updateVizState(it) },
                        isFullscreen = isVizFullscreen,
                        onFullscreenChange = { isVizFullscreen = it },
                        sourceFileName = uiState.vizSourceFile,
                        onAnalyzePredictions = { viewModel.analyzeVizPredictions() },
                        onPlaybackTick = viewModel::onVizPlaybackTick
                    )
                }
                item { SensorPreview(uiState) }
                item { DataFilesHeader(uiState) }
                items(uiState.dataFiles, key = { it.name }) { file ->
                    SwipeToDismissFileItem(
                        file = file,
                        onDelete = { showDeleteFileDialog = file.name },
                        quality = fileQuality[file.name],
                        onAnalyze = { viewModel.analyzeFileQuality(file) },
                        onReview = { onNavigateToReview(viewModel.filePathFor(file.name)) },
                        onVisualize = { visualizeFile(file.name) }
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    // Delete ALL dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon = {
                FlaticonIcon(
                    glyph = FlaticonIcons.DELETE,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    fontSize = 28.sp,
                )
            },
            title = { Text("Delete All Data?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will permanently delete ${uiState.dataFiles.size} file(s) " +
                        "(${uiState.totalDataSizeKb} KB) and ${uiState.globalTotalSamples} samples. " +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                NiaOutlinedButton(
                    onClick = {
                        viewModel.deleteAllData()
                        showDeleteAllDialog = false
                    }
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete SINGLE FILE dialog
    showDeleteFileDialog?.let { fileName ->
        AlertDialog(
            onDismissRequest = { showDeleteFileDialog = null },
            icon = {
                FlaticonIcon(
                    glyph = FlaticonIcons.DELETE,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    fontSize = 26.sp,
                )
            },
            title = { Text("Delete File?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Delete this recording?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                NiaOutlinedButton(
                    onClick = {
                        viewModel.deleteFile(fileName)
                        showDeleteFileDialog = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFileDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════
// LIVE PRAYER RECORDING CARD
// ═══════════════════════════════════════════════════════

@Composable
private fun LivePrayerRecordingCard(onNavigateToLiveRecording: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(
                    bounded = true,
                    color = MaterialTheme.colorScheme.primary
                ),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigateToLiveRecording()
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                FlaticonIcon(
                    glyph = FlaticonIcons.POSTURE_TRAINING,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    fontSize = 22.sp,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Record Live Prayer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Pray normally with phone in pocket. ML detects postures automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            FlaticonIcon(
                glyph = FlaticonIcons.ANGLE_RIGHT,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                fontSize = 20.sp,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// GUIDED RECORDING CARD
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuidedRecordingCard(
    uiState: SalahDataCollectionUiState,
    viewModel: SalahDataCollectionViewModel
) {
    val guidedState = uiState.guidedState
    val hapticFeedback = LocalHapticFeedback.current
    val guidedPosture = if (guidedState == GuidedRecordingState.IDLE) {
        uiState.currentPosture
    } else {
        uiState.guidedCurrentPosture ?: uiState.currentPosture
    }
    val isFocusedMovementSession = uiState.guidedSpecificOnly && guidedPosture in setOf(
        SalahPosture.QIYAM_RISING,
        SalahPosture.GOING_TO_SUJUD,
        SalahPosture.RISING_TO_QIYAM,
    )
    val selectedStoredWindows = uiState.globalPostureCounts[uiState.currentPosture.name] ?: 0
    val selectedEligibleSessions = eligibleSessionCount(uiState, uiState.currentPosture)
    val remainingWindows = (POSTURE_TARGET_WINDOWS - selectedStoredWindows).coerceAtLeast(0)
    val remainingSplitSessions = (MIN_INDEPENDENT_SESSIONS - selectedEligibleSessions).coerceAtLeast(0)
    val expectedWindowsPerGuidedSession = if (isFocusedMovementSession) {
        TRANSITION_DURATION * WINDOWS_PER_SECOND * FOCUSED_MOVEMENT_REPETITIONS
    } else {
        uiState.guidedSelectedDuration * WINDOWS_PER_SECOND
    }
    val sessionsForWindowTarget = if (remainingWindows == 0) {
        0
    } else {
        (remainingWindows + expectedWindowsPerGuidedSession - 1) / expectedWindowsPerGuidedSession
    }
    val recommendedSessions = maxOf(remainingSplitSessions, sessionsForWindowTarget)
    val hasRecommendedCoverage = recommendedSessions == 0

    // Hide when manual recording is active (non-guided)
    if ((uiState.isRecording || uiState.isCountingDown) && guidedState == GuidedRecordingState.IDLE) {
        return
    }

    // Hide when fully idle and manual recording just finished
    if (guidedState == GuidedRecordingState.IDLE && uiState.trimmedSamples > 0) {
        return
    }

    // IDLE reads as a normal surface card like every other section; only the
    // active guided-session states keep a soft primary wash so a running
    // session stays visually distinct.
    val cardColor = when (guidedState) {
        GuidedRecordingState.IDLE -> MaterialTheme.colorScheme.surface
        GuidedRecordingState.PREPARING,
        GuidedRecordingState.WELCOME, GuidedRecordingState.COUNTDOWN -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        GuidedRecordingState.RECORDING_POSTURE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        GuidedRecordingState.POSTURE_TRANSITION -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        GuidedRecordingState.COMPLETED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        GuidedRecordingState.CANCELLED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (guidedState != GuidedRecordingState.IDLE) 6.dp else 3.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            )
            .animateContentSize(tween(300))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (guidedState) {
                GuidedRecordingState.IDLE -> {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            FlaticonIcon(
                                glyph = FlaticonIcons.VOICE,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                fontSize = 22.sp,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Guided Recording",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Voice cues separate movement from held postures",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Choose between one selected label and the complete sequence.
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            listOf(true to "Specific", false to "Full prayer").forEach { (specific, label) ->
                                val isSelected = uiState.guidedSpecificOnly == specific
                                val segColor by animateColorAsState(
                                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent,
                                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                                    label = "segColor"
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(50))
                                        .background(segColor)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(bounded = true),
                                            onClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.setGuidedSpecificOnly(specific)
                                            }
                                        )
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    val selectedIsMovement = isFocusedMovementSession
                    if (uiState.guidedSpecificOnly) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(
                                    text = "SELECTED ${if (selectedIsMovement) "MOVEMENT" else "POSTURE"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp,
                                )
                                Text(
                                    text = uiState.currentPosture.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text = "Change this using the posture and movement choices above.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (hasRecommendedCoverage) {
                                        "RECOMMENDED COVERAGE REACHED"
                                    } else {
                                        "RECOMMENDED NEXT"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp,
                                )
                                Text(
                                    text = when {
                                        hasRecommendedCoverage ->
                                            "No additional captures required for baseline coverage"
                                        selectedIsMovement -> {
                                            val takes = recommendedSessions * FOCUSED_MOVEMENT_REPETITIONS
                                            "Record $recommendedSessions more guided ${if (recommendedSessions == 1) "session" else "sessions"} ($takes takes)"
                                        }
                                        else ->
                                            "Record $recommendedSessions more independent ${uiState.guidedSelectedDuration}s ${if (recommendedSessions == 1) "capture" else "captures"}"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text = "$selectedStoredWindows of $POSTURE_TARGET_WINDOWS clean windows · " +
                                        "$selectedEligibleSessions of $MIN_INDEPENDENT_SESSIONS independent sessions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                )
                            }
                        }
                    }

                    if (!uiState.guidedSpecificOnly || !selectedIsMovement) {
                        Text(
                            text = if (uiState.guidedSpecificOnly) "Hold duration" else "Hold duration per posture",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(4.dp)) {
                                listOf(10, 15, 20, 30).forEach { duration ->
                                    val isSelected = uiState.guidedSelectedDuration == duration
                                    val segColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent,
                                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                                        label = "durationColor"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(50))
                                            .background(segColor)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = ripple(bounded = true),
                                                onClick = {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    viewModel.setGuidedDuration(duration)
                                                }
                                            )
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${duration}s",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "The guide records 5 separate takes. Each 5-second capture begins with “Move now,” then pauses while you return to the starting position.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Text(
                        text = if (uiState.guidedSpecificOnly) {
                            "Before starting: secure the phone in one trouser pocket. Use a separate take for each movement starting position, and collect at least 3 independent sessions."
                        } else {
                            "Before starting: turn up media volume and secure the phone in one trouser pocket. Keep the same placement for the full session; vary placement between sessions. Record at least 3 complete sessions for train, validation, and test splits."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                    )

                    // TTS Engine Download Section — flat editorial rows, no
                    // nested box; the tonal button carries the action.
                    if (!uiState.isTtsAvailable) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Voice-guided recording needs the Kokoro TTS engine (~175 MB, one-time download).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )

                            if (uiState.isTtsDownloading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "Downloading… pull down to see progress",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                uiState.ttsDownloadError?.let { error ->
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                NiaOutlinedButton(
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.downloadTtsEngine()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    FlaticonIcon(
                                        glyph = FlaticonIcons.DOWNLOAD,
                                        contentDescription = null,
                                        fontSize = 17.sp,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (uiState.ttsDownloadError != null) "Retry Download (~175 MB)" else "Download Voice Engine",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }

                    // Start button — app-wide outlined style.
                    NiaOutlinedButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.startGuidedRecording()
                        },
                        enabled = uiState.isTtsAvailable,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (uiState.guidedSpecificOnly) "Guide This Selection" else "Start Full Guided Recording",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                GuidedRecordingState.PREPARING -> {
                    val total = uiState.guidedPrepareTotal
                    val done = uiState.guidedPrepareDone
                    Text(
                        text = "Preparing Voice",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (total > 0) {
                        LinearProgressIndicator(
                            progress = { done.toFloat() / total },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                        Text(
                            text = "$done of $total instructions ready",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                    Text(
                        text = "Generating every instruction now so the session never pauses while you hold a posture.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    NiaOutlinedButton(
                        onClick = { viewModel.cancelGuidedRecording() }
                    ) {
                        Text("Cancel")
                    }
                }

                GuidedRecordingState.WELCOME -> {
                    val infiniteTransition = rememberInfiniteTransition(label = "welcome_pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.6f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "welcome_alpha"
                    )

                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Get Ready",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = uiState.guidedMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    NiaOutlinedButton(
                        onClick = { viewModel.cancelGuidedRecording() }
                    ) {
                        Text("Cancel")
                    }
                }

                GuidedRecordingState.COUNTDOWN -> {
                    val infiniteTransition = rememberInfiniteTransition(label = "cd_pulse")
                    val countdownScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "cd_scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .scale(countdownScale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.countdownSeconds > 0) "${uiState.countdownSeconds}" else "Go!",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Keep the phone secured in your pocket",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    NiaOutlinedButton(
                        onClick = { viewModel.cancelGuidedRecording() }
                    ) {
                        Text("Cancel")
                    }
                }

                GuidedRecordingState.RECORDING_POSTURE -> {
                    val posture = uiState.guidedCurrentPosture
                    val timeRemaining = uiState.guidedPostureTimeRemaining
                    val totalDuration = uiState.guidedPostureDuration
                    val progress = if (totalDuration > 0) {
                        1f - (timeRemaining.toFloat() / totalDuration)
                    } else 0f
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "guided_progress"
                    )

                    // Pulsing recording indicator
                    val infiniteTransition = rememberInfiniteTransition(label = "rec_indicator")
                    val recAlpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "rec_dot"
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = recAlpha))
                        )
                        Text(
                            text = "RECORDING",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = if (isFocusedMovementSession) {
                                    "TAKE ${uiState.guidedPostureIndex + 1} OF ${uiState.guidedTotalPostures}"
                                } else {
                                    "STEP ${uiState.guidedPostureIndex + 1} OF ${uiState.guidedTotalPostures}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Circular progress with posture name
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(120.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.size(120.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            strokeWidth = 6.dp,
                            strokeCap = StrokeCap.Round
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${timeRemaining}s",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Posture name
                    if (posture != null) {
                        Text(
                            text = uiState.guidedMessage.ifBlank { posture.displayName },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (posture.arabicName.isNotEmpty()) {
                            Text(
                                text = posture.arabicName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                fontSize = 20.sp
                            )
                        }
                    }

                    // Cancel button
                    NiaOutlinedButton(
                        onClick = { viewModel.cancelGuidedRecording() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FlaticonIcon(
                            glyph = FlaticonIcons.STOP,
                            contentDescription = null,
                            fontSize = 17.sp,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel Guided Recording")
                    }
                }

                GuidedRecordingState.POSTURE_TRANSITION -> {
                    val infiniteTransition = rememberInfiniteTransition(label = "transition_pulse")
                    val transAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "transition_alpha"
                    )

                    if (isFocusedMovementSession && uiState.guidedTotalPostures > 1) {
                        val completedTake = uiState.guidedPostureTimeRemaining == 0
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = if (completedTake) {
                                    "TAKE ${uiState.guidedPostureIndex + 1} COMPLETE · NEXT ${uiState.guidedPostureIndex + 2} OF ${uiState.guidedTotalPostures}"
                                } else {
                                    "PREPARE TAKE ${uiState.guidedPostureIndex + 1} OF ${uiState.guidedTotalPostures}"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }

                    Text(
                        text = uiState.guidedMessage,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = transAlpha),
                        textAlign = TextAlign.Center
                    )

                    NiaOutlinedButton(
                        onClick = { viewModel.cancelGuidedRecording() }
                    ) {
                        Text("Cancel")
                    }
                }

                GuidedRecordingState.COMPLETED -> {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "\u2714",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Recording Complete!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = uiState.guidedMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "${uiState.totalSamples} samples across ${uiState.postureCounts.size} postures",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    NiaOutlinedButton(
                        onClick = { viewModel.resetGuidedState() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }

                GuidedRecordingState.CANCELLED -> {
                    Text(
                        text = "Guided Recording Stopped",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (uiState.guidedMessage.isNotBlank()) {
                        Text(
                            text = uiState.guidedMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (uiState.totalSamples > 0) {
                        Text(
                            text = "${uiState.totalSamples} samples saved before cancellation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    NiaOutlinedButton(
                        onClick = { viewModel.resetGuidedState() }
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// NEXT-UP GUIDANCE + DEPLOYED MODEL QUALITY
// ═══════════════════════════════════════════════════════

private fun eligibleSessionCount(
    uiState: SalahDataCollectionUiState,
    posture: SalahPosture,
): Int = uiState.dataFiles.count { file ->
    file.trainingIssue == null && (file.postureCounts[posture.name] ?: 0) > 0
}

/**
 * A concise definition of "done" for collectors. Collection is ready only when
 * every class has enough windows and independent files; model retraining remains
 * a separate, quality-gated step after the recordings are exported.
 */
@Composable
private fun CollectionReadinessCard(
    uiState: SalahDataCollectionUiState,
    onSelectPosture: (SalahPosture) -> Unit,
) {
    val labels = SalahPosture.classificationLabels
    val windowsReady = labels.count { posture ->
        (uiState.globalPostureCounts[posture.name] ?: 0) >= POSTURE_TARGET_WINDOWS
    }
    val sessionsReady = labels.count { posture ->
        eligibleSessionCount(uiState, posture) >= MIN_INDEPENDENT_SESSIONS
    }
    val labelsReady = labels.count { posture ->
        (uiState.globalPostureCounts[posture.name] ?: 0) >= POSTURE_TARGET_WINDOWS &&
            eligibleSessionCount(uiState, posture) >= MIN_INDEPENDENT_SESSIONS
    }
    val pendingReview = uiState.dataFiles.count { it.isPendingReview }
    val ranked = labels
        .map { posture ->
            Triple(
                posture,
                uiState.globalPostureCounts[posture.name] ?: 0,
                eligibleSessionCount(uiState, posture),
            )
        }
        .sortedBy { (_, windows, sessions) ->
            minOf(
                windows / POSTURE_TARGET_WINDOWS.toFloat(),
                sessions / MIN_INDEPENDENT_SESSIONS.toFloat(),
            )
        }
    val recommended = ranked.firstOrNull { (_, windows, sessions) ->
        windows < POSTURE_TARGET_WINDOWS || sessions < MIN_INDEPENDENT_SESSIONS
    }
    val readiness = ranked.sumOf { (_, windows, sessions) ->
        minOf(
            windows.coerceAtMost(POSTURE_TARGET_WINDOWS) / POSTURE_TARGET_WINDOWS.toFloat(),
            sessions.coerceAtMost(MIN_INDEPENDENT_SESSIONS) /
                MIN_INDEPENDENT_SESSIONS.toFloat(),
        ).toDouble()
    }.toFloat() / labels.size
    val progress by animateFloatAsState(
        targetValue = readiness,
        animationSpec = NiaMotion.emphasizedTween(NiaMotion.Duration.LONG_1),
        label = "collectionReadiness",
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.TRENDING_UP,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        fontSize = 22.sp,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Training readiness",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "$labelsReady of ${labels.size} postures ready for training",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                strokeCap = StrokeCap.Round,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrainingReadinessMetric(
                    value = "$windowsReady/${labels.size}",
                    label = "Coverage",
                    modifier = Modifier.weight(1f),
                )
                TrainingReadinessMetric(
                    value = "$sessionsReady/${labels.size}",
                    label = "Sessions",
                    modifier = Modifier.weight(1f),
                )
                TrainingReadinessMetric(
                    value = "$pendingReview",
                    label = "To review",
                    modifier = Modifier.weight(1f),
                    needsAttention = pendingReview > 0,
                )
            }

            if (recommended != null) {
                val (posture, windows, sessions) = recommended
                Surface(
                    onClick = { onSelectPosture(posture) },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "NEXT CAPTURE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = posture.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                if (posture.arabicName.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = posture.arabicName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                            .copy(alpha = 0.72f),
                                    )
                                }
                            }
                            Text(
                                text = "${(POSTURE_TARGET_WINDOWS - windows).coerceAtLeast(0)} windows · " +
                                    "${(MIN_INDEPENDENT_SESSIONS - sessions).coerceAtLeast(0)} sessions still needed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Select ${posture.displayName}",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FlaticonIcon(
                            glyph = FlaticonIcons.VERIFIED,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            fontSize = 22.sp,
                        )
                        Column {
                            Text(
                                text = "Collection baseline ready",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "Review approved recordings before retraining the model.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingReadinessMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    needsAttention: Boolean = false,
) {
    Surface(
        modifier = modifier,
        color = if (needsAttention) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (needsAttention) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (needsAttention) {
                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.76f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
        }
    }
}

/**
 * Quality of the model currently shipped in assets — from the training pipeline's
 * dataset_report.json. Absent until the first `export_tflite.py --deploy` run.
 */
@Composable
private fun DeployedModelCard(info: DeployedModelInfo) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlaticonIcon(
                    glyph = if (info.isDeploymentReady) {
                        FlaticonIcons.VERIFIED
                    } else {
                        FlaticonIcons.DEVELOPER
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    fontSize = 19.sp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Model v${info.modelVersion}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (info.isDeploymentReady) {
                        "ready · test ${(info.testAccuracy * 100).toInt()}%"
                    } else {
                        "experimental · test ${(info.testAccuracy * 100).toInt()}%"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (info.isDeploymentReady) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            Text(
                text = buildString {
                    append("val ${(info.valAccuracy * 100).toInt()}%")
                    if (info.weakestClasses.isNotEmpty()) {
                        append(" · weakest: ")
                        append(
                            info.weakestClasses.joinToString(", ") { (name, f1) ->
                                "$name ${(f1 * 100).toInt()}%"
                            }
                        )
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// QUICK GUIDE
// ═══════════════════════════════════════════════════════

@Composable
private fun QuickGuide() {
    // Collapsed by default: the steps matter on first use, then become scroll noise.
    var expanded by remember { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.INFO,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        fontSize = 21.sp,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "How to Collect Data",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                val chevron by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = NiaMotion.standardTween(NiaMotion.Duration.SHORT_4),
                    label = "guideChevron"
                )
                FlaticonIcon(
                    glyph = FlaticonIcons.ANGLE_DOWN,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = chevron },
                    fontSize = 18.sp,
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = expanded,
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = NiaMotion.spatialDefault()
                ) + androidx.compose.animation.fadeIn(
                    animationSpec = NiaMotion.standardTween(NiaMotion.Duration.SHORT_4)
                ),
                exit = androidx.compose.animation.shrinkVertically(
                    animationSpec = NiaMotion.standardTween(NiaMotion.Duration.MEDIUM_1)
                ) + androidx.compose.animation.fadeOut(
                    animationSpec = NiaMotion.standardTween(NiaMotion.Duration.SHORT_3)
                ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(12.dp))
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
            Spacer(modifier = Modifier.height(16.dp))

            val steps = listOf(
                "Select a posture below",
                "Tap Record - 5s countdown starts",
                "Put phone in pocket & hold posture",
                "Tap Stop when done",
                "Last 3s auto-trimmed"
            )
            steps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
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
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// RECORDING HERO - Large animated record button
// ═══════════════════════════════════════════════════════

@Composable
private fun RecordingHero(
    uiState: SalahDataCollectionUiState,
    viewModel: SalahDataCollectionViewModel
) {
    val shadowElevation = when {
        uiState.isRecording -> 8.dp
        uiState.isCountingDown -> 6.dp
        else -> 3.dp
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                uiState.isCountingDown -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                uiState.isRecording -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(28.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            )
            .animateContentSize(tween(300))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (uiState.isRecording) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        FlaticonIcon(
                            glyph = FlaticonIcons.MICROPHONE,
                            contentDescription = null,
                            tint = if (uiState.isRecording) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            fontSize = 17.sp,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uiState.isRecording) {
                            "Recording ${uiState.currentPosture.displayName}"
                        } else {
                            "Record ${uiState.currentPosture.displayName}"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (uiState.isRecording) {
                            "Hold the pose, then stop when the sample is complete"
                        } else {
                            "Start, place the phone securely, then perform the selected pose"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Countdown
            if (uiState.isCountingDown) {
                val infiniteTransition = rememberInfiniteTransition(label = "countdown_pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${uiState.countdownSeconds}",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Put phone in pocket now!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                NiaOutlinedButton(
                    onClick = { viewModel.cancelCountdown() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
                return@Card
            }

            // Recording state with pulsing indicator
            if (uiState.isRecording) {
                val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
                val recAlpha by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "rec_alpha"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = recAlpha))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "RECORDING",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = "${uiState.totalSamples} samples",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                // Current posture display
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.currentPosture.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (uiState.currentPosture.arabicName.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = uiState.currentPosture.arabicName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

            // Record / Stop Button — shared app-wide button style.
            val heroHaptics = LocalHapticFeedback.current
            if (uiState.isRecording) {
                NiaOutlinedButton(
                    onClick = {
                        heroHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.stopRecording()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.STOP,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        fontSize = 21.sp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stop Recording",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                NiaOutlinedButton(
                    onClick = {
                        heroHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.startRecording()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.MICROPHONE,
                        contentDescription = null,
                        fontSize = 19.sp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Recording", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }

            // Trim info
            if (uiState.isRecording) {
                Text(
                    text = "Last 3s auto-trimmed on stop",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp)
                )
            } else if (uiState.trimmedSamples > 0) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Text(
                        text = "Trimmed ${uiState.trimmedSamples} samples (phone-grab noise)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// POSTURE SELECTOR - Grid layout (all visible)
// ═══════════════════════════════════════════════════════

@Composable
private fun PostureSelector(
    uiState: SalahDataCollectionUiState,
    viewModel: SalahDataCollectionViewModel
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            )
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                        FlaticonIcon(
                            glyph = FlaticonIcons.POSTURE_TRAINING,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            fontSize = 18.sp,
                        )
                    }
                    Text(
                        text = "Choose posture",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = uiState.currentPosture.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = postureRecordingGuidance(uiState.currentPosture),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "STILL POSITIONS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))

            val staticPostures = listOf(
                SalahPosture.QIYAM,
                SalahPosture.RUKU,
                SalahPosture.SUJUD,
                SalahPosture.JALSA,
                SalahPosture.TASHAHHUD,
            )
            staticPostures.chunked(3).forEachIndexed { rowIndex, rowPostures ->
                if (rowIndex > 0) Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowPostures.forEach { posture ->
                        Box(modifier = Modifier.weight(1f)) {
                            PostureChip(
                                posture = posture,
                                isSelected = uiState.currentPosture == posture,
                                isRecording = uiState.isRecording,
                                sessionCount = uiState.postureCounts[posture] ?: 0,
                                globalCount = uiState.globalPostureCounts[posture.name] ?: 0,
                                onClick = { viewModel.setPosture(posture) },
                            )
                        }
                    }
                    repeat(3 - rowPostures.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "MOVEMENTS · RECORD ONLY THE ARROW",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            listOf(
                SalahPosture.QIYAM_RISING to "Ruku\n→ Standing",
                SalahPosture.GOING_TO_SUJUD to "Standing or Sitting\n→ Sujud",
                SalahPosture.RISING_TO_QIYAM to "Second Sujud or Sitting\n→ Standing",
            ).chunked(2).forEachIndexed { rowIndex, rowMovements ->
                if (rowIndex > 0) Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowMovements.forEach { (posture, label) ->
                        Box(modifier = Modifier.weight(1f)) {
                            PostureChip(
                                posture = posture,
                                label = label,
                                isSelected = uiState.currentPosture == posture,
                                isRecording = uiState.isRecording,
                                sessionCount = uiState.postureCounts[posture] ?: 0,
                                globalCount = uiState.globalPostureCounts[posture.name] ?: 0,
                                onClick = { viewModel.setPosture(posture) },
                            )
                        }
                    }
                    repeat(2 - rowMovements.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun postureRecordingGuidance(posture: SalahPosture): String = when (posture) {
    SalahPosture.QIYAM ->
        "Static posture: stand upright with your hands folded and remain still."
    SalahPosture.RUKU ->
        "Static posture: hold ruku with your hands on your knees and remain still."
    SalahPosture.QIYAM_RISING ->
        "Record only RUKU → STANDING. Begin already in ruku and stop fully upright. Do not continue toward sujud."
    SalahPosture.RISING_TO_QIYAM ->
        "Record only SECOND SUJUD OR TASHAHHUD → STANDING. Start fully down or seated, rise naturally into the next rak‘ah, and stop upright. Record both starting variants in separate takes."
    SalahPosture.GOING_TO_SUJUD ->
        "Record only STANDING OR SITTING → SUJUD. Begin already upright or already seated. Do not include rising from ruku; record that movement separately."
    SalahPosture.SUJUD ->
        "Static posture: hold the prostration position and remain still."
    SalahPosture.JALSA ->
        "Static posture: sit upright between the two prostrations and remain still."
    SalahPosture.TASHAHHUD ->
        "Static posture: hold the final seated tashahhud position and remain still."
    SalahPosture.NOT_PRAYING ->
        "Negative example: carry the phone in your pocket and do anything that is NOT prayer — " +
            "walk, sit, use stairs, drive, or leave it on a desk. Record one activity per take " +
            "so a confusion can be traced back to it. Never include any prayer posture."
}

@Composable
private fun PostureChip(
    posture: SalahPosture,
    label: String = posture.displayName,
    isSelected: Boolean,
    isRecording: Boolean,
    sessionCount: Int,
    globalCount: Int,
    onClick: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val isComplete = globalCount >= POSTURE_TARGET_WINDOWS

    val textColor by animateColorAsState(
        targetValue = when {
            isSelected && isRecording -> MaterialTheme.colorScheme.onPrimary
            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "text"
    )
    val shadowElevation by animateFloatAsState(
        targetValue = if (isSelected) 3f else 0f,
        label = "shadowElevation"
    )

    // Each tile doubles as a dataset gauge: the bar fills toward the
    // 500-window target so the grid reads as a per-class dashboard while
    // you collect. Completed classes get a check badge.
    val datasetProgress by animateFloatAsState(
        targetValue = globalCount.coerceAtMost(POSTURE_TARGET_WINDOWS) / POSTURE_TARGET_WINDOWS.toFloat(),
        animationSpec = NiaMotion.emphasizedTween(NiaMotion.Duration.LONG_1),
        label = "postureDatasetProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .shadow(
                elevation = shadowElevation.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(
                        bounded = true,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                ),
            shape = RoundedCornerShape(18.dp),
            color = Color.Transparent
        ) {
            // Background with gradient for selected+recording
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSelected && isRecording) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                                }
                            )
                    )
                }
            }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                fontSize = 12.sp,
                lineHeight = 14.sp,
            )
            if (posture.arabicName.isNotEmpty()) {
                Text(
                    text = posture.arabicName,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LinearProgressIndicator(
                    progress = { datasetProgress },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (isSelected && isRecording) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = textColor.copy(alpha = 0.15f),
                )
                Text(
                    // Live "+N" while this posture is being recorded; the
                    // stored dataset count otherwise.
                    text = if (isRecording && sessionCount > 0) "+$sessionCount" else "$globalCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
        }
        }
        if (isComplete) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                FlaticonIcon(
                    glyph = FlaticonIcons.CHECK,
                    contentDescription = "Target reached",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/** Dataset target per posture class, in 100ms windows — matches the training
 *  pipeline's expectation and the progress card at the top of the screen. */
private const val POSTURE_TARGET_WINDOWS = 500
private const val MIN_INDEPENDENT_SESSIONS = 3
private const val WINDOWS_PER_SECOND = 10

// ═══════════════════════════════════════════════════════
// SESSION STATS
// ═══════════════════════════════════════════════════════

@Composable
private fun SessionStats(uiState: SalahDataCollectionUiState) {
    AnimatedVisibility(
        visible = uiState.isRecording || uiState.totalSamples > 0,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 3.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
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
                        FlaticonIcon(
                            glyph = FlaticonIcons.MICROPHONE,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            fontSize = 15.sp,
                        )
                    }
                    Text(
                        text = "Session",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (uiState.postureCounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
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
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatPill("Samples", "${uiState.totalSamples}", MaterialTheme.colorScheme.primary)
                    StatPill("Postures", "${uiState.postureCounts.size}", MaterialTheme.colorScheme.tertiary)
                    StatPill(
                        "Active",
                        uiState.currentPosture.displayName,
                        MaterialTheme.colorScheme.secondary
                    )
                }

                if (uiState.postureCounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    uiState.postureCounts.forEach { (posture, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = posture.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, color: Color) {
    // Numbers-first, borderless: the value is the visual anchor, the label a
    // quiet caption underneath.
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ═══════════════════════════════════════════════════════
// SENSOR PREVIEW
// ═══════════════════════════════════════════════════════

@Composable
private fun SensorPreview(uiState: SalahDataCollectionUiState) {
    AnimatedVisibility(
        visible = uiState.lastSample != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        uiState.lastSample?.let { sample ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.tertiaryContainer,
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            FlaticonIcon(
                                glyph = FlaticonIcons.QUICK_ACTION,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                fontSize = 15.sp,
                            )
                        }
                        Text(
                            text = "Live Sensors",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    // Gradient divider
                    Box(modifier = Modifier.fillMaxWidth()) {
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
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SensorValue("Pitch", "%.1f\u00B0".format(sample.pitch))
                        SensorValue("Roll", "%.1f\u00B0".format(sample.roll))
                        SensorValue("Accel", "%.2f".format(sample.accelMagnitude))
                        SensorValue("Gyro", "%.3f".format(sample.gyroMagnitude))
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ═══════════════════════════════════════════════════════
// TRAINING PROGRESS - Circular progress per posture
// ═══════════════════════════════════════════════════════

@Composable
private fun TrainingProgress(uiState: SalahDataCollectionUiState) {
    var isTrainingExpanded by remember { mutableStateOf(true) }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isTrainingExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chevronRotation"
    )

    val shadowElevation by animateFloatAsState(
        targetValue = if (isTrainingExpanded) 5f else 2f,
        label = "shadowElevation"
    )

    AnimatedVisibility(
        visible = uiState.globalTotalSamples > 0,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = shadowElevation.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(
                                bounded = true,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            onClick = { isTrainingExpanded = !isTrainingExpanded }
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Training Progress",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${uiState.globalTotalSamples} total",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = CircleShape,
                            color = if (isTrainingExpanded)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                FlaticonIcon(
                                    glyph = FlaticonIcons.ANGLE_DOWN,
                                    contentDescription = if (isTrainingExpanded) "Collapse" else "Expand",
                                    tint = if (isTrainingExpanded)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(rotationAngle),
                                    fontSize = 17.sp,
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isTrainingExpanded,
                    enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(),
                    exit = shrinkVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeOut()
                ) {
                    Column {
                        // Gradient divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
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

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {

                val target = 500
                val postureOrder = SalahPosture.recordingLabels.map { it.name }
                val postureDisplayNames = mapOf(
                    "QIYAM" to "Qiyam",
                    "RUKU" to "Ruku",
                    "GOING_TO_SUJUD" to "Lowering to Sujud",
                    "SUJUD" to "Sujud",
                    "JALSA" to "Jalsa",
                    "TASHAHHUD" to "Tashahhud",
                    "QIYAM_RISING" to "Ruku to Standing",
                    "RISING_TO_QIYAM" to "Rise to Next Rak‘ah",
                )

                postureOrder.forEach { posture ->
                    val count = uiState.globalPostureCounts[posture] ?: 0
                    val progress = (count.toFloat() / target).coerceIn(0f, 1f)
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress,
                        animationSpec = tween(600, easing = FastOutSlowInEasing),
                        label = "progress_$posture"
                    )
                    val isComplete = count >= target

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = postureDisplayNames[posture] ?: posture,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(80.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (isComplete) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                strokeCap = StrokeCap.Round
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isComplete) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.width(40.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }

                // Per-class window coverage. The desktop inspector separately verifies
                // that there are enough independent segments for leakage-safe splits.
                val minCollected = postureOrder.minOf { uiState.globalPostureCounts[it] ?: 0 }
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (minCollected >= target) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (minCollected >= target) {
                                "Window target reached"
                            } else {
                                "Need ${target - minCollected}+ more per posture (target: $target)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (minCollected >= target) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (minCollected >= target) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// DATA FILES - Swipe to delete
// ═══════════════════════════════════════════════════════

@Composable
private fun DataFilesHeader(uiState: SalahDataCollectionUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlaticonIcon(
                glyph = FlaticonIcons.STORAGE,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 17.sp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Recorded Files",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        if (uiState.totalDataSizeKb > 0) {
            Text(
                text = "${uiState.dataFiles.size} files \u00B7 ${uiState.totalDataSizeKb} KB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (uiState.dataFiles.isEmpty()) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlaticonIcon(
                    glyph = FlaticonIcons.STORAGE,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    fontSize = 26.sp,
                )
                Text(
                    text = "No recordings yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Select a posture above and start recording.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
/**
 * Model-agreement badge for a recording: green >= 85%, amber >= 70%, red below.
 * Untested files show a tappable "check" chip that runs the analysis.
 */
@Composable
private fun FileQualityBadge(quality: FileQuality?, onAnalyze: () -> Unit) {
    when {
        quality == null -> Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onAnalyze),
        ) {
            FlaticonIcon(
                glyph = FlaticonIcons.QUICK_ACTION,
                contentDescription = "Analyze recording quality",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 11.sp,
            )
        }

        quality.isAnalyzing -> CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.5.dp
        )

        else -> {
            val pct = (quality.agreement * 100).toInt()
            val color = when {
                pct >= 85 -> Color(0xFF2E7D32)
                pct >= 70 -> Color(0xFFF9A825)
                else -> MaterialTheme.colorScheme.error
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.clickable(onClick = onAnalyze),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    FlaticonIcon(
                        glyph = if (pct >= 85 && quality.flaggedCount == 0) {
                            FlaticonIcons.VERIFIED
                        } else {
                            FlaticonIcons.WARNING
                        },
                        contentDescription = null,
                        tint = color,
                        fontSize = 9.sp,
                    )
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeToDismissFileItem(
    file: DataFileInfo,
    onDelete: () -> Unit,
    quality: FileQuality? = null,
    onAnalyze: () -> Unit = {},
    onReview: () -> Unit = {},
    onVisualize: () -> Unit = {}
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                false // Don't auto-dismiss, let dialog handle it
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier.padding(end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FlaticonIcon(
                        glyph = FlaticonIcons.DELETE,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 20.sp,
                    )
                }
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        DataFileItem(
            file = file,
            onDelete = onDelete,
            quality = quality,
            onAnalyze = onAnalyze,
            onReview = onReview,
            onVisualize = onVisualize,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataFileItem(
    file: DataFileInfo,
    onDelete: () -> Unit,
    quality: FileQuality? = null,
    onAnalyze: () -> Unit = {},
    onReview: () -> Unit = {},
    onVisualize: () -> Unit = {}
) {
    // Map posture names to short display names
    val postureDisplayNames = mapOf(
        "QIYAM" to "Qiyam",
        "RUKU" to "Ruku",
        "GOING_TO_SUJUD" to "Lowering to Sujud",
        "SUJUD" to "Sujud",
        "JALSA" to "Jalsa",
        "TASHAHHUD" to "Tashahhud",
        "QIYAM_RISING" to "Ruku to Standing",
        "RISING_TO_QIYAM" to "Rise to Next Rak‘ah",
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 1.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // File icon with gradient
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
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
                FlaticonIcon(
                    glyph = FlaticonIcons.STORAGE,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    fontSize = 17.sp,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // File info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                            .format(Date(file.lastModified)),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = " \u00B7 ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${file.sizeKb} KB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = " \u00B7 ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${file.totalSamples} samples",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FileQualityBadge(quality = quality, onAnalyze = onAnalyze)
                }

                // Surface any reason this file is excluded from training. Live and
                // legacy files have a direct review path; malformed/empty files do not.
                file.trainingIssue?.let { trainingIssue ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = trainingIssue,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Medium
                        )
                        if (file.isPendingReview) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Review →",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = false),
                                    onClick = onReview
                                )
                            )
                        }
                    }
                }

                // Posture tags
                if (file.postureCounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        file.postureCounts.entries
                            .sortedByDescending { it.value }
                            .forEach { (posture, count) ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "${postureDisplayNames[posture] ?: posture}: $count",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                    }
                }
            }

            // Load just this recording into the 3D view. Empty files have no poses to
            // play back, so the action would open an empty scene.
            if (file.totalSamples > 0) {
                IconButton(
                    onClick = onVisualize,
                    modifier = Modifier.size(36.dp)
                ) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.POSTURE_TRAINING,
                        contentDescription = "Visualize this recording in 3D",
                        tint = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                    )
                }
            }

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                FlaticonIcon(
                    glyph = FlaticonIcons.DELETE,
                    contentDescription = "Delete file",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontSize = 15.sp,
                )
            }
        }
    }
}

// ============================================
// 3D Visualization Card
// ============================================

@Composable
private fun Visualization3DCard(
    allSamples: List<SalahDataSample>,
    vizState: VisualizationState,
    showVisualization: Boolean,
    onToggleVisualization: (Boolean) -> Unit,
    onVizStateChange: (VisualizationState) -> Unit,
    // Hoisted so tapping a recording in the file list can open it straight into the
    // fullscreen view instead of quietly loading it into a card that is scrolled away.
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    sourceFileName: String? = null,
    onAnalyzePredictions: () -> Unit = {},
    onPlaybackTick: ((Int, SalahPosture?, Float, Float, Float, Float, Boolean) -> Unit)? = null
) {
    val shadowElevation by animateFloatAsState(
        targetValue = if (showVisualization) 5f else 2f,
        label = "shadowElevation"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = shadowElevation.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleVisualization(!showVisualization) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewInAr,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Review in 3D",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (allSamples.isNotEmpty()) {
                            Text(
                                // Naming the source matters: combining every recording
                                // sorts unrelated sessions into one timeline, so playback
                                // of "all files" is not a real prayer.
                                text = sourceFileName?.let { "${allSamples.size} samples · $it" }
                                    ?: "${allSamples.size} samples · all recordings combined",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Text(
                    text = if (showVisualization) "Hide" else "Show",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp)
                )
            }

            // Visualization content
            AnimatedVisibility(
                visible = showVisualization,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                    Spacer(modifier = Modifier.height(4.dp))

                    if (
                        allSamples.isEmpty() &&
                        vizState.posePlaybackSource != PosePlaybackSource.TWO_RAKAH_SAMPLE
                    ) {
                        // Empty state
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ViewInAr,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No data yet",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Record training data or open the built-in sample",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                NiaOutlinedButton(
                                    onClick = {
                                        onVizStateChange(
                                            vizState.copy(
                                                mode = com.starception.submission.feature.salah.visualization.VisualizationMode.PHONE_MODEL,
                                                posePlaybackSource = PosePlaybackSource.TWO_RAKAH_SAMPLE,
                                            ),
                                        )
                                    },
                                ) {
                                    Text("View 2 Rak'ah sample")
                                }
                            }
                        }
                    } else {
                        VisualizationModePicker(
                            state = vizState,
                            onStateChange = onVizStateChange,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // 3D View
                        if (!isFullscreen) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(520.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color(0xFF080D10))
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFF62E2C2).copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(22.dp),
                                    ),
                            ) {
                                Visualization3DView(
                                    samples = allSamples,
                                    state = vizState,
                                    onStateChange = onVizStateChange,
                                    onPlaybackTick = onPlaybackTick,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(bottom = 138.dp),
                                    onFullscreenChange = onFullscreenChange,
                                )

                                if (
                                    vizState.totalSamples > 0 ||
                                    vizState.posePlaybackSource == PosePlaybackSource.TWO_RAKAH_SAMPLE
                                ) {
                                    VisualizationPlaybackDeck(
                                        state = vizState,
                                        onStateChange = onVizStateChange,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                    )
                                }
                            }
                        } else {
                            // Keep the card's layout stable while the Filament surface is
                            // hosted by the fullscreen dialog. Only one renderer stays alive.
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(520.dp),
                            )
                        }

                        // Recorded sensor details remain available below the immersive stage.
                        if (
                            vizState.totalSamples > 0 &&
                            vizState.posePlaybackSource == PosePlaybackSource.RECORDED
                        ) {
                            CurrentSampleCard(vizState)
                        }

                        // Data quality summary
                        if (allSamples.isNotEmpty()) {
                            DataQualitySummary(
                                samples = allSamples,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Controls
                        VisualizationControls(
                            state = vizState,
                            onStateChange = onVizStateChange,
                            modifier = Modifier.fillMaxWidth(),
                            onAnalyzePredictions = onAnalyzePredictions
                        )
                    }
                }
            }
        }
    }

    if (
        isFullscreen &&
        (allSamples.isNotEmpty() || vizState.posePlaybackSource == PosePlaybackSource.TWO_RAKAH_SAMPLE)
    ) {
        Dialog(
            onDismissRequest = { onFullscreenChange(false) },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF080D10)),
            ) {
                Visualization3DView(
                    samples = allSamples,
                    state = vizState,
                    onStateChange = onVizStateChange,
                    onPlaybackTick = onPlaybackTick,
                    modifier = Modifier.fillMaxSize(),
                    isFullscreen = true,
                    onFullscreenChange = onFullscreenChange,
                )

                if (
                    vizState.totalSamples > 0 ||
                    vizState.posePlaybackSource == PosePlaybackSource.TWO_RAKAH_SAMPLE
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.94f)
                            .widthIn(max = 720.dp)
                            .navigationBarsPadding()
                            .padding(bottom = 14.dp),
                    ) {
                        VisualizationPlaybackDeck(
                            state = vizState,
                            onStateChange = onVizStateChange,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// CAPTURE QUALITY CARD - Simple visual feedback
// ═══════════════════════════════════════════════════════

@Composable
private fun CaptureQualityCard(uiState: SalahDataCollectionUiState) {
    val sample = uiState.lastSample ?: return
    val posture = uiState.currentPosture

    // Stability from gyro magnitude
    val gyro = sample.gyroMagnitude
    val isStable = gyro < 0.3f
    val isShaky = gyro >= 0.6f

    // Posture orientation check
    val orientationOk = isPostureOrientationOk(posture, sample.pitch, sample.roll)

    // Overall quality: both stability AND orientation must be good
    val overallQuality = when {
        isShaky -> CaptureQuality.BAD
        !isStable && !orientationOk -> CaptureQuality.BAD
        isStable && orientationOk -> CaptureQuality.GREAT
        isStable || orientationOk -> CaptureQuality.OK
        else -> CaptureQuality.OK
    }

    val statusColor = when (overallQuality) {
        CaptureQuality.GREAT -> Color(0xFF43A047)
        CaptureQuality.OK -> Color(0xFFFFB300)
        CaptureQuality.BAD -> Color(0xFFE53935)
    }

    val cardColor = when (overallQuality) {
        CaptureQuality.GREAT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        CaptureQuality.OK -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
        CaptureQuality.BAD -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
    }

    val shadowElevation = when (overallQuality) {
        CaptureQuality.GREAT -> 4.dp
        CaptureQuality.OK -> 3.dp
        CaptureQuality.BAD -> 2.dp
    }

    val shadowColor = when (overallQuality) {
        CaptureQuality.GREAT -> Color(0xFF43A047).copy(alpha = 0.12f)
        CaptureQuality.OK -> Color(0xFFFFB300).copy(alpha = 0.12f)
        CaptureQuality.BAD -> Color(0xFFE53935).copy(alpha = 0.12f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(20.dp),
                ambientColor = shadowColor,
                spotColor = shadowColor
            )
            .animateContentSize(tween(300))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Big status indicator circle
            val infiniteTransition = rememberInfiniteTransition(label = "quality_pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (overallQuality == CaptureQuality.BAD) 1.08f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "quality_scale"
            )

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f))
                    .border(2.5.dp, statusColor.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                FlaticonIcon(
                    glyph = when (overallQuality) {
                        CaptureQuality.GREAT -> FlaticonIcons.VERIFIED
                        CaptureQuality.OK -> FlaticonIcons.INFO
                        CaptureQuality.BAD -> FlaticonIcons.WARNING
                    },
                    contentDescription = when (overallQuality) {
                        CaptureQuality.GREAT -> "Capture quality is great"
                        CaptureQuality.OK -> "Capture quality needs attention"
                        CaptureQuality.BAD -> "Capture quality is poor"
                    },
                    tint = statusColor,
                    fontSize = 28.sp,
                )
            }

            // Main message - big and clear
            Text(
                text = when (overallQuality) {
                    CaptureQuality.GREAT -> "Perfect! Keep holding"
                    CaptureQuality.OK -> "Almost there..."
                    CaptureQuality.BAD -> "Hold still!"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                textAlign = TextAlign.Center
            )

            // Simple guidance text
            val guidanceText = getSimpleGuidance(posture, isStable, orientationOk)
            Text(
                text = guidanceText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            // Stability bar (simple, no numbers)
            val stabilityFraction = (1f - (gyro / 1.0f).coerceIn(0f, 1f))
            val animatedFraction by animateFloatAsState(
                targetValue = stabilityFraction,
                animationSpec = tween(200),
                label = "stability_bar"
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Steadiness",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .fillMaxWidth(animatedFraction)
                            .background(
                                color = statusColor,
                                shape = RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
        }
    }
}

private enum class CaptureQuality {
    GREAT, OK, BAD
}

private fun isPostureOrientationOk(posture: SalahPosture, pitch: Float, roll: Float): Boolean {
    return when (posture) {
        SalahPosture.QIYAM -> abs(pitch) < 30f && abs(roll) < 30f
        SalahPosture.RUKU -> pitch in 30f..80f || pitch in -80f..-30f
        SalahPosture.GOING_TO_SUJUD -> true  // movement expected
        SalahPosture.SUJUD -> abs(pitch) > 50f || abs(roll) > 50f
        SalahPosture.JALSA -> abs(pitch) < 45f
        SalahPosture.TASHAHHUD -> abs(pitch) < 45f
        SalahPosture.QIYAM_RISING -> true  // movement expected
        SalahPosture.RISING_TO_QIYAM -> true  // movement expected
        // Negatives are deliberately unconstrained — any orientation is valid, that is
        // the point of the class. Flagging one as "wrong" would train the user to filter
        // out exactly the variety the model needs.
        SalahPosture.NOT_PRAYING -> true
    }
}

private fun getSimpleGuidance(posture: SalahPosture, isStable: Boolean, orientationOk: Boolean): String {
    // If shaky, that's the priority message
    if (!isStable) {
        return "Place the phone in your pocket and stay in position"
    }

    // If orientation is wrong, give posture-specific simple tip
    if (!orientationOk) {
        return when (posture) {
            SalahPosture.QIYAM -> "Stand straight with phone in your pocket"
            SalahPosture.RUKU -> "Bend forward with your back level"
            SalahPosture.SUJUD -> "Go fully into prostration"
            SalahPosture.JALSA -> "Sit upright between prostrations"
            SalahPosture.TASHAHHUD -> "Sit still in the final position"
            else -> "Adjust your position"
        }
    }

    // Everything is good
    return when (posture) {
        SalahPosture.QIYAM -> "Standing position looks great"
        SalahPosture.RUKU -> "Bowing position looks great"
        SalahPosture.GOING_TO_SUJUD -> "Keep moving naturally"
        SalahPosture.SUJUD -> "Prostration looks great"
        SalahPosture.JALSA -> "Sitting position looks great"
        SalahPosture.TASHAHHUD -> "Final sitting looks great"
        SalahPosture.QIYAM_RISING -> "Keep rising smoothly"
        SalahPosture.RISING_TO_QIYAM -> "Rise naturally and stop fully upright"
        else -> "Data is being captured"
    }
}
