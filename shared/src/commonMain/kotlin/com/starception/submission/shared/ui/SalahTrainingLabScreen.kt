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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.starception.submission.shared.content.SharedContentStore
import com.starception.submission.shared.ml.SalahPosture
import com.starception.submission.shared.ml.SalahSensorRecorder
import kotlinx.coroutines.delay
import kotlin.time.Clock

/** One guided recording pass, in seconds, matching Android's collection flow. */
private const val RECORDING_SECONDS = 15

/**
 * The Salah Training Lab — the shared counterpart of Android's
 * SalahDataCollectionScreen: guided 15-second posture recordings through the
 * 50Hz [SalahSensorRecorder], live window counters, and per-posture sample
 * totals persisted through the store.
 */
@Composable
internal fun SalahTrainingLabScreen(
    store: SharedContentStore,
    onBack: () -> Unit,
) {
    val recorder = remember { SalahSensorRecorder() }
    val mlPostures = remember {
        listOf(
            SalahPosture.QIYAM,
            SalahPosture.RUKU,
            SalahPosture.GOING_TO_SUJUD,
            SalahPosture.SUJUD,
            SalahPosture.JALSA,
            SalahPosture.TASHAHHUD,
            SalahPosture.QIYAM_RISING,
            SalahPosture.RISING_TO_QIYAM,
        )
    }
    var counts by remember { mutableStateOf(store.salahSampleCounts()) }
    var recordingPosture by remember { mutableStateOf<SalahPosture?>(null) }
    var countdown by remember { mutableIntStateOf(0) }
    var windows by remember { mutableIntStateOf(0) }
    var sensorsUnavailable by remember { mutableStateOf(false) }

    // The recording engine: 3-2-1 countdown, then stream windows for
    // RECORDING_SECONDS, persisting the labeled count at the end.
    LaunchedEffect(recordingPosture) {
        val posture = recordingPosture ?: return@LaunchedEffect
        windows = 0
        val started = recorder.start("training-${Clock.System.now().toEpochMilliseconds()}") { sample ->
            windows += 1
            sample.hashCode() // keep the sample alive for the counter
        }
        if (!started) {
            sensorsUnavailable = true
            recordingPosture = null
            return@LaunchedEffect
        }
        for (tick in 3 downTo 1) {
            countdown = tick
            delay(1000)
        }
        countdown = 0
        val start = Clock.System.now().toEpochMilliseconds()
        while (Clock.System.now().toEpochMilliseconds() - start < RECORDING_SECONDS * 1000) {
            delay(100)
        }
        recorder.stop()
        if (windows > 0) {
            counts = store.addSalahSamples(posture, windows)
        }
        recordingPosture = null
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { recorder.stop() }
    }

    SharedDetailScaffold(title = "Salah Training Lab", onBack = onBack) {
        if (sensorsUnavailable) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Motion sensors are unavailable on this device, so posture recordings can't be captured here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        Text(
            "Hold the phone in your pocket and hold each posture for the full ${RECORDING_SECONDS}s pass. Each pass adds ~150 labeled windows to the on-device model's training set.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        val activePosture = recordingPosture
        if (activePosture != null) {
            RecordingPanel(
                posture = activePosture,
                countdown = countdown,
                windows = windows,
                totalSeconds = RECORDING_SECONDS,
                onCancel = {
                    recorder.stop()
                    recordingPosture = null
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(mlPostures, key = { it.name }) { posture ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                posture.displayName,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                posture.arabicName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            "${counts[posture.name] ?: 0} windows",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Button(
                            onClick = { recordingPosture = posture },
                            enabled = activePosture == null,
                        ) {
                            Text("Record")
                        }
                    }
                }
            }
            item {
                val total = counts.values.sum()
                Text(
                    "Collected: $total windows total. Samples feed the on-device posture model; they never leave the device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (total > 0) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            counts = store.clearSalahSamples()
                        },
                    ) {
                        Text("Clear training data")
                    }
                }
            }
        }
    }
}

/** The live pass card: countdown, then a window counter + progress sweep. */
@Composable
private fun RecordingPanel(
    posture: SalahPosture,
    countdown: Int,
    windows: Int,
    totalSeconds: Int,
    onCancel: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (countdown > 0) {
                Box(
                    modifier = Modifier.size(96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { 1f - (countdown - 1) / 3f },
                        modifier = Modifier.size(96.dp),
                        strokeWidth = 6.dp,
                    )
                    Text(
                        "$countdown",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Get into ${posture.displayName} — recording starts soon",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            } else {
                val progress by animateFloatAsState(
                    targetValue = (windows % (totalSeconds * 10)) / (totalSeconds * 10f),
                    animationSpec = tween(100),
                    label = "recordingProgress",
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Recording ${posture.displayName} — $windows windows",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Hold the posture steady until the pass completes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}
