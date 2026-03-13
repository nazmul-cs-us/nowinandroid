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
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.starception.submission.ml.SalahDataSample
import com.starception.submission.ml.SalahPosture
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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
class SalahVisualization3D(
    private val onPlaybackUpdate: (Int, SalahPosture?, Float, Float, Float, Float) -> Unit
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

    // 3D Models
    private var phoneModel: Model? = null
    private var phoneInstance: ModelInstance? = null
    private var groundModel: Model? = null
    private var groundInstance: ModelInstance? = null

    // Gravity vectors
    private var gravityArrowModels: MutableList<Model> = mutableListOf()
    private var gravityArrowInstances: MutableList<Pair<SalahPosture, ModelInstance>> = mutableListOf()

    // Highlight
    private var highlightModel: Model? = null
    private var highlightInstance: ModelInstance? = null

    // Scatter point models (for efficient rendering)
    private var scatterPointModel: Model? = null
    private val scatterInstances: MutableList<ModelInstance> = mutableListOf()

    // Temporary vectors for calculations
    private val tmpVec3 = Vector3()
    private val tmpMatrix = Matrix4()

    override fun create() {
        // Setup camera
        camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.position.set(20f, 20f, 20f)
        camera.lookAt(0f, 0f, 0f)
        camera.near = 0.1f
        camera.far = 300f
        camera.update()

        // Setup camera controller
        cameraController = CameraInputController(camera)
        Gdx.input.inputProcessor = cameraController

        // Setup rendering
        modelBatch = ModelBatch()
        shapeRenderer = ShapeRenderer()
        shapeRenderer.setAutoShapeType(true)

        // Setup environment
        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f))
        environment.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f))

        // Build models
        buildPhoneModel()
        buildGroundPlane()
        buildHighlightModel()
        buildScatterPointModel()

        isReady = true
    }

    override fun render() {
        val delta = Gdx.graphics.deltaTime

        // Update camera
        cameraController.update()

        // Update playback
        if (isPlaying) {
            updatePlayback(delta)
        }

        // Clear screen
        Gdx.gl.glClearColor(0.04f, 0.04f, 0.1f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        // Enable depth testing
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        // Render based on mode
        when (currentMode) {
            VisualizationMode.SCATTER -> renderScatter()
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
        gravityArrowModels.forEach { it.dispose() }
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
        }
    }

    fun setMode(mode: VisualizationMode) {
        safePostRunnable {
            currentMode = mode

            when (mode) {
                VisualizationMode.GRAVITY_VECTOR -> buildGravityVectors()
                VisualizationMode.SCATTER -> rebuildScatterInstances()
                else -> { /* No special setup */ }
            }
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

    // ============================================
    // Private rendering methods
    // ============================================

    private fun renderScatter() {
        // Render scatter points using model instances
        modelBatch.begin(camera)
        for (instance in scatterInstances) {
            modelBatch.render(instance, environment)
        }

        // Render highlight for current playback position
        if (playbackIndex in dataPoints.indices) {
            val sample = dataPoints[playbackIndex]
            highlightInstance?.let { instance ->
                val x = sample.getAxisValue(axisX)
                val y = sample.getAxisValue(axisY)
                val z = sample.getAxisValue(axisZ)
                instance.transform.setToTranslation(x, y, z)
                instance.transform.scale(pointSize * 0.3f, pointSize * 0.3f, pointSize * 0.3f)
                modelBatch.render(instance, environment)
            }
        }
        modelBatch.end()
    }

    private fun renderPhone() {
        if (playbackIndex !in dataPoints.indices) return

        val sample = dataPoints[playbackIndex]
        val pitch = sample.pitch
        val roll = sample.roll

        // Update phone orientation
        phoneInstance?.let { instance ->
            instance.transform.idt()
            instance.transform.rotate(Vector3.X, pitch)
            instance.transform.rotate(Vector3.Z, roll)

            // Update phone color based on posture
            val postureColor = getPostureColor(sample.posture)
            val material = instance.materials.first()
            material.set(ColorAttribute.createDiffuse(postureColor))
        }

        // Render phone and ground
        modelBatch.begin(camera)
        phoneInstance?.let { modelBatch.render(it, environment) }
        groundInstance?.let { modelBatch.render(it, environment) }
        modelBatch.end()

        // Draw orientation info using shape renderer
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

        // Draw pitch arc
        shapeRenderer.setColor(1f, 0f, 0f, 0.5f)
        val pitchRadius = 5f
        for (i in 0..20) {
            val angle1 = (i / 20f) * pitch
            val angle2 = ((i + 1) / 20f) * pitch
            val x1 = pitchRadius * sin(Math.toRadians(angle1.toDouble())).toFloat()
            val y1 = pitchRadius * cos(Math.toRadians(angle1.toDouble())).toFloat()
            val x2 = pitchRadius * sin(Math.toRadians(angle2.toDouble())).toFloat()
            val y2 = pitchRadius * cos(Math.toRadians(angle2.toDouble())).toFloat()
            shapeRenderer.line(0f, y1, x1, 0f, y2, x2)
        }

        shapeRenderer.end()
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
        shapeRenderer.setColor(0.15f, 0.15f, 0.15f, 1f)

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
                sample.gyroMagnitude
            )
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

        val scale = pointSize * 0.15f

        for (sample in filteredPoints) {
            val x = sample.getAxisValue(axisX)
            val y = sample.getAxisValue(axisY)
            val z = sample.getAxisValue(axisZ)

            val instance = ModelInstance(scatterPointModel)
            instance.transform.setToTranslation(x, y, z)
            instance.transform.scale(scale, scale, scale)

            // Set color based on posture
            val postureColor = getPostureColor(sample.posture)
            val material = instance.materials.first()
            material.set(ColorAttribute.createDiffuse(postureColor))

            scatterInstances.add(instance)
        }
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
        filteredPoints = dataPoints.filter { it.posture in visiblePostures }
    }
}
