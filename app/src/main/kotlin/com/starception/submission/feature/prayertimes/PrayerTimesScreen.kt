package com.starception.submission.feature.prayertimes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.PaddingValues



import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus.Denied
import com.google.accompanist.permissions.rememberPermissionState
import com.starception.submission.feature.prayertimes.utils.getCurrentDate
import com.starception.submission.feature.prayertimes.utils.formatTime
import com.starception.submission.feature.prayertimes.data.PrayerTimesCalculator
import com.starception.submission.feature.prayertimes.data.PrayerTimeCalculatorEntryPoint
import com.starception.submission.feature.prayertimes.animations.RefreshIndicator
import com.starception.submission.feature.prayertimes.animations.FlowingArrowsAnimation
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
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
/**
 * Main prayer times screen with comprehensive Islamic prayer time display
 * 
 * This composable creates a full-screen prayer times interface with pull-to-refresh,
 * real-time updates, location services, and permission management.
 * 
 * @param modifier Optional Modifier for customization
 * 
 * SCREEN COMPONENTS:
 * - Header: Shows current location and date
 * - Prayer Cards: Individual prayer times with status indicators
 * - Real-time Clock: Updates every minute to show current prayer status
 * - Pull-to-Refresh: Manual location/calculation refresh
 * - Permission Handlers: Location and notification permission requests
 * 
 * STATE MANAGEMENT:
 * - prayerTimes: Calculated Islamic prayer times for current location/date
 * - isLoading: Controls loading indicator visibility
 * - location: Human-readable location string for display
 * - currentTime: Live clock updated every minute
 * - Pull-to-refresh states: isRefreshing, pullOffset, isDragging
 * 
 * CALCULATION FLOW:
 * 1. Request location permission if needed
 * 2. Get GPS coordinates or use cached location
 * 3. Calculate prayer times using astronomical algorithms
 * 4. Display results with real-time status updates
 * 5. Handle errors gracefully with fallback to defaults
 * 
 * REFRESH BEHAVIOR:
 * - Automatic: Runs calculation on screen load and permission changes
 * - Manual: Pull-to-refresh gesture clears cache and recalculates
 * - Timeout: 3-second limit prevents infinite loading states
 * 
 * DEBUG MONITORING:
 * - Watch LaunchedEffect blocks for initialization and refresh logic
 * - Monitor permission state changes and their effects
 * - Check pullOffset values during gesture interactions
 * - Verify currentTime updates every minute
 * - Track calculation timeouts and error handling
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PrayerTimesScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    
    // UI STATE MANAGEMENT - These control what the user sees
    var prayerTimes by remember { mutableStateOf<com.starception.submission.prayer.model.DayPrayerTimes?>(null) }  // Calculated prayer times
    var isLoading by remember { mutableStateOf(false) }     // Start with no loading - only show for first-time users
    var location by remember { mutableStateOf("Loading location...") }  // Location display text
    
    // REAL-TIME CLOCK STATE - Updates every minute for live prayer status
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    
    // PULL-TO-REFRESH STATE - Simple implementation
    var isRefreshing by remember { mutableStateOf(false) }
    var pullOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    // LOCATION SERVICE PROMPT STATE
    var showLocationServiceDialog by remember { mutableStateOf(false) }
    var locationServiceCheckPending by remember { mutableStateOf(false) }
    
    // Smooth animation for pull offset
    val animatedPullOffset by animateFloatAsState(
        targetValue = pullOffset,
        animationSpec = tween(durationMillis = 100),
        label = "pullOffset"
    )

    
    // REFRESH LOGIC - Handle pull-to-refresh action with location service checking
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            try {
                // LOCATION SERVICE CHECK: Verify location services before proceeding
                android.util.Log.d("PullToRefresh", "=== STARTING PULL-TO-REFRESH DEBUG ===")
                android.util.Log.d("PullToRefresh", "User initiated prayer times refresh with location service validation")
                
                // Get location service to check if services are enabled
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    PrayerTimeCalculatorEntryPoint::class.java
                )
                val locationService = entryPoint.enhancedLocationService()
                
                val hasPermission = locationService.hasLocationPermission()
                val servicesEnabled = locationService.isLocationEnabled()
                
                android.util.Log.d("PullToRefresh", "Location permission granted: $hasPermission")
                android.util.Log.d("PullToRefresh", "Location services enabled: $servicesEnabled")
                
                // CHECK: If location services are not fully available, prompt user
                if (!hasPermission) {
                    android.util.Log.w("PullToRefresh", "⚠️  LOCATION PERMISSION NOT GRANTED!")
                    android.util.Log.w("PullToRefresh", "User has not granted location permission")
                    android.util.Log.w("PullToRefresh", "Will proceed with cached/default location but showing advisory")
                    
                    // Continue with cached/default but don't show dialog for permission (handled by permission UI)
                } else if (!servicesEnabled) {
                    android.util.Log.w("PullToRefresh", "⚠️  LOCATION SERVICES DISABLED!")
                    android.util.Log.w("PullToRefresh", "User has granted permission but turned off location services")
                    android.util.Log.w("PullToRefresh", "Showing dialog to prompt user to enable location services")
                    
                    // Stop refresh and show dialog
                    isRefreshing = false
                    isLoading = false
                    showLocationServiceDialog = true
                    return@LaunchedEffect
                } else {
                    android.util.Log.d("PullToRefresh", "✅ Location permission and services are both available")
                }
                
                // Step 1: Clear in-memory cache to force GPS location fetch and prayer calculation
                android.util.Log.d("PullToRefresh", "STEP 1: Clearing LocationCache to force fresh GPS and calculations...")
                try {
                    // Access the LocationCache service through Hilt dependency injection
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        PrayerTimeCalculatorEntryPoint::class.java
                    )
                    val cache = entryPoint.locationCache()
                    cache.clearCache()
                    android.util.Log.d("PullToRefresh", "✓ Cache cleared - next calculation will fetch fresh GPS location")
                } catch (e: Exception) {
                    android.util.Log.e("PullToRefresh", "❌ CRITICAL: Failed to clear cache: ${e.message}", e)
                    // Continue anyway - calculation may still work with cached data
                }
                
                // Set loading state
                isLoading = true
                
                // Calculate with 3-second timeout to prevent infinite loading
                android.util.Log.d("PullToRefresh", "STEP 2: Starting prayer time calculation with 3-second timeout...")
                val startTime = System.currentTimeMillis()
                try {
                    // Run calculation with timeout protection
                    android.util.Log.d("PullToRefresh", "TIMEOUT PROTECTION: Calculation has maximum 3000ms to complete")
                    withTimeout(3000L) { 
                        withContext(Dispatchers.Default) {
                            android.util.Log.d("PullToRefresh", "CALCULATION START: Creating PrayerTimesCalculator and running calculation")
                            val calculator = PrayerTimesCalculator(context)
                            val result = calculator.calculateDefaultPrayerTimes()
                            
                            android.util.Log.d("PullToRefresh", "CALCULATION RESULT: Prayer times = ${if (result.first != null) "SUCCESS" else "NULL"}")
                            android.util.Log.d("PullToRefresh", "CALCULATION RESULT: Location = \"${result.second}\"")
                            
                            prayerTimes = result.first   // Calculated prayer times (or null if failed)
                            location = result.second     // Location name for display
                        }
                    }
                    android.util.Log.d("PullToRefresh", "Calculation completed successfully in ${System.currentTimeMillis() - startTime}ms")
                } catch (e: TimeoutCancellationException) {
                    android.util.Log.w("PullToRefresh", "Calculation timed out after 3 seconds, keeping existing data")
                    // Keep current prayer times if they exist, or show default location
                    if (prayerTimes == null) {
                        location = "Location unavailable"
                    }
                }
            } catch (e: Exception) {
                // Handle any other errors gracefully
                android.util.Log.e("PullToRefresh", "Error during refresh: ${e.message}")
            } finally {
                // Always reset loading and refresh states after timeout
                isLoading = false
                isRefreshing = false
            }
        }
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
                
                // CRITICAL: Always turn off loading after calculation completes
                isLoading = false
            }
        } catch (e: Exception) {
            // GRACEFUL ERROR HANDLING - Never crash, always show something useful
            // Keep current values if calculation fails
            if (prayerTimes == null) {
                // Set default location if we don't have any data at all
                location = "Dubai, UAE (Default)"
            }
            // CRITICAL: Always turn off loading even if calculation fails
            isLoading = false
            // Note: Prayer times remain null, which will show appropriate fallback UI
        }
    }
    
    // INSTANT LOAD STRATEGY - Show cached data immediately, update in background
    LaunchedEffect(Unit) {
        android.util.Log.d("PrayerScreen", "=== INSTANT LOAD STRATEGY ===")
        
        // STEP 1: Try to load cached data instantly (no loading screen)
        android.util.Log.d("PrayerScreen", "STEP 1: Checking for instant cached data...")
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                PrayerTimeCalculatorEntryPoint::class.java
            )
            val cache = entryPoint.locationCache()
            
            // Check if we have cached prayer times for today
            val cachedData = cache.getCachedPrayerTimes()
            if (cachedData != null) {
                val (cachedPrayerTimes, cachedDate, cachedLocationName) = cachedData
                if (cachedPrayerTimes != null && cachedLocationName != null) {
                    android.util.Log.d("PrayerScreen", "✓ INSTANT LOAD: Found cached prayer times for today!")
                    android.util.Log.d("PrayerScreen", "  Location: $cachedLocationName")
                    android.util.Log.d("PrayerScreen", "  Date: $cachedDate")
                    
                    // Show cached data immediately - NO LOADING SCREEN!
                    prayerTimes = cachedPrayerTimes
                    location = cachedLocationName
                    isLoading = false
                    
                    android.util.Log.d("PrayerScreen", "✓ UI updated instantly with cached data")
                }
            } else {
                android.util.Log.d("PrayerScreen", "No cached data found - this is first time use")
                isLoading = true  // Only show loading for brand new users
            }
        } catch (e: Exception) {
            android.util.Log.w("PrayerScreen", "Failed to load cached data: ${e.message}")
            isLoading = true  // Show loading if cache access fails
        }
        
        // STEP 2: Update with fresh GPS data in background
        android.util.Log.d("PrayerScreen", "STEP 2: Starting background GPS update...")
        calculatePrayerTimes()
        android.util.Log.d("PrayerScreen", "Background update completed")
        
        // Note: calculatePrayerTimes() now handles turning off isLoading
    }
    
    // PERMISSION CHANGE HANDLER - Update data when permissions change
    LaunchedEffect(locationPermissionState.status) {
        android.util.Log.d("PrayerScreen", "Permission status changed, running background update...")
        // Update in background - calculatePrayerTimes() handles loading state
        calculatePrayerTimes()
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
    
    // Get smart information based on time of day and prayer status
    fun getSmartTitle(): String {
        val hour = currentTime.hour
        val currentPrayer = getCurrentPrayer()
        val nextPrayer = getNextPrayer()
        
        return when {
            hour in 5..11 -> "Morning Focus"
            hour in 12..17 -> "Afternoon Progress"
            hour in 18..22 -> "Evening Reflection"
            else -> "Night Preparation"
        }
    }
    
    fun getSmartContent(): String {
        val hour = currentTime.hour
        val currentPrayer = getCurrentPrayer()
        val nextPrayer = getNextPrayer()
        
        return when {
            hour in 5..11 -> "Start your day with intention and gratitude"
            hour in 12..17 -> "Keep Allah in your thoughts as you work"
            hour in 18..22 -> "Reflect on today's blessings and lessons"
            else -> "Prepare your heart for tomorrow's opportunities"
        }
    }
    
    fun getSmartFooter(): String {
        val currentPrayer = getCurrentPrayer()
        val nextPrayer = getNextPrayer()
        
        return when {
            currentPrayer != null -> "In ${currentPrayer.first} time"
            nextPrayer != null -> "Approaching ${nextPrayer.first}"
            else -> "Stay mindful"
        }
    }
    
    // Calculate daily prayer progress
    fun getPrayerProgress(): Pair<Int, Int> {
        val times = prayerTimes ?: return Pair(0, 5)
        val now = currentTime
        
        val prayers = listOf(
            "Fajr" to times.fajr,
            "Dhuhr" to times.dhuhr,
            "Asr" to times.asr,
            "Maghrib" to times.maghrib,
            "Isha" to times.isha
        )
        
        val completedCount = prayers.count { it.second.isBefore(now) }
        return Pair(completedCount, 5)
    }
    
    fun getDailyStatsTitle(): String {
        val (completed, total) = getPrayerProgress()
        return when {
            completed == total -> "Perfect Day!"
            completed >= 3 -> "Great Progress"
            completed >= 1 -> "Keep Going"
            else -> "New Day Begins"
        }
    }
    
    fun getDailyStatsMessage(): String {
        val (completed, total) = getPrayerProgress()
        val remaining = total - completed
        
        return when {
            completed == total -> "All prayers completed with devotion"
            remaining == 1 -> "1 prayer remaining today"
            remaining > 1 -> "$remaining prayers remaining today"
            else -> "Ready to begin the day with prayer"
        }
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
    

    
    // Simple pull-to-refresh implementation - simplified approach
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { 
                        isDragging = true
                    },
                    onDragEnd = {
                        if (pullOffset > 100f) {
                            isRefreshing = true
                        }
                        pullOffset = 0f
                        isDragging = false
                    },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0) {
                            val newOffset = (pullOffset + dragAmount * 0.5f).coerceAtMost(150f)
                            pullOffset = newOffset
                        } else {
                            val newOffset = (pullOffset + dragAmount).coerceAtLeast(0f)
                            pullOffset = newOffset
                        }
                    }
                )
            }
    ) {
        // PROFESSIONAL pull-to-refresh indicator with enhanced animations
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(10f),
            contentAlignment = Alignment.Center
        ) {
            // Show professional refresh indicator when pulling or refreshing
            RefreshIndicator(
                isRefreshing = isRefreshing,
                pullOffset = pullOffset
            )
            
            // Show elegant flowing arrow hint when not pulling
            if (!isRefreshing && pullOffset < 30f) {
                FlowingArrowsAnimation(
                    isPulling = isDragging,
                    pullOffset = pullOffset,
                    isRefreshing = isRefreshing
                )
            }
        }
        
        if (isLoading) {
            // Loading state with Material 3 design
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading prayer times...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            // REMOVE SCROLL - it conflicts with pull-to-refresh detection
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(
                        top = (80 + (animatedPullOffset * 0.5f)).dp, // Adjusted base padding to 80dp for optimal spacing
                        bottom = 24.dp // Increased from 16.dp to accommodate swipe hint
                    )
                    .offset(y = (animatedPullOffset * 0.2f).dp), // Reduced content movement to prevent overlap
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {


                
                // Swipeable Big Tiles - HorizontalPager with 3 tiles and infinite scroll
                val pagerState = rememberPagerState(
                    pageCount = { Int.MAX_VALUE }, // Enable infinite scrolling
                    initialPage = Int.MAX_VALUE / 2 // Start in the middle for smooth infinite scroll
                )
                
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    pageSpacing = 16.dp,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { page ->
                    val actualPage = page % 3 // Map infinite pages to our 3 actual tiles
                        when (actualPage) {
                        0 -> {
                            // Tile 1: Next Prayer Information
                            val mainPrayer = getNextPrayer() ?: getCurrentPrayer()
                            if (mainPrayer != null) {
                                // Single prayer card without layered background
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                        shape = RoundedCornerShape(
                                            topStart = 40.dp,
                                            topEnd = 20.dp,
                                            bottomStart = 20.dp,
                                            bottomEnd = 40.dp
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
                                            // Prayer info
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
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
                                                
                                                Text(
                                                    text = getPrayerTimeDisplay(mainPrayer.first),
                                                    style = MaterialTheme.typography.displaySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            
                                            // Countdown timer
                                            Column(
                                                horizontalAlignment = Alignment.End,
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Surface(
                                                    modifier = Modifier.size(88.dp),
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    shadowElevation = 6.dp
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        CircularProgressIndicator(
                                                            progress = { 0.7f },
                                                            modifier = Modifier.size(80.dp),
                                                            color = MaterialTheme.colorScheme.primary,
                                                            strokeWidth = 4.dp
                                                        )
                                                        
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
                            } else {
                                // Fallback if no prayer data
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize(),
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Loading prayer times...",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                        
                        1 -> {
                            // Tile 2: Smart Information - Dynamic content based on time and prayer status
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize(),
                                shape = RoundedCornerShape(
                                    topStart = 20.dp,
                                    topEnd = 40.dp,
                                    bottomStart = 40.dp,
                                    bottomEnd = 20.dp
                                ),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shadowElevation = 8.dp
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Dynamic title based on time of day
                                    Text(
                                        text = getSmartTitle(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                    
                                    // Contextual content and guidance
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = getSmartContent(),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            textAlign = TextAlign.Center
                                        )
                                        
                                        // Current date for context
                                        Text(
                                            text = getCurrentDate(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    
                                    // Prayer context footer
                                    Text(
                                        text = getSmartFooter(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        
                        2 -> {
                            // Tile 3: Daily Statistics - Dynamic progress tracking
                            val (completed, total) = getPrayerProgress()
                            val progress = if (total > 0) completed.toFloat() / total.toFloat() else 0f
                            
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize(),
                                shape = RoundedCornerShape(
                                    topStart = 32.dp,
                                    topEnd = 16.dp,
                                    bottomStart = 16.dp,
                                    bottomEnd = 32.dp
                                ),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shadowElevation = 8.dp
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Dynamic title based on progress
                                    Text(
                                        text = getDailyStatsTitle(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                    
                                    // Progress visualization and stats
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Prayer completion progress
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "$completed/$total",
                                                style = MaterialTheme.typography.displaySmall,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "prayers",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                        
                                        // Progress bar
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = MaterialTheme.colorScheme.tertiary,
                                            trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)
                                        )
                                    }
                                    
                                    // Contextual message
                                    Text(
                                        text = getDailyStatsMessage(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Page indicators for swipeable tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        val isSelected = (pagerState.currentPage % 3) == index
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                        )
                        if (index < 2) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }
                
                // Professional swipe hint
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Swipe left",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Swipe for more insights",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Swipe right",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
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
    
    // NATIVE-STYLE LOCATION SERVICE DIALOG
    if (showLocationServiceDialog) {
        AlertDialog(
            onDismissRequest = { showLocationServiceDialog = false },
            title = {
                Text(
                    text = "Enable Location Services?",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Prayer Times needs location access to calculate accurate prayer times for your area.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Your location will be used to determine prayer times",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLocationServiceDialog = false
                        // Open device location settings
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "ENABLE",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showLocationServiceDialog = false
                        // Continue with cached/default location
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        text = "NOT NOW",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp)
        )
    }
}