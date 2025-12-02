package com.starception.submission.islamic.qibla.presentation.component

import android.content.Context as AndroidContext
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.GeomagneticField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import com.starception.submission.R
import com.starception.submission.feature.prayertimes.utils.calculateQiblaDirection
import gov.nasa.worldwind.BasicWorldWindowController
import gov.nasa.worldwind.WorldWind
import gov.nasa.worldwind.WorldWindow
import gov.nasa.worldwind.geom.LookAt
import gov.nasa.worldwind.geom.Position
import gov.nasa.worldwind.gesture.GestureRecognizer
import gov.nasa.worldwind.layer.BackgroundLayer
import gov.nasa.worldwind.layer.BlueMarbleLayer
import gov.nasa.worldwind.layer.BlueMarbleLandsatLayer
import gov.nasa.worldwind.layer.RenderableLayer
import gov.nasa.worldwind.render.Color as WwColor
import gov.nasa.worldwind.render.ImageSource
import gov.nasa.worldwind.shape.Path
import gov.nasa.worldwind.shape.Placemark
import gov.nasa.worldwind.shape.PlacemarkAttributes
import gov.nasa.worldwind.shape.Polygon
import gov.nasa.worldwind.shape.ShapeAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Custom controller that tracks user touch interaction
 */
private class TouchTrackingController(
    private val onTouchStart: () -> Unit,
    private val onTouchEnd: () -> Unit
) : BasicWorldWindowController() {

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onTouchStart()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onTouchEnd()
            }
        }
        return super.onTouchEvent(event)
    }
}

/**
 * Qibla Globe View - 3D globe showing direction from user location to Makkah
 *
 * Displays a NASA WorldWind 3D globe with:
 * - User's current location marker
 * - Kaaba location in Makkah
 * - Great circle path showing Qibla direction
 * - Dynamic compass rotation that follows device orientation
 *
 * @param userLatitude User's latitude
 * @param userLongitude User's longitude
 * @param modifier Composable modifier
 */
@Composable
fun QiblaGlobeView(
    userLatitude: Double,
    userLongitude: Double,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Makkah coordinates (Kaaba)
    val makkahLatitude = 21.4225
    val makkahLongitude = 39.8262

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    // Use BoxWithConstraints to get actual tile dimensions for proper centering
    BoxWithConstraints(modifier = modifier) {
        val tileWidthPx = with(density) { maxWidth.toPx() }
        val tileHeightPx = with(density) { maxHeight.toPx() }

    // Compass sensor state for dynamic rotation
    var deviceHeading by remember { mutableFloatStateOf(0f) }
    var lastUpdatedHeading by remember { mutableFloatStateOf(0f) }  // Track last heading used for update
    var worldWindowRef by remember { mutableStateOf<WorldWindow?>(null) }
    var qiblaLayerRef by remember { mutableStateOf<RenderableLayer?>(null) }
    var isUserInteracting by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableStateOf(0L) }
    var wasAlignedWithQibla by remember { mutableStateOf(false) }
    var headingIndicator by remember { mutableStateOf<Polygon?>(null) }

    // Calculate magnetic declination for location
    val magneticDeclination = remember(userLatitude, userLongitude) {
        val geoField = GeomagneticField(
            userLatitude.toFloat(),
            userLongitude.toFloat(),
            0f, // altitude (can use 0 if not critical)
            System.currentTimeMillis()
        )
        geoField.declination
    }

    // Calculate Qibla direction from user location
    val qiblaDirection = remember(userLatitude, userLongitude) {
        calculateQiblaDirection(userLatitude, userLongitude).toFloat()
    }

    // Check if user is facing Qibla (within ±5 degrees)
    val angularDiff = kotlin.math.abs(deviceHeading - qiblaDirection)
    val normalizedDiff = if (angularDiff > 180f) 360f - angularDiff else angularDiff
    val isAlignedWithQibla = normalizedDiff <= 5f

    // Haptic feedback when becoming aligned
    LaunchedEffect(isAlignedWithQibla) {
        if (isAlignedWithQibla && !wasAlignedWithQibla) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            wasAlignedWithQibla = true
        } else if (!isAlignedWithQibla) {
            wasAlignedWithQibla = false
        }
    }

    // Sensor manager for compass - only active when user is NOT touching
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(AndroidContext.SENSOR_SERVICE) as SensorManager
        val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)

        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                    }
                }

                val rotationMatrix = FloatArray(9)
                val success = SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)

                if (success) {
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)

                    // Azimuth (heading) in radians, convert to degrees
                    var azimuthDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    if (azimuthDegrees < 0) {
                        azimuthDegrees += 360f
                    }

                    // Apply magnetic declination correction: magnetic north + declination = true north
                    val newHeading = azimuthDegrees + magneticDeclination

                    // Only update heading if change is significant (more than 1.5 degrees)
                    // Threshold lowered for smoother, more professional movement
                    val headingDiff = kotlin.math.abs(newHeading - lastUpdatedHeading)
                    val normalizedHeadingDiff = if (headingDiff > 180f) 360f - headingDiff else headingDiff

                    if (normalizedHeadingDiff > 1.5f) {
                        // Update the heading state (used for overlay calculations)
                        deviceHeading = newHeading
                        lastUpdatedHeading = newHeading

                        qiblaLayerRef?.let { layer ->
                            // Remove old heading indicator
                            headingIndicator?.let { oldCone ->
                                layer.removeRenderable(oldCone)
                            }

                            // Create new heading indicator with current heading
                            val newCone = createHeadingIndicator(userLatitude, userLongitude, newHeading)
                            layer.addRenderable(newCone)
                            headingIndicator = newCone

                            // Request redraw to show updated indicator
                            worldWindowRef?.requestRedraw()
                        }
                    }

                    // Globe rotation disabled - markers flip when camera heading changes
                    // The blue heading wedge provides sufficient visual feedback for user rotation
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // Register sensors
        magneticSensor?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        accelerometerSensor?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(sensorListener)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        // Create backdrop for liquid glass effect
        val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
        val backdrop = rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }

        // WorldWind globe view with lifecycle management
        AndroidView(
            factory = { ctx ->
                val (worldWindow, qiblaLayer, headingCone) = createWorldWindow(
                    context = ctx,
                    userLat = userLatitude,
                    userLon = userLongitude,
                    makkahLat = makkahLatitude,
                    makkahLon = makkahLongitude,
                    viewWidth = tileWidthPx.toInt(),
                    viewHeight = tileHeightPx.toInt(),
                    onTouchStart = {
                        isUserInteracting = true
                        lastInteractionTime = System.currentTimeMillis()
                    },
                    onTouchEnd = {
                        isUserInteracting = false
                        lastInteractionTime = System.currentTimeMillis()
                    }
                )

                // Store references for sensor updates
                worldWindowRef = worldWindow
                qiblaLayerRef = qiblaLayer
                headingIndicator = headingCone

                // Add lifecycle observer to properly manage GLSurfaceView
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            worldWindow.onResume()
                        }
                        Lifecycle.Event.ON_PAUSE -> {
                            worldWindow.onPause()
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)

                worldWindow
            },
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
            onRelease = { worldWindow ->
                // Clean up when view is removed
                worldWindow.onPause()
                worldWindowRef = null
            }
        )

        // Combined header: alignment status + directional guidance
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(12.dp) },
                    effects = {
                        vibrancy()
                        lens(with(density) { 16.dp.toPx() }, with(density) { 32.dp.toPx() })
                    }
                )
        ) {
            if (isAlignedWithQibla) {
                // Aligned state - compact text
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "✓ QIBLA",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Aligned",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 9.sp
                    )
                }
            } else {
                // Not aligned - show compact directional guidance
                // Calculate signed difference: positive = turn right, negative = turn left
                var diff = qiblaDirection - deviceHeading

                // Normalize to [-180, 180] for shortest path
                if (diff > 180f) diff -= 360f
                if (diff < -180f) diff += 360f

                val turnLeft = diff < 0
                val angleDiff = kotlin.math.abs(diff)

                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Direction arrow
                    Icon(
                        imageVector = if (turnLeft) Icons.Default.KeyboardArrowLeft else Icons.Default.KeyboardArrowRight,
                        contentDescription = if (turnLeft) "Turn left" else "Turn right",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (turnLeft) "LEFT" else "RIGHT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 9.sp
                        )
                        Text(
                            text = "${angleDiff.toInt()}°",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Second arrow for emphasis
                    Icon(
                        imageVector = if (turnLeft) Icons.Default.KeyboardArrowLeft else Icons.Default.KeyboardArrowRight,
                        contentDescription = if (turnLeft) "Turn left" else "Turn right",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Distance info overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(12.dp) },
                    effects = {
                        vibrancy()
                        lens(with(density) { 16.dp.toPx() }, with(density) { 32.dp.toPx() })
                    }
                )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "🕋 Kaaba",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Makkah, Saudi Arabia",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp
                )
            }
        }
    }
    }  // End BoxWithConstraints
}

/**
 * Create and configure WorldWind globe view with enhanced Qibla visualization
 */
private fun createWorldWindow(
    context: AndroidContext,
    userLat: Double,
    userLon: Double,
    makkahLat: Double,
    makkahLon: Double,
    viewWidth: Int,
    viewHeight: Int,
    onTouchStart: () -> Unit,
    onTouchEnd: () -> Unit
): Triple<WorldWindow, RenderableLayer, Polygon> {
    val worldWindow = WorldWindow(context)

    // Set up touch-tracking controller for pan/zoom with compass pause
    worldWindow.worldWindowController = TouchTrackingController(onTouchStart, onTouchEnd)

    // Add base layers for Earth imagery (CRITICAL - without these, globe is black!)
    worldWindow.layers.addLayer(BackgroundLayer())

    // Use BlueMarbleLandsatLayer for better texture stability
    // Note: This may have slightly less uniform ocean color but renders more consistently
    worldWindow.layers.addLayer(BlueMarbleLandsatLayer())

    // Add renderable layer for path and markers
    val qiblaLayer = RenderableLayer("Qibla Layer")
    worldWindow.layers.addLayer(qiblaLayer)

    // Create Position objects with elevation for better visibility
    val userPos = Position.fromDegrees(userLat, userLon, 200000.0)  // 200km elevation
    val kaabaPos = Position.fromDegrees(makkahLat, makkahLon, 200000.0)  // 200km elevation

    // Calculate Qibla direction from user to Kaaba
    val qiblaAzimuth = userPos.greatCircleAzimuth(kaabaPos)

    // Load custom icon bitmaps
    val userLocationBitmap = drawableToBitmap(context, R.drawable.ic_user_location_arrow)
    val kaabaBitmap = emojiToBitmap("🕋", sizeDp = 48)  // Use 🕋 emoji from overlay

    // 1. Add User Location Placemark with custom arrow icon (static)
    val userAttributes = PlacemarkAttributes().apply {
        imageSource = ImageSource.fromBitmap(userLocationBitmap)
        imageScale = 0.6  // Bigger for better visibility
    }
    val userPlacemark = Placemark(userPos, userAttributes).apply {
        altitudeMode = WorldWind.ABSOLUTE  // Use absolute altitude
    }
    qiblaLayer.addRenderable(userPlacemark)

    // 2. Add heading indicator (blue viewing cone) - shows direction user is FACING
    // Initial heading points north (0°)
    val headingCone = createHeadingIndicator(userLat, userLon, 0f)
    qiblaLayer.addRenderable(headingCone)

    // 3. Add Kaaba Placemark with 🕋 emoji icon
    val kaabaAttributes = PlacemarkAttributes().apply {
        imageSource = ImageSource.fromBitmap(kaabaBitmap)
        imageScale = 0.4  // Smaller, more subtle marker
    }
    val kaabaPlacemark = Placemark(kaabaPos, kaabaAttributes).apply {
        altitudeMode = WorldWind.ABSOLUTE  // Use absolute altitude
    }
    qiblaLayer.addRenderable(kaabaPlacemark)

    // Setup camera view optimized for tile dimensions - Google Maps style
    val globe = worldWindow.globe

    // Calculate great circle heading and distance
    val heading = userPos.greatCircleAzimuth(kaabaPos)
    val distanceRadians = userPos.greatCircleDistance(kaabaPos)
    val distanceMeters = distanceRadians * globe.equatorialRadius
    val earthRadius = globe.equatorialRadius

    // Center camera on user's location (Kaaba will be positioned in the direction of heading)
    val aspectRatio = viewWidth.toDouble() / viewHeight.toDouble()

    // Base range calculation: zoom out to show user centered with Kaaba visible
    // User at center, Kaaba positioned down in the view direction
    val baseRange = when {
        distanceMeters < earthRadius * 0.3 -> {
            // Very short distance: zoom in to show detail
            distanceMeters * 4.5
        }
        distanceMeters < earthRadius * 0.5 -> {
            // Short-medium distance (like Dubai-Makkah): show user centered
            distanceMeters * 4.0
        }
        distanceMeters < earthRadius * 1.0 -> {
            // Medium distance: balanced view with curvature
            distanceMeters * 3.2
        }
        else -> {
            // Long distance: zoom out to show full arc
            distanceMeters * 2.5
        }
    }

    // Adjust for aspect ratio - wider tiles can show more horizontally
    val aspectCorrectedRange = if (aspectRatio > 1.0) {
        baseRange * (0.9 / Math.sqrt(aspectRatio))  // Slight zoom in for wide tiles
    } else {
        baseRange * Math.sqrt(1.1 / aspectRatio)  // Zoom out more for tall tiles
    }

    // Ensure we're far enough to see Earth curvature with user centered
    val minRange = earthRadius * 2.0  // Minimum distance for centered user view
    val maxRange = earthRadius * 5.0  // Maximum distance to keep detail visible
    val finalRange = aspectCorrectedRange.coerceIn(minRange, maxRange)

    // Optimal tilt angle - lower values = more overhead view (user centered, Kaaba below)
    val tilt = when {
        distanceMeters < earthRadius * 0.3 -> 25.0  // More overhead for very close points
        distanceMeters < earthRadius * 0.5 -> 30.0  // Overhead angle - user centered, Kaaba visible below
        distanceMeters < earthRadius * 1.0 -> 35.0  // Medium tilt for medium distance
        else -> 40.0  // Shallower overhead for far points
    }

    // To truly center the user marker in the view with tilt, we need to raise the look-at altitude
    // This compensates for the camera angle - higher altitude brings the marker to screen center
    val centeringAltitude = finalRange * 0.15  // 15% of range works well for typical tilt angles

    val lookAt = LookAt().apply {
        set(
            userLat, userLon, centeringAltitude,  // Raised altitude to center user marker in view
            WorldWind.ABSOLUTE,
            finalRange,           // Optimized range for user-centered view
            heading,              // Orient view toward Kaaba direction
            tilt,                 // Dynamic tilt for user-centered perspective
            0.0                   // No roll
        )
    }

    worldWindow.navigator.setAsLookAt(globe, lookAt)

    return Triple(worldWindow, qiblaLayer, headingCone)
}

