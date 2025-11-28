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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.sin
import kotlin.math.PI
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import android.util.Log
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import com.starception.submission.feature.prayertimes.components.ElasticTopShape
import com.starception.submission.feature.prayertimes.wobble.WobblePullToRefresh
import com.starception.submission.feature.prayertimes.wobble.wobbleTransform
import com.starception.submission.feature.prayertimes.utils.convertToArabicNumerals
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
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
import com.starception.submission.islamic.qibla.presentation.component.QiblaGlobeView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    val screenContext = LocalContext.current
    
    // COMPREHENSIVE UI LOGGING SYSTEM
    LaunchedEffect(Unit) {
        android.util.Log.i("PrayerTimesScreen", "")
        android.util.Log.i("PrayerTimesScreen", "🖼️ PRAYER TIMES SCREEN COMPOSITION")
        android.util.Log.i("PrayerTimesScreen", "=".repeat(60))
        android.util.Log.i("PrayerTimesScreen", "🚀 Screen initialization started")
        android.util.Log.i("PrayerTimesScreen", "⏰ Timestamp: ${LocalDateTime.now()}")
        android.util.Log.i("PrayerTimesScreen", "📱 Context: ${screenContext.javaClass.simpleName}")
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
                screenContext.applicationContext,
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

    // PRAYER COUNTER STATE - Tracks how many prayers have been marked as prayed today
    var prayedCount by remember { mutableStateOf(
        try {
            com.starception.submission.util.PrayerTracker.getPrayedCountToday()
        } catch (e: Exception) {
            0
        }
    ) }

    // Track current time updates for prayer status calculations
    LaunchedEffect(currentTime) {
        // Update prayed count when time changes (this happens every minute)
        prayedCount = try {
            com.starception.submission.util.PrayerTracker.getPrayedCountToday()
        } catch (e: Exception) {
            0
        }
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
    var popupDialState by remember { mutableStateOf<String?>(null) }  // null means closed, non-null means open with that prayer name

    val hapticFeedback = LocalHapticFeedback.current

    // OBSERVE PRAYER OFFSETS FROM REPOSITORY FLOW - Automatically updates when settings change!
    val repository = remember {
        val entryPoint = EntryPointAccessors.fromApplication(
            screenContext.applicationContext,
            com.starception.submission.feature.prayertimes.data.PrayerTimeCalculatorEntryPoint::class.java
        )
        val repo = entryPoint.prayerSettingsRepository()
        val instanceId = System.identityHashCode(repo).toString(16)
        android.util.Log.d("PrayerTimesScreen", "📍 REPOSITORY INSTANCE OBTAINED: $instanceId")
        repo
    }

    // Observe the reactive flow - this automatically updates when Prayer Settings changes!
    val calculationSettings = repository.calculationSettingsFlow.collectAsState().value
    val storedOffsets = calculationSettings.timeOffsets

    // Log whenever calculationSettings changes (BEFORE extracting offsets)
    LaunchedEffect(calculationSettings) {
        android.util.Log.d("PrayerTimesScreen", "📥 CALCULATION SETTINGS RECEIVED FROM FLOW:")
        android.util.Log.d("PrayerTimesScreen", "   📦 Full settings object: $calculationSettings")
        android.util.Log.d("PrayerTimesScreen", "   🕰️ timeOffsets object: ${calculationSettings.timeOffsets}")
        android.util.Log.d("PrayerTimesScreen", "   ⏰ Timestamp: ${System.currentTimeMillis()}")
    }

    // Log whenever offsets change (AFTER extracting from settings)
    LaunchedEffect(storedOffsets) {
        android.util.Log.d("PrayerTimesScreen", "🔄 OFFSETS UPDATED FROM FLOW:")
        android.util.Log.d("PrayerTimesScreen", "   🌅 Fajr: ${storedOffsets.fajr}")
        android.util.Log.d("PrayerTimesScreen", "   🌄 Sunrise: ${storedOffsets.sunrise}")
        android.util.Log.d("PrayerTimesScreen", "   🌞 Dhuhr: ${storedOffsets.dhuhr}")
        android.util.Log.d("PrayerTimesScreen", "   🌇 Asr: ${storedOffsets.asr}")
        android.util.Log.d("PrayerTimesScreen", "   🌆 Maghrib: ${storedOffsets.maghrib}")
        android.util.Log.d("PrayerTimesScreen", "   🌙 Isha: ${storedOffsets.isha}")
        android.util.Log.d("PrayerTimesScreen", "   📊 Total non-zero offsets: ${listOf(storedOffsets.fajr, storedOffsets.sunrise, storedOffsets.dhuhr, storedOffsets.asr, storedOffsets.maghrib, storedOffsets.isha).count { it != 0 }}")
        android.util.Log.d("PrayerTimesScreen", "   ⏰ Timestamp: ${System.currentTimeMillis()}")
    }
    
    // LOCATION SERVICE - For Qibla compass functionality
    val locationService = remember {
        val entryPoint = EntryPointAccessors.fromApplication(
            screenContext.applicationContext,
            PrayerTimeCalculatorEntryPoint::class.java
        )
        entryPoint.enhancedLocationService()
    }
    

    
    // REFRESH LOGIC - Handle pull-to-refresh action with location service checking
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            try {
                // LOCATION SERVICE CHECK: Verify location services before proceeding
                android.util.Log.d("PullToRefresh", "=== STARTING PULL-TO-REFRESH DEBUG ===")
                android.util.Log.d("PullToRefresh", "User initiated prayer times refresh with location service validation")
                
                // Get location service to check if services are enabled
                val entryPoint = EntryPointAccessors.fromApplication(
                    screenContext.applicationContext,
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
                        screenContext.applicationContext,
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
                android.util.Log.d("PullToRefresh", "CURRENT LOCATION BEFORE REFRESH: \"$location\"")
                val startTime = System.currentTimeMillis()
                try {
                    // Run calculation with timeout protection
                    android.util.Log.d("PullToRefresh", "TIMEOUT PROTECTION: Calculation has maximum 3000ms to complete")
                    withTimeout(3000L) {
                        withContext(Dispatchers.Default) {
                            android.util.Log.d("PullToRefresh", "CALCULATION START: Creating PrayerTimesCalculator and running calculation")
                            android.util.Log.d("PullToRefresh", "FORCE GPS REFRESH: true - will skip saved location and fetch fresh GPS")
                            val calculator = PrayerTimesCalculator(screenContext)
                            val result = calculator.calculateDefaultPrayerTimes(forceGpsRefresh = true)

                            android.util.Log.d("PullToRefresh", "CALCULATION RESULT: Prayer times = ${if (result.first != null) "SUCCESS" else "NULL"}")
                            android.util.Log.d("PullToRefresh", "CALCULATION RESULT: New location = \"${result.second}\"")
                            android.util.Log.d("PullToRefresh", "LOCATION COMPARISON: Old=\"$location\" → New=\"${result.second}\"")
                            android.util.Log.d("PullToRefresh", "LOCATION CHANGED: ${location != result.second}")

                            prayerTimes = result.first   // Calculated prayer times (or null if failed)
                            location = result.second     // Location name for display

                            android.util.Log.d("PullToRefresh", "STATE UPDATED: location variable now = \"$location\"")
                        }
                    }
                    android.util.Log.d("PullToRefresh", "Calculation completed successfully in ${System.currentTimeMillis() - startTime}ms")
                    android.util.Log.d("PullToRefresh", "FINAL LOCATION AFTER REFRESH: \"$location\"")
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
    
    // Activity recognition permission for activity detection
    val activityRecognitionPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        rememberPermissionState(
            permission = Manifest.permission.ACTIVITY_RECOGNITION
        )
    } else {
        null // Not needed on older Android versions
    }
    
    // Storage/Media audio permission for Quran playback from SD card
    // This will be requested only when user tries to play Quran audio
    val audioPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+ uses READ_MEDIA_AUDIO
        rememberPermissionState(
            permission = Manifest.permission.READ_MEDIA_AUDIO
        )
    } else {
        // Android 12 and below use READ_EXTERNAL_STORAGE
        rememberPermissionState(
            permission = Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
    
    // Monitor permission changes and re-initialize ActivityTracker
    val activityContext = LocalContext.current
    LaunchedEffect(locationPermissionState.status, activityRecognitionPermissionState?.status) {
        // Check if both location and activity recognition permissions are now granted
        val locationGranted = locationPermissionState.status is com.google.accompanist.permissions.PermissionStatus.Granted
        val activityGranted = activityRecognitionPermissionState?.status is com.google.accompanist.permissions.PermissionStatus.Granted 
            || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        
        if (locationGranted && activityGranted) {
            // Re-initialize ActivityTracker in case permissions were just granted
            try {
                com.starception.submission.util.ActivityTracker.reinitializeIfNeeded(activityContext)
            } catch (e: Exception) {
                Log.e("PrayerTimesScreen", "Error reinitializing ActivityTracker", e)
            }
        }
    }
    
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
            kotlinx.coroutines.delay(500) // Small delay between permission requests
        }
        
        // STEP 2: Request notification permission for prayer alerts (Android 13+)
        // Only request if we're on Android 13+ where this permission is required
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val notificationStatus = notificationPermissionState.status
            if (notificationStatus is Denied && !notificationStatus.shouldShowRationale) {
                notificationPermissionState.launchPermissionRequest()
                kotlinx.coroutines.delay(500) // Small delay between permission requests
            }
        }
        
        // STEP 3: Audio permission will be requested only when user tries to play Quran audio
        // No automatic request here - permission will be requested on-demand
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
        
        // Material 3 expressive animation states with spring physics
        val expressiveAnimationSpec = spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
            visibilityThreshold = 0.01f
        )
        
        val sizeAnimationSpec = spring<IntSize>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
        
        // Material 3 expressive scale animation: shrink other tiles when one is in edit mode
        val scale by animateFloatAsState(
            targetValue = when {
                isInEditMode -> 1f // Keep normal size when this tile is in edit mode
                isAnotherTileInEditMode -> 0.82f // More pronounced shrink with spring bounce
                else -> 1f // Normal size when no tile is in edit mode
            },
            animationSpec = expressiveAnimationSpec,
            label = "expressiveTileScale"
        )
        
        // Debug logging
        android.util.Log.d("PrayerCard", "🔄 Rendering InteractivePrayerCard for $prayerName, isInEditMode=$isInEditMode, scale=$scale")
        
        if (isInEditMode) {
            // Show ONLY the circular dial - complete transformation, no extra UI
            // CRITICAL FIX: Initialize from currentOffset and track independently
            var timeAdjustment by remember(prayerName) { mutableStateOf(currentOffset) }

            // CRITICAL LOGGING: Track when timeAdjustment state variable changes
            LaunchedEffect(timeAdjustment) {
                android.util.Log.w("PrayerTimesScreen", "⚠️ STATE CHANGE - Prayer: $prayerName")
                android.util.Log.w("PrayerTimesScreen", "   📊 timeAdjustment state value: $timeAdjustment minutes")
                android.util.Log.w("PrayerTimesScreen", "   🔍 currentOffset reference: $currentOffset minutes")
                android.util.Log.w("PrayerTimesScreen", "   🎯 isInEditMode: $isInEditMode")
            }

            // Track when currentOffset changes (the remember key)
            LaunchedEffect(currentOffset) {
                android.util.Log.w("PrayerTimesScreen", "🔄 CURRENT OFFSET CHANGED - Prayer: $prayerName")
                android.util.Log.w("PrayerTimesScreen", "   📥 New currentOffset: $currentOffset minutes")
                android.util.Log.w("PrayerTimesScreen", "   ⚙️ This triggers remember {} to reinitialize with new value")
            }

            // Use Box to constrain the circular dial to the original tile space
            Box(
                modifier = modifier
                    .aspectRatio(1f) // Force square container for perfect circle
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale
                    ),
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
            ) {
                // ONLY show the circular dial - complete transformation with no overlapping content
                com.starception.submission.feature.prayertimes.components.InteractivePrayerDial(
                    prayerName = prayerName,
                    originalTime = when (prayerName) {
                        "Fajr" -> prayerTimes?.fajr ?: LocalTime.of(5, 23)
                        "Sunrise" -> prayerTimes?.sunrise ?: LocalTime.of(6, 42)
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

                        // Save the adjustment to Prayer settings using singleton repository
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                // CRITICAL FIX: Use singleton repository from EntryPoint, not new instance
                                val entryPoint = EntryPointAccessors.fromApplication(
                                    screenContext.applicationContext,
                                    com.starception.submission.feature.prayertimes.data.PrayerTimeCalculatorEntryPoint::class.java
                                )
                                val repository = entryPoint.prayerSettingsRepository()
                                repository.updateSinglePrayerOffset(prayerName, finalAdjustment)
                                android.util.Log.i("PrayerTimesScreen", "✅ SAVE SUCCESS: $prayerName offset saved as $finalAdjustment minutes")

                                // CRITICAL: Wait for preferences to be fully written to disk
                                // This ensures the recalculation will read the NEW offset values
                                delay(100) // 100ms delay to ensure SharedPreferences commit completes
                                android.util.Log.d("PrayerTimesScreen", "⏸️ Waited 100ms for preferences write to complete")

                                // Update UI on main thread
                                withContext(Dispatchers.Main) {
                                    // NO MANUAL UPDATE NEEDED - The repository flow automatically updates storedOffsets!
                                    // When we call repository.updateCalculationSettings() above, it triggers the flow
                                    // which causes calculationSettingsFlow.collectAsState() to recompose with new values
                                    android.util.Log.d("PrayerTimesScreen", "✅ Offset saved - repository flow will automatically update UI")
                                    android.util.Log.d("PrayerTimesScreen", "   💾 Saved $prayerName offset: $finalAdjustment minutes")
                                    android.util.Log.d("PrayerTimesScreen", "   🔄 Flow-based recomposition will trigger automatically")

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
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = modifier
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale
                    )
                    .pointerInput(prayerName) {
                        detectTapGestures(
                            onLongPress = {
                                android.util.Log.d("PrayerCard", "🔥 LONG PRESS detected on $prayerName card!")
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                // Call onShowPopup directly (transitioning flag prevents double triggers)
                                onShowPopup(prayerName)
                                android.util.Log.d("PrayerCard", "✅ Called onShowPopup for $prayerName")
                            },
                            onTap = {
                                android.util.Log.d("PrayerCard", "👆 Regular tap detected on $prayerName card")
                            }
                        )
                    }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp)
                            .padding(vertical = 1.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top section: Prayer names
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
                        
                        // Bottom section: Times with proper spacing and bottom padding
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(-2.dp)
                        ) {
                            // Arabic numerals time (smaller, at top) - show adjusted time
                            val originalTime = when (prayerName) {
                                "Fajr" -> prayerTimes?.fajr
                                "Sunrise" -> prayerTimes?.sunrise
                                "Dhuhr" -> prayerTimes?.dhuhr
                                "Asr" -> prayerTimes?.asr
                                "Maghrib" -> prayerTimes?.maghrib
                                "Isha" -> prayerTimes?.isha
                                else -> null
                            }
                            val adjustedTime = originalTime?.let { time ->
                                val adjustedDateTime = java.time.LocalDateTime.of(java.time.LocalDate.now(), time).plusMinutes(currentOffset.toLong())
                                val adjusted = adjustedDateTime.toLocalTime()
                                val hour12 = if (adjusted.hour == 0) 12 
                                            else if (adjusted.hour > 12) adjusted.hour - 12 
                                            else adjusted.hour
                                val amPm = if (adjusted.hour < 12) "AM" else "PM"
                                val result = String.format("%d:%02d %s", hour12, adjusted.minute, amPm)
                                android.util.Log.d("PrayerCard", "🕐 TIME CALCULATION for $prayerName:")
                                android.util.Log.d("PrayerCard", "   📅 Original time: $time")
                                android.util.Log.d("PrayerCard", "   ⏱️ Current offset: $currentOffset minutes")
                                android.util.Log.d("PrayerCard", "   🔄 Adjusted time: $result")
                                result
                            } ?: ""
                            val arabicTime = convertToArabicNumerals(adjustedTime)
                            
                            Text(
                                text = arabicTime,
                                style = MaterialTheme.typography.bodySmall,
                                color = when (PrayerTimeHelpers.getPrayerStatus(prayerName, currentTime, prayerTimes)) {
                                    "Current" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                                    "Next" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                },
                                fontWeight = FontWeight.Normal,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                            
                            // English time (main display) with bottom padding - show adjusted time
                            Text(
                                text = buildAnnotatedString {
                                    val baseColor = when (PrayerTimeHelpers.getPrayerStatus(prayerName, currentTime, prayerTimes)) {
                                        "Current" -> MaterialTheme.colorScheme.tertiary
                                        "Next" -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    
                                    // Main prayer time in bold - show adjusted time
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                                        append(adjustedTime)
                                    }
                                    
                                    // Always show offset indicator (including zero values for clarity)
                                    append(" ")
                                    withStyle(style = SpanStyle(
                                        fontWeight = FontWeight.Medium,
                                        color = if (currentOffset != 0) baseColor.copy(alpha = 0.9f) else baseColor.copy(alpha = 0.5f),
                                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                                    )) {
                                        append(if (currentOffset > 0) "+${currentOffset}m" else if (currentOffset < 0) "${currentOffset}m" else "±0m")
                                    }
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                modifier = Modifier.padding(bottom = 8.dp) // Add specific bottom padding after English prayer time
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
                val calculator = PrayerTimesCalculator(screenContext)
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
                screenContext.applicationContext,
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
    
    

    
    // Use WobblePullToRefresh component
    
    WobblePullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = { isRefreshing = true },
        modifier = modifier
    ) { wobbleState ->
        Column(modifier = Modifier.fillMaxSize()) {
            // Show pull instruction ONLY when dragging with smooth animation
            AnimatedVisibility(
                visible = wobbleState.dragDistance > 30f && !isRefreshing,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                ) + slideInVertically(
                    initialOffsetY = { -it / 2 },
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = LinearOutSlowInEasing
                    )
                ) + slideOutVertically(
                    targetOffsetY = { -it / 2 },
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = LinearOutSlowInEasing
                    )
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = if (wobbleState.dragDistance >= 800f) {
                            "Release to refresh location"
                        } else {
                            "Keep pulling down to refresh location"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Refreshing indicator
            AnimatedVisibility(
                visible = isRefreshing,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Refreshing location...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            // Home page content with wobble transformation applied to actual content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
            
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
            // Main content with simple wobble transformations
            // Use wobbleState.wobbleIntensity from wobbleState
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(
                        top = 8.dp + (wobbleState.wobbleIntensity * 30f).dp, // Simple wobble spacing
                        bottom = 0.dp
                    )
                    .wobbleTransform(wobbleState.wobbleIntensity),
                verticalArrangement = Arrangement.Top
            ) {


                
                // Swipeable Big Tiles with simple wobble
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wobbleTransform(wobbleState.wobbleIntensity, offsetMultiplier = 0.33f, scaleMultiplier = 0.6f)
                ) {
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
                    getPrayed = { prayedCount },
                    getCurrentActivity = { 
                        // Get current activity from ActivityTracker
                        try {
                            com.starception.submission.util.ActivityTracker.getCurrentActivity()
                        } catch (e: Exception) {
                            "UNKNOWN"
                        }
                    },
                    onCompassClick = { 
                        Log.d("PrayerTimes", "Compass clicked, showing popup")
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        showCompassPopup = true 
                    },
                    timeOffsets = storedOffsets
                )
                }
                
                // Instruction banner for prayer time adjustment with wobble
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp) // Match big tiles horizontal constraint
                        .offset(
                            y = (wobbleState.wobbleIntensity * 3f).dp
                        )
                        .graphicsLayer {
                            scaleX = 1f + (wobbleState.wobbleIntensity * 0.02f)
                            scaleY = 1f + (wobbleState.wobbleIntensity * 0.03f)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
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
                
                // Material 3 expressive tile height animation with spring physics
                val tileHeight by animateDpAsState(
                    targetValue = if (showAllPrayers) 120.dp else 145.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                        visibilityThreshold = 0.1.dp
                    ),
                    label = "expressiveTileHeight"
                )
                
                // Staggered animation progress for choreographed card entrances
                val fajrAnimProgress by animateFloatAsState(
                    targetValue = if (showAllPrayers) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                        visibilityThreshold = 0.01f
                    ),
                    label = "fajrCardAnimation"
                )
                
                val sunriseAnimProgress by animateFloatAsState(
                    targetValue = if (showAllPrayers) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                        visibilityThreshold = 0.01f
                    ).let { spec ->
                        // Add stagger delay for sunrise card
                        tween(
                            durationMillis = (spec as? SpringSpec)?.let { 600 } ?: 400,
                            delayMillis = 80,
                            easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
                        )
                    },
                    label = "sunriseCardAnimation"
                )
                
                // Always show current and next 3 prayers (4 total) by default
                val defaultPrayers = listOf("Dhuhr", "Asr", "Maghrib", "Isha")
                val additionalPrayers = listOf("Fajr", "Sunrise")
                
                // First row: Most relevant prayers (default view) with wobble
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .offset(
                            y = (wobbleState.wobbleIntensity * 4f).dp
                        )
                        .graphicsLayer {
                            scaleX = 1f + (wobbleState.wobbleIntensity * 0.02f)
                            scaleY = 1f + (wobbleState.wobbleIntensity * 0.03f)
                        },
                        //.background(Color.Red.copy(alpha = 0.2f)), // DEBUG: Red background for first row
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Dhuhr
                    InteractivePrayerCard(
                        prayerName = "Dhuhr",
                        currentEditingTile = currentEditingTile,
                        onEditingTileChange = { currentEditingTile = it },
                        currentOffset = storedOffsets.dhuhr,
                        modifier = Modifier
                            .weight(1f)
                            .height(tileHeight)
                            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), // Extra bottom space for elevation shadows
                        onShowPopup = { prayerName ->
                            android.util.Log.d("PrayerCard", "🚀 onShowPopup called with $prayerName")
                            popupDialState = prayerName  // Direct state update
                            android.util.Log.d("PrayerCard", "✅ Set popupDialState to $prayerName")
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
                            .height(tileHeight)
                            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), // Extra bottom space for elevation shadows
                        onShowPopup = { prayerName ->
                            android.util.Log.d("PrayerCard", "🚀 onShowPopup called with $prayerName")
                            popupDialState = prayerName  // Direct state update
                            android.util.Log.d("PrayerCard", "✅ Set popupDialState to $prayerName")
                        }
                    )
                }
                
                // Second row: Remaining main prayers with wobble
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .offset(
                            y = (wobbleState.wobbleIntensity * 6f).dp
                        )
                        .graphicsLayer {
                            scaleX = 1f + (wobbleState.wobbleIntensity * 0.025f)
                            scaleY = 1f + (wobbleState.wobbleIntensity * 0.02f)
                        },
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Maghrib
                    InteractivePrayerCard(
                        prayerName = "Maghrib",
                        currentEditingTile = currentEditingTile,
                        onEditingTileChange = { currentEditingTile = it },
                        currentOffset = storedOffsets.maghrib,
                        modifier = Modifier
                            .weight(1f)
                            .height(tileHeight)
                            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), // Extra bottom space for elevation shadows
                        onShowPopup = { prayerName ->
                            android.util.Log.d("PrayerCard", "🚀 onShowPopup called with $prayerName")
                            popupDialState = prayerName  // Direct state update
                            android.util.Log.d("PrayerCard", "✅ Set popupDialState to $prayerName")
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
                            .height(tileHeight)
                            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), // Extra bottom space for elevation shadows
                        onShowPopup = { prayerName ->
                            android.util.Log.d("PrayerCard", "🚀 onShowPopup called with $prayerName")
                            popupDialState = prayerName  // Direct state update
                            android.util.Log.d("PrayerCard", "✅ Set popupDialState to $prayerName")
                        }
                    )
                }
                
                // Material 3 expressive expandable section with spring-based animations
                AnimatedVisibility(
                    visible = showAllPrayers,
                    enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                            visibilityThreshold = IntSize.VisibilityThreshold
                        ),
                        expandFrom = Alignment.Top
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 400,
                            delayMillis = 50,
                            easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f) // Material motion emphasis
                        )
                    ),
                    exit = shrinkVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        shrinkTowards = Alignment.Top
                    ) + fadeOut(
                        animationSpec = tween(
                            durationMillis = 250,
                            easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f) // Material motion standard
                        )
                    )
                ) {
                    // Material 3 expressive choreographed card entrance
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Fajr with staggered entrance animation
                        InteractivePrayerCard(
                            prayerName = "Fajr",
                            currentEditingTile = currentEditingTile,
                            onEditingTileChange = { currentEditingTile = it },
                            currentOffset = storedOffsets.fajr,
                            modifier = Modifier
                                .weight(1f)
                                .height(tileHeight)
                                .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                                .graphicsLayer {
                                    // Material 3 expressive card entrance with bounce and scale
                                    translationY = (1f - fajrAnimProgress) * 24f
                                    scaleX = 0.85f + (fajrAnimProgress * 0.15f)
                                    scaleY = 0.85f + (fajrAnimProgress * 0.15f)
                                    alpha = fajrAnimProgress
                                },
                            onShowPopup = { prayerName ->
                                android.util.Log.d("PrayerCard", "🚀 onShowPopup called with $prayerName")
                                popupDialState = prayerName  // Direct state update
                                android.util.Log.d("PrayerCard", "✅ Set popupDialState to $prayerName")
                            }
                        )
                        
                        // Sunrise with staggered entrance animation
                        InteractivePrayerCard(
                            prayerName = "Sunrise",
                            currentEditingTile = currentEditingTile,
                            onEditingTileChange = { currentEditingTile = it },
                            currentOffset = storedOffsets.sunrise,
                            modifier = Modifier
                                .weight(1f)
                                .height(tileHeight)
                                .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                                .graphicsLayer {
                                    // Material 3 expressive card entrance with staggered timing
                                    translationY = (1f - sunriseAnimProgress) * 32f
                                    scaleX = 0.82f + (sunriseAnimProgress * 0.18f)
                                    scaleY = 0.82f + (sunriseAnimProgress * 0.18f)
                                    alpha = sunriseAnimProgress
                                },
                            onShowPopup = { prayerName ->
                                android.util.Log.d("PrayerCard", "🚀 onShowPopup called with $prayerName")
                                popupDialState = prayerName  // Direct state update
                                android.util.Log.d("PrayerCard", "✅ Set popupDialState to $prayerName")
                            }
                        )
                    }
                }
                
                // Material 3 expressive toggle button with smooth icon rotation and scale
                val buttonIconRotation by animateFloatAsState(
                    targetValue = if (showAllPrayers) 180f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "iconRotation"
                )
                
                val buttonContentScale by animateFloatAsState(
                    targetValue = if (showAllPrayers) 1.05f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessHigh
                    ),
                    label = "buttonScale"
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(
                        onClick = { 
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showAllPrayers = !showAllPrayers 
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.graphicsLayer {
                            scaleX = buttonContentScale
                            scaleY = buttonContentScale
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore, // Always use ExpandMore, rotation handles the direction
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    rotationZ = buttonIconRotation
                                }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showAllPrayers) "Show Less" else "Show All Prayers",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                
                
                // Removed spacer to eliminate excessive gap
                
                // Location info using Material 3 design - aligned with big tiles
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
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
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center
                        ).also {
                            android.util.Log.d("LocationText", "📍 LOCATION DISPLAY: '$location' (length=${location.length})")
                        }
                    }
                }
            }
        }
    }
    
    // MATERIAL 3 EXPRESSIVE LOCATION SERVICE DIALOG
    AnimatedVisibility(
        visible = showLocationServiceDialog,
        enter = fadeIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + scaleIn(
            initialScale = 0.9f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        exit = fadeOut(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            )
        ) + scaleOut(
            targetScale = 0.95f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            )
        )
    ) {
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
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        showLocationServiceDialog = false
                        // Open device location settings
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        screenContext.startActivity(intent)
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
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
    
    // MATERIAL 3 EXPRESSIVE COMPASS POPUP - Enhanced entrance with slide and scale
    AnimatedVisibility(
        visible = showCompassPopup,
        enter = slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight / 3 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + scaleIn(
            initialScale = 0.85f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        exit = slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight / 4 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            )
        ) + fadeOut(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            )
        ) + scaleOut(
            targetScale = 0.9f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            )
        )
    ) {
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
            locationService = locationService,
            onDismiss = {
                Log.d("PrayerTimes", "onDismiss called, hiding compass popup")
                showCompassPopup = false
            }
        )
    }
    
    // INTERACTIVE PRAYER DIAL POPUP - Material 3 expressive overlay
    // Debug logging for popup state
    LaunchedEffect(popupDialState) {
        android.util.Log.w("PrayerDialPopup", "🎯 popupDialState changed to: $popupDialState")
        android.util.Log.w("PrayerDialPopup", "   Visible = ${popupDialState != null}")
    }

    if (popupDialState != null) {
        // Use popup state directly - it won't be null inside AnimatedVisibility when visible
        val safePrayerName = popupDialState ?: "Dhuhr"
        Log.d("PrayerTimes", "Popup dial is active, rendering InteractivePrayerDial overlay for $safePrayerName")
        
        // Full screen overlay with Material 3 expressive backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            // Clickable background layer that fills the entire screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        // Tap anywhere to close
                        Log.d("PrayerTimes", "Background tapped, closing popup")
                        popupDialState = null  // Single state update for clean dismissal
                    }
            )
            // State for adjustment in popup
            var timeAdjustment by remember { mutableStateOf(
                when (popupDialState) {
                    "Fajr" -> storedOffsets.fajr
                    "Sunrise" -> storedOffsets.sunrise
                    "Dhuhr" -> storedOffsets.dhuhr
                    "Asr" -> storedOffsets.asr
                    "Maghrib" -> storedOffsets.maghrib
                    "Isha" -> storedOffsets.isha
                    else -> 0
                }
            ) }
            
            // Circular dial container - no clickable to allow background taps through
            Box(
                modifier = Modifier
                    .size(350.dp) // Larger size for better interaction
                    .offset(y = (-300).dp), // Shift up to position dial in upper portion of screen
                contentAlignment = Alignment.Center
            ) {
                // Close button at top-right of dial
                Surface(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        Log.d("PrayerTimes", "✖️ Close button clicked")
                        popupDialState = null  // Single state update for clean dismissal
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(56.dp),
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                com.starception.submission.feature.prayertimes.components.InteractivePrayerDial(
                    prayerName = safePrayerName,
                    originalTime = when (safePrayerName) {
                        "Fajr" -> prayerTimes?.fajr ?: LocalTime.of(5, 23)
                        "Sunrise" -> prayerTimes?.sunrise ?: LocalTime.of(6, 42)
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
                        android.util.Log.d("PrayerTimesScreen", "🎯 POPUP DIAL SAVE:")
                        android.util.Log.d("PrayerTimesScreen", "   📝 Prayer: $prayerName")
                        android.util.Log.d("PrayerTimesScreen", "   ⏱️ Final Adjustment: $finalAdjustment minutes")
                        android.util.Log.d("PrayerTimesScreen", "   💾 Saving to prayer settings...")

                        // Save the adjustment to Prayer settings using singleton repository
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                // CRITICAL FIX: Use singleton repository from EntryPoint, not new instance
                                val entryPoint = EntryPointAccessors.fromApplication(
                                    screenContext.applicationContext,
                                    com.starception.submission.feature.prayertimes.data.PrayerTimeCalculatorEntryPoint::class.java
                                )
                                val repository = entryPoint.prayerSettingsRepository()
                                repository.updateSinglePrayerOffset(prayerName, finalAdjustment)
                                android.util.Log.i("PrayerTimesScreen", "✅ SAVE SUCCESS: $prayerName offset saved as $finalAdjustment minutes")

                                // CRITICAL: Wait for preferences to be fully written to disk
                                // This ensures the recalculation will read the NEW offset values
                                delay(100) // 100ms delay to ensure SharedPreferences commit completes
                                android.util.Log.d("PrayerTimesScreen", "⏸️ Waited 100ms for preferences write to complete")

                                // Update UI on main thread
                                withContext(Dispatchers.Main) {
                                    // NO MANUAL UPDATE NEEDED - The repository flow automatically updates storedOffsets!
                                    // When we call repository.updateCalculationSettings() above, it triggers the flow
                                    // which causes calculationSettingsFlow.collectAsState() to recompose with new values
                                    android.util.Log.d("PrayerTimesScreen", "✅ Offset saved - repository flow will automatically update UI")
                                    android.util.Log.d("PrayerTimesScreen", "   💾 Saved $prayerName offset: $finalAdjustment minutes")
                                    android.util.Log.d("PrayerTimesScreen", "   🔄 Flow-based recomposition will trigger automatically")

                                    // Close popup after successful saving
                                    popupDialState = null  // Single state update for clean dismissal
                                    android.util.Log.d("PrayerTimesScreen", "🚪 Closed popup - returning to main view")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("PrayerTimesScreen", "❌ SAVE FAILED: Error saving $prayerName offset", e)
                                // Still close popup even if save failed
                                withContext(Dispatchers.Main) {
                                    popupDialState = null  // Single state update for clean dismissal
                                }
                            }
                        }
                    },
                    onResetAdjustment = {
                        Log.d("PrayerTimes", "🔄 POPUP DIAL RESET for $safePrayerName")
                        timeAdjustment = 0
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } // Close if (popupDialState != null)
    } // Missing brace 1
    } // Missing brace 2
}

/**
 * Helper function to get location text with country information
 * Uses CountryCodeMapper to add country code or full country name based on available space
 */
private fun getLocationWithCountryCode(
    locationString: String,
    locationData: com.starception.submission.prayer.model.Location?
): String {
    android.util.Log.d("LocationDisplay", "🏷️ FORMATTING LOCATION DISPLAY:")
    android.util.Log.d("LocationDisplay", "   Input: '$locationString'")
    android.util.Log.d("LocationDisplay", "   Location Data: ${locationData?.let { "${it.city}, ${it.country} (${it.countryCode})" } ?: "null"}")
    
    if (locationString.isBlank()) {
        android.util.Log.d("LocationDisplay", "   ❌ Location string is blank - returning loading message")
        return "Loading location..."
    }
    
    // Clean up the location string by removing redundant country information
    val cleanLocationString = locationString
        .replace(Regex(",\\s*(UAE|United Arab Emirates)\\s*\\([A-Z]{2}\\)"), "") // Remove country name with code
        .replace(Regex(",\\s*(UAE|United Arab Emirates)"), "") // Remove country name only
        .replace(Regex("\\s*\\([A-Z]{2}\\)"), "") // Remove country code in parentheses
        .replace(Regex("\\s+"), " ") // Normalize whitespace
        .trim()
    
    android.util.Log.d("LocationDisplay", "   🧹 Cleaned location: '$cleanLocationString'")
    
    // If we have location data with country information, enhance the display
    if (locationData != null) {
        val countryCode = when {
            // Use existing country code if available
            locationData.countryCode.isNotEmpty() -> {
                android.util.Log.d("LocationDisplay", "   ✅ Using existing country code: ${locationData.countryCode}")
                locationData.countryCode
            }
            
            // Try to map country name to code using CountryCodeMapper
            locationData.country.isNotEmpty() -> {
                android.util.Log.d("LocationDisplay", "   🔍 Mapping country name '${locationData.country}' to code")
                CountryCodeMapper.getCountryCode(locationData.country) ?: ""
            }
            
            else -> {
                android.util.Log.d("LocationDisplay", "   ⚠️ No country information available")
                ""
            }
        }
        
        if (countryCode.isNotEmpty()) {
            // Get full country name from country code
            val fullCountryName = CountryCodeMapper.getFullCountryName(countryCode)
            
            android.util.Log.d("LocationDisplay", "   🌍 COUNTRY INFO:")
            android.util.Log.d("LocationDisplay", "      Country Code: '$countryCode'")
            android.util.Log.d("LocationDisplay", "      Full Country Name: '$fullCountryName'")
            
            if (fullCountryName != null) {
                // Calculate available space for the format: "City, Full Country Name (CODE)"
                val baseLocationLength = cleanLocationString.length
                val totalAvailableSpace = 45 // Increased estimate for location display
                val fullFormatLength = baseLocationLength + 2 + fullCountryName.length + 3 + countryCode.length + 1 // " , " + name + " (" + code + ")"
                val codeOnlyFormatLength = baseLocationLength + 3 + countryCode.length + 1 // " (" + code + ")"
                
                android.util.Log.d("LocationDisplay", "   📏 SPACE CALCULATION:")
                android.util.Log.d("LocationDisplay", "      Base location length: $baseLocationLength")
                android.util.Log.d("LocationDisplay", "      Total available space: $totalAvailableSpace")
                android.util.Log.d("LocationDisplay", "      Full format length: $fullFormatLength")
                android.util.Log.d("LocationDisplay", "      Code only format length: $codeOnlyFormatLength")
                
                val finalResult = when {
                    // Format: "Dubai, United Arab Emirates (AE)" - preferred format when space allows
                    fullFormatLength <= totalAvailableSpace -> {
                        val result = "$cleanLocationString, $fullCountryName ($countryCode)"
                        android.util.Log.i("LocationDisplay", "   ✅ Using full format: '$result' (${result.length} chars)")
                        result
                    }
                    
                    // Format: "Dubai (AE)" - fallback when full name doesn't fit
                    codeOnlyFormatLength <= totalAvailableSpace -> {
                        val result = "$cleanLocationString ($countryCode)"
                        android.util.Log.i("LocationDisplay", "   ✅ Using code only format: '$result' (${result.length} chars)")
                        result
                    }
                    
                    // Format: "Dubai" - fallback when even code doesn't fit
                    else -> {
                        android.util.Log.w("LocationDisplay", "   ⚠️ No space for country info - showing location only: '$cleanLocationString'")
                        cleanLocationString
                    }
                }
                
                android.util.Log.i("LocationDisplay", "   🎯 FINAL RESULT: '$finalResult' (${finalResult.length} chars)")
                return finalResult
            } else {
                // Fallback to code only if we can't get full country name
                val baseLocationLength = cleanLocationString.length
                val codeOnlyFormatLength = baseLocationLength + 3 + countryCode.length + 1 // " (" + code + ")"
                
                if (codeOnlyFormatLength <= 45) {
                    val result = "$cleanLocationString ($countryCode)"
                    android.util.Log.i("LocationDisplay", "   ✅ Using code fallback: '$result' (${result.length} chars)")
                    return result
                }
            }
        }
    }
    
    // Fallback to cleaned location string
    android.util.Log.d("LocationDisplay", "   📋 Using fallback: cleaned location string")
    return cleanLocationString
}



