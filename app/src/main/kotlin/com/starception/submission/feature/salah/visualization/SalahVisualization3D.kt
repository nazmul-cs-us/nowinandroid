package com.starception.submission.feature.salah.visualization

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import kotlin.math.max
import com.starception.submission.ml.SalahDataSample
import com.starception.submission.ml.SalahPosture
import net.mgsx.gltf.loaders.glb.GLBLoader
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx
import net.mgsx.gltf.scene3d.scene.Scene
import net.mgsx.gltf.scene3d.scene.SceneAsset
import net.mgsx.gltf.scene3d.scene.SceneManager
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * LibGDX-based 3D visualization for salah sensor training data.
 *
 * Supports three visualization modes:
 * - SCATTER: 3D scatter plot of data points colored by posture
 * - PHONE_MODEL: Animated phone model showing orientation (pitch/roll)
 * - GRAVITY_VECTOR: Mean acceleration vectors for each posture with reference sphere
 *
 * Features:
 * - Orbit camera controls (drag to rotate, scroll to zoom)
 * - Playback controls with adjustable speed
 * - Posture filtering
 * - Dynamic axis mapping
 * - Real-time updates via callback
 */
/** World-space X separation between the TRUTH and PRED humanoids in dual playback. */
private const val HUMANOID_PAIR_OFFSET = 4.5f

/** Optional rigged glTF asset path — see [SalahVisualization3D.tryLoadGltfHumanoid]. */
private const val GLTF_HUMANOID_PATH = "models/salah_humanoid.glb"

