package com.starception.submission.feature.prayertimes.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.starception.submission.feature.prayertimes.utils.calculateQiblaDirection
import com.starception.submission.prayer.service.EnhancedLocationService
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Enhanced Qibla Compass Component
 * 
 * A modern Islamic compass that combines prayer time countdown with Qibla direction indicator.
 * Uses Material 3 design principles with Islamic theming for religious applications.
 * 
 * ## Key Features:
 * - **Circular Progress Arc**: 10% arc that rotates to point toward Mecca (Qibla direction)
 * - **Prayer Time Display**: Shows remaining time until next prayer inside the compass
 * - **Islamic Theming**: Green color scheme with 🕋 Kaaba emoji for religious context
 * - **Real-time Updates**: Responds to device orientation changes via magnetometer sensor
 * - **Material 3 Design**: Clean, modern UI that integrates seamlessly with prayer apps
 * 
 * ## Design Philosophy:
 * - **Clean & Minimal**: No sensor status indicators or visual clutter
 * - **Islamic Colors**: Traditional green (#10B981) representing Islamic culture
 * - **Perfect Circle**: Clean white background with subtle border
 * - **Responsive**: Smooth animations using spring physics for natural feel
 * 
 * ## Technical Implementation:
 * - Uses `CircularProgressIndicator` for the Qibla direction arc
 * - Integrates with device magnetometer for compass functionality
 * - Location-based Qibla calculation using GPS coordinates
 * - Lifecycle-aware sensor management to preserve battery
 * 
 * @param progress Float value representing prayer time progress (not used for arc display)
 * @param timeText String showing time remaining until next prayer (e.g., "2h 57m")
 * @param modifier Modifier for styling and layout customization
 * @param size Dp size of the compass component (default: 88.dp, recommended: 120.dp+)
 * @param locationService Enhanced location service for GPS and Qibla calculations
 */
@Composable
fun CompassProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 88.dp,
    locationService: EnhancedLocationService? = null,
    userLatitude: Double = 0.0,
    userLongitude: Double = 0.0,
    showGlobe: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
    // COMPASS STATE - Modified for Qibla direction
    var compassDegree by remember { mutableFloatStateOf(0f) }
    var currentDegree by remember { mutableFloatStateOf(0f) }
    var qiblaDirection by remember { mutableFloatStateOf(0f) } // Direction to Qibla from North
    var userLocation by remember { mutableStateOf<android.location.Location?>(null) }
    var sensorAccuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    var isInitializing by remember { mutableStateOf(true) }
    var magneticFieldStrength by remember { mutableFloatStateOf(0f) } // For accuracy detection
    var isInForeground by remember { mutableStateOf(true) } // Track if app is in foreground for haptic control

    // Throttling for compass updates to prevent flickering
    var lastCompassUpdateTime by remember { mutableLongStateOf(0L) }
    val COMPASS_UPDATE_INTERVAL_MS = 50L // Update at most every 50ms (20 updates per second)
    
    // SENSOR MANAGEMENT - Use TYPE_ORIENTATION for compass, TYPE_MAGNETIC_FIELD for accuracy
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val orientationSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION) }
    val magneticSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    
    // ORIENTATION SENSOR LISTENER - For compass direction
    val orientationListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val currentTime = System.currentTimeMillis()
                // Throttle updates to prevent flickering
                if (currentTime - lastCompassUpdateTime < COMPASS_UPDATE_INTERVAL_MS) {
                    return
                }
                lastCompassUpdateTime = currentTime
                
                // Get device's magnetic north direction
                val magneticNorth = Math.round(event!!.values[0]).toFloat()
                compassDegree = magneticNorth
                // Mark as no longer initializing once we get sensor data
                if (isInitializing) {
                    isInitializing = false
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // TYPE_ORIENTATION may not report accuracy reliably, but update if it does
                if (!isInitializing && accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE) {
                    sensorAccuracy = accuracy
                }
            }
        }
    }
    
    // MAGNETIC FIELD SENSOR LISTENER - For accurate sensor strength detection
    val magneticFieldListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                
                // Calculate magnetic field strength: sqrt(x^2 + y^2 + z^2)
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val strength = sqrt(x * x + y * y + z * z)
                magneticFieldStrength = strength

                // Log magnetic field strength for debugging
                android.util.Log.d("CompassMagField", "Magnetic field strength: $strength µT (x=$x, y=$y, z=$z)")

                // Determine accuracy based on magnetic field strength
                // Typical Earth magnetic field: ~25-65 microteslas
                // Adjusted thresholds based on real-world measurements
                if (!isInitializing) {
                    sensorAccuracy = when {
                        strength < 15f -> SensorManager.SENSOR_STATUS_ACCURACY_LOW // Too weak
                        strength > 100f -> SensorManager.SENSOR_STATUS_ACCURACY_LOW // Too strong (interference)
                        strength < 25f -> SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM // Weak field
                        else -> SensorManager.SENSOR_STATUS_ACCURACY_HIGH // Normal range (25-100)
                    }
                    android.util.Log.d("CompassMagField", "Sensor accuracy: ${when(sensorAccuracy) {
                        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "HIGH"
                        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "MEDIUM"
                        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "LOW"
                        else -> "UNRELIABLE"
                    }}")
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Magnetic field sensor reports accuracy more reliably
                if (!isInitializing) {
                    // Combine reported accuracy with strength-based accuracy
                    val strengthBasedAccuracy = when {
                        magneticFieldStrength < 15f -> SensorManager.SENSOR_STATUS_ACCURACY_LOW
                        magneticFieldStrength > 100f -> SensorManager.SENSOR_STATUS_ACCURACY_LOW
                        magneticFieldStrength < 25f -> SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                        else -> SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                    }

                    // Prioritize strength-based accuracy over system-reported accuracy
                    // System often reports MEDIUM even when field strength is perfectly normal
                    sensorAccuracy = if (magneticFieldStrength > 0f) {
                        strengthBasedAccuracy // Trust our calculation
                    } else {
                        minOf(accuracy, strengthBasedAccuracy) // Fallback to conservative approach
                    }
                    android.util.Log.d("CompassMagField", "onAccuracyChanged: reported=$accuracy, strengthBased=$strengthBasedAccuracy, final=$sensorAccuracy, fieldStrength=$magneticFieldStrength")
                }
            }
        }
    }
    
    // INITIALIZATION DELAY - Prevent red flicker on refresh
    LaunchedEffect(Unit) {
        // Allow sensor to initialize properly before showing accuracy colors
        kotlinx.coroutines.delay(800) // 800ms delay
        if (isInitializing) {
            isInitializing = false
        }
    }
    
    // GET USER LOCATION FOR QIBLA CALCULATION
    LaunchedEffect(locationService) {
        locationService?.let { service ->
            coroutineScope.launch {
                service.getBestAvailableLocation().fold(
                    onSuccess = { location ->
                        userLocation = location
                        // Calculate Qibla direction from user's location
                        qiblaDirection = calculateQiblaDirection(
                            lat1 = location.latitude,
                            lon1 = location.longitude
                        ).toFloat()
                    },
                    onFailure = {
                        // Use default Qibla direction (approximation) if location fails
                        qiblaDirection = 0f
                    }
                )
            }
        }
    }
    
    // QIBLA COMPASS ANIMATION - Needle points to Qibla direction
    // Combine device orientation with Qibla direction to show where Qibla is
    val targetDegree = -(compassDegree - qiblaDirection) // Point needle toward Qibla

    // Debug logging for compass values
    LaunchedEffect(compassDegree, qiblaDirection, targetDegree) {
        android.util.Log.d("QiblaCompass", "compassDegree=$compassDegree, qiblaDirection=$qiblaDirection, targetDegree=$targetDegree")
    }
    val currentDegreeState = remember { mutableFloatStateOf(targetDegree) }
    
    // Calculate the shortest rotation path
    LaunchedEffect(targetDegree) {
        val currentValue = currentDegreeState.floatValue
        val diff = targetDegree - currentValue
        
        // Normalize difference to [-180, 180] range
        val normalizedDiff = when {
            diff > 180f -> diff - 360f
            diff < -180f -> diff + 360f
            else -> diff
        }
        
        currentDegreeState.floatValue = currentValue + normalizedDiff
    }
    
    val animatedCompassDegree by animateFloatAsState(
        targetValue = currentDegreeState.floatValue,
        animationSpec = tween(
            durationMillis = 300, // Slightly longer animation for smoother movement
            easing = androidx.compose.animation.core.FastOutSlowInEasing // Smoother easing
        ),
        label = "compassRotation"
    )
    
    // Update current degree for rotation animation (same as original)
    LaunchedEffect(animatedCompassDegree) {
        currentDegree = animatedCompassDegree
    }
    
    // LIFECYCLE MANAGEMENT - Register both sensors
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isInForeground = true
                    // Register orientation sensor for compass direction
                    orientationSensor?.let {
                        sensorManager.registerListener(
                            orientationListener, 
                            it, 
                            SensorManager.SENSOR_DELAY_GAME
                        )
                    }
                    // Register magnetic field sensor for accuracy detection
                    magneticSensor?.let {
                        sensorManager.registerListener(
                            magneticFieldListener, 
                            it, 
                            SensorManager.SENSOR_DELAY_GAME
                        )
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    isInForeground = false
                    // Unregister both sensors
                    sensorManager.unregisterListener(orientationListener)
                    sensorManager.unregisterListener(magneticFieldListener)
                }
                else -> {}
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager.unregisterListener(orientationListener)
            sensorManager.unregisterListener(magneticFieldListener)
        }
    }
    
    
    // Helper functions for sensor accuracy testing
    fun getAccuracyColor(accuracy: Int): Color = when(accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> Color(0xFF10B981)
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> Color(0xFFFFA500)
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> Color(0xFFFF6B6B)
        else -> Color(0xFFFF4444) // UNRELIABLE - Red
    }
    
    fun getAccuracyText(accuracy: Int): String = when(accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High Accuracy"
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium Accuracy"
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low Accuracy - Calibrate"
        else -> "Move in Figure-8 Pattern" // UNRELIABLE
    }
    
    val accuracyColor = if (isInitializing) Color(0xFF10B981) else getAccuracyColor(sensorAccuracy)
    val needsCalibration = !isInitializing && sensorAccuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
    
    // ENHANCED DESIGN - Better Qibla identification with accuracy feedback
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Clean original background with subtle directional guidance
        Canvas(
            modifier = Modifier.size(size)
        ) {
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val backgroundRadius = (this.size.minDimension / 2) - 1.dp.toPx()  // Moved outward
            
            // Perfect circle background
            drawCircle(
                color = Color.White,
                radius = backgroundRadius,
                center = center
            )
            
            // Accuracy indication border (changes color based on sensor strength)
            drawCircle(
                color = accuracyColor.copy(alpha = 0.6f),
                radius = backgroundRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
            
            // Inner border for definition
            drawCircle(
                color = Color.Black.copy(alpha = 0.1f),
                radius = backgroundRadius - 2.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            
        }
        
        // Calculate if user is facing Qibla (with hysteresis to prevent rapid toggling)
        val qiblaAngle = animatedCompassDegree
        val normalizedAngle = ((qiblaAngle % 360f) + 360f) % 360f

        // Calculate circular angular distance from 0° (handles both sides symmetrically)
        val angularDistance = minOf(
            kotlin.math.abs(normalizedAngle),
            kotlin.math.abs(normalizedAngle - 360f)
        )

        // Simple alignment check - no threshold/hysteresis
        val isNearQibla = angularDistance <= 5f // User is facing Qibla when within 5°

        // Continuous radar-style haptic feedback while aligned with Qibla
        val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
        val currentlyAligned = isNearQibla && !needsCalibration

        // Continuous pulsing haptic feedback every second while aligned (like radar confirmation)
        // Only trigger haptic when app is in foreground to avoid background vibration annoyance
        LaunchedEffect(currentlyAligned, isInForeground) {
            if (size >= 260.dp && currentlyAligned && isInForeground) {
                android.util.Log.d("QiblaAlignment", "🎯 ALIGNED! Starting radar-style haptic loop (foreground)")
                while (currentlyAligned && isInForeground) {
                    // Strong haptic pulse
                    hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    android.util.Log.d("QiblaAlignment", "📡 Haptic pulse (radar ping)")
                    // Wait 1 second before next pulse
                    kotlinx.coroutines.delay(1000)
                }
                android.util.Log.d("QiblaAlignment", "⏸️ Stopped haptic loop (not aligned or backgrounded)")
            }
        }

        // Debug logging for alignment detection
        android.util.Log.d("QiblaAlignment", "animatedCompassDegree=$animatedCompassDegree, normalizedAngle=$normalizedAngle, angularDistance=$angularDistance, isNearQibla=$isNearQibla, needsCalibration=$needsCalibration")
        
        // Stable rotation direction with time-based debounce to prevent rapid toggling
        val stableDirection = remember { mutableStateOf<Boolean?>(null) }
        val lastDirectionChangeTime = remember { mutableStateOf(0L) }
        val currentTime = System.currentTimeMillis()

        // Determine the raw direction (shortest path)
        val rawNeedsClockwise = normalizedAngle <= 180f

        // Only change direction if:
        // 1. It's been at least 800ms since last change, OR
        // 2. We don't have a stable direction yet, OR
        // 3. The angle is very clear (far from 180°)
        val needsClockwise = if (stableDirection.value == null) {
            // First time, set immediately
            stableDirection.value = rawNeedsClockwise
            lastDirectionChangeTime.value = currentTime
            rawNeedsClockwise
        } else if (stableDirection.value != rawNeedsClockwise) {
            // Direction wants to change
            val timeSinceLastChange = currentTime - lastDirectionChangeTime.value
            val isVeryClearDirection = normalizedAngle < 30f || normalizedAngle > 330f ||
                                       (normalizedAngle > 150f && normalizedAngle < 210f)

            if (timeSinceLastChange > 800 || isVeryClearDirection) {
                // Allow the change
                stableDirection.value = rawNeedsClockwise
                lastDirectionChangeTime.value = currentTime
                rawNeedsClockwise
            } else {
                // Keep old direction (debounce)
                stableDirection.value!!
            }
        } else {
            // Direction hasn't changed, keep it
            rawNeedsClockwise
        }
        
        // ANIMATION: Professional surface tension effect
        // Smooth, subtle transitions - arc merges elegantly into Kaaba

        val arcAlpha by animateFloatAsState(
            targetValue = if (isNearQibla) 0f else 1f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "arcAlpha"
        )
        val arcScale by animateFloatAsState(
            targetValue = if (isNearQibla) 0.2f else 1f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "arcScale"
        )
        // Arc progress animates smoothly
        val arcProgress by animateFloatAsState(
            targetValue = if (isNearQibla) 0.03f else 0.1f, // Arc shrinks to small dot
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
            label = "arcProgress"
        )
        val kaabaScale by animateFloatAsState(
            targetValue = if (isNearQibla) 1.35f else 1f, // Subtle scale increase
            animationSpec = spring(
                dampingRatio = 0.7f, // Less bouncy, more controlled
                stiffness = 400f // Quick but smooth
            ),
            label = "kaabaScale"
        )
        val kaabaGlow by animateFloatAsState(
            targetValue = if (isNearQibla) 1f else 0f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "kaabaGlow"
        )
        // Subtle breathing effect for Kaaba when aligned
        val infiniteTransition = rememberInfiniteTransition(label = "kaabaPulse")
        val kaabaPulse by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f, // Very subtle pulse
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing), // Slower, calmer
                repeatMode = RepeatMode.Reverse
            ),
            label = "kaabaPulseAnim"
        )
        val effectiveKaabaScale = if (isNearQibla) kaabaScale * kaabaPulse else kaabaScale

        // Original elegant Qibla direction indicator with surface tension animation
        // Arc centered between outer accuracy circle and globe container
        CircularProgressIndicator(
            progress = { arcProgress }, // Animated progress - shrinks to dot when aligned
            modifier = Modifier
                .size(size - 12.dp)  // Moved outward, closer to accuracy circle
                .rotate(animatedCompassDegree - 18f) // Offset by -18° (half of 36°) so arc CENTER aligns with Kaaba when facing Qibla
                .graphicsLayer {
                    alpha = arcAlpha
                    scaleX = arcScale
                    scaleY = arcScale
                },
            color = if (isNearQibla && sensorAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH) {
                // When aligned with Qibla AND high accuracy, show bright green
                Color(0xFF00C853) // Bright green - aligned with high accuracy!
            } else if (needsCalibration) {
                Color(0xFFFF4444) // Red when needs calibration
            } else {
                // Use accuracy-based color (orange for medium, green for high when not aligned)
                accuracyColor
            },
            strokeWidth = if (isNearQibla) 6.dp else 5.dp, // Thinner arc to give globe more space
            trackColor = Color.Black.copy(alpha = 0.1f),
            strokeCap = StrokeCap.Round,
        )

        // Kaaba icon positioned INSIDE the compass (not on arc track)
        // Hide when showing globe (globe has its own Kaaba marker)
        // Shows as target indicator - arc rotates around it
        // ANIMATION: Kaaba grows and glows when arc aligns with it
        if (!needsCalibration && !showGlobe) {
            // Position Kaaba inside the arc track (closer to center)
            val radiusOffset = (size / 2) - (if (size >= 260.dp) 48.dp else 28.dp) // More inward

            // Fixed at top (12 o'clock position) = -90° in math coordinates
            val topAngleRad = Math.toRadians(-90.0)
            val offsetX = (radiusOffset.value * kotlin.math.cos(topAngleRad)).dp
            val offsetY = (radiusOffset.value * kotlin.math.sin(topAngleRad)).dp

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Subtle glow effect behind Kaaba when aligned
                if (kaabaGlow > 0.1f) {
                    // Single elegant glow
                    Box(
                        modifier = Modifier
                            .offset(x = offsetX, y = offsetY)
                            .size((22 * effectiveKaabaScale).dp)
                            .graphicsLayer { alpha = kaabaGlow * 0.4f }
                            .background(
                                Color(0xFF10B981).copy(alpha = 0.25f), // Softer green
                                shape = CircleShape
                            )
                    )
                }

                // Kaaba icon fixed at top - absorbs the arc with surface tension effect
                Text(
                    text = "🕋",
                    fontSize = (if (size >= 260.dp) 18.sp else 14.sp),
                    modifier = Modifier
                        .offset(x = offsetX, y = offsetY)
                        .graphicsLayer {
                            scaleX = effectiveKaabaScale
                            scaleY = effectiveKaabaScale
                        }
                )
            }
        }
        
        
        // Enhanced center content with integrated facing status or globe
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (showGlobe) 0.dp else 16.dp),  // No padding for globe
            contentAlignment = Alignment.Center
        ) {
            if (showGlobe && userLatitude != 0.0 && userLongitude != 0.0) {
                // Show simple 3D globe inside the arc
                // Smaller container to create gap from arc
                val globeSize = size - 28.dp
                Box(
                    modifier = Modifier
                        .size(globeSize)
                        .clip(CircleShape)
                ) {
                    SimpleGlobeView(
                        userLatitude = userLatitude,
                        userLongitude = userLongitude,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Qibla direction with status - no emoji since Kaaba icon is shown separately
                    val displayText = if (needsCalibration) {
                        "Calibrate"
                    } else if (isNearQibla) {
                        if (size >= 140.dp) "Facing Qibla" else "Aligned"
                    } else {
                        "Qibla"
                    }


                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (needsCalibration) {
                            Color(0xFFFF4444)
                        } else if (isNearQibla && sensorAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH) {
                            Color(0xFF00C853) // Bright green when facing Qibla with high accuracy
                        } else {
                            accuracyColor // Use accuracy-based color (orange for medium, green for high)
                        },
                        textAlign = TextAlign.Center,
                        fontWeight = if (isNearQibla && !needsCalibration) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = if (size >= 280.dp) 16.sp else 12.sp,
                    )

                    // Guidance text for popup - dynamic based on status with rotation direction
                    if (size >= 260.dp) {
                        // Stable guidance text to prevent overlapping during rapid changes
                        val guidanceText = remember(isNearQibla, needsCalibration, needsClockwise, angularDistance) {
                            if (isNearQibla && !needsCalibration) {
                                "✓ Aligned with Qibla"
                            } else if (needsCalibration) {
                                "Calibrate compass\nby moving phone\nin figure-8"
                            } else {
                                // Show rotation direction for minimum path to Qibla
                                val rotationDirection = if (needsClockwise) "Clockwise" else "Counter-clockwise"
                                "Turn $rotationDirection\n${angularDistance.toInt()}° to Qibla"
                            }
                        }

                        Text(
                            text = guidanceText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isNearQibla && sensorAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH) {
                                Color(0xFF00C853) // Bright green when aligned with high accuracy
                            } else {
                                Color.Black.copy(alpha = 0.6f)
                            },
                            textAlign = TextAlign.Center,
                            fontWeight = if (isNearQibla && !needsCalibration) FontWeight.Medium else FontWeight.Medium,
                            fontSize = 13.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
        
        // Note: Calibration message removed - now handled by CompassPopupScreen
        // The popup will automatically show when sensor accuracy is poor
        
    }
}

// Note: drawProgressRing function removed as we now use Material 3 CircularProgressIndicator
// for better performance and consistency with modern Android design guidelines.

// Note: drawCompassNeedle function removed as we replaced the traditional compass needle
// with a cleaner Material 3 CircularProgressIndicator approach for better UX and performance.
// 
// ## Design Evolution:
// - Old: Traditional compass needle with arrowhead pointing to Qibla
// - New: 10% circular progress arc that rotates to indicate Qibla direction
// - Benefits: Cleaner design, no visual clutter, better Material 3 integration

/* ============================================================================
 * COMPONENT ARCHITECTURE & USAGE
 * ============================================================================
 * 
 * ## Integration Example:
 * ```kotlin
 * CompassProgressIndicator(
 *     progress = 0.7f,              // Current prayer progress (0-1)
 *     timeText = "2h 57m",           // Time remaining until next prayer
 *     size = 120.dp,                // Compass diameter
 *     locationService = locationService
 * )
 * ```
 * 
 * ## State Management:
 * - **Sensor Management**: Automatic lifecycle-aware sensor registration/unregistration
 * - **Location Updates**: Real-time Qibla calculation based on GPS coordinates
 * - **Orientation Changes**: Smooth animation response to device rotation
 * - **Battery Optimization**: Sensors only active when component is visible
 * 
 * ## Color Scheme:
 * - **Primary Green**: #10B981 (Islamic traditional color)
 * - **Background**: White with subtle black border
 * - **Text**: High contrast black with Islamic green accent
 * 
 * ## Performance Notes:
 * - Uses hardware-accelerated Canvas drawing for smooth animations
 * - Spring-based physics for natural movement feel
 * - Optimized sensor sampling to balance accuracy with battery life
 * ============================================================================ */

