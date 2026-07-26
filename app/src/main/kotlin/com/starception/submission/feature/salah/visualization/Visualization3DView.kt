package com.starception.submission.feature.salah.visualization

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.filament.Box
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.google.android.filament.VertexBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import io.github.sceneview.Scene
import io.github.sceneview.SceneScope
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberView
import com.starception.submission.ml.SalahDataSample
import com.starception.submission.ml.SalahPosture
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val MAX_SCATTER_POINTS = 420
private const val FIGURE_TRANSITION_MS = 180

private data class CameraFrame(
    val eye: Position,
    val target: Position,
)

/**
 * Compose-native Salah training visualizer backed by Google Filament through SceneView.
 *
 * The public API intentionally matches the previous renderer so the data collection
 * screen, controls, playback bar, PCA pipeline, and prediction analysis remain unchanged.
 */
@Composable
fun Visualization3DView(
    samples: List<SalahDataSample>,
    state: VisualizationState,
    onStateChange: (VisualizationState) -> Unit,
    modifier: Modifier = Modifier,
    onPlaybackTick: ((index: Int, posture: SalahPosture?, pitch: Float, roll: Float, accelMag: Float, gyroMag: Float, playing: Boolean) -> Unit)? = null,
    isFullscreen: Boolean = false,
    onFullscreenChange: ((Boolean) -> Unit)? = null,
) {
    val latestState by rememberUpdatedState(state)
    val latestOnStateChange by rememberUpdatedState(onStateChange)
    val latestOnPlaybackTick by rememberUpdatedState(onPlaybackTick)

    fun dispatchPlayback(index: Int, playing: Boolean) {
        val sample = samples.getOrNull(index) ?: return
        latestOnPlaybackTick?.invoke(
            index,
            sample.posture,
            sample.pitch,
            sample.roll,
            sample.accelMagnitude,
            sample.gyroMagnitude,
            playing,
        ) ?: latestOnStateChange(
            latestState.copy(
                playbackIndex = index,
                currentPosture = sample.posture,
                currentPitch = sample.pitch,
                currentRoll = sample.roll,
                currentAccelMag = sample.accelMagnitude,
                currentGyroMag = sample.gyroMagnitude,
                isPlaying = playing,
            ),
        )
    }

    // Filament is render-only here; playback timing remains deterministic Compose state.
    LaunchedEffect(state.isPlaying, state.playbackSpeed, samples, state.posePlaybackSource) {
        if (
            state.posePlaybackSource != PosePlaybackSource.RECORDED ||
            !state.isPlaying ||
            samples.isEmpty()
        ) return@LaunchedEffect
        if (samples.size == 1) {
            dispatchPlayback(0, false)
            return@LaunchedEffect
        }

        var index = state.playbackIndex.coerceIn(0, samples.lastIndex)
        if (index == samples.lastIndex) index = 0
        dispatchPlayback(index, true)

        while (coroutineContext.isActive && latestState.isPlaying && index < samples.lastIndex) {
            val current = samples[index]
            val next = samples[index + 1]
            val recordedInterval = (next.timestamp - current.timestamp)
                .takeIf { current.sessionId == next.sessionId && it in 20L..500L }
                ?: 100L
            val speed = latestState.playbackSpeed.coerceIn(0.5f, 50f)
            delay((recordedInterval / speed).toLong().coerceAtLeast(8L))
            index += 1
            dispatchPlayback(index, index < samples.lastIndex)
        }
    }

    // The built-in sample advances by prayer positions instead of sensor timestamps.
    // Keying on the step makes pause, skip, scrub, and speed changes take effect immediately.
    LaunchedEffect(
        state.posePlaybackSource,
        state.isTwoRakahPlaying,
        state.twoRakahStepIndex,
        state.playbackSpeed,
    ) {
        if (
            state.posePlaybackSource != PosePlaybackSource.TWO_RAKAH_SAMPLE ||
            !state.isTwoRakahPlaying
        ) return@LaunchedEffect

        val index = state.twoRakahStepIndex.coerceIn(twoRakahSample.indices)
        val step = twoRakahSample[index]
        delay(
            (step.durationMillis / state.playbackSpeed.coerceIn(0.5f, 10f))
                .toLong()
                .coerceAtLeast(120L),
        )
        if (
            latestState.posePlaybackSource == PosePlaybackSource.TWO_RAKAH_SAMPLE &&
            latestState.isTwoRakahPlaying &&
            latestState.twoRakahStepIndex == index
        ) {
            latestOnStateChange(
                latestState.copy(
                    twoRakahStepIndex = (index + 1).coerceAtMost(twoRakahSample.lastIndex),
                    isTwoRakahPlaying = index < twoRakahSample.lastIndex,
                ),
            )
        }
    }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val environment = rememberEnvironment(environmentLoader, isOpaque = false)
    val filamentView = rememberView(engine)
    val renderer = rememberRenderer(engine)
    val dualFigure = state.predictions != null &&
        state.posePlaybackSource == PosePlaybackSource.RECORDED
    val cameraFrame = remember(state.mode, dualFigure) {
        cameraFrame(state.mode, dualFigure)
    }
    // The manipulator owns the camera transform every frame. Build both from the same
    // frame so its first update cannot snap the camera back to SceneView's generic default.
    val cameraNode = key(state.mode, dualFigure, state.cameraResetToken) {
        rememberCameraNode(engine) {
            lookAt(
                eye = cameraFrame.eye,
                center = cameraFrame.target,
                up = Direction(y = 1f),
            )
            setExposure(5.6f, 1f / 100f, 180f)
        }
    }
    val cameraManipulator = key(state.mode, dualFigure, state.cameraResetToken) {
        rememberCameraManipulator(
            orbitHomePosition = cameraFrame.eye,
            targetPosition = cameraFrame.target,
        )
    }
    val mainLightNode = rememberMainLightNode(engine) {
        intensity = 90_000f
        lightDirection = Direction(-0.55f, -0.9f, -0.7f)
    }

    // Turn on the modern rendering features that the previous basic ModelBatch lacked.
    LaunchedEffect(filamentView, renderer, environment) {
        filamentView.ambientOcclusionOptions = filamentView.ambientOcclusionOptions.apply {
            enabled = true
        }
        filamentView.bloomOptions = filamentView.bloomOptions.apply {
            enabled = false
            strength = 0f
        }
        filamentView.multiSampleAntiAliasingOptions =
            filamentView.multiSampleAntiAliasingOptions.apply {
                enabled = true
                sampleCount = 4
        }
        filamentView.setShadowingEnabled(true)
        environment.indirectLight?.intensity = 45_000f
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = floatArrayOf(0f, 0f, 0f, 0f)
        }
    }

    Box(
        modifier = modifier
            .clip(if (isFullscreen) RectangleShape else RoundedCornerShape(16.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF18332F),
                        Color(0xFF10191B),
                        Color(0xFF080D10),
                    ),
                    radius = 1_150f,
                ),
            ),
    ) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            surfaceType = SurfaceType.TextureSurface,
            engine = engine,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            environment = environment,
            view = filamentView,
            renderer = renderer,
            mainLightNode = mainLightNode,
            cameraNode = cameraNode,
            cameraManipulator = cameraManipulator,
            isOpaque = false,
        ) {
            val unlitColorMaterial = remember(materialLoader) {
                materialLoader.createMaterial("materials/salah_unlit_color.filamat")
            }
            val palette = rememberPostureMaterials(materialLoader)
            val gridMaterial = remember(materialLoader, unlitColorMaterial) {
                materialLoader.createUnlitColorInstance(
                    unlitColorMaterial,
                    0.08f,
                    0.30f,
                    0.27f,
                )
            }
            val highlightMaterial = remember(materialLoader) {
                materialLoader.createColorInstance(
                    color = Color(0xFFFFE08A),
                    metallic = 0.35f,
                    roughness = 0.22f,
                    reflectance = 0.9f,
                )
            }

            when (state.mode) {
                VisualizationMode.SCATTER, VisualizationMode.FEATURE_PCA -> ScatterScene(
                    samples = samples,
                    state = state,
                    palette = palette,
                    gridMaterial = gridMaterial,
                    highlightMaterial = highlightMaterial,
                )
                VisualizationMode.GRAVITY_VECTOR -> GravityScene(
                    samples = samples,
                    visiblePostures = state.visiblePostures,
                    palette = palette,
                    gridMaterial = gridMaterial,
                )
                VisualizationMode.PHONE_MODEL -> HumanoidScene(
                    engine = engine,
                    samples = samples,
                    state = state,
                    materialLoader = materialLoader,
                    unlitColorMaterial = unlitColorMaterial,
                    gridMaterial = gridMaterial,
                )
            }
        }

        if (isFullscreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(132.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    ),
            )
        }

        if (state.mode == VisualizationMode.PHONE_MODEL) {
            val twoRakahStep = if (
                state.posePlaybackSource == PosePlaybackSource.TWO_RAKAH_SAMPLE
            ) state.currentTwoRakahStep() else null
            val recorded = twoRakahStep?.posture ?: samples.getOrNull(state.playbackIndex)?.posture
            val prediction = if (twoRakahStep == null) {
                state.predictions?.getOrNull(state.playbackIndex)
            } else {
                null
            }
            if (isFullscreen) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 16.dp, top = 14.dp, end = 72.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xE6111A1B),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    shadowElevation = 8.dp,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                        Text(
                            text = if (twoRakahStep != null) {
                                "2 RAK'AH SAMPLE · STEP ${state.twoRakahStepIndex + 1} OF ${twoRakahSample.size}"
                            } else {
                                "3D POSE REVIEW"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF62E2C2),
                        )
                        Text(
                            text = if (twoRakahStep != null) {
                                "Rak'ah ${twoRakahStep.rakah} · ${twoRakahStep.label}"
                            } else {
                                recorded?.displayName ?: "No pose selected"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF1F7F4),
                        )
                        if (dualFigure) {
                            Text(
                                text = if (prediction?.predicted == null) {
                                    "Model prediction unavailable"
                                } else {
                                    "Model · ${prediction.predicted.displayName} ${(prediction.confidence * 100).toInt()}%"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (prediction?.predicted != null && prediction.predicted != recorded) {
                                    Color(0xFFFF8A9B)
                                } else {
                                    Color(0xFFACBBB5)
                                },
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(
                            start = 12.dp,
                            top = 10.dp,
                            end = if (onFullscreenChange != null) 58.dp else 12.dp,
                            bottom = 10.dp,
                        ),
                ) {
                    PoseLegendChip(
                        text = if (twoRakahStep != null) {
                            "RAK'AH ${twoRakahStep.rakah} · ${twoRakahStep.label.uppercase()}"
                        } else {
                            "LABEL · ${recorded?.displayName ?: "—"}"
                        },
                        isError = false,
                        modifier = Modifier.weight(1f),
                    )
                    if (dualFigure) {
                        PoseLegendChip(
                            text = if (prediction?.predicted == null) {
                                "MODEL · —"
                            } else {
                                "MODEL · ${prediction.predicted.displayName} ${(prediction.confidence * 100).toInt()}%"
                            },
                            isError = prediction?.predicted != null && prediction.predicted != recorded,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            FilledIconButton(
                onClick = {
                    onStateChange(state.copy(cameraResetToken = state.cameraResetToken + 1))
                },
                modifier = Modifier
                    .align(if (isFullscreen) Alignment.CenterEnd else Alignment.BottomEnd)
                    .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                    .padding(if (isFullscreen) 16.dp else 10.dp)
                    .size(if (isFullscreen) 42.dp else 40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xE6111A1B),
                    contentColor = Color(0xFFF1F7F4),
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "Reset camera",
                )
            }
        }

        onFullscreenChange?.let { changeFullscreen ->
            FilledIconButton(
                onClick = { changeFullscreen(!isFullscreen) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                    .padding(if (isFullscreen) 16.dp else 10.dp)
                    .size(if (isFullscreen) 42.dp else 40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xE6111A1B),
                    contentColor = Color(0xFFF1F7F4),
                ),
            ) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                )
            }
        }
    }
}

private fun cameraFrame(mode: VisualizationMode, dualFigure: Boolean): CameraFrame = when (mode) {
    VisualizationMode.PHONE_MODEL -> if (dualFigure) {
        CameraFrame(
            eye = Position(2.75f, 1.85f, 4.25f),
            target = Position(0f, 0.8f, 0f),
        )
    } else {
        CameraFrame(
            eye = Position(1.65f, 1.5f, 2.75f),
            target = Position(0f, 0.82f, 0f),
        )
    }
    VisualizationMode.GRAVITY_VECTOR -> CameraFrame(
        eye = Position(2.9f, 2.2f, 3.25f),
        target = Position(0f, 0f, 0f),
    )
    VisualizationMode.SCATTER, VisualizationMode.FEATURE_PCA -> CameraFrame(
        eye = Position(3.0f, 2.45f, 3.45f),
        target = Position(0f, 0f, 0f),
    )
}

@Composable
private fun SceneScope.rememberPostureMaterials(
    materialLoader: io.github.sceneview.loaders.MaterialLoader,
): Map<SalahPosture, MaterialInstance> = remember(materialLoader) {
    SalahPosture.entries.associateWith { posture ->
        materialLoader.createColorInstance(
            color = postureColor(posture),
            metallic = 0.3f,
            roughness = 0.28f,
            reflectance = 0.8f,
        )
    }
}

private data class PlotPoint(
    val originalIndex: Int,
    val posture: SalahPosture,
    val position: Position,
    val flagged: Boolean,
)

@Composable
private fun SceneScope.ScatterScene(
    samples: List<SalahDataSample>,
    state: VisualizationState,
    palette: Map<SalahPosture, MaterialInstance>,
    gridMaterial: MaterialInstance,
    highlightMaterial: MaterialInstance,
) {
    val allPoints = remember(
        samples,
        state.visiblePostures,
        state.axisX,
        state.axisY,
        state.axisZ,
        state.mode,
        state.pcaPositions,
        state.flaggedIndices,
        state.showDisagreements,
    ) {
        normalizedPlotPoints(samples, state)
    }
    val renderPoints = remember(allPoints) {
        if (allPoints.size <= MAX_SCATTER_POINTS) {
            allPoints
        } else {
            val stride = ceil(allPoints.size / MAX_SCATTER_POINTS.toFloat()).toInt()
            allPoints.filterIndexed { index, _ -> index % stride == 0 }
        }
    }
    val flaggedMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = Color(0xFFFF4D6D),
            metallic = 0.2f,
            roughness = 0.18f,
            reflectance = 1f,
        )
    }
    val pointRadius = (0.018f + state.pointSize.coerceIn(1f, 10f) * 0.006f)

    ScientificGrid(gridMaterial)
    renderPoints.forEach { point ->
        key(point.originalIndex) {
            SphereNode(
                radius = if (point.flagged) pointRadius * 1.5f else pointRadius,
                stacks = 8,
                slices = 12,
                materialInstance = if (point.flagged) flaggedMaterial else palette[point.posture],
                position = point.position,
            )
        }
    }

    if (state.showEllipsoids) {
        allPoints.groupBy { it.posture }.forEach { (posture, points) ->
            if (points.size < 3) return@forEach
            val mean = Position(
                points.map { it.position.x }.average().toFloat(),
                points.map { it.position.y }.average().toFloat(),
                points.map { it.position.z }.average().toFloat(),
            )
            val spread = Scale(
                standardDeviation(points.map { it.position.x }).coerceAtLeast(0.06f) * 1.6f,
                standardDeviation(points.map { it.position.y }).coerceAtLeast(0.06f) * 1.6f,
                standardDeviation(points.map { it.position.z }).coerceAtLeast(0.06f) * 1.6f,
            )
            val cloudMaterial = remember(materialLoader, posture) {
                materialLoader.createColorInstance(
                    postureColor(posture).copy(alpha = 0.12f),
                    metallic = 0f,
                    roughness = 0.9f,
                    reflectance = 0.1f,
                )
            }
            Node(position = mean, scale = spread) {
                SphereNode(
                    radius = 1f,
                    stacks = 16,
                    slices = 24,
                    materialInstance = cloudMaterial,
                )
            }
        }
    }

    allPoints.firstOrNull { it.originalIndex == state.playbackIndex }?.let { highlighted ->
        SphereNode(
            radius = pointRadius * 2.15f,
            stacks = 14,
            slices = 20,
            materialInstance = highlightMaterial,
            position = highlighted.position,
        )
    }
}

@Composable
private fun SceneScope.ScientificGrid(material: MaterialInstance) {
    for (step in -3..3) {
        val coordinate = step * 0.5f
        LineNode(
            start = Position(-1.5f, 0f, coordinate),
            end = Position(1.5f, 0f, coordinate),
            materialInstance = material,
        )
        LineNode(
            start = Position(coordinate, 0f, -1.5f),
            end = Position(coordinate, 0f, 1.5f),
            materialInstance = material,
        )
    }
    LineNode(Position(-1.65f, 0f, 0f), Position(1.65f, 0f, 0f), material)
    LineNode(Position(0f, -1.65f, 0f), Position(0f, 1.65f, 0f), material)
    LineNode(Position(0f, 0f, -1.65f), Position(0f, 0f, 1.65f), material)
}

@Composable
private fun SceneScope.GravityScene(
    samples: List<SalahDataSample>,
    visiblePostures: Set<SalahPosture>,
    palette: Map<SalahPosture, MaterialInstance>,
    gridMaterial: MaterialInstance,
) {
    val vectors = remember(samples, visiblePostures) {
        samples
            .filter { it.posture in visiblePostures }
            .groupBy { it.posture }
            .mapNotNull { (posture, group) ->
                val rawX = group.map { it.meanAccelX }.average().toFloat()
                val rawY = group.map { it.meanAccelY }.average().toFloat()
                val rawZ = group.map { it.meanAccelZ }.average().toFloat()
                val magnitude = sqrt(rawX * rawX + rawY * rawY + rawZ * rawZ)
                if (magnitude < 0.1f) null else posture to Position(
                    rawX / magnitude * 1.35f,
                    rawY / magnitude * 1.35f,
                    rawZ / magnitude * 1.35f,
                )
            }
    }

    ScientificGrid(gridMaterial)
    // Three great circles form a restrained 1g reference shell.
    repeat(3) { ring ->
        val points = (0..64).map { i ->
            val angle = i / 64f * PI.toFloat() * 2f
            when (ring) {
                0 -> Position(cos(angle) * 1.45f, sin(angle) * 1.45f, 0f)
                1 -> Position(cos(angle) * 1.45f, 0f, sin(angle) * 1.45f)
                else -> Position(0f, cos(angle) * 1.45f, sin(angle) * 1.45f)
            }
        }
        PathNode(points = points, closed = true, materialInstance = gridMaterial)
    }

    vectors.forEach { (posture, end) ->
        val material = palette.getValue(posture)
        BeadedSegment(
            from = Position(0f, 0f, 0f),
            to = end,
            material = material,
            radius = 0.045f,
            beadCount = 22,
        )
        SphereNode(
            radius = 0.11f,
            stacks = 12,
            slices = 18,
            materialInstance = material,
            position = end,
        )
    }
}

@Composable
private fun SceneScope.HumanoidScene(
    engine: Engine,
    samples: List<SalahDataSample>,
    state: VisualizationState,
    materialLoader: io.github.sceneview.loaders.MaterialLoader,
    unlitColorMaterial: Material,
    gridMaterial: MaterialInstance,
) {
    val sample = samples.getOrNull(state.playbackIndex)
    val twoRakahStep = if (
        state.posePlaybackSource == PosePlaybackSource.TWO_RAKAH_SAMPLE
    ) state.currentTwoRakahStep() else null
    val recorded = twoRakahStep?.posture ?: sample?.posture ?: SalahPosture.QIYAM
    val prediction = if (twoRakahStep == null) {
        state.predictions?.getOrNull(state.playbackIndex)
    } else {
        null
    }
    val dual = state.predictions != null && twoRakahStep == null
    val disagrees = prediction?.predicted != null && prediction.predicted != recorded

    // The figure uses a shaded material (multiplies base colour by the baked per-vertex shade);
    // the stage/grid keep the plain unlit material since they carry no vertex colours.
    val shadedColorMaterial = remember(materialLoader) {
        materialLoader.createMaterial("materials/salah_shaded_color.filamat")
    }
    val truthMaterial = remember(materialLoader, shadedColorMaterial) {
        materialLoader.createUnlitColorInstance(shadedColorMaterial, 0.09f, 0.85f, 0.66f)
    }
    val predictionGood = remember(materialLoader, shadedColorMaterial) {
        materialLoader.createUnlitColorInstance(shadedColorMaterial, 0.45f, 0.92f, 0.28f)
    }
    val predictionBad = remember(materialLoader, shadedColorMaterial) {
        materialLoader.createUnlitColorInstance(shadedColorMaterial, 1f, 0.28f, 0.42f)
    }
    val matMaterial = remember(materialLoader, unlitColorMaterial) {
        materialLoader.createUnlitColorInstance(unlitColorMaterial, 0.035f, 0.12f, 0.10f)
    }
    val stageMaterial = remember(materialLoader, unlitColorMaterial) {
        materialLoader.createUnlitColorInstance(unlitColorMaterial, 0.025f, 0.04f, 0.05f)
    }

    CubeNode(
        size = Size(if (dual) 2.6f else 1.45f, 0.035f, 2.15f),
        materialInstance = stageMaterial,
        position = Position(0f, -0.08f, 0f),
    )
    CubeNode(
        size = Size(if (dual) 2.35f else 1.15f, 0.022f, 1.9f),
        materialInstance = matMaterial,
        position = Position(0f, -0.045f, -0.05f),
    )
    // Subtle orbital guides make the stage read as a live digital twin.
    repeat(3) { ring ->
        val radiusX = (if (dual) 1.35f else 0.75f) + ring * 0.08f
        val radiusZ = 1.03f + ring * 0.06f
        val points = (0..72).map { i ->
            val angle = i / 72f * PI.toFloat() * 2f
            Position(cos(angle) * radiusX, -0.025f, sin(angle) * radiusZ)
        }
        PathNode(points = points, closed = true, materialInstance = gridMaterial)
    }

    HolographicHumanoid(
        engine = engine,
        pose = skeletonPose(recorded, if (dual) -0.58f else 0f),
        bodyShapeStyle = state.bodyShapeStyle,
        material = truthMaterial,
    )

    if (dual) {
        val predictedPosture = prediction?.predicted ?: recorded
        val predictionMaterial = if (disagrees) predictionBad else predictionGood
        key(disagrees) {
            HolographicHumanoid(
                engine = engine,
                pose = skeletonPose(predictedPosture, 0.58f),
                bodyShapeStyle = state.bodyShapeStyle,
                material = predictionMaterial,
            )
        }
    }
}

private fun io.github.sceneview.loaders.MaterialLoader.createUnlitColorInstance(
    material: Material,
    red: Float,
    green: Float,
    blue: Float,
): MaterialInstance {
    return createInstance(material).apply {
        setParameter(
            "color",
            Colors.RgbaType.SRGB,
            red,
            green,
            blue,
            1f,
        )
    }
}

private enum class Joint {
    HEAD, NECK,
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE,
    LEFT_ANKLE, RIGHT_ANKLE,
    LEFT_TOE, RIGHT_TOE,
}

private data class SkeletonPose(val joints: Map<Joint, Position>)

@Composable
private fun SceneScope.HolographicHumanoid(
    engine: Engine,
    pose: SkeletonPose,
    bodyShapeStyle: BodyShapeStyle,
    material: MaterialInstance,
) {
    val animated = buildMap {
        pose.joints.forEach { (joint, target) ->
            val x by animateFloatAsState(
                targetValue = target.x,
                animationSpec = tween(FIGURE_TRANSITION_MS),
                label = "${joint.name}-x",
            )
            val y by animateFloatAsState(
                targetValue = target.y,
                animationSpec = tween(FIGURE_TRANSITION_MS),
                label = "${joint.name}-y",
            )
            val z by animateFloatAsState(
                targetValue = target.z,
                animationSpec = tween(FIGURE_TRANSITION_MS),
                label = "${joint.name}-z",
            )
            put(joint, Position(x, y, z))
        }
    }

    // Build the whole figure as ONE custom mesh we hand to Filament directly (buildFigureMesh).
    // SceneView's SphereNode/CylinderNode render flattened or hollow in this build, so instead
    // each body segment is a real capsule (cylinder + hemispherical caps) and each joint a
    // sphere, all fused into a single solid, continuous mannequin.
    fun j(joint: Joint) = animated.getValue(joint)
    // A distinct capsule for one bone, inset from both joints so neighbouring parts stay
    // visually separate (articulated) instead of fusing into a single blob.
    fun bone(a: Joint, b: Joint, radius: Float, inset: Float = 0.18f): CapsuleSpec {
        val pa = j(a)
        val pb = j(b)
        return CapsuleSpec(lerp(pa, pb, inset), lerp(pa, pb, 1f - inset), radius)
    }
    val head = j(Joint.HEAD)
    val neck = j(Joint.NECK)
    val shoulderCenter = lerp(j(Joint.LEFT_SHOULDER), j(Joint.RIGHT_SHOULDER), 0.5f)
    val hipCenter = lerp(j(Joint.LEFT_HIP), j(Joint.RIGHT_HIP), 0.5f)
    val torsoSplit = lerp(shoulderCenter, hipCenter, 0.5f)
    val proportions = bodyMeasurements(animated).toFeatures()
    val synthesizedShape = ShapeProportionModel.synthesize(proportions, bodyShapeStyle)
    val shoulderBreadth = distance(j(Joint.LEFT_SHOULDER), j(Joint.RIGHT_SHOULDER))
    val hipBreadth = distance(j(Joint.LEFT_HIP), j(Joint.RIGHT_HIP))
    val bodyWidthDirection = normalizedDirection(
        j(Joint.LEFT_SHOULDER),
        j(Joint.RIGHT_SHOULDER),
    )

    val specs = buildList<FigurePartSpec> {
        // A vertical oval is closer to the paper's annotated head than a plain sphere.
        add(
            CapsuleSpec(
                Position(head.x, head.y - 0.045f, head.z),
                Position(head.x, head.y + 0.045f, head.z),
                0.125f,
            ),
        )
        add(CapsuleSpec(lerp(neck, head, 0.18f), lerp(neck, head, 0.58f), 0.05f))

        // The PDF's central idea: torso and abdomen are independent primitive-shape blends.
        // These are true 3D superellipse/frustum volumes, not fixed-radius limb capsules.
        add(
            ShapeVolumeSpec(
                from = lerp(shoulderCenter, torsoSplit, 0.06f),
                to = lerp(shoulderCenter, torsoSplit, 0.94f),
                widthDirection = bodyWidthDirection,
                fromHalfWidth = shoulderBreadth * 0.44f * synthesizedShape.torsoWidthScale,
                toHalfWidth = hipBreadth * 0.50f * synthesizedShape.abdomenWidthScale,
                halfDepth = 0.115f * synthesizedShape.depthScale,
                weights = synthesizedShape.torsoWeights,
                triangleWideAtStart = true,
            ),
        )
        add(
            ShapeVolumeSpec(
                from = lerp(torsoSplit, hipCenter, 0.08f),
                to = lerp(torsoSplit, hipCenter, 0.96f),
                widthDirection = bodyWidthDirection,
                fromHalfWidth = hipBreadth * 0.50f * synthesizedShape.abdomenWidthScale,
                toHalfWidth = hipBreadth * 0.56f * synthesizedShape.abdomenWidthScale,
                halfDepth = 0.105f * synthesizedShape.depthScale,
                weights = synthesizedShape.abdomenWeights,
                triangleWideAtStart = false,
            ),
        )
        // Thin shoulder + hip girdles keep the figure's width, inset to stay distinct.
        add(CapsuleSpec(lerp(j(Joint.LEFT_SHOULDER), j(Joint.RIGHT_SHOULDER), 0.22f), lerp(j(Joint.LEFT_SHOULDER), j(Joint.RIGHT_SHOULDER), 0.78f), 0.055f))
        add(CapsuleSpec(lerp(j(Joint.LEFT_HIP), j(Joint.RIGHT_HIP), 0.18f), lerp(j(Joint.LEFT_HIP), j(Joint.RIGHT_HIP), 0.82f), 0.07f))
        // Arms: distinct upper arm and forearm ovals.
        add(bone(Joint.LEFT_SHOULDER, Joint.LEFT_ELBOW, 0.072f))
        add(bone(Joint.RIGHT_SHOULDER, Joint.RIGHT_ELBOW, 0.072f))
        add(bone(Joint.LEFT_ELBOW, Joint.LEFT_WRIST, 0.056f))
        add(bone(Joint.RIGHT_ELBOW, Joint.RIGHT_WRIST, 0.056f))
        // Hands.
        add(CapsuleSpec(lerp(j(Joint.LEFT_WRIST), extendFrom(j(Joint.LEFT_ELBOW), j(Joint.LEFT_WRIST), 0.075f), 0.35f), extendFrom(j(Joint.LEFT_ELBOW), j(Joint.LEFT_WRIST), 0.075f), 0.048f))
        add(CapsuleSpec(lerp(j(Joint.RIGHT_WRIST), extendFrom(j(Joint.RIGHT_ELBOW), j(Joint.RIGHT_WRIST), 0.075f), 0.35f), extendFrom(j(Joint.RIGHT_ELBOW), j(Joint.RIGHT_WRIST), 0.075f), 0.048f))
        // Legs: distinct thigh and calf ovals.
        add(bone(Joint.LEFT_HIP, Joint.LEFT_KNEE, 0.098f))
        add(bone(Joint.RIGHT_HIP, Joint.RIGHT_KNEE, 0.098f))
        add(bone(Joint.LEFT_KNEE, Joint.LEFT_ANKLE, 0.07f))
        add(bone(Joint.RIGHT_KNEE, Joint.RIGHT_ANKLE, 0.07f))
        // Feet.
        add(bone(Joint.LEFT_ANKLE, Joint.LEFT_TOE, 0.05f, 0.1f))
        add(bone(Joint.RIGHT_ANKLE, Joint.RIGHT_TOE, 0.05f, 0.1f))
        // Small joint spheres (connector circles) at each articulation.
        listOf(
            Joint.LEFT_SHOULDER to 0.058f, Joint.RIGHT_SHOULDER to 0.058f,
            Joint.LEFT_ELBOW to 0.048f, Joint.RIGHT_ELBOW to 0.048f,
            Joint.LEFT_HIP to 0.07f, Joint.RIGHT_HIP to 0.07f,
            Joint.LEFT_KNEE to 0.058f, Joint.RIGHT_KNEE to 0.058f,
            Joint.LEFT_ANKLE to 0.048f, Joint.RIGHT_ANKLE to 0.048f,
        ).forEach { (joint, r) -> add(CapsuleSpec(j(joint), j(joint), r)) }
    }

    FigureMeshNode(engine = engine, specs = specs, material = material)
}