/**
 * Update globe view to keep both markers optimally visible
 * The camera stays positioned at the midpoint but rotates to show the best angle
 */
private fun updateGlobeViewForOptimalMarkerVisibility(
    worldWindow: WorldWindow,
    deviceHeading: Float,
    userLat: Double,
    userLon: Double,
    makkahLat: Double,
    makkahLon: Double
) {
    val globe = worldWindow.globe

    // Get current LookAt position
    val lookAt = LookAt()
    worldWindow.navigator.getAsLookAt(globe, lookAt)

    // Keep the camera centered on midpoint between user and Kaaba
    // Only update the heading to rotate the view smoothly
    // This keeps both markers in view while showing different angles

    // Subtle rotation based on device heading (reduced by 50% for stability)
    val adjustedHeading = (deviceHeading * 0.5) % 360.0
    lookAt.heading = adjustedHeading.toDouble()

    // Apply updated camera
    worldWindow.navigator.setAsLookAt(globe, lookAt)
    worldWindow.requestRedraw()
}

/**
 * Create a heading indicator (blue viewing cone) showing which direction the user is facing
 * Google Maps style - small, subtle wedge
 */
private fun createHeadingIndicator(userLat: Double, userLon: Double, heading: Float): Polygon {
    // Use altitude 0 for all positions since we'll clamp to ground
    val userPos = Position.fromDegrees(userLat, userLon, 0.0)

    // Create a small viewing wedge like Google Maps - narrower angle, shorter distance
    val coneAngle = 15.0  // 30-degree total spread (narrower than before)
    val coneDistance = 150000.0  // 150km distance (much shorter, more subtle)

    val leftAzimuth = heading - coneAngle
    val rightAzimuth = heading + coneAngle

    // Create cone points - convert Location to Position
    val leftLoc = userPos.greatCircleLocation(leftAzimuth, coneDistance, gov.nasa.worldwind.geom.Location())
    val centerLoc = userPos.greatCircleLocation(heading.toDouble(), coneDistance, gov.nasa.worldwind.geom.Location())
    val rightLoc = userPos.greatCircleLocation(rightAzimuth, coneDistance, gov.nasa.worldwind.geom.Location())

    // Use altitude 0 and let CLAMP_TO_GROUND handle surface positioning
    val leftPos = Position.fromDegrees(leftLoc.latitude, leftLoc.longitude, 0.0)
    val centerPos = Position.fromDegrees(centerLoc.latitude, centerLoc.longitude, 0.0)
    val rightPos = Position.fromDegrees(rightLoc.latitude, rightLoc.longitude, 0.0)

    val conePositions = ArrayList<Position>()
    conePositions.add(userPos)
    conePositions.add(leftPos)
    conePositions.add(centerPos)
    conePositions.add(rightPos)
    conePositions.add(userPos)  // Close the polygon

    val coneAttributes = ShapeAttributes().apply {
        // Google Maps style: Bright vibrant blue with professional opacity
        interiorColor = WwColor(0.26f, 0.52f, 0.96f, 0.45f)  // Google blue with subtle fill
        outlineColor = WwColor(0.26f, 0.52f, 0.96f, 0.75f)  // Professional blue outline
        outlineWidth = 3.0f  // Clean, professional thickness
    }
    val polygon = Polygon(conePositions, coneAttributes).apply {
        altitudeMode = WorldWind.CLAMP_TO_GROUND  // Fixed: Clamp to surface to prevent distortion during rotation
    }
    return polygon
}

/**
 * Update the heading indicator to show new direction
 *
 * TODO: This function is currently disabled due to WorldWind API limitations.
 * The Polygon class doesn't expose methods to update positions dynamically.
 * For now, the static heading indicator shows the initial direction.
 */
private fun updateHeadingIndicator(polygon: Polygon, userLat: Double, userLon: Double, heading: Float) {
    // Disabled - WorldWind Polygon API doesn't support dynamic position updates
    // The API doesn't expose getOuterBoundary() or similar methods to modify positions after creation

    /*
    val userPos = Position.fromDegrees(userLat, userLon, 200000.0)

    // Update cone with new heading
    val coneAngle = 22.5
    val coneDistance = 600000.0  // 600km distance for the cone

    val leftAzimuth = heading - coneAngle
    val rightAzimuth = heading + coneAngle

    // Update cone points - convert Location to Position
    val leftLoc = userPos.greatCircleLocation(leftAzimuth, coneDistance, gov.nasa.worldwind.geom.Location())
    val centerLoc = userPos.greatCircleLocation(heading.toDouble(), coneDistance, gov.nasa.worldwind.geom.Location())
    val rightLoc = userPos.greatCircleLocation(rightAzimuth, coneDistance, gov.nasa.worldwind.geom.Location())

    val leftPos = Position.fromDegrees(leftLoc.latitude, leftLoc.longitude, 200000.0)
    val centerPos = Position.fromDegrees(centerLoc.latitude, centerLoc.longitude, 200000.0)
    val rightPos = Position.fromDegrees(rightLoc.latitude, rightLoc.longitude, 200000.0)

    // Clear and set new positions
    // polygon.getOuterBoundary().clear()  // Method doesn't exist in API
    // polygon.getOuterBoundary().add(userPos)
    // ... etc
    */
}

/**
 * Calculate distance between two points in kilometers using Haversine formula
 */
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371.0 // km

    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)

    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)

    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

    return earthRadius * c
}

/**
 * Convert a drawable resource to a Bitmap for use with WorldWind placemarks
 */
private fun drawableToBitmap(context: AndroidContext, drawableId: Int): Bitmap {
    val drawable = ContextCompat.getDrawable(context, drawableId)
        ?: throw IllegalArgumentException("Drawable resource not found")

    if (drawable is BitmapDrawable) {
        return drawable.bitmap
    }

    // For vector drawables, create a bitmap and draw the drawable on it
    val bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth,
        drawable.intrinsicHeight,
        Bitmap.Config.ARGB_8888
    )

    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)

    return bitmap
}

/**
 * Create a bitmap from emoji text for use with WorldWind placemarks
 */
private fun emojiToBitmap(emoji: String, sizeDp: Int = 64): Bitmap {
    val size = sizeDp * 3 // Scale up for better quality
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = android.graphics.Paint().apply {
        textSize = size * 0.75f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }

    // Calculate vertical center (accounting for baseline offset)
    val textBounds = android.graphics.Rect()
    paint.getTextBounds(emoji, 0, emoji.length, textBounds)
    val y = (size - textBounds.height()) / 2f - textBounds.top

    canvas.drawText(emoji, size / 2f, y, paint)

    return bitmap
}
