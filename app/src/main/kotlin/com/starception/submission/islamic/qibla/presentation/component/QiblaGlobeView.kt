package com.starception.submission.islamic.qibla.presentation.component

import android.content.Context as AndroidContext
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.GeomagneticField
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * Always returns true to claim the touch sequence and prevent Compose from cancelling it
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
        // Let WorldWindow handle the gesture
        super.onTouchEvent(event)
        // Always return true to claim touch events
        return true
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

        android.util.Log.d("QiblaGlobeView", "📐 Tile dimensions: width=${tileWidthPx}px (${maxWidth}), height=${tileHeightPx}px (${maxHeight})")

    // Compass sensor state for dynamic rotation
    var deviceHeading by remember { mutableFloatStateOf(0f) }
    var lastUpdatedHeading by remember { mutableFloatStateOf(0f) }  // Track last heading used for update
    var worldWindowRef by remember { mutableStateOf<WorldWindow?>(null) }
    var qiblaLayerRef by remember { mutableStateOf<RenderableLayer?>(null) }
    var isUserInteracting by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableStateOf(0L) }
    var wasAlignedWithQibla by remember { mutableStateOf(false) }
    var userMarkerPlacemark by remember { mutableStateOf<Placemark?>(null) }
    var isInForeground by remember { mutableStateOf(true) } // Track if app is in foreground for haptic control

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

    // Debug logging to compare with QiblaCompass
    android.util.Log.d("QiblaGlobeAlignment", "deviceHeading=$deviceHeading, qiblaDirection=$qiblaDirection, normalizedDiff=$normalizedDiff, isAligned=$isAlignedWithQibla, declination=$magneticDeclination")

    // Haptic feedback when becoming aligned - only in foreground to avoid background vibration
    LaunchedEffect(isAlignedWithQibla, isInForeground) {
        if (isAlignedWithQibla && !wasAlignedWithQibla && isInForeground) {
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
                        // Update the heading state
                        deviceHeading = newHeading
                        lastUpdatedHeading = newHeading

                        // Update user marker with RELATIVE heading to Qibla
                        // When aligned (diff = 0), beam points UP toward Qibla
                        // When not aligned, beam shows how much user needs to turn
                        userMarkerPlacemark?.let { placemark ->
                            val relativeHeading = newHeading - qiblaDirection
                            val newBitmap = createUserMarkerWithHeadingShadow(relativeHeading)
                            placemark.attributes.imageSource = ImageSource.fromBitmap(newBitmap)
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
            .fillMaxSize()  // Fill both width and height from parent constraints
            // Removed clip - can interfere with touch handling on AndroidView
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp)
            )
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
                    userMarkerPlacemark = headingCone

                    // CRITICAL: Enable touch handling for WorldWindow (GLSurfaceView)
                    worldWindow.isFocusable = true
                    worldWindow.isFocusableInTouchMode = true
                    worldWindow.isClickable = true

                    // Handle touches directly and forward to controller
                    // This is required because Compose's AndroidView doesn't properly forward
                    // touch events to GLSurfaceView (WorldWindow) by default
                    worldWindow.setOnTouchListener { v, event ->
                        // Request parent to not intercept our touch events
                        v.parent?.requestDisallowInterceptTouchEvent(true)

                        // Forward touch event to WorldWindow's controller for gesture handling
                        worldWindow.worldWindowController?.onTouchEvent(event)

                        // Return true to claim the touch sequence and prevent cancellation
                        true
                    }

                    // Add lifecycle observer to properly manage GLSurfaceView
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> {
                                isInForeground = true
                                worldWindow.onResume()
                            }
                            Lifecycle.Event.ON_PAUSE -> {
                                isInForeground = false
                                worldWindow.onPause()
                            }
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)

                    worldWindow.requestFocus()
                    worldWindow
                },
                modifier = Modifier.fillMaxSize(),
                update = { worldWindow ->
                    worldWindow.requestLayout()
                    if (!worldWindow.hasFocus()) {
                        worldWindow.requestFocus()
                    }
                },
                onRelease = { worldWindow ->
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
                        imageVector = if (turnLeft) Icons.AutoMirrored.Outlined.ArrowBackIos else Icons.AutoMirrored.Outlined.ArrowForwardIos,
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
                        imageVector = if (turnLeft) Icons.AutoMirrored.Outlined.ArrowBackIos else Icons.AutoMirrored.Outlined.ArrowForwardIos,
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

        // Locate Me button - resets view to show user and Kaaba (with liquid glass effect)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(44.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        lens(with(density) { 8.dp.toPx() }, with(density) { 16.dp.toPx() })
                    }
                )
                .clickable {
                    // Reset camera to show both user and Kaaba
                    worldWindowRef?.let { ww ->
                        resetCameraToShowBoth(
                            ww,
                            userLatitude,
                            userLongitude,
                            makkahLatitude,
                            makkahLongitude
                        )
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Show my location and Kaaba",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
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
): Triple<WorldWindow, RenderableLayer, Placemark> {
    val worldWindow = WorldWindow(context)

    // Explicitly set layout parameters to ensure proper viewport sizing
    worldWindow.layoutParams = android.view.ViewGroup.LayoutParams(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.ViewGroup.LayoutParams.MATCH_PARENT
    )
    android.util.Log.d("QiblaGlobeView", "🌍 WorldWindow created with explicit size: ${viewWidth}x${viewHeight}")

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

    // Create Google Maps style user marker with heading shadow
    val userMarkerBitmap = createUserMarkerWithHeadingShadow(0f)  // Initial heading 0
    val kaabaBitmap = emojiToBitmap("🕋", sizeDp = 48)

    // 1. Add User Location Placemark with heading shadow marker
    val userAttributes = PlacemarkAttributes().apply {
        imageSource = ImageSource.fromBitmap(userMarkerBitmap)
        imageScale = 0.5
    }
    val userPlacemark = Placemark(userPos, userAttributes).apply {
        altitudeMode = WorldWind.ABSOLUTE
    }
    qiblaLayer.addRenderable(userPlacemark)

    // 2. User location marker with heading shadow is handled by the placemark itself
    // No separate heading indicator - we'll rotate the user marker bitmap instead
    val headingCone: Polygon? = null  // Not used - heading shown via rotated marker

    // 3. Add Kaaba Placemark with emoji icon
    val kaabaAttributes = PlacemarkAttributes().apply {
        imageSource = ImageSource.fromBitmap(kaabaBitmap)
        imageScale = 0.4  // Original size
    }
    val kaabaPlacemark = Placemark(kaabaPos, kaabaAttributes).apply {
        altitudeMode = WorldWind.ABSOLUTE
    }
    qiblaLayer.addRenderable(kaabaPlacemark)

    // Setup camera view to show BOTH user location AND Kaaba - ZOOMED IN
    val globe = worldWindow.globe

    // Calculate great circle heading and distance
    val heading = userPos.greatCircleAzimuth(kaabaPos)
    val distanceRadians = userPos.greatCircleDistance(kaabaPos)
    val distanceMeters = distanceRadians * globe.equatorialRadius
    val earthRadius = globe.equatorialRadius

    // Calculate midpoint between user and Kaaba for camera centering
    val midLat = (userLat + makkahLat) / 2.0
    val midLon = (userLon + makkahLon) / 2.0

    android.util.Log.d("QiblaGlobeView", "📷 Camera setup: distance=${(distanceMeters/1000).toInt()}km, midpoint=($midLat, $midLon)")

    // ZOOMED IN: Closer range to fill the tile with the globe
    // Use 1.8x distance to show both markers with tight framing
    val baseRange = distanceMeters * 1.8

    // Tighter zoom limits - globe fills more of the tile
    val minRange = earthRadius * 0.8   // Allow closer zoom
    val maxRange = earthRadius * 2.5   // Don't zoom out too far
    val finalRange = baseRange.coerceIn(minRange, maxRange)

    // Tilt angle - 50° for good 3D perspective
    val tilt = 50.0

    val lookAt = LookAt().apply {
        set(
            midLat, midLon, 0.0,  // Center on midpoint between user and Kaaba
            WorldWind.ABSOLUTE,
            finalRange,           // Closer range - globe fills tile
            heading,              // Orient view along Qibla direction
            tilt,                 // 3D perspective
            0.0                   // No roll
        )
    }

    android.util.Log.d("QiblaGlobeView", "🎯 Camera: midpoint=($midLat, $midLon), range=${(finalRange/1000).toInt()}km, heading=$heading°")

    worldWindow.navigator.setAsLookAt(globe, lookAt)

    return Triple(worldWindow, qiblaLayer, userPlacemark)
}

/**
 * Reset camera to show both user location and Kaaba
 * Called when user taps the "Locate Me" button
 */
private fun resetCameraToShowBoth(
    worldWindow: WorldWindow,
    userLat: Double,
    userLon: Double,
    makkahLat: Double,
    makkahLon: Double
) {
    val globe = worldWindow.globe
    val userPos = Position.fromDegrees(userLat, userLon, 0.0)
    val kaabaPos = Position.fromDegrees(makkahLat, makkahLon, 0.0)

    // Calculate heading and distance
    val heading = userPos.greatCircleAzimuth(kaabaPos)
    val distanceRadians = userPos.greatCircleDistance(kaabaPos)
    val distanceMeters = distanceRadians * globe.equatorialRadius
    val earthRadius = globe.equatorialRadius

    // Midpoint between user and Kaaba
    val midLat = (userLat + makkahLat) / 2.0
    val midLon = (userLon + makkahLon) / 2.0

    // Calculate range to show both
    val baseRange = distanceMeters * 1.8
    val minRange = earthRadius * 0.8
    val maxRange = earthRadius * 2.5
    val finalRange = baseRange.coerceIn(minRange, maxRange)

    val lookAt = LookAt().apply {
        set(
            midLat, midLon, 0.0,
            WorldWind.ABSOLUTE,
            finalRange,
            heading,
            50.0,  // tilt
            0.0    // roll
        )
    }

    worldWindow.navigator.setAsLookAt(globe, lookAt)
    worldWindow.requestRedraw()

    android.util.Log.d("QiblaGlobeView", "📍 Camera reset to show user ($userLat, $userLon) and Kaaba")
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
 * Create a Google Maps style user location marker with all elements:
 * 1. Small light blue accuracy circle
 * 2. Blue heading cone with radar/torch gradient effect
 * 3. White ring around center
 * 4. Solid blue dot (center)
 */
private fun createUserMarkerWithHeadingShadow(heading: Float): Bitmap {
    val size = 400
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val centerX = size / 2f
    val centerY = size / 2f

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
    }

    // Define white ring radius first (used by both beam and ring)
    val whiteRingRadius = size * 0.09f

    // 1. HEADING BEAM - Natural light beam that fades out gradually
    canvas.save()
    canvas.rotate(heading, centerX, centerY)

    // Draw multiple layered ovals that fade out - creates natural light beam effect
    val beamColor = 0x4285F4  // Google blue (without alpha)
    val numLayers = 12
    val maxLength = size * 0.48f
    val maxWidth = size * 0.32f

    for (i in numLayers downTo 1) {
        val progress = i.toFloat() / numLayers
        val layerLength = maxLength * progress
        val layerWidth = maxWidth * progress * 0.8f  // Narrower near center

        // Alpha decreases as we go further (outer layers are more transparent)
        val alpha = (200 * (1f - progress * 0.7f)).toInt().coerceIn(20, 200)

        paint.color = (alpha shl 24) or beamColor

        // Draw oval for each layer - creates soft gradient effect
        val ovalLeft = centerX - layerWidth / 2
        val ovalTop = centerY - layerLength - whiteRingRadius * 0.5f
        val ovalRight = centerX + layerWidth / 2
        val ovalBottom = centerY - whiteRingRadius * 0.3f

        canvas.drawOval(ovalLeft, ovalTop, ovalRight, ovalBottom, paint)
    }

    canvas.restore()

    // 3. WHITE RING - around the center dot (uses whiteRingRadius defined above)
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(centerX, centerY, whiteRingRadius, paint)

    // 4. BLUE DOT - solid center
    val blueDotRadius = size * 0.06f
    paint.color = 0xFF4285F4.toInt()
    canvas.drawCircle(centerX, centerY, blueDotRadius, paint)

    return bitmap
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
