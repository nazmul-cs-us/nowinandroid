package com.starception.submission.islamic.qibla.presentation.component

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.rotate
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
 * Combines prayer time countdown with accurate Qibla direction indicator using device sensors.
 * 
 * ## Key Features:
 * - **Qibla Direction**: 10% circular arc pointing toward Mecca
 * - **Prayer Time Display**: Shows remaining time until next prayer
 * - **Islamic Theming**: Green color scheme with 🕋 Kaaba emoji
 * - **Real-time Updates**: Responds to device orientation via magnetometer
 * - **Material 3 Design**: Clean, modern Islamic UI design
 * 
 * @param progress Prayer time progress (for future use)
 * @param timeText Time remaining until next prayer (e.g., "2h 57m")
 * @param modifier Modifier for styling
 * @param size Compass size (default: 88.dp, recommended: 120.dp+)
 * @param locationService Enhanced location service for GPS and Qibla calculations
 */
@Composable
fun QiblaCompass(
    progress: Float,
    timeText: String,
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
    var isInitializing by remember { mutableStateOf(true) }
    
    // Sensor management
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION) }
    
    // Sensor listener
    val sensorListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val magneticNorth = Math.round(event!!.values[0]).toFloat()
                compassDegree = magneticNorth
                if (isInitializing) {
                    isInitializing = false
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (!isInitializing) {
                    sensorAccuracy = accuracy
                }
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
                    },
                    onFailure = {
                        qiblaDirection = 0f
                    }
                )
            }
        }
    }
    
    // Qibla compass animation - needle points to Qibla direction
    val targetDegree = -(compassDegree - qiblaDirection)
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
            durationMillis = 210,
            easing = LinearEasing
        ),
        label = "compassRotation"
    )
    
    // Lifecycle management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    sensorManager.registerListener(
                        sensorListener, 
                        sensor, 
                        SensorManager.SENSOR_DELAY_GAME
                    )
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
    
    // UI
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Background circle with accuracy indication
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
            
            // Accuracy indication border
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
        
        // Qibla direction indicator
        CircularProgressIndicator(
            progress = { 0.1f }, // 10% progress pointing to Qibla
            modifier = Modifier
                .size(size - 16.dp)
                .rotate(animatedCompassDegree),
            color = if (needsCalibration) Color(0xFFFF4444) else Color(0xFF10B981),
            strokeWidth = 8.dp,
            trackColor = Color.Black.copy(alpha = 0.1f),
            strokeCap = StrokeCap.Round,
        )
        
        // Content display
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
                // Time display
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Black.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (size >= 280.dp) 22.sp else 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Qibla direction with Kaaba emoji
                Text(
                    text = "🕋 Qibla",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (needsCalibration) Color(0xFFFF4444) else Color(0xFF10B981),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (size >= 280.dp) 16.sp else 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                // Guidance text for larger compasses
                if (size >= 260.dp) {
                    Text(
                        text = "Turn until green arc\npoints up ↑",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
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