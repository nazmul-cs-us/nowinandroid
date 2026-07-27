package com.starception.submission.islamic.qibla.presentation.component

import android.content.Context as AndroidContext
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.GeomagneticField
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.graphics.graphicsLayer
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
import earth.worldwind.WorldWindow
import earth.worldwind.geom.AltitudeMode
import earth.worldwind.geom.Angle.Companion.degrees
import earth.worldwind.geom.LookAt
import earth.worldwind.geom.Position
import earth.worldwind.layer.BackgroundLayer
import earth.worldwind.layer.BlueMarbleLandsatLayer
import earth.worldwind.layer.RenderableLayer
import earth.worldwind.layer.atmosphere.AtmosphereLayer
import earth.worldwind.render.image.ImageSource
import earth.worldwind.shape.Placemark
import earth.worldwind.shape.Polygon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    modifier: Modifier = Modifier,
    showControls: Boolean = true,  // Set to false to hide overlay buttons and info cards
    isActiveTile: Boolean = true,  // When this becomes true, plays a one-time day/night sweep
    surfaceCornerRadius: Dp = 16.dp,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Makkah coordinates (Kaaba)
    val makkahLatitude = 21.4225
    val makkahLongitude = 39.8262

    val density = LocalDensity.current
    val surfaceCornerRadiusPx = with(density) { surfaceCornerRadius.toPx() }
    val haptic = LocalHapticFeedback.current

    // Use BoxWithConstraints to get actual tile dimensions for proper centering
    BoxWithConstraints(modifier = modifier) {
        val tileWidthPx = with(density) { maxWidth.toPx() }
        val tileHeightPx = with(density) { maxHeight.toPx() }

        android.util.Log.d("QiblaGlobeView", "📐 Tile dimensions: width=${tileWidthPx}px (${maxWidth}), height=${tileHeightPx}px (${maxHeight})")

    // Compass sensor state for dynamic rotation
    var deviceHeading by remember { mutableFloatStateOf(0f) }
    var lastUpdatedHeading by remember { mutableFloatStateOf(0f) }  // Track last heading used for update

    val animatedHeading by animateFloatAsState(
        targetValue = deviceHeading,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "smoothBeamHeading"
    )
    var worldWindowRef by remember { mutableStateOf<WorldWindow?>(null) }
    var qiblaLayerRef by remember { mutableStateOf<RenderableLayer?>(null) }
    var isUserInteracting by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableStateOf(0L) }
    var wasAlignedWithQibla by remember { mutableStateOf(false) }
    var userMarkerPlacemark by remember { mutableStateOf<Placemark?>(null) }
    var isInForeground by remember { mutableStateOf(true) } // Track if app is in foreground for haptic control
    var sensorAccuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    var magneticFieldStrength by remember { mutableFloatStateOf(50f) } // For strength-based accuracy like Smart Prediction
    var hasPlayedDayNightSweep by remember { mutableStateOf(false) }

    // Debounce direction changes to prevent flickering when angle is near 180°
    var stableDirection by remember { mutableStateOf<Boolean?>(null) } // true = turn right, false = turn left
    var lastDirectionChangeTime by remember { mutableLongStateOf(0L) }

    // Throttling for sensor updates to prevent jitter (same as Smart Prediction - 50ms)
    var lastSensorUpdateTime by remember { mutableLongStateOf(0L) }
    val SENSOR_UPDATE_INTERVAL_MS = 50L

    // Low-pass filter for smoothing sensor data (reduces jitter from noisy accelerometer/magnetometer)
    var filteredHeading by remember { mutableFloatStateOf(0f) }
    val SMOOTHING_FACTOR = 0.03f  // Very low = very smooth (raw data jumps 50-100° even when still!)

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

    // Sensor manager for compass - only active when user is NOT touching.
    // Also keyed on isActiveTile: while the card is stacked behind others the
    // globe is hidden, so compass work would be wasted — sensors unregister
    // and re-register when the card lands on front again.
    DisposableEffect(context, isActiveTile) {
        if (!isActiveTile) {
            return@DisposableEffect onDispose { }
        }
        val sensorManager = context.getSystemService(AndroidContext.SENSOR_SERVICE) as SensorManager
        // Use TYPE_ORIENTATION for stable heading (same as Smart Prediction tile)
        // TYPE_ORIENTATION is deprecated but provides pre-filtered, stable values
        val orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        // Orientation sensor listener - for stable compass heading (like Smart Prediction)
        val orientationListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Throttle sensor updates (same as Smart Prediction - 50ms)
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSensorUpdateTime < SENSOR_UPDATE_INTERVAL_MS) {
                    return
                }
                lastSensorUpdateTime = currentTime

                // Get pre-filtered heading from TYPE_ORIENTATION (much more stable than manual calculation)
                val rawHeading = event.values[0] + magneticDeclination

                // Only update heading if change is significant (3° threshold like Smart Prediction)
                val headingDiff = kotlin.math.abs(rawHeading - lastUpdatedHeading)
                val normalizedHeadingDiff = if (headingDiff > 180f) 360f - headingDiff else headingDiff

                if (normalizedHeadingDiff > 3f) {
                    deviceHeading = rawHeading
                    lastUpdatedHeading = rawHeading
                    // The marker bitmap (heading + accuracy color + breathing pulse) is
                    // regenerated continuously by the pulse loop below, which reads the
                    // latest deviceHeading — so we only record the new heading here.
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Not used - accuracy from magnetic field listener
            }
        }

        // Magnetic field listener - for accuracy detection only (same as Smart Prediction)
        val magneticFieldListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Calculate magnetic field strength for accuracy detection
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val strength = kotlin.math.sqrt(x * x + y * y + z * z)
                magneticFieldStrength = strength

                // Determine accuracy based on magnetic field strength
                sensorAccuracy = when {
                    strength < 15f -> SensorManager.SENSOR_STATUS_ACCURACY_LOW
                    strength > 100f -> SensorManager.SENSOR_STATUS_ACCURACY_LOW
                    strength < 25f -> SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                    else -> SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // Register sensors - SAME as Smart Prediction tile
        orientationSensor?.let {
            sensorManager.registerListener(orientationListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magneticSensor?.let {
            sensorManager.registerListener(magneticFieldListener, it, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager.unregisterListener(orientationListener)
            sensorManager.unregisterListener(magneticFieldListener)
        }
    }

    // Breathing pulse for the user dot (Google Maps style), matching the Smart
    // Prediction globe. Regenerates the marker bitmap at ~20fps with a varying dot
    // radius while the tile is in the foreground; the cone and white ring stay
    // constant and the fill stays the accuracy-driven color — only the dot scales.
    LaunchedEffect(isInForeground, isActiveTile) {
        if (!isInForeground || !isActiveTile) return@LaunchedEffect
        val cycleMs = 2200f
        while (true) {
            val phase = (System.currentTimeMillis() % cycleMs.toLong()) / cycleMs
            val dotScale = DOT_PULSE_MIN +
                (1f - DOT_PULSE_MIN) *
                (0.5f - 0.5f * kotlin.math.cos(2.0 * Math.PI * phase).toFloat())
            userMarkerPlacemark?.let { placemark ->
                val relativeHeading = deviceHeading - qiblaDirection
                val aligned = kotlin.math.abs(relativeHeading) <= 5f ||
                    kotlin.math.abs(relativeHeading) >= 355f
                val color = when {
                    aligned && sensorAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> 0xFF00C853.toInt()
                    sensorAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> 0xFF10B981.toInt()
                    sensorAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 0xFFFFA500.toInt()
                    sensorAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW -> 0xFFFF6B6B.toInt()
                    else -> 0xFFFF4444.toInt()
                }
                val bmp = createUserMarkerWithHeadingShadow(relativeHeading, color, dotScale)
                placemark.attributes.imageSource = ImageSource.fromBitmap(bmp)
                worldWindowRef?.requestRedraw()
            }
            kotlinx.coroutines.delay(50)
        }
    }

    // When the user lands on the Kaaba tile, play a one-time ~4s day/night sweep — the
    // terminator rolls a full 24h loop and lands back on the real current time — then keep
    // the terminator tracking real time each minute. Re-runs whenever the tile re-activates.
    LaunchedEffect(isActiveTile, isInForeground) {
        if (!isActiveTile || !isInForeground) return@LaunchedEffect

        fun atmosphere(): AtmosphereLayer? =
            worldWindowRef?.engine?.layers?.firstOrNull { it is AtmosphereLayer } as? AtmosphereLayer
        // Wait for the globe + its AtmosphereLayer to be created.
        while (atmosphere() == null) kotlinx.coroutines.delay(50)

        fun setTime(instant: kotlin.time.Instant) {
            atmosphere()?.time = instant
            worldWindowRef?.requestRedraw()
        }

        if (!hasPlayedDayNightSweep) {
            // Play this once for the retained surface, not after every carousel swipe.
            val baseMs = System.currentTimeMillis()
            val dayMs = 24L * 60 * 60 * 1000
            val durationMs = 4000L
            val startMs = System.currentTimeMillis()
            while (true) {
                val t = ((System.currentTimeMillis() - startMs).toFloat() / durationMs)
                    .coerceIn(0f, 1f)
                val eased = 0.5f - 0.5f * kotlin.math.cos((t * Math.PI).toFloat())
                setTime(kotlin.time.Instant.fromEpochMilliseconds(baseMs + (eased * dayMs).toLong()))
                if (t >= 1f) break
                kotlinx.coroutines.delay(16)
            }
            hasPlayedDayNightSweep = true
        }

        // Settle on real time, then keep it current while the tile stays active + foreground.
        while (true) {
            setTime(nowInstant())
            kotlinx.coroutines.delay(60_000L)
        }
    }

    // ONE live globe across deck shuffles: while the card is stacked behind
    // others, the GL surface is HIDDEN (un-punching its hole so it can't bleed
    // through the transformed deck cards) and the render thread paused; when
    // the card lands on the front the surface is shown and nudged to redraw.
    // The EGL context is preserved across the pause, so coming back is a fast
    // redraw with warm textures — not the black-flashing cold recreate the
    // old live/static composition swap caused.
    // Keep the SurfaceView laid out at one stable position. Alpha does not destroy its
    // Surface, so the last GL buffer is immediately available when the carousel returns.
    // Translating a SurfaceView off-screen caused stale SurfaceControl copies to bleed
    // into adjacent carousel items on some devices.
    LaunchedEffect(isActiveTile, worldWindowRef) {
        val ww = worldWindowRef ?: return@LaunchedEffect
        ww.translationX = 0f
        if (isActiveTile) {
            ww.alpha = 1f
            ww.requestRedraw()
        } else {
            ww.alpha = 0f
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()  // Fill both width and height from parent constraints
            // Removed clip - can interfere with touch handling on AndroidView
            // Space-black, not theme surface: this backs the GL hole, so it must
            // match the globe scene — a light theme color here flashes white
            // while a fresh surface waits for its first frame.
            .background(
                color = Color(0xFF070B10),
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

                    // Disable touch interactions on globe to allow HorizontalPager swiping
                    // The globe is display-only, compass rotation is automatic
                    worldWindow.isFocusable = false
                    worldWindow.isFocusableInTouchMode = false
                    worldWindow.isClickable = false
                    worldWindow.setOnTouchListener { _, _ -> false }
                    worldWindow.alpha = if (isActiveTile) 1f else 0f
                    worldWindow.clipToOutline = true
                    worldWindow.outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, surfaceCornerRadiusPx)
                        }
                    }
                    worldWindow.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                        view.clipBounds = android.graphics.Rect(0, 0, view.width, view.height)
                        view.invalidateOutline()
                    }

                    // Survive app pause/resume without re-uploading textures.
                    worldWindow.preserveEGLContextOnPause = true

                    // DEBUG: trace the GL surface lifecycle to find black-frame gaps.
                    android.util.Log.d("GlobeSurface", "🏗️ WorldWindow CREATED @${android.os.SystemClock.uptimeMillis()}")
                    worldWindow.holder.addCallback(object : android.view.SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                            android.util.Log.d("GlobeSurface", "🟢 surfaceCreated @${android.os.SystemClock.uptimeMillis()}")
                        }
                        override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {
                            android.util.Log.d("GlobeSurface", "🔄 surfaceChanged ${width}x$height @${android.os.SystemClock.uptimeMillis()}")
                        }
                        override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                            android.util.Log.d("GlobeSurface", "🔴 surfaceDestroyed @${android.os.SystemClock.uptimeMillis()}")
                        }
                    })
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

                    worldWindow
                },
                modifier = Modifier.fillMaxSize(),
                update = { worldWindow ->
                    worldWindow.alpha = if (isActiveTile) 1f else 0f
                    worldWindow.clipBounds = android.graphics.Rect(
                        0,
                        0,
                        worldWindow.width,
                        worldWindow.height,
                    )
                    worldWindow.invalidateOutline()
                },
                onRelease = { worldWindow ->
                    android.util.Log.d("GlobeSurface", "🗑️ WorldWindow RELEASED @${android.os.SystemClock.uptimeMillis()}")
                    worldWindow.onPause()
                    worldWindowRef = null
                }
            )

        // Only show overlay controls when showControls is true
        if (showControls) {
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
                    // Use animatedHeading for smooth, stable angle display (like Smart Prediction)
                    var diff = qiblaDirection - animatedHeading

                    // Normalize to [-180, 180] for shortest path
                    if (diff > 180f) diff -= 360f
                    if (diff < -180f) diff += 360f

                    val rawTurnRight = diff > 0
                    val angleDiff = kotlin.math.abs(diff)

                    // Debounce direction changes to prevent flickering near 180°
                    val currentTime = System.currentTimeMillis()
                    val turnRight = if (stableDirection == null) {
                        // First time - set immediately
                        stableDirection = rawTurnRight
                        lastDirectionChangeTime = currentTime
                        rawTurnRight
                    } else if (stableDirection != rawTurnRight) {
                        // Direction wants to change - only allow if:
                        // 1. It's been at least 800ms since last change, OR
                        // 2. The angle is very clear (far from 180°)
                        val timeSinceLastChange = currentTime - lastDirectionChangeTime
                        val isVeryClearDirection = angleDiff < 30f || angleDiff > 330f

                        if (timeSinceLastChange > 800 || isVeryClearDirection) {
                            stableDirection = rawTurnRight
                            lastDirectionChangeTime = currentTime
                            rawTurnRight
                        } else {
                            // Keep old direction (debounce)
                            stableDirection!!
                        }
                    } else {
                        rawTurnRight
                    }

                    val turnLeft = !turnRight

                    // Round angle to nearest 5 degrees for stable display
                    val displayAngle = ((angleDiff / 5f).toInt() * 5)

                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Left arrow with horizontal slide animation
                        AnimatedContent(
                            targetState = turnLeft,
                            transitionSpec = {
                                // Slide horizontally - natural direction for arrows
                                (slideInHorizontally { width -> -width } + fadeIn(
                                    animationSpec = tween(200)
                                )) togetherWith (slideOutHorizontally { width -> width } + fadeOut(
                                    animationSpec = tween(200)
                                ))
                            },
                            label = "leftArrow"
                        ) { isLeft ->
                            Icon(
                                imageVector = if (isLeft) Icons.AutoMirrored.Outlined.ArrowBackIos else Icons.AutoMirrored.Outlined.ArrowForwardIos,
                                contentDescription = if (isLeft) "Turn left" else "Turn right",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Direction text with horizontal slide (matches arrow direction)
                            AnimatedContent(
                                targetState = turnLeft,
                                transitionSpec = {
                                    // Slide in direction of turn
                                    val slideDirection = if (targetState) -1 else 1
                                    (slideInHorizontally { width -> slideDirection * width } + fadeIn(
                                        animationSpec = tween(250)
                                    )) togetherWith (slideOutHorizontally { width -> -slideDirection * width } + fadeOut(
                                        animationSpec = tween(250)
                                    ))
                                },
                                label = "directionText"
                            ) { isLeft ->
                                Text(
                                    text = if (isLeft) "LEFT" else "RIGHT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 7.sp
                                )
                            }

                            // Airport-style scrolling angle display (compact)
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Animate each digit separately for airport board effect
                                val angleStr = displayAngle.toString().padStart(3, ' ')
                                angleStr.forEach { char ->
                                    if (char == ' ') {
                                        Spacer(modifier = Modifier.width(4.dp))
                                    } else {
                                        AnimatedContent(
                                            targetState = char,
                                            transitionSpec = {
                                                // Scroll up when increasing, down when decreasing
                                                val direction = if (targetState > initialState) -1 else 1
                                                slideInVertically(
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMedium
                                                    )
                                                ) { height -> direction * height } + fadeIn() togetherWith
                                                slideOutVertically(
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMedium
                                                    )
                                                ) { height -> -direction * height } + fadeOut()
                                            },
                                            label = "digitScroll"
                                        ) { digit ->
                                            Text(
                                                text = digit.toString(),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "°",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Second arrow with matching animation
                        AnimatedContent(
                            targetState = turnLeft,
                            transitionSpec = {
                                // Slide horizontally - opposite direction from first arrow
                                (slideInHorizontally { width -> width } + fadeIn(
                                    animationSpec = tween(200)
                                )) togetherWith (slideOutHorizontally { width -> -width } + fadeOut(
                                    animationSpec = tween(200)
                                ))
                            },
                            label = "rightArrow"
                        ) { isLeft ->
                            Icon(
                                imageVector = if (isLeft) Icons.AutoMirrored.Outlined.ArrowBackIos else Icons.AutoMirrored.Outlined.ArrowForwardIos,
                                contentDescription = if (isLeft) "Turn left" else "Turn right",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
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

    // Uses the WorldWindow's default controller; touch is disabled via setOnTouchListener
    // so HorizontalPager swiping works. Layers/camera live on worldWindow.engine in WWK.

    // Add base layers for Earth imagery (CRITICAL - without these, globe is black!)
    worldWindow.engine.layers.addLayer(BackgroundLayer())

    // Use BlueMarbleLandsatLayer for better texture stability
    // Note: This may have slightly less uniform ocean color but renders more consistently
    worldWindow.engine.layers.addLayer(BlueMarbleLandsatLayer())

    // Day/night cycle: darkens the night side (with a city-lights texture) and draws the
    // real terminator from the sun position at `time`. Added above the imagery but below
    // the markers so the dot/Kaaba/cone stay lit. Time is refreshed on a timer in the
    // composable so the terminator tracks the real time of day.
    worldWindow.engine.layers.addLayer(AtmosphereLayer().apply { time = nowInstant() })

    // Add renderable layer for path and markers
    val qiblaLayer = RenderableLayer("Qibla Layer")
    worldWindow.engine.layers.addLayer(qiblaLayer)

    // Create Position objects with elevation for better visibility
    val userPos = Position.fromDegrees(userLat, userLon, 200000.0)  // 200km elevation
    val kaabaPos = Position.fromDegrees(makkahLat, makkahLon, 200000.0)  // 200km elevation

    // Calculate Qibla direction from user to Kaaba
    val qiblaAzimuth = userPos.greatCircleAzimuth(kaabaPos)

    // Create Google Maps style user marker with heading shadow
    val userMarkerBitmap = createUserMarkerWithHeadingShadow(0f)  // Initial heading 0
    val kaabaBitmap = emojiToBitmap("🕋", sizeDp = 48)

    // 1. Add User Location Placemark with heading shadow marker
    val userPlacemark = Placemark.createWithImage(
        userPos, ImageSource.fromBitmap(userMarkerBitmap),
    ).apply {
        attributes.imageScale = 1.0
        altitudeMode = AltitudeMode.ABSOLUTE
    }
    qiblaLayer.addRenderable(userPlacemark)

    // 2. User location marker with heading shadow is handled by the placemark itself
    // No separate heading indicator - we'll rotate the user marker bitmap instead
    val headingCone: Polygon? = null  // Not used - heading shown via rotated marker

    // 3. Add Kaaba Placemark with emoji icon
    val kaabaPlacemark = Placemark.createWithImage(
        kaabaPos, ImageSource.fromBitmap(kaabaBitmap),
    ).apply {
        attributes.imageScale = 0.4  // Original size
        altitudeMode = AltitudeMode.ABSOLUTE
    }
    qiblaLayer.addRenderable(kaabaPlacemark)

    // Setup camera view to show BOTH user location AND Kaaba - ZOOMED IN
    val globe = worldWindow.engine.globe

    // Calculate great circle heading and distance
    val distanceRadians = userPos.greatCircleDistance(kaabaPos)
    val distanceMeters = distanceRadians * globe.equatorialRadius
    val earthRadius = globe.equatorialRadius

    // Calculate great-circle midpoint between user and Kaaba for camera centering
    val mid = greatCircleMidpoint(userLat, userLon, makkahLat, makkahLon)
    val midLat = mid[0]
    val midLon = mid[1]

    // Heading = bearing from the camera center (midpoint) toward the Kaaba, so the view's
    // "up" points at the Kaaba: it ends up at the top of the globe and the user at the bottom.
    val heading = Position.fromDegrees(midLat, midLon, 0.0).greatCircleAzimuth(kaabaPos)

    android.util.Log.d("QiblaGlobeView", "📷 Camera setup: distance=${(distanceMeters/1000).toInt()}km, midpoint=($midLat, $midLon)")

    // Pulled back so the whole Earth reads as a sphere inside the wide carousel card.
    // 1.8x still clipped the lower limb in this short viewport; 2.05x preserves a small
    // black margin around the complete disc and its atmosphere glow.
    val baseRange = distanceMeters * 1.8
    val minRange = earthRadius * 2.05
    val maxRange = earthRadius * 3.2
    val finalRange = baseRange.coerceIn(minRange, maxRange)

    // Small tilt for a touch of 3D depth (still mostly head-on, glow all around).
    val tilt = 12.0

    val lookAt = LookAt().apply {
        set(
            midLat.degrees, midLon.degrees, 0.0,  // Center on midpoint between user and Kaaba
            AltitudeMode.ABSOLUTE,
            finalRange,           // Closer range - globe fills tile
            heading,              // Orient view along Qibla direction (Angle)
            tilt.degrees,         // 3D perspective
            0.0.degrees,          // No roll
        )
    }

    android.util.Log.d("QiblaGlobeView", "🎯 Camera: midpoint=($midLat, $midLon), range=${(finalRange/1000).toInt()}km, heading=$heading")

    worldWindow.engine.cameraFromLookAt(lookAt)

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
    val globe = worldWindow.engine.globe
    val userPos = Position.fromDegrees(userLat, userLon, 0.0)
    val kaabaPos = Position.fromDegrees(makkahLat, makkahLon, 0.0)

    // Calculate distance
    val distanceRadians = userPos.greatCircleDistance(kaabaPos)
    val distanceMeters = distanceRadians * globe.equatorialRadius
    val earthRadius = globe.equatorialRadius

    // Great-circle midpoint between user and Kaaba
    val mid = greatCircleMidpoint(userLat, userLon, makkahLat, makkahLon)
    val midLat = mid[0]
    val midLon = mid[1]

    // Heading toward the Kaaba from the midpoint → Kaaba at top, user at bottom.
    val heading = Position.fromDegrees(midLat, midLon, 0.0).greatCircleAzimuth(kaabaPos)

    // Calculate range to show the full sphere (matches createWorldWindow framing)
    val baseRange = distanceMeters * 1.8
    val minRange = earthRadius * 2.05
    val maxRange = earthRadius * 3.2
    val finalRange = baseRange.coerceIn(minRange, maxRange)

    val lookAt = LookAt().apply {
        set(
            midLat.degrees, midLon.degrees, 0.0,
            AltitudeMode.ABSOLUTE,
            finalRange,
            heading,
            12.0.degrees,  // tilt (small, for 3D depth)
            0.0.degrees,   // roll
        )
    }

    worldWindow.engine.cameraFromLookAt(lookAt)
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
    // Get current camera as a LookAt
    val lookAt = worldWindow.engine.cameraAsLookAt(LookAt())

    // Keep the camera centered on midpoint between user and Kaaba
    // Only update the heading to rotate the view smoothly
    // This keeps both markers in view while showing different angles

    // Subtle rotation based on device heading (reduced by 50% for stability)
    val adjustedHeading = (deviceHeading * 0.5) % 360.0
    lookAt.heading = adjustedHeading.degrees

    // Apply updated camera
    worldWindow.engine.cameraFromLookAt(lookAt)
    worldWindow.requestRedraw()
}

// Current instant, used as the AtmosphereLayer time so the day/night terminator
// matches the real time of day.
private fun nowInstant(): kotlin.time.Instant =
    kotlin.time.Instant.fromEpochMilliseconds(System.currentTimeMillis())

// Great-circle midpoint (degrees) between two lat/lon points. Correct for any pair —
// including far-apart points and across the antimeridian — unlike a lat/lon average,
// so the camera centers on the true mid-arc and both markers stay framed worldwide.
private fun greatCircleMidpoint(lat1: Double, lon1: Double, lat2: Double, lon2: Double): DoubleArray {
    val p1 = Math.toRadians(lat1); val l1 = Math.toRadians(lon1)
    val p2 = Math.toRadians(lat2); val dl = Math.toRadians(lon2 - lon1)
    val bx = Math.cos(p2) * Math.cos(dl)
    val by = Math.cos(p2) * Math.sin(dl)
    val midLat = Math.atan2(
        Math.sin(p1) + Math.sin(p2),
        Math.sqrt((Math.cos(p1) + bx) * (Math.cos(p1) + bx) + by * by),
    )
    val midLon = l1 + Math.atan2(by, Math.cos(p1) + bx)
    return doubleArrayOf(Math.toDegrees(midLat), Math.toDegrees(midLon))
}

// Colored dot radius at the pulse start, as a fraction of its full (end) radius.
// Google measures ~0.75; bumped so the dot stays a bit larger at the start (gentler
// pulse, thinner white band at the start) per preference.
private const val DOT_PULSE_MIN = 0.85f

// White ring outer radius as a multiple of the dot's full radius. The ring stays a
// constant size while the colored dot grows into it, so the white band is thickest
// at the pulse start and thinnest at the end — matching Google's location dot.
private const val WHITE_RING_RATIO = 1.25f

/**
 * Create a Google Maps style user location marker with all elements:
 * 1. Small light blue accuracy circle
 * 2. Blue heading cone with radar/torch gradient effect
 * 3. White ring around center
 * 4. Solid blue dot (center)
 */
private fun createUserMarkerWithHeadingShadow(
    heading: Float,
    coneColor: Int = 0xFF10B981.toInt(),
    dotScale: Float = 1f,
): Bitmap {
    val size = 400
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val centerX = size / 2f
    val centerY = size / 2f

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
    }

    // Beam color from parameter - changes based on sensor accuracy
    val ccR = android.graphics.Color.red(coneColor)
    val ccG = android.graphics.Color.green(coneColor)
    val ccB = android.graphics.Color.blue(coneColor)

    // ============ RADAR CONE (matches SimpleGlobeView RingOverlayView) ============
    // Single bright 65° sweep — same style as Smart Prediction's globe-mode cone
    // emanating from the user dot. Radial gradient fades from full opacity at the
    // dot to transparent at the cone's outer edge.
    val coneRadius = size * 0.45f
    val coneSweep = 65f
    val coneStart = heading - 90f - coneSweep / 2f

    val coneOval = android.graphics.RectF(
        centerX - coneRadius, centerY - coneRadius,
        centerX + coneRadius, centerY + coneRadius
    )

    val coneShader = android.graphics.RadialGradient(
        centerX, centerY, coneRadius,
        intArrayOf(
            android.graphics.Color.argb(0xFF, ccR, ccG, ccB),  // 100% at dot
            android.graphics.Color.argb(0xCC, ccR, ccG, ccB),  // ~80% near dot
            android.graphics.Color.argb(0x66, ccR, ccG, ccB),  // ~40% mid
            android.graphics.Color.argb(0x00, ccR, ccG, ccB)   // transparent at edge
        ),
        floatArrayOf(0f, 0.2f, 0.55f, 1f),
        android.graphics.Shader.TileMode.CLAMP
    )

    paint.style = android.graphics.Paint.Style.FILL
    paint.shader = coneShader

    val conePath = android.graphics.Path()
    conePath.moveTo(centerX, centerY)
    conePath.arcTo(coneOval, coneStart, coneSweep)
    conePath.close()
    canvas.drawPath(conePath, paint)
    paint.shader = null

    // User dot: constant white ring + accuracy-colored fill that pulses inside it,
    // so the white band breathes from thick (start) to thin (end) like Google.
    val dotRadius = size * 0.042f
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(centerX, centerY, dotRadius * WHITE_RING_RATIO, paint)
    paint.color = coneColor
    canvas.drawCircle(centerX, centerY, dotRadius * dotScale, paint)

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
