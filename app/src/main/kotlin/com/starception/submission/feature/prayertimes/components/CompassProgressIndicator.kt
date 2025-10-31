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
    locationService: EnhancedLocationService? = null
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
    
    // SENSOR MANAGEMENT - Use TYPE_ORIENTATION for compass, TYPE_MAGNETIC_FIELD for accuracy
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val orientationSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION) }
    val magneticSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    
    // ORIENTATION SENSOR LISTENER - For compass direction
    val orientationListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
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
                
                // Determine accuracy based on magnetic field strength
                // Typical Earth magnetic field: ~30-60 microteslas (0.03-0.06)
                // Low strength (<20) or very high (>100) indicates interference
                if (!isInitializing) {
                    sensorAccuracy = when {
                        strength < 20f -> SensorManager.SENSOR_STATUS_ACCURACY_LOW // Too weak
                        strength > 100f -> SensorManager.SENSOR_STATUS_ACCURACY_LOW // Too strong (interference)
                        strength < 30f -> SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM // Weak field
                        else -> SensorManager.SENSOR_STATUS_ACCURACY_HIGH // Normal range
                    }
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Magnetic field sensor reports accuracy more reliably
                if (!isInitializing) {
                    // Combine reported accuracy with strength-based accuracy
                    val strengthBasedAccuracy = when {
                        magneticFieldStrength < 20f -> SensorManager.SENSOR_STATUS_ACCURACY_LOW
                        magneticFieldStrength > 100f -> SensorManager.SENSOR_STATUS_ACCURACY_LOW
                        magneticFieldStrength < 30f -> SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                        else -> SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                    }
                    
                    // Use the worse of the two (more conservative)
                    sensorAccuracy = minOf(accuracy, strengthBasedAccuracy)
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
            durationMillis = 210, // Exact same duration as original
            easing = LinearEasing
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
            val backgroundRadius = (this.size.minDimension / 2) - 2.dp.toPx()
            
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
        
        // Calculate if user is facing Qibla (within ±15 degrees)
        val qiblaAngle = animatedCompassDegree
        val normalizedAngle = ((qiblaAngle % 360f) + 360f) % 360f
        
        // FIXED: Calculate if within ±15 degrees of 0° (Qibla direction)
        // This means: 0° to 15° OR 345° to 360°
        val isNearQibla = normalizedAngle <= 15f || normalizedAngle >= 345f
        
        // Determine rotation direction needed
        val needsClockwise = when {
            normalizedAngle > 180f -> true  // Turn clockwise to get to 0°
            normalizedAngle <= 180f && normalizedAngle > 15f -> false // Turn counter-clockwise
            else -> false // Already near Qibla
        }
        
        // Original elegant Qibla direction indicator with enhanced feedback
        CircularProgressIndicator(
            progress = { 0.1f }, // Original 10% progress
            modifier = Modifier
                .size(size - 16.dp)
                .rotate(animatedCompassDegree),
            color = if (needsCalibration) {
                Color(0xFFFF4444)
            } else if (isNearQibla) {
                Color(0xFF00C853) // Brighter green when aligned
            } else {
                Color(0xFF10B981) // Original green
            },
            strokeWidth = if (isNearQibla) 10.dp else 8.dp, // Slightly thicker when aligned
            trackColor = Color.Black.copy(alpha = 0.1f),
            strokeCap = StrokeCap.Round,
        )
        
        // Animated rotation guidance - only when not near Qibla and not calibrating
        if (size >= 120.dp && !isNearQibla && !needsCalibration) {
            // Animated rotation indicator
            val infiniteTransition = rememberInfiniteTransition(label = "rotation_guide")
            val rotationIndicatorAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "rotation_alpha"
            )
            
            val rotationIndicatorScale by infiniteTransition.animateFloat(
                initialValue = 0.9f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "rotation_scale"
            )
            
            // Draw animated arc showing rotation direction
            Canvas(
                modifier = Modifier
                    .size(size - 8.dp)
                    .graphicsLayer {
                        alpha = rotationIndicatorAlpha
                        scaleX = rotationIndicatorScale
                        scaleY = rotationIndicatorScale
                    }
            ) {
                val center = Offset(this.size.width / 2, this.size.height / 2)
                val radius = (this.size.minDimension / 2) - 4.dp.toPx()
                
                if (needsClockwise) {
                    // Clockwise rotation arc
                    drawArc(
                        color = Color(0xFF2196F3).copy(alpha = 0.6f),
                        startAngle = -45f,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        ),
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2)
                    )
                } else {
                    // Counter-clockwise rotation arc
                    drawArc(
                        color = Color(0xFF2196F3).copy(alpha = 0.6f),
                        startAngle = 45f,
                        sweepAngle = -90f,
                        useCenter = false,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        ),
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2)
                    )
                }
            }
        }
        
        
        // Enhanced center content with integrated facing status
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Qibla direction with better status for different sizes
                val displayText = if (needsCalibration) {
                    "🕋 Qibla"
                } else if (isNearQibla) {
                    if (size >= 140.dp) "🕋 Facing Qibla" else "🕋 Aligned" // Shorter text for big tiles
                } else {
                    "🕋 Qibla"
                }
                
                
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (needsCalibration) {
                        Color(0xFFFF4444)
                    } else if (isNearQibla) {
                        Color(0xFF00C853) // Bright green when facing Qibla
                    } else {
                        Color(0xFF10B981) // Original green
                    },
                    textAlign = TextAlign.Center,
                    fontWeight = if (isNearQibla && !needsCalibration) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = if (size >= 280.dp) 16.sp else 12.sp,
                )
                
                // Guidance text for popup - dynamic based on status
                if (size >= 260.dp) {
                    Text(
                        text = if (isNearQibla && !needsCalibration) {
                            "✓ Aligned correctly"
                        } else {
                            "Turn until green arc\npoints up ↑"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isNearQibla && !needsCalibration) {
                            Color(0xFF00C853)
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

