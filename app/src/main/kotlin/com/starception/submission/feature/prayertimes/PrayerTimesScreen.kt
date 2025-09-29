/**
 * Prayer Times Screen - Main UI for Islamic Prayer Times Application
 * 
 * This file contains the complete prayer times interface implementation with:
 * - Real-time prayer time calculations
 * - Location-based services
 * - Qibla direction compass
 * - Interactive prayer tiles
 * - Permission management
 * - Material 3 design system
 * 
 * ## Architecture:
 * - **MVVM Pattern**: Uses ViewModels for state management
 * - **Compose UI**: Modern declarative UI with Material 3
 * - **Dependency Injection**: Hilt for dependency management
 * - **Permissions**: Location permissions with graceful degradation
 * - **Services**: Background location and prayer calculation services
 * 
 * ## Key Components:
 * - `PrayerTimesScreen`: Main screen composable with permission handling
 * - `SwipeableBigTiles`: Interactive prayer time cards with smooth animations
 * - `CompassProgressIndicator`: Enhanced Qibla direction compass
 * - Location services integration for accurate prayer time calculation
 * 
 * @author Prayer Times Development Team
 * @version 2.0 - Enhanced with Material 3 and improved UX
 */
package com.starception.submission.feature.prayertimes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import android.util.Log
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll



import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus.Denied
import com.google.accompanist.permissions.rememberPermissionState
import com.starception.submission.feature.prayertimes.utils.getCurrentDate
import com.starception.submission.feature.prayertimes.utils.formatTime
import com.starception.submission.feature.prayertimes.data.PrayerTimesCalculator
import com.starception.submission.feature.prayertimes.data.PrayerTimeCalculatorEntryPoint
import com.starception.submission.feature.prayertimes.animations.RefreshIndicator
import com.starception.submission.feature.prayertimes.animations.FlowingArrowsAnimation
import com.starception.submission.feature.prayertimes.SwipeableBigTiles
import com.starception.submission.feature.prayertimes.SmartContentUtils
import com.starception.submission.feature.prayertimes.PrayerTimeHelpers
import com.starception.submission.feature.prayertimes.components.CompassPopupScreen
import com.starception.submission.feature.prayertimes.getPrayerNameInLocalLanguage
import dagger.hilt.android.EntryPointAccessors
import com.starception.submission.prayer.service.CountryCodeMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime
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
    
    // COMPREHENSIVE UI LOGGING SYSTEM
    LaunchedEffect(Unit) {
        android.util.Log.i("PrayerTimesScreen", "")
        android.util.Log.i("PrayerTimesScreen", "🖼️ PRAYER TIMES SCREEN COMPOSITION")
        android.util.Log.i("PrayerTimesScreen", "=".repeat(60))
        android.util.Log.i("PrayerTimesScreen", "🚀 Screen initialization started")
        android.util.Log.i("PrayerTimesScreen", "⏰ Timestamp: ${LocalDateTime.now()}")
        android.util.Log.i("PrayerTimesScreen", "📱 Context: ${context.javaClass.simpleName}")
        android.util.Log.i("PrayerTimesScreen", "")
    }
    
    // SHARED STATE - Only one tile can be in edit mode at a time
    var currentEditingTile by remember { mutableStateOf<String?>(null) }
    
    // Track tile editing state changes
    LaunchedEffect(currentEditingTile) {
        android.util.Log.i("PrayerTimesScreen", "🔄 TILE EDITING STATE CHANGED")
        android.util.Log.i("PrayerTimesScreen", "  📝 Currently editing: ${currentEditingTile ?: "None"}")
        android.util.Log.i("PrayerTimesScreen", "  🔒 Other tiles locked: ${currentEditingTile != null}")
    }
    
    // TODO: Load actual prayer settings - for now use defaults to test functionality
    val prayerSettings = null
    
    // UI STATE MANAGEMENT - These control what the user sees
    // Try to load cached data immediately, with Dubai fallback for instant startup
    val (initialPrayerTimes, initialLocation, initialLoading) = remember {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                PrayerTimeCalculatorEntryPoint::class.java
            )
            val cache = entryPoint.locationCache()
            val cachedData = cache.getCachedPrayerTimes()
            
            if (cachedData != null) {
                val (cachedPrayerTimes, _, cachedLocationName) = cachedData
                if (cachedPrayerTimes != null && cachedLocationName != null) {
                    android.util.Log.d("PrayerScreen", "🚀 INSTANT STARTUP: Loaded cached data immediately!")
                    Triple(cachedPrayerTimes, cachedLocationName, false)
                } else {
                    // Provide Dubai fallback for instant startup
                    val dubaiLocation = com.starception.submission.prayer.model.Location(
                        latitude = 25.2048,
                        longitude = 55.2708,
                        timeZoneOffset = 4.0,
                        city = "Dubai",
                        country = "UAE"
                    )
                    val defaultTimes = com.starception.submission.prayer.model.DayPrayerTimes(
                        date = LocalDateTime.now(),
                        fajr = LocalTime.of(5, 15),
                        sunrise = LocalTime.of(6, 45),
                        dhuhr = LocalTime.of(12, 15),
                        asr = LocalTime.of(15, 45),
                        maghrib = LocalTime.of(18, 30),
                        isha = LocalTime.of(19, 45),
                        location = dubaiLocation
                    )
                    android.util.Log.d("PrayerScreen", "🕌 Using Dubai default prayer times for instant startup")
                    Triple(defaultTimes, "Dubai (Default)", false)
                }
            } else {
                // Provide Dubai fallback for instant startup
                val dubaiLocation = com.starception.submission.prayer.model.Location(
                    latitude = 25.2048,
                    longitude = 55.2708,
                    timeZoneOffset = 4.0,
                    city = "Dubai",
                    country = "UAE"
                )
                val defaultTimes = com.starception.submission.prayer.model.DayPrayerTimes(
                    date = LocalDateTime.now(),
                    fajr = LocalTime.of(5, 15),
                    sunrise = LocalTime.of(6, 45),
                    dhuhr = LocalTime.of(12, 15),
                    asr = LocalTime.of(15, 45),
                    maghrib = LocalTime.of(18, 30),
                    isha = LocalTime.of(19, 45),
                    location = dubaiLocation
                )
                android.util.Log.d("PrayerScreen", "🕌 Using Dubai default prayer times for first-time startup")
                Triple(defaultTimes, "Dubai (Default)", false)
            }
        } catch (e: Exception) {
            android.util.Log.w("PrayerScreen", "Failed to load cache, using Dubai fallback: ${e.message}")
            // Always provide fallback data - never show loading
            val dubaiLocation = com.starception.submission.prayer.model.Location(
                latitude = 25.2048,
                longitude = 55.2708,
                timeZoneOffset = 4.0,
                city = "Dubai",
                country = "UAE"
            )
            val defaultTimes = com.starception.submission.prayer.model.DayPrayerTimes(
                date = LocalDateTime.now(),
                fajr = LocalTime.of(5, 15),
                sunrise = LocalTime.of(6, 45),
                dhuhr = LocalTime.of(12, 15),
                asr = LocalTime.of(15, 45),
                maghrib = LocalTime.of(18, 30),
                isha = LocalTime.of(19, 45),
                location = dubaiLocation
            )
            Triple(defaultTimes, "Dubai (Default)", false)
        }
    }
    
    var prayerTimes by remember { mutableStateOf<com.starception.submission.prayer.model.DayPrayerTimes?>(initialPrayerTimes) }  // Calculated prayer times
    var isLoading by remember { mutableStateOf(initialLoading) }     // Start with no loading if we have cached data
    var location by remember { mutableStateOf(initialLocation) }  // Location display text
    
    // COMPREHENSIVE DATA STATE LOGGING
    LaunchedEffect(prayerTimes) {
        android.util.Log.i("PrayerTimesScreen", "🕌 PRAYER TIMES DATA STATE CHANGED")
        if (prayerTimes != null) {
            android.util.Log.i("PrayerTimesScreen", "  ✅ Prayer times available")
            android.util.Log.i("PrayerTimesScreen", "  📅 Date: ${prayerTimes!!.date.toLocalDate()}")
            android.util.Log.i("PrayerTimesScreen", "  📍 Location: ${prayerTimes!!.location.getDisplayName()}")
            android.util.Log.i("PrayerTimesScreen", "  🌄 Fajr: ${prayerTimes!!.fajr}")
            android.util.Log.i("PrayerTimesScreen", "  🌅 Sunrise: ${prayerTimes!!.sunrise}")
            android.util.Log.i("PrayerTimesScreen", "  ☀️ Dhuhr: ${prayerTimes!!.dhuhr}")
            android.util.Log.i("PrayerTimesScreen", "  🌇 Asr: ${prayerTimes!!.asr}")
            android.util.Log.i("PrayerTimesScreen", "  🌆 Maghrib: ${prayerTimes!!.maghrib}")
            android.util.Log.i("PrayerTimesScreen", "  🌙 Isha: ${prayerTimes!!.isha}")
        } else {
            android.util.Log.w("PrayerTimesScreen", "  ❌ No prayer times available")
        }
    }
    
    LaunchedEffect(isLoading) {
        android.util.Log.i("PrayerTimesScreen", "⏳ LOADING STATE CHANGED: ${if (isLoading) "LOADING" else "IDLE"}")
    }
    
    LaunchedEffect(location) {
        android.util.Log.i("PrayerTimesScreen", "📍 LOCATION DISPLAY CHANGED: $location")
    }
    
    // REAL-TIME CLOCK STATE - Updates every minute for live prayer status
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    
    // Track current time updates for prayer status calculations
    LaunchedEffect(currentTime) {
        android.util.Log.d("PrayerTimesScreen", "⏰ CURRENT TIME UPDATED: $currentTime")
        prayerTimes?.let { times ->
            val nextPrayer = times.getNextPrayer()
            android.util.Log.d("PrayerTimesScreen", "  🔔 Next prayer: ${nextPrayer?.name ?: "None today"}")
            android.util.Log.d("PrayerTimesScreen", "  ⏱️ Time until next: ${nextPrayer?.let { 
                val duration = Duration.between(currentTime, it.time)
                "${duration.toHours()}h ${duration.toMinutes() % 60}m"
            } ?: "N/A"}")
        }
    }
    
    // PULL-TO-REFRESH STATE - Simple implementation
    var isRefreshing by remember { mutableStateOf(false) }
    var pullOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    // Track refresh state changes
    LaunchedEffect(isRefreshing) {
        android.util.Log.i("PrayerTimesScreen", "🔄 REFRESH STATE CHANGED: ${if (isRefreshing) "REFRESHING" else "IDLE"}")
    }
    
    // LOCATION SERVICE PROMPT STATE
    var showLocationServiceDialog by remember { mutableStateOf(false) }
    var locationServiceCheckPending by remember { mutableStateOf(false) }
    
    // COMPASS POPUP STATE - Shows large compass with calibration guidance
    var showCompassPopup by remember { mutableStateOf(false) }
    
    // INTERACTIVE PRAYER DIAL POPUP STATE
    var showPrayerDialPopup by remember { mutableStateOf(false) }
    var popupPrayerName by remember { mutableStateOf<String?>(null) }
    
    val hapticFeedback = LocalHapticFeedback.current
    
    // LOAD STORED PRAYER OFFSETS
    var storedOffsets by remember { mutableStateOf(com.starception.submission.prayer.model.PrayerTimeOffsets()) }
    var offsetRefreshTrigger by remember { mutableStateOf(0) }
    
    // Function to refresh offsets from storage
    suspend fun refreshStoredOffsets() {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.starception.submission.feature.prayertimes.data.PrayerTimeCalculatorEntryPoint::class.java
            )
            val repository = entryPoint.prayerSettingsRepository()
            val currentSettings = repository.getLoadedCalculationSettings()
            storedOffsets = currentSettings.timeOffsets
            android.util.Log.d("PrayerTimesScreen", "🔄 REFRESHED STORED OFFSETS:")
            android.util.Log.d("PrayerTimesScreen", "   🌞 Dhuhr: ${storedOffsets.dhuhr}")
            android.util.Log.d("PrayerTimesScreen", "   🌇 Asr: ${storedOffsets.asr}")
            android.util.Log.d("PrayerTimesScreen", "   🌆 Maghrib: ${storedOffsets.maghrib}")
            android.util.Log.d("PrayerTimesScreen", "   🌙 Isha: ${storedOffsets.isha}")
        } catch (e: Exception) {
            android.util.Log.e("PrayerTimesScreen", "❌ Failed to refresh stored offsets", e)
        }
    }
    
    // Also refresh when screen is resumed in case offsets were changed in Prayer Settings
    LaunchedEffect(Unit) {
        refreshStoredOffsets()
    }
    
    // Load offsets once when screen initializes and when refresh trigger changes
    LaunchedEffect(offsetRefreshTrigger) {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.starception.submission.feature.prayertimes.data.PrayerTimeCalculatorEntryPoint::class.java
            )
            val repository = entryPoint.prayerSettingsRepository()
            val currentSettings = repository.getLoadedCalculationSettings()
            storedOffsets = currentSettings.timeOffsets
            android.util.Log.d("PrayerTimesScreen", "📥 LOADED STORED OFFSETS:")
            android.util.Log.d("PrayerTimesScreen", "   📄 Repository Source: ${repository.javaClass.simpleName}")
            android.util.Log.d("PrayerTimesScreen", "   🔍 Raw Offsets Object: $storedOffsets")
            android.util.Log.d("PrayerTimesScreen", "   🌅 Fajr: ${storedOffsets.fajr}")
            android.util.Log.d("PrayerTimesScreen", "   🌄 Sunrise: ${storedOffsets.sunrise}")
            android.util.Log.d("PrayerTimesScreen", "   🌞 Dhuhr: ${storedOffsets.dhuhr}")
            android.util.Log.d("PrayerTimesScreen", "   🌇 Asr: ${storedOffsets.asr}")
            android.util.Log.d("PrayerTimesScreen", "   🌆 Maghrib: ${storedOffsets.maghrib}")
            android.util.Log.d("PrayerTimesScreen", "   🌙 Isha: ${storedOffsets.isha}")
            android.util.Log.d("PrayerTimesScreen", "   📊 Total non-zero offsets: ${listOf(storedOffsets.fajr, storedOffsets.sunrise, storedOffsets.dhuhr, storedOffsets.asr, storedOffsets.maghrib, storedOffsets.isha).count { it != 0 }}")
        } catch (e: Exception) {
            android.util.Log.e("PrayerTimesScreen", "❌ Failed to load stored offsets", e)
        }
    }
    
    // LOCATION SERVICE - For Qibla compass functionality
    val locationService = remember {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            PrayerTimeCalculatorEntryPoint::class.java
        )
        entryPoint.enhancedLocationService()
    }
    
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
    
    // COMPREHENSIVE PERMISSION STATE LOGGING
    LaunchedEffect(notificationPermissionState.status) {
        android.util.Log.i("PrayerTimesScreen", "🔔 NOTIFICATION PERMISSION STATE CHANGED")
        when (notificationPermissionState.status) {
            is com.google.accompanist.permissions.PermissionStatus.Granted -> {
                android.util.Log.i("PrayerTimesScreen", "  ✅ Notification permission: GRANTED")
                android.util.Log.i("PrayerTimesScreen", "  🔔 Prayer alerts will be shown")
            }
            is com.google.accompanist.permissions.PermissionStatus.Denied -> {
                val denied = notificationPermissionState.status as com.google.accompanist.permissions.PermissionStatus.Denied
                android.util.Log.w("PrayerTimesScreen", "  ❌ Notification permission: DENIED")
                android.util.Log.w("PrayerTimesScreen", "  🚫 Should show rationale: ${denied.shouldShowRationale}")
                android.util.Log.w("PrayerTimesScreen", "  📵 Prayer alerts will not be shown")
            }
        }
    }
    
    LaunchedEffect(locationPermissionState.status) {
        android.util.Log.i("PrayerTimesScreen", "📍 LOCATION PERMISSION STATE CHANGED")
        when (locationPermissionState.status) {
            is com.google.accompanist.permissions.PermissionStatus.Granted -> {
                android.util.Log.i("PrayerTimesScreen", "  ✅ Location permission: GRANTED")
                android.util.Log.i("PrayerTimesScreen", "  🎯 Precise prayer times available")
            }
            is com.google.accompanist.permissions.PermissionStatus.Denied -> {
                val denied = locationPermissionState.status as com.google.accompanist.permissions.PermissionStatus.Denied
                android.util.Log.w("PrayerTimesScreen", "  ❌ Location permission: DENIED")
                android.util.Log.w("PrayerTimesScreen", "  🚫 Should show rationale: ${denied.shouldShowRationale}")
                android.util.Log.w("PrayerTimesScreen", "  🏠 Will use default/cached location")
            }
        }
    }
    
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
    
    // INTERACTIVE PRAYER CARD HELPER - Creates prayer card with long-press dial functionality
    @Composable
    fun InteractivePrayerCard(
        prayerName: String,
        currentEditingTile: String?,
        onEditingTileChange: (String?) -> Unit,
        currentOffset: Int = 0,
        modifier: Modifier = Modifier,
        onShowPopup: (String) -> Unit = {}
    ) {
        // Check if this specific card is in edit mode
        val isInEditMode = currentEditingTile == prayerName
        val isAnotherTileInEditMode = currentEditingTile != null && currentEditingTile != prayerName
        
        // Animation states
        val animationSpec = tween<Float>(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        )
        
        val sizeAnimationSpec = tween<IntSize>(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        )
        
        // Scale animation: shrink other tiles when one is in edit mode
        val scale by animateFloatAsState(
            targetValue = when {
                isInEditMode -> 1f // Keep normal size when this tile is in edit mode
                isAnotherTileInEditMode -> 0.85f // Shrink when another tile is in edit mode
                else -> 1f // Normal size when no tile is in edit mode
            },
            animationSpec = animationSpec,
            label = "TileScale"
        )
        
        // Transform animation for tile to dial transition
        val alpha by animateFloatAsState(
            targetValue = 1f,
            animationSpec = animationSpec,
            label = "TileAlpha"
        )
        
        // No rotation animation - keep tiles stationary during transformation
        
        // Pop effect animation - slight scale up then down for transformation
        val transformScale by animateFloatAsState(
            targetValue = if (isInEditMode) 1.05f else 1f, // Slight scale up when transforming
            animationSpec = tween(
                durationMillis = 250,
                easing = FastOutSlowInEasing
            ),
            label = "TransformScale"
        )
        
        // Debug logging
        android.util.Log.d("PrayerCard", "🔄 Rendering InteractivePrayerCard for $prayerName, isInEditMode=$isInEditMode, scale=$scale")
        
        if (isInEditMode) {
            // Show ONLY the circular dial - complete transformation, no extra UI
            var timeAdjustment by remember { mutableStateOf(currentOffset) }
            
            // Use Box to constrain the circular dial to the original tile space
            Box(
                modifier = modifier
                    .aspectRatio(1f) // Force square container for perfect circle
                    .graphicsLayer(
                        scaleX = scale * transformScale,
                        scaleY = scale * transformScale,
                        alpha = alpha
                    )
                    .animateContentSize(animationSpec = sizeAnimationSpec),
                contentAlignment = Alignment.Center
            ) {
                ElevatedCard(
                    shape = CircleShape, // Make the card circular for the dial
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 4.dp,
                        focusedElevation = 4.dp
                    ),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = when (PrayerTimeHelpers.getPrayerStatus(prayerName, currentTime, prayerTimes)) {
                            "Current" -> MaterialTheme.colorScheme.tertiaryContainer
                            "Next" -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    modifier = Modifier
                        .fillMaxSize() // Fill container space
                    .pointerInput(prayerName) {
                        detectTapGestures(
                            onLongPress = {
                                // Exit edit mode on long press without saving
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                // Exit edit mode by clearing the current editing tile
                                onEditingTileChange(null)
                                android.util.Log.d("PrayerTimes", "🚪 Exited edit mode via long press without saving")
                            }
                        )
                    }
            ) {
                // ONLY show the circular dial - complete transformation with no overlapping content
                com.starception.submission.feature.prayertimes.components.InteractivePrayerDial(
                    prayerName = prayerName,
                    originalTime = when (prayerName) {
                        "Dhuhr" -> prayerTimes?.dhuhr ?: LocalTime.of(12, 0)
                        "Asr" -> prayerTimes?.asr ?: LocalTime.of(15, 46)
                        "Maghrib" -> prayerTimes?.maghrib ?: LocalTime.of(18, 25)
                        "Isha" -> prayerTimes?.isha ?: LocalTime.of(19, 55)
                        else -> LocalTime.of(12, 0)
                    },
                    timeAdjustment = timeAdjustment,
                    onTimeAdjusted = { adjustment ->
                        timeAdjustment = adjustment
                    },
                    onSaveAdjustment = { prayerName, finalAdjustment ->
                        android.util.Log.d("PrayerTimesScreen", "🎯 INTERACTIVE DIAL SAVE:")
                        android.util.Log.d("PrayerTimesScreen", "   📝 Prayer: $prayerName")
                        android.util.Log.d("PrayerTimesScreen", "   ⏱️ Final Adjustment: $finalAdjustment minutes")
                        android.util.Log.d("PrayerTimesScreen", "   💾 Saving to prayer settings...")
                        
                        // Save the adjustment to Prayer settings using repository
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val repository = com.starception.submission.prayer.repository.PrayerSettingsRepository(context)
                                repository.updateSinglePrayerOffset(prayerName, finalAdjustment)
                                android.util.Log.i("PrayerTimesScreen", "✅ SAVE SUCCESS: $prayerName offset saved as $finalAdjustment minutes")
                                
                                // Update UI on main thread
                                withContext(Dispatchers.Main) {
                                    // Trigger refresh to reload stored offsets from storage
                                    offsetRefreshTrigger++
                                    android.util.Log.d("PrayerTimesScreen", "🔄 TRIGGERING OFFSET REFRESH: trigger=$offsetRefreshTrigger")
                                    
                                    // Exit edit mode after successful saving
                                    onEditingTileChange(null)
                                    android.util.Log.d("PrayerTimesScreen", "🚪 Exited edit mode - returning to tile view")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("PrayerTimesScreen", "❌ SAVE FAILED: Error saving $prayerName offset", e)
                                // Still exit edit mode even if save failed
                                withContext(Dispatchers.Main) {
                                    onEditingTileChange(null)
                                }
                            }
                        }
                    },
                    onResetAdjustment = {
                        android.util.Log.d("PrayerTimesScreen", "🔄 INTERACTIVE DIAL RESET for $prayerName")
                        timeAdjustment = 0
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            }
        } else {
            // Show regular small card with long-press detection
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 4.dp,
                    focusedElevation = 3.dp
                ),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = when (PrayerTimeHelpers.getPrayerStatus(prayerName, currentTime, prayerTimes)) {
                        "Current" -> MaterialTheme.colorScheme.tertiaryContainer
                        "Next" -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                modifier = modifier
                    .graphicsLayer(
                        scaleX = scale * transformScale,
                        scaleY = scale * transformScale,
                        alpha = alpha
                    )
                    .animateContentSize(animationSpec = sizeAnimationSpec)
                    .pointerInput(prayerName) {
                        detectTapGestures(
                            onLongPress = {
                                android.util.Log.d("PrayerCard", "🔥 LONG PRESS detected on $prayerName card!")
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                // Show popup instead of in-place editing
                                onShowPopup(prayerName)
                                android.util.Log.d("PrayerCard", "✅ Showing popup for $prayerName")
                            },
                            onTap = {
                                android.util.Log.d("PrayerCard", "👆 Regular tap detected on $prayerName card")
                            }
                        )
                    }
            ) {
                Box {
                    Column(
                        modifier = Modifier
                            .wrapContentHeight()
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 8.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Text(
                            text = prayerName,
                            style = MaterialTheme.typography.titleLarge,
                            color = when (PrayerTimeHelpers.getPrayerStatus(prayerName, currentTime, prayerTimes)) {
                                "Current" -> MaterialTheme.colorScheme.onTertiaryContainer
                                "Next" -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Medium,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                        Text(
                            text = getPrayerNameInLocalLanguage(prayerName, prayerTimes?.location?.countryCode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = when (PrayerTimeHelpers.getPrayerStatus(prayerName, currentTime, prayerTimes)) {
                                "Current" -> MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                "Next" -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            },
                            fontWeight = FontWeight.Normal,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                    
                    // Minimal gap between Arabic prayer name and time
                    Spacer(modifier = Modifier.height(0.dp))
                    
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                val baseColor = when (PrayerTimeHelpers.getPrayerStatus(prayerName, currentTime, prayerTimes)) {
                                    "Current" -> MaterialTheme.colorScheme.tertiary
                                    "Next" -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                
                                // Main prayer time in bold
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                                    append(PrayerTimeHelpers.getPrayerTimeDisplay(prayerName, prayerTimes))
                                }
                                
                                // Offset indicator in smaller, lighter style
                                if (currentOffset != 0) {
                                    append(" ")
                                    withStyle(style = SpanStyle(
                                        fontWeight = FontWeight.Medium,
                                        color = baseColor.copy(alpha = 0.7f),
                                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                                    )) {
                                        append(if (currentOffset > 0) "+${currentOffset}m" else "${currentOffset}m")
                                    }
                                }
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                }
                
            }
            }
        }
    }

    // PRAYER TIMES CALCULATION ENGINE - Background calculation with 3-second location timeout
    val calculatePrayerTimes: suspend () -> Unit = {
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
                // Set fallback location immediately so it's visible
                location = "Dubai (Default)"
                isLoading = true  // Only show loading for brand new users
            }
        } catch (e: Exception) {
            android.util.Log.w("PrayerScreen", "Failed to load cached data: ${e.message}")
            // Set fallback location immediately so it's visible
            location = "Dubai (Default)"
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
        // Pull-to-refresh indicators temporarily hidden to remove gaps
        /*Box(
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
            
            // Arrow animation temporarily hidden
            if (!isRefreshing && pullOffset < 30f) {
                FlowingArrowsAnimation(
                    isPulling = isDragging,
                    pullOffset = pullOffset,
                    isRefreshing = isRefreshing
                )
            }
        }*/
        
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(
                        top = 8.dp, // Further reduced for closer gap to Home header
                        bottom = 24.dp // Provide room for elevation shadows near screen bottom
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {


                
                // Swipeable Big Tiles - Using extracted component with Qibla compass
                SwipeableBigTiles(
                    prayerTimes = prayerTimes,
                    currentTime = currentTime,
                    locationService = locationService,
                    getNextPrayer = { PrayerTimeHelpers.getNextPrayer(currentTime, prayerTimes) },
                    getCurrentPrayer = { PrayerTimeHelpers.getCurrentPrayer(currentTime, prayerTimes) },
                    getPrayerStatus = { prayerName -> PrayerTimeHelpers.getPrayerStatus(prayerName, currentTime, prayerTimes) },
                    getPrayerTimeDisplay = { prayerName -> PrayerTimeHelpers.getPrayerTimeDisplay(prayerName, prayerTimes) },
                    getTimeUntilNextPrayer = { PrayerTimeHelpers.getTimeUntilNextPrayer(currentTime, prayerTimes) },
                    getCurrentDate = { PrayerTimeHelpers.getCurrentDate() },
                    getSmartTitle = { SmartContentUtils.getSmartTitle(currentTime) },
                    getSmartContent = { SmartContentUtils.getSmartContent(currentTime, prayerTimes) { PrayerTimeHelpers.getCurrentPrayer(currentTime, prayerTimes) } },
                    getSmartFooter = { SmartContentUtils.getSmartFooter(PrayerTimeHelpers.getCurrentPrayer(currentTime, prayerTimes), PrayerTimeHelpers.getNextPrayer(currentTime, prayerTimes)) },
                    getTimeSinceCurrentPrayer = { SmartContentUtils.formatTimeSinceCurrentPrayer(SmartContentUtils.getMinutesSinceCurrentPrayer(prayerTimes, currentTime) { PrayerTimeHelpers.getCurrentPrayer(currentTime, prayerTimes) }) },
                    getPrayerProgress = { SmartContentUtils.getPrayerProgress(prayerTimes, currentTime) },
                    getDailyStatsTitle = { 
                        val (completed, total) = SmartContentUtils.getPrayerProgress(prayerTimes, currentTime)
                        SmartContentUtils.getDailyStatsTitle(completed, total) 
                    },
                    getDailyStatsMessage = { 
                        val (completed, total) = SmartContentUtils.getPrayerProgress(prayerTimes, currentTime)
                        SmartContentUtils.getDailyStatsMessage(completed, total) 
                    },
                    onCompassClick = { 
                        Log.d("PrayerTimes", "Compass clicked, showing popup")
                        showCompassPopup = true 
                    }
                )
                
                // Instruction banner for prayer time adjustment
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp), // Match big tiles horizontal constraint
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Adjust Prayer Times",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Hold any prayer card to fine-tune times",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                
                // Expandable prayer layout - smart default view with expand option
                var showAllPrayers by remember { mutableStateOf(false) }
                
                // Show/Hide toggle button - placed at top for better visibility
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(
                        onClick = { showAllPrayers = !showAllPrayers },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (showAllPrayers) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showAllPrayers) "Show Less" else "Show All Prayers",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                
                // Always show current and next 3 prayers (4 total) by default
                val defaultPrayers = listOf("Dhuhr", "Asr", "Maghrib", "Isha")
                val additionalPrayers = listOf("Fajr", "Sunrise")
                
                // First row: Most relevant prayers (default view)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Dhuhr
                    InteractivePrayerCard(
                        prayerName = "Dhuhr",
                        currentEditingTile = currentEditingTile,
                        onEditingTileChange = { currentEditingTile = it },
                        currentOffset = storedOffsets.dhuhr,
                        modifier = Modifier
                            .weight(1f)
                            .height(95.dp),
                        onShowPopup = { prayerName ->
                            popupPrayerName = prayerName
                            showPrayerDialPopup = true
                        }
                    )
                    
                    // Asr
                    InteractivePrayerCard(
                        prayerName = "Asr",
                        currentEditingTile = currentEditingTile,
                        onEditingTileChange = { currentEditingTile = it },
                        currentOffset = storedOffsets.asr,
                        modifier = Modifier
                            .weight(1f)
                            .height(95.dp),
                        onShowPopup = { prayerName ->
                            popupPrayerName = prayerName
                            showPrayerDialPopup = true
                        }
                    )
                }
                
                // Second row: Remaining main prayers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 0.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Maghrib
                    InteractivePrayerCard(
                        prayerName = "Maghrib",
                        currentEditingTile = currentEditingTile,
                        onEditingTileChange = { currentEditingTile = it },
                        currentOffset = storedOffsets.maghrib,
                        modifier = Modifier
                            .weight(1f)
                            .height(95.dp),
                        onShowPopup = { prayerName ->
                            popupPrayerName = prayerName
                            showPrayerDialPopup = true
                        }
                    )
                    
                    // Isha
                    InteractivePrayerCard(
                        prayerName = "Isha",
                        currentEditingTile = currentEditingTile,
                        onEditingTileChange = { currentEditingTile = it },
                        currentOffset = storedOffsets.isha,
                        modifier = Modifier
                            .weight(1f)
                            .height(95.dp),
                        onShowPopup = { prayerName ->
                            popupPrayerName = prayerName
                            showPrayerDialPopup = true
                        }
                    )
                }
                
                // Expandable section for additional prayers (Fajr & Sunrise)
                AnimatedVisibility(
                    visible = showAllPrayers,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, top = 0.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Fajr
                        InteractivePrayerCard(
                            prayerName = "Fajr",
                            currentEditingTile = currentEditingTile,
                            onEditingTileChange = { currentEditingTile = it },
                            currentOffset = storedOffsets.fajr,
                            modifier = Modifier
                                .weight(1f)
                                .height(95.dp),
                            onShowPopup = { prayerName ->
                                popupPrayerName = prayerName
                                showPrayerDialPopup = true
                            }
                        )
                        
                        // Sunrise
                        InteractivePrayerCard(
                            prayerName = "Sunrise",
                            currentEditingTile = currentEditingTile,
                            onEditingTileChange = { currentEditingTile = it },
                            currentOffset = 0, // Sunrise doesn't have adjustments
                            modifier = Modifier
                                .weight(1f)
                                .height(95.dp),
                            onShowPopup = { } // Sunrise doesn't have popup
                        )
                    }
                }
                
                
                // Fixed spacer to provide consistent spacing
                Spacer(modifier = Modifier.height(16.dp))
                
                // Location info using Material 3 design
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 0.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
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
                            text = getLocationWithCountryCode(location, prayerTimes?.location),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            fontWeight = FontWeight.Medium
                        ).also {
                            android.util.Log.d("LocationText", "📍 LOCATION DISPLAY: '$location' (length=${location.length})")
                        }
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
    
    // COMPASS POPUP - Shows large compass with calibration guidance
    if (showCompassPopup) {
        Log.d("PrayerTimes", "showCompassPopup is true, rendering CompassPopupScreen")
        CompassPopupScreen(
            progress = prayerTimes?.let { times ->
                PrayerTimeHelpers.getNextPrayer(currentTime, times)?.let { nextPrayer ->
                    // Calculate progress based on current time and next prayer time
                    val now = currentTime.toSecondOfDay().toFloat()
                    val nextPrayerTime = nextPrayer.second.toSecondOfDay().toFloat()
                    if (nextPrayerTime > now) {
                        val totalDaySeconds = 24 * 60 * 60f
                        val timeUntilNext = nextPrayerTime - now
                        1f - (timeUntilNext / totalDaySeconds).coerceIn(0f, 1f)
                    } else 0.7f
                } ?: 0.7f
            } ?: 0.7f,
            timeText = PrayerTimeHelpers.getTimeUntilNextPrayer(currentTime, prayerTimes),
            locationService = locationService,
            onDismiss = { 
                Log.d("PrayerTimes", "onDismiss called, hiding compass popup")
                showCompassPopup = false 
            }
        )
    }
    
    // INTERACTIVE PRAYER DIAL POPUP - Shows circular dial overlay
    if (showPrayerDialPopup && popupPrayerName != null) {
        Log.d("PrayerTimes", "showPrayerDialPopup is true, rendering InteractivePrayerDial overlay for $popupPrayerName")
        
        // Full screen overlay with dark background - tap outside to dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            // Calculate if tap is outside the dial area (350.dp diameter)
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val dialRadius = 175.dp.toPx() // 350.dp / 2
                            val distance = kotlin.math.sqrt(
                                (offset.x - centerX) * (offset.x - centerX) + 
                                (offset.y - centerY) * (offset.y - centerY)
                            )
                            
                            if (distance > dialRadius) {
                                Log.d("PrayerTimes", "Tap outside dial area, closing popup")
                                showPrayerDialPopup = false
                                popupPrayerName = null
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // State for adjustment in popup
            var timeAdjustment by remember { mutableStateOf(
                when (popupPrayerName) {
                    "Dhuhr" -> storedOffsets.dhuhr
                    "Asr" -> storedOffsets.asr
                    "Maghrib" -> storedOffsets.maghrib
                    "Isha" -> storedOffsets.isha
                    else -> 0
                }
            ) }
            
            // Circular dial container - NO gesture interference
            Box(
                modifier = Modifier
                    .size(350.dp), // Larger size for better interaction
                contentAlignment = Alignment.Center
            ) {
                com.starception.submission.feature.prayertimes.components.InteractivePrayerDial(
                    prayerName = popupPrayerName!!,
                    originalTime = when (popupPrayerName) {
                        "Dhuhr" -> prayerTimes?.dhuhr ?: LocalTime.of(12, 0)
                        "Asr" -> prayerTimes?.asr ?: LocalTime.of(15, 46)
                        "Maghrib" -> prayerTimes?.maghrib ?: LocalTime.of(18, 25)
                        "Isha" -> prayerTimes?.isha ?: LocalTime.of(19, 55)
                        else -> LocalTime.of(12, 0)
                    },
                    timeAdjustment = timeAdjustment,
                    onTimeAdjusted = { adjustment ->
                        timeAdjustment = adjustment
                    },
                    onSaveAdjustment = { prayerName, finalAdjustment ->
                        Log.d("PrayerTimes", "🎯 POPUP DIAL SAVE: $prayerName = $finalAdjustment minutes")
                        // Save the adjustment and close popup
                        showPrayerDialPopup = false
                        popupPrayerName = null
                    },
                    onResetAdjustment = {
                        Log.d("PrayerTimes", "🔄 POPUP DIAL RESET for $popupPrayerName")
                        timeAdjustment = 0
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Helper function to get location text with country code
 * Uses CountryCodeMapper to add country code to location display
 */
private fun getLocationWithCountryCode(
    locationString: String,
    locationData: com.starception.submission.prayer.model.Location?
): String {
    if (locationString.isBlank()) {
        return "Loading location..."
    }
    
    // If we have location data with country information, try to get country code
    if (locationData != null) {
        val countryCode = when {
            // Use existing country code if available
            locationData.countryCode.isNotEmpty() -> locationData.countryCode
            
            // Try to map country name to code using CountryCodeMapper
            locationData.country.isNotEmpty() -> {
                CountryCodeMapper.getCountryCode(locationData.country) ?: ""
            }
            
            else -> ""
        }
        
        // Return location with country code if we found one
        if (countryCode.isNotEmpty()) {
            return "$locationString ($countryCode)"
        }
    }
    
    // Fallback to original location string
    return locationString
}

