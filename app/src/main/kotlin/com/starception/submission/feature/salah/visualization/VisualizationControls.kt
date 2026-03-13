package com.starception.submission.feature.salah.visualization

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.ml.SalahPosture

/**
 * Material 3 controls panel for 3D visualization of prayer posture data.
 * Provides mode selection, posture filtering, playback controls, and axis mapping.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualizationControls(
    state: VisualizationState,
    onStateChange: (VisualizationState) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section 1: Mode Toggle Row
        Text(
            text = "Visualization Mode",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeButton(
                text = "Scatter",
                icon = Icons.Default.Grain,
                selected = state.mode == VisualizationMode.SCATTER,
                onClick = { onStateChange(state.copy(mode = VisualizationMode.SCATTER)) },
                modifier = Modifier.weight(1f)
            )
            ModeButton(
                text = "Phone",
                icon = Icons.Default.PhoneAndroid,
                selected = state.mode == VisualizationMode.PHONE_MODEL,
                onClick = { onStateChange(state.copy(mode = VisualizationMode.PHONE_MODEL)) },
                modifier = Modifier.weight(1f)
            )
            ModeButton(
                text = "Gravity",
                icon = Icons.Default.Public,
                selected = state.mode == VisualizationMode.GRAVITY_VECTOR,
                onClick = { onStateChange(state.copy(mode = VisualizationMode.GRAVITY_VECTOR)) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Section 2: Posture Filters
        Text(
            text = "Visible Postures",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SalahPosture.classificationLabels.forEach { posture ->
                val isSelected = state.visiblePostures.contains(posture)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val newPostures = if (isSelected) {
                            state.visiblePostures - posture
                        } else {
                            state.visiblePostures + posture
                        }
                        onStateChange(state.copy(visiblePostures = newPostures))
                    },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = getPostureColor(posture),
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = posture.displayName,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Section 3: Playback Controls
        if (state.totalSamples > 0) {
            Text(
                text = "Playback",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Play/Pause
                IconButton(
                    onClick = { onStateChange(state.copy(isPlaying = !state.isPlaying)) }
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Skip Previous
                IconButton(
                    onClick = {
                        val newIndex = (state.playbackIndex - 1).coerceAtLeast(0)
                        onStateChange(state.copy(playbackIndex = newIndex, isPlaying = false))
                    },
                    enabled = state.playbackIndex > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Sample"
                    )
                }

                // Skip Next
                IconButton(
                    onClick = {
                        val newIndex = (state.playbackIndex + 1).coerceAtMost(state.totalSamples - 1)
                        onStateChange(state.copy(playbackIndex = newIndex, isPlaying = false))
                    },
                    enabled = state.playbackIndex < state.totalSamples - 1
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Sample"
                    )
                }

                // Sample counter
                Text(
                    text = "Sample ${state.playbackIndex + 1} / ${state.totalSamples}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }

            // Speed slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Speed: ${state.playbackSpeed.toInt()}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = state.playbackSpeed,
                    onValueChange = { onStateChange(state.copy(playbackSpeed = it)) },
                    valueRange = 1f..50f,
                    steps = 48
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section 4: Current Sample Info
            Text(
                text = "Current Sample",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Posture name
                state.currentPosture?.let { posture ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = getPostureColor(posture),
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = posture.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = posture.arabicName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Sensor values
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Pitch: ${"%.1f".format(state.currentPitch)}°",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Roll: ${"%.1f".format(state.currentRoll)}°",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Accel: ${"%.2f".format(state.currentAccelMag)} m/s²",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Gyro: ${"%.3f".format(state.currentGyroMag)} rad/s",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Section 5: Scatter Options (only when mode == SCATTER)
        if (state.mode == VisualizationMode.SCATTER) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Scatter Plot Axes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Axis dropdowns
            AxisSelector(
                label = "X-Axis",
                selectedAxis = state.axisX,
                onAxisSelected = { onStateChange(state.copy(axisX = it)) }
            )
            AxisSelector(
                label = "Y-Axis",
                selectedAxis = state.axisY,
                onAxisSelected = { onStateChange(state.copy(axisY = it)) }
            )
            AxisSelector(
                label = "Z-Axis",
                selectedAxis = state.axisZ,
                onAxisSelected = { onStateChange(state.copy(axisZ = it)) }
            )

            // Point size slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Point Size: ${state.pointSize.toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = state.pointSize,
                    onValueChange = { onStateChange(state.copy(pointSize = it)) },
                    valueRange = 1f..10f,
                    steps = 8
                )
            }
        }
    }
}

@Composable
private fun ModeButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun AxisSelector(
    label: String,
    selectedAxis: String,
    onAxisSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val axisOptions = mapOf(
        "pitch" to "Pitch",
        "roll" to "Roll",
        "ax" to "Accel X",
        "ay" to "Accel Y",
        "az" to "Accel Z",
        "am" to "Accel Mag",
        "gx" to "Gyro X",
        "gy" to "Gyro Y",
        "gz" to "Gyro Z",
        "gm" to "Gyro Mag"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = axisOptions[selectedAxis] ?: selectedAxis,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
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
}

/**
 * Returns the Material 3 color associated with each prayer posture.
 */
fun getPostureColor(posture: SalahPosture): Color {
    return when (posture) {
        SalahPosture.QIYAM -> Color(0xFF00BFFF)
        SalahPosture.QIYAM_RISING -> Color(0xFF00CED1)
        SalahPosture.RUKU -> Color(0xFFFF8C00)
        SalahPosture.GOING_TO_SUJUD -> Color(0xFFFF1493)
        SalahPosture.SUJUD -> Color(0xFF32CD32)
        SalahPosture.JALSA -> Color(0xFF9370DB)
        SalahPosture.TASHAHHUD -> Color(0xFFFF4500)
        SalahPosture.TRANSITION -> Color.Gray
        SalahPosture.NOT_PRAYING -> Color.LightGray
    }
}
