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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.res.stringResource
import com.starception.submission.R
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.layout.layout
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.starception.submission.feature.prayertimes.components.ElasticTopShape
import com.starception.submission.feature.prayertimes.wobble.AlertPhase
import com.starception.submission.feature.prayertimes.wobble.PrayerAlertState
import com.starception.submission.feature.prayertimes.wobble.calculatePrayerAlertState
import com.starception.submission.feature.prayertimes.wobble.PullToSyncContainer
import com.starception.submission.feature.prayertimes.utils.convertToArabicNumerals
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding



import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus.Denied
import com.google.accompanist.permissions.rememberPermissionState
import com.starception.submission.feature.prayertimes.utils.getCurrentDate
import com.starception.submission.feature.prayertimes.utils.formatTime
import com.starception.submission.feature.prayertimes.data.PrayerTimesCalculator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.starception.submission.core.designsystem.theme.QuranFonts
import kotlinx.coroutines.launch
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.Duration
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.starception.submission.core.designsystem.theme.FloatingNavClearance

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

/**
 * Helper function to get the Arabic font family based on user selection
 */
@Composable
private fun getSelectedArabicFontFamily(context: Context): androidx.compose.ui.text.font.FontFamily {
    // Read directly from SharedPreferences with observable state
    val prefs = context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)

    // Re-read every time this composable recomposes
    val selectedFont = prefs.getString("arabic_font", "pdms_saleem") ?: "pdms_saleem"

    return com.starception.submission.feature.surah.getArabicFontFamilyForSelection(selectedFont)
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalFoundationApi::class)
@Composable
fun PrayerTimesScreen(
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onSurahClick: (Int) -> Unit = {},
    onSurahClickWithAyah: (surahNumber: Int, ayahNumber: Int) -> Unit = { _, _ -> },
    // Full media-source router for the mini-bar title tap (surah/hadith/dua);
    // when null, falls back to the legacy surah-only behavior.
    onMediaSourceClick: ((com.starception.submission.media.MediaSource) -> Unit)? = null,
    downloadProgress: Float = 0f,
    downloadLabel: String = "Downloading content",
    mediaState: com.starception.submission.media.MediaControllerUiState = com.starception.submission.media.MediaControllerUiState(),
    onMediaAction: (com.starception.submission.media.MediaAction) -> Unit = {},
    isTtsPreparing: Boolean = false,
    onPrayerAlertChanged: (com.starception.submission.feature.prayertimes.wobble.PrayerAlertState) -> Unit = {},
    prayerAlertOverride: com.starception.submission.feature.prayertimes.wobble.PrayerAlertState = com.starception.submission.feature.prayertimes.wobble.PrayerAlertState(),
    onSearchSubmit: (query: String) -> Unit = {},
    isSyncingExternal: Boolean = false,
    onSetSyncing: (Boolean) -> Unit = {},
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

    // SHARED STATE - Track which prayer card has swipe actions revealed (iOS-style)
    var revealedPrayerCard by remember { mutableStateOf<String?>(null) }

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
    // Priority: 1. In-memory cache, 2. SharedPreferences cache, 3. Dubai fallback
    val (initialPrayerTimes, initialLocation, initialLoading) = remember {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                screenContext.applicationContext,
                PrayerTimeCalculatorEntryPoint::class.java
            )

            // PRIORITY 1: Check in-memory cache (fastest)
            val cache = entryPoint.locationCache()
            val cachedData = cache.getCachedPrayerTimes()

            if (cachedData != null) {
                val (cachedPrayerTimes, _, cachedLocationName) = cachedData
                if (cachedPrayerTimes != null && cachedLocationName != null) {
                    android.util.Log.d("PrayerScreen", "🚀 INSTANT STARTUP: Loaded from in-memory cache!")
                    return@remember Triple(cachedPrayerTimes, cachedLocationName, false)
                }
            }

            // PRIORITY 2: Check SharedPreferences cache (persists across app restarts)
            val settingsRepository = entryPoint.prayerSettingsRepository()
            val persistedCache = settingsRepository.getCachedPrayerTimes()
            if (persistedCache != null) {
                android.util.Log.d("PrayerScreen", "🚀 INSTANT STARTUP: Loaded from SharedPreferences cache!")
                android.util.Log.d("PrayerScreen", "  📍 Cached location: ${persistedCache.location.getDisplayName()}")
                return@remember Triple(persistedCache, persistedCache.location.getDisplayName(), false)
            }

            // PRIORITY 3: Dubai fallback for first-time startup only
            android.util.Log.d("PrayerScreen", "🕌 No cached data found, using Dubai default for first-time startup")
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
    // Single source of truth: read straight from the hoisted VM flag.
    // All writes go through onSetSyncing so the app-level container on other
    // tabs sees the same value. (Previous mirror-state approach used a
    // remember(key) that snapped the reset signal mid-flight.)
    val isRefreshing = isSyncingExternal
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

    // Clean up popup state when navigating away to prevent lingering shadow effects
    DisposableEffect(Unit) {
        onDispose {
            popupDialState = null
        }
    }

    // BACKDROP FOR CONTROL CENTER - captures live app content for glass blur effect
    val controlCenterBackdrop = rememberLayerBackdrop()

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

    // AI SUGGESTION REPOSITORY - For AI-powered prayer time offset suggestions
    val suggestionRepository = remember {
        val entryPoint = EntryPointAccessors.fromApplication(
            screenContext.applicationContext,
            com.starception.submission.feature.prayertimes.data.PrayerTimeCalculatorEntryPoint::class.java
        )
        entryPoint.prayerTimeSuggestionRepository()
    }

    // Observe the reactive flow - this automatically updates when Prayer Settings changes!
    val calculationSettings = repository.calculationSettingsFlow.collectAsState().value
    val storedOffsets = calculationSettings.timeOffsets

    // Observe AI suggestions flow
    val aiSuggestions = suggestionRepository.suggestions.collectAsState().value

    // Observe notification preferences for per-prayer notification toggles
    val notificationPreferences = repository.notificationPreferencesFlow.collectAsState().value

    // FIRST-RUN: if "Silent During Prayer" is on (default) but the app lacks Do-Not-Disturb
    // access, show a one-time explainer that opens the DND access settings.
    var showDndDialog by remember { mutableStateOf(false) }
    LaunchedEffect(notificationPreferences.silentDuringPrayerEnabled) {
        val nm = screenContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager
        if (notificationPreferences.silentDuringPrayerEnabled &&
            !nm.isNotificationPolicyAccessGranted &&
            !repository.hasShownDndPrompt()
        ) {
            showDndDialog = true
        }
    }
    if (showDndDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                repository.markDndPromptShown()
                showDndDialog = false
            },
            title = { Text("Silence phone during prayer?") },
            text = {
                Text(
                    "Now in Android can automatically turn on Do Not Disturb at prayer time " +
                        "so you're not interrupted, then restore it afterward. This needs " +
                        "Do Not Disturb access — you can grant it on the next screen."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    repository.markDndPromptShown()
                    showDndDialog = false
                    com.starception.submission.prayer.silent.openDndAccessSettings(screenContext)
                }) { Text("Allow") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    repository.markDndPromptShown()
                    showDndDialog = false
                }) { Text("Not now") }
            },
        )
    }

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

    // Log whenever notification preferences change
    LaunchedEffect(notificationPreferences) {
        android.util.Log.d("PrayerTimesScreen", "🔔 NOTIFICATION PREFERENCES UPDATED FROM FLOW:")
        android.util.Log.d("PrayerTimesScreen", "   📱 Master toggle: ${notificationPreferences.notificationsEnabled}")
        android.util.Log.d("PrayerTimesScreen", "   🌅 Fajr: ${notificationPreferences.fajrNotificationEnabled}")
        android.util.Log.d("PrayerTimesScreen", "   🌞 Dhuhr: ${notificationPreferences.dhuhrNotificationEnabled}")
        android.util.Log.d("PrayerTimesScreen", "   🌇 Asr: ${notificationPreferences.asrNotificationEnabled}")
        android.util.Log.d("PrayerTimesScreen", "   🌆 Maghrib: ${notificationPreferences.maghribNotificationEnabled}")
        android.util.Log.d("PrayerTimesScreen", "   🌙 Isha: ${notificationPreferences.ishaNotificationEnabled}")
    }

    // Helper function to toggle prayer notification and update scheduled notifications
    val togglePrayerNotificationAndUpdate: (String, Boolean) -> Unit = { prayerName, enabled ->
        repository.togglePrayerNotification(prayerName, enabled)
        // Update scheduled notifications to reflect the change
        try {
            val serviceManager = dagger.hilt.android.EntryPointAccessors.fromApplication(
                screenContext.applicationContext,
                com.starception.submission.prayer.service.PrayerNotificationServiceManagerEntryPoint::class.java
            ).prayerNotificationServiceManager()
            serviceManager.updatePrayerNotifications()
            android.util.Log.i("PrayerTimesScreen", "🔔 NOTIFICATIONS UPDATED: $prayerName ${if (enabled) "enabled" else "disabled"}")
        } catch (e: Exception) {
            android.util.Log.e("PrayerTimesScreen", "❌ Failed to update notifications after toggle", e)
        }
    }

    // Fetch AI suggestions when prayer times are available
    LaunchedEffect(prayerTimes, calculationSettings) {
        if (prayerTimes != null) {
            kotlinx.coroutines.Dispatchers.IO.let { dispatcher ->
                kotlinx.coroutines.withContext(dispatcher) {
                    try {
                        android.util.Log.d("PrayerTimesScreen", "✨ Fetching AI suggestions for prayer times...")
                        suggestionRepository.fetchSuggestions(
                            ourCalculatedTimes = prayerTimes!!,
                            settings = com.starception.submission.prayer.model.PrayerSettings(
                                calculationMethod = calculationSettings.calculationMethod,
                                asrMadhhab = calculationSettings.asrMadhhab,
                                timeOffsets = storedOffsets
                            )
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("PrayerTimesScreen", "❌ Failed to fetch AI suggestions: ${e.message}")
                    }
                }
            }
        }
    }

    // Helper function to apply AI suggestion
    val applySuggestion: (String, Int) -> Unit = { prayerName, suggestedOffset ->
        kotlinx.coroutines.MainScope().launch {
            try {
                repository.updateSinglePrayerOffset(prayerName, suggestedOffset)
                android.util.Log.i("PrayerTimesScreen", "✨ AI suggestion applied: $prayerName → $suggestedOffset minutes")
            } catch (e: Exception) {
                android.util.Log.e("PrayerTimesScreen", "❌ Failed to apply suggestion: ${e.message}")
            }
        }
    }

    // Helper function to get AI suggestion for a specific prayer
    val getSuggestionFor: (String) -> com.starception.submission.prayer.model.PrayerTimeSuggestion? = { prayerName ->
        aiSuggestions.getSuggestionFor(prayerName)
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
            // Track visual start time — ensures minimum 3s hold for sync progress animation
            val visualStartTime = System.currentTimeMillis()
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
                    onSetSyncing(false)
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
                
                // Don't set isLoading = true here — PullToSyncContainer already shows
                // "Syncing your data" with a spinner. Setting isLoading replaces the
                // prayer tiles with a loading spinner, causing a visual blink.

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
                // Ensure minimum 3s visual hold for Fitbit-style sync progress animation
                val elapsed = System.currentTimeMillis() - visualStartTime
                val minVisualDuration = 3000L
                if (elapsed < minVisualDuration) {
                    android.util.Log.d("PullToRefresh", "⏳ Holding sync animation for ${minVisualDuration - elapsed}ms more (elapsed: ${elapsed}ms)")
                    delay(minVisualDuration - elapsed)
                }
                // Always reset loading and refresh states after visual hold
                isLoading = false
                onSetSyncing(false)
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
        notificationEnabled: Boolean = true,
        onNotificationToggle: (Boolean) -> Unit = {},
        modifier: Modifier = Modifier,
        onShowPopup: (String) -> Unit = {},
        suggestion: com.starception.submission.prayer.model.PrayerTimeSuggestion? = null,
        onApplySuggestion: ((String, Int) -> Unit)? = null,
        // iOS-style swipe-to-reveal state
        isRevealed: Boolean = false,
        onRevealChange: (Boolean) -> Unit = {}
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
        
        // Device-tilt parallax — shared sensor (one listener for all tiles). Each tile
        // gets a slightly different depth so the grid reads as layered, not a flat sheet.
        val tilt by com.starception.submission.feature.prayertimes.components.rememberParallaxTilt()
        val tileDepth = 0.7f + 0.3f * ((kotlin.math.abs(prayerName.hashCode()) % 100) / 100f)
        val parallaxMaxPx = with(LocalDensity.current) { 6.dp.toPx() } * tileDepth
        val parallaxShift = Offset(tilt.x * parallaxMaxPx, tilt.y * parallaxMaxPx)

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
                // Using Card with border instead of shadow - shadows cause navigation artifacts
                Card(
                    shape = CircleShape, // Make the card circular for the dial
                    colors = CardDefaults.cardColors(
                        containerColor = when (PrayerTimeHelpers.getPrayerStatus(prayerName, currentTime, prayerTimes)) {
                            "Current" -> MaterialTheme.colorScheme.tertiaryContainer
                            "Next" -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    border = BorderStroke(
                        2.dp,
                        when (PrayerTimeHelpers.getPrayerStatus(prayerName, currentTime, prayerTimes)) {
                            "Current" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                            "Next" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        }
                    ),
                    modifier = Modifier.fillMaxSize()
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

                                // CRITICAL: Update scheduled notifications with new prayer time
                                try {
                                    val appContext = screenContext.applicationContext
                                    val serviceManager = dagger.hilt.android.EntryPointAccessors.fromApplication(
                                        appContext,
                                        com.starception.submission.prayer.service.PrayerNotificationServiceManagerEntryPoint::class.java
                                    ).prayerNotificationServiceManager()
                                    serviceManager.updatePrayerNotifications()
                                    android.util.Log.i("PrayerTimesScreen", "🔔 NOTIFICATIONS UPDATED: Rescheduled with new $prayerName time")
                                } catch (e: Exception) {
                                    android.util.Log.e("PrayerTimesScreen", "❌ Failed to update notifications", e)
                                }

                                // Update UI on main thread
                                withContext(Dispatchers.Main) {
                                    // NO MANUAL UPDATE NEEDED - The repository flow automatically updates storedOffsets!
                                    // When we call repository.updateCalculationSettings() above, it triggers the flow
                                    // which causes calculationSettingsFlow.collectAsState() to recompose with new values
                                    android.util.Log.d("PrayerTimesScreen", "✅ Offset saved - repository flow will automatically update UI")
                                    android.util.Log.d("PrayerTimesScreen", "   💾 Saved $prayerName offset: $finalAdjustment minutes")
                                    android.util.Log.d("PrayerTimesScreen", "   🔄 Flow-based recomposition will trigger automatically")

                                    // Wait for swipe animation and tile scale animation to complete smoothly
                                    delay(250) // Give time for animations to finish

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
            // Show regular small card with iOS-style swipe-to-reveal actions
            // Swipe left to reveal +/- buttons for adjusting prayer time
            com.starception.submission.feature.prayertimes.components.SwipeToRevealCard(
                prayerName = prayerName,
                currentOffset = currentOffset,
                isRevealed = isRevealed,
                onRevealChange = { revealed ->
                    if (revealed) {
                        // Close any other revealed card and reveal this one
                        onRevealChange(true)
                    } else {
                        onRevealChange(false)
                    }
                },
                onOffsetChange = { newOffset ->
                    // Save the new offset to prayer settings
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val entryPoint = EntryPointAccessors.fromApplication(
                                screenContext.applicationContext,
                                com.starception.submission.feature.prayertimes.data.PrayerTimeCalculatorEntryPoint::class.java
                            )
                            val repository = entryPoint.prayerSettingsRepository()
                            repository.updateSinglePrayerOffset(prayerName, newOffset)
                            android.util.Log.d("PrayerCard", "⏱️ SWIPE ACTION: $prayerName offset -> $newOffset minutes")
                        } catch (e: Exception) {
                            android.util.Log.e("PrayerCard", "❌ Failed to save offset adjustment", e)
                        }
                    }
                },
                onResetOffset = {
                    // Reset to auto-detected default offset
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val entryPoint = EntryPointAccessors.fromApplication(
                                screenContext.applicationContext,
                                com.starception.submission.feature.prayertimes.data.PrayerTimeCalculatorEntryPoint::class.java
                            )
                            val repository = entryPoint.prayerSettingsRepository()
                            val defaultOffset = repository.getDefaultPrayerOffset(prayerName)
                            repository.updateSinglePrayerOffset(prayerName, defaultOffset)
                            android.util.Log.d("PrayerCard", "🔄 RESET: $prayerName offset -> $defaultOffset minutes (default)")
                        } catch (e: Exception) {
                            android.util.Log.e("PrayerCard", "❌ Failed to reset offset", e)
                        }
                    }
                },
                modifier = modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = parallaxShift.x
                        translationY = parallaxShift.y
                    }
            ) {
                // Card content inside SwipeToRevealCard with crisp border definition
                val prayerStatus = PrayerTimeHelpers.getPrayerStatus(prayerName, currentTime, prayerTimes)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = when (prayerStatus) {
                        "Current" -> MaterialTheme.colorScheme.tertiaryContainer
                        "Next" -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = BorderStroke(
                        1.dp,
                        when (prayerStatus) {
                            "Current" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                            "Next" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }
                    ),
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(prayerName, currentOffset) {
                            detectTapGestures(
                                onDoubleTap = {
                                    android.util.Log.d("PrayerCard", "👆👆 DOUBLE TAP detected on $prayerName card!")
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                                    // Reset to auto-detected default offset
                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            val entryPoint = EntryPointAccessors.fromApplication(
                                                screenContext.applicationContext,
                                                com.starception.submission.feature.prayertimes.data.PrayerTimeCalculatorEntryPoint::class.java
                                            )
                                            val repository = entryPoint.prayerSettingsRepository()
                                            val defaultOffset = repository.getDefaultPrayerOffset(prayerName)

                                            if (currentOffset != defaultOffset) {
                                                repository.updateSinglePrayerOffset(prayerName, defaultOffset)
                                                android.util.Log.d("PrayerCard", "✅ RESET: $prayerName offset -> $defaultOffset minutes (default)")
                                            } else {
                                                android.util.Log.d("PrayerCard", "ℹ️ $prayerName already at default offset: $defaultOffset")
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("PrayerCard", "❌ Failed to reset offset", e)
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    // Card content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top section: Prayer names with notification bell
                        Column(
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Prayer name with notification bell icon
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = getPrayerDisplayName(prayerName),
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                    color = when (PrayerTimeHelpers.getPrayerStatus(prayerName, currentTime, prayerTimes)) {
                                        "Current" -> MaterialTheme.colorScheme.onTertiaryContainer
                                        "Next" -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )

                                // Notification bell toggle icon (only for main 5 prayers)
                                if (prayerName != "Sunrise") {
                                    IconButton(
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onNotificationToggle(!notificationEnabled)
                                            android.util.Log.d("PrayerCard", "🔔 Notification toggled for $prayerName: ${!notificationEnabled}")
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (notificationEnabled) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                                            contentDescription = if (notificationEnabled) "Notifications enabled" else "Notifications disabled",
                                            tint = when (PrayerTimeHelpers.getPrayerStatus(prayerName, currentTime, prayerTimes)) {
                                                "Current" -> MaterialTheme.colorScheme.onTertiaryContainer
                                                "Next" -> MaterialTheme.colorScheme.onPrimaryContainer
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }.copy(alpha = if (notificationEnabled) 1f else 0.5f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = getPrayerNameInLocalLanguage(prayerName, prayerTimes?.location?.countryCode),
                                // The Arabic fonts carry very tall ascent/descent, so an
                                // unconstrained line box here eats the 112dp expanded tile's
                                // height budget and the time row below gets clipped. Pin the
                                // line box like the time text does.
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = getSelectedArabicFontFamily(screenContext),
                                    fontSize = 16.sp,
                                    letterSpacing = 0.4.sp,
                                    lineHeight = 22.sp,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                ),
                                color = when (PrayerTimeHelpers.getPrayerStatus(prayerName, currentTime, prayerTimes)) {
                                    "Current" -> MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                    "Next" -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                },
                                fontWeight = FontWeight.Normal,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Bottom section: Time display with separated AM/PM
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            // Calculate adjusted time
                            val originalTime = when (prayerName) {
                                "Fajr" -> prayerTimes?.fajr
                                "Sunrise" -> prayerTimes?.sunrise
                                "Dhuhr" -> prayerTimes?.dhuhr
                                "Asr" -> prayerTimes?.asr
                                "Maghrib" -> prayerTimes?.maghrib
                                "Isha" -> prayerTimes?.isha
                                else -> null
                            }

                            val (timeOnly, amPm) = originalTime?.let { time ->
                                val adjustedDateTime = java.time.LocalDateTime.of(java.time.LocalDate.now(), time).plusMinutes(currentOffset.toLong())
                                val adjusted = adjustedDateTime.toLocalTime()
                                val hour12 = if (adjusted.hour == 0) 12
                                            else if (adjusted.hour > 12) adjusted.hour - 12
                                            else adjusted.hour
                                val period = if (adjusted.hour < 12) "AM" else "PM"
                                Pair(String.format("%d:%02d", hour12, adjusted.minute), period)
                            } ?: Pair("", "")

                            // Time display - NO direct gestures, use swipe-to-reveal instead
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val baseColor = when (PrayerTimeHelpers.getPrayerStatus(prayerName, currentTime, prayerTimes)) {
                                    "Current" -> MaterialTheme.colorScheme.tertiary
                                    "Next" -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }

                                // Left side: Time + AM/PM grouped together
                                Row(
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    // Time (hour:minute). headlineMedium's default ~36sp
                                    // line box PLUS the extra font padding overflows the
                                    // shorter expanded tile and clips the time. Drop the
                                    // font padding and use a snug (but glyph-safe) line box
                                    // so it stays fully visible without taller tiles.
                                    Text(
                                        text = timeOnly,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontSize = 24.sp,
                                            lineHeight = 30.sp,
                                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        ),
                                        color = baseColor,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // AM/PM (smaller)
                                    Text(
                                        text = amPm,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                        color = baseColor.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }

                                // Right side: Offset indicator with AI suggestion alternation
                                com.starception.submission.feature.prayertimes.components.AiSuggestionBadge(
                                    currentOffset = currentOffset,
                                    suggestion = suggestion,
                                    baseColor = baseColor,
                                    onApplySuggestion = if (onApplySuggestion != null) {
                                        { suggestedOffset -> onApplySuggestion(prayerName, suggestedOffset) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // PRAYER TIMES CALCULATION ENGINE - Background calculation with 3-second location timeout
    val calculatePrayerTimes: suspend (forceGpsRefresh: Boolean) -> Unit = { forceGpsRefresh ->
        try {
            // Run calculation on background thread to prevent UI blocking
            withContext(Dispatchers.Default) {
                val calculator = PrayerTimesCalculator(screenContext)
                // This uses our improved 3-second timeout system
                val result = calculator.calculateDefaultPrayerTimes(forceGpsRefresh = forceGpsRefresh)

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
        
        // STEP 1: Skip redundant cache loading - already done in remember{} block
        // The initial state was set from SharedPreferences cache which persists across app restarts
        // We only need to refresh with GPS if we don't already have valid data
        android.util.Log.d("PrayerScreen", "STEP 1: Checking if cached data needs refresh...")
        val hasValidCachedData = prayerTimes != null && !location.contains("Default")
        if (hasValidCachedData) {
            android.util.Log.d("PrayerScreen", "✓ Already loaded from persistent cache: $location")
            android.util.Log.d("PrayerScreen", "  Skipping redundant in-memory cache check")
        } else {
            android.util.Log.d("PrayerScreen", "No valid cached data - first time use or cache expired")
            isLoading = true  // Only show loading for brand new users
        }
        
        // STEP 2: Update with fresh GPS data in background (auto-refresh on app open)
        android.util.Log.d("PrayerScreen", "STEP 2: Starting background GPS update with fresh location...")
        calculatePrayerTimes(true)  // forceGpsRefresh = true for auto-refresh on app open
        android.util.Log.d("PrayerScreen", "Background update completed with fresh GPS location")
        
        // Note: calculatePrayerTimes() now handles turning off isLoading
    }
    
    // PERMISSION CHANGE HANDLER - Update data when permissions change
    LaunchedEffect(locationPermissionState.status) {
        android.util.Log.d("PrayerScreen", "Permission status changed, running background update...")
        // Update in background with fresh GPS - calculatePrayerTimes() handles loading state
        calculatePrayerTimes(true)  // forceGpsRefresh = true when permission changes
    }
    


    // Animate blur/dim for smooth Control Center transitions
    val controlCenterProgress by animateFloatAsState(
        targetValue = if (popupDialState != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "controlCenterProgress"
    )

    // Use PullToSyncContainer component wrapped in Box for Control Center overlay
    Box(modifier = modifier.fillMaxSize()) {
        // Main content - wrapped in Box with layerBackdrop to capture for Control Center glass effect
        // Blur and dim animate smoothly based on Control Center progress
        // CRITICAL: Check popupDialState != null to immediately stop effects on dismiss (prevents lingering shadow on navigation)
        val isPopupActive = popupDialState != null
        Box(
            modifier = Modifier
                .fillMaxSize()
                // CRITICAL: Only apply ALL layer effects when popup is active to prevent visual artifacts during navigation
                .then(
                    if (isPopupActive) {
                        Modifier
                            .layerBackdrop(controlCenterBackdrop)
                            .graphicsLayer {
                                if (controlCenterProgress > 0f) {
                                    val blurRadius = 4f.dp.toPx() * controlCenterProgress
                                    renderEffect = androidx.compose.ui.graphics.BlurEffect(blurRadius, blurRadius)
                                }
                            }
                            .drawWithContent {
                                drawContent()
                                if (controlCenterProgress > 0f) {
                                    drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f * controlCenterProgress))
                                }
                            }
                    } else {
                        Modifier
                    }
                )
        ) {
            // Single source of truth: Home renders the SAME VM-produced prayer-alert state
            // (prayerAlertOverride = mainViewModel.prayerAlertState) that every other page
            // renders, so Home and other pages are always in sync. The VM's app-wide ticker is
            // the sole producer (it reads the same cached prayer times Home's calculator writes),
            // so Home no longer computes or pushes its own banner.
            val prayerAlertState = prayerAlertOverride

            val silentModeState by com.starception.submission.feature.prayertimes.wobble.rememberSilentModeState()
            val islamicEventStateProvider = remember {
                EntryPointAccessors.fromApplication(
                    screenContext.applicationContext,
                    PrayerTimeCalculatorEntryPoint::class.java
                ).islamicEventStateProvider()
            }
            val islamicEventState by islamicEventStateProvider.state.collectAsStateWithLifecycle()
            PullToSyncContainer(
                isRefreshing = isRefreshing,
                onRefresh = { onSetSyncing(true) },
                downloadProgress = downloadProgress,
                downloadLabel = downloadLabel,
                mediaState = mediaState,
                onMediaAction = onMediaAction,
                isTtsPreparing = isTtsPreparing,
                onMediaTitleClick = {
                    val source = mediaState.playback.source
                    if (onMediaSourceClick != null) {
                        onMediaSourceClick.invoke(source)
                    } else {
                        (source as? com.starception.submission.media.MediaSource.Quran)
                            ?.let { onSurahClick(it.surahIndex + 1) }
                    }
                },
                prayerAlertState = prayerAlertState,
                silentModeState = silentModeState,
                islamicEventState = islamicEventState,
                onIslamicEventClick = { event ->
                    com.starception.submission.ui.search.SearchPrefillBus.requestSearch(event.searchQuery)
                },
                modifier = Modifier.fillMaxSize()
            ) { syncState ->
            val outerConfiguration = LocalConfiguration.current
            val outerIsLandscape = outerConfiguration.orientation == Configuration.ORIENTATION_LANDSCAPE
            // Dynamic top inset: full at rest, collapses during pull (Fitbit-style)
            val statusBarInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                .asPaddingValues().calculateTopPadding()
            val dynamicTopInset = statusBarInset * (1f - (syncState.wobbleIntensity * 2f).coerceAtMost(1f))
            com.starception.submission.ui.AppTopSearchBar(
                title = stringResource(R.string.prayer_times_title),
                onSettingsClick = onSettingsClick,
                topInset = dynamicTopInset,
                onVerseClick = onSurahClickWithAyah,
                onSearchSubmit = onSearchSubmit,
            ) {
            // Apply syncState.pullModifier here so the inner ComposeView's scrollable
            // feeds the outer PullToSyncContainer's NestedScrollConnection. Without
            // this the View↔Compose boundary swallows the drag events.
            Column(modifier = Modifier
                .fillMaxSize()
                .then(syncState.pullModifier)
                .then(if (outerIsLandscape) Modifier else Modifier.verticalScroll(rememberScrollState()))) {
            // Pull-to-refresh indicator is handled by PullToSyncContainer in the sage background
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
            // Use syncState.wobbleIntensity from syncState

            // Detect orientation for adaptive layout
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            if (isLandscape) {
                // LANDSCAPE LAYOUT: Side-by-side with swipeable tiles on left, prayer cards on right
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        // Landscape doesn't scroll, so the gesture-nav bar inset must be
                        // reserved here or the location card / prayer grid sit under it.
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left column: Swipeable tiles + Location info
                    Column(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Swipeable Big Tiles - take most of the height
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
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
                                timeOffsets = storedOffsets,
                                isLandscape = true,
                                onSurahClick = onSurahClick,
                                onSurahClickWithAyah = onSurahClickWithAyah,
                                goToMosqueDurationMinutes = { name -> notificationPreferences.getGoToMosqueDurationForPrayer(name) },
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Location info at bottom - larger for better visibility
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shadowElevation = 0.dp  // Removed to prevent navigation artifacts
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = "Location",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = getLocationWithCountryCode(location, prayerTimes?.location),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    textAlign = TextAlign.Start,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Right column: Prayer cards in scrollable column
                    Column(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = FloatingNavClearance),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // No instruction banner in landscape to save space

                        // Get ordered prayers
                        val orderedPrayers = remember(currentTime, prayerTimes) {
                            val result = mutableListOf<String>()
                            val allPrayersList = prayerTimes?.let { times ->
                                listOf(
                                    "Fajr" to times.fajr, "Sunrise" to times.sunrise,
                                    "Dhuhr" to times.dhuhr, "Asr" to times.asr,
                                    "Maghrib" to times.maghrib, "Isha" to times.isha
                                )
                            } ?: emptyList()
                            if (allPrayersList.isNotEmpty()) {
                                val currentPrayerIndex = allPrayersList.indexOfLast { it.second.isBefore(currentTime) || it.second == currentTime }
                                if (currentPrayerIndex != -1) {
                                    for (i in 0 until 6) {
                                        val index = (currentPrayerIndex + i) % allPrayersList.size
                                        result.add(allPrayersList[index].first)
                                    }
                                } else {
                                    result.addAll(allPrayersList.map { it.first })
                                }
                            }
                            result
                        }

                        // Prayer cards in a 2-column grid for landscape
                        // Use appropriate height to show prayer time info (name + arabic + time)
                        val landscapeTileHeight = 115.dp
                        for (i in orderedPrayers.indices step 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // First card in row
                                InteractivePrayerCard(
                                    prayerName = orderedPrayers[i],
                                    currentEditingTile = currentEditingTile,
                                    onEditingTileChange = { currentEditingTile = it },
                                    currentOffset = when (orderedPrayers[i]) {
                                        "Fajr" -> storedOffsets.fajr
                                        "Sunrise" -> storedOffsets.sunrise
                                        "Dhuhr" -> storedOffsets.dhuhr
                                        "Asr" -> storedOffsets.asr
                                        "Maghrib" -> storedOffsets.maghrib
                                        "Isha" -> storedOffsets.isha
                                        else -> 0
                                    },
                                    notificationEnabled = when (orderedPrayers[i]) {
                                        "Fajr" -> notificationPreferences.fajrNotificationEnabled
                                        "Dhuhr" -> notificationPreferences.dhuhrNotificationEnabled
                                        "Asr" -> notificationPreferences.asrNotificationEnabled
                                        "Maghrib" -> notificationPreferences.maghribNotificationEnabled
                                        "Isha" -> notificationPreferences.ishaNotificationEnabled
                                        else -> true
                                    },
                                    onNotificationToggle = { enabled ->
                                        togglePrayerNotificationAndUpdate(orderedPrayers[i], enabled)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(landscapeTileHeight),
                                    onShowPopup = { prayerName -> popupDialState = prayerName },
                                    suggestion = getSuggestionFor(orderedPrayers[i]),
                                    onApplySuggestion = applySuggestion,
                                    isRevealed = revealedPrayerCard == orderedPrayers[i],
                                    onRevealChange = { revealed -> revealedPrayerCard = if (revealed) orderedPrayers[i] else null }
                                )

                                // Second card in row (if exists)
                                if (i + 1 < orderedPrayers.size) {
                                    InteractivePrayerCard(
                                        prayerName = orderedPrayers[i + 1],
                                        currentEditingTile = currentEditingTile,
                                        onEditingTileChange = { currentEditingTile = it },
                                        currentOffset = when (orderedPrayers[i + 1]) {
                                            "Fajr" -> storedOffsets.fajr
                                            "Sunrise" -> storedOffsets.sunrise
                                            "Dhuhr" -> storedOffsets.dhuhr
                                            "Asr" -> storedOffsets.asr
                                            "Maghrib" -> storedOffsets.maghrib
                                            "Isha" -> storedOffsets.isha
                                            else -> 0
                                        },
                                        notificationEnabled = when (orderedPrayers[i + 1]) {
                                            "Fajr" -> notificationPreferences.fajrNotificationEnabled
                                            "Dhuhr" -> notificationPreferences.dhuhrNotificationEnabled
                                            "Asr" -> notificationPreferences.asrNotificationEnabled
                                            "Maghrib" -> notificationPreferences.maghribNotificationEnabled
                                            "Isha" -> notificationPreferences.ishaNotificationEnabled
                                            else -> true
                                        },
                                        onNotificationToggle = { enabled ->
                                            togglePrayerNotificationAndUpdate(orderedPrayers[i + 1], enabled)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(landscapeTileHeight),
                                        onShowPopup = { prayerName -> popupDialState = prayerName },
                                        suggestion = getSuggestionFor(orderedPrayers[i + 1]),
                                        onApplySuggestion = applySuggestion,
                                        isRevealed = revealedPrayerCard == orderedPrayers[i + 1],
                                        onRevealChange = { revealed -> revealedPrayerCard = if (revealed) orderedPrayers[i + 1] else null }
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
            // PORTRAIT LAYOUT: Original vertical layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(
                        top = 8.dp,
                        bottom = 0.dp
                    ),
                verticalArrangement = Arrangement.Top
            ) {


                
                // Swipeable Big Tiles
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Let the tile deck BLEED 9dp into each side margin
                        // (24dp screen padding → 15dp effective for the deck)
                        // so the cards read wider than the rest of the column.
                        .layout { measurable, constraints ->
                            val extra = 18.dp.roundToPx()
                            val placeable = measurable.measure(
                                constraints.copy(maxWidth = constraints.maxWidth + extra),
                            )
                            layout(placeable.width - extra, placeable.height) {
                                placeable.place(-extra / 2, 0)
                            }
                        }
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
                    timeOffsets = storedOffsets,
                    onSurahClick = onSurahClick,
                    onSurahClickWithAyah = onSurahClickWithAyah,
                    goToMosqueDurationMinutes = { name -> notificationPreferences.getGoToMosqueDurationForPrayer(name) },
                )
                }

                // Spacer between swipeable tiles and adjust prayer times info card
                Spacer(modifier = Modifier.height(10.dp))

                // Instruction banner for prayer time adjustment
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
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
                            imageVector = Icons.Outlined.Tune,
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
                                text = "← Swipe left to adjust · Swipe right to reset →",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // Spacer between info card and prayer tiles (reduced from 8dp to 6dp)
                Spacer(modifier = Modifier.height(6.dp))

                // Expandable prayer layout - smart default view with expand option
                var showAllPrayers by remember { mutableStateOf(false) }

                // Material 3 expressive tile height animation with spring physics.
                // Expanded tiles compress a bit further (was 120dp) so the third
                // row + Show Less button land just above the floating nav pill
                // without the page having to scroll.
                val tileHeight by animateDpAsState(
                    // Collapsed tiles are a touch shorter (was 140) so the whole
                    // collapsed dashboard fits one screen with the location card
                    // clearing the floating nav bar — no scrolling needed. Expanded
                    // The visible card needs ~110dp inside its 2dp vertical margins:
                    // any less and the name row + Arabic line + 24sp time clip the
                    // digits, any more and the third row pushes "Show Less" into the
                    // floating nav pill. 114 outer − 4 margin = 110 card.
                    targetValue = if (showAllPrayers) 114.dp else 134.dp,
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
                    animationSpec = tween(
                        durationMillis = 600,
                        delayMillis = 80,
                        easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
                    ),
                    label = "sunriseCardAnimation"
                )
                
                // Get next 6 prayers in circular chronological order
                // All 6 items (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha) are included
                // Shows next 4 when collapsed, next 6 when expanded
                val orderedPrayers = remember(currentTime, prayerTimes) {
                    val result = mutableListOf<String>()

                    // Define all 6 prayers in chronological order
                    val allPrayersList = prayerTimes?.let { times ->
                        listOf(
                            "Fajr" to times.fajr,
                            "Sunrise" to times.sunrise,
                            "Dhuhr" to times.dhuhr,
                            "Asr" to times.asr,
                            "Maghrib" to times.maghrib,
                            "Isha" to times.isha
                        )
                    } ?: emptyList()

                    if (allPrayersList.isNotEmpty()) {
                        // Find the current prayer (last prayer that has passed)
                        val currentPrayerIndex = allPrayersList
                            .indexOfLast { it.second.isBefore(currentTime) || it.second == currentTime }

                        if (currentPrayerIndex != -1) {
                            // Found current prayer - start from there and show next 6 prayers in circular order
                            for (i in 0 until 6) {
                                val index = (currentPrayerIndex + i) % allPrayersList.size
                                result.add(allPrayersList[index].first)
                            }
                        } else {
                            // No prayer has passed yet (very early morning before Fajr) - start from Fajr
                            result.addAll(allPrayersList.map { it.first })
                        }
                    }

                    result
                }

                // First row: First 2 prayers from ordered list (most relevant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // First prayer tile
                    if (orderedPrayers.isNotEmpty()) {
                        InteractivePrayerCard(
                            prayerName = orderedPrayers[0],
                            currentEditingTile = currentEditingTile,
                            onEditingTileChange = { currentEditingTile = it },
                            currentOffset = when (orderedPrayers[0]) {
                                "Fajr" -> storedOffsets.fajr
                                "Sunrise" -> storedOffsets.sunrise
                                "Dhuhr" -> storedOffsets.dhuhr
                                "Asr" -> storedOffsets.asr
                                "Maghrib" -> storedOffsets.maghrib
                                "Isha" -> storedOffsets.isha
                                else -> 0
                            },
                            notificationEnabled = when (orderedPrayers[0]) {
                                "Fajr" -> notificationPreferences.fajrNotificationEnabled
                                "Dhuhr" -> notificationPreferences.dhuhrNotificationEnabled
                                "Asr" -> notificationPreferences.asrNotificationEnabled
                                "Maghrib" -> notificationPreferences.maghribNotificationEnabled
                                "Isha" -> notificationPreferences.ishaNotificationEnabled
                                else -> true
                            },
                            onNotificationToggle = { enabled ->
                                togglePrayerNotificationAndUpdate(orderedPrayers[0], enabled)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(tileHeight)
                                .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                            onShowPopup = { prayerName ->
                                android.util.Log.d("PrayerCard", "🚀 onShowPopup called with $prayerName")
                                popupDialState = prayerName
                                android.util.Log.d("PrayerCard", "✅ Set popupDialState to $prayerName")
                            },
                            suggestion = getSuggestionFor(orderedPrayers[0]),
                            onApplySuggestion = applySuggestion,
                            isRevealed = revealedPrayerCard == orderedPrayers[0],
                            onRevealChange = { revealed -> revealedPrayerCard = if (revealed) orderedPrayers[0] else null }
                        )
                    }

                    // Second prayer tile
                    if (orderedPrayers.size > 1) {
                        InteractivePrayerCard(
                            prayerName = orderedPrayers[1],
                            currentEditingTile = currentEditingTile,
                            onEditingTileChange = { currentEditingTile = it },
                            currentOffset = when (orderedPrayers[1]) {
                                "Fajr" -> storedOffsets.fajr
                                "Sunrise" -> storedOffsets.sunrise
                                "Dhuhr" -> storedOffsets.dhuhr
                                "Asr" -> storedOffsets.asr
                                "Maghrib" -> storedOffsets.maghrib
                                "Isha" -> storedOffsets.isha
                                else -> 0
                            },
                            notificationEnabled = when (orderedPrayers[1]) {
                                "Fajr" -> notificationPreferences.fajrNotificationEnabled
                                "Dhuhr" -> notificationPreferences.dhuhrNotificationEnabled
                                "Asr" -> notificationPreferences.asrNotificationEnabled
                                "Maghrib" -> notificationPreferences.maghribNotificationEnabled
                                "Isha" -> notificationPreferences.ishaNotificationEnabled
                                else -> true
                            },
                            onNotificationToggle = { enabled ->
                                togglePrayerNotificationAndUpdate(orderedPrayers[1], enabled)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(tileHeight)
                                .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                            onShowPopup = { prayerName ->
                                android.util.Log.d("PrayerCard", "🚀 onShowPopup called with $prayerName")
                                popupDialState = prayerName
                                android.util.Log.d("PrayerCard", "✅ Set popupDialState to $prayerName")
                            },
                            suggestion = getSuggestionFor(orderedPrayers[1]),
                            onApplySuggestion = applySuggestion,
                            isRevealed = revealedPrayerCard == orderedPrayers[1],
                            onRevealChange = { revealed -> revealedPrayerCard = if (revealed) orderedPrayers[1] else null }
                        )
                    }
                }

                // Second row: Remaining 2 prayers from ordered list
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Third prayer tile
                    if (orderedPrayers.size > 2) {
                        InteractivePrayerCard(
                            prayerName = orderedPrayers[2],
                            currentEditingTile = currentEditingTile,
                            onEditingTileChange = { currentEditingTile = it },
                            currentOffset = when (orderedPrayers[2]) {
                                "Fajr" -> storedOffsets.fajr
                                "Sunrise" -> storedOffsets.sunrise
                                "Dhuhr" -> storedOffsets.dhuhr
                                "Asr" -> storedOffsets.asr
                                "Maghrib" -> storedOffsets.maghrib
                                "Isha" -> storedOffsets.isha
                                else -> 0
                            },
                            notificationEnabled = when (orderedPrayers[2]) {
                                "Fajr" -> notificationPreferences.fajrNotificationEnabled
                                "Dhuhr" -> notificationPreferences.dhuhrNotificationEnabled
                                "Asr" -> notificationPreferences.asrNotificationEnabled
                                "Maghrib" -> notificationPreferences.maghribNotificationEnabled
                                "Isha" -> notificationPreferences.ishaNotificationEnabled
                                else -> true
                            },
                            onNotificationToggle = { enabled ->
                                togglePrayerNotificationAndUpdate(orderedPrayers[2], enabled)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(tileHeight)
                                .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                            onShowPopup = { prayerName ->
                                android.util.Log.d("PrayerCard", "🚀 onShowPopup called with $prayerName")
                                popupDialState = prayerName
                                android.util.Log.d("PrayerCard", "✅ Set popupDialState to $prayerName")
                            },
                            suggestion = getSuggestionFor(orderedPrayers[2]),
                            onApplySuggestion = applySuggestion,
                            isRevealed = revealedPrayerCard == orderedPrayers[2],
                            onRevealChange = { revealed -> revealedPrayerCard = if (revealed) orderedPrayers[2] else null }
                        )
                    }

                    // Fourth prayer tile
                    if (orderedPrayers.size > 3) {
                        InteractivePrayerCard(
                            prayerName = orderedPrayers[3],
                            currentEditingTile = currentEditingTile,
                            onEditingTileChange = { currentEditingTile = it },
                            currentOffset = when (orderedPrayers[3]) {
                                "Fajr" -> storedOffsets.fajr
                                "Sunrise" -> storedOffsets.sunrise
                                "Dhuhr" -> storedOffsets.dhuhr
                                "Asr" -> storedOffsets.asr
                                "Maghrib" -> storedOffsets.maghrib
                                "Isha" -> storedOffsets.isha
                                else -> 0
                            },
                            notificationEnabled = when (orderedPrayers[3]) {
                                "Fajr" -> notificationPreferences.fajrNotificationEnabled
                                "Dhuhr" -> notificationPreferences.dhuhrNotificationEnabled
                                "Asr" -> notificationPreferences.asrNotificationEnabled
                                "Maghrib" -> notificationPreferences.maghribNotificationEnabled
                                "Isha" -> notificationPreferences.ishaNotificationEnabled
                                else -> true
                            },
                            onNotificationToggle = { enabled ->
                                togglePrayerNotificationAndUpdate(orderedPrayers[3], enabled)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(tileHeight)
                                .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                            onShowPopup = { prayerName ->
                                android.util.Log.d("PrayerCard", "🚀 onShowPopup called with $prayerName")
                                popupDialState = prayerName
                                android.util.Log.d("PrayerCard", "✅ Set popupDialState to $prayerName")
                            },
                            suggestion = getSuggestionFor(orderedPrayers[3]),
                            onApplySuggestion = applySuggestion,
                            isRevealed = revealedPrayerCard == orderedPrayers[3],
                            onRevealChange = { revealed -> revealedPrayerCard = if (revealed) orderedPrayers[3] else null }
                        )
                    }
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
                        // Fifth prayer with staggered entrance animation
                        if (orderedPrayers.size > 4) {
                            InteractivePrayerCard(
                                prayerName = orderedPrayers[4],
                                currentEditingTile = currentEditingTile,
                                onEditingTileChange = { currentEditingTile = it },
                                currentOffset = when (orderedPrayers[4]) {
                                    "Fajr" -> storedOffsets.fajr
                                    "Sunrise" -> storedOffsets.sunrise
                                    "Dhuhr" -> storedOffsets.dhuhr
                                    "Asr" -> storedOffsets.asr
                                    "Maghrib" -> storedOffsets.maghrib
                                    "Isha" -> storedOffsets.isha
                                    else -> 0
                                },
                                notificationEnabled = when (orderedPrayers[4]) {
                                    "Fajr" -> notificationPreferences.fajrNotificationEnabled
                                    "Dhuhr" -> notificationPreferences.dhuhrNotificationEnabled
                                    "Asr" -> notificationPreferences.asrNotificationEnabled
                                    "Maghrib" -> notificationPreferences.maghribNotificationEnabled
                                    "Isha" -> notificationPreferences.ishaNotificationEnabled
                                    else -> true
                                },
                                onNotificationToggle = { enabled ->
                                    togglePrayerNotificationAndUpdate(orderedPrayers[4], enabled)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(tileHeight)
                                    .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 2.dp)
                                    .graphicsLayer {
                                        // Material 3 expressive card entrance with bounce and scale
                                        translationY = (1f - fajrAnimProgress) * 24f
                                        scaleX = 0.85f + (fajrAnimProgress * 0.15f)
                                        scaleY = 0.85f + (fajrAnimProgress * 0.15f)
                                        alpha = fajrAnimProgress
                                    },
                                onShowPopup = { prayerName ->
                                    android.util.Log.d("PrayerCard", "🚀 onShowPopup called with $prayerName")
                                    popupDialState = prayerName
                                    android.util.Log.d("PrayerCard", "✅ Set popupDialState to $prayerName")
                                },
                                suggestion = getSuggestionFor(orderedPrayers[4]),
                                onApplySuggestion = applySuggestion,
                                isRevealed = revealedPrayerCard == orderedPrayers[4],
                                onRevealChange = { revealed -> revealedPrayerCard = if (revealed) orderedPrayers[4] else null }
                            )
                        }

                        // Sixth prayer with staggered entrance animation
                        if (orderedPrayers.size > 5) {
                            InteractivePrayerCard(
                                prayerName = orderedPrayers[5],
                                currentEditingTile = currentEditingTile,
                                onEditingTileChange = { currentEditingTile = it },
                                currentOffset = when (orderedPrayers[5]) {
                                    "Fajr" -> storedOffsets.fajr
                                    "Sunrise" -> storedOffsets.sunrise
                                    "Dhuhr" -> storedOffsets.dhuhr
                                    "Asr" -> storedOffsets.asr
                                    "Maghrib" -> storedOffsets.maghrib
                                    "Isha" -> storedOffsets.isha
                                    else -> 0
                                },
                                notificationEnabled = when (orderedPrayers[5]) {
                                    "Fajr" -> notificationPreferences.fajrNotificationEnabled
                                    "Dhuhr" -> notificationPreferences.dhuhrNotificationEnabled
                                    "Asr" -> notificationPreferences.asrNotificationEnabled
                                    "Maghrib" -> notificationPreferences.maghribNotificationEnabled
                                    "Isha" -> notificationPreferences.ishaNotificationEnabled
                                    else -> true
                                },
                                onNotificationToggle = { enabled ->
                                    togglePrayerNotificationAndUpdate(orderedPrayers[5], enabled)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(tileHeight)
                                    .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 2.dp)
                                    .graphicsLayer {
                                        // Material 3 expressive card entrance with staggered timing
                                        translationY = (1f - sunriseAnimProgress) * 32f
                                        scaleX = 0.82f + (sunriseAnimProgress * 0.18f)
                                        scaleY = 0.82f + (sunriseAnimProgress * 0.18f)
                                        alpha = sunriseAnimProgress
                                    },
                                onShowPopup = { prayerName ->
                                    android.util.Log.d("PrayerCard", "🚀 onShowPopup called with $prayerName")
                                    popupDialState = prayerName
                                    android.util.Log.d("PrayerCard", "✅ Set popupDialState to $prayerName")
                                },
                                suggestion = getSuggestionFor(orderedPrayers[5]),
                                onApplySuggestion = applySuggestion,
                                isRevealed = revealedPrayerCard == orderedPrayers[5],
                                onRevealChange = { revealed -> revealedPrayerCard = if (revealed) orderedPrayers[5] else null }
                            )
                        }
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
                        .padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 0.dp)
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

                // Spacer between Show Less/Show All button and location card (4dp to match spacing above for symmetry)
                Spacer(modifier = Modifier.height(0.dp))

                // Location info using Material 3 Expressive Design - symmetric rounded shape
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shadowElevation = 0.dp  // Removed to prevent navigation artifacts
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = "Location",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Get location text
                        val locationText = getLocationWithCountryCode(location, prayerTimes?.location)

                        // Check if location text contains Arabic (Unicode range 0600-06FF)
                        val containsArabic = locationText.any { it in '\u0600'..'\u06FF' }

                        // Get selected Arabic font if location contains Arabic
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val arabicFontFamily = if (containsArabic) {
                            val prefs = context.getSharedPreferences("quran_prefs", android.content.Context.MODE_PRIVATE)
                            val selectedFont = prefs.getString("arabic_font", "pdms_saleem") ?: "pdms_saleem"
                            when (selectedFont) {
                                "pdms_saleem" -> QuranFonts.PDMSSaleem
                                "noor_e_hidayat" -> QuranFonts.NoorEHidayat
                                "thabit" -> QuranFonts.Thabit
                                "uthmani_script" -> QuranFonts.UthmanicScript
                                "indopak_script" -> QuranFonts.IndoPakScript
                                else -> QuranFonts.PDMSSaleem
                            }
                        } else {
                            null
                        }

                        Text(
                            text = locationText,
                            style = if (containsArabic && arabicFontFamily != null) {
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = arabicFontFamily,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
                                )
                            } else {
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Start,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(FloatingNavClearance))
            }
            } // End of portrait layout else block
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
            },
            userLatitude = prayerTimes?.location?.latitude ?: 0.0,
            userLongitude = prayerTimes?.location?.longitude ?: 0.0,
            showGlobe = true
        )
    }

        } // Close Column inside AppTopSearchBar content lambda
        } // Close AppTopSearchBar scaffold (content lambda)
        } // Close PullToSyncContainer lambda
        } // Close Box with layerBackdrop

        // INTERACTIVE PRAYER DIAL POPUP - Control Center overlay (OUTSIDE PullToSyncContainer, inside Box)
        // Debug logging for popup state
        LaunchedEffect(popupDialState) {
            android.util.Log.w("PrayerDialPopup", "🎯 popupDialState changed to: $popupDialState")
            android.util.Log.w("PrayerDialPopup", "   Visible = ${popupDialState != null}")
        }

        // Control Center overlay - renders on top of PullToSyncContainer content
        if (popupDialState != null) {
            val safePrayerName = popupDialState ?: "Dhuhr"
            Log.d("PrayerTimes", "Control Center active for $safePrayerName")

            val prayerTimeDisplay = when (safePrayerName) {
                "Fajr" -> prayerTimes?.fajr ?: LocalTime.of(5, 23)
                "Sunrise" -> prayerTimes?.sunrise ?: LocalTime.of(6, 42)
                "Dhuhr" -> prayerTimes?.dhuhr ?: LocalTime.of(12, 0)
                "Asr" -> prayerTimes?.asr ?: LocalTime.of(15, 46)
                "Maghrib" -> prayerTimes?.maghrib ?: LocalTime.of(18, 25)
                "Isha" -> prayerTimes?.isha ?: LocalTime.of(19, 55)
                else -> LocalTime.of(12, 0)
            }
            val formattedTime = prayerTimeDisplay.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))

            // Get current offset for this prayer
            val currentPrayerOffset = when (safePrayerName) {
                "Fajr" -> storedOffsets.fajr
                "Sunrise" -> storedOffsets.sunrise
                "Dhuhr" -> storedOffsets.dhuhr
                "Asr" -> storedOffsets.asr
                "Maghrib" -> storedOffsets.maghrib
                "Isha" -> storedOffsets.isha
                else -> 0
            }

            // Control Center overlay - directly in composition, on top of all content
            com.starception.submission.feature.prayertimes.components.ControlCenterPrayerPopup(
                prayerName = safePrayerName,
                prayerTime = formattedTime,
                originalTime = prayerTimeDisplay,
                currentOffset = currentPrayerOffset,
                onDismiss = { popupDialState = null },
                onSaveAdjustment = { prayerName, adjustment ->
                    Log.d("PrayerTimes", "🎯 Control Center SAVE: $prayerName offset = $adjustment minutes")
                    // Save adjustment via repository
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            repository.updateSinglePrayerOffset(prayerName, adjustment)
                            Log.i("PrayerTimes", "✅ Saved $prayerName offset: $adjustment minutes")

                            // Wait for preferences to be written
                            delay(100)

                            // Update notifications
                            try {
                                val appContext = screenContext.applicationContext
                                val serviceManager = dagger.hilt.android.EntryPointAccessors.fromApplication(
                                    appContext,
                                    com.starception.submission.prayer.service.PrayerNotificationServiceManagerEntryPoint::class.java
                                ).prayerNotificationServiceManager()
                                serviceManager.updatePrayerNotifications()
                                Log.i("PrayerTimes", "🔔 Notifications updated for $prayerName")
                            } catch (e: Exception) {
                                Log.e("PrayerTimes", "❌ Failed to update notifications", e)
                            }

                            // Close popup on main thread
                            withContext(Dispatchers.Main) {
                                popupDialState = null
                            }
                        } catch (e: Exception) {
                            Log.e("PrayerTimes", "❌ Failed to save $prayerName offset", e)
                            withContext(Dispatchers.Main) {
                                popupDialState = null
                            }
                        }
                    }
                },
                backdrop = controlCenterBackdrop,
                modifier = Modifier.fillMaxSize()
            )
        } // Close if (popupDialState != null)
    } // Close outer Box
}

/**
 * Helper function to get location text with smart priority based on available space
 *
 * PRIORITY SYSTEM (tries formats in order until one fits):
 * 1. "Area, City (CC)" - Most detailed (e.g., "Al Thanyah First, Dubai (AE)")
 * 2. "City (CC)" - Standard (e.g., "Dubai (AE)")
 * 3. "City" - Minimal (e.g., "Dubai")
 * 4. Fallback to coordinates
 *
 * MAX LENGTH: 35 characters for single-line display without ellipsis
 */
private fun getLocationWithCountryCode(
    locationString: String,
    locationData: com.starception.submission.prayer.model.Location?
): String {
    android.util.Log.d("LocationDisplay", "🏷️ SMART LOCATION PRIORITY:")
    android.util.Log.d("LocationDisplay", "   Input: '$locationString'")
    android.util.Log.d("LocationDisplay", "   Data: ${locationData?.let { "area='${it.area}', city='${it.city}', country='${it.country}', code='${it.countryCode}'" } ?: "null"}")

    // Handle no data
    if (locationData == null) {
        return locationString.ifBlank { "Loading location..." }
    }

    val maxLength = 60  // Increased limit to fit full location with country name

    // Extract available fields
    val area = locationData.area.takeIf { it.isNotEmpty() }
    val subLocality = locationData.subLocality.takeIf { it.isNotEmpty() }
    val city = locationData.city.takeIf { it.isNotEmpty() }
    val country = locationData.country.takeIf { it.isNotEmpty() }
    val countryCode = locationData.countryCode.takeIf { it.isNotEmpty() }

    // PRIORITY 1: Area + City + Country + Country Code (most detailed)
    if (area != null && city != null && country != null && countryCode != null) {
        val format1 = "$area, $city, $country ($countryCode)"
        if (format1.length <= maxLength) {
            android.util.Log.i("LocationDisplay", "   ✅ P1: '$format1' (${format1.length} chars)")
            return format1
        }
        android.util.Log.d("LocationDisplay", "   ❌ P1 too long: $format1 (${format1.length} chars)")
    }

    // PRIORITY 2: Area + City + Country Code (without full country name)
    if (area != null && city != null && countryCode != null) {
        val format2 = "$area, $city ($countryCode)"
        if (format2.length <= maxLength) {
            android.util.Log.i("LocationDisplay", "   ✅ P2: '$format2' (${format2.length} chars)")
            return format2
        }
        android.util.Log.d("LocationDisplay", "   ❌ P2 too long: $format2 (${format2.length} chars)")
    }

    // PRIORITY 3: SubLocality + City + Country Code (alternative to area)
    if (subLocality != null && city != null && countryCode != null) {
        val format3 = "$subLocality, $city ($countryCode)"
        if (format3.length <= maxLength) {
            android.util.Log.i("LocationDisplay", "   ✅ P3: '$format3' (${format3.length} chars)")
            return format3
        }
        android.util.Log.d("LocationDisplay", "   ❌ P3 too long: $format3 (${format3.length} chars)")
    }

    // PRIORITY 4: City + Country Code (standard format)
    if (city != null && countryCode != null) {
        val format4 = "$city ($countryCode)"
        if (format4.length <= maxLength) {
            android.util.Log.i("LocationDisplay", "   ✅ P4: '$format4' (${format4.length} chars)")
            return format4
        }
        android.util.Log.d("LocationDisplay", "   ❌ P4 too long: $format4 (${format4.length} chars)")
    }

    // PRIORITY 5: Area + City (no country code)
    if (area != null && city != null) {
        val result = "$area, $city"
        android.util.Log.i("LocationDisplay", "   ✅ P5: '$result' (${result.length} chars)")
        return result
    }

    // PRIORITY 6: City only
    if (city != null) {
        android.util.Log.i("LocationDisplay", "   ✅ P6: '$city' (${city.length} chars)")
        return city
    }

    // PRIORITY 7: Area only (rare case)
    if (area != null) {
        android.util.Log.i("LocationDisplay", "   ✅ P7: '$area' (${area.length} chars)")
        return area
    }

    // FINAL FALLBACK: Coordinates
    val fallback = locationString.ifBlank {
        "${String.format("%.4f", locationData.latitude)}, ${String.format("%.4f", locationData.longitude)}"
    }
    android.util.Log.w("LocationDisplay", "   ⚠️ Fallback: '$fallback'")
    return fallback
}

// calculatePrayerAlertState moved to wobble/PrayerAlertCalculator.kt so MainActivityViewModel's
// app-wide minute ticker can reuse the exact same logic (keeps the banner countdown live on
// every screen, not just Home).
