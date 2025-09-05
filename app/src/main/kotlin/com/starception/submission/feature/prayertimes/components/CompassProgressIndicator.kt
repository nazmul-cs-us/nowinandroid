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
    var sensorAccuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_UNRELIABLE) }
    
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
                sensorAccuracy = accuracy
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
    
    // Helper functions for sensor accuracy
    fun getAccuracyColor(accuracy: Int): Color = when(accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> Color(0xFF10B981)
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> Color(0xFFFFA500)
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> Color(0xFFFF6B6B)
        else -> Color(0xFFFF4444)
    }
    
    fun getAccuracyText(accuracy: Int): String = when(accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High Accuracy"
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium Accuracy"
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low Accuracy - Calibrate"
        else -> "Move in Figure-8"
    }
    
    val accuracyColor = getAccuracyColor(sensorAccuracy)
    val needsCalibration = sensorAccuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
    
    // ENHANCED DESIGN - Better Qibla identification with accuracy feedback
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Perfect circle background with accuracy indication
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
                color = accuracyColor.copy(alpha = 0.3f),
                radius = backgroundRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            
            // Kaaba direction marker at the end of progress arc
            val qiblaAngle = Math.toRadians((animatedCompassDegree + 36.0)) // 10% of 360° = 36°
            val markerRadius = backgroundRadius - 12.dp.toPx()
            val markerX = center.x + cos(qiblaAngle).toFloat() * markerRadius
            val markerY = center.y + sin(qiblaAngle).toFloat() * markerRadius
            
            // Kaaba symbol (small black square)
            drawRect(
                color = Color.Black,
                topLeft = Offset(markerX - 4.dp.toPx(), markerY - 4.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(8.dp.toPx(), 8.dp.toPx())
            )
            
            // Glow effect around Kaaba marker
            drawCircle(
                color = Color(0xFFFFD700).copy(alpha = 0.4f),
                radius = 6.dp.toPx(),
                center = Offset(markerX, markerY)
            )
        }
        
        // QIBLA DIRECTION INDICATOR - Enhanced circular progress
        CircularProgressIndicator(
            progress = { 0.1f }, // 10% progress pointing to Qibla
            modifier = Modifier
                .size(size - 16.dp)
                .rotate(animatedCompassDegree),
            color = if (needsCalibration) Color(0xFFFF6B6B) else Color(0xFF10B981),
            strokeWidth = 8.dp,
            trackColor = Color.Black.copy(alpha = 0.1f),
            strokeCap = StrokeCap.Round,
        )
        
        // MAIN CONTENT - Time and direction info
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
                // Main time display
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Qibla direction with Kaaba emoji
                Text(
                    text = "🕋 Qibla",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF10B981),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        
        // ACCURACY STATUS - Bottom indicator
        if (needsCalibration) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accuracyColor.copy(alpha = 0.9f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = getAccuracyText(sensorAccuracy),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Enhanced progress ring with Islamic luxury styling
 */
private fun DrawScope.drawProgressRing(
    center: Offset,
    radius: Float,
    progress: Float
) {
    // Background track with Islamic pattern inspiration
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                Color(0xFF374151).copy(alpha = 0.4f),
                Color(0xFF4B5563).copy(alpha = 0.3f),
                Color(0xFF6B7280).copy(alpha = 0.2f),
                Color(0xFF374151).copy(alpha = 0.4f)
            ),
            center = center
        ),
        radius = radius,
        center = center,
        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
    )
    
    // Inner shadow ring for depth
    drawCircle(
        color = Color(0xFF1F2937).copy(alpha = 0.5f),
        radius = radius - 3.dp.toPx(),
        center = center,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )
    
    if (progress > 0f) {
        val sweepAngle = progress * 360f
        
        // Progress glow effect
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFF10B981).copy(alpha = 0.3f),
                    Color(0xFFFFD700).copy(alpha = 0.4f),
                    Color(0xFF059669).copy(alpha = 0.3f)
                ),
                center = center
            ),
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius - 2.dp.toPx(), center.y - radius - 2.dp.toPx()),
            size = androidx.compose.ui.geometry.Size((radius + 2.dp.toPx()) * 2, (radius + 2.dp.toPx()) * 2),
            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Main progress arc with Islamic colors
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFF10B981),
                    Color(0xFFFFD700),
                    Color(0xFF059669)
                ),
                center = center
            ),
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Inner bright highlight
        drawArc(
            color = Color(0xFFFFFFFF).copy(alpha = 0.7f),
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius + 1.dp.toPx(), center.y - radius + 1.dp.toPx()),
            size = androidx.compose.ui.geometry.Size((radius - 1.dp.toPx()) * 2, (radius - 1.dp.toPx()) * 2),
            style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Enhanced Islamic Qibla compass needle with luxury styling
 */
private fun DrawScope.drawCompassNeedle(
    center: Offset,
    radius: Float
) {
    val qiblaEnd = Offset(center.x, center.y - radius)
    val oppositeEnd = Offset(center.x, center.y + radius * 0.6f)
    
    // Sacred Qibla direction needle with glow effect
    // Glow/shadow effect
    drawLine(
        color = Color(0xFF10B981).copy(alpha = 0.4f),
        start = center,
        end = qiblaEnd,
        strokeWidth = 8.dp.toPx(),
        cap = StrokeCap.Round
    )
    
    // Main Qibla needle with Islamic green and gold gradient
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFD700),
                Color(0xFF10B981),
                Color(0xFF059669)
            ),
            start = center,
            end = qiblaEnd
        ),
        start = center,
        end = qiblaEnd,
        strokeWidth = 5.dp.toPx(),
        cap = StrokeCap.Round
    )
    
    // Bright highlight on Qibla needle
    drawLine(
        color = Color(0xFFFFFFFF).copy(alpha = 0.9f),
        start = center,
        end = qiblaEnd,
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round
    )
    
    // Enhanced opposite direction needle
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF6B7280),
                Color(0xFF4B5563)
            ),
            start = center,
            end = oppositeEnd
        ),
        start = center,
        end = oppositeEnd,
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round
    )
    
    // Sacred Qibla arrowhead
    val arrowSize = 8.dp.toPx()
    val arrowAngle = kotlin.math.PI / 6
    
    val leftArrow = Offset(
        qiblaEnd.x - sin(arrowAngle).toFloat() * arrowSize,
        qiblaEnd.y + cos(arrowAngle).toFloat() * arrowSize
    )
    
    val rightArrow = Offset(
        qiblaEnd.x + sin(arrowAngle).toFloat() * arrowSize,
        qiblaEnd.y + cos(arrowAngle).toFloat() * arrowSize
    )
    
    // Arrowhead with Islamic styling
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFD700),
                Color(0xFF10B981)
            )
        ),
        start = qiblaEnd,
        end = leftArrow,
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round
    )
    
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFD700),
                Color(0xFF10B981)
            )
        ),
        start = qiblaEnd,
        end = rightArrow,
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round
    )
    
    // Enhanced center with Islamic geometric design
    // Outer glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFD700).copy(alpha = 0.3f),
                Color.Transparent
            ),
            radius = 8.dp.toPx()
        ),
        radius = 6.dp.toPx(),
        center = center
    )
    
    // Main center circle
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFD700),
                Color(0xFF10B981),
                Color(0xFF1E293B)
            )
        ),
        radius = 4.dp.toPx(),
        center = center
    )
    
    // Inner bright center
    drawCircle(
        color = Color(0xFFFFFFFF).copy(alpha = 0.9f),
        radius = 1.5.dp.toPx(),
        center = center
    )
}