class SalahVisualization3D(
    private val onPlaybackUpdate: (Int, SalahPosture?, Float, Float, Float, Float, Boolean) -> Unit
) : ApplicationAdapter() {

    // Camera
    private lateinit var camera: PerspectiveCamera
    private lateinit var cameraController: CameraInputController

    // Rendering
    private lateinit var modelBatch: ModelBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var environment: Environment

    // Readiness flag — set true after create() finishes
    @Volatile
    var isReady: Boolean = false
        private set

    // State
    private var currentMode = VisualizationMode.SCATTER
    private var dataPoints: List<SalahDataSample> = emptyList()
    private var filteredPoints: List<SalahDataSample> = emptyList()
    private var visiblePostures: Set<SalahPosture> = SalahPosture.classificationLabels.toSet()
    private var playbackIndex = 0
    private var isPlaying = false
    private var playbackSpeed = 5f
    private var playbackTimer = 0f
    private var axisX = "pitch"
    private var axisY = "roll"
    private var axisZ = "am"
    private var pointSize = 3f
    private var stateTime = 0f

    // Diagnostics overlays
    private var flaggedIndices: Set<Int> = emptySet()
    private var showDisagreements = false
    private var showEllipsoids = false

    // PCA projection: x,y,z triplets parallel to dataPoints (FEATURE_PCA mode)
    private var pcaPositions: FloatArray? = null

    // Original dataPoints index for each entry of filteredPoints
    private var filteredIndices: IntArray = IntArray(0)

    // 3D Models
    private var phoneModel: Model? = null
    private var phoneInstance: ModelInstance? = null
    private var groundModel: Model? = null
    private var groundInstance: ModelInstance? = null

    // Humanoid rig (procedural capsule figure) — built TWICE. TRUTH plays back
    // the recorded posture label; PRED plays back the model's prediction for
    // the same window. Side by side, the two figures visibly diverge exactly
    // when the model disagrees with the label — that divergence, plus a
    // confidence-based tint on PRED, is the whole point of this view for R&D.
    // Each rig owns its own Model/Material objects (never shared with the
    // other rig) so tinting one never bleeds into the other.
    private class HumanoidRig(
        val head: ModelInstance,
        val torso: ModelInstance,
        val leftUpperArm: ModelInstance,
        val rightUpperArm: ModelInstance,
        val leftForearm: ModelInstance,
        val rightForearm: ModelInstance,
        val leftUpperLeg: ModelInstance,
        val rightUpperLeg: ModelInstance,
        val leftLowerLeg: ModelInstance,
        val rightLowerLeg: ModelInstance,
        val mat: ModelInstance,
        /** All Models owned by this rig, for disposal. */
        val models: List<Model>,
        /** The actual (post-construction) cloth materials used by this rig's
         *  torso + upper arms — tint these, not the pre-construction locals,
         *  since ModelInstance may copy materials internally. */
        val clothMaterials: List<Material>,
        val matMaterial: Material,
    )
    private var truthRig: HumanoidRig? = null
    private var predRig: HumanoidRig? = null

    // Per-window model predictions (parallel to dataPoints); enables the
    // dual ground-truth vs prediction humanoid playback in PHONE_MODEL mode.
    // Null (default, before "Analyze predictions" runs) → single centered
    // TRUTH figure only, matching the original single-humanoid behavior.
    private var predictions: List<VizPrediction>? = null

    // Optional rigged glTF humanoid (mgsx-dev gdx-gltf). Loaded eagerly if
    // present at GLTF_HUMANOID_PATH but NOT YET wired into rendering — no
    // rigged asset ships with the app today, so this is forward scaffolding.
    // Wiring it into renderPhone()/renderRig() is the next step once a real
    // salah_humanoid.glb (with per-posture animation clips) is added.
    private var gltfAsset: SceneAsset? = null
    private var sceneManager: SceneManager? = null
    private var truthScene: Scene? = null
    private var predScene: Scene? = null

    // Gravity vectors
    private var gravityArrowModels: MutableList<Model> = mutableListOf()
    private var gravityArrowInstances: MutableList<Pair<SalahPosture, ModelInstance>> = mutableListOf()

    // Highlight
    private var highlightModel: Model? = null
    private var highlightInstance: ModelInstance? = null

    // Scatter point models (for efficient rendering)
    private var scatterPointModel: Model? = null
    private val scatterInstances: MutableList<ModelInstance> = mutableListOf()

    // Flagged (model-vs-label disagreement) points — rendered with a pulse
    private val flaggedInstances: MutableList<Pair<ModelInstance, Vector3>> = mutableListOf()

    // Per-class 1-sigma spread ellipsoids
    private var ellipsoidModel: Model? = null
    private val ellipsoidInstances: MutableList<ModelInstance> = mutableListOf()

    // Temporary vectors for calculations
    private val tmpVec3 = Vector3()
    private val tmpMatrix = Matrix4()

    override fun create() {
        // Setup camera
        camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.near = 0.1f
        camera.far = 300f
        resetCameraForMode()

        // Setup camera controller — override pinch zoom to dolly (move camera)
        // instead of changing field-of-view, which feels unresponsive on mobile
        cameraController = object : CameraInputController(camera) {
            private val zoomDir = Vector3()
            private var downX = 0f
            private var downY = 0f
            private var downTime = 0L

            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                if (pointer == 0) {
                    downX = screenX.toFloat()
                    downY = screenY.toFloat()
                    downTime = System.currentTimeMillis()
                }
                return super.touchDown(screenX, screenY, pointer, button)
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                val handled = super.touchUp(screenX, screenY, pointer, button)
                if (pointer == 0) {
                    val dx = screenX - downX
                    val dy = screenY - downY
                    val quick = System.currentTimeMillis() - downTime < 350
                    val still = dx * dx + dy * dy < 24f * 24f
                    if (quick && still) pickPointAt(screenX.toFloat(), screenY.toFloat())
                }
                return handled
            }

            override fun pinchZoom(amount: Float): Boolean {
                // Dolly zoom: move camera along view direction toward/away from target
                val delta = zoomDir.set(camera.direction).nor().scl(amount * translateUnits)
                camera.position.add(delta)
                // Prevent zooming through the target
                if (camera.position.dst(target) < 2f) {
                    camera.position.sub(delta)
                }
                camera.update()
                return true
            }
        }
        Gdx.input.inputProcessor = cameraController

        // Setup rendering
        modelBatch = ModelBatch()
        shapeRenderer = ShapeRenderer()
        shapeRenderer.setAutoShapeType(true)

        // Setup environment — brighter ambient + two-point lighting for depth
        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.5f, 0.5f, 0.5f, 1f))
        environment.add(DirectionalLight().set(0.85f, 0.85f, 0.9f, -1f, -0.8f, -0.2f))
        environment.add(DirectionalLight().set(0.3f, 0.3f, 0.35f, 1f, 0.5f, 0.5f))

        // Build models
        buildPhoneModel()
        buildGroundPlane()
        buildHighlightModel()
        buildScatterPointModel()
        buildEllipsoidModel()
        buildHumanoid()
        tryLoadGltfHumanoid()

        isReady = true
    }

    override fun render() {
        val delta = Gdx.graphics.deltaTime
        stateTime += delta

        // Update camera
        cameraController.update()

        // Update playback
        if (isPlaying) {
            updatePlayback(delta)
        }

        // Clear screen — warm charcoal background
        Gdx.gl.glClearColor(0.11f, 0.11f, 0.13f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        // Enable depth testing
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        // Render based on mode
        when (currentMode) {
            VisualizationMode.SCATTER, VisualizationMode.FEATURE_PCA -> renderScatter()
            VisualizationMode.PHONE_MODEL -> renderPhone()
            VisualizationMode.GRAVITY_VECTOR -> renderGravity()
        }

        // Render common elements
        renderAxes()
        renderGrid()
    }

    override fun resize(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()
    }

    override fun dispose() {
        modelBatch.dispose()
        shapeRenderer.dispose()
        phoneModel?.dispose()
        groundModel?.dispose()
        highlightModel?.dispose()
        scatterPointModel?.dispose()
        ellipsoidModel?.dispose()
        gravityArrowModels.forEach { it.dispose() }
        truthRig?.models?.forEach { it.dispose() }
        predRig?.models?.forEach { it.dispose() }
        sceneManager?.dispose()
        gltfAsset?.dispose()
    }

    /**
     * Load an optional rigged glTF humanoid from assets. When present, the
     * PHONE_MODEL mode renders it (with skeletal animation clips named after
     * [SalahPosture] enum values, e.g. "QIYAM", "RUKU") instead of the
     * procedural capsule rig. Any failure falls back silently to the rig.
     */
    private fun tryLoadGltfHumanoid() {
        try {
            val file = Gdx.files.internal(GLTF_HUMANOID_PATH)
            if (!file.exists()) return
            val asset = GLBLoader().load(file)
            val sm = SceneManager()
            sm.setCamera(camera)
            sm.environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.6f, 0.6f, 0.6f, 1f))
            sm.environment.add(DirectionalLightEx().apply { set(0.9f, 0.9f, 0.95f, -1f, -0.8f, -0.2f) })
            truthScene = Scene(asset.scene).also { sm.addScene(it) }
            predScene = Scene(asset.scene).also { sm.addScene(it) }
            gltfAsset = asset
            sceneManager = sm
            Gdx.app?.log("SalahViz3D", "glTF humanoid loaded from $GLTF_HUMANOID_PATH")
        } catch (e: Exception) {
            Gdx.app?.log("SalahViz3D", "glTF humanoid unavailable, using procedural rig: ${e.message}")
            sceneManager?.dispose()
            gltfAsset?.dispose()
            gltfAsset = null
            sceneManager = null
            truthScene = null
            predScene = null
        }
    }

    // ============================================
    // Public API (thread-safe via postRunnable)
    // ============================================

    private fun safePostRunnable(runnable: Runnable) {
        try {
            Gdx.app?.postRunnable(runnable)
        } catch (_: Exception) {
            // GL context not ready yet — silently ignore
        }
    }

    fun setData(samples: List<SalahDataSample>) {
        safePostRunnable {
            dataPoints = samples
            applyFilter()
            playbackIndex = 0

            // Rebuild gravity vectors when data changes
            if (currentMode == VisualizationMode.GRAVITY_VECTOR) {
                buildGravityVectors()
            }

            // Rebuild scatter instances
            if (currentMode == VisualizationMode.SCATTER) {
                rebuildScatterInstances()
            }

            resetCameraForMode()
        }
    }

    fun setMode(mode: VisualizationMode) {
        safePostRunnable {
            currentMode = mode

            when (mode) {
                VisualizationMode.GRAVITY_VECTOR -> buildGravityVectors()
                VisualizationMode.SCATTER, VisualizationMode.FEATURE_PCA -> rebuildScatterInstances()
                VisualizationMode.PHONE_MODEL -> Unit
            }
            resetCameraForMode()
        }
    }

    fun setVisiblePostures(postures: Set<SalahPosture>) {
        safePostRunnable {
            visiblePostures = postures
            applyFilter()

            // Rebuild instances for scatter mode
            if (currentMode == VisualizationMode.SCATTER) {
                rebuildScatterInstances()
            }

            if (currentMode != VisualizationMode.PHONE_MODEL) {
                resetCameraForMode()
            }
        }
    }

    fun setPlaybackIndex(index: Int) {
        safePostRunnable {
            playbackIndex = index.coerceIn(0, maxOf(0, dataPoints.size - 1))
            updatePlaybackCallback()
        }
    }

    fun setPlaying(playing: Boolean) {
        safePostRunnable {
            isPlaying = playing
            playbackTimer = 0f
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        safePostRunnable {
            playbackSpeed = speed.coerceIn(0.1f, 100f)
        }
    }

    fun setAxisMapping(x: String, y: String, z: String) {
        safePostRunnable {
            axisX = x
            axisY = y
            axisZ = z

            // Rebuild scatter instances with new mapping
            if (currentMode == VisualizationMode.SCATTER) {
                rebuildScatterInstances()
                resetCameraForMode()
            }
        }
    }

    fun setPointSize(size: Float) {
        safePostRunnable {
            pointSize = size.coerceIn(1f, 10f)

            // Rebuild scatter instances with new size
            if (currentMode == VisualizationMode.SCATTER) {
                rebuildScatterInstances()
            }
        }
    }

    fun resetCamera() {
        safePostRunnable {
            resetCameraForMode()
        }
    }

    /** Flagged sample indices (model-vs-label disagreement runs) from batch analysis. */
    fun setDisagreements(indices: Set<Int>) {
        safePostRunnable {
            flaggedIndices = indices
            if (isScatterLike()) rebuildScatterInstances()
        }
    }

    fun setShowDisagreements(show: Boolean) {
        safePostRunnable {
            showDisagreements = show
            if (isScatterLike()) rebuildScatterInstances()
        }
    }

    fun setShowEllipsoids(show: Boolean) {
        safePostRunnable {
            showEllipsoids = show
            if (isScatterLike()) rebuildScatterInstances()
        }
    }

    /** x,y,z triplets parallel to the sample list, from FeatureSpacePCA. */
    /** Per-window model predictions, parallel to the sample list (or null to clear). */
    fun setPredictions(preds: List<VizPrediction>?) {
        safePostRunnable {
            predictions = preds
        }
    }

    fun setPcaPositions(positions: FloatArray?) {
        safePostRunnable {
            pcaPositions = positions
            if (currentMode == VisualizationMode.FEATURE_PCA) {
                rebuildScatterInstances()
                resetCameraForMode()
            }
        }
    }

    private fun isScatterLike() =
        currentMode == VisualizationMode.SCATTER || currentMode == VisualizationMode.FEATURE_PCA

    /**
     * World-space position of a sample: PCA projection in FEATURE_PCA mode
     * (falling back to axis mapping until the projection arrives), else the
     * user-selected axis mapping.
     */
    private fun samplePosition(originalIndex: Int, sample: SalahDataSample, out: Vector3): Vector3 {
        val pca = pcaPositions
        return if (currentMode == VisualizationMode.FEATURE_PCA && pca != null &&
            originalIndex * 3 + 2 < pca.size
        ) {
            out.set(pca[originalIndex * 3], pca[originalIndex * 3 + 1], pca[originalIndex * 3 + 2])
        } else {
            out.set(
                sample.getAxisValue(axisX),
                sample.getAxisValue(axisY),
                sample.getAxisValue(axisZ),
            )
        }
    }

    /** Ray-pick the nearest visible point and jump playback to it. */
    private fun pickPointAt(screenX: Float, screenY: Float) {
        if (!isScatterLike() || filteredPoints.isEmpty()) return
        val ray = camera.getPickRay(screenX, screenY)
        val pos = Vector3()
        val toPoint = Vector3()
        val threshold = max(0.9f, pointSize * 0.25f)
        var bestIndex = -1
        var bestPerp = Float.MAX_VALUE

        filteredPoints.forEachIndexed { fi, sample ->
            samplePosition(filteredIndices[fi], sample, pos)
            toPoint.set(pos).sub(ray.origin)
            val along = toPoint.dot(ray.direction)
            if (along <= 0f) return@forEachIndexed // behind the camera
            // Perpendicular distance from the point to the ray
            val perp = tmpVec3.set(ray.direction).scl(along).add(ray.origin).dst(pos)
            if (perp < threshold && perp < bestPerp) {
                bestPerp = perp
                bestIndex = filteredIndices[fi]
            }
        }

        if (bestIndex >= 0) {
            playbackIndex = bestIndex
            isPlaying = false
            updatePlaybackCallback()
        }
    }

    // ============================================
    // Private rendering methods
    // ============================================

    private fun renderScatter() {
        // Render scatter points using model instances
        modelBatch.begin(camera)
        for (instance in scatterInstances) {
            modelBatch.render(instance, environment)
        }

        // Flagged disagreement points pulse so they are findable at a glance
        if (flaggedInstances.isNotEmpty()) {
            val pulse = 1f + 0.35f * sin(stateTime * 5f)
            val scale = pointSize * 0.15f * 1.4f * pulse
            for ((instance, position) in flaggedInstances) {
                instance.transform.setToTranslation(position)
                instance.transform.scale(scale, scale, scale)
                modelBatch.render(instance, environment)
            }
        }

        // Per-class spread ellipsoids (translucent; ModelBatch sorts blended last)
        if (showEllipsoids) {
            for (instance in ellipsoidInstances) {
                modelBatch.render(instance, environment)
            }
        }

        // Render highlight for current playback position
        if (playbackIndex in dataPoints.indices) {
            val sample = dataPoints[playbackIndex]
            highlightInstance?.let { instance ->
                samplePosition(playbackIndex, sample, tmpVec3)
                instance.transform.setToTranslation(tmpVec3)
                instance.transform.scale(pointSize * 0.3f, pointSize * 0.3f, pointSize * 0.3f)
                modelBatch.render(instance, environment)
            }
        }
        modelBatch.end()
    }

    private fun renderPhone() {
        if (playbackIndex !in dataPoints.indices) return
        val truth = truthRig ?: return
        val sample = dataPoints[playbackIndex]

        // Dual playback only once predictions have been analyzed (state.predictions
        // != null); otherwise a single centered TRUTH figure, matching the
        // original single-humanoid behavior.
        val pred = predRig
        val predictionList = predictions
        val dual = predictionList != null && pred != null

        if (dual) {
            poseHumanoid(truth, sample.posture, -HUMANOID_PAIR_OFFSET)
            val prediction = predictionList!!.getOrNull(playbackIndex)
            val predPosture = prediction?.predicted ?: sample.posture
            poseHumanoid(pred!!, predPosture, HUMANOID_PAIR_OFFSET)
            tintPredictionRig(pred, prediction, agrees = predPosture == sample.posture)
        } else {
            poseHumanoid(truth, sample.posture, 0f)
        }

        modelBatch.begin(camera)
        renderRig(truth)
        if (dual) renderRig(pred!!)
        modelBatch.end()
    }

    private fun renderRig(rig: HumanoidRig) {
        modelBatch.render(rig.mat, environment)
        modelBatch.render(rig.head, environment)
        modelBatch.render(rig.torso, environment)
        modelBatch.render(rig.leftUpperArm, environment)
        modelBatch.render(rig.rightUpperArm, environment)
        modelBatch.render(rig.leftForearm, environment)
        modelBatch.render(rig.rightForearm, environment)
        modelBatch.render(rig.leftUpperLeg, environment)
        modelBatch.render(rig.rightUpperLeg, environment)
        modelBatch.render(rig.leftLowerLeg, environment)
        modelBatch.render(rig.rightLowerLeg, environment)
    }

    /**
     * Confidence-based tint of the PREDICTION figure: greener as the model
     * agrees with the label more confidently; redder as it disagrees more
     * confidently (a low-confidence wrong guess still reads amber, not full
     * alarm red — it's genuinely more ambiguous, not necessarily "worse").
     * Also tints the mat under the figure so the divergence reads even
     * glancing at the scene, without needing to look at the body color.
     */
    private fun tintPredictionRig(rig: HumanoidRig, prediction: VizPrediction?, agrees: Boolean) {
        val white = Color(0.95f, 0.95f, 0.95f, 1f)
        val clothTarget = when {
            prediction?.predicted == null -> Color(0.75f, 0.75f, 0.75f, 1f)
            agrees -> white.cpy().lerp(Color(0.2f, 0.78f, 0.35f, 1f), prediction.confidence.coerceIn(0f, 1f))
            else -> white.cpy().lerp(Color(0.85f, 0.18f, 0.18f, 1f), (0.45f + 0.55f * prediction.confidence).coerceIn(0f, 1f))
        }
        rig.clothMaterials.forEach { mat ->
            (mat.get(ColorAttribute.Diffuse) as? ColorAttribute)?.color?.set(clothTarget)
        }
        val matTarget = when {
            prediction?.predicted == null -> Color(0.18f, 0.55f, 0.34f, 1f)
            agrees -> Color(0.15f, 0.5f, 0.2f, 1f)
            else -> Color(0.55f, 0.15f, 0.15f, 1f)
        }
        (rig.matMaterial.get(ColorAttribute.Diffuse) as? ColorAttribute)?.color?.set(matTarget)
    }

    private fun renderGravity() {
        // Render mean gravity vectors for each posture
        modelBatch.begin(camera)
        for ((_, instance) in gravityArrowInstances) {
            modelBatch.render(instance, environment)
        }
        modelBatch.end()

        // Draw reference sphere (magnitude = 9.81 m/s²)
        val sphereRadius = 9.81f
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 0.5f)

        // Draw wireframe sphere using circles
        val segments = 32
        val rings = 16

        // Latitude circles
        for (i in 0 until rings) {
            val lat = (i.toFloat() / rings - 0.5f) * Math.PI.toFloat()
            val r = sphereRadius * cos(lat)
            val y = sphereRadius * sin(lat)

            for (j in 0 until segments) {
                val lon1 = (j.toFloat() / segments) * Math.PI.toFloat() * 2
                val lon2 = ((j + 1).toFloat() / segments) * Math.PI.toFloat() * 2
                val x1 = r * cos(lon1)
                val z1 = r * sin(lon1)
                val x2 = r * cos(lon2)
                val z2 = r * sin(lon2)
                shapeRenderer.line(x1, y, z1, x2, y, z2)
            }
        }

        // Longitude circles
        for (i in 0 until segments step 4) {
            val lon = (i.toFloat() / segments) * Math.PI.toFloat() * 2
            for (j in 0 until rings) {
                val lat1 = (j.toFloat() / rings - 0.5f) * Math.PI.toFloat()
                val lat2 = ((j + 1).toFloat() / rings - 0.5f) * Math.PI.toFloat()
                val r1 = sphereRadius * cos(lat1)
                val y1 = sphereRadius * sin(lat1)
                val r2 = sphereRadius * cos(lat2)
                val y2 = sphereRadius * sin(lat2)
                val x1 = r1 * cos(lon)
                val z1 = r1 * sin(lon)
                val x2 = r2 * cos(lon)
                val z2 = r2 * sin(lon)
                shapeRenderer.line(x1, y1, z1, x2, y2, z2)
            }
        }

        shapeRenderer.end()
    }

    private fun renderAxes() {
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

        val axisLength = 15f
        val arrowSize = 0.5f

        // X axis (red)
        shapeRenderer.setColor(1f, 0f, 0f, 1f)
        shapeRenderer.line(0f, 0f, 0f, axisLength, 0f, 0f)
        shapeRenderer.line(axisLength, 0f, 0f, axisLength - arrowSize, arrowSize, 0f)
        shapeRenderer.line(axisLength, 0f, 0f, axisLength - arrowSize, -arrowSize, 0f)

        // Y axis (green)
        shapeRenderer.setColor(0f, 1f, 0f, 1f)
        shapeRenderer.line(0f, 0f, 0f, 0f, axisLength, 0f)
        shapeRenderer.line(0f, axisLength, 0f, arrowSize, axisLength - arrowSize, 0f)
        shapeRenderer.line(0f, axisLength, 0f, -arrowSize, axisLength - arrowSize, 0f)

        // Z axis (blue)
        shapeRenderer.setColor(0f, 0f, 1f, 1f)
        shapeRenderer.line(0f, 0f, 0f, 0f, 0f, axisLength)
        shapeRenderer.line(0f, 0f, axisLength, arrowSize, 0f, axisLength - arrowSize)
        shapeRenderer.line(0f, 0f, axisLength, -arrowSize, 0f, axisLength - arrowSize)

        shapeRenderer.end()
    }

    private fun renderGrid() {
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.setColor(0.22f, 0.22f, 0.24f, 1f)

        val gridSize = 40
        val gridSpacing = 2f
        val halfSize = (gridSize / 2) * gridSpacing

        // Grid lines parallel to X axis
        for (i in -gridSize / 2..gridSize / 2) {
            val z = i * gridSpacing
            shapeRenderer.line(-halfSize, 0f, z, halfSize, 0f, z)
        }

        // Grid lines parallel to Z axis
        for (i in -gridSize / 2..gridSize / 2) {
            val x = i * gridSpacing
            shapeRenderer.line(x, 0f, -halfSize, x, 0f, halfSize)
        }

        shapeRenderer.end()
    }

    private fun updatePlayback(delta: Float) {
        if (dataPoints.isEmpty()) return

        playbackTimer += delta * playbackSpeed

        // Update at 50Hz (0.02s intervals)
        while (playbackTimer >= 0.02f) {
            playbackTimer -= 0.02f
            playbackIndex++

            if (playbackIndex >= dataPoints.size) {
                playbackIndex = 0
                isPlaying = false
            }

            updatePlaybackCallback()
        }
    }

    private fun updatePlaybackCallback() {
        if (playbackIndex in dataPoints.indices) {
            val sample = dataPoints[playbackIndex]
            onPlaybackUpdate(
                playbackIndex,
                sample.posture,
                sample.pitch,
                sample.roll,
                sample.accelMagnitude,
                sample.gyroMagnitude,
                isPlaying
            )
        }
    }

    private fun resetCameraForMode() {
        when (currentMode) {
            VisualizationMode.PHONE_MODEL -> {
                // Wide enough to frame both TRUTH and PRED figures when dual
                // playback is active (±HUMANOID_PAIR_OFFSET); single-figure
                // mode just has extra headroom, which reads fine.
                camera.position.set(15f, 7f, 17f)
                camera.lookAt(0f, 3.5f, 0f)
            }
            VisualizationMode.SCATTER, VisualizationMode.FEATURE_PCA -> fitCameraToScatterData()
            VisualizationMode.GRAVITY_VECTOR -> fitCameraToGravityData()
        }
        camera.up.set(Vector3.Y)
        camera.update()
        if (::cameraController.isInitialized) {
            cameraController.target.set(cameraPositionTarget())
        }
    }

    private fun fitCameraToScatterData() {
        val points = filteredPoints.ifEmpty { dataPoints }
        if (points.isEmpty()) {
            camera.position.set(20f, 20f, 20f)
            camera.lookAt(0f, 0f, 0f)
            return
        }

        val pos = Vector3()
        val positions = points.mapIndexed { fi, sample ->
            val original = if (filteredPoints.isNotEmpty()) filteredIndices[fi] else fi
            samplePosition(original, sample, pos)
            Triple(pos.x, pos.y, pos.z)
        }
        val xs = positions.map { it.first }
        val ys = positions.map { it.second }
        val zs = positions.map { it.third }

        val centerX = (xs.minOrNull()!! + xs.maxOrNull()!!) / 2f
        val centerY = (ys.minOrNull()!! + ys.maxOrNull()!!) / 2f
        val centerZ = (zs.minOrNull()!! + zs.maxOrNull()!!) / 2f
        val radius = max(
            6f,
            max(xs.maxOrNull()!! - xs.minOrNull()!!, max(ys.maxOrNull()!! - ys.minOrNull()!!, zs.maxOrNull()!! - zs.minOrNull()!!)) * 0.9f
        )

        camera.position.set(centerX + radius, centerY + radius * 0.7f, centerZ + radius)
        camera.lookAt(centerX, centerY, centerZ)
    }

    private fun fitCameraToGravityData() {
        val arrows = gravityArrowInstances.map { it.second.transform.getTranslation(Vector3()) }
        if (arrows.isEmpty()) {
            camera.position.set(18f, 14f, 18f)
            camera.lookAt(0f, 0f, 0f)
            return
        }

        val maxExtent = max(12f, arrows.maxOf { kotlin.math.sqrt(it.x * it.x + it.y * it.y + it.z * it.z) } * 1.8f)
        camera.position.set(maxExtent, maxExtent * 0.8f, maxExtent)
        camera.lookAt(0f, 0f, 0f)
    }

    private fun cameraPositionTarget(): Vector3 {
        return when (currentMode) {
            VisualizationMode.PHONE_MODEL -> Vector3(0f, 3.5f, 0f)
            VisualizationMode.SCATTER, VisualizationMode.FEATURE_PCA -> {
                val points = filteredPoints.ifEmpty { dataPoints }
                if (points.isEmpty()) {
                    Vector3.Zero.cpy()
                } else {
                    val pos = Vector3()
                    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
                    var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
                    var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
                    points.forEachIndexed { fi, sample ->
                        val original = if (filteredPoints.isNotEmpty()) filteredIndices[fi] else fi
                        samplePosition(original, sample, pos)
                        minX = min(minX, pos.x); maxX = max(maxX, pos.x)
                        minY = min(minY, pos.y); maxY = max(maxY, pos.y)
                        minZ = min(minZ, pos.z); maxZ = max(maxZ, pos.z)
                    }
                    Vector3((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f)
                }
            }
            VisualizationMode.GRAVITY_VECTOR -> Vector3.Zero.cpy()
        }
    }

    // ============================================
    // Model building
    // ============================================

    private fun buildPhoneModel() {
        val modelBuilder = ModelBuilder()
        modelBuilder.begin()

        // Phone body (box)
        val bodyMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.2f, 0.2f, 0.2f, 1f)),
            ColorAttribute.createSpecular(Color(0.5f, 0.5f, 0.5f, 1f))
        )
        modelBuilder.part(
            "body",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
            bodyMaterial
        ).box(2f, 4f, 0.25f)

        // Screen (front face)
        val screenMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.1f, 0.1f, 0.15f, 1f)),
            ColorAttribute.createSpecular(Color(0.8f, 0.8f, 0.8f, 1f))
        )
        modelBuilder.part(
            "screen",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
            screenMaterial
        ).box(1.8f, 3.8f, 0.01f, 0f, 0f, 0.13f)

        phoneModel = modelBuilder.end()
        phoneInstance = ModelInstance(phoneModel)
    }

    private fun buildGroundPlane() {
        val modelBuilder = ModelBuilder()
        modelBuilder.begin()

        val groundMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.1f, 0.1f, 0.12f, 0.8f)),
            ColorAttribute.createSpecular(Color(0.2f, 0.2f, 0.2f, 1f))
        )
        modelBuilder.part(
            "ground",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
            groundMaterial
        ).box(12f, 0.1f, 12f, 0f, -2f, 0f)

        groundModel = modelBuilder.end()
        groundInstance = ModelInstance(groundModel)
    }

    private fun buildHighlightModel() {
        val modelBuilder = ModelBuilder()
        modelBuilder.begin()

        val highlightMaterial = Material(
            ColorAttribute.createDiffuse(Color(1f, 1f, 0f, 0.8f)),
            ColorAttribute.createEmissive(Color(1f, 1f, 0f, 0.5f))
        )
        modelBuilder.part(
            "highlight",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
            highlightMaterial
        ).sphere(1f, 1f, 1f, 16, 16)

        highlightModel = modelBuilder.end()
        highlightInstance = ModelInstance(highlightModel)
    }

    private fun buildScatterPointModel() {
        val modelBuilder = ModelBuilder()
        modelBuilder.begin()

        val pointMaterial = Material(
            ColorAttribute.createDiffuse(Color.WHITE)
        )
        modelBuilder.part(
            "point",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
            pointMaterial
        ).sphere(1f, 1f, 1f, 8, 8)

        scatterPointModel = modelBuilder.end()
    }

    private fun buildEllipsoidModel() {
        val modelBuilder = ModelBuilder()
        modelBuilder.begin()
        // Unit sphere, scaled per class to its 1-sigma spread; translucent material
        val material = Material(
            ColorAttribute.createDiffuse(Color.WHITE),
            BlendingAttribute(true, 0.22f),
        )
        modelBuilder.part(
            "ellipsoid",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
            material
        ).sphere(2f, 2f, 2f, 20, 20) // diameter 2 => unit radius
        ellipsoidModel = modelBuilder.end()
    }

    /**
     * Per-class centroid + axis-aligned 1-sigma ellipsoids over the CURRENT positions
     * (axis mapping or PCA). Overlapping ellipsoids = classes the model will confuse.
     */
    private fun rebuildEllipsoids() {
        ellipsoidInstances.clear()
        val model = ellipsoidModel ?: return
        if (filteredPoints.isEmpty()) return

        val byPosture = mutableMapOf<SalahPosture, MutableList<Vector3>>()
        val pos = Vector3()
        filteredPoints.forEachIndexed { fi, sample ->
            samplePosition(filteredIndices[fi], sample, pos)
            byPosture.getOrPut(sample.posture) { mutableListOf() }.add(pos.cpy())
        }

        for ((posture, positions) in byPosture) {
            if (positions.size < 5) continue
            var mx = 0f; var my = 0f; var mz = 0f
            for (v in positions) { mx += v.x; my += v.y; mz += v.z }
            val n = positions.size
            mx /= n; my /= n; mz /= n
            var vx = 0f; var vy = 0f; var vz = 0f
            for (v in positions) {
                vx += (v.x - mx) * (v.x - mx)
                vy += (v.y - my) * (v.y - my)
                vz += (v.z - mz) * (v.z - mz)
            }
            val sx = max(0.4f, sqrt(vx / n))
            val sy = max(0.4f, sqrt(vy / n))
            val sz = max(0.4f, sqrt(vz / n))

            val instance = ModelInstance(model)
            instance.transform.setToTranslation(mx, my, mz)
            instance.transform.scale(sx, sy, sz)
            val color = getPostureColor(posture)
            instance.materials.first().set(
                ColorAttribute.createDiffuse(Color(color.r, color.g, color.b, 0.22f)),
                BlendingAttribute(true, 0.22f),
            )
            ellipsoidInstances.add(instance)
        }
    }

    // ============================================
    // Humanoid model building and posing
    // ============================================

    /**
     * Build a simple humanoid figure from pill-shaped (capsule) parts.
     * Each body part is a cylinder capped with spheres at both ends,
     * approximated by using a capsule shape (cylinder + 2 spheres).
     * LibGDX ModelBuilder doesn't have a native capsule, so we build
     * each part as a single cylinder — the visual effect is close enough.
     */
    private fun buildHumanoid() {
        truthRig = buildHumanoidRig()
        predRig = buildHumanoidRig()
    }

    /**
     * Builds one full capsule-figure rig with its own Model/Material objects
     * so it can be posed and tinted completely independently of any other
     * rig (see [tintPredictionRig]).
     */
    private fun buildHumanoidRig(): HumanoidRig {
        val mb = ModelBuilder()
        val attrs = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()

        // Skin color — warm tone
        val skinColor = Color(0.85f, 0.7f, 0.55f, 1f)
        val clothColor = Color(0.95f, 0.95f, 0.95f, 1f)   // white thobe/clothing
        val pantsColor = Color(0.3f, 0.3f, 0.35f, 1f)     // dark pants

        val skinMat = Material(
            ColorAttribute.createDiffuse(skinColor),
            ColorAttribute.createSpecular(Color(0.3f, 0.3f, 0.3f, 1f))
        )
        val clothMat = Material(
            ColorAttribute.createDiffuse(clothColor),
            ColorAttribute.createSpecular(Color(0.4f, 0.4f, 0.4f, 1f))
        )
        val pantsMat = Material(
            ColorAttribute.createDiffuse(pantsColor),
            ColorAttribute.createSpecular(Color(0.2f, 0.2f, 0.2f, 1f))
        )

        // Head — sphere (radius ~0.5)
        mb.begin()
        mb.part("head", GL20.GL_TRIANGLES, attrs, skinMat)
            .sphere(1f, 1f, 1f, 12, 12)
        val headModel = mb.end()
        val head = ModelInstance(headModel)

        // Torso — tall cylinder (clothing color)
        mb.begin()
        mb.part("torso", GL20.GL_TRIANGLES, attrs, clothMat)
            .capsule(0.6f, 3f, 12)
        val torsoModel = mb.end()
        val torso = ModelInstance(torsoModel)

        // Upper arm — cylinder
        mb.begin()
        mb.part("upper_arm", GL20.GL_TRIANGLES, attrs, clothMat)
            .capsule(0.22f, 1.6f, 8)
        val upperArmModel = mb.end()
        val leftUpperArm = ModelInstance(upperArmModel)
        val rightUpperArm = ModelInstance(upperArmModel)

        // Forearm — cylinder (skin)
        mb.begin()
        mb.part("forearm", GL20.GL_TRIANGLES, attrs, skinMat)
            .capsule(0.18f, 1.4f, 8)
        val forearmModel = mb.end()
        val leftForearm = ModelInstance(forearmModel)
        val rightForearm = ModelInstance(forearmModel)

        // Upper leg — cylinder (pants)
        mb.begin()
        mb.part("upper_leg", GL20.GL_TRIANGLES, attrs, pantsMat)
            .capsule(0.28f, 2f, 8)
        val upperLegModel = mb.end()
        val leftUpperLeg = ModelInstance(upperLegModel)
        val rightUpperLeg = ModelInstance(upperLegModel)

        // Lower leg — cylinder (pants)
        mb.begin()
        mb.part("lower_leg", GL20.GL_TRIANGLES, attrs, pantsMat)
            .capsule(0.22f, 1.8f, 8)
        val lowerLegModel = mb.end()
        val leftLowerLeg = ModelInstance(lowerLegModel)
        val rightLowerLeg = ModelInstance(lowerLegModel)

        // Prayer mat — flat box (green)
        mb.begin()
        val matMaterial = Material(
            ColorAttribute.createDiffuse(Color(0.18f, 0.55f, 0.34f, 1f)),
            ColorAttribute.createSpecular(Color(0.1f, 0.1f, 0.1f, 1f))
        )
        mb.part("prayer_mat", GL20.GL_TRIANGLES, attrs, matMaterial)
            .box(3f, 0.05f, 6f)
        val matModel = mb.end()
        val mat = ModelInstance(matModel).apply { transform.setToTranslation(0f, -0.025f, 0f) }

        // Read back the ACTUAL materials each instance renders with (ModelInstance
        // may copy them internally) so runtime tinting always hits what's on screen.
        val clothMaterials = listOfNotNull(
            torso.materials.firstOrNull(),
            leftUpperArm.materials.firstOrNull(),
            rightUpperArm.materials.firstOrNull()
        ).distinct()
        val matMaterialActual = mat.materials.firstOrNull() ?: matMaterial

        return HumanoidRig(
            head = head,
            torso = torso,
            leftUpperArm = leftUpperArm,
            rightUpperArm = rightUpperArm,
            leftForearm = leftForearm,
            rightForearm = rightForearm,
            leftUpperLeg = leftUpperLeg,
            rightUpperLeg = rightUpperLeg,
            leftLowerLeg = leftLowerLeg,
            rightLowerLeg = rightLowerLeg,
            mat = mat,
            models = listOf(headModel, torsoModel, upperArmModel, forearmModel, upperLegModel, lowerLegModel, matModel),
            clothMaterials = clothMaterials,
            matMaterial = matMaterialActual
        )
    }

    /**
     * Position all humanoid body parts of [rig] based on the current salah
     * posture, offset laterally by [xOffset] (0 = centered; ±[HUMANOID_PAIR_OFFSET]
     * for side-by-side dual playback).
     *
     * Coordinate system: Y is up, X is right, Z is toward viewer.
     * The figure faces the -Z direction (toward Qibla).
     *
     * Body proportions (approximate):
     *   Head center:      y = torsoTop + 0.5
     *   Shoulder center:  y = torsoTop
     *   Hip center:       y = torsoBottom
     *   Knee:             y = hipY - upperLegLength
     *   Foot:             y = kneeY - lowerLegLength
     */
    private fun poseHumanoid(rig: HumanoidRig, posture: SalahPosture, xOffset: Float) {
        when (posture) {
            SalahPosture.QIYAM, SalahPosture.QIYAM_RISING -> poseStanding(rig, xOffset)
            SalahPosture.RUKU -> poseRuku(rig, xOffset)
            SalahPosture.GOING_TO_SUJUD -> poseGoingToSujud(rig, xOffset)
            SalahPosture.SUJUD -> poseSujud(rig, xOffset)
            SalahPosture.JALSA -> poseJalsa(rig, xOffset)
            SalahPosture.TASHAHHUD -> poseTashahhud(rig, xOffset)
            else -> poseStanding(rig, xOffset)
        }
        rig.mat.transform.setToTranslation(xOffset, -0.025f, 0f)
    }

    /** Standing upright — Qiyam position, arms at sides */
    private fun poseStanding(rig: HumanoidRig, xOffset: Float) {
        // Build upward from ground (y=0 = feet level)
        // Lower leg capsule h=1.8 → center at 0.9
        // Upper leg capsule h=2.0 → center at 0.9+1.8/2+2.0/2 = 0.9+0.9+1.0 = 2.8
        // Hip ≈ 3.8, torso capsule h=3.0 → center at 3.8+1.5 = 5.3
        // Head sphere r=0.5 → center at 5.3+1.5+0.5 = 7.3
        val groundY = 0f
        val lowerLegY = groundY + 0.9f     // center of lower leg
        val upperLegY = groundY + 2.8f     // center of upper leg
        val hipY = groundY + 3.8f          // hip joint
        val torsoY = hipY + 1.5f           // center of torso (5.3)
        val headY = torsoY + 2f            // center of head (7.3)

        rig.torso.transform.setToTranslation(xOffset, torsoY, 0f)
        rig.head.transform.setToTranslation(xOffset, headY, 0f)

        // Arms hanging at sides
        rig.leftUpperArm.transform.idt()
        rig.leftUpperArm.transform.setToTranslation(xOffset - 0.9f, torsoY + 0.7f, 0f)

        rig.rightUpperArm.transform.idt()
        rig.rightUpperArm.transform.setToTranslation(xOffset + 0.9f, torsoY + 0.7f, 0f)

        rig.leftForearm.transform.idt()
        rig.leftForearm.transform.setToTranslation(xOffset - 0.9f, torsoY - 0.6f, 0f)

        rig.rightForearm.transform.idt()
        rig.rightForearm.transform.setToTranslation(xOffset + 0.9f, torsoY - 0.6f, 0f)

        // Legs
        rig.leftUpperLeg.transform.setToTranslation(xOffset - 0.4f, upperLegY, 0f)
        rig.rightUpperLeg.transform.setToTranslation(xOffset + 0.4f, upperLegY, 0f)
        rig.leftLowerLeg.transform.setToTranslation(xOffset - 0.4f, lowerLegY, 0f)
        rig.rightLowerLeg.transform.setToTranslation(xOffset + 0.4f, lowerLegY, 0f)
    }

    /** Bowing — Ruku position: torso bent ~90 degrees forward, hands on knees */
    private fun poseRuku(rig: HumanoidRig, xOffset: Float) {
        // Same leg geometry as standing — feet at ground
        val groundY = 0f
        val lowerLegY = groundY + 0.9f
        val upperLegY = groundY + 2.8f
        val hipY = groundY + 3.8f

        // Torso: tilted forward 90 degrees from hip
        rig.torso.transform.idt()
        rig.torso.transform.setToTranslation(xOffset, hipY + 0.3f, -0.8f)
        rig.torso.transform.rotate(Vector3.X, 90f)

        // Head: in front of torso (bent forward)
        rig.head.transform.setToTranslation(xOffset, hipY + 0.3f, -2.8f)

        // Arms reaching down to knees
        rig.leftUpperArm.transform.idt()
        rig.leftUpperArm.transform.setToTranslation(xOffset - 0.5f, hipY - 0.2f, -1f)
        rig.leftUpperArm.transform.rotate(Vector3.X, 45f)

        rig.rightUpperArm.transform.idt()
        rig.rightUpperArm.transform.setToTranslation(xOffset + 0.5f, hipY - 0.2f, -1f)
        rig.rightUpperArm.transform.rotate(Vector3.X, 45f)

        rig.leftForearm.transform.idt()
        rig.leftForearm.transform.setToTranslation(xOffset - 0.5f, hipY - 0.8f, -0.6f)

        rig.rightForearm.transform.idt()
        rig.rightForearm.transform.setToTranslation(xOffset + 0.5f, hipY - 0.8f, -0.6f)

        // Legs: straight, feet on ground
        rig.leftUpperLeg.transform.setToTranslation(xOffset - 0.4f, upperLegY, 0f)
        rig.rightUpperLeg.transform.setToTranslation(xOffset + 0.4f, upperLegY, 0f)
        rig.leftLowerLeg.transform.setToTranslation(xOffset - 0.4f, lowerLegY, 0f)
        rig.rightLowerLeg.transform.setToTranslation(xOffset + 0.4f, lowerLegY, 0f)
    }

    /** Transitioning down — partway between standing and prostration */
    private fun poseGoingToSujud(rig: HumanoidRig, xOffset: Float) {
        // Hip is lowering — person bending knees to go down
        val groundY = 0f
        val hipY = 2.5f  // partway between standing (3.8) and kneeling (1.2)

        // Torso tilted forward ~45 degrees, descending
        rig.torso.transform.idt()
        rig.torso.transform.setToTranslation(xOffset, hipY + 0.5f, -0.5f)
        rig.torso.transform.rotate(Vector3.X, 45f)

        rig.head.transform.setToTranslation(xOffset, hipY + 1f, -1.8f)

        // Arms reaching forward
        rig.leftUpperArm.transform.idt()
        rig.leftUpperArm.transform.setToTranslation(xOffset - 0.7f, hipY, -1f)
        rig.leftUpperArm.transform.rotate(Vector3.X, 60f)

        rig.rightUpperArm.transform.idt()
        rig.rightUpperArm.transform.setToTranslation(xOffset + 0.7f, hipY, -1f)
        rig.rightUpperArm.transform.rotate(Vector3.X, 60f)

        rig.leftForearm.transform.idt()
        rig.leftForearm.transform.setToTranslation(xOffset - 0.7f, hipY - 0.5f, -1.5f)
        rig.leftForearm.transform.rotate(Vector3.X, 70f)

        rig.rightForearm.transform.idt()
        rig.rightForearm.transform.setToTranslation(xOffset + 0.7f, hipY - 0.5f, -1.5f)
        rig.rightForearm.transform.rotate(Vector3.X, 70f)

        // Legs bending — knees going forward, feet on ground
        rig.leftUpperLeg.transform.idt()
        rig.leftUpperLeg.transform.setToTranslation(xOffset - 0.4f, hipY - 0.8f, 0.5f)
        rig.leftUpperLeg.transform.rotate(Vector3.X, -40f)

        rig.rightUpperLeg.transform.idt()
        rig.rightUpperLeg.transform.setToTranslation(xOffset + 0.4f, hipY - 0.8f, 0.5f)
        rig.rightUpperLeg.transform.rotate(Vector3.X, -40f)

        rig.leftLowerLeg.transform.idt()
        rig.leftLowerLeg.transform.setToTranslation(xOffset - 0.4f, groundY + 0.5f, 1.0f)
        rig.leftLowerLeg.transform.rotate(Vector3.X, -70f)

        rig.rightLowerLeg.transform.idt()
        rig.rightLowerLeg.transform.setToTranslation(xOffset + 0.4f, groundY + 0.5f, 1.0f)
        rig.rightLowerLeg.transform.rotate(Vector3.X, -70f)
    }

    /** Prostration — Sujud: face on ground, back arched, knees on ground */
    private fun poseSujud(rig: HumanoidRig, xOffset: Float) {
        val groundY = 0f

        // Torso: heavily tilted forward, close to ground
        rig.torso.transform.idt()
        rig.torso.transform.setToTranslation(xOffset, groundY + 0.8f, -0.5f)
        rig.torso.transform.rotate(Vector3.X, 120f)

        // Head: touching the ground
        rig.head.transform.setToTranslation(xOffset, groundY + 0.3f, -2.5f)

        // Arms: on ground, palms down beside head
        rig.leftUpperArm.transform.idt()
        rig.leftUpperArm.transform.setToTranslation(xOffset - 0.7f, groundY + 0.4f, -1.5f)
        rig.leftUpperArm.transform.rotate(Vector3.X, 90f)

        rig.rightUpperArm.transform.idt()
        rig.rightUpperArm.transform.setToTranslation(xOffset + 0.7f, groundY + 0.4f, -1.5f)
        rig.rightUpperArm.transform.rotate(Vector3.X, 90f)

        rig.leftForearm.transform.idt()
        rig.leftForearm.transform.setToTranslation(xOffset - 0.7f, groundY + 0.2f, -2.3f)

        rig.rightForearm.transform.idt()
        rig.rightForearm.transform.setToTranslation(xOffset + 0.7f, groundY + 0.2f, -2.3f)

        // Legs: knees on ground, lower legs folded back
        rig.leftUpperLeg.transform.idt()
        rig.leftUpperLeg.transform.setToTranslation(xOffset - 0.4f, groundY + 0.6f, 1f)
        rig.leftUpperLeg.transform.rotate(Vector3.X, -60f)

        rig.rightUpperLeg.transform.idt()
        rig.rightUpperLeg.transform.setToTranslation(xOffset + 0.4f, groundY + 0.6f, 1f)
        rig.rightUpperLeg.transform.rotate(Vector3.X, -60f)

        rig.leftLowerLeg.transform.idt()
        rig.leftLowerLeg.transform.setToTranslation(xOffset - 0.4f, groundY + 0.15f, 2f)
        rig.leftLowerLeg.transform.rotate(Vector3.X, -90f)

        rig.rightLowerLeg.transform.idt()
        rig.rightLowerLeg.transform.setToTranslation(xOffset + 0.4f, groundY + 0.15f, 2f)
        rig.rightLowerLeg.transform.rotate(Vector3.X, -90f)
    }

    /** Sitting between prostrations — Jalsa: kneeling upright */
    private fun poseJalsa(rig: HumanoidRig, xOffset: Float) {
        val groundY = 0f
        val seatY = groundY + 1.2f

        // Torso: upright but lower (sitting)
        rig.torso.transform.idt()
        rig.torso.transform.setToTranslation(xOffset, seatY + 1f, 0f)

        // Head: on top
        rig.head.transform.setToTranslation(xOffset, seatY + 2.5f, 0f)

        // Arms: resting on thighs
        rig.leftUpperArm.transform.idt()
        rig.leftUpperArm.transform.setToTranslation(xOffset - 0.8f, seatY + 1.2f, 0f)

        rig.rightUpperArm.transform.idt()
        rig.rightUpperArm.transform.setToTranslation(xOffset + 0.8f, seatY + 1.2f, 0f)

        rig.leftForearm.transform.idt()
        rig.leftForearm.transform.setToTranslation(xOffset - 0.6f, seatY + 0.2f, -0.4f)
        rig.leftForearm.transform.rotate(Vector3.X, 30f)

        rig.rightForearm.transform.idt()
        rig.rightForearm.transform.setToTranslation(xOffset + 0.6f, seatY + 0.2f, -0.4f)
        rig.rightForearm.transform.rotate(Vector3.X, 30f)

        // Legs folded underneath
        rig.leftUpperLeg.transform.idt()
        rig.leftUpperLeg.transform.setToTranslation(xOffset - 0.4f, seatY - 0.3f, 0.3f)
        rig.leftUpperLeg.transform.rotate(Vector3.X, -90f)

        rig.rightUpperLeg.transform.idt()
        rig.rightUpperLeg.transform.setToTranslation(xOffset + 0.4f, seatY - 0.3f, 0.3f)
        rig.rightUpperLeg.transform.rotate(Vector3.X, -90f)

        rig.leftLowerLeg.transform.idt()
        rig.leftLowerLeg.transform.setToTranslation(xOffset - 0.4f, groundY + 0.15f, 1.3f)
        rig.leftLowerLeg.transform.rotate(Vector3.X, -90f)

        rig.rightLowerLeg.transform.idt()
        rig.rightLowerLeg.transform.setToTranslation(xOffset + 0.4f, groundY + 0.15f, 1.3f)
        rig.rightLowerLeg.transform.rotate(Vector3.X, -90f)
    }

    /** Final sitting — Tashahhud: similar to Jalsa but right index finger extended */
    private fun poseTashahhud(rig: HumanoidRig, xOffset: Float) {
        // Tashahhud is very similar to Jalsa posture
        poseJalsa(rig, xOffset)

        // Right forearm slightly angled forward (pointing finger)
        rig.rightForearm.transform.idt()
        rig.rightForearm.transform.setToTranslation(xOffset + 0.5f, 1.4f, -0.8f)
        rig.rightForearm.transform.rotate(Vector3.X, 40f)
    }

    private fun buildGravityVectors() {
        // Dispose old models
        gravityArrowModels.forEach { it.dispose() }
        gravityArrowModels.clear()
        gravityArrowInstances.clear()

        if (filteredPoints.isEmpty()) return

        // Group by posture and calculate mean acceleration
        val postureGroups = filteredPoints.groupBy { it.posture }

        for ((posture, samples) in postureGroups) {
            if (samples.isEmpty()) continue

            val meanX = samples.map { it.meanAccelX }.average().toFloat()
            val meanY = samples.map { it.meanAccelY }.average().toFloat()
            val meanZ = samples.map { it.meanAccelZ }.average().toFloat()

            val magnitude = kotlin.math.sqrt(meanX * meanX + meanY * meanY + meanZ * meanZ)
            if (magnitude < 0.1f) continue

            // Build arrow model
            val modelBuilder = ModelBuilder()
            modelBuilder.begin()

            val postureColor = getPostureColor(posture)
            val arrowMaterial = Material(
                ColorAttribute.createDiffuse(postureColor),
                ColorAttribute.createSpecular(Color.WHITE)
            )

            // Arrow shaft (cylinder)
            val shaftLength = magnitude * 0.9f
            val shaftRadius = 0.15f
            modelBuilder.part(
                "shaft",
                GL20.GL_TRIANGLES,
                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
                arrowMaterial
            ).cylinder(shaftRadius, shaftLength, shaftRadius, 16)

            // Arrow head (cone)
            val headLength = magnitude * 0.2f
            val headRadius = 0.3f
            modelBuilder.part(
                "head",
                GL20.GL_TRIANGLES,
                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
                arrowMaterial
            ).cone(headRadius, headLength, headRadius, 16)

            val arrowModel = modelBuilder.end()
            gravityArrowModels.add(arrowModel)

            val arrowInstance = ModelInstance(arrowModel)

            // Orient arrow to point in direction of mean acceleration
            val direction = Vector3(meanX, meanY, meanZ).nor()
            val defaultUp = Vector3.Y
            val rotation = tmpMatrix.setToLookAt(direction, defaultUp)
            rotation.rotate(Vector3.X, 90f)
            arrowInstance.transform.set(rotation)

            gravityArrowInstances.add(Pair(posture, arrowInstance))
        }
    }

    private fun rebuildScatterInstances() {
        scatterInstances.clear()
        flaggedInstances.clear()

        val scale = pointSize * 0.15f
        val pos = Vector3()

        filteredPoints.forEachIndexed { fi, sample ->
            val originalIndex = filteredIndices[fi]
            samplePosition(originalIndex, sample, pos)

            val isFlagged = showDisagreements && originalIndex in flaggedIndices
            val instance = ModelInstance(scatterPointModel)
            val material = instance.materials.first()

            if (isFlagged) {
                // Red + emissive so it pops against the class colors; transform is
                // set every frame by the pulse in renderScatter.
                material.set(ColorAttribute.createDiffuse(Color(1f, 0.25f, 0.25f, 1f)))
                material.set(ColorAttribute.createEmissive(Color(0.5f, 0.05f, 0.05f, 1f)))
                flaggedInstances.add(instance to pos.cpy())
            } else {
                instance.transform.setToTranslation(pos)
                instance.transform.scale(scale, scale, scale)
                material.set(ColorAttribute.createDiffuse(getPostureColor(sample.posture)))
                scatterInstances.add(instance)
            }
        }

        rebuildEllipsoids()
    }

    private fun getPostureColor(posture: SalahPosture): Color {
        return when (posture) {
            SalahPosture.QIYAM -> Color(0f, 0.75f, 1f, 1f)
            SalahPosture.QIYAM_RISING -> Color(0f, 0.81f, 0.82f, 1f)
            SalahPosture.RUKU -> Color(1f, 0.55f, 0f, 1f)
            SalahPosture.GOING_TO_SUJUD -> Color(1f, 0.08f, 0.58f, 1f)
            SalahPosture.SUJUD -> Color(0.2f, 0.8f, 0.2f, 1f)
            SalahPosture.JALSA -> Color(0.58f, 0.44f, 0.86f, 1f)
            SalahPosture.TASHAHHUD -> Color(1f, 0.27f, 0f, 1f)
            else -> Color.GRAY
        }
    }

    private fun applyFilter() {
        val points = mutableListOf<SalahDataSample>()
        val indices = mutableListOf<Int>()
        dataPoints.forEachIndexed { index, sample ->
            if (sample.posture in visiblePostures) {
                points.add(sample)
                indices.add(index)
            }
        }
        filteredPoints = points
        filteredIndices = indices.toIntArray()
    }
}
