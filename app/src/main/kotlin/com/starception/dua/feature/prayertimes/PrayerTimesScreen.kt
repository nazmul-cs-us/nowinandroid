package com.starception.submission.feature.prayertimes

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import com.starception.submission.prayer.service.PrayerTimeCalculatorService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.starception.submission.core.designsystem.theme.NiaTheme
import com.starception.submission.prayer.viewmodel.PrayerTimesViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.Duration
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.Location
import java.time.format.DateTimeFormatter
import java.time.LocalTime
import kotlin.math.*
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PrayerTimeCalculatorEntryPoint {
    fun prayerTimeCalculatorService(): PrayerTimeCalculatorService
}

/**
 * Prayer Times screen showing daily prayer schedule
 * Settings are accessed via the main app's context-aware settings button
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PrayerTimesScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var prayerTimes by remember { mutableStateOf<com.starception.submission.prayer.model.DayPrayerTimes?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var location by remember { mutableStateOf("Loading location...") }
    
    // Calculate prayer times in background to prevent blocking
    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.Default) {
                // Get prayer calculator service
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    PrayerTimeCalculatorEntryPoint::class.java
                )
                val calculator = entryPoint.prayerTimeCalculatorService()
                
                // Use default location (can be improved with GPS later)
                val defaultLocation = com.starception.submission.prayer.model.Location(
                    latitude = 25.2048,  // Dubai coordinates as default
                    longitude = 55.2708,
                    timeZoneOffset = 4.0, // UAE timezone
                    city = "Dubai",
                    country = "UAE"
                )
                
                // Default prayer settings
                val settings = com.starception.submission.prayer.model.PrayerSettings()
                
                // Calculate for today
                val today = java.time.LocalDate.now()
                val calculatedTimes = calculator.calculatePrayerTimes(today, defaultLocation, settings)
                
                prayerTimes = calculatedTimes
                location = defaultLocation.getDisplayName()
                isLoading = false
            }
        } catch (e: Exception) {
            // Fallback to static times if calculation fails
            isLoading = false
        }
    }
    
    // Use same layout pattern as other tabs
    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(300.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalItemSpacing = 24.dp,
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Prayer times header card
            item(span = StaggeredGridItemSpan.FullLine) {
                PrayerTimesHeaderCard(
                    location = location,
                    date = getCurrentDate()
                )
            }
            
            if (isLoading) {
                // Loading card
                item(span = StaggeredGridItemSpan.FullLine) {
                    PrayerTimesLoadingCard()
                }
            } else {
                // Prayer times cards
                val times = prayerTimes
                if (times != null) {
                    // Dynamic prayer times
                    item { PrayerTimeCard("Fajr", formatTime(times.fajr)) }
                    item { PrayerTimeCard("Sunrise", formatTime(times.sunrise)) }
                    item { PrayerTimeCard("Dhuhr", formatTime(times.dhuhr)) }
                    item { PrayerTimeCard("Asr", formatTime(times.asr)) }
                    item { PrayerTimeCard("Maghrib", formatTime(times.maghrib)) }
                    item { PrayerTimeCard("Isha", formatTime(times.isha)) }
                } else {
                    // Fallback static times
                    item { PrayerTimeCard("Fajr", "5:30 AM") }
                    item { PrayerTimeCard("Sunrise", "6:45 AM") }
                    item { PrayerTimeCard("Dhuhr", "12:15 PM") }
                    item { PrayerTimeCard("Asr", "3:45 PM") }
                    item { PrayerTimeCard("Maghrib", "6:30 PM") }
                    item { PrayerTimeCard("Isha", "8:00 PM") }
                }
            }
        }
    }
}

private fun formatTime(time: java.time.LocalTime): String {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
    return time.format(formatter)
}

@Composable
private fun PrayerTimesHeaderCard(
    location: String,
    date: String
) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Prayer Times",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "📍 $location",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "📅 $date",
                style = MaterialTheme.typography.bodyMedium, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PrayerTimesLoadingCard() {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun PrayerTimeCard(
    prayerName: String,
    time: String
) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = prayerName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = time,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun getCurrentDate(): String {
    val formatter = java.text.SimpleDateFormat("EEEE, MMMM dd, yyyy", java.util.Locale.getDefault())
    return formatter.format(java.util.Date())
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PrayerTimesContent(
            uiState: com.starception.submission.prayer.viewmodel.PrayerTimesUiState,
    locationPermissions: com.google.accompanist.permissions.MultiplePermissionsState,
    onRefresh: () -> Unit,
    onRefreshButton: () -> Unit,
    onRequestLocation: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(300.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalItemSpacing = 24.dp,
        modifier = modifier.fillMaxSize()
    ) {
        // Error handling
        uiState.error?.let { error ->
            item(span = StaggeredGridItemSpan.FullLine, contentType = "error") {
                Card(
                modifier = Modifier.padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Error",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = onClearError) {
                            Text("Dismiss")
                        }
                        if (error.contains("Location", ignoreCase = true) || error.contains("permission", ignoreCase = true)) {
                            TextButton(onClick = onRequestLocation) {
                                Text(
                                    if (locationPermissions.allPermissionsGranted) "Get Location" 
                                    else "Grant Permission"
                                )
                            }
                        }
                        TextButton(onClick = onRefreshButton) {
                            Text("Retry")
                        }
                    }
                }
            }
            }
        }
        
        // Loading state
        if (uiState.isLoading) {
            item(span = StaggeredGridItemSpan.FullLine, contentType = "loading") {
                Card(
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Calculating prayer times...")
                    }
                }
            }
            }
        }
        
        // Location loading
        if (uiState.isLoadingLocation) {
            item(span = StaggeredGridItemSpan.FullLine, contentType = "locationLoading") {
                Card(
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Getting location...")
                    }
                }
            }
            }
        }
        
        // Prayer times display
        uiState.prayerTimes?.let { prayerTimes ->
            // Unified prayer time and Qibla dial
            item(span = StaggeredGridItemSpan.FullLine, contentType = "unifiedPrayerDial") {
                UnifiedPrayerAndQiblaDial(
                    prayerTimes = prayerTimes,
                    timeUntilNext = uiState.timeUntilNext,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
        
        // No prayer times available
        if (!uiState.isLoading && uiState.prayerTimes == null && uiState.error == null) {
            item(span = StaggeredGridItemSpan.FullLine, contentType = "noPrayerTimes") {
                Card(
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "No Prayer Times Available",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Please set your location to calculate prayer times.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onRequestLocation) {
                        Text(
                            if (locationPermissions.allPermissionsGranted) "Get My Location"
                            else "Grant Location Permission"
                        )
                    }
                }
            }
            }
        }
    }
}

/**
 * Unified prayer time and Qibla dial with modern swipe UI and pagination
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
private fun UnifiedPrayerAndQiblaDial(
    prayerTimes: DayPrayerTimes,
    timeUntilNext: String?,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val context = LocalContext.current
    var deviceOrientation by remember { mutableStateOf(0f) }
    
    // Qibla direction calculation
    val qiblaAngle = remember(prayerTimes.location) {
        prayerTimes.location?.let { location ->
            calculateQiblaDirection(
                lat1 = location.latitude,
                lon1 = location.longitude,
                lat2 = 21.4225, // Makkah coordinates
                lon2 = 39.8262
            )
        } ?: 0.0
    }
    
    // Sensor listener for device orientation
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        
        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val rotationMatrix = FloatArray(9)
                    val orientationAngles = FloatArray(3)
                    
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, it.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    
                    // Convert radians to degrees and normalize
                    deviceOrientation = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                    if (deviceOrientation < 0) deviceOrientation += 360f
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        if (rotationSensor != null) {
            sensorManager.registerListener(
                sensorEventListener,
                rotationSensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        
        onDispose {
            sensorManager.unregisterListener(sensorEventListener)
        }
    }
    
    // Calculate final qibla direction
    val finalQiblaDirection = (qiblaAngle - deviceOrientation).toFloat()
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with view title and close button style
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // View title
                Text(
                    text = if (pagerState.currentPage == 0) "Analog Watch" else "Digital View",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // View indicator icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (pagerState.currentPage == 0) Icons.Default.Schedule else Icons.Default.ViewList,
                        contentDescription = "View Type",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Main content area with smooth horizontal swipe
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) { page ->
                when (page) {
                    0 -> {
                        // Analog circular view with Qibla
                        AnalogPrayerAndQiblaView(
                            prayerTimes = prayerTimes,
                            timeUntilNext = timeUntilNext,
                            qiblaDirection = finalQiblaDirection,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    1 -> {
                        // Digital list view
                        DigitalPrayerView(
                            prayerTimes = prayerTimes,
                            timeUntilNext = timeUntilNext,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Pagination dots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(2) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(
                                width = if (pagerState.currentPage == index) 24.dp else 8.dp,
                                height = 8.dp
                            )
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (pagerState.currentPage == index) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Qibla info (always visible) with device orientation
            QiblaInfoSection(
                currentLocation = prayerTimes.location,
                qiblaDirection = finalQiblaDirection,
                deviceOrientation = deviceOrientation
            )
        }
    }
}

@Composable
private fun AnalogPrayerAndQiblaView(
    prayerTimes: DayPrayerTimes,
    timeUntilNext: String?,
    qiblaDirection: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f), // Make it square
        contentAlignment = Alignment.Center
    ) {
        // Calculate size based on available space
        val watchSize = 320.dp
        
        // Premium background with multiple layers
        Box(
            modifier = Modifier
                .size(watchSize)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF1E293B),
                            Color(0xFF334155)
                        ),
                        center = Offset(0.2f, 0.2f)
                    )
                )
        )
        
        // Outer metallic ring with shimmer effect
        Box(
            modifier = Modifier
                .size(watchSize)
                .padding(2.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF60A5FA),
                            Color(0xFF3B82F6),
                            Color(0xFF1E40AF),
                            Color(0xFF1E3A8A)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1f, 1f)
                    )
                )
        )
        
        // Inner premium ring
        Box(
            modifier = Modifier
                .size(watchSize - 6.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            Color(0xFF0F172A)
                        )
                    )
                )
        )
        
        // Subtle inner glow
        Box(
            modifier = Modifier
                .size(watchSize - 12.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF3B82F6).copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        // Enhanced hour markers with premium design
        PremiumHourMarkers(watchSize)
        
        // Premium prayer time progress ring
        PremiumPrayerProgressRing(prayerTimes, watchSize)
        
        // Enhanced prayer time indicators
        PremiumPrayerIndicators(prayerTimes, watchSize)
        
        // Premium Qibla compass hand
        PremiumQiblaCompassHand(qiblaDirection, watchSize)
        
        // Premium center design
        PremiumCenterDesign(watchSize)
        
        // Enhanced current time info
        PremiumCurrentTimeInfo(prayerTimes, timeUntilNext, watchSize)
        
        // Floating prayer time labels
        FloatingPrayerLabels(prayerTimes, watchSize)
    }
}

@Composable
private fun DigitalPrayerView(
    prayerTimes: DayPrayerTimes,
    timeUntilNext: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Next prayer info
        if (timeUntilNext != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Next Prayer",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        val nextPrayer = prayerTimes.getNextPrayer()
                        Text(
                            text = "Next: ${nextPrayer?.name ?: "Loading..."}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "in $timeUntilNext",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Prayer times list
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val prayers = listOf(
                "Fajr" to prayerTimes.fajr,
                "Dhuhr" to prayerTimes.dhuhr,
                "Asr" to prayerTimes.asr,
                "Maghrib" to prayerTimes.maghrib,
                "Isha" to prayerTimes.isha
            )
            
            prayers.forEach { (name, time) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(
                        text = time.format(DateTimeFormatter.ofPattern("h:mm a")),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun QiblaInfoSection(
    currentLocation: Location?,
    qiblaDirection: Float,
    deviceOrientation: Float = 0f
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Qibla Direction",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Qibla Direction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (currentLocation != null) {
                    Text(
                        text = "🕋 Qibla: ${String.format("%.1f°", qiblaDirection)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "📱 Device: ${String.format("%.1f°", deviceOrientation)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "📍 ${currentLocation.getDisplayName()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Location required for Qibla direction",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumHourMarkers(watchSize: Dp) {
    // Premium cardinal directions with enhanced design
    val center = watchSize.value / 2f
    val radius = watchSize.value / 2f - 20.dp.value
    
    // Cardinal and intercardinal directions
    val directions = listOf(
        "N" to 0f,
        "NE" to 45f,
        "E" to 90f,
        "SE" to 135f,
        "S" to 180f,
        "SW" to 225f,
        "W" to 270f,
        "NW" to 315f
    )
    
    directions.forEach { (direction, angle) ->
        val angleRad = Math.toRadians(angle.toDouble())
        
        Box(
            modifier = Modifier
                .offset(
                    x = (center + (cos(angleRad) * (radius - 50.dp.value)) - 18.dp.value).dp,
                    y = (center + (sin(angleRad) * (radius - 50.dp.value)) - 18.dp.value).dp
                )
                .size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            // Premium background circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF3B82F6).copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            // Direction text with premium styling
            Text(
                text = direction,
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF60A5FA),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
    
    Canvas(modifier = Modifier.size(watchSize)) {
        val canvasCenter = Offset(size.width / 2, size.height / 2)
        val canvasRadius = size.width / 2 - 20.dp.value
        
        // Major degree markers (every 30 degrees)
        for (i in 0..11) {
            val angle = i * 30f
            val angleRad = Math.toRadians(angle.toDouble())
            
            // Major tick mark
            val startPoint = Offset(
                canvasCenter.x + (cos(angleRad) * (canvasRadius - 15.dp.value)).toFloat(),
                canvasCenter.y + (sin(angleRad) * (canvasRadius - 15.dp.value)).toFloat()
            )
            val endPoint = Offset(
                canvasCenter.x + (cos(angleRad) * canvasRadius).toFloat(),
                canvasCenter.y + (sin(angleRad) * canvasRadius).toFloat()
            )
            
            drawLine(
                color = Color.White,
                start = startPoint,
                end = endPoint,
                strokeWidth = 3.dp.value
            )
        }
        
        // Minor tick marks (every 5 degrees)
        for (i in 0..71) {
            val angle = i * 5f
            val angleRad = Math.toRadians(angle.toDouble())
            
            // Skip major markers
            if (i % 6 != 0) {
                val startPoint = Offset(
                    canvasCenter.x + (cos(angleRad) * (canvasRadius - 12.dp.value)).toFloat(),
                    canvasCenter.y + (sin(angleRad) * (canvasRadius - 12.dp.value)).toFloat()
                )
                val endPoint = Offset(
                    canvasCenter.x + (cos(angleRad) * canvasRadius).toFloat(),
                    canvasCenter.y + (sin(angleRad) * canvasRadius).toFloat()
                )
                
                drawLine(
                    color = Color.White.copy(alpha = 0.6f),
                    start = startPoint,
                    end = endPoint,
                    strokeWidth = 1.dp.value
                )
            }
        }
    }
    
    // Major degree numbers (every 30 degrees)
    for (i in 0..11) {
        val angle = i * 30f
        val angleRad = Math.toRadians(angle.toDouble())
        val textRadius = radius - 35.dp.value
        val textX = (cos(angleRad) * textRadius).dp
        val textY = (sin(angleRad) * textRadius).dp
        
        Text(
            text = "${(i * 30)}",
            modifier = Modifier
                .offset(
                    x = (center + textX.value - 12.dp.value).dp,
                    y = (center + textY.value - 8.dp.value).dp
                ),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
    
    // Red line indicator for North
    val northAngleRad = Math.toRadians(0.0)
    Canvas(
        modifier = Modifier
            .offset(
                x = (center + (cos(northAngleRad) * (radius - 25.dp.value)) - 1.dp.value).dp,
                y = (center + (sin(northAngleRad) * (radius - 25.dp.value)) - 1.dp.value).dp
            )
            .size(2.dp, 20.dp)
    ) {
        drawLine(
            color = Color.Red,
            start = Offset(0f, 0f),
            end = Offset(0f, size.height),
            strokeWidth = 2.dp.value
        )
    }
}

@Composable
private fun PremiumPrayerProgressRing(prayerTimes: DayPrayerTimes, watchSize: Dp) {
    val currentTime = LocalTime.now()
    val prayers = listOf(
        prayerTimes.fajr,
        prayerTimes.dhuhr,
        prayerTimes.asr,
        prayerTimes.maghrib,
        prayerTimes.isha
    )
    
    // Find current prayer period
    val currentPrayerIndex = prayers.indexOfFirst { time ->
        val nextPrayerTime = prayers.getOrNull(prayers.indexOf(time) + 1) ?: prayers[0]
        currentTime >= time && (if (nextPrayerTime > time) currentTime < nextPrayerTime else true)
    }.let { if (it == -1) 0 else it }
    
    val currentPrayerTime = prayers[currentPrayerIndex]
    val nextPrayerTime = prayers.getOrNull(currentPrayerIndex + 1) ?: prayers[0]
    
    val progress = if (nextPrayerTime > currentPrayerTime) {
        val totalDuration = Duration.between(currentPrayerTime, nextPrayerTime)
        val elapsed = Duration.between(currentPrayerTime, currentTime)
        (elapsed.toMinutes().toFloat() / totalDuration.toMinutes().toFloat()).coerceIn(0f, 1f)
    } else {
        val totalDuration = Duration.between(currentPrayerTime, nextPrayerTime.plusHours(24))
        val elapsed = Duration.between(currentPrayerTime, currentTime)
        (elapsed.toMinutes().toFloat() / totalDuration.toMinutes().toFloat()).coerceIn(0f, 1f)
    }
    
    Canvas(
        modifier = Modifier.size(watchSize)
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.width / 2 - 50f
        
        // Premium background ring with gradient
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF374151).copy(alpha = 0.4f),
                    Color(0xFF1F2937).copy(alpha = 0.2f)
                )
            ),
            center = center,
            radius = radius,
            style = Stroke(width = 12f, cap = StrokeCap.Round)
        )
        
        // Premium progress arc with multiple effects
        val startAngle = -90f
        val sweepAngle = 360f * progress
        
        // Glow effect
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFFFF4444).copy(alpha = 0.3f),
                    Color(0xFFFF6B6B).copy(alpha = 0.2f)
                ),
                center = center
            ),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius - 4f, center.y - radius - 4f),
            size = Size((radius + 4f) * 2, (radius + 4f) * 2),
            style = Stroke(width = 20f, cap = StrokeCap.Round)
        )
        
        // Main progress arc
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFFFF4444),
                    Color(0xFFFF6B6B),
                    Color(0xFFFF8E8E)
                ),
                center = center
            ),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 12f, cap = StrokeCap.Round)
        )
        
        // Inner highlight
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFFFF8E8E),
                    Color(0xFFFFB3B3)
                ),
                center = center
            ),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius + 2f, center.y - radius + 2f),
            size = Size((radius - 2f) * 2, (radius - 2f) * 2),
            style = Stroke(width = 8f, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun PremiumPrayerIndicators(prayerTimes: DayPrayerTimes, watchSize: Dp) {
    val prayers = listOf(
        "Fajr" to prayerTimes.fajr,
        "Dhuhr" to prayerTimes.dhuhr,
        "Asr" to prayerTimes.asr,
        "Maghrib" to prayerTimes.maghrib,
        "Isha" to prayerTimes.isha
    )
    
    val currentTime = LocalTime.now()
    
    prayers.forEach { (name, time) ->
        val hour = time.hour
        val minute = time.minute
        val timeInHours = hour + minute / 60f
        val angle = (timeInHours * 15f) - 90f
        val radius = (watchSize.value * 0.36f).dp
        val angleRad = Math.toRadians(angle.toDouble())
        
        val isActive = currentTime >= time && 
            prayers.find { it.second > time }?.let { currentTime < it.second } ?: true
        
        Box(
            modifier = Modifier
                .offset(
                    x = (cos(angleRad) * radius.value).dp,
                    y = (sin(angleRad) * radius.value).dp
                )
                .size(if (isActive) 20.dp else 12.dp),
            contentAlignment = Alignment.Center
        ) {
            // Premium background with glow
            Box(
                modifier = Modifier
                    .size(if (isActive) 20.dp else 12.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) {
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFF4444),
                                    Color(0xFFFF6B6B)
                                )
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF60A5FA),
                                    Color(0xFF3B82F6)
                                )
                            )
                        }
                    )
            )
            
            // Inner highlight
            Box(
                modifier = Modifier
                    .size(if (isActive) 12.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) Color(0xFFFFB3B3) else Color(0xFF93C5FD)
                    )
            )
        }
    }
}

@Composable
private fun PremiumQiblaCompassHand(qiblaDirection: Float, watchSize: Dp) {
    // Animate the rotation for smooth movement
    val animatedDirection by animateFloatAsState(
        targetValue = qiblaDirection,
        animationSpec = tween(300, easing = EaseOutCubic)
    )
    
    Canvas(
        modifier = Modifier.size(watchSize)
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val handLength = size.width / 2 - 50f
        // Fix Qibla direction calculation - North should be at top (0°), East at right (90°)
        val angle = Math.toRadians((animatedDirection - 90).toDouble())
        
        val endPoint = Offset(
            (center.x + cos(angle) * handLength).toFloat(),
            (center.y + sin(angle) * handLength).toFloat()
        )
        
        // Draw glow effect first
        drawLine(
            color = Color(0xFFFFD700).copy(alpha = 0.3f),
            start = center,
            end = endPoint,
            strokeWidth = 12f,
            cap = StrokeCap.Round
        )
        
        // Draw main compass hand
        drawLine(
            color = Color(0xFFFFD700),
            start = center,
            end = endPoint,
            strokeWidth = 8f,
            cap = StrokeCap.Round
        )
        
        // Draw center dot
        drawCircle(
            color = Color(0xFFFFD700),
            radius = 6f,
            center = center
        )
        
        // Draw arrowhead
        val arrowLength = 25f
        val arrowAngle = PI / 5
        
        val leftArrow = Offset(
            (endPoint.x - cos(angle - arrowAngle) * arrowLength).toFloat(),
            (endPoint.y - sin(angle - arrowAngle) * arrowLength).toFloat()
        )
        
        val rightArrow = Offset(
            (endPoint.x - cos(angle + arrowAngle) * arrowLength).toFloat(),
            (endPoint.y - sin(angle + arrowAngle) * arrowLength).toFloat()
        )
        
        // Draw arrowhead with glow
        drawLine(
            color = Color(0xFFFFD700).copy(alpha = 0.3f),
            start = endPoint,
            end = leftArrow,
            strokeWidth = 8f,
            cap = StrokeCap.Round
        )
        
        drawLine(
            color = Color(0xFFFFD700).copy(alpha = 0.3f),
            start = endPoint,
            end = rightArrow,
            strokeWidth = 8f,
            cap = StrokeCap.Round
        )
        
        drawLine(
            color = Color(0xFFFFD700),
            start = endPoint,
            end = leftArrow,
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )
        
        drawLine(
            color = Color(0xFFFFD700),
            start = endPoint,
            end = rightArrow,
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )
        
        // Draw Kaaba icon at the tip
        drawCircle(
            color = Color(0xFF000000),
            radius = 8f,
            center = endPoint
        )
        
        drawCircle(
            color = Color(0xFFFFD700),
            radius = 6f,
            center = endPoint
        )
    }
}

@Composable
private fun CurrentTimeInfo(prayerTimes: DayPrayerTimes, timeUntilNext: String?, watchSize: Dp) {
    val currentTime = LocalTime.now()
    val prayers = listOf(
        "Fajr" to prayerTimes.fajr,
        "Dhuhr" to prayerTimes.dhuhr,
        "Asr" to prayerTimes.asr,
        "Maghrib" to prayerTimes.maghrib,
        "Isha" to prayerTimes.isha
    )
    
    val currentPrayer = prayers.find { (_, time) ->
        val nextPrayer = prayers.find { it.second > time }
        currentTime >= time && (nextPrayer?.let { currentTime < it.second } ?: true)
    }?.first ?: "Fajr"
    
    Box(
        modifier = Modifier
            .offset(y = (watchSize.value * 0.25f).dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentPrayer,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFEF4444),
                fontWeight = FontWeight.Bold
            )
            if (timeUntilNext != null) {
                Text(
                    text = "Next in $timeUntilNext",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ProfessionalHourMarkers(watchSize: Dp) {
    // Major hour markers with cardinal directions
    val majorHours = listOf(0, 6, 12, 18)
    val cardinalDirections = listOf("N", "E", "S", "W")
    
    majorHours.forEachIndexed { index, hour ->
        val angle = (hour * 15f) - 90f // 360°/24h = 15° per hour
        val radius = (watchSize.value * 0.42f).dp
        
        Box(
            modifier = Modifier
                .offset(
                    x = (cos(Math.toRadians(angle.toDouble())) * radius.value).dp,
                    y = (sin(Math.toRadians(angle.toDouble())) * radius.value).dp
                )
                .size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = cardinalDirections[index],
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF3B82F6),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (hour == 0) "24" else hour.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
    
    // Minor hour markers with subtle design
    val minorHours = listOf(3, 9, 15, 21)
    minorHours.forEach { hour ->
        val angle = (hour * 15f) - 90f
        val radius = (watchSize.value * 0.41f).dp
        
        Box(
            modifier = Modifier
                .offset(
                    x = (cos(Math.toRadians(angle.toDouble())) * radius.value).dp,
                    y = (sin(Math.toRadians(angle.toDouble())) * radius.value).dp
                )
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF3B82F6),
                            Color(0xFF1E40AF)
                        )
                    )
                )
        )
    }
    
    // Quarter hour markers (every 1.5 hours)
    val quarterHours = listOf(1.5f, 4.5f, 7.5f, 10.5f, 13.5f, 16.5f, 19.5f, 22.5f)
    quarterHours.forEach { hour ->
        val angle = (hour * 15f) - 90f
        val radius = (watchSize.value * 0.405f).dp
        
        Box(
            modifier = Modifier
                .offset(
                    x = (cos(Math.toRadians(angle.toDouble())) * radius.value).dp,
                    y = (sin(Math.toRadians(angle.toDouble())) * radius.value).dp
                )
                .size(2.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun MakkahCompassHand(qiblaDirection: Float, watchSize: Dp) {
    Box(
        modifier = Modifier.size(watchSize)
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val handLength = 140f
            val handWidth = 8f
            
            // Draw compass hand with professional design
            val angle = qiblaDirection * (PI / 180f)
            
            // Hand body (main shaft)
            drawLine(
                color = Color(0xFFFFD700), // Gold
                start = Offset(
                    (center.x - sin(angle) * handWidth / 2).toFloat(),
                    (center.y + cos(angle) * handWidth / 2).toFloat()
                ),
                end = Offset(
                    (center.x + sin(angle) * handWidth / 2).toFloat(),
                    (center.y - cos(angle) * handWidth / 2).toFloat()
                ),
                strokeWidth = handWidth,
                cap = StrokeCap.Round
            )
            
            // Hand tip (arrowhead)
            val tipLength = 20f
            val tipAngle = PI / 6 // 30 degrees
            
            val tipStart = Offset(
                (center.x + cos(angle) * (handLength - tipLength)).toFloat(),
                (center.y + sin(angle) * (handLength - tipLength)).toFloat()
            )
            
            val tipEnd = Offset(
                (center.x + cos(angle) * handLength).toFloat(),
                (center.y + sin(angle) * handLength).toFloat()
            )
            
            // Left tip line
            val leftTipAngle = angle + PI - tipAngle
            val leftTipEnd = Offset(
                (tipStart.x + cos(leftTipAngle) * tipLength).toFloat(),
                (tipStart.y + sin(leftTipAngle) * tipLength).toFloat()
            )
            
            // Right tip line
            val rightTipAngle = angle + PI + tipAngle
            val rightTipEnd = Offset(
                (tipStart.x + cos(rightTipAngle) * tipLength).toFloat(),
                (tipStart.y + sin(rightTipAngle) * tipLength).toFloat()
            )
            
            // Draw tip lines
            drawLine(
                color = Color(0xFFFFD700),
                start = tipStart,
                end = leftTipEnd,
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
            
            drawLine(
                color = Color(0xFFFFD700),
                start = tipStart,
                end = rightTipEnd,
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
            
            // Add glow effect
            drawLine(
                color = Color(0xFFFFD700).copy(alpha = 0.3f),
                start = Offset(
                    (center.x - sin(angle) * handWidth / 2).toFloat(),
                    (center.y + cos(angle) * handWidth / 2).toFloat()
                ),
                end = Offset(
                    (center.x + sin(angle) * handWidth / 2).toFloat(),
                    (center.y - cos(angle) * handWidth / 2).toFloat()
                ),
                strokeWidth = handWidth + 4f,
                cap = StrokeCap.Round
            )
        }
        
        // Makkah direction label
        Box(
            modifier = Modifier
                .offset(
                    x = (watchSize.value * 0.25f).dp,
                    y = (watchSize.value * 0.25f).dp
                )
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFD700).copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🕋",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 16.sp
                )
                Text(
                    text = "${qiblaDirection.toInt()}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CenterPivot(watchSize: Dp) {
    Box(
        modifier = Modifier.size(watchSize),
        contentAlignment = Alignment.Center
    ) {
        // Central pivot with crosshairs
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFD700),
                            Color(0xFFFFA500)
                        )
                    )
                )
        )
        
        // Crosshairs
        Canvas(
            modifier = Modifier.size(32.dp)
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            
            // Horizontal line
            drawLine(
                color = Color.White.copy(alpha = 0.8f),
                start = Offset(center.x - 8f, center.y),
                end = Offset(center.x + 8f, center.y),
                strokeWidth = 1f,
                cap = StrokeCap.Round
            )
            
            // Vertical line
            drawLine(
                color = Color.White.copy(alpha = 0.8f),
                start = Offset(center.x, center.y - 8f),
                end = Offset(center.x, center.y + 8f),
                strokeWidth = 1f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun CurrentPrayerIndicator(prayerTimes: DayPrayerTimes, watchSize: Dp) {
    val currentTime = LocalTime.now()
    val prayers = listOf(
        "Fajr" to prayerTimes.fajr,
        "Dhuhr" to prayerTimes.dhuhr,
        "Asr" to prayerTimes.asr,
        "Maghrib" to prayerTimes.maghrib,
        "Isha" to prayerTimes.isha
    )
    
    // Find which prayer time we're currently in
    val currentPrayerIndex = prayers.indexOfFirst { (_, time) ->
        val prayerTime = time
        val nextPrayerTime = prayers.getOrNull(prayers.indexOfFirst { it.second > time } + 1)?.second
        currentTime >= prayerTime && (nextPrayerTime == null || currentTime < nextPrayerTime)
    }.let { if (it == -1) 0 else it }
    
    val currentPrayerName = prayers[currentPrayerIndex].first
    
    Box(
        modifier = Modifier
            .offset(
                x = -(watchSize.value * 0.15f).dp,
                y = (watchSize.value * 0.15f).dp
            )
            .size(80.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF4444),
                        Color(0xFFFF6B6B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🕌",
                style = MaterialTheme.typography.labelMedium,
                fontSize = 20.sp
            )
            Text(
                text = currentPrayerName,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PrayerTimeLabels(prayerTimes: DayPrayerTimes, watchSize: Dp = 300.dp) {
    val prayers = listOf(
        "Fajr" to prayerTimes.fajr,
        "Dhuhr" to prayerTimes.dhuhr,
        "Asr" to prayerTimes.asr,
        "Maghrib" to prayerTimes.maghrib,
        "Isha" to prayerTimes.isha
    )
    
    // Calculate current time and find active prayer
    val currentTime = LocalTime.now()
    val activePrayerIndex = prayers.indexOfFirst { (_, time) ->
        val prayerTime = time
        val nextPrayerTime = prayers.getOrNull(prayers.indexOfFirst { it.second > time } + 1)?.second
        currentTime >= prayerTime && (nextPrayerTime == null || currentTime < nextPrayerTime)
    }.let { if (it == -1) 0 else it }
    
    prayers.forEachIndexed { index, (name, time) ->
        // Calculate angle based on actual time (24-hour clock)
        val hour = time.hour
        val minute = time.minute
        val timeInHours = hour + minute / 60f
        val angle = (timeInHours * 15f) - 90f // 360°/24h = 15° per hour, -90° to start at 12 o'clock
        val radius = (watchSize.value * 0.35f).dp // Adjust radius based on watch size
        val isActive = index == activePrayerIndex
        
        Box(
            modifier = Modifier
                .offset(
                    x = (cos(Math.toRadians(angle.toDouble())) * radius.value).dp,
                    y = (sin(Math.toRadians(angle.toDouble())) * radius.value).dp
                )
                .size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            // Active prayer indicator (radar effect)
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFD700).copy(alpha = 0.3f),
                                    Color(0xFFFFD700).copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isActive) Color(0xFFFFD700) else Color.White,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    text = time.format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) Color(0xFFFFD700) else Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                
                // Active indicator dot
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFD700))
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentTimeHand(prayerTimes: DayPrayerTimes, watchSize: Dp = 300.dp) {
    val currentTime = LocalTime.now()
    val prayers = listOf(
        prayerTimes.fajr,
        prayerTimes.dhuhr,
        prayerTimes.asr,
        prayerTimes.maghrib,
        prayerTimes.isha
    )
    
    // Find which prayer time we're currently in
    val currentPrayerIndex = prayers.indexOfFirst { time ->
        val prayerTime = time
        val nextPrayerTime = prayers.getOrNull(prayers.indexOfFirst { it > time } + 1)
        currentTime >= prayerTime && (nextPrayerTime == null || currentTime < nextPrayerTime)
    }.let { if (it == -1) 0 else it }
    
    // Calculate angle based on current actual time
    val currentHour = currentTime.hour
    val currentMinute = currentTime.minute
    val currentTimeInHours = currentHour + currentMinute / 60f
    val angle = (currentTimeInHours * 15f) - 90f // 360°/24h = 15° per hour, -90° to start at 12 o'clock
    
    val handSize = (watchSize.value * 0.3f).dp
    
    Box(
        modifier = Modifier
            .size(handSize)
            .rotate(angle)
    ) {
        // Clock hand shaft
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .offset(x = (handSize.value / 2 - 2f).dp)
                .background(
                    Color(0xFFFF6B6B), // Red color for current time
                    RoundedCornerShape(2.dp)
                )
        )
        
        // Clock hand head
        Box(
            modifier = Modifier
                .size(8.dp)
                .offset(
                    x = (handSize.value / 2 - 4f).dp,
                    y = 0.dp
                )
                .clip(CircleShape)
                .background(Color(0xFFFF6B6B))
        )
    }
}

@Composable
private fun QiblaArrow(qiblaDirection: Float, watchSize: Dp = 300.dp) {
    val animatedRotation by animateFloatAsState(
        targetValue = qiblaDirection,
        animationSpec = tween(500, easing = EaseOutCubic)
    )
    
    val arrowSize = (watchSize.value * 0.4f).dp // Scale arrow with watch size
    
    Box(
        modifier = Modifier
            .size(arrowSize)
            .rotate(animatedRotation)
    ) {
        // Arrow shaft (vertical, pointing upward)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .offset(x = (arrowSize.value / 2 - 1.5f).dp)
                .background(
                    Color(0xFFFFD700),
                    RoundedCornerShape(1.5.dp)
                )
        )
        
        // Arrow head (at the top)
        Box(
            modifier = Modifier
                .size(8.dp)
                .offset(
                    x = (arrowSize.value / 2 - 4f).dp,
                    y = 0.dp
                )
                .clip(CircleShape)
                .background(Color(0xFFFFD700))
        )
        
        // Arrow tail (at the bottom)
        Box(
            modifier = Modifier
                .size(6.dp)
                .offset(
                    x = (arrowSize.value / 2 - 3f).dp,
                    y = (arrowSize.value - 6f).dp
                )
                .clip(CircleShape)
                .background(Color(0xFFFFD700))
        )
    }
}

/**
 * Calculate Qibla direction using great circle formula
 */
private fun calculateQiblaDirection(
    lat1: Double, lon1: Double,
    lat2: Double, lon2: Double
): Double {
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val deltaLon = Math.toRadians(lon2 - lon1)
    
    val y = sin(deltaLon)
    val x = cos(lat1Rad) * tan(lat2Rad) - sin(lat1Rad) * cos(deltaLon)
    
    var qibla = Math.toDegrees(atan2(y, x))
    
    // Normalize to 0-360 degrees
    if (qibla < 0) qibla += 360.0
    
    return qibla
}

@Composable
private fun EnhancedTimeProgressRing(prayerTimes: DayPrayerTimes, watchSize: Dp) {
    val currentTime = LocalTime.now()
    val prayers = listOf(
        prayerTimes.fajr,
        prayerTimes.dhuhr,
        prayerTimes.asr,
        prayerTimes.maghrib,
        prayerTimes.isha
    )
    
    // Find which prayer time we're currently in
    val currentPrayerIndex = prayers.indexOfFirst { time ->
        val prayerTime = time
        val nextPrayerTime = prayers.getOrNull(prayers.indexOfFirst { it > time } + 1)
        currentTime >= prayerTime && (nextPrayerTime == null || currentTime < nextPrayerTime)
    }.let { if (it == -1) 0 else it }
    
    // Calculate progress within current prayer period
    val currentPrayerTime = prayers[currentPrayerIndex]
    val nextPrayerTime = prayers.getOrNull(currentPrayerIndex + 1) ?: prayers[0]
    
    val progress = if (nextPrayerTime > currentPrayerTime) {
        val totalDuration = Duration.between(currentPrayerTime, nextPrayerTime)
        val elapsed = Duration.between(currentPrayerTime, currentTime)
        (elapsed.toMinutes().toFloat() / totalDuration.toMinutes().toFloat()).coerceIn(0f, 1f)
    } else {
        // Handle midnight crossing
        val totalDuration = Duration.between(currentPrayerTime, nextPrayerTime.plusHours(24))
        val elapsed = Duration.between(currentPrayerTime, currentTime)
        (elapsed.toMinutes().toFloat() / totalDuration.toMinutes().toFloat()).coerceIn(0f, 1f)
    }
    
    Box(
        modifier = Modifier.size(watchSize)
    ) {
        // Background progress ring (subtle)
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = 120f
            
            // Draw background ring
            drawCircle(
                color = Color(0xFF374151).copy(alpha = 0.3f),
                center = center,
                radius = radius,
                style = Stroke(width = 12f, cap = StrokeCap.Round)
            )
        }
        
        // Enhanced progress arc with red bar
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            
            // Calculate start angle based on current prayer time
            val currentPrayerTime = prayers[currentPrayerIndex]
            val currentPrayerHour = currentPrayerTime.hour
            val currentPrayerMinute = currentPrayerTime.minute
            val currentPrayerTimeInHours = currentPrayerHour + currentPrayerTime.minute / 60f
            val startAngle = (currentPrayerTimeInHours * 15f - 90f)
            
            // Calculate sweep angle based on time difference to next prayer
            val nextPrayerTime = prayers.getOrNull(currentPrayerIndex + 1) ?: prayers[0]
            val nextPrayerHour = nextPrayerTime.hour
            val nextPrayerMinute = nextPrayerTime.minute
            val nextPrayerTimeInHours = nextPrayerHour + nextPrayerTime.minute / 60f
            
            val timeDifferenceInHours = if (nextPrayerTimeInHours > currentPrayerTimeInHours) {
                nextPrayerTimeInHours - currentPrayerTimeInHours
            } else {
                (24f - currentPrayerTimeInHours) + nextPrayerTimeInHours
            }
            
            val sweepAngle = timeDifferenceInHours * 15f * progress // Convert hours to degrees and apply progress
            
            // Draw enhanced progress arc with gradient
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFFF4444), // Bright red
                        Color(0xFFFF6B6B), // Medium red
                        Color(0xFFFF8E8E)  // Light red
                    ),
                    center = center
                ),
                startAngle = startAngle.toFloat(),
                sweepAngle = sweepAngle.toFloat(),
                useCenter = false,
                topLeft = Offset(center.x - 120f, center.y - 120f),
                size = Size(240f, 240f),
                style = Stroke(width = 12f, cap = StrokeCap.Round)
            )
            
            // Add glow effect
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFFF4444).copy(alpha = 0.3f),
                        Color(0xFFFF6B6B).copy(alpha = 0.2f)
                    ),
                    center = center
                ),
                startAngle = startAngle.toFloat(),
                sweepAngle = sweepAngle.toFloat(),
                useCenter = false,
                topLeft = Offset(center.x - 125f, center.y - 125f),
                size = Size(250f, 250f),
                style = Stroke(width = 20f, cap = StrokeCap.Round)
            )
        }
        
        // Progress percentage indicator
        Box(
            modifier = Modifier
                .offset(
                    x = (watchSize.value * 0.15f).dp,
                    y = (watchSize.value * 0.15f).dp
                )
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF4444).copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PremiumCenterDesign(watchSize: Dp) {
    Box(
        modifier = Modifier.size(watchSize),
        contentAlignment = Alignment.Center
    ) {
        // Real compass center with crosshair
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF374151))
        )
        
        // Crosshair lines
        Canvas(modifier = Modifier.size(40.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            
            // Horizontal line
            drawLine(
                color = Color.White,
                start = Offset(0f, center.y),
                end = Offset(size.width, center.y),
                strokeWidth = 2.dp.value
            )
            
            // Vertical line
            drawLine(
                color = Color.White,
                start = Offset(center.x, 0f),
                end = Offset(center.x, size.height),
                strokeWidth = 2.dp.value
            )
        }
        
        // Inner circle
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFF1F2937))
        )
        
        // Center dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun PremiumCurrentTimeInfo(prayerTimes: DayPrayerTimes, timeUntilNext: String?, watchSize: Dp) {
    val currentTime = LocalTime.now()
    val prayers = listOf(
        "Fajr" to prayerTimes.fajr,
        "Dhuhr" to prayerTimes.dhuhr,
        "Asr" to prayerTimes.asr,
        "Maghrib" to prayerTimes.maghrib,
        "Isha" to prayerTimes.isha
    )
    
    val currentPrayer = prayers.find { (_, time) ->
        val nextPrayer = prayers.find { it.second > time }
        currentTime >= time && (nextPrayer?.let { currentTime < it.second } ?: true)
    }?.first ?: "Fajr"
    
    Box(
        modifier = Modifier
            .offset(y = (watchSize.value * 0.25f).dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1E293B).copy(alpha = 0.95f),
                        Color(0xFF334155).copy(alpha = 0.9f)
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🕌 $currentPrayer",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFFFD700),
                fontWeight = FontWeight.Bold
            )
            if (timeUntilNext != null) {
                Text(
                    text = "Next in $timeUntilNext",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun FloatingPrayerLabels(prayerTimes: DayPrayerTimes, watchSize: Dp) {
    val prayers = listOf(
        "Fajr" to prayerTimes.fajr,
        "Dhuhr" to prayerTimes.dhuhr,
        "Asr" to prayerTimes.asr,
        "Maghrib" to prayerTimes.maghrib,
        "Isha" to prayerTimes.isha
    )
    
    val currentTime = LocalTime.now()
    
    prayers.forEach { (name, time) ->
        val hour = time.hour
        val minute = time.minute
        val timeInHours = hour + minute / 60f
        val angle = (timeInHours * 15f) - 90f
        val radius = (watchSize.value * 0.28f).dp
        val angleRad = Math.toRadians(angle.toDouble())
        
        val isActive = currentTime >= time && 
            prayers.find { it.second > time }?.let { currentTime < it.second } ?: true
        
        Box(
            modifier = Modifier
                .offset(
                    x = (cos(angleRad) * radius.value).dp,
                    y = (sin(angleRad) * radius.value).dp
                )
                .size(if (isActive) 70.dp else 50.dp),
            contentAlignment = Alignment.Center
        ) {
            // Floating label background
            Box(
                modifier = Modifier
                    .size(if (isActive) 70.dp else 50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isActive) {
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFF4444).copy(alpha = 0.9f),
                                    Color(0xFFFF6B6B).copy(alpha = 0.8f)
                                )
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF3B82F6).copy(alpha = 0.8f),
                                    Color(0xFF1E40AF).copy(alpha = 0.7f)
                                )
                            )
                        }
                    )
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    fontSize = if (isActive) 14.sp else 12.sp
                )
                Text(
                    text = time.format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp
                )
            }
        }
    }
}