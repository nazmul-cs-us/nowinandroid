package com.starception.submission.islamic.qibla.presentation.component

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
    
    // Enhanced UI with better novice user guidance
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Enhanced compass background with directional labels
        Canvas(
            modifier = Modifier.size(size)
        ) {
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val backgroundRadius = (this.size.minDimension / 2) - 2.dp.toPx()
            val labelRadius = backgroundRadius + 15.dp.toPx()
            
            // Gradient background for better depth
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        Color(0xFFF8F9FA)
                    ),
                    radius = backgroundRadius
                ),
                radius = backgroundRadius,
                center = center
            )
            
            // Accuracy indication border with enhanced styling
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
            
            // Cardinal direction markers and labels
            val directions = listOf(
                "N" to 0f,    // North (top)
                "E" to 90f,   // East (right)
                "S" to 180f,  // South (bottom)
                "W" to 270f   // West (left)
            )
            
            directions.forEach { (label, angle) ->
                val angleRad = Math.toRadians(angle.toDouble())
                
                // Direction marker lines
                val markerStart = Offset(
                    x = center.x + cos(angleRad).toFloat() * (backgroundRadius - 10.dp.toPx()),
                    y = center.y + sin(angleRad).toFloat() * (backgroundRadius - 10.dp.toPx())
                )
                val markerEnd = Offset(
                    x = center.x + cos(angleRad).toFloat() * backgroundRadius,
                    y = center.y + sin(angleRad).toFloat() * backgroundRadius
                )
                
                drawLine(
                    color = Color.Black.copy(alpha = 0.4f),
                    start = markerStart,
                    end = markerEnd,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        
        // Directional labels positioned outside the compass
        if (size >= 120.dp) {
            val directions = listOf(
                "N" to Offset(0f, -1f),    // North (top)
                "E" to Offset(1f, 0f),     // East (right)  
                "S" to Offset(0f, 1f),     // South (bottom)
                "W" to Offset(-1f, 0f)     // West (left)
            )
            
            directions.forEach { (label, offset) ->
                Box(
                    modifier = Modifier
                        .offset(
                            x = (offset.x * (size.value / 2 + 20)).dp,
                            y = (offset.y * (size.value / 2 + 20)).dp
                        )
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                        ),
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
        
        // Enhanced Qibla direction indicator with arrow
        Box(
            modifier = Modifier
                .size(size - 16.dp)
                .rotate(animatedCompassDegree),
            contentAlignment = Alignment.Center
        ) {
            // Qibla direction arc
            CircularProgressIndicator(
                progress = { 0.15f }, // 15% progress pointing to Qibla (more visible)
                modifier = Modifier.fillMaxSize(),
                color = if (needsCalibration) Color(0xFFFF4444) else Color(0xFF10B981),
                strokeWidth = 10.dp,
                trackColor = Color.Black.copy(alpha = 0.05f),
                strokeCap = StrokeCap.Round,
            )
            
            // Qibla arrow indicator at top of compass
            Box(
                modifier = Modifier
                    .offset(y = -(size.value / 2 - 25).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (needsCalibration) Color(0xFFFF4444) else Color(0xFF10B981)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Navigation,
                    contentDescription = "Qibla Direction",
                    modifier = Modifier
                        .size(16.dp)
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
                
                // Status text
                Text(
                    text = if (needsCalibration) "Qibla" else "Qibla",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (needsCalibration) Color(0xFFFF4444) else Color(0xFF10B981),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
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
                } else if (size >= 140.dp) {
                    Text(
                        text = "Turn phone until\narrow points up ↑",
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