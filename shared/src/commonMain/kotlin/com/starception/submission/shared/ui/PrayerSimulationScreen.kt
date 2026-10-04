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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.shared.ml.SalahPosture
import com.starception.submission.shared.content.createSharedFortressRepository
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** One pose of the guided prayer — Android's TwoRakahStep. */
internal data class PrayerSimStep(
    val rakah: Int,
    val posture: SalahPosture,
    val label: String,
    val durationMillis: Long,
    /** Fortress of the Muslim chapter whose dua belongs to this phase. */
    val fortressChapterId: Int? = null,
    val guidance: String? = null,
)

/**
 * Builds the canonical 2-, 3-, or 4-rak'ah pose sequence — the port of
 * Android's buildPrayerSample. Presentation data, not captured sensor data;
 * transitional poses make the articulated figure move naturally.
 */
internal fun buildPrayerSimulationSteps(rakahCount: Int): List<PrayerSimStep> = buildList {
    require(rakahCount in 2..4)
    for (rakah in 1..rakahCount) {
        if (rakah == 1) {
            add(
                PrayerSimStep(
                    1,
                    SalahPosture.QIYAM,
                    "Opening takbir",
                    1_500,
                    guidance = "Raise your hands and say Allahu Akbar to enter the prayer.",
                ),
            )
            add(
                PrayerSimStep(
                    1,
                    SalahPosture.QIYAM,
                    "Opening supplication",
                    5_000,
                    fortressChapterId = 16,
                ),
            )
        }
        add(
            PrayerSimStep(
                rakah,
                SalahPosture.QIYAM,
                "Qur'an recitation",
                5_000,
                guidance = if (rakah <= 2) {
                    "Recite Al-Fatihah, followed by another passage of the Qur'an."
                } else {
                    "Recite Al-Fatihah."
                },
            ),
        )
        add(PrayerSimStep(rakah, SalahPosture.RUKU, "Bowing", 4_000, fortressChapterId = 17))
        add(
                PrayerSimStep(
                    rakah,
                    SalahPosture.QIYAM_RISING,
                    "Standing after ruku",
                    3_500,
                    fortressChapterId = 18,
                ),
            )
        add(
            PrayerSimStep(
                rakah,
                SalahPosture.GOING_TO_SUJUD,
                "Lowering to first sujud",
                1_200,
                guidance = "Say Allahu Akbar while lowering into prostration.",
            ),
        )
        add(PrayerSimStep(rakah, SalahPosture.SUJUD, "First sujud", 4_000, fortressChapterId = 19))
        add(
                PrayerSimStep(
                    rakah,
                    SalahPosture.JALSA,
                    "Sitting between sujud",
                    3_500,
                    fortressChapterId = 20,
                ),
            )
        add(
            PrayerSimStep(
                rakah,
                SalahPosture.GOING_TO_SUJUD,
                "Lowering to second sujud",
                1_200,
                guidance = "Say Allahu Akbar while lowering into the second prostration.",
            ),
        )
        add(PrayerSimStep(rakah, SalahPosture.SUJUD, "Second sujud", 4_000, fortressChapterId = 19))
        if (rakah == rakahCount) {
            add(PrayerSimStep(rakah, SalahPosture.TASHAHHUD, "Tashahhud", 5_000, fortressChapterId = 22))
            add(
                PrayerSimStep(
                    rakah,
                    SalahPosture.TASHAHHUD,
                    "Blessings upon the Prophet",
                    5_000,
                    fortressChapterId = 23,
                ),
            )
            add(
                PrayerSimStep(
                    rakah,
                    SalahPosture.TASHAHHUD,
                    "Supplication before salam",
                    5_000,
                    fortressChapterId = 24,
                ),
            )
            add(
                PrayerSimStep(
                    rakah,
                    SalahPosture.TASHAHHUD,
                    "End with salam",
                    2_500,
                    guidance = "Turn to the right and then the left to end the prayer with salam.",
                ),
            )
            add(
                PrayerSimStep(
                    rakah,
                    SalahPosture.TASHAHHUD,
                    "Remembrance after salam",
                    5_000,
                    fortressChapterId = 25,
                ),
            )
        } else {
            // Three- and four-rak'ah prayers include the first tashahhud after rak'ah two.
            if (rakah == 2 && rakahCount > 2) {
                add(PrayerSimStep(rakah, SalahPosture.TASHAHHUD, "First tashahhud", 5_000, fortressChapterId = 22))
            }
            add(
                PrayerSimStep(
                    rakah,
                    SalahPosture.RISING_TO_QIYAM,
                    "Rise for rak'ah ${rakah + 1}",
                    1_500,
                    guidance = "Say Allahu Akbar while rising for rak'ah ${rakah + 1}.",
                ),
            )
        }
    }
}

/** The figure's pose parameters, interpolated between steps. */
private data class FigurePose(val bend: Float, val crouch: Float, val lean: Float)

private fun poseFor(posture: SalahPosture): FigurePose = when (posture) {
    SalahPosture.QIYAM -> FigurePose(0f, 0f, 0f)
    SalahPosture.RUKU -> FigurePose(0.78f, 0f, 0f)
    SalahPosture.QIYAM_RISING -> FigurePose(0.18f, 0f, 0f)
    SalahPosture.GOING_TO_SUJUD -> FigurePose(0.45f, 0.45f, 0f)
    SalahPosture.SUJUD -> FigurePose(0.85f, 0.92f, 0f)
    SalahPosture.JALSA -> FigurePose(0.05f, 0.55f, 0f)
    SalahPosture.TASHAHHUD -> FigurePose(0.05f, 0.55f, 0f)
    SalahPosture.RISING_TO_QIYAM -> FigurePose(0.1f, 0.3f, 0f)
    SalahPosture.NOT_PRAYING -> FigurePose(0f, 0f, 0f)
}

private fun lerp(a: FigurePose, b: FigurePose, t: Float) = FigurePose(
    bend = a.bend + (b.bend - a.bend) * t,
    crouch = a.crouch + (b.crouch - a.crouch) * t,
    lean = a.lean + (b.lean - a.lean) * t,
)

/**
 * The 2-, 3-, or 4-rak'ah prayer in 3-D — the shared counterpart of Android's
 * Visualization3DView guided figure: an articulated stick figure in a
 * rotatable perspective scene animating through the full prayer, rak'ah by
 * rak'ah, with step captions and guidance.
 */
@Composable
internal fun PrayerSimulationScreen(
    onBack: () -> Unit,
    scene3DService: Salah3DSceneService? = null,
) {
    var rakat by remember { mutableIntStateOf(4) }
    var stepIndex by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var yaw by remember { mutableFloatStateOf(0.6f) }
    var pitch by remember { mutableFloatStateOf(0.35f) }
    var transition by remember { mutableFloatStateOf(1f) }
    val steps = remember(rakat) { buildPrayerSimulationSteps(rakat) }

    val stepProgress = remember { Animatable(1f) }

    // ── The respective dua per step — Android's TwoRakahDuaPanel behavior ──
    val duaPlayer = remember { com.starception.submission.shared.audio.QuranAudioPlayer() }
    val fortressRepository = remember { createSharedFortressRepository() }
    var duaInvocation by remember {
        mutableStateOf<com.starception.submission.shared.content.FortressInvocation?>(null)
    }
    var duaPlaying by remember { mutableStateOf(false) }
    var duaPausedByUser by remember { mutableStateOf(false) }

    val currentStep = steps[stepIndex.coerceIn(0, steps.lastIndex)]

    // Each step that owns a Fortress chapter plays its first invocation from
    // the beginning — the step index is part of the key, so repeated phases
    // (such as both sujud) replay their clip instead of resuming the last one.
    LaunchedEffect(steps, stepIndex, playing) {
        val chapterId = steps[stepIndex.coerceIn(0, steps.lastIndex)].fortressChapterId
        if (playing && chapterId != null) {
            val invocation = runCatching {
                fortressRepository.getChapterInvocations(chapterId)
            }.getOrNull()
                ?.firstOrNull { it.audioUrl.isNotBlank() }
            if (invocation != null) {
                duaInvocation = invocation
                duaPausedByUser = false
                duaPlayer.stop()
                duaPlaying = duaPlayer.play(invocation.audioUrl)
            } else {
                duaInvocation = null
                duaPlaying = false
            }
        } else {
            duaPlayer.stop()
            duaPlaying = false
        }
    }
    // Leaving the screen stops the recitation.
    androidx.compose.runtime.DisposableEffect(duaPlayer) {
        onDispose { duaPlayer.stop() }
    }

    // The step engine: each step runs its duration, easing into the next pose.
    LaunchedEffect(playing, steps) {
        stepProgress.snapTo(0f)
        while (playing) {
            val duration = steps[stepIndex.coerceIn(0, steps.lastIndex)].durationMillis
            stepProgress.animateTo(1f, tween(durationMillis = duration.toInt()))
            if (playing && stepIndex < steps.lastIndex) {
                stepIndex += 1
                stepProgress.snapTo(0f)
            } else if (playing && stepIndex >= steps.lastIndex) {
                playing = false
            }
        }
    }

    // The native 3-D scene shows the interpolated skeleton at the Metal frame
    // rate; the pose target eases across the whole step duration, matching the
    // Canvas figure's motion beat for beat.
    if (scene3DService != null) {
        LaunchedEffect(scene3DService) {
            scene3DService.setSceneMode(0)
        }
        LaunchedEffect(scene3DService, steps, stepIndex) {
            while (true) {
                val current = steps[stepIndex.coerceIn(0, steps.lastIndex)]
                val previous = steps[(stepIndex - 1).coerceAtLeast(0)]
                val interpolated = lerpSkeletonPose(
                    skeletonPoseFor(previous.posture),
                    skeletonPoseFor(current.posture),
                    stepProgress.value,
                )
                scene3DService.updatePose(
                    joints = interpolated.flatten(),
                    postureIndex = SalahPosture.classificationLabels.indexOf(current.posture),
                )
                kotlinx.coroutines.delay(33)
            }
        }
    }

    SharedDetailScaffold(title = "Prayer simulation", onBack = onBack) {
        Column(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 3, 4).forEach { count ->
                    FilterChip(
                        selected = rakat == count,
                        onClick = {
                            rakat = count
                            stepIndex = 0
                            transition = 0f
                            playing = true
                        },
                        label = { Text("$count rak'ah") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val primary = MaterialTheme.colorScheme.primary
            val tertiary = MaterialTheme.colorScheme.tertiary
            val outline = MaterialTheme.colorScheme.outlineVariant
            val onSurface = MaterialTheme.colorScheme.onSurface
            val current = steps[stepIndex.coerceIn(0, steps.lastIndex)]
            val previous = steps[(stepIndex - 1).coerceAtLeast(0)]
            val pose = lerp(poseFor(previous.posture), poseFor(current.posture), stepProgress.value)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (scene3DService != null) {
                    Salah3DSceneHost(
                        service = scene3DService,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    yaw -= drag.x / 240f
                                    pitch = (pitch - drag.y / 240f).coerceIn(-1.35f, 1.35f)
                                }
                            },
                    ) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val scale = size.minDimension / 3.2f
                    fun project(x: Float, y: Float, z: Float): Offset {
                        val cy1 = cos(yaw)
                        val sy1 = sin(yaw)
                        val x1 = x * cy1 + z * sy1
                        val z1 = -x * sy1 + z * cy1
                        val cp = cos(pitch)
                        val sp = sin(pitch)
                        val y2 = y * cp - z1 * sp
                        val z2 = y * sp + z1 * cp
                        val perspective = 2.4f / (2.4f + z2)
                        return Offset(cx + x1 * scale * perspective, cy - y2 * scale * perspective)
                    }
                    drawCircle(outline, radius = scale * 1.1f, center = Offset(cx, cy), style = Stroke(1.dp.toPx()))
                    // The articulated figure in the interpolated pose.
                    val hipY = 1f - pose.crouch
                    val torsoTopX = sin(pose.bend * 0.9f) * 1.0f
                    val torsoTopY = hipY + cos(pose.bend * 0.9f) * (1f - pose.crouch * 0.4f)
                    val shoulder = SimOffset3(torsoTopX, torsoTopY, pose.lean)
                    val hip = SimOffset3(0f, hipY, 0f)
                    val head = SimOffset3(shoulder.x * 1.15f, shoulder.y + 0.28f, shoulder.z)
                    val stroke = 5.dp.toPx()
                    fun limb(a: SimOffset3, b: SimOffset3, color: Color, width: Float) {
                        drawLine(color, project(a.x, a.y, a.z), project(b.x, b.y, b.z), strokeWidth = width)
                    }
                    if (pose.crouch > 0f) {
                        limb(hip, SimOffset3(0.45f, 0f, -0.35f), onSurface, stroke)
                        limb(hip, SimOffset3(-0.45f, 0f, -0.35f), onSurface, stroke)
                    } else {
                        limb(hip, SimOffset3(0.3f, 0f, 0.1f), onSurface, stroke)
                        limb(hip, SimOffset3(-0.3f, 0f, 0.1f), onSurface, stroke)
                    }
                    limb(hip, shoulder, primary, stroke)
                    drawCircle(primary, radius = 7.dp.toPx(), center = project(head.x, head.y, head.z))
                    val armY = hipY - 0.1f - pose.crouch * 0.15f
                    val armX = if (pose.crouch > 0.8f) 0.9f else sin(pose.bend) * 0.5f
                    limb(shoulder, SimOffset3(shoulder.x + armX * 0.6f, armY, shoulder.z + 0.15f), tertiary, stroke * 0.8f)
                    limb(shoulder, SimOffset3(shoulder.x - armX * 0.4f, armY, shoulder.z - 0.15f), tertiary, stroke * 0.8f)
                }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (stepIndex >= steps.lastIndex && !playing) {
                            stepIndex = 0
                            playing = true
                        } else {
                            playing = !playing
                        }
                    },
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (playing) "Pause" else "Play")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Rak'ah ${currentStep.rakah} · ${currentStep.label}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    currentStep.guidance?.let { guidance ->
                        Text(
                            guidance,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            duaInvocation?.let { invocation ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconTapTarget(
                            icon = if (duaPlaying) {
                                androidx.compose.material.icons.Icons.Filled.Pause
                            } else {
                                androidx.compose.material.icons.Icons.Filled.PlayArrow
                            },
                            contentDescription = if (duaPlaying) {
                                "Pause dua recitation"
                            } else {
                                "Play dua recitation"
                            },
                            tint = MaterialTheme.colorScheme.primary,
                            visualSize = 34.dp,
                            iconSize = 22.dp,
                            showBackground = false,
                            onClick = {
                                if (duaPlaying) {
                                    duaPlayer.pause()
                                    duaPlaying = false
                                    duaPausedByUser = true
                                } else if (invocation.audioUrl.isNotBlank()) {
                                    duaPlaying = duaPlayer.play(invocation.audioUrl)
                                    duaPausedByUser = false
                                }
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Dua · ${currentStep.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                invocation.arabic,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Right,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            val scrubScope = androidx.compose.runtime.rememberCoroutineScope()
            Slider(
                value = stepIndex.toFloat() + stepProgress.value,
                onValueChange = { absolute ->
                    val index = absolute.toInt().coerceIn(0, steps.lastIndex)
                    stepIndex = index
                    scrubScope.launch { stepProgress.snapTo((absolute - index).coerceIn(0f, 1f)) }
                },
                valueRange = 0f..steps.lastIndex.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Step ${stepIndex + 1} of ${steps.size} · drag to orbit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}

private data class SimSimOffset3(val x: Float, val y: Float, val z: Float)

private data class SimOffset3(val x: Float, val y: Float, val z: Float)
