package com.starception.submission.feature.salah.visualization

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.google.android.filament.Colors
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
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
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
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
    LaunchedEffect(state.isPlaying, state.playbackSpeed, samples) {
        if (!state.isPlaying || samples.isEmpty()) return@LaunchedEffect
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

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val environment = rememberEnvironment(environmentLoader, isOpaque = false)
    val filamentView = rememberView(engine)
    val renderer = rememberRenderer(engine)
    val dualFigure = state.predictions != null
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
                    samples = samples,
                    state = state,
                    materialLoader = materialLoader,
                    unlitColorMaterial = unlitColorMaterial,
                    gridMaterial = gridMaterial,
                )
            }
        }

        if (state.mode == VisualizationMode.PHONE_MODEL) {
            val recorded = samples.getOrNull(state.playbackIndex)?.posture
            val prediction = state.predictions?.getOrNull(state.playbackIndex)
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                    .padding(
                        start = 12.dp,
                        top = 10.dp,
                        end = if (onFullscreenChange != null) 58.dp else 12.dp,
                        bottom = 10.dp,
                    ),
            ) {
                PoseLegendChip(
                    text = "LABEL · ${recorded?.displayName ?: "—"}",
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

            FilledIconButton(
                onClick = {
                    onStateChange(state.copy(cameraResetToken = state.cameraResetToken + 1))
                },
                modifier = Modifier
                    .align(if (isFullscreen) Alignment.CenterEnd else Alignment.BottomEnd)
                    .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                    .padding(10.dp)
                    .size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
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
                    .padding(10.dp)
                    .size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
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
    samples: List<SalahDataSample>,
    state: VisualizationState,
    materialLoader: io.github.sceneview.loaders.MaterialLoader,
    unlitColorMaterial: Material,
    gridMaterial: MaterialInstance,
) {
    val sample = samples.getOrNull(state.playbackIndex)
    val recorded = sample?.posture ?: SalahPosture.QIYAM
    val prediction = state.predictions?.getOrNull(state.playbackIndex)
    val dual = state.predictions != null
    val disagrees = prediction?.predicted != null && prediction.predicted != recorded

    val truthMaterial = remember(materialLoader, unlitColorMaterial) {
        materialLoader.createUnlitColorInstance(unlitColorMaterial, 0.07f, 0.78f, 0.61f)
    }
    val truthJointMaterial = remember(materialLoader, unlitColorMaterial) {
        materialLoader.createUnlitColorInstance(unlitColorMaterial, 0.57f, 1f, 0.86f)
    }
    val predictionGood = remember(materialLoader, unlitColorMaterial) {
        materialLoader.createUnlitColorInstance(unlitColorMaterial, 0.42f, 0.89f, 0.25f)
    }
    val predictionBad = remember(materialLoader, unlitColorMaterial) {
        materialLoader.createUnlitColorInstance(unlitColorMaterial, 1f, 0.23f, 0.38f)
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
        pose = skeletonPose(recorded, if (dual) -0.58f else 0f),
        bodyMaterial = truthMaterial,
        jointMaterial = truthJointMaterial,
    )

    if (dual) {
        val predictedPosture = prediction?.predicted ?: recorded
        val predictionMaterial = if (disagrees) predictionBad else predictionGood
        key(disagrees) {
            HolographicHumanoid(
                pose = skeletonPose(predictedPosture, 0.58f),
                bodyMaterial = predictionMaterial,
                jointMaterial = predictionMaterial,
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
    pose: SkeletonPose,
    bodyMaterial: MaterialInstance,
    jointMaterial: MaterialInstance,
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

    // Oval-mannequin scaffold (matches the artist's construction reference): every body
    // segment is a plump ellipsoid and every articulation a small sphere, so the figure
    // reads as a built-up mannequin instead of a thin stick/wireframe. The ovals stay
    // pinned to the animated joints, so proportions hold through every pose.
    val head = animated.getValue(Joint.HEAD)
    val neck = animated.getValue(Joint.NECK)
    val leftShoulder = animated.getValue(Joint.LEFT_SHOULDER)
    val rightShoulder = animated.getValue(Joint.RIGHT_SHOULDER)
    val leftHip = animated.getValue(Joint.LEFT_HIP)
    val rightHip = animated.getValue(Joint.RIGHT_HIP)
    val shoulderCenter = lerp(leftShoulder, rightShoulder, 0.5f)
    val hipCenter = lerp(leftHip, rightHip, 0.5f)
    val torsoSplit = lerp(shoulderCenter, hipCenter, 0.5f)

    // Head — a rounded sphere sitting on the neck.
    SphereNode(
        radius = 0.2f,
        stacks = 22,
        slices = 30,
        materialInstance = bodyMaterial,
        position = head,
    )

    // Neck, chest oval, pelvis oval, and the shoulder/hip girdles that tie them together.
    OvalPart(neck, lerp(neck, head, 0.45f), 0.085f, jointMaterial)
    OvalPart(shoulderCenter, torsoSplit, 0.26f, bodyMaterial)
    OvalPart(torsoSplit, hipCenter, 0.21f, bodyMaterial)
    OvalPart(leftShoulder, rightShoulder, 0.11f, bodyMaterial)
    OvalPart(leftHip, rightHip, 0.12f, bodyMaterial)

    // Arms: upper arm, forearm, and a small hand oval past the wrist.
    listOf(
        Triple(Joint.LEFT_SHOULDER, Joint.LEFT_ELBOW, 0.125f),
        Triple(Joint.RIGHT_SHOULDER, Joint.RIGHT_ELBOW, 0.125f),
        Triple(Joint.LEFT_ELBOW, Joint.LEFT_WRIST, 0.1f),
        Triple(Joint.RIGHT_ELBOW, Joint.RIGHT_WRIST, 0.1f),
    ).forEach { (start, end, width) ->
        OvalPart(animated.getValue(start), animated.getValue(end), width, bodyMaterial)
    }
    listOf(
        Joint.LEFT_ELBOW to Joint.LEFT_WRIST,
        Joint.RIGHT_ELBOW to Joint.RIGHT_WRIST,
    ).forEach { (elbowJoint, wristJoint) ->
        val elbow = animated.getValue(elbowJoint)
        val wrist = animated.getValue(wristJoint)
        OvalPart(wrist, extendFrom(elbow, wrist, 0.085f), 0.088f, bodyMaterial)
    }

    // Legs: thigh, calf, and foot ovals with progressively lighter proportions.
    listOf(
        Triple(Joint.LEFT_HIP, Joint.LEFT_KNEE, 0.17f),
        Triple(Joint.RIGHT_HIP, Joint.RIGHT_KNEE, 0.17f),
        Triple(Joint.LEFT_KNEE, Joint.LEFT_ANKLE, 0.135f),
        Triple(Joint.RIGHT_KNEE, Joint.RIGHT_ANKLE, 0.135f),
        Triple(Joint.LEFT_ANKLE, Joint.LEFT_TOE, 0.1f),
        Triple(Joint.RIGHT_ANKLE, Joint.RIGHT_TOE, 0.1f),
    ).forEach { (start, end, width) ->
        OvalPart(animated.getValue(start), animated.getValue(end), width, bodyMaterial)
    }

    // Construction spheres at each articulation, sized to sit inside the ovals they join.
    animated.filterKeys { it != Joint.HEAD }.forEach { (joint, position) ->
        SphereNode(
            radius = when (joint) {
                Joint.NECK -> 0.09f
                Joint.LEFT_SHOULDER, Joint.RIGHT_SHOULDER -> 0.135f
                Joint.LEFT_HIP, Joint.RIGHT_HIP -> 0.155f
                Joint.LEFT_ELBOW, Joint.RIGHT_ELBOW -> 0.115f
                Joint.LEFT_KNEE, Joint.RIGHT_KNEE -> 0.14f
                Joint.LEFT_WRIST, Joint.RIGHT_WRIST -> 0.098f
                Joint.LEFT_ANKLE, Joint.RIGHT_ANKLE -> 0.11f
                Joint.LEFT_TOE, Joint.RIGHT_TOE -> 0.08f
                Joint.HEAD -> error("Head is rendered as a sphere")
            },
            stacks = 12,
            slices = 16,
            materialInstance = jointMaterial,
            position = position,
        )
    }
}

@Composable
private fun SceneScope.OvalPart(
    from: Position,
    to: Position,
    width: Float,
    material: MaterialInstance,
) {
    val length = distance(from, to)
    if (length <= 0.0001f) return
    // CylinderNode renders as a hollow wire outline in this SceneView build, whereas SphereNode
    // renders as a solid volume — but the spheres also render vertically flattened, so they only
    // overlap into a solid limb when packed very tightly. Pack them densely so every limb reads
    // as one continuous solid capsule regardless of its orientation.
    val steps = max(3, ceil(length / (width * 0.22f)).toInt())
    for (i in 0..steps) {
        SphereNode(
            radius = width,
            stacks = 10,
            slices = 14,
            materialInstance = material,
            position = lerp(from, to, i.toFloat() / steps),
        )
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
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
            },
            tonalElevation = 2.dp,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                maxLines = 1,
            )
        }
    }
}