private sealed interface FigurePartSpec

private data class CapsuleSpec(
    val from: Position,
    val to: Position,
    val radius: Float,
) : FigurePartSpec

private data class ShapeVolumeSpec(
    val from: Position,
    val to: Position,
    val widthDirection: Position,
    val fromHalfWidth: Float,
    val toHalfWidth: Float,
    val halfDepth: Float,
    val weights: PrimitiveShapeWeights,
    val triangleWideAtStart: Boolean,
) : FigurePartSpec

private class FigureMesh(
    val vertexBuffer: VertexBuffer,
    val indexBuffer: IndexBuffer,
    val boundingBox: Box,
    val indexCount: Int,
)

@Composable
private fun SceneScope.FigureMeshNode(
    engine: Engine,
    specs: List<FigurePartSpec>,
    material: MaterialInstance,
) {
    // Rebuild the mesh only when the pose (specs) changes; dispose the GPU buffers when it does.
    val mesh = remember(specs) { buildFigureMesh(engine, specs) }
    DisposableEffect(mesh) {
        onDispose {
            engine.destroyVertexBuffer(mesh.vertexBuffer)
            engine.destroyIndexBuffer(mesh.indexBuffer)
        }
    }
    MeshNode(
        RenderableManager.PrimitiveType.TRIANGLES,
        mesh.vertexBuffer,
        mesh.indexBuffer,
        mesh.boundingBox,
        material,
    )
}

private fun buildFigureMesh(engine: Engine, specs: List<FigurePartSpec>): FigureMesh {
    val positions = ArrayList<Float>(16384)
    val colors = ArrayList<Float>(16384)
    val indices = ArrayList<Int>(32768)
    specs.forEach { spec ->
        when (spec) {
            is CapsuleSpec -> appendCapsule(
                positions,
                colors,
                indices,
                spec.from,
                spec.to,
                spec.radius,
            )
            is ShapeVolumeSpec -> appendShapeVolume(positions, colors, indices, spec)
        }
    }

    val positionBuffer = ByteBuffer
        .allocateDirect(positions.size * 4)
        .order(ByteOrder.nativeOrder())
    positions.forEach { positionBuffer.putFloat(it) }
    positionBuffer.flip()

    val colorBuffer = ByteBuffer
        .allocateDirect(colors.size * 4)
        .order(ByteOrder.nativeOrder())
    colors.forEach { colorBuffer.putFloat(it) }
    colorBuffer.flip()

    val indexBufferData = ByteBuffer
        .allocateDirect(indices.size * 4)
        .order(ByteOrder.nativeOrder())
    indices.forEach { indexBufferData.putInt(it) }
    indexBufferData.flip()

    val vertexBuffer = VertexBuffer.Builder()
        .bufferCount(2)
        .vertexCount(positions.size / 3)
        .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
        .attribute(VertexBuffer.VertexAttribute.COLOR, 1, VertexBuffer.AttributeType.FLOAT4, 0, 16)
        .build(engine)
    vertexBuffer.setBufferAt(engine, 0, positionBuffer)
    vertexBuffer.setBufferAt(engine, 1, colorBuffer)

    val indexBuffer = IndexBuffer.Builder()
        .indexCount(indices.size)
        .bufferType(IndexBuffer.Builder.IndexType.UINT)
        .build(engine)
    indexBuffer.setBuffer(engine, indexBufferData)

    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
    var i = 0
    while (i < positions.size) {
        val x = positions[i]; val y = positions[i + 1]; val z = positions[i + 2]
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (z < minZ) minZ = z
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
        if (z > maxZ) maxZ = z
        i += 3
    }
    val box = Box(
        (minX + maxX) * 0.5f, (minY + maxY) * 0.5f, (minZ + maxZ) * 0.5f,
        (maxX - minX) * 0.5f + 0.02f, (maxY - minY) * 0.5f + 0.02f, (maxZ - minZ) * 0.5f + 0.02f,
    )
    return FigureMesh(vertexBuffer, indexBuffer, box, indices.size)
}

/**
 * Builds a closed torso/abdomen volume whose silhouette is a continuous blend of the paper's
 * circle, square, and triangle primitives. Circle controls mid-body fullness, square controls
 * superellipse corner strength, and triangle controls the top-to-bottom taper.
 */
private fun appendShapeVolume(
    positions: MutableList<Float>,
    colors: MutableList<Float>,
    indices: MutableList<Int>,
    spec: ShapeVolumeSpec,
    lengthSegments: Int = 10,
    radialSegments: Int = 40,
) {
    val axis = normalizedDirection(spec.from, spec.to)
    val widthDotAxis = dot(spec.widthDirection, axis)
    val projectedWidth = Position(
        spec.widthDirection.x - widthDotAxis * axis.x,
        spec.widthDirection.y - widthDotAxis * axis.y,
        spec.widthDirection.z - widthDotAxis * axis.z,
    )
    val width = normalize(projectedWidth, fallback = Position(1f, 0f, 0f))
    val depth = normalize(cross(axis, width), fallback = Position(0f, 0f, 1f))
    val exponent = 2f + 6.5f * spec.weights.square
    val curvePower = 2f / exponent
    val ringStarts = ArrayList<Int>(lengthSegments + 1)

    val light = normalize(Position(0.428f, 0.771f, 0.471f), Position(0f, 1f, 0f))
    fun addColor(nx: Float, ny: Float, nz: Float) {
        val normalLength = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
        val nDotL = (nx * light.x + ny * light.y + nz * light.z) / normalLength
        val shade = 0.4f + 0.6f * (0.5f * nDotL + 0.5f)
        colors.add(shade); colors.add(shade); colors.add(shade); colors.add(1f)
    }

    for (ring in 0..lengthSegments) {
        val t = ring.toFloat() / lengthSegments
        val center = lerp(spec.from, spec.to, t)
        val middle = sin(PI.toFloat() * t)
        val triangleDirection = if (spec.triangleWideAtStart) 1f - 2f * t else 2f * t - 1f
        val triangleTaper = 1f + 0.16f * spec.weights.triangle * triangleDirection
        val circleBulge = 1f + 0.13f * spec.weights.circle * middle
        val halfWidth = lerp(spec.fromHalfWidth, spec.toHalfWidth, t) *
            triangleTaper * circleBulge
        val halfDepth = spec.halfDepth *
            (1f + 0.10f * spec.weights.circle * middle) *
            (1f - 0.035f * spec.weights.triangle * abs(triangleDirection))

        ringStarts.add(positions.size / 3)
        for (segment in 0 until radialSegments) {
            val angle = segment.toFloat() / radialSegments * PI.toFloat() * 2f
            val cosAngle = cos(angle)
            val sinAngle = sin(angle)
            val shapeX = signedPower(cosAngle, curvePower)
            val shapeZ = signedPower(sinAngle, curvePower)
            val x = shapeX * halfWidth
            val z = shapeZ * halfDepth

            positions.add(center.x + width.x * x + depth.x * z)
            positions.add(center.y + width.y * x + depth.y * z)
            positions.add(center.z + width.z * x + depth.z * z)

            // Gradient of the corresponding ellipse is a stable shading approximation for a
            // superellipse and avoids adding another vertex stream solely for normals.
            addColor(
                width.x * shapeX / halfWidth + depth.x * shapeZ / halfDepth,
                width.y * shapeX / halfWidth + depth.y * shapeZ / halfDepth,
                width.z * shapeX / halfWidth + depth.z * shapeZ / halfDepth,
            )
        }
    }

    for (ring in 0 until lengthSegments) {
        val current = ringStarts[ring]
        val next = ringStarts[ring + 1]
        for (segment in 0 until radialSegments) {
            val following = (segment + 1) % radialSegments
            val a = current + segment
            val b = current + following
            val c = next + following
            val d = next + segment
            indices.add(a); indices.add(b); indices.add(d)
            indices.add(b); indices.add(c); indices.add(d)
        }
    }

    fun cap(center: Position, ringStart: Int, normalScale: Float, reverse: Boolean) {
        val centerIndex = positions.size / 3
        positions.add(center.x); positions.add(center.y); positions.add(center.z)
        addColor(axis.x * normalScale, axis.y * normalScale, axis.z * normalScale)
        for (segment in 0 until radialSegments) {
            val next = (segment + 1) % radialSegments
            if (reverse) {
                indices.add(centerIndex); indices.add(ringStart + next); indices.add(ringStart + segment)
            } else {
                indices.add(centerIndex); indices.add(ringStart + segment); indices.add(ringStart + next)
            }
        }
    }
    cap(spec.from, ringStarts.first(), -1f, reverse = true)
    cap(spec.to, ringStarts.last(), 1f, reverse = false)
}

// Appends a capsule (cylinder + two hemispherical caps) from `from` to `to` in world space,
// writing a per-vertex surface normal baked into a soft light-to-dark shade (vertex colour) so
// the figure reads as a smooth rounded volume. When from == to the caps meet to form a plain
// sphere (used for joints and the head).
private fun appendCapsule(
    positions: MutableList<Float>,
    colors: MutableList<Float>,
    indices: MutableList<Int>,
    from: Position,
    to: Position,
    radius: Float,
    radialSegments: Int = 36,
    capRings: Int = 14,
) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val dz = to.z - from.z
    val len = sqrt(dx * dx + dy * dy + dz * dz)
    val ax: Float
    val ay: Float
    val az: Float
    if (len < 1e-5f) {
        ax = 0f; ay = 1f; az = 0f
    } else {
        ax = dx / len; ay = dy / len; az = dz / len
    }

    // Orthonormal basis (u, v) perpendicular to the capsule axis.
    val hx: Float
    val hy: Float
    val hz: Float
    if (kotlin.math.abs(ay) < 0.99f) {
        hx = 0f; hy = 1f; hz = 0f
    } else {
        hx = 1f; hy = 0f; hz = 0f
    }
    var ux = ay * hz - az * hy
    var uy = az * hx - ax * hz
    var uz = ax * hy - ay * hx
    val ul = sqrt(ux * ux + uy * uy + uz * uz).coerceAtLeast(1e-5f)
    ux /= ul; uy /= ul; uz /= ul
    val vx = ay * uz - az * uy
    val vy = az * ux - ax * uz
    val vz = ax * uy - ay * ux

    // Fixed key-light direction (world space), pre-dotted with the basis so shading is a smooth
    // half-lambert gradient computed once per vertex — independent of the scene's exposure.
    val lx = 0.428f
    val ly = 0.771f
    val lz = 0.471f
    val axisDotL = ax * lx + ay * ly + az * lz
    val uDotL = ux * lx + uy * ly + uz * lz
    val vDotL = vx * lx + vy * ly + vz * lz

    val ringStart = ArrayList<Int>(2 * (capRings + 1))

    // na/nr are the axial and radial components of the outward surface normal for this ring.
    fun addRing(cx: Float, cy: Float, cz: Float, rr: Float, na: Float, nr: Float) {
        ringStart.add(positions.size / 3)
        for (s in 0 until radialSegments) {
            val phi = s.toFloat() / radialSegments * (PI.toFloat() * 2f)
            val cphi = cos(phi)
            val sphi = sin(phi)
            positions.add(cx + rr * (cphi * ux + sphi * vx))
            positions.add(cy + rr * (cphi * uy + sphi * vy))
            positions.add(cz + rr * (cphi * uz + sphi * vz))
            val nDotL = na * axisDotL + nr * (cphi * uDotL + sphi * vDotL)
            val shade = 0.4f + 0.6f * (0.5f * nDotL + 0.5f)
            colors.add(shade); colors.add(shade); colors.add(shade); colors.add(1f)
        }
    }

    // Bottom hemisphere: pole beyond `from` up to the equator ring centred on `from`.
    for (k in 0..capRings) {
        val ang = k.toFloat() / capRings * (PI.toFloat() / 2f)
        val cosA = cos(ang)
        val sinA = sin(ang)
        addRing(from.x - radius * cosA * ax, from.y - radius * cosA * ay, from.z - radius * cosA * az, radius * sinA, -cosA, sinA)
    }
    // Top hemisphere: equator ring centred on `to` up to the pole beyond `to`.
    for (k in 0..capRings) {
        val ang = k.toFloat() / capRings * (PI.toFloat() / 2f)
        val cosA = cos(ang)
        val sinA = sin(ang)
        addRing(to.x + radius * sinA * ax, to.y + radius * sinA * ay, to.z + radius * sinA * az, radius * cosA, sinA, cosA)
    }

    // Stitch consecutive rings into triangles (material is double-sided, so winding is free).
    for (r in 0 until ringStart.size - 1) {
        val a0 = ringStart[r]
        val b0 = ringStart[r + 1]
        for (s in 0 until radialSegments) {
            val s2 = (s + 1) % radialSegments
            val a = a0 + s
            val b = a0 + s2
            val c = b0 + s2
            val d = b0 + s
            indices.add(a); indices.add(b); indices.add(d)
            indices.add(b); indices.add(c); indices.add(d)
        }
    }
}

private fun segmentRotation(from: Position, to: Position): Rotation {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val dz = to.z - from.z
    val length = sqrt(dx * dx + dy * dy + dz * dz)
    if (length <= 0.0001f) return Rotation(x = 0f)

    // Quaternion rotating the cylinder's local +Y axis onto the body-part direction.
    val x = dx / length
    val y = dy / length
    val z = dz / length
    if (y < -0.9999f) return dev.romainguy.kotlin.math.Quaternion(1f, 0f, 0f, 0f).toEulerAngles()
    val qx = z
    val qy = 0f
    val qz = -x
    val qw = 1f + y
    val magnitude = sqrt(qx * qx + qy * qy + qz * qz + qw * qw)
    return dev.romainguy.kotlin.math.Quaternion(
        qx / magnitude,
        qy,
        qz / magnitude,
        qw / magnitude,
    ).toEulerAngles()
}

private fun distance(from: Position, to: Position): Float {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val dz = to.z - from.z
    return sqrt(dx * dx + dy * dy + dz * dz)
}

private fun normalizedDirection(from: Position, to: Position): Position = normalize(
    Position(to.x - from.x, to.y - from.y, to.z - from.z),
    fallback = Position(0f, 1f, 0f),
)

private fun normalize(vector: Position, fallback: Position): Position {
    val length = sqrt(vector.x * vector.x + vector.y * vector.y + vector.z * vector.z)
    if (length <= 0.0001f) return fallback
    return Position(vector.x / length, vector.y / length, vector.z / length)
}

private fun dot(a: Position, b: Position): Float = a.x * b.x + a.y * b.y + a.z * b.z

private fun cross(a: Position, b: Position): Position = Position(
    a.y * b.z - a.z * b.y,
    a.z * b.x - a.x * b.z,
    a.x * b.y - a.y * b.x,
)

private fun signedPower(value: Float, power: Float): Float = when {
    value > 0f -> value.pow(power)
    value < 0f -> -(-value).pow(power)
    else -> 0f
}

private fun lerp(from: Float, to: Float, amount: Float): Float =
    from + (to - from) * amount

private fun angleDegrees(a: Position, b: Position): Float {
    val na = normalize(a, Position(0f, 1f, 0f))
    val nb = normalize(b, Position(0f, 1f, 0f))
    return (acos(dot(na, nb).coerceIn(-1f, 1f)) * 180f / PI.toFloat())
}

