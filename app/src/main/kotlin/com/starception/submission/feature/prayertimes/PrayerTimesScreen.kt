package com.starception.submission.feature.prayertimes

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer



import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus.Denied
import com.google.accompanist.permissions.rememberPermissionState
import com.starception.submission.feature.prayertimes.utils.getCurrentDate
import com.starception.submission.feature.prayertimes.utils.formatTime
import com.starception.submission.feature.prayertimes.data.PrayerTimesCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.Duration

/**
 * PRAYER TIMES SCREEN: Main UI for displaying Islamic prayer times with Material 3 design
 * 
 * This is the primary user interface for the prayer times feature, providing:
 * 
 * VISUAL DESIGN:
 * - Material 3 expressive design with asymmetrical shapes
 * - Real-time prayer status updates (Current/Next/Upcoming)
 * - Layered background with gradient effects
 * - Responsive layout for different screen sizes
 * 
 * FUNCTIONALITY:
 * - Live prayer time calculations with 3-second location timeout
 * - Smart permission handling (location and notifications)
 * - Real-time clock updates every minute
 * - Automatic refresh when permissions change
 * - Fallback to cached data or Dubai default
 * 
 * STATE MANAGEMENT:
 * - Uses Compose state for reactive UI updates
 * - Background calculation to prevent UI blocking
 * - Error handling with graceful fallbacks
 * 
 * PERMISSIONS:
 * - Requests location permission for accurate prayer times
 * - Requests notification permission for prayer alerts (Android 13+)
 * - Continues working without permissions using defaults
 * 
 * EDIT THIS TO:
 * - Change UI design and colors
 * - Modify permission request strategy
 * - Add new prayer time display formats
 * - Include additional Islamic features
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PrayerTimesScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    
    // UI STATE MANAGEMENT - These control what the user sees
    var prayerTimes by remember { mutableStateOf<com.starception.submission.prayer.model.DayPrayerTimes?>(null) }  // Calculated prayer times
    var isLoading by remember { mutableStateOf(true) }      // Loading indicator state
    var location by remember { mutableStateOf("Loading location...") }  // Location display text
    
    // REAL-TIME CLOCK STATE - Updates every minute for live prayer status
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    
    // PULL-TO-REFRESH STATE - Handle pull down to refresh location and prayer times
    var isRefreshing by remember { mutableStateOf(false) }
    var pullOffset by remember { mutableStateOf(0f) }
    var isPulling by remember { mutableStateOf(false) }
    
    // Smooth animation for pull offset
    val animatedPullOffset by animateFloatAsState(
        targetValue = pullOffset,
        animationSpec = tween(durationMillis = 200),
        label = "pullOffset"
    )
    
    // Apple-style swipe-up hint animation with multiple effects
    val infiniteTransition = rememberInfiniteTransition(label = "swipeHint")
    val swipeHintOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "swipeHintOffset"
    )
    
    // Scale effect for the hint text
    val swipeHintScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "swipeHintScale"
    )
    
    // Opacity effect for the hint text
    val swipeHintAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "swipeHintAlpha"
    )
    

    

    

    
    // Custom pull-to-refresh implementation
    fun onRefresh() {
        isRefreshing = true
        // Refresh location and prayer times
        // Note: calculatePrayerTimes is a suspend function, so we'll trigger it via LaunchedEffect
    }
    
    // PERMISSION MANAGEMENT - Handle user permissions gracefully
    // Notification permission for prayer alerts (Android 13+)
    val notificationPermissionState = rememberPermissionState(
        permission = Manifest.permission.POST_NOTIFICATIONS
    )
    
    // Location permission for accurate prayer times (or fallback to default)
    val locationPermissionState = rememberPermissionState(
        permission = Manifest.permission.ACCESS_FINE_LOCATION
    )
    
    // PERMISSION REQUEST STRATEGY - Request permissions politely on first screen load
    LaunchedEffect(Unit) {
        // STEP 1: Request location permission for accurate prayer times
        val locationStatus = locationPermissionState.status
        if (locationStatus is Denied && !locationStatus.shouldShowRationale) {
            locationPermissionState.launchPermissionRequest()
        }
        
        // STEP 2: Request notification permission for prayer alerts (Android 13+)
        // Only request if we're on Android 13+ where this permission is required
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val notificationStatus = notificationPermissionState.status
            if (notificationStatus is Denied && !notificationStatus.shouldShowRationale) {
                notificationPermissionState.launchPermissionRequest()
            }
        }
    }
    
    // LIVE CLOCK UPDATES - Updates current time every minute for real-time prayer status
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()      // Update current time
            kotlinx.coroutines.delay(60000)   // Wait 1 minute (60,000 milliseconds)
            // This enables real-time updates like "Next prayer in 15 minutes"
        }
    }
    
    // PRAYER TIMES CALCULATION ENGINE - Background calculation with 3-second location timeout
    suspend fun calculatePrayerTimes() {
        try {
            // Run calculation on background thread to prevent UI blocking
            withContext(Dispatchers.Default) {
                val calculator = PrayerTimesCalculator(context)
                // This uses our improved 3-second timeout system
                val result = calculator.calculateDefaultPrayerTimes()
                
                prayerTimes = result.first   // Calculated prayer times (or null if failed)
                location = result.second     // Location name for display
            }
        } catch (e: Exception) {
            // GRACEFUL ERROR HANDLING - Never crash, always show something useful
            // Keep current values if calculation fails
            if (prayerTimes == null) {
                // Set default location if we don't have any data at all
                location = "Dubai, UAE (Default)"
            }
            // Note: Prayer times remain null, which will show appropriate fallback UI
        }
    }
    
    // SMART LOADING STRATEGY - Recalculate when permissions change, show loading only when needed
    LaunchedEffect(locationPermissionState.status) {
        // SMART LOADING: Only show loading spinner if we don't have existing data to display
        // This prevents flickering when permissions change after data is already loaded
        if (prayerTimes == null) {
            isLoading = true  // Show loading spinner for first load
        }
        
        // Calculate prayer times with our improved 3-second timeout system
        calculatePrayerTimes()
        
        // Hide loading spinner after calculation completes (success or failure)
        isLoading = false
    }
    
    // Handle refresh when pull-to-refresh is triggered
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            try {
                calculatePrayerTimes()
            } catch (e: Exception) {
                // Handle any errors during refresh
            } finally {
                isRefreshing = false
            }
        }
    }
    
    // Get next prayer and current prayer
    fun getNextPrayer(): Pair<String, LocalTime>? {
        val times = prayerTimes ?: return null
        
        val prayers = listOf(
            "Fajr" to times.fajr,
            "Dhuhr" to times.dhuhr,
            "Asr" to times.asr,
            "Maghrib" to times.maghrib,
            "Isha" to times.isha
        )
        
        // Find next prayer today
        val nextPrayer = prayers.find { it.second.isAfter(currentTime) }
        return nextPrayer ?: prayers.first() // If no prayers left today, return Fajr (tomorrow)
    }
    
    fun getCurrentPrayer(): Pair<String, LocalTime>? {
        val times = prayerTimes ?: return null
        
        val prayers = listOf(
            "Fajr" to times.fajr,
            "Dhuhr" to times.dhuhr,
            "Asr" to times.asr,
            "Maghrib" to times.maghrib,
            "Isha" to times.isha
        )
        
        // Find current prayer (the one we're in the time window for)
        for (i in prayers.indices) {
            val prayer = prayers[i]
            val nextPrayer = if (i < prayers.size - 1) prayers[i + 1] else null
            
            if (nextPrayer != null) {
                if (currentTime.isAfter(prayer.second) && currentTime.isBefore(nextPrayer.second)) {
                    return prayer
                }
            } else {
                // For Isha, check if we're within 2 hours after it starts
                if (currentTime.isAfter(prayer.second) && currentTime.isBefore(prayer.second.plusHours(2))) {
                    return prayer
                }
            }
        }
        return null
    }
    
    // Calculate time until next prayer
    fun getTimeUntilNextPrayer(): String {
        val nextPrayer = getNextPrayer() ?: return "2:30 till Dhuhr"
        
        val duration = Duration.between(currentTime, nextPrayer.second)
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        
        return when {
            hours > 0 -> "${hours}:${String.format("%02d", minutes)} till ${nextPrayer.first}"
            minutes > 0 -> "${minutes}m till ${nextPrayer.first}"
            else -> "Now"
        }
    }
    
    // Get prayer status
    fun getPrayerStatus(prayerName: String): String {
        val nextPrayer = getNextPrayer()
        val currentPrayer = getCurrentPrayer()
        
        return when {
            currentPrayer?.first == prayerName -> "Current"
            nextPrayer?.first == prayerName -> "Next"
            else -> "Upcoming"
        }
    }
    
    // Get prayer time display
    fun getPrayerTimeDisplay(prayerName: String): String {
        val times = prayerTimes ?: return "00:00 AM"
        
        val time = when (prayerName) {
            "Fajr" -> times.fajr
            "Dhuhr" -> times.dhuhr
            "Asr" -> times.asr
            "Maghrib" -> times.maghrib
            "Isha" -> times.isha
            else -> times.fajr
        }
        
        return time.format(DateTimeFormatter.ofPattern("hh:mm a"))
    }
    
    // Get main message for the notification format (same as notification service)
    fun getMainMessage(prayerName: String): String {
        val times = prayerTimes ?: return "Live Updates Active"
        val now = currentTime
        
        val prayerTime = when (prayerName) {
            "Fajr" -> times.fajr
            "Dhuhr" -> times.dhuhr
            "Asr" -> times.asr
            "Maghrib" -> times.maghrib
            "Isha" -> times.isha
            else -> times.fajr
        }
        
        // Calculate elapsed time since prayer started
        val elapsedMinutes = Duration.between(prayerTime, now).toMinutes()
        
        // Use same logic as notification service
        return when {
            elapsedMinutes < 20 -> "Go to Mosque for $prayerName"
            elapsedMinutes < 60 -> "Best Time to Pray $prayerName"
            else -> "Make Time for $prayerName"
        }
    }

    // Get current status for the notification format (same as notification service)
    fun getCurrentStatus(prayerName: String): String {
        val times = prayerTimes ?: return "Live Updates Active"
        val now = currentTime
        
        val prayerTime = when (prayerName) {
            "Fajr" -> times.fajr
            "Dhuhr" -> times.dhuhr
            "Asr" -> times.asr
            "Maghrib" -> times.maghrib
            "Isha" -> times.isha
            else -> times.fajr
        }
        
        // Calculate elapsed time since prayer started
        val elapsedMinutes = Duration.between(prayerTime, now).toMinutes()
        
        // Use same format as notification service
        val elapsedText = when {
            elapsedMinutes == 0L -> "just started"
            elapsedMinutes == 1L -> "1 minute"
            elapsedMinutes < 60 -> "${elapsedMinutes} minutes"
            else -> {
                val hours = elapsedMinutes / 60
                val minutes = elapsedMinutes % 60
                when {
                    minutes == 0L -> "${hours}h"
                    else -> "${hours}h ${minutes}m"
                }
            }
        }
        
        return "$elapsedText since $prayerName"
    }

    // Get next prayer info for the notification format (same as notification service)
    fun getNextPrayerInfo(): String {
        val nextPrayer = getNextPrayer()
        return if (nextPrayer != null) {
            val timeRemaining = getTimeUntilNextPrayer()
            "Next • ${nextPrayer.first} in $timeRemaining"
        } else {
            "No upcoming prayers"
        }
    }
    

    
    // Full page pull-to-refresh - only top to bottom
    Box(
        modifier = modifier
            .fillMaxSize()
            .offset(y = animatedPullOffset.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // Start pull-to-refresh from anywhere on the full page
                        isPulling = true
                    },
                    onDragEnd = {
                        if (pullOffset > 30f && isPulling) {
                            onRefresh()
                        }
                        pullOffset = 0f
                        isPulling = false
                    },
                    onDrag = { change, _ ->
                        // Only activate when pulling down (top to bottom)
                        if (isPulling && change.position.y > 0) {
                            // Direct 1:1 mapping for immediate response
                            pullOffset = change.position.y.coerceAtMost(80f)
                        } else if (change.position.y <= 0) {
                            // Reset if dragging upward (bottom to top)
                            pullOffset = 0f
                            isPulling = false
                        }
                    }
                )
            }
    ) {

        
        // Refresh indicator when actually refreshing
        if (isRefreshing) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 120.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Updating location and prayer times...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        if (isLoading) {
            // Loading state with Material 3 design
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp),
                        strokeWidth = 4.dp
                    )
                    Text(
                        text = "Live Updates Active",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // Dynamic pull-to-refresh hint with enhanced Apple-style animation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = if (!isPulling && !isRefreshing) (swipeHintOffset - 8f).dp else (-8).dp)
                        .graphicsLayer(
                            scaleX = if (!isPulling && !isRefreshing) swipeHintScale else 1f,
                            scaleY = if (!isPulling && !isRefreshing) swipeHintScale else 1f,
                            alpha = if (!isPulling && !isRefreshing) swipeHintAlpha else 0.7f
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isPulling && pullOffset > 25f) Icons.Default.Refresh else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Pull to refresh",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = if (!isPulling && !isRefreshing) swipeHintAlpha else 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = if (isPulling && pullOffset > 25f) "Release to refresh" else "Pull down to refresh location",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (!isPulling && !isRefreshing) swipeHintAlpha else 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
                
                // Main prayer section - shows next prayer or current prayer with expressive shape
                val mainPrayer = getNextPrayer() ?: getCurrentPrayer()
                if (mainPrayer != null) {
                    // Layered background effect with expressive asymmetrical shape
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        // Background cream layer (peeking through) - positioned behind
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(x = 8.dp, y = 8.dp)
                                .zIndex(0f),
                            shape = RoundedCornerShape(
                                topStart = 32.dp,    // Large rounded corner
                                topEnd = 16.dp,      // Smaller rounded corner
                                bottomStart = 16.dp,  // Smaller rounded corner
                                bottomEnd = 32.dp     // Large rounded corner
                            ),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 4.dp
                        ) {}
                        
                        // Main prayer card with expressive asymmetrical shape - positioned on top
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(1f),
                            shape = RoundedCornerShape(
                                topStart = 40.dp,    // Very large rounded corner (organic)
                                topEnd = 20.dp,      // Smaller rounded corner
                                bottomStart = 20.dp,  // Smaller rounded corner
                                bottomEnd = 40.dp     // Very large rounded corner (organic)
                            ),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 12.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Left side - Prayer info
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Prayer name and status
                                    Column {
                                        Text(
                                            text = mainPrayer.first,
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = getPrayerStatus(mainPrayer.first),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    
                                    // Prayer time
                                    Text(
                                        text = getPrayerTimeDisplay(mainPrayer.first),
                                        style = MaterialTheme.typography.displaySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    // Additional info for Fajr
                                    if (mainPrayer.first == "Fajr") {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Sunrise: ${prayerTimes?.sunrise?.format(DateTimeFormatter.ofPattern("hh:mm a")) ?: "06:30 AM"}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                            Text(
                                                text = "Iqamah: ${prayerTimes?.fajr?.plusMinutes(20)?.format(DateTimeFormatter.ofPattern("hh:mm a")) ?: "05:35 AM"}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                }
                                
                                // Right side - Countdown only (status indicator removed)
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Countdown timer with Material 3 design
                                    Surface(
                                        modifier = Modifier.size(88.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shadowElevation = 6.dp
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            // Progress indicator
                                            CircularProgressIndicator(
                                                progress = { 0.7f },
                                                modifier = Modifier.size(80.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                strokeWidth = 4.dp
                                            )
                                            
                                            // Timer text
                                            Text(
                                                text = getTimeUntilNextPrayer(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Other prayer times using Material 3 design
                // First row: Dhuhr and Asr
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Dhuhr prayer
                    ElevatedCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = when (getPrayerStatus("Dhuhr")) {
                                "Current" -> MaterialTheme.colorScheme.tertiaryContainer
                                "Next" -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        elevation = CardDefaults.elevatedCardElevation(
                            defaultElevation = 4.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Dhuhr",
                                style = MaterialTheme.typography.titleLarge,
                                color = when (getPrayerStatus("Dhuhr")) {
                                    "Current" -> MaterialTheme.colorScheme.onTertiaryContainer
                                    "Next" -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = getPrayerTimeDisplay("Dhuhr"),
                                style = MaterialTheme.typography.headlineSmall,
                                color = when (getPrayerStatus("Dhuhr")) {
                                    "Current" -> MaterialTheme.colorScheme.tertiary
                                    "Next" -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Asr prayer
                    ElevatedCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = when (getPrayerStatus("Asr")) {
                                "Current" -> MaterialTheme.colorScheme.tertiaryContainer
                                "Next" -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        elevation = CardDefaults.elevatedCardElevation(
                            defaultElevation = 4.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Asr",
                                style = MaterialTheme.typography.titleLarge,
                                color = when (getPrayerStatus("Asr")) {
                                    "Current" -> MaterialTheme.colorScheme.onTertiaryContainer
                                    "Next" -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = getPrayerTimeDisplay("Asr"),
                                style = MaterialTheme.typography.headlineSmall,
                                color = when (getPrayerStatus("Asr")) {
                                    "Current" -> MaterialTheme.colorScheme.tertiary
                                    "Next" -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // Second row: Maghrib and Isha
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Maghrib prayer
                    ElevatedCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = when (getPrayerStatus("Maghrib")) {
                                "Current" -> MaterialTheme.colorScheme.tertiaryContainer
                                "Next" -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        elevation = CardDefaults.elevatedCardElevation(
                            defaultElevation = 4.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Maghrib",
                                style = MaterialTheme.typography.titleLarge,
                                color = when (getPrayerStatus("Maghrib")) {
                                    "Current" -> MaterialTheme.colorScheme.onTertiaryContainer
                                    "Next" -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = getPrayerTimeDisplay("Maghrib"),
                                style = MaterialTheme.typography.headlineSmall,
                                color = when (getPrayerStatus("Maghrib")) {
                                    "Current" -> MaterialTheme.colorScheme.tertiary
                                    "Next" -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Isha prayer
                    ElevatedCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = when (getPrayerStatus("Isha")) {
                                "Current" -> MaterialTheme.colorScheme.tertiaryContainer
                                "Next" -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        elevation = CardDefaults.elevatedCardElevation(
                            defaultElevation = 4.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Isha",
                                style = MaterialTheme.typography.titleLarge,
                                color = when (getPrayerStatus("Isha")) {
                                    "Current" -> MaterialTheme.colorScheme.onTertiaryContainer
                                    "Next" -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = getPrayerTimeDisplay("Isha"),
                                style = MaterialTheme.typography.headlineSmall,
                                color = when (getPrayerStatus("Isha")) {
                                    "Current" -> MaterialTheme.colorScheme.tertiary
                                    "Next" -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // Location info using Material 3 design
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = "Location",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}