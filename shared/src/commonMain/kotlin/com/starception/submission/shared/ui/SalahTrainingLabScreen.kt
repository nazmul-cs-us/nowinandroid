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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.starception.submission.shared.ml.SalahPosture
import com.starception.submission.core.designsystem.component.NiaButton
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import com.starception.submission.shared.ml.SalahRecordingStore
import com.starception.submission.shared.ml.SalahSensorRecorder
import com.starception.submission.shared.ml.SalahSessionInfo
import com.starception.submission.shared.voice.PlatformSpeechSynthesizer
import kotlin.time.Clock

/** Android's constants: 5s countdown, 3s trailing trim, 5s transition capture. */
private const val COUNTDOWN_SECONDS = 5
private const val TRIM_LAST_MS = 3000L
private const val TRANSITION_DURATION_SECONDS = 5

/**
 * One guided step — Android's GuidedStep: a static posture held for the
 * selected duration, or a transition captured over a short fixed window
 * with a spoken movement cue at the instant capture begins.
 */
private data class GuidedStep(
    val posture: SalahPosture,
    val isTransition: Boolean,
    val instruction: String,
    val recordingLabel: String = posture.displayName,
    val movementCue: String? = null,
)

/**
 * Android's GUIDED_POSTURE_SEQUENCE: a rak'ah that continues into the next,
 * so every model class is represented in each complete session.
 */
private val GUIDED_POSTURE_SEQUENCE = listOf(
    GuidedStep(
        SalahPosture.QIYAM,
        isTransition = false,
        instruction = "Stand upright in the prayer position, with your hands folded. Become still. Keep holding until the next instruction.",
    ),
    GuidedStep(
        SalahPosture.RUKU,
        isTransition = false,
        instruction = "Now bow into ruku, with your hands on your knees. Become still. Keep holding until the next instruction.",
    ),
    GuidedStep(
        SalahPosture.QIYAM_RISING,
        isTransition = true,
        instruction = "Stay in ruku. Do not move yet. When you hear move now, rise from ruku until you are fully upright. Stop in standing, and do not start going down.",
        recordingLabel = "Ruku → Standing",
        movementCue = "Move now. Rise from ruku until you are fully upright, then stop.",
    ),
    GuidedStep(
        SalahPosture.GOING_TO_SUJUD,
        isTransition = true,
        instruction = "You should now be fully upright after ruku. Stay standing and do not move yet. When you hear move now, lower from standing into the first prostration.",
        recordingLabel = "Standing → First Sujud",
        movementCue = "Move now. From standing, lower smoothly into the first prostration.",
    ),
    GuidedStep(
        SalahPosture.SUJUD,
        isTransition = false,
        instruction = "Remain in the first prostration, or sujud. Become still. Keep holding until the next instruction.",
    ),
    GuidedStep(
        SalahPosture.JALSA,
        isTransition = false,
        instruction = "Now sit up into the seated position between the two prostrations. Become still. Keep holding until the next instruction.",
    ),
    GuidedStep(
        SalahPosture.GOING_TO_SUJUD,
        isTransition = true,
        instruction = "You should now be seated between the two prostrations. Stay seated and do not move yet. When you hear move now, lower from sitting into the second prostration.",
        recordingLabel = "Sitting → Second Sujud",
        movementCue = "Move now. From sitting, lower smoothly into the second prostration.",
    ),
    GuidedStep(
        SalahPosture.SUJUD,
        isTransition = false,
        instruction = "Remain in the second prostration, or sujud. Become still. Keep holding until the next instruction.",
    ),
    GuidedStep(
        SalahPosture.TASHAHHUD,
        isTransition = false,
        instruction = "Now sit up into the seated position for tashahhud. Become still. Keep holding until the next instruction.",
    ),
    GuidedStep(
        SalahPosture.RISING_TO_QIYAM,
        isTransition = true,
        instruction = "Remain seated after tashahhud and do not move yet. When you hear move now, rise naturally into the next rak'ah and stop fully upright.",
        recordingLabel = "Tashahhud → Next Rak'ah",
        movementCue = "Move now. Rise naturally into the next rak'ah and stop fully upright.",
    ),
)

/** Android's GuidedRecordingState. */
private enum class GuidedState { IDLE, COUNTDOWN, RECORDING, COMPLETED }

/**
 * The Salah Training Lab — the shared counterpart of Android's
 * SalahDataCollectionScreen: a voice-guided pass through the rak'ah
 * sequence whose labeled 50Hz windows persist as JSONL session files
 * (auto-trimmed, descriptively named), with a session browser and
 * per-posture totals.
 */
@Composable
internal fun SalahTrainingLabScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit = {},
    onOpenPrayerSimulation: () -> Unit = {},
) {
    val recorder = remember { SalahSensorRecorder() }
    val store = remember { SalahRecordingStore() }
    val synthesizer = remember { PlatformSpeechSynthesizer() }

    var guidedState by remember { mutableStateOf(GuidedState.IDLE) }
    var stepIndex by remember { mutableIntStateOf(0) }
    var focusedPosture by remember { mutableStateOf<SalahPosture?>(null) }
    var selectedDuration by remember { mutableIntStateOf(15) }
    var countdown by remember { mutableIntStateOf(0) }
    var stepSecondsLeft by remember { mutableIntStateOf(0) }
    var stepSecondsTotal by remember { mutableIntStateOf(0) }
    var windows by remember { mutableIntStateOf(0) }
    var sessionWindows by remember { mutableIntStateOf(0) }
    var sensorsUnavailable by remember { mutableStateOf(false) }
    var sessions by remember { mutableStateOf(store.sessions()) }

    val steps = remember(focusedPosture) {
        focusedPosture?.let { posture ->
            listOf(
                GuidedStep(
                    posture,
                    isTransition = false,
                    instruction = "Get into ${posture.displayName}. Become still and hold until the pass completes.",
                ),
            )
        } ?: GUIDED_POSTURE_SEQUENCE
    }

    fun refreshSessions() {
        sessions = store.sessions()
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            recorder.stop()
            synthesizer.stop()
        }
    }

    // The guided engine: countdown → per-step capture → advance → complete.
    LaunchedEffect(guidedState, stepIndex) {
        when (guidedState) {
            GuidedState.IDLE -> Unit
            GuidedState.COUNTDOWN -> {
                val step = steps[stepIndex]
                synthesizer.speak(text = step.instruction)
                for (tick in COUNTDOWN_SECONDS downTo 1) {
                    countdown = tick
                    kotlinx.coroutines.delay(1000)
                }
                countdown = 0
                guidedState = GuidedState.RECORDING
            }
            GuidedState.RECORDING -> {
                val step = steps[stepIndex]
                if (step.isTransition) {
                    synthesizer.speak(text = step.movementCue ?: "Move now.")
                }
                val duration = if (step.isTransition) {
                    TRANSITION_DURATION_SECONDS
                } else {
                    selectedDuration
                }
                stepSecondsTotal = duration
                sessionWindows = 0
                val sessionId = Clock.System.now().toEpochMilliseconds().toString(16).takeLast(8)
                val sessionStart = store.startSession(prefix = "salah_guided_", sessionId = sessionId)
                val started = if (sessionStart != null) {
                    recorder.start(sessionId) { sample ->
                        store.appendSample(sample.copy(posture = step.posture))
                        sessionWindows += 1
                    }
                } else {
                    false
                }
                if (!started) {
                    sensorsUnavailable = true
                    recorder.stop()
                    guidedState = GuidedState.IDLE
                    return@LaunchedEffect
                }
                val start = Clock.System.now().toEpochMilliseconds()
                while (true) {
                    val elapsed = ((Clock.System.now().toEpochMilliseconds() - start) / 1000).toInt()
                    stepSecondsLeft = (duration - elapsed).coerceAtLeast(0)
                    windows = sessionWindows
                    if (elapsed >= duration) break
                    kotlinx.coroutines.delay(200)
                }
                recorder.stop()
                store.stopSession(trimLastMs = TRIM_LAST_MS)
                refreshSessions()
                if (stepIndex + 1 < steps.size) {
                    stepIndex += 1
                    guidedState = GuidedState.COUNTDOWN
                } else {
                    synthesizer.speak(text = "Recording complete.")
                    guidedState = GuidedState.COMPLETED
                }
            }
            GuidedState.COMPLETED -> Unit
        }
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

        when (guidedState) {
            GuidedState.IDLE, GuidedState.COMPLETED -> {
                if (guidedState == GuidedState.COMPLETED) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "Session saved",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "$sessionWindows labeled windows across ${steps.size} steps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.height(12.dp))
                            NiaOutlinedButton(
                                onClick = {
                                    guidedState = GuidedState.IDLE
                                    stepIndex = 0
                                },
                                text = { Text("Record another session") },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                NiaOutlinedButton(
                    onClick = onOpenPrayerSimulation,
                    text = { Text("Prayer simulation in 3-D") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                )
                Spacer(Modifier.height(8.dp))
                GuidedSetupPanel(
                    steps = steps,
                    selectedDuration = selectedDuration,
                    onDurationChange = { selectedDuration = it },
                    focusedMode = focusedPosture != null,
                    onFocusPosture = { focusedPosture = it },
                    onClearFocus = { focusedPosture = null },
                    onStart = {
                        stepIndex = 0
                        guidedState = GuidedState.COUNTDOWN
                    },
                )
                Spacer(Modifier.height(14.dp))
                SessionBrowser(
                    sessions = sessions,
                    onOpen = onOpenSession,
                    onDelete = { name ->
                        store.deleteSession(name)
                        refreshSessions()
                    },
                    onDeleteAll = {
                        store.deleteAllSessions()
                        refreshSessions()
                    },
                )
            }
            GuidedState.COUNTDOWN -> {
                val step = steps[stepIndex]
                GuidedLivePanel(
                    headline = "Step ${stepIndex + 1} of ${steps.size}",
                    label = step.recordingLabel,
                    body = step.instruction,
                    countdown = countdown,
                    progress = null,
                    onCancel = {
                        synthesizer.stop()
                        guidedState = GuidedState.IDLE
                    },
                )
            }
            GuidedState.RECORDING -> {
                val step = steps[stepIndex]
                GuidedLivePanel(
                    headline = "Step ${stepIndex + 1} of ${steps.size}",
                    label = step.recordingLabel,
                    body = "Recording — hold the posture steady",
                    countdown = null,
                    progress = stepSecondsLeft.toFloat() / stepSecondsTotal.toFloat(),
                    windows = windows,
                    onCancel = {
                        recorder.stop()
                        store.stopSession(trimLastMs = TRIM_LAST_MS)
                        refreshSessions()
                        synthesizer.stop()
                        guidedState = GuidedState.IDLE
                    },
                )
            }
        }
    }
}

/** Idle controls: duration chips, the mode toggle, and the start button. */
@Composable
private fun GuidedSetupPanel(
    steps: List<GuidedStep>,
    selectedDuration: Int,
    onDurationChange: (Int) -> Unit,
    focusedMode: Boolean,
    onFocusPosture: (SalahPosture) -> Unit,
    onClearFocus: () -> Unit,
    onStart: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (focusedMode) {
                    "Focused recording: one posture, your selected duration."
                } else {
                    "Guided pass: ${steps.size} steps through a full rak'ah. Hold each posture still; move only when told."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 15, 20).forEach { seconds ->
                    FilterChip(
                        selected = selectedDuration == seconds,
                        onClick = { onDurationChange(seconds) },
                        label = { Text("${seconds}s") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Focus on one posture",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SalahPosture.prayerPostures.take(4).forEach { posture ->
                    FilterChip(
                        selected = focusedMode && steps.firstOrNull()?.posture == posture,
                        onClick = {
                            if (focusedMode && steps.firstOrNull()?.posture == posture) {
                                onClearFocus()
                            } else {
                                onFocusPosture(posture)
                            }
                        },
                        label = { Text(posture.displayName, maxLines = 1) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            NiaButton(
                onClick = onStart,
                text = {
                    Text(if (focusedMode) "Start focused recording" else "Start guided pass")
                },
                leadingIcon = {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            )
        }
    }
}

/** The live recording card: countdown ring or progress sweep + cancel. */
@Composable
private fun GuidedLivePanel(
    headline: String,
    label: String,
    body: String,
    countdown: Int?,
    progress: Float?,
    windows: Int = 0,
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
            Text(
                headline,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            if (countdown != null) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.height(96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { countdown / COUNTDOWN_SECONDS.toFloat() },
                        modifier = Modifier.height(96.dp).width(96.dp),
                        strokeWidth = 6.dp,
                    )
                    Text(
                        "$countdown",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            } else if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "$windows windows · ${((progress * 100).toInt())}% complete",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            NiaOutlinedButton(
                onClick = onCancel,
                text = { Text("Stop and save what's recorded") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Saved session files: counts, sizes, posture badges, per-file delete. */
@Composable
private fun SessionBrowser(
    sessions: List<SalahSessionInfo>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
) {
    Text(
        "Recorded sessions (${sessions.size} · ${sessions.sumOf { it.sizeKb }} KB)",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    if (sessions.isEmpty()) {
        Text(
            "No sessions yet — a guided pass records labeled sensor windows that stay on this device and feed the on-device posture model.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.heightIn(max = 320.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sessions, key = { it.fileName }) { session ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                onClick = { onOpen(session.fileName) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            session.fileName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            "${session.sampleCount} windows · ${session.sizeKb} KB · ${session.postureCounts.size} postures",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDelete(session.fileName) }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete session",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            NiaOutlinedButton(
                onClick = onDeleteAll,
                text = { Text("Delete all recordings") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
