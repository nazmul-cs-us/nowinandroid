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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.starception.submission.shared.ml.SalahDataSample
import com.starception.submission.shared.ml.SalahPosture
import com.starception.submission.shared.ml.SalahRecordingStore
import kotlin.math.cos
import kotlin.math.sin

/** Android's VisualizationMode — the shared review renders the same scenes. */
private enum class ReviewScene(val label: String) {
    HUMANOID("Humanoid"),
    SCATTER("Scatter"),
    GRAVITY("Gravity"),
}

/**
 * 3-D review of a recorded session — the shared counterpart of Android's
 * LibGDX Visualization3DView: a draggable orbit view that replays the session
 * as a humanoid posed by the captured pitch/roll, a sensor-point scatter, or
 * the gravity vector, with scrub and playback.
 */
@Composable
internal fun SalahSessionReviewScreen(
    fileName: String,
    onBack: () -> Unit,
) {
    val store = remember { SalahRecordingStore() }
    var samples by remember { mutableStateOf<List<SalahDataSample>?>(null) }
    LaunchedEffect(fileName) {
        samples = store.sessionSamples(fileName)
    }

    SharedDetailScaffold(title = "Session review", onBack = onBack) {
        val loaded = samples
        when {
            loaded == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            loaded.isEmpty() -> Text(
                "This session has no samples.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> ReviewBody(fileName, loaded)
        }
    }
}

@Composable
private fun ReviewBody(fileName: String, samples: List<SalahDataSample>) {
    var scene by remember { mutableStateOf(ReviewScene.HUMANOID) }
    var frame by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var yaw by remember { mutableFloatStateOf(0.6f) }
    var pitch by remember { mutableFloatStateOf(0.35f) }

    // Playback: ~10 windows per second — the capture cadence.
    LaunchedEffect(playing, samples.size) {
        while (playing) {
            kotlinx.coroutines.delay(100)
            frame = (frame + 1) % samples.size
        }
    }

    val postureNames = samples.map { it.posture }.distinct()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                fileName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                "${samples.size} windows · ${(samples.size / 10)}s",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(postureNames, key = { it.name }) { posture ->
                FilterChip(
                    selected = samples[frame].posture == posture,
                    onClick = { },
                    label = { Text(posture.displayName, maxLines = 1) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            SceneCanvas(
                samples = samples,
                frame = frame,
                scene = scene,
                yaw = yaw,
                pitch = pitch,
                onOrbit = { dyaw, dpitch ->
                    yaw += dyaw
                    pitch = (pitch + dpitch).coerceIn(-1.35f, 1.35f)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { playing = !playing },
                modifier = Modifier.heightIn(min = 42.dp),
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                )
                Spacer(Modifier.width(6.dp))
                Text(if (playing) "Pause" else "Play")
            }
            Text(
                "Window ${frame + 1}/${samples.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = frame.toFloat(),
            onValueChange = { frame = it.toInt().coerceIn(0, samples.size - 1) },
            valueRange = 0f..(samples.size - 1).toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReviewScene.entries.forEach { candidate ->
                FilterChip(
                    selected = scene == candidate,
                    onClick = { scene = candidate },
                    label = { Text(candidate.label) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = { }, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Drag to orbit · pinch-free 3-D, rendered on-device",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** The orbit-draggable 3-D canvas — one projection shared by all scenes. */
@Composable
private fun SceneCanvas(
    samples: List<SalahDataSample>,
    frame: Int,
    scene: ReviewScene,
    yaw: Float,
    pitch: Float,
    onOrbit: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sample = samples[frame.coerceIn(0, samples.lastIndex)]
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier = modifier
            .padding(8.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onOrbit(-drag.x / 240f, -drag.y / 240f)
                }
            },
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val scale = size.minDimension / 3.2f
        fun project(x: Float, y: Float, z: Float): Offset {
            // Rotate by yaw then pitch; y is up in model space, down on canvas.
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

        // Ground ring for depth reference.
        drawCircle(outline, radius = scale * 1.1f, center = Offset(cx, cy), style = Stroke(1.dp.toPx()))

        when (scene) {
            ReviewScene.HUMANOID -> drawHumanoid(::project, sample, primary, onSurface, tertiary)
            ReviewScene.SCATTER -> drawScatter(::project, samples, frame, primary, tertiary)
            ReviewScene.GRAVITY -> drawGravity(::project, sample, primary, tertiary, onSurface)
        }
    }
}

/** Stick figure posed by the window's pitch (bend) and roll (lean). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHumanoid(
    project: (Float, Float, Float) -> Offset,
    sample: SalahDataSample,
    primary: Color,
    onSurface: Color,
    tertiary: Color,
) {
    val bend = (sample.pitch / 90f).coerceIn(-1f, 1f)
    val lean = (sample.roll / 90f).coerceIn(-1f, 1f)
    val crouch = when (sample.posture) {
        SalahPosture.SUJUD -> 0.9f
        SalahPosture.JALSA, SalahPosture.TASHAHHUD -> 0.55f
        else -> 0f
    }
    val hipY = 1f - crouch
    // Torso: hip to shoulders, bent forward by pitch.
    val torsoTopX = sin(bend * 0.9f) * 1.0f
    val torsoTopY = hipY + cos(bend * 0.9f) * (1f - crouch * 0.4f)
    val shoulder = Offset3(torsoTopX + lean * 0.2f, torsoTopY, lean * 0.3f)
    val hip = Offset3(0f, hipY, 0f)
    val head = Offset3(shoulder.x * 1.15f, shoulder.y + 0.28f, shoulder.z)

    // Arms: hanging for standing, on knees in ruku, on the floor in sujud.
    val armX = if (crouch > 0.8f) 0.9f else sin(bend) * 0.5f
    val armY = hipY - 0.1f - crouch * 0.15f
    fun limb(a: Offset3, b: Offset3, color: Color, width: Float) {
        drawLine(color, project(a.x, a.y, a.z), project(b.x, b.y, b.z), strokeWidth = width)
    }
    val stroke = 5.dp.toPx()
    // Legs: fold under the body when seated/prostrating.
    if (crouch > 0f) {
        limb(hip, Offset3(0.45f, 0f, -0.35f), onSurface, stroke)
        limb(hip, Offset3(-0.45f, 0f, -0.35f), onSurface, stroke)
    } else {
        limb(hip, Offset3(0.3f, 0f, 0.1f), onSurface, stroke)
        limb(hip, Offset3(-0.3f, 0f, 0.1f), onSurface, stroke)
    }
    limb(hip, shoulder, primary, stroke)
    // Head
    drawCircle(primary, radius = 7.dp.toPx(), center = project(head.x, head.y, head.z))
    // Arms
    limb(shoulder, Offset3(shoulder.x + armX * 0.6f, armY, shoulder.z + 0.15f), tertiary, stroke * 0.8f)
    limb(shoulder, Offset3(shoulder.x - armX * 0.4f, armY, shoulder.z - 0.15f), tertiary, stroke * 0.8f)
}

/** The last 120 windows' accelerations as depth-scaled points. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScatter(
    project: (Float, Float, Float) -> Offset,
    samples: List<SalahDataSample>,
    frame: Int,
    primary: Color,
    tertiary: Color,
) {
    val start = (frame - 120).coerceAtLeast(0)
    for (i in start..frame) {
        val s = samples[i]
        // One point per window: the window's mean acceleration, axis-clamped.
        val x = s.accelX.average().toFloat().coerceIn(-12f, 12f) / 8f
        val y = s.accelY.average().toFloat().coerceIn(-12f, 12f) / 8f
        val z = s.accelZ.average().toFloat().coerceIn(-12f, 12f) / 8f
        val point = project(x, y, z)
        val alpha = 0.25f + 0.75f * (i - start) / (frame - start + 1).coerceAtLeast(1)
        drawCircle(
            if (i == frame) tertiary else primary,
            radius = (if (i == frame) 5.dp else 2.5.dp).toPx(),
            center = point,
            alpha = alpha,
        )
    }
}

/** The window's gravity vector against the world frame. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGravity(
    project: (Float, Float, Float) -> Offset,
    sample: SalahDataSample,
    primary: Color,
    tertiary: Color,
    onSurface: Color,
) {
    // World frame axes.
    drawLine(onSurface.copy(alpha = 0.4f), project(-1.2f, 0f, 0f), project(1.2f, 0f, 0f), strokeWidth = 1.5.dp.toPx())
    drawLine(onSurface.copy(alpha = 0.4f), project(0f, 0f, -1.2f), project(0f, 0f, 1.2f), strokeWidth = 1.5.dp.toPx())
    // Device gravity direction (accel points up when the phone rests flat).
    val gx = sample.accelX.average().toFloat() / 9.8f
    val gy = sample.accelY.average().toFloat() / 9.8f
    val gz = sample.accelZ.average().toFloat() / 9.8f
    val tip = project(gx, gy, gz)
    drawLine(primary, project(0f, 0f, 0f), tip, strokeWidth = 5.dp.toPx())
    drawCircle(tertiary, radius = 6.dp.toPx(), center = tip)
}

private data class Offset3(val x: Float, val y: Float, val z: Float)
