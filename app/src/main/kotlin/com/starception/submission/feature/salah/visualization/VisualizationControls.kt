package com.starception.submission.feature.salah.visualization

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.ml.SalahPosture
import com.starception.submission.core.designsystem.component.NiaOutlinedButton

/**
 * Professional Material 3 controls panel for 3D visualization.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualizationControls(
    state: VisualizationState,
    onStateChange: (VisualizationState) -> Unit,
    modifier: Modifier = Modifier,
    onAnalyzePredictions: () -> Unit = {}
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Mode Selector ──────────────────────────────
        SectionHeader("Mode")
        SegmentedModeSelector(
            currentMode = state.mode,
            onModeSelected = { onStateChange(state.copy(mode = it)) }
        )

        // ── Model Diagnostics ──────────────────────────
        if (state.mode == VisualizationMode.SCATTER || state.mode == VisualizationMode.FEATURE_PCA) {
            SectionHeader("Diagnostics")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (state.predictions == null) {
                    FilterChip(
                        selected = false,
                        enabled = !state.isAnalyzingPredictions,
                        onClick = onAnalyzePredictions,
                        label = {
                            Text(if (state.isAnalyzingPredictions) "Analyzing…" else "Run model analysis")
                        }
                    )
                } else {
                    FilterChip(
                        selected = state.showDisagreements,
                        onClick = { onStateChange(state.copy(showDisagreements = !state.showDisagreements)) },
                        label = { Text("Disagreements (${state.flaggedIndices.size})") }
                    )
                }
                FilterChip(
                    selected = state.showEllipsoids,
                    onClick = { onStateChange(state.copy(showEllipsoids = !state.showEllipsoids)) },
                    label = { Text("Class spread") }
                )
            }
            if (state.mode == VisualizationMode.FEATURE_PCA) {
                Text(
                    text = when {
                        state.isComputingPca -> "Projecting 30-D features…"
                        state.pcaVariance != null ->
                            "PCA view · ${(state.pcaVariance * 100).toInt()}% of variance in 3 axes"
                        else -> "PCA projection not computed yet"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Posture Filters ────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader("Postures")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickActionButton(
                    label = "All",
                    onClick = { onStateChange(state.copy(visiblePostures = SalahPosture.classificationLabels.toSet())) }
                )
                QuickActionButton(
                    label = "None",
                    onClick = { onStateChange(state.copy(visiblePostures = emptySet<SalahPosture>())) }
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SalahPosture.classificationLabels.forEach { posture ->
                val isSelected = state.visiblePostures.contains(posture)
                PostureFilterChip(
                    posture = posture,
                    isSelected = isSelected,
                    onClick = {
                        val newPostures = if (isSelected) {
                            state.visiblePostures - posture
                        } else {
                            state.visiblePostures + posture
                        }
                        onStateChange(state.copy(visiblePostures = newPostures))
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            FilledIconButton(
                onClick = { onStateChange(state.copy(cameraResetToken = state.cameraResetToken + 1)) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "Reset camera"
                )
            }
        }

        // ── Scatter Options ────────────────────────────
        if (state.mode == VisualizationMode.SCATTER) {
            SectionHeader("Axes")
            ScatterAxisRow(state, onStateChange)

            // Point size
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Size",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(32.dp)
                )
                Slider(
                    value = state.pointSize,
                    onValueChange = { onStateChange(state.copy(pointSize = it)) },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = "${state.pointSize.toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(20.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    onClick: () -> Unit
) {
    NiaOutlinedButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}


// ═══════════════════════════════════════════════════════
// Section Header
// ═══════════════════════════════════════════════════════

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.2.sp
    )
}

// ═══════════════════════════════════════════════════════
// Segmented Mode Selector
// ═══════════════════════════════════════════════════════

@Composable
private fun SegmentedModeSelector(
    currentMode: VisualizationMode,
    onModeSelected: (VisualizationMode) -> Unit
) {
    val modes = listOf(
        Triple(VisualizationMode.SCATTER, "Scatter", Icons.Default.Grain),
        Triple(VisualizationMode.PHONE_MODEL, "Pose", Icons.Default.AccessibilityNew),
        Triple(VisualizationMode.GRAVITY_VECTOR, "Gravity", Icons.Default.Public),
        Triple(VisualizationMode.FEATURE_PCA, "PCA", Icons.Default.BubbleChart)
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            modes.forEach { (mode, label, icon) ->
                val isSelected = currentMode == mode
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    label = "mode_bg"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    label = "mode_content"
                )

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onModeSelected(mode) },
                    shape = RoundedCornerShape(12.dp),
                    color = bgColor,
                    shadowElevation = if (isSelected) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Posture Filter Chip
// ═══════════════════════════════════════════════════════

@Composable
private fun PostureFilterChip(
    posture: SalahPosture,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val postureColor = getPostureColor(posture)
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) postureColor.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "chip_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) postureColor.copy(alpha = 0.6f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "chip_border"
    )

    Surface(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (isSelected) postureColor else postureColor.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            )
            Text(
                text = posture.displayName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// Playback Bar
// ═══════════════════════════════════════════════════════

@Composable
fun PlaybackBar(
    state: VisualizationState,
    onStateChange: (VisualizationState) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Skip Previous
                IconButton(
                    onClick = {
                        val newIndex = (state.playbackIndex - 1).coerceAtLeast(0)
                        onStateChange(state.copy(playbackIndex = newIndex, isPlaying = false))
                    },
                    enabled = state.playbackIndex > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Play/Pause (prominent)
                FilledIconButton(
                    onClick = { onStateChange(state.copy(isPlaying = !state.isPlaying)) },
                    modifier = Modifier.size(42.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Skip Next
                IconButton(
                    onClick = {
                        val newIndex = (state.playbackIndex + 1).coerceAtMost(state.totalSamples - 1)
                        onStateChange(state.copy(playbackIndex = newIndex, isPlaying = false))
                    },
                    enabled = state.playbackIndex < state.totalSamples - 1,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Sample counter
                Text(
                    text = "${state.playbackIndex + 1} / ${state.totalSamples}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Progress bar
            val progress = if (state.totalSamples > 1) {
                state.playbackIndex.toFloat() / (state.totalSamples - 1)
            } else 0f
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(200, easing = FastOutSlowInEasing),
                label = "progress"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }

            // Speed control
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Slider(
                    value = state.playbackSpeed,
                    onValueChange = { onStateChange(state.copy(playbackSpeed = it)) },
                    valueRange = 0.5f..10f,
                    steps = 18,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = if (state.playbackSpeed % 1f == 0f) {
                            "${state.playbackSpeed.toInt()}x"
                        } else {
                            "${"%.1f".format(state.playbackSpeed)}x"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Current Sample Card
// ═══════════════════════════════════════════════════════

@Composable
fun CurrentSampleCard(state: VisualizationState) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Posture name with color indicator
            state.currentPosture?.let { posture ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = getPostureColor(posture),
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = posture.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (posture.arabicName.isNotEmpty()) {
                        Text(
                            text = posture.arabicName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Model prediction vs label at the current playback window — red when
            // they disagree, so scrubbing a flagged run shows instantly whether the
            // label or the model is wrong.
            val prediction = state.predictions?.getOrNull(state.playbackIndex)
            if (prediction != null && state.currentPosture != null) {
                val predicted = prediction.predicted
                val agrees = predicted == state.currentPosture
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (agrees) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    ) {
                        Text(
                            text = when {
                                predicted == null -> "Model: —"
                                else -> "Model: ${predicted.displayName} ${(prediction.confidence * 100).toInt()}%"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = if (agrees) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                    if (!agrees && predicted != null) {
                        Text(
                            text = "≠ label",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Sensor values in a clean grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SensorReadout("Pitch", "${"%.1f".format(state.currentPitch)}\u00B0")
                SensorReadout("Roll", "${"%.1f".format(state.currentRoll)}\u00B0")
                SensorReadout("Accel", "${"%.2f".format(state.currentAccelMag)}")
                SensorReadout("Gyro", "${"%.3f".format(state.currentGyroMag)}")
            }
        }
    }
}

@Composable
private fun SensorReadout(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

// ═══════════════════════════════════════════════════════
// Scatter Axis Row (compact inline)
// ═══════════════════════════════════════════════════════

@Composable
private fun ScatterAxisRow(
    state: VisualizationState,
    onStateChange: (VisualizationState) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactAxisSelector(
            label = "X",
            selectedAxis = state.axisX,
            labelColor = Color(0xFFEF5350),
            onAxisSelected = { onStateChange(state.copy(axisX = it)) },
            modifier = Modifier.weight(1f)
        )
        CompactAxisSelector(
            label = "Y",
            selectedAxis = state.axisY,
            labelColor = Color(0xFF66BB6A),
            onAxisSelected = { onStateChange(state.copy(axisY = it)) },
            modifier = Modifier.weight(1f)
        )
        CompactAxisSelector(
            label = "Z",
            selectedAxis = state.axisZ,
            labelColor = Color(0xFF42A5F5),
            onAxisSelected = { onStateChange(state.copy(axisZ = it)) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CompactAxisSelector(
    label: String,
    selectedAxis: String,
    labelColor: Color,
    onAxisSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val axisOptions = mapOf(
        "pitch" to "Pitch",
        "roll" to "Roll",
        "ax" to "Accel X",
        "ay" to "Accel Y",
        "az" to "Accel Z",
        "am" to "Accel M",
        "gx" to "Gyro X",
        "gy" to "Gyro Y",
        "gz" to "Gyro Z",
        "gm" to "Gyro M"
    )

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Colored axis label
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = labelColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = labelColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Text(
                    text = axisOptions[selectedAxis] ?: selectedAxis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            axisOptions.forEach { (key, displayName) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = {
                        onAxisSelected(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Posture Color Map
// ═══════════════════════════════════════════════════════

fun getPostureColor(posture: SalahPosture): Color {
    return when (posture) {
        SalahPosture.QIYAM -> Color(0xFF00BFFF)
        SalahPosture.QIYAM_RISING -> Color(0xFF00CED1)
        SalahPosture.RUKU -> Color(0xFFFF8C00)
        SalahPosture.GOING_TO_SUJUD -> Color(0xFFFF1493)
        SalahPosture.SUJUD -> Color(0xFF32CD32)
        SalahPosture.JALSA -> Color(0xFF9370DB)
        SalahPosture.TASHAHHUD -> Color(0xFFFF4500)
        else -> Color.Gray
    }
}

// ═══════════════════════════════════════════════════════
// Data Quality Summary
// ═══════════════════════════════════════════════════════

@Composable
fun DataQualitySummary(
    samples: List<com.starception.submission.ml.SalahDataSample>,
    modifier: Modifier = Modifier
) {
    if (samples.isEmpty()) return

    val postureCounts = remember(samples) {
        val counts = mutableMapOf<SalahPosture, Int>()
        SalahPosture.classificationLabels.forEach { counts[it] = 0 }
        samples.forEach { sample ->
            if (sample.posture in SalahPosture.classificationLabels) {
                counts[sample.posture] = (counts[sample.posture] ?: 0) + 1
            }
        }
        counts.toList().sortedByDescending { it.second }
    }

    val maxCount = remember(postureCounts) { postureCounts.maxOfOrNull { it.second } ?: 1 }
    val minCount = remember(postureCounts) {
        postureCounts.filter { it.second > 0 }.minOfOrNull { it.second } ?: 0
    }
    val totalClassification = remember(postureCounts) { postureCounts.sumOf { it.second } }
    val sessionCount = remember(samples) { samples.map { it.sessionId }.distinct().size }
    val balanceRatio = remember(minCount, maxCount) {
        if (maxCount > 0) (minCount.toFloat() / maxCount * 100).toInt() else 0
    }
    val emptyClasses = remember(postureCounts) { postureCounts.count { it.second == 0 } }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header with quality badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DATA QUALITY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.2.sp
                )
                val (badgeText, badgeColor) = when {
                    emptyClasses > 0 -> "Missing classes" to Color(0xFFE53935)
                    balanceRatio < 30 -> "Imbalanced" to Color(0xFFFF8C00)
                    balanceRatio < 60 -> "Fair" to Color(0xFFFFB300)
                    else -> "Good" to Color(0xFF43A047)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            // Per-posture distribution bars
            postureCounts.forEach { (posture, count) ->
                val animatedFraction by animateFloatAsState(
                    targetValue = if (maxCount > 0) count.toFloat() / maxCount else 0f,
                    animationSpec = tween(600, easing = FastOutSlowInEasing),
                    label = "bar_${posture.name}"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Color dot + name
                    Row(
                        modifier = Modifier.width(90.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = getPostureColor(posture),
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = posture.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedFraction)
                                .background(
                                    color = getPostureColor(posture),
                                    shape = RoundedCornerShape(5.dp)
                                )
                        )
                    }

                    // Count
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (count == 0) Color(0xFFE53935)
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End
                    )
                }
            }

            // Summary stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryStatChip("Balance", "${balanceRatio}%")
                SummaryStatChip(
                    "Sessions",
                    "$sessionCount"
                )
                SummaryStatChip("Samples", "$totalClassification")
            }
        }
    }
}

@Composable
private fun SummaryStatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}