private fun bodyMeasurements(joints: Map<Joint, Position>): BodyMeasurements {
    fun j(joint: Joint) = joints.getValue(joint)
    fun vector(from: Position, to: Position) = Position(
        to.x - from.x,
        to.y - from.y,
        to.z - from.z,
    )

    val shoulderCenter = lerp(j(Joint.LEFT_SHOULDER), j(Joint.RIGHT_SHOULDER), 0.5f)
    val hipCenter = lerp(j(Joint.LEFT_HIP), j(Joint.RIGHT_HIP), 0.5f)
    val ankleCenter = lerp(j(Joint.LEFT_ANKLE), j(Joint.RIGHT_ANKLE), 0.5f)
    val shoulderBreadth = distance(j(Joint.LEFT_SHOULDER), j(Joint.RIGHT_SHOULDER))
    val hipBreadth = distance(j(Joint.LEFT_HIP), j(Joint.RIGHT_HIP))
    val leftLegLength = distance(j(Joint.LEFT_HIP), j(Joint.LEFT_KNEE)) +
        distance(j(Joint.LEFT_KNEE), j(Joint.LEFT_ANKLE))
    val rightLegLength = distance(j(Joint.RIGHT_HIP), j(Joint.RIGHT_KNEE)) +
        distance(j(Joint.RIGHT_KNEE), j(Joint.RIGHT_ANKLE))
    val bodyDown = vector(shoulderCenter, hipCenter)
    val leftArmAngle = angleDegrees(
        bodyDown,
        vector(j(Joint.LEFT_SHOULDER), j(Joint.LEFT_ELBOW)),
    )
    val rightArmAngle = angleDegrees(
        bodyDown,
        vector(j(Joint.RIGHT_SHOULDER), j(Joint.RIGHT_ELBOW)),
    )
    val legAngle = angleDegrees(
        vector(hipCenter, j(Joint.LEFT_ANKLE)),
        vector(hipCenter, j(Joint.RIGHT_ANKLE)),
    )

    return BodyMeasurements(
        // Head radius (0.125 * 2) plus the visible neck-to-head distance.
        headAndNeckHeight = 0.25f + distance(j(Joint.HEAD), j(Joint.NECK)),
        upperBodyHeight = distance(shoulderCenter, hipCenter),
        lowerBodyHeight = ((leftLegLength + rightLegLength) * 0.5f)
            .coerceAtLeast(distance(hipCenter, ankleCenter)),
        abdomenBreadth = hipBreadth + 0.03f,
        shoulderBreadth = shoulderBreadth,
        shoulderCurve = distance(j(Joint.NECK), shoulderCenter) /
            shoulderBreadth.coerceAtLeast(0.0001f),
        armAngleDegrees = (leftArmAngle + rightArmAngle) * 0.5f,
        legAngleDegrees = legAngle,
    )
}

private fun extendFrom(from: Position, through: Position, amount: Float): Position {
    val length = distance(from, through)
    if (length <= 0.0001f) return through
    return Position(
        x = through.x + (through.x - from.x) / length * amount,
        y = through.y + (through.y - from.y) / length * amount,
        z = through.z + (through.z - from.z) / length * amount,
    )
}

@Composable
private fun SceneScope.BeadedSegment(
    from: Position,
    to: Position,
    material: MaterialInstance,
    radius: Float,
    beadCount: Int,
) {
    PathNode(points = listOf(from, to), materialInstance = material)
    repeat(beadCount) { index ->
        val t = (index + 1f) / (beadCount + 1f)
        SphereNode(
            radius = radius,
            stacks = 6,
            slices = 10,
            materialInstance = material,
            position = lerp(from, to, t),
        )
    }
}

