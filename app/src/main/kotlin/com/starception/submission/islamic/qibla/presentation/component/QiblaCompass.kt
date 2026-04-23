package com.starception.submission.islamic.qibla.presentation.component

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.GeomagneticField
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.starception.submission.feature.prayertimes.utils.calculateQiblaDirection
import com.starception.submission.prayer.service.EnhancedLocationService
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Islamic Qibla Compass Component
 * 
 * A modern Islamic compass that shows the direction to Mecca (Qibla) for prayer.
 * Provides accurate Qibla direction indicator using device sensors.
 * 
 * ## Key Features:
 * - **Qibla Direction**: 10% circular arc pointing toward Mecca
 * - **Islamic Theming**: Green color scheme with 🕋 Kaaba emoji
 * - **Real-time Updates**: Responds to device orientation via magnetometer
 * - **Material 3 Design**: Clean, modern Islamic UI design
 * 
 * @param progress Prayer time progress (for future use)
 * @param modifier Modifier for styling
 * @param size Compass size (default: 88.dp, recommended: 120.dp+)
 * @param locationService Enhanced location service for GPS and Qibla calculations
 */
@Composable
fun QiblaCompass(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    locationService: EnhancedLocationService? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
    // Compass state
    var compassDegree by remember { mutableFloatStateOf(0f) }
    var qiblaDirection by remember { mutableFloatStateOf(0f) }
    var userLocation by remember { mutableStateOf<android.location.Location?>(null) }
    var sensorAccuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    var pendingAccuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    var pendingAccuracySince by remember { mutableLongStateOf(0L) }
    val ACCURACY_DEBOUNCE_MS = 2000L // Only change displayed accuracy after 2s of stability
    var isInitializing by remember { mutableStateOf(true) }
    var magneticFieldStrength by remember { mutableFloatStateOf(0f) } // For accuracy detection
    var magneticDeclination by remember { mutableFloatStateOf(0f) } // Magnetic declination correction
    
    // Throttling for compass updates to prevent flickering
    var lastCompassUpdateTime by remember { mutableLongStateOf(0L) }
    val COMPASS_UPDATE_INTERVAL_MS = 50L // Update at most every 50ms (20 updates per second)
    
    // Sensor management - Use rotation matrix method (same as QiblaGlobeView) for consistency
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accelerometerSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val magneticSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }

    // Sensor data arrays for rotation matrix calculation
    val gravity = remember { FloatArray(3) }
    val geomagnetic = remember { FloatArray(3) }

    // Combined sensor listener - Uses rotation matrix for accurate heading (same as QiblaGlobeView)
    val sensorListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return

                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)

                        // Calculate magnetic field strength for accuracy detection
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]
                        val strength = sqrt(x * x + y * y + z * z)
                        magneticFieldStrength = strength

                        // Debounce: only change displayed accuracy after 2s of stability
                        if (!isInitializing) {
                            val newAccuracy = when {
                                strength < 15f -> SensorManager.SENSOR_STATUS_ACCURACY_LOW
                                strength > 100f -> SensorManager.SENSOR_STATUS_ACCURACY_LOW
                                strength < 25f -> SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                                else -> SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                            }
                            val now = System.currentTimeMillis()
                            if (newAccuracy != pendingAccuracy) {
                                pendingAccuracy = newAccuracy
                                pendingAccuracySince = now
                            } else if (newAccuracy != sensorAccuracy && (now - pendingAccuracySince) >= ACCURACY_DEBOUNCE_MS) {
                                sensorAccuracy = newAccuracy
                            }
                        }
                    }
                }

                // Throttle compass updates to prevent flickering
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastCompassUpdateTime < COMPASS_UPDATE_INTERVAL_MS) {
                    return
                }

                // Calculate orientation using rotation matrix (same method as QiblaGlobeView)
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

                    // Store magnetic north (before declination correction)
                    // Declination is applied later in trueNorth calculation
                    compassDegree = azimuthDegrees
                    lastCompassUpdateTime = currentTime

                    if (isInitializing) {
                        isInitializing = false
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Debounced via onSensorChanged — no direct update here
            }
        }
    }
    
    // Initialization delay to prevent red flicker
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800) // 800ms delay
        if (isInitializing) {
            isInitializing = false
        }
    }
    
    // Get user location for Qibla calculation
    LaunchedEffect(locationService) {
        locationService?.let { service ->
            coroutineScope.launch {
                service.getBestAvailableLocation().fold(
                    onSuccess = { location ->
                        userLocation = location
                        qiblaDirection = calculateQiblaDirection(
                            lat1 = location.latitude,
                            lon1 = location.longitude
                        ).toFloat()

                        // Calculate magnetic declination for this location
                        // This corrects magnetic north to true north
                        val geoField = GeomagneticField(
                            location.latitude.toFloat(),
                            location.longitude.toFloat(),
                            location.altitude.toFloat(),
                            System.currentTimeMillis()
                        )
                        magneticDeclination = geoField.declination
                    },
                    onFailure = {
                        qiblaDirection = 0f
                        magneticDeclination = 0f
                    }
                )
            }
        }
    }
    
    // Compass rose animation - background counter-rotates with the device so the rose
    // always shows cardinal directions in their real geographic positions (heads-up mode).
    // The arc is fixed at the top representing the torch / camera direction.
    val trueNorth = compassDegree + magneticDeclination
    val targetDegree = -trueNorth   // background rotates opposite to device heading
    val currentDegreeState = remember { mutableFloatStateOf(targetDegree) }
    
    // Calculate shortest rotation path
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
    
    // Lifecycle management - Register accelerometer and magnetic field sensors (same as QiblaGlobeView)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Register accelerometer sensor
                    accelerometerSensor?.let {
                        sensorManager.registerListener(
                            sensorListener,
                            it,
                            SensorManager.SENSOR_DELAY_UI
                        )
                    }
                    // Register magnetic field sensor
                    magneticSensor?.let {
                        sensorManager.registerListener(
                            sensorListener,
                            it,
                            SensorManager.SENSOR_DELAY_UI
                        )
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    sensorManager.unregisterListener(sensorListener)
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager.unregisterListener(sensorListener)
        }
    }
    
    // Accuracy color and calibration status
    val accuracyColor = if (isInitializing) Color(0xFF10B981) else getAccuracyColor(sensorAccuracy)
    val needsCalibration = !isInitializing && sensorAccuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW

    // isNearQibla: device torch is pointing toward Qibla when trueNorth ≈ qiblaDirection
    val rawDeviation = (trueNorth - qiblaDirection + 360f) % 360f
    val qiblaDeviation = if (rawDeviation > 180f) rawDeviation - 360f else rawDeviation
    val angularDistance = kotlin.math.abs(qiblaDeviation)
    val isNearQibla = angularDistance <= 5f

    android.util.Log.d("QiblaCompassAlignment", "trueNorth=$trueNorth, qiblaDirection=$qiblaDirection, deviation=$qiblaDeviation, isNearQibla=$isNearQibla, declination=$magneticDeclination")

    // Enhanced UI with better novice user guidance
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // ── Rotating compass rose ────────────────────────────────────────────────
        // The entire rose (background + N/E/S/W labels + Qibla dot) counter-rotates
        // with the device so cardinal directions stay geographically correct on screen.
        // animatedCompassDegree == animated(-trueNorth).
        Box(
            modifier = Modifier
                .size(size)
                .rotate(animatedCompassDegree),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier.size(size)
            ) {
                val center = Offset(this.size.width / 2, this.size.height / 2)
                val backgroundRadius = (this.size.minDimension / 2) - 2.dp.toPx()

                // Gradient background for better depth
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, Color(0xFFF8F9FA)),
                        radius = backgroundRadius
                    ),
                    radius = backgroundRadius,
                    center = center
                )

                // Accuracy indication border
                drawCircle(
                    color = accuracyColor.copy(alpha = 0.8f),
                    radius = backgroundRadius,
                    center = center,
                    style = Stroke(width = 4.dp.toPx())
                )

                // Inner definition border
                drawCircle(
                    color = Color.Black.copy(alpha = 0.15f),
                    radius = backgroundRadius - 3.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Cardinal direction tick marks
                listOf(0f, 90f, 180f, 270f).forEach { angle ->
                    val angleRad = Math.toRadians(angle.toDouble())
                    drawLine(
                        color = Color.Black.copy(alpha = 0.4f),
                        start = Offset(
                            x = center.x + cos(angleRad).toFloat() * (backgroundRadius - 10.dp.toPx()),
                            y = center.y + sin(angleRad).toFloat() * (backgroundRadius - 10.dp.toPx())
                        ),
                        end = Offset(
                            x = center.x + cos(angleRad).toFloat() * backgroundRadius,
                            y = center.y + sin(angleRad).toFloat() * backgroundRadius
                        ),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Qibla direction dot — fixed on the rose at the geographic Qibla bearing
                // (sin/cos conversion: compass 0°=up, 90°=right → x=sin, y=-cos)
                val qiblaRad = Math.toRadians(qiblaDirection.toDouble())
                val dotRadius = backgroundRadius * 0.82f
                val dotCenter = Offset(
                    x = center.x + sin(qiblaRad).toFloat() * dotRadius,
                    y = center.y - cos(qiblaRad).toFloat() * dotRadius
                )
                // Outer halo
                drawCircle(
                    color = if (isNearQibla) Color(0xFF00C853).copy(alpha = 0.35f) else Color(0xFF10B981).copy(alpha = 0.25f),
                    radius = 9.dp.toPx(),
                    center = dotCenter
                )
                // Solid dot
                drawCircle(
                    color = if (isNearQibla) Color(0xFF00C853) else Color(0xFF10B981),
                    radius = 5.dp.toPx(),
                    center = dotCenter
                )
            }

            // N/E/S/W labels — rotate with the rose so they always face their compass direction
            if (size >= 120.dp) {
                listOf(
                    "N" to Offset(0f, -1f),
                    "E" to Offset(1f, 0f),
                    "S" to Offset(0f, 1f),
                    "W" to Offset(-1f, 0f)
                ).forEach { (label, offset) ->
                    Box(
                        modifier = Modifier
                            .offset(
                                x = (offset.x * (size.value / 2 + 20)).dp,
                                y = (offset.y * (size.value / 2 + 20)).dp
                            )
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
        // ── End rotating compass rose ─────────────────────────────────────────────

        // ── Fixed arc centered at top (torch / camera direction) ─────────────────
        // CircularProgressIndicator starts at 12 o'clock and sweeps clockwise.
        // Rotating by -(sweep/2) = -(0.15*360/2) = -27° centers the arc at 12 o'clock.
        Box(
            modifier = Modifier
                .size(size - 16.dp)
                .rotate(-27f),
            contentAlignment = Alignment.Center
        ) {
            // Qibla direction arc - color reflects sensor accuracy strength
            CircularProgressIndicator(
                progress = { 0.15f }, // 15% progress pointing to Qibla (more visible)
                modifier = Modifier.fillMaxSize(),
                color = if (isNearQibla && !needsCalibration) {
                    // When aligned with Qibla, ALWAYS show bright green
                    Color(0xFF00C853) // Bright green - aligned!
                } else if (needsCalibration) {
                    Color(0xFFFF4444) // Red when needs calibration
                } else {
                    // Use accuracy-based color to show sensor strength
                    accuracyColor
                },
                strokeWidth = if (isNearQibla && !needsCalibration) 12.dp else 10.dp,
                trackColor = Color.Black.copy(alpha = 0.05f),
                strokeCap = StrokeCap.Round,
            )
            
            // Qibla arrow indicator at top of compass - matches arc color
            Box(
                modifier = Modifier
                    .offset(y = -(size.value / 2 - 25).dp)
                    .size(if (isNearQibla && !needsCalibration) 28.dp else 24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isNearQibla && !needsCalibration) {
                            Color(0xFF00C853) // Bright green when aligned
                        } else if (needsCalibration) {
                            Color(0xFFFF4444) // Red when needs calibration
                        } else {
                            // Match the arc color for consistency
                            accuracyColor
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Navigation,
                    contentDescription = "Qibla Direction",
                    modifier = Modifier
                        .size(if (isNearQibla && !needsCalibration) 18.dp else 16.dp)
                        .rotate(-90f), // Point upward
                    tint = Color.White
                )
            }
        }
        
        // Enhanced center content with better instructions
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Kaaba icon with enhanced styling
                Box(
                    modifier = Modifier
                        .size(if (size >= 140.dp) 32.dp else 24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (needsCalibration) Color(0xFFFF4444).copy(alpha = 0.1f) 
                            else Color(0xFF10B981).copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🕋",
                        fontSize = if (size >= 140.dp) 20.sp else 16.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Status text - dynamic based on alignment
                Text(
                    text = if (needsCalibration) {
                        "Qibla"
                    } else if (isNearQibla) {
                        "Aligned ✓"
                    } else {
                        "Qibla"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (needsCalibration) {
                        Color(0xFFFF4444)
                    } else if (isNearQibla) {
                        Color(0xFF00C853) // Bright green when aligned
                    } else {
                        Color(0xFF10B981)
                    },
                    textAlign = TextAlign.Center,
                    fontWeight = if (isNearQibla && !needsCalibration) FontWeight.ExtraBold else FontWeight.Bold,
                    fontSize = if (size >= 140.dp) 14.sp else 11.sp
                )

                // Calibration status or guidance
                if (needsCalibration) {
                    Text(
                        text = "Calibrate",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF4444),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        fontSize = if (size >= 140.dp) 11.sp else 9.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else if (isNearQibla && size >= 140.dp) {
                    Text(
                        text = "Facing Qibla Direction",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF00C853),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (size >= 140.dp) {
                    Text(
                        text = "Turn phone until\narrow points up ↑\n${angularDistance.toInt()}° off",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        
        // Calibration guidance overlay for larger compasses
        if (needsCalibration && size >= 160.dp) {
            Box(
                modifier = Modifier
                    .offset(y = (size.value / 2 + 35).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFF4444).copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Move phone in figure-8 pattern",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Get sensor accuracy color
 */
private fun getAccuracyColor(accuracy: Int): Color = when(accuracy) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> Color(0xFF10B981)
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> Color(0xFFFFA500)
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> Color(0xFFFF6B6B)
    else -> Color(0xFFFF4444) // UNRELIABLE - Red
}