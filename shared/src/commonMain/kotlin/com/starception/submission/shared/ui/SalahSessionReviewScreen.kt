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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.shared.ml.SalahDataSample
import com.starception.submission.shared.ml.SalahFeatureExtractor
import com.starception.submission.shared.ml.SalahPosture
import com.starception.submission.shared.ml.SalahRecordingStore
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Android's VisualizationMode — the shared review renders the same scenes. */
private enum class ReviewScene(val label: String) {
    HUMANOID("Humanoid"),
    SCATTER("Scatter"),
    GRAVITY("Gravity"),
    FEATURE_PCA("Features (PCA)"),
}

/**
 * 3-D review of a recorded session — the shared counterpart of Android's
 * Visualization3DView: a draggable orbit view that replays the session as a
 * keyframed 16-joint skeleton posed per posture, a posture-coloured sensor
 * scatter, the gravity vector, or the 30-D feature space projected to 3-D via
 * PCA, with scrub, playback and posture visibility filters.
 */
@Composable
internal fun SalahSessionReviewScreen(
    fileName: String,
    qualityAnalyzer: com.starception.submission.shared.ml.SalahQualityAnalyzer? = null,
    scene3DService: Salah3DSceneService? = null,
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
            else -> ReviewBody(fileName, loaded, qualityAnalyzer, scene3DService)
        }
    }
}

/** One projected point in the SCATTER / FEATURE_PCA scenes. */
private data class PlotPoint3(
    val index: Int,
    val posture: SalahPosture,
    val x: Float,
    val y: Float,
    val z: Float,
)

/** PCA over the shared 30-D feature vectors: positions plus explained variance. */
private data class PcaResult(val positions: FloatArray, val varianceFraction: Float)

@Composable
private fun ReviewBody(
    fileName: String,
    samples: List<SalahDataSample>,
    qualityAnalyzer: com.starception.submission.shared.ml.SalahQualityAnalyzer?,
    scene3DService: Salah3DSceneService?,
) {
    var report by remember { mutableStateOf<com.starception.submission.shared.ml.SalahQualityReport?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var scene by remember { mutableStateOf(ReviewScene.HUMANOID) }
    var frame by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var yaw by remember { mutableFloatStateOf(0.6f) }
    var pitch by remember { mutableFloatStateOf(0.35f) }
    var visiblePostures by remember { mutableStateOf(SalahPosture.classificationLabels.toSet()) }
    var pcaResult by remember { mutableStateOf<PcaResult?>(null) }
    var computingPca by remember { mutableStateOf(false) }

    // Playback: ~10 windows per second — the capture cadence.
    LaunchedEffect(playing, samples.size) {
        while (playing) {
            kotlinx.coroutines.delay(100)
            frame = (frame + 1) % samples.size
        }
    }
    // The model-vs-label quality pass — Android's "Analyze data quality".
    LaunchedEffect(analyzing) {
        if (analyzing) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                qualityAnalyzer?.analyze(samples)
            }?.let { result ->
                report = result
                analyzing = false
            }
        }
    }
    // The FEATURE_PCA projection is computed once per session, off the main thread.
    LaunchedEffect(scene, samples) {
        if (scene == ReviewScene.FEATURE_PCA && pcaResult == null && samples.size >= 4) {
            computingPca = true
            pcaResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                computePca(samples)
            }
            computingPca = false
        }
    }

    val postureNames = samples.map { it.posture }.distinct()
    // The normalized point cloud for the SCATTER / FEATURE_PCA scenes.
    val plotPoints = remember(samples, visiblePostures, pcaResult, scene) {
        buildPlotPoints(samples, visiblePostures, scene, pcaResult)
    }
    val sample = samples[frame.coerceIn(0, samples.lastIndex)]

    // ── Native 3-D bridge (Swift3D on iOS) ─────────────────────────────────
    if (scene3DService != null) {
        LaunchedEffect(scene3DService, scene) {
            scene3DService.setSceneMode(scene.ordinal)
        }
        when (scene) {
            ReviewScene.HUMANOID -> LaunchedEffect(scene3DService, sample.posture, frame) {
                // The paper's walking gait animates the figure through
                // NOT_PRAYING (walking) runs; prayer postures keep their
                // keyframes.
                val joints = if (sample.posture == SalahPosture.NOT_PRAYING) {
                    walkingSkeletonPose(frame * GAIT_PHASE_PER_WINDOW)
                } else {
                    skeletonPoseFor(sample.posture)
                }
                scene3DService.updatePose(
                    joints = joints.flatten(),
                    postureIndex = SalahPosture.classificationLabels.indexOf(sample.posture),
                )
            }
            ReviewScene.SCATTER, ReviewScene.FEATURE_PCA -> {
                // Thousands of Metal spheres per frame is too many draw calls;
                // stride-sample the cloud down to a few hundred visible points.
                val scatterPayload = remember(plotPoints) {
                    if (plotPoints.isEmpty()) {
                        null
                    } else {
                        val stride = (plotPoints.size / 480) + 1
                        val sampled = plotPoints
                            .filterIndexed { index, _ -> index % stride == 0 }
                            .let { it + plotPoints.last() }
                        Triple(
                            sampled.flatMap { listOf(it.x, it.y, it.z) },
                            sampled.map { SalahPosture.classificationLabels.indexOf(it.posture) },
                            sampled.map { it.index },
                        )
                    }
                }
                LaunchedEffect(scene3DService, scatterPayload) {
                    val payload = scatterPayload
                    if (payload == null) {
                        scene3DService.updateScatter(emptyList(), emptyList())
                    } else {
                        scene3DService.updateScatter(payload.first, payload.second)
                    }
                }
                LaunchedEffect(scene3DService, plotPoints, frame) {
                    val playheadPoint = plotPoints.lastOrNull { it.index <= frame }
                    scene3DService.updatePlayhead(
                        if (playheadPoint == null) {
                            emptyList()
                        } else {
                            listOf(playheadPoint.x, playheadPoint.y, playheadPoint.z)
                        },
                    )
                }
            }
            ReviewScene.GRAVITY -> {
                LaunchedEffect(scene3DService, sample) {
                    scene3DService.updateGravity(
                        listOf(
                            sample.accelX.average().toFloat() / 9.8f,
                            sample.accelY.average().toFloat() / 9.8f,
                            sample.accelZ.average().toFloat() / 9.8f,
                        ),
                    )
                }
                LaunchedEffect(scene3DService) {
                    scene3DService.updatePlayhead(emptyList())
                }
            }
        }
    }
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
                    selected = posture in visiblePostures,
                    onClick = {
                        visiblePostures = if (posture in visiblePostures) {
                            visiblePostures - posture
                        } else {
                            visiblePostures + posture
                        }
                    },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(postureColor(posture), CircleShape),
                        )
                    },
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
            if (scene3DService != null) {
                Salah3DSceneHost(
                    service = scene3DService,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                SceneCanvas(
                    samples = samples,
                    frame = frame,
                    scene = scene,
                    plotPoints = plotPoints,
                    yaw = yaw,
                    pitch = pitch,
                    onOrbit = { dyaw, dpitch ->
                        yaw += dyaw
                        pitch = (pitch + dpitch).coerceIn(-1.35f, 1.35f)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (scene == ReviewScene.FEATURE_PCA) {
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    computingPca -> "Projecting the 30-D feature space…"
                    pcaResult != null ->
                        "Top 3 components capture ${(pcaResult!!.varianceFraction * 100).toInt()}% of feature variance"
                    else -> "Not enough windows for PCA"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ReviewScene.entries) { candidate ->
                FilterChip(
                    selected = scene == candidate,
                    onClick = { scene = candidate },
                    label = { Text(candidate.label, maxLines = 1) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        if (qualityAnalyzer != null && report == null) {
            Button(
                onClick = { analyzing = true },
                enabled = !analyzing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (analyzing) "Analyzing…" else "Analyze data quality")
            }
        }
        report?.let { QualityReportCard(it) }
    }
}

/** The orbit-draggable 3-D canvas — one projection shared by all scenes. */
@Composable
private fun SceneCanvas(
    samples: List<SalahDataSample>,
    frame: Int,
    scene: ReviewScene,
    plotPoints: List<PlotPoint3>,
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
    // The drawn pose chases the keyframe target so posture changes animate.
    val poseChase = remember { PoseChase() }
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
        val scale = size.minDimension / if (scene == ReviewScene.HUMANOID) 4.4f else 3.2f
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
            ReviewScene.HUMANOID -> {
                // A scientific reference grid on the floor, like Android's.
                drawGroundGrid(::project, outline)
                poseChase.joints = lerpSkeletonPose(poseChase.joints, skeletonPoseFor(sample.posture), 0.45f)
                drawSkeleton(poseChase.joints, ::project, postureColor(sample.posture), onSurface)
            }
            ReviewScene.SCATTER, ReviewScene.FEATURE_PCA -> {
                drawPlot(::project, plotPoints, frame, tertiary)
            }
            ReviewScene.GRAVITY -> drawGravity(::project, sample, primary, tertiary, onSurface)
        }
    }
}

// ─────────────────────────── humanoid scene ───────────────────────────

/** The currently drawn pose; eased toward each window's keyframe target. */
private class PoseChase(var joints: SkeletonJoints = skeletonPoseFor(SalahPosture.QIYAM))

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGroundGrid(
    project: (Float, Float, Float) -> Offset,
    outline: Color,
) {
    val ring = 1.0f
    val segments = 48
    val stroke = 1.dp.toPx()
    var previous = project(ring, 0f, 0f)
    for (step in 1..segments) {
        val angle = step * 2f * kotlin.math.PI.toFloat() / segments
        val current = project(ring * cos(angle), 0f, ring * sin(angle))
        drawLine(outline.copy(alpha = 0.65f), previous, current, strokeWidth = stroke)
        previous = current
    }
    // Cross axes through the ring centre for orientation.
    drawLine(outline.copy(alpha = 0.4f), project(-ring, 0f, 0f), project(ring, 0f, 0f), strokeWidth = stroke)
    drawLine(outline.copy(alpha = 0.4f), project(0f, 0f, -ring), project(0f, 0f, ring), strokeWidth = stroke)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSkeleton(
    joints: SkeletonJoints,
    project: (Float, Float, Float) -> Offset,
    postureColor: Color,
    onSurface: Color,
) {
    fun j(joint: Joint) = joints.getValue(joint)
    fun segment(a: Joint, b: Joint, color: Color, width: Float) {
        val pa = j(a)
        val pb = j(b)
        drawLine(
            color,
            project(pa.x, pa.y, pa.z),
            project(pb.x, pb.y, pb.z),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
    val limb = 5.dp.toPx()
    val torso = 7.dp.toPx()

    // Torso: shoulder line, spine, hip line.
    segment(Joint.LEFT_SHOULDER, Joint.RIGHT_SHOULDER, postureColor, torso)
    segment(Joint.LEFT_HIP, Joint.RIGHT_HIP, postureColor, torso)
    val shoulderMid = Joint3(
        (j(Joint.LEFT_SHOULDER).x + j(Joint.RIGHT_SHOULDER).x) / 2f,
        j(Joint.LEFT_SHOULDER).y,
        (j(Joint.LEFT_SHOULDER).z + j(Joint.RIGHT_SHOULDER).z) / 2f,
    )
    val hipMid = Joint3(
        (j(Joint.LEFT_HIP).x + j(Joint.RIGHT_HIP).x) / 2f,
        j(Joint.LEFT_HIP).y,
        (j(Joint.LEFT_HIP).z + j(Joint.RIGHT_HIP).z) / 2f,
    )
    drawLine(
        postureColor,
        project(shoulderMid.x, shoulderMid.y, shoulderMid.z),
        project(hipMid.x, hipMid.y, hipMid.z),
        strokeWidth = torso,
        cap = StrokeCap.Round,
    )
    // Neck.
    segment(Joint.NECK, Joint.HEAD, postureColor, limb)
    drawLine(
        postureColor,
        project(j(Joint.LEFT_SHOULDER).x, j(Joint.LEFT_SHOULDER).y, j(Joint.LEFT_SHOULDER).z),
        project(shoulderMid.x, shoulderMid.y, shoulderMid.z),
        strokeWidth = limb,
        cap = StrokeCap.Round,
    )
    // Arms.
    segment(Joint.LEFT_SHOULDER, Joint.LEFT_ELBOW, postureColor, limb)
    segment(Joint.LEFT_ELBOW, Joint.LEFT_WRIST, postureColor, limb)
    segment(Joint.RIGHT_SHOULDER, Joint.RIGHT_ELBOW, postureColor, limb)
    segment(Joint.RIGHT_ELBOW, Joint.RIGHT_WRIST, postureColor, limb)
    // Legs.
    segment(Joint.LEFT_HIP, Joint.LEFT_KNEE, postureColor, limb)
    segment(Joint.LEFT_KNEE, Joint.LEFT_ANKLE, postureColor, limb)
    segment(Joint.LEFT_ANKLE, Joint.LEFT_TOE, postureColor, limb * 0.8f)
    segment(Joint.RIGHT_HIP, Joint.RIGHT_KNEE, postureColor, limb)
    segment(Joint.RIGHT_KNEE, Joint.RIGHT_ANKLE, postureColor, limb)
    segment(Joint.RIGHT_ANKLE, Joint.RIGHT_TOE, postureColor, limb * 0.8f)
    // Head.
    val head = j(Joint.HEAD)
    drawCircle(postureColor, radius = 8.dp.toPx(), center = project(head.x, head.y, head.z))
    // Articulation dots at the key joints.
    listOf(
        Joint.LEFT_ELBOW, Joint.RIGHT_ELBOW, Joint.LEFT_WRIST, Joint.RIGHT_WRIST,
        Joint.LEFT_KNEE, Joint.RIGHT_KNEE, Joint.LEFT_ANKLE, Joint.RIGHT_ANKLE,
    ).forEach { joint ->
        val p = j(joint)
        drawCircle(onSurface, radius = 2.dp.toPx(), center = project(p.x, p.y, p.z))
    }
}

// ───────────────────── scatter / feature-PCA scenes ─────────────────────

/** Android's posture palette — the same colors across Android and iOS. */
private fun postureColor(posture: SalahPosture): Color = when (posture) {
    SalahPosture.QIYAM -> Color(0xFF48D9FF)
    SalahPosture.QIYAM_RISING -> Color(0xFF42F5D4)
    SalahPosture.RISING_TO_QIYAM -> Color(0xFF16C79A)
    SalahPosture.RUKU -> Color(0xFFFFB347)
    SalahPosture.GOING_TO_SUJUD -> Color(0xFFFF4FA3)
    SalahPosture.SUJUD -> Color(0xFF72ED7D)
    SalahPosture.JALSA -> Color(0xFFAD8CFF)
    SalahPosture.TASHAHHUD -> Color(0xFFFF704D)
    SalahPosture.NOT_PRAYING -> Color(0xFF9AA5B1)
}

/**
 * Normalizes the visible samples into the [-1.3, 1.3] plot cube — Android's
 * normalizedPlotPoints. SCATTER uses per-window mean acceleration; FEATURE_PCA
 * uses the cached PCA triplets.
 */
private fun buildPlotPoints(
    samples: List<SalahDataSample>,
    visiblePostures: Set<SalahPosture>,
    scene: ReviewScene,
    pca: PcaResult?,
): List<PlotPoint3> {
    if (scene != ReviewScene.SCATTER && scene != ReviewScene.FEATURE_PCA) return emptyList()
    val usePca = scene == ReviewScene.FEATURE_PCA && pca != null
    val raw = ArrayList<PlotPoint3>(samples.size)
    samples.forEachIndexed { index, sample ->
        if (sample.posture !in visiblePostures) return@forEachIndexed
        if (usePca && index * 3 + 2 < pca!!.positions.size) {
            raw.add(
                PlotPoint3(
                    index,
                    sample.posture,
                    pca.positions[index * 3],
                    pca.positions[index * 3 + 1],
                    pca.positions[index * 3 + 2],
                ),
            )
        } else if (!usePca) {
            raw.add(
                PlotPoint3(
                    index,
                    sample.posture,
                    sample.accelX.average().toFloat().coerceIn(-12f, 12f),
                    sample.accelY.average().toFloat().coerceIn(-12f, 12f),
                    sample.accelZ.average().toFloat().coerceIn(-12f, 12f),
                ),
            )
        }
    }
    if (raw.isEmpty()) return emptyList()
    val minX = raw.minOf { it.x }
    val maxX = raw.maxOf { it.x }
    val minY = raw.minOf { it.y }
    val maxY = raw.maxOf { it.y }
    val minZ = raw.minOf { it.z }
    val maxZ = raw.maxOf { it.z }
    val centerX = (minX + maxX) * 0.5f
    val centerY = (minY + maxY) * 0.5f
    val centerZ = (minZ + maxZ) * 0.5f
    val largestSpan = maxOf(maxX - minX, maxOf(maxY - minY, maxZ - minZ)).coerceAtLeast(0.001f)
    val scale = 2.6f / largestSpan
    return raw.map { point ->
        PlotPoint3(
            point.index,
            point.posture,
            (point.x - centerX) * scale,
            (point.y - centerY) * scale,
            (point.z - centerZ) * scale,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlot(
    project: (Float, Float, Float) -> Offset,
    points: List<PlotPoint3>,
    frame: Int,
    tertiary: Color,
) {
    val playheadIndex = points.lastOrNull { it.index <= frame }?.index
    points.forEach { point ->
        val center = project(point.x, point.y, point.z)
        if (point.index == playheadIndex) {
            drawCircle(Color.White, radius = 9.dp.toPx(), center = center)
            drawCircle(tertiary, radius = 7.dp.toPx(), center = center)
        } else {
            // Older windows fade toward the back of the trail.
            val age = (frame - point.index).coerceAtLeast(0)
            val alpha = (1f - age / 400f).coerceIn(0.18f, 1f)
            drawCircle(
                postureColor(point.posture).copy(alpha = alpha),
                radius = 2.5.dp.toPx(),
                center = center,
            )
        }
    }
}

// ────────────────────────────── PCA ──────────────────────────────

/**
 * Projects the 30-D shared feature space to 3-D via covariance power
 * iteration with Hotelling deflation — the shared counterpart of Android's
 * FEATURE_PCA mode, computed once per session.
 */
private fun computePca(samples: List<SalahDataSample>): PcaResult? {
    val dims = SalahFeatureExtractor.FEATURES_PER_WINDOW
    val n = samples.size
    if (n < 4) return null
    val features = samples.map { SalahFeatureExtractor.extractFeatures(it) }
    if (features.any { it.size < dims }) return null

    val mean = FloatArray(dims)
    features.forEach { feature -> for (d in 0 until dims) mean[d] += feature[d] }
    for (d in 0 until dims) mean[d] /= n

    // One deflated working copy per component and one pristine copy for the
    // final projection.
    val original = Array(n) { i ->
        FloatArray(dims) { d -> features[i][d] - mean[d] }
    }
    var working = Array(n) { original[it].copyOf() }

    var totalVariance = 0f
    for (i in 0 until n) {
        val f = original[i]
        for (d in 0 until dims) totalVariance += f[d] * f[d]
    }
    totalVariance /= n
    if (totalVariance <= 0f) return null

    val components = ArrayList<FloatArray>(3)
    var explained = 0f
    repeat(3) { componentIndex ->
        var v = FloatArray(dims)
        v[(componentIndex * 7) % dims] = 1f
        repeat(40) {
            val scores = FloatArray(n)
            for (i in 0 until n) {
                var s = 0f
                val f = working[i]
                for (d in 0 until dims) s += f[d] * v[d]
                scores[i] = s
            }
            val next = FloatArray(dims)
            for (d in 0 until dims) {
                var s = 0f
                for (i in 0 until n) s += working[i][d] * scores[i]
                next[d] = s
            }
            var normSquared = 0f
            for (d in 0 until dims) normSquared += next[d] * next[d]
            val norm = sqrt(normSquared)
            if (norm <= 1e-20f) return@repeat
            for (d in 0 until dims) v[d] = next[d] / norm
        }
        // Rayleigh quotient against the current deflated data.
        var rayleigh = 0f
        for (i in 0 until n) {
            var s = 0f
            val f = working[i]
            for (d in 0 until dims) s += f[d] * v[d]
            rayleigh += s * s
        }
        explained += rayleigh / n
        // Hotelling deflation for the next component.
        working = Array(n) { i ->
            val f = working[i]
            var dot = 0f
            for (d in 0 until dims) dot += f[d] * v[d]
            FloatArray(dims) { d -> f[d] - dot * v[d] }
        }
        components.add(v)
    }

    val positions = FloatArray(n * 3)
    for (i in 0 until n) {
        val f = original[i]
        for (k in components.indices) {
            var s = 0f
            val v = components[k]
            for (d in 0 until dims) s += f[d] * v[d]
            positions[i * 3 + k] = s
        }
    }
    return PcaResult(positions, (explained / totalVariance).coerceIn(0f, 1f))
}

// ───────────────────────── gravity scene ─────────────────────────

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
    drawLine(primary, project(0f, 0f, 0f), tip, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
    drawCircle(tertiary, radius = 6.dp.toPx(), center = tip)
}

/** The quality report — overall agreement plus per-posture accuracy chips. */
@Composable
private fun QualityReportCard(report: com.starception.submission.shared.ml.SalahQualityReport) {
    val low = report.agreementPercent < 70
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (low) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Model agreement: ${report.agreementPercent}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${report.analyzedWindows} of ${report.totalWindows} windows analyzed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(report.perPosture.entries.toList(), key = { it.key }) { entry ->
                    FilterChip(
                        selected = entry.value >= 70,
                        onClick = { },
                        label = { Text("${entry.key}: ${entry.value}%", maxLines = 1) },
                    )
                }
            }
        }
    }
}