private fun skeletonPose(posture: SalahPosture, xOffset: Float): SkeletonPose {
    fun p(x: Float, y: Float, z: Float) = Position(x + xOffset, y, z)
    val joints = when (posture) {
        SalahPosture.QIYAM -> mapOf(
            Joint.HEAD to p(0f, 1.78f, 0f), Joint.NECK to p(0f, 1.57f, 0f),
            Joint.LEFT_SHOULDER to p(-0.19f, 1.49f, 0f), Joint.RIGHT_SHOULDER to p(0.19f, 1.49f, 0f),
            Joint.LEFT_ELBOW to p(-0.25f, 1.22f, -0.03f), Joint.RIGHT_ELBOW to p(0.25f, 1.22f, -0.03f),
            Joint.LEFT_WRIST to p(0.06f, 1.08f, -0.16f), Joint.RIGHT_WRIST to p(-0.06f, 1.04f, -0.18f),
            Joint.LEFT_HIP to p(-0.12f, 0.92f, 0f), Joint.RIGHT_HIP to p(0.12f, 0.92f, 0f),
            Joint.LEFT_KNEE to p(-0.13f, 0.5f, 0f), Joint.RIGHT_KNEE to p(0.13f, 0.5f, 0f),
            Joint.LEFT_ANKLE to p(-0.13f, 0.1f, 0f), Joint.RIGHT_ANKLE to p(0.13f, 0.1f, 0f),
            Joint.LEFT_TOE to p(-0.13f, 0.05f, -0.2f), Joint.RIGHT_TOE to p(0.13f, 0.05f, -0.2f),
        )
        SalahPosture.QIYAM_RISING -> mapOf(
            Joint.HEAD to p(0f, 1.7f, -0.06f), Joint.NECK to p(0f, 1.5f, -0.06f),
            Joint.LEFT_SHOULDER to p(-0.19f, 1.43f, -0.06f), Joint.RIGHT_SHOULDER to p(0.19f, 1.43f, -0.06f),
            Joint.LEFT_ELBOW to p(-0.29f, 1.16f, -0.08f), Joint.RIGHT_ELBOW to p(0.29f, 1.16f, -0.08f),
            Joint.LEFT_WRIST to p(-0.29f, 0.9f, -0.08f), Joint.RIGHT_WRIST to p(0.29f, 0.9f, -0.08f),
            Joint.LEFT_HIP to p(-0.12f, 0.9f, 0.03f), Joint.RIGHT_HIP to p(0.12f, 0.9f, 0.03f),
            Joint.LEFT_KNEE to p(-0.13f, 0.49f, 0f), Joint.RIGHT_KNEE to p(0.13f, 0.49f, 0f),
            Joint.LEFT_ANKLE to p(-0.13f, 0.1f, 0f), Joint.RIGHT_ANKLE to p(0.13f, 0.1f, 0f),
            Joint.LEFT_TOE to p(-0.13f, 0.05f, -0.2f), Joint.RIGHT_TOE to p(0.13f, 0.05f, -0.2f),
        )
        SalahPosture.RUKU -> mapOf(
            Joint.HEAD to p(0f, 1.02f, -0.72f), Joint.NECK to p(0f, 1.04f, -0.51f),
            Joint.LEFT_SHOULDER to p(-0.2f, 1.06f, -0.43f), Joint.RIGHT_SHOULDER to p(0.2f, 1.06f, -0.43f),
            Joint.LEFT_ELBOW to p(-0.24f, 0.82f, -0.28f), Joint.RIGHT_ELBOW to p(0.24f, 0.82f, -0.28f),
            Joint.LEFT_WRIST to p(-0.15f, 0.58f, -0.12f), Joint.RIGHT_WRIST to p(0.15f, 0.58f, -0.12f),
            Joint.LEFT_HIP to p(-0.13f, 0.91f, 0.08f), Joint.RIGHT_HIP to p(0.13f, 0.91f, 0.08f),
            Joint.LEFT_KNEE to p(-0.14f, 0.5f, 0f), Joint.RIGHT_KNEE to p(0.14f, 0.5f, 0f),
            Joint.LEFT_ANKLE to p(-0.14f, 0.1f, 0f), Joint.RIGHT_ANKLE to p(0.14f, 0.1f, 0f),
            Joint.LEFT_TOE to p(-0.14f, 0.05f, -0.2f), Joint.RIGHT_TOE to p(0.14f, 0.05f, -0.2f),
        )
        SalahPosture.GOING_TO_SUJUD -> mapOf(
            Joint.HEAD to p(0f, 0.97f, -0.47f), Joint.NECK to p(0f, 0.93f, -0.29f),
            Joint.LEFT_SHOULDER to p(-0.19f, 0.91f, -0.23f), Joint.RIGHT_SHOULDER to p(0.19f, 0.91f, -0.23f),
            Joint.LEFT_ELBOW to p(-0.27f, 0.6f, -0.33f), Joint.RIGHT_ELBOW to p(0.27f, 0.6f, -0.33f),
            Joint.LEFT_WRIST to p(-0.27f, 0.27f, -0.49f), Joint.RIGHT_WRIST to p(0.27f, 0.27f, -0.49f),
            Joint.LEFT_HIP to p(-0.13f, 0.66f, 0.09f), Joint.RIGHT_HIP to p(0.13f, 0.66f, 0.09f),
            Joint.LEFT_KNEE to p(-0.17f, 0.26f, -0.03f), Joint.RIGHT_KNEE to p(0.17f, 0.26f, -0.03f),
            Joint.LEFT_ANKLE to p(-0.16f, 0.09f, 0.35f), Joint.RIGHT_ANKLE to p(0.16f, 0.09f, 0.35f),
            Joint.LEFT_TOE to p(-0.16f, 0.05f, 0.53f), Joint.RIGHT_TOE to p(0.16f, 0.05f, 0.53f),
        )
        SalahPosture.SUJUD -> mapOf(
            Joint.HEAD to p(0f, 0.18f, -0.62f), Joint.NECK to p(0f, 0.3f, -0.43f),
            Joint.LEFT_SHOULDER to p(-0.2f, 0.34f, -0.31f), Joint.RIGHT_SHOULDER to p(0.2f, 0.34f, -0.31f),
            Joint.LEFT_ELBOW to p(-0.29f, 0.19f, -0.38f), Joint.RIGHT_ELBOW to p(0.29f, 0.19f, -0.38f),
            Joint.LEFT_WRIST to p(-0.27f, 0.08f, -0.61f), Joint.RIGHT_WRIST to p(0.27f, 0.08f, -0.61f),
            Joint.LEFT_HIP to p(-0.13f, 0.57f, 0.18f), Joint.RIGHT_HIP to p(0.13f, 0.57f, 0.18f),
            Joint.LEFT_KNEE to p(-0.17f, 0.09f, 0.1f), Joint.RIGHT_KNEE to p(0.17f, 0.09f, 0.1f),
            Joint.LEFT_ANKLE to p(-0.16f, 0.08f, 0.53f), Joint.RIGHT_ANKLE to p(0.16f, 0.08f, 0.53f),
            Joint.LEFT_TOE to p(-0.16f, 0.05f, 0.7f), Joint.RIGHT_TOE to p(0.16f, 0.05f, 0.7f),
        )
        SalahPosture.JALSA, SalahPosture.TASHAHHUD -> mapOf(
            Joint.HEAD to p(0f, 1.18f, 0.02f), Joint.NECK to p(0f, 0.98f, 0f),
            Joint.LEFT_SHOULDER to p(-0.19f, 0.91f, 0f), Joint.RIGHT_SHOULDER to p(0.19f, 0.91f, 0f),
            Joint.LEFT_ELBOW to p(-0.24f, 0.67f, -0.02f), Joint.RIGHT_ELBOW to p(0.24f, 0.67f, -0.02f),
            Joint.LEFT_WRIST to p(-0.18f, 0.49f, -0.25f), Joint.RIGHT_WRIST to p(0.18f, 0.49f, -0.25f),
            Joint.LEFT_HIP to p(-0.13f, 0.41f, 0.12f), Joint.RIGHT_HIP to p(0.13f, 0.41f, 0.12f),
            Joint.LEFT_KNEE to p(-0.2f, 0.16f, -0.29f), Joint.RIGHT_KNEE to p(0.2f, 0.16f, -0.29f),
            Joint.LEFT_ANKLE to p(-0.18f, 0.08f, 0.37f), Joint.RIGHT_ANKLE to p(0.18f, 0.08f, 0.37f),
            Joint.LEFT_TOE to p(-0.18f, 0.05f, 0.55f), Joint.RIGHT_TOE to p(0.18f, 0.05f, 0.55f),
        )
    }
    return SkeletonPose(joints)
}

private fun normalizedPlotPoints(
    samples: List<SalahDataSample>,
    state: VisualizationState,
): List<PlotPoint> {
    data class RawPoint(
        val originalIndex: Int,
        val posture: SalahPosture,
        val x: Float,
        val y: Float,
        val z: Float,
    )

    val raw = samples.mapIndexedNotNull { index, sample ->
        if (sample.posture !in state.visiblePostures) return@mapIndexedNotNull null
        val pca = state.pcaPositions
        if (state.mode == VisualizationMode.FEATURE_PCA && pca != null && index * 3 + 2 < pca.size) {
            RawPoint(index, sample.posture, pca[index * 3], pca[index * 3 + 1], pca[index * 3 + 2])
        } else {
            RawPoint(
                index,
                sample.posture,
                sample.getAxisValue(state.axisX),
                sample.getAxisValue(state.axisY),
                sample.getAxisValue(state.axisZ),
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
    val largestSpan = max(maxX - minX, max(maxY - minY, maxZ - minZ)).coerceAtLeast(0.001f)
    val scale = 2.7f / largestSpan

    return raw.map { point ->
        PlotPoint(
            originalIndex = point.originalIndex,
            posture = point.posture,
            position = Position(
                (point.x - centerX) * scale,
                (point.y - centerY) * scale,
                (point.z - centerZ) * scale,
            ),
            flagged = state.showDisagreements && point.originalIndex in state.flaggedIndices,
        )
    }
}

private fun postureColor(posture: SalahPosture): Color = when (posture) {
    SalahPosture.QIYAM -> Color(0xFF48D9FF)
    SalahPosture.QIYAM_RISING -> Color(0xFF42F5D4)
    SalahPosture.RUKU -> Color(0xFFFFB347)
    SalahPosture.GOING_TO_SUJUD -> Color(0xFFFF4FA3)
    SalahPosture.SUJUD -> Color(0xFF72ED7D)
    SalahPosture.JALSA -> Color(0xFFAD8CFF)
    SalahPosture.TASHAHHUD -> Color(0xFFFF704D)
}

private fun standardDeviation(values: List<Float>): Float {
    if (values.isEmpty()) return 0f
    val mean = values.average().toFloat()
    return sqrt(values.sumOf { value ->
        val delta = value - mean
        (delta * delta).toDouble()
    }.toFloat() / values.size)
}

private fun lerp(from: Position, to: Position, amount: Float) = Position(
    from.x + (to.x - from.x) * amount,
    from.y + (to.y - from.y) * amount,
    from.z + (to.z - from.z) * amount,
)

@Composable
private fun PoseLegendChip(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(50),
            color = if (isError) {
                Color(0xEB3A151B)
            } else {
                Color(0xEB111A1B)
            },
            border = BorderStroke(
                1.dp,
                if (isError) Color(0xFFFF8A9B).copy(alpha = 0.35f)
                else Color.White.copy(alpha = 0.12f),
            ),
            tonalElevation = 2.dp,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isError) {
                    Color(0xFFFFC2CA)
                } else {
                    Color(0xFFF1F7F4)
                },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                maxLines = 1,
            )
        }
    }
}
