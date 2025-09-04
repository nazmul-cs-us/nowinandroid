package com.starception.submission.feature.prayertimes.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.starception.submission.feature.prayertimes.utils.calculateQiblaDirection
import com.starception.submission.prayer.service.EnhancedLocationService
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * QIBLA COMPASS PROGRESS INDICATOR: Circular progress with integrated Qibla compass
 * 
 * This component combines the time remaining circular progress indicator 
 * with Qibla direction compass functionality.
 * 
 * FEATURES:
 * - Circular progress showing time remaining until next prayer
 * - Compass needle pointing to Qibla (Mecca) direction
 * - Location-based Qibla calculation using user's GPS coordinates
 * - Same smooth sensor management and rotation animation
 * - Integrated seamlessly into your existing prayer times UI
 */
@Composable
fun CompassProgressIndicator(
    progress: Float,
    timeText: String,
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
    
    // SENSOR MANAGEMENT - Exact copy from original CompassActivity
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION) }
    
    // SENSOR LISTENER - Modified to calculate Qibla-relative direction
    val sensorListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                // Get device's magnetic north direction
                val magneticNorth = Math.round(event!!.values[0]).toFloat()
                compassDegree = magneticNorth
            }
            
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Same empty implementation as original
            }
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
    
    // LIFECYCLE MANAGEMENT - Same as original app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Same sensor registration as original
                    sensorManager.registerListener(
                        sensorListener, 
                        sensor, 
                        SensorManager.SENSOR_DELAY_GAME
                    )
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // Same sensor unregistration as original
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
    
    // UI COMPONENT - Combines progress and compass
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            // COMPASS CANVAS - Custom drawing with needle
            Canvas(
                modifier = Modifier.size(size)
            ) {
                val center = Offset(this.size.width / 2, this.size.height / 2)
                val radius = (this.size.minDimension / 2) - 16.dp.toPx()
                
                // PROGRESS RING - Time remaining indicator
                drawProgressRing(
                    center = center,
                    radius = radius,
                    progress = progress
                )
                
                // COMPASS NEEDLE - Rotates with device orientation
                rotate(
                    degrees = animatedCompassDegree,
                    pivot = center
                ) {
                    drawCompassNeedle(
                        center = center,
                        radius = radius * 0.7f
                    )
                }
                
                // Qibla "Q" marker (simple dot)
                drawCircle(
                    color = Color(0xFF006400), // Dark green for Qibla
                    radius = 2.dp.toPx(),
                    center = Offset(center.x, center.y - radius * 0.85f)
                )
            }
            
            // TIME TEXT - Overlaid in center
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

/**
 * Draw the circular progress ring showing time remaining
 */
private fun DrawScope.drawProgressRing(
    center: Offset,
    radius: Float,
    progress: Float
) {
    // Background track
    drawCircle(
        color = Color.Gray.copy(alpha = 0.3f),
        radius = radius,
        center = center,
        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
    )
    
    // Progress arc
    val sweepAngle = progress * 360f
    drawArc(
        color = Color.Blue,
        startAngle = -90f, // Start from top
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
    )
}

/**
 * Draw compass needle pointing to Qibla (Mecca direction)
 */
private fun DrawScope.drawCompassNeedle(
    center: Offset,
    radius: Float
) {
    // Qibla needle (pointing to Mecca) - Green like Islamic tradition
    drawLine(
        color = Color(0xFF006400), // Dark green for Qibla direction
        start = center,
        end = Offset(center.x, center.y - radius),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round
    )
    
    // Opposite direction needle (pointing away from Qibla)
    drawLine(
        color = Color.Gray,
        start = center,
        end = Offset(center.x, center.y + radius * 0.7f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )
    
    // Center dot
    drawCircle(
        color = Color.Black,
        radius = 3.dp.toPx(),
        center = center
    )
}

