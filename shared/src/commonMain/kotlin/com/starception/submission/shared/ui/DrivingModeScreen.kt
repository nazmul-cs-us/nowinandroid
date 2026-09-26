/*
 * Copyright 2026 The Android Open Source Project
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.shared.travel.DrivingModeCoordinator

/**
 * Driving mode screen, mirroring the Android driving-mode chain:
 * Travel Dua -> daily hadith narration -> Quran surah.
 *
 * Auto-detection is driven by speed evidence supplied by the host
 * ([DrivingModeCoordinator.updateSpeed]); this screen offers manual control
 * and a live stage indicator.
 */
@Composable
internal fun DrivingModeScreen(
    coordinator: DrivingModeCoordinator,
    onBack: () -> Unit,
) {
    var stage by remember { mutableStateOf(DrivingModeCoordinator.Stage.IDLE) }
    DisposableEffect(coordinator) {
        val previous = coordinator.onStageChanged
        coordinator.onStageChanged = { stage = it }
        onDispose {
            coordinator.onStageChanged = previous
            coordinator.stop()
        }
    }

    SharedDetailScaffold(title = "Driving mode", onBack = onBack, maxContentWidth = 680.dp) {
        Card(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Journey companion",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "When driving is detected, the app plays the Travel Dua, " +
                        "continues with the daily hadith narration, and then recites a surah.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StageRow("Travel Dua", stage, DrivingModeCoordinator.Stage.DUA)
                StageRow("Daily hadith", stage, DrivingModeCoordinator.Stage.HADITH)
                StageRow("Quran recitation", stage, DrivingModeCoordinator.Stage.QURAN)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { coordinator.start() }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Start chain")
                    }
                    OutlinedButton(onClick = { coordinator.stop() }) {
                        Icon(Icons.Filled.Pause, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Stop")
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Speed evidence for automatic detection is provided by the host's " +
                "location updates; the shared trigger policy decides when the chain plays.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StageRow(label: String, current: DrivingModeCoordinator.Stage, stage: DrivingModeCoordinator.Stage) {
    val active = current == stage
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (active) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Box(Modifier.size(8.dp))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (active) {
        Text(
            "Playing…",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Transparent,
        )
    }
}
