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

import kotlinx.datetime.LocalDate as KotlinLocalDate

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import com.starception.submission.R
import com.starception.submission.core.designsystem.theme.mainPageBackgroundBrush
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import android.util.Log
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.animateScrollBy
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
import kotlin.math.roundToInt
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starception.submission.feature.quran.QuranPlayerViewModel
import com.starception.submission.feature.prayertimes.data.PrayerTimeCalculatorEntryPoint
import com.starception.submission.feature.prayertimes.animations.RefreshIndicator
import com.starception.submission.feature.prayertimes.animations.FlowingArrowsAnimation
import com.starception.submission.feature.prayertimes.SwipeableBigTiles
import com.starception.submission.feature.prayertimes.SmartContentUtils
import com.starception.submission.feature.prayertimes.PrayerTimeHelpers
import com.starception.submission.feature.prayertimes.components.CompassPopupScreen
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.Duration
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.starception.submission.core.designsystem.theme.FloatingNavClearance
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
import com.starception.submission.core.designsystem.component.NiaBottomSheetDefaults
import com.starception.submission.core.designsystem.component.NiaBottomSheetDragHandle
import com.starception.submission.core.designsystem.component.NiaBottomSheetFrame
import com.starception.submission.core.designsystem.component.NiaBottomSheetTheme
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import com.starception.submission.feature.prayertimes.weather.CurrentWeather
import com.starception.submission.feature.prayertimes.weather.CurrentWeatherRepository
import com.starception.submission.feature.prayertimes.weather.MeteoconStyle
import com.starception.submission.feature.prayertimes.weather.PrayerWeatherThresholds
import com.starception.submission.feature.prayertimes.weather.AnimatedPrayerWeatherIcon
import com.starception.submission.feature.prayertimes.weather.AnimatedCurrentWeatherIcon
import com.starception.submission.feature.prayertimes.weather.PrayerWeatherVisual
import com.starception.submission.feature.prayertimes.weather.WeatherThresholdLevel
import com.starception.submission.feature.prayertimes.weather.temperatureThresholdLevel
import com.starception.submission.feature.prayertimes.weather.humidityThresholdLevel
import com.starception.submission.feature.prayertimes.weather.rainThresholdLevel
import com.starception.submission.feature.prayertimes.weather.primaryPrayerWeatherVisual
import com.starception.submission.feature.prayertimes.weather.prayerWeatherThresholdLevel
import com.starception.submission.feature.prayertimes.weather.prayerWeatherWarningDelayMillis
import com.starception.submission.feature.prayertimes.weather.PrayerWeatherIntelligence
import com.starception.submission.feature.prayertimes.weather.weatherThresholdPreviewLevel
import com.starception.submission.feature.prayertimes.weather.PrayerWeatherThresholdStore
import com.starception.submission.feature.prayertimes.weather.getUpcomingPrayerForecastTarget
import androidx.compose.ui.graphics.lerp

private val PrayerReferenceInk = Color(0xFF0A0808)
private val PrayerReferenceCard = Color(0xFFFFFDF7)
private val PrayerReferenceSlate = Color(0xFF5D6574)
private val PrayerReferenceBlue = Color(0xFF4F779D)
private val PrayerReferenceRust = Color(0xFF99593C)
private val PrayerReferenceGold = Color(0xFFD8AB59)

private data class PrayerTileWeatherAlert(
    val visual: PrayerWeatherVisual,
    val level: WeatherThresholdLevel,
)

internal fun shouldReplacePrayerBellWithWeather(
    prayerStatus: String,
    prayerTimeEditMode: Boolean,
): Boolean = !prayerTimeEditMode && (prayerStatus == "Current" || prayerStatus == "Next")

/** Chooses one tile for weather decoration, preferring the prayer already in progress. */
internal fun selectPrayerWeatherAlertTarget(
    prayersWithAlerts: Set<String>,
    statusForPrayer: (String) -> String,
): String? = prayersWithAlerts.firstOrNull { statusForPrayer(it) == "Current" }
    ?: prayersWithAlerts.firstOrNull { statusForPrayer(it) == "Next" }

private sealed interface CurrentWeatherLoadState {
    data object Loading : CurrentWeatherLoadState
    data object Unavailable : CurrentWeatherLoadState
    data class Available(val weather: CurrentWeather) : CurrentWeatherLoadState
}

@Composable
private fun rememberCurrentWeatherLoadState(
    location: com.starception.submission.prayer.model.Location?,
): State<CurrentWeatherLoadState> = produceState<CurrentWeatherLoadState>(
    initialValue = CurrentWeatherLoadState.Loading,
    key1 = location?.latitude,
    key2 = location?.longitude,
) {
    value = CurrentWeatherLoadState.Loading
    val validLocation = location?.takeIf { it.isValid() }
    value = if (validLocation == null) {
        CurrentWeatherLoadState.Unavailable
    } else {
        CurrentWeatherRepository.get(
            latitude = validLocation.latitude,
            longitude = validLocation.longitude,
        )?.let { CurrentWeatherLoadState.Available(it) }
            ?: CurrentWeatherLoadState.Unavailable
    }
}

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
    onFortressDuaClick: (com.starception.submission.core.duadatabase.Dua) -> Unit = {},
    onBukhariBookPlayClick: (Int) -> Unit = {},
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
    forbiddenPrayerTimeState: com.starception.submission.feature.prayertimes.wobble.ForbiddenPrayerTimeState = com.starception.submission.feature.prayertimes.wobble.ForbiddenPrayerTimeState(),
    onSearchSubmit: (query: String) -> Unit = {},
    isSyncingExternal: Boolean = false,
    onSetSyncing: (Boolean) -> Unit = {},
) {
    val screenContext = LocalContext.current
    val dailyReadingPlayer: QuranPlayerViewModel = viewModel(
        key = "homeDailyReadingPlayer",
    ) {
        val audioDownloadHelper = EntryPointAccessors.fromApplication(
            screenContext.applicationContext,
            AudioDownloadHelperEntryPoint::class.java,
        ).audioDownloadHelper()
        QuranPlayerViewModel(screenContext.applicationContext, audioDownloadHelper)
    }
    val contextualDuasByChapter by produceState(
        initialValue = emptyMap<Int, List<com.starception.submission.core.duadatabase.Dua>>(),
        key1 = screenContext.applicationContext,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val entryPoint = EntryPointAccessors.fromApplication(
                    screenContext.applicationContext,
                    PrayerTimeCalculatorEntryPoint::class.java,
                )
                val repository = entryPoint.duaRepository()
                buildMap {
                    CONTEXTUAL_DUA_CHAPTER_IDS.forEach { chapterId ->
                        repository.getDuasByChapter(chapterId)
                            .takeIf { it.isNotEmpty() }
                            ?.let { put(chapterId, it) }
                    }
                }
            }.getOrElse { error ->
                Log.w("PrayerTimesScreen", "Unable to load contextual Fortress duas", error)
                emptyMap()
            }
        }
    }
    
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
    var prayerTimeEditMode by rememberSaveable { mutableStateOf(false) }
    var showWeatherThresholds by rememberSaveable { mutableStateOf(false) }
    var prayerWeatherThresholds by remember {
        mutableStateOf(PrayerWeatherThresholdStore.load(screenContext))
    }
    var prayerTileWeatherAlerts by remember {
        mutableStateOf<Map<String, PrayerTileWeatherAlert>>(emptyMap())
    }
    var syncWeatherResult by remember { mutableStateOf<String?>(null) }
    val voiceFeedback by com.starception.submission.ui.search.SearchPrefillBus.voiceFeedback
        .collectAsStateWithLifecycle()

    LaunchedEffect(syncWeatherResult) {
        if (syncWeatherResult != null) {
            delay(10_000L)
            syncWeatherResult = null
        }
    }

    if (showWeatherThresholds) {
        PrayerWeatherThresholdSheet(
            thresholds = prayerWeatherThresholds,
            onDismiss = { showWeatherThresholds = false },
            onSave = { updated ->
                prayerWeatherThresholds = updated
                PrayerWeatherThresholdStore.save(screenContext, updated)
                showWeatherThresholds = false
            },
        )
    }

    LaunchedEffect(prayerTimeEditMode) {
        if (!prayerTimeEditMode) {
            revealedPrayerCard = null
            currentEditingTile = null
        }
    }

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

    val prayedPrayersToday by com.starception.submission.util.PrayerTracker
        .prayedPrayersToday
        .collectAsStateWithLifecycle()
    val prayedCount = prayedPrayersToday.size

    // Track current time updates for prayer status calculations
    LaunchedEffect(currentTime) {
        com.starception.submission.util.PrayerTracker.refreshToday()
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

    LaunchedEffect(prayerTimes, storedOffsets, prayerWeatherThresholds, currentTime) {
        val times = prayerTimes
        if (times == null) {
            prayerTileWeatherAlerts = emptyMap()
            return@LaunchedEffect
        }

        val date = LocalDate.now()
        val adjustedTimes = mapOf(
            "Fajr" to LocalDateTime.of(date, times.fajr)
                .plusMinutes(storedOffsets.fajr.toLong()),
            "Dhuhr" to LocalDateTime.of(date, times.dhuhr)
                .plusMinutes(storedOffsets.dhuhr.toLong()),
            "Asr" to LocalDateTime.of(date, times.asr)
                .plusMinutes(storedOffsets.asr.toLong()),
            "Maghrib" to LocalDateTime.of(date, times.maghrib)
                .plusMinutes(storedOffsets.maghrib.toLong()),
            "Isha" to LocalDateTime.of(date, times.isha)
                .plusMinutes(storedOffsets.isha.toLong()),
        )
        val forecasts = CurrentWeatherRepository.getPrayerForecasts(
            latitude = times.location.latitude,
            longitude = times.location.longitude,
            date = date,
            times = adjustedTimes.mapValues { it.value.toLocalTime() },
        )
        val now = LocalDateTime.of(date, currentTime)
        prayerTileWeatherAlerts = forecasts.mapNotNull { (prayerName, forecast) ->
            val occurrence = adjustedTimes[prayerName] ?: return@mapNotNull null
            // Keep the current prayer's warning briefly after its start, but do not
            // decorate completed prayers with weather that is no longer actionable.
            if (occurrence.isBefore(now.minusMinutes(30))) return@mapNotNull null
            val insight = PrayerWeatherIntelligence.create(
                prayerName = prayerName,
                forecast = forecast,
                thresholds = prayerWeatherThresholds,
            ) ?: return@mapNotNull null
            val visual = primaryPrayerWeatherVisual(insight.summary)
                ?: return@mapNotNull null
            prayerName to PrayerTileWeatherAlert(
                visual = visual,
                level = prayerWeatherThresholdLevel(
                    summary = insight.summary,
                    thresholds = prayerWeatherThresholds,
                ),
            )
        }.toMap()
    }

    val prayerTileWeatherAlertTarget = remember(
        prayerTileWeatherAlerts,
        currentTime,
        prayerTimes,
    ) {
        selectPrayerWeatherAlertTarget(prayerTileWeatherAlerts.keys) { prayerName ->
            PrayerTimeHelpers.getPrayerStatus(prayerName, currentTime, prayerTimes)
        }
    }

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
        if (!prayerTimeEditMode) {
            android.util.Log.d(
                "PrayerTimesScreen",
                "Ignoring AI suggestion for $prayerName while prayer schedule is locked",
            )
        } else {
            kotlinx.coroutines.MainScope().launch {
                try {
                    repository.updateSinglePrayerOffset(prayerName, suggestedOffset)
                    android.util.Log.i("PrayerTimesScreen", "✨ AI suggestion applied: $prayerName → $suggestedOffset minutes")
                } catch (e: Exception) {
                    android.util.Log.e("PrayerTimesScreen", "❌ Failed to apply suggestion: ${e.message}")
                }
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
            syncWeatherResult = null
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
                    prayerTimes?.let { refreshedTimes ->
                        val target = getUpcomingPrayerForecastTarget(
                            prayerTimes = refreshedTimes,
                            timeOffsets = storedOffsets,
                            now = LocalDateTime.now(),
                        )
                        val displayName = getPrayerDisplayName(target.prayerName, target.date)
                        val insight = withTimeoutOrNull(3_500L) {
                            CurrentWeatherRepository.getPrayerInsight(
                                latitude = refreshedTimes.location.latitude,
                                longitude = refreshedTimes.location.longitude,
                                prayerName = displayName,
                                prayerDate = target.date,
                                prayerTime = target.time,
                                forceRefresh = true,
                                thresholds = prayerWeatherThresholds,
                            )
                        }
                        syncWeatherResult = insight?.compactText
                    }
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
        val compactTile = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        
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
                gesturesEnabled = prayerTimeEditMode,
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
                    }
            ) {
                val prayerStatus = PrayerTimeHelpers.getPrayerStatus(prayerName, currentTime, prayerTimes)
                val isDarkTheme = LocalDarkTheme.current
                val accentColor = if (isDarkTheme) {
                    when (prayerStatus) {
                        "Current" -> MaterialTheme.colorScheme.tertiary
                        "Next" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.secondary
                    }
                } else {
                    when (prayerStatus) {
                        "Current" -> PrayerReferenceRust
                        "Next" -> PrayerReferenceBlue
                        else -> if (prayerName == "Sunrise") PrayerReferenceGold else PrayerReferenceSlate
                    }
                }
                val tileColor = if (isDarkTheme) {
                    // surfaceContainerLow is nearly identical to this screen's
                    // dark background, which erased the card silhouette. Lift the
                    // neutral cards one surface step and tint only status cards.
                    when (prayerStatus) {
                        "Current" -> lerp(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.tertiary,
                            0.12f,
                        )
                        "Next" -> lerp(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.primary,
                            0.10f,
                        )
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                } else {
                    when (prayerStatus) {
                        "Current" -> lerp(PrayerReferenceCard, PrayerReferenceRust, 0.12f)
                        "Next" -> lerp(PrayerReferenceCard, PrayerReferenceBlue, 0.11f)
                        else -> PrayerReferenceCard
                    }
                }
                val tileBorder = if (isDarkTheme) {
                    BorderStroke(
                        width = 1.dp,
                        color = when (prayerStatus) {
                            "Current" -> accentColor.copy(alpha = 0.38f)
                            "Next" -> accentColor.copy(alpha = 0.32f)
                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
                        },
                    )
                } else {
                    null
                }
                val titleColor = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else PrayerReferenceInk
                val supportingColor = if (isDarkTheme) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    PrayerReferenceSlate
                }
                Surface(
                    shape = RoundedCornerShape(if (compactTile) 20.dp else 28.dp),
                    color = tileColor,
                    border = tileBorder,
                    tonalElevation = if (isDarkTheme) 1.dp else 0.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(prayerName, currentOffset, prayerTimeEditMode) {
                            if (!prayerTimeEditMode) return@pointerInput
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                drawCircle(
                                    color = accentColor.copy(alpha = if (isDarkTheme) 0.13f else 0.065f),
                                    radius = size.minDimension * 0.42f,
                                    center = Offset(size.width * 0.96f, size.height * 0.98f),
                                )
                            }
                            .padding(
                                start = if (compactTile) 11.dp else 14.dp,
                                end = if (compactTile) 11.dp else 14.dp,
                                top = if (compactTile) 5.dp else 8.dp,
                                bottom = if (compactTile) 6.dp else 8.dp,
                            ),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Keep names and time in the same vertical layout. The
                        // selected Arabic font can paint outside its nominal line
                        // box, so independently pinning this group and the time to
                        // opposite Box edges allowed their visible glyphs to cross.
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            // Prayer name with notification bell icon
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = getPrayerDisplayName(prayerName),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = if (compactTile) 15.sp else 18.sp,
                                        lineHeight = if (compactTile) 16.sp else 19.sp,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        // includeFontPadding = false still reserves the
                                        // font's own ascent/descent leading, so lowering
                                        // lineHeight alone did nothing. Trimming it is
                                        // what closes the gap to the localized name.
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.Both,
                                        ),
                                    ),
                                    color = titleColor,
                                    fontWeight = FontWeight.SemiBold,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )

                                // Notification bell toggle icon (only for main 5 prayers)
                                if (prayerName != "Sunrise") {
                                    IconButton(
                                        // Keep notification state visible during normal
                                        // reading, but only allow changes after the user
                                        // explicitly enters Tune schedule mode.
                                        enabled = prayerTimeEditMode,
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onNotificationToggle(!notificationEnabled)
                                            android.util.Log.d("PrayerCard", "🔔 Notification toggled for $prayerName: ${!notificationEnabled}")
                                        },
                                        modifier = Modifier.size(if (compactTile) 26.dp else 30.dp)
                                    ) {
                                        val weatherAlert = prayerTileWeatherAlerts[prayerName]
                                            .takeIf {
                                                prayerName == prayerTileWeatherAlertTarget &&
                                                shouldReplacePrayerBellWithWeather(
                                                    prayerStatus = prayerStatus,
                                                    prayerTimeEditMode = prayerTimeEditMode,
                                                )
                                            }
                                        var showPrayerWeather by remember(
                                            prayerName,
                                            weatherAlert,
                                        ) {
                                            mutableStateOf(weatherAlert != null)
                                        }
                                        LaunchedEffect(weatherAlert) {
                                            if (weatherAlert == null) {
                                                showPrayerWeather = false
                                                return@LaunchedEffect
                                            }

                                            // Surface the warning first, then regularly
                                            // restore the bell so its enabled/disabled state
                                            // never disappears behind the forecast.
                                            while (true) {
                                                showPrayerWeather = true
                                                delay(8_000L)
                                                showPrayerWeather = false
                                                delay(8_000L)
                                            }
                                        }
                                        AnimatedContent(
                                            targetState = weatherAlert.takeIf {
                                                showPrayerWeather
                                            },
                                            transitionSpec = {
                                                (fadeIn(tween(220)) + scaleIn(
                                                    initialScale = 0.72f,
                                                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                                                )) togetherWith
                                                    (fadeOut(tween(150)) + scaleOut(
                                                        targetScale = 0.78f,
                                                        animationSpec = tween(190),
                                                    ))
                                            },
                                            label = "${prayerName}BellWeatherMorph",
                                        ) { alert ->
                                            if (alert != null) {
                                                AnimatedPrayerWeatherIcon(
                                                    visual = alert.visual,
                                                    level = alert.level,
                                                    styleOverride = MeteoconStyle.Fill,
                                                    preserveOriginalColors = true,
                                                    modifier = Modifier.size(
                                                        if (compactTile) 26.dp else 30.dp,
                                                    ),
                                                )
                                            } else {
                                                FlaticonIcon(
                                                    glyph = if (notificationEnabled) {
                                                        FlaticonIcons.NOTIFICATIONS_ACTIVE
                                                    } else {
                                                        FlaticonIcons.NOTIFICATIONS
                                                    },
                                                    contentDescription = when {
                                                        prayerTimeEditMode && notificationEnabled -> "Notifications enabled. Tap to disable"
                                                        prayerTimeEditMode -> "Notifications disabled. Tap to enable"
                                                        notificationEnabled -> "Notifications enabled. Enter edit mode to change"
                                                        else -> "Notifications disabled. Enter edit mode to change"
                                                    },
                                                    tint = accentColor.copy(
                                                        alpha = when {
                                                            notificationEnabled && prayerTimeEditMode -> 1f
                                                            notificationEnabled -> 0.78f
                                                            prayerTimeEditMode -> 0.48f
                                                            else -> 0.36f
                                                        },
                                                    ),
                                                    fontSize = if (compactTile) 13.sp else 16.sp,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (!compactTile) {
                                Text(
                                    text = getPrayerNameInLocalLanguage(prayerName, prayerTimes?.location?.countryCode),
                                    // PDMS Saleem has tall marks and descenders. A slightly
                                    // tighter measured line leaves a real visual safety gap
                                    // above the time without making the Arabic hard to read.
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = getSelectedArabicFontFamily(screenContext),
                                        fontSize = 15.sp,
                                        letterSpacing = 0.4.sp,
                                        lineHeight = 17.sp,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.Both,
                                        ),
                                    ),
                                    color = supportingColor,
                                    fontWeight = FontWeight.Normal,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                    // The name shares its Row with a 24dp IconButton,
                                    // which is taller than the name's own line box and
                                    // therefore sets the Row height, centring the name
                                    // and stranding dead space beneath it. That space
                                    // belongs to the Row, so no lineHeight or
                                    // LineHeightStyle on this text can reclaim it —
                                    // measured pixel-identical when tried. Close the
                                    // distance to the name it translates directly.
                                    modifier = Modifier.offset(y = (-6).dp),
                                )
                            }
                        }

                        // Bottom section: Time display with separated AM/PM.
                        Column(
                            modifier = Modifier.fillMaxWidth(),
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
                                val baseColor = accentColor

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
                                            fontSize = if (compactTile) 19.sp else 24.sp,
                                            lineHeight = if (compactTile) 22.sp else 30.sp,
                                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        ),
                                        color = baseColor,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.width(if (compactTile) 2.dp else 4.dp))

                                    // AM/PM (smaller)
                                    Text(
                                        text = amPm,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = if (compactTile) 11.sp else 14.sp,
                                        ),
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
                                    enabled = prayerTimeEditMode,
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
        // Measure automatic warning timing from app entry. The actual delay is
        // resolved after the forecast arrives because it depends on severity.
        val appOpenStartedAt = System.currentTimeMillis()
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

        // Surface noteworthy conditions immediately on app entry. Previously this
        // forecast was only evaluated after a manual pull-to-sync, so users could
        // miss a rain/heat/humidity warning until they refreshed the page themselves.
        prayerTimes?.let { freshTimes ->
            val target = getUpcomingPrayerForecastTarget(
                prayerTimes = freshTimes,
                timeOffsets = storedOffsets,
                now = LocalDateTime.now(),
            )
            val displayName = getPrayerDisplayName(target.prayerName, target.date)
            val insight = withTimeoutOrNull(3_500L) {
                CurrentWeatherRepository.getPrayerInsight(
                    latitude = freshTimes.location.latitude,
                    longitude = freshTimes.location.longitude,
                    prayerName = displayName,
                    prayerDate = target.date,
                    prayerTime = target.time,
                    thresholds = prayerWeatherThresholds,
                )
            }
            insight?.compactText?.takeIf { it.isNotBlank() }?.let { warningText ->
                val warningDelay = prayerWeatherWarningDelayMillis(
                    summary = insight.summary,
                    thresholds = prayerWeatherThresholds,
                )
                val warningEligibleAt = appOpenStartedAt + warningDelay
                val remainingDelay = warningEligibleAt - System.currentTimeMillis()
                if (remainingDelay > 0L) {
                    delay(remainingDelay)
                }
                // A manual pull-to-sync may have produced a newer warning while
                // the app-open result was waiting; never overwrite that result.
                if (syncWeatherResult == null) {
                    syncWeatherResult = warningText
                }
            }
        }
        
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
                syncResultText = voiceFeedback,
                onSyncResultClick = voiceFeedback?.let {
                    {
                        com.starception.submission.ui.search.SearchPrefillBus.clearVoiceFeedback()
                        com.starception.submission.ui.search.SearchPrefillBus.requestVoiceSearch()
                    }
                },
                onSyncResultDismiss = {
                    com.starception.submission.ui.search.SearchPrefillBus.clearVoiceFeedback()
                },
                idleContainerColor = Color.Transparent,
                idleContainerBrush = mainPageBackgroundBrush(),
                downloadProgress = downloadProgress,
                downloadLabel = downloadLabel,
                isTtsPreparing = isTtsPreparing,
                mediaBar = com.starception.submission.ui.mediaSyncBarRow(
                    state = mediaState,
                    onAction = onMediaAction,
                    onTitleClick = {
                        val source = mediaState.playback.source
                        if (onMediaSourceClick != null) {
                            onMediaSourceClick.invoke(source)
                        } else {
                            (source as? com.starception.submission.media.MediaSource.Quran)
                                ?.let { onSurahClick(it.surahIndex + 1) }
                        }
                    },
                    isTtsPreparing = isTtsPreparing,
                ),
                prayerAlertState = prayerAlertState,
                forbiddenPrayerTimeState = forbiddenPrayerTimeState,
                weatherWarningText = syncWeatherResult,
                onWeatherWarningDismiss = { syncWeatherResult = null },
                silentModeState = silentModeState,
                islamicEventState = islamicEventState,
                onIslamicEventClick = { event ->
                    com.starception.submission.ui.search.SearchPrefillBus.requestSearch(event.searchQuery)
                },
                modifier = Modifier.fillMaxSize()
            ) { syncState ->
            val outerConfiguration = LocalConfiguration.current
            val outerIsLandscape = outerConfiguration.orientation == Configuration.ORIENTATION_LANDSCAPE
            var showAllPrayers by rememberSaveable { mutableStateOf(false) }
            val portraitScrollState = rememberScrollState()
            var keepExpansionScrollEnabled by remember { mutableStateOf(false) }
            // Insights keeps its full geometry in expanded mode. Follow the added
            // prayer row with scroll instead of compressing the carousel; the full
            // location card simultaneously contracts into a compact action.
            // Row 3 adds 106dp while the location control gives back 52dp
            // (92dp -> 40dp). Scroll only that 54dp net growth. The previous
            // 122dp travel made the entering tiles move farther than their reveal,
            // which looked like a bounce before the layout settled.
            val expansionScrollDistancePx = with(LocalDensity.current) { 54.dp.toPx() }
            // A disabled verticalScroll does not dispatch nested-scroll deltas, which
            // prevents PullToSyncContainer from seeing downward drags while the prayer
            // list is collapsed. This no-op scrollable keeps the page stationary while
            // still forwarding those gestures to the pull-to-sync connection.
            val pullGestureScrollState = rememberScrollableState { 0f }
            LaunchedEffect(showAllPrayers, outerIsLandscape) {
                if (!outerIsLandscape) {
                    if (showAllPrayers) {
                        // Bring the added row and its controls into view while the
                        // full-size Insights carousel remains available above.
                        keepExpansionScrollEnabled = true
                        withFrameNanos { }
                        portraitScrollState.animateScrollBy(
                            value = expansionScrollDistancePx,
                            animationSpec = tween(
                                durationMillis = 840,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                    } else if (keepExpansionScrollEnabled) {
                        portraitScrollState.animateScrollTo(
                            value = 0,
                            animationSpec = tween(
                                durationMillis = 680,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                        keepExpansionScrollEnabled = false
                    }
                }
            }
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
                .then(
                    if (!outerIsLandscape && (showAllPrayers || keepExpansionScrollEnabled)) {
                        Modifier.verticalScroll(
                            state = portraitScrollState,
                        )
                    } else {
                        Modifier.scrollable(
                            state = pullGestureScrollState,
                            orientation = Orientation.Vertical,
                        )
                    },
                )) {
            // Pull-to-refresh indicator is handled by PullToSyncContainer in the sage background
            // Home page content with wobble transformation applied to actual content
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
            val fontScale = LocalDensity.current.fontScale
            // Reserve room for the search chrome, prayer rows, location card and
            // floating navigation, then give the Insights carousel what remains.
            // This keeps Location visible at rest instead of relying on a height
            // tuned for one handset. Larger accessibility text gets extra room too.
            val portraitInsightMaxHeight = when {
                configuration.screenHeightDp < 820 -> 280.dp
                configuration.screenHeightDp < 900 -> 304.dp
                // The Pixel 9 Pro class has enough vertical room for the carousel
                // to absorb the final dashboard slack. At 328dp the location tile
                // still stopped about 30dp above the floating navigation; 348dp
                // leaves the intended compact 10–12dp visual gap.
                else -> 348.dp
            }
            // Measured against the real chrome rather than guessed: the prayer rows
            // gave back 32dp (112 -> 96), the Show All control 8dp, the Prayer times
            // header 8dp and the location card 6dp, on top of the slack that was
            // already there while this sat pinned to its 208dp floor. Charge less
            // than the chrome actually needs and the card slides under the floating
            // navigation; charge more and dead space collects above it.
            val portraitInsightRestingHeight = (
                configuration.screenHeightDp.dp -
                    576.dp -
                    (80f * (fontScale - 1f).coerceAtLeast(0f)).dp
                ).coerceIn(208.dp, portraitInsightMaxHeight)
            // Keep the location tile at the same screen position while the sync strip
            // is held. The sheet moves down by heldContentInsetTop, while the search
            // chrome simultaneously gives back only the portion of the status-bar
            // inset represented by dynamicTopInset. Let the large Insights strip absorb
            // the remaining displacement so prayer cards keep their complete design,
            // including the localized Arabic prayer name.
            val syncTopInsetReclaim = (statusBarInset - dynamicTopInset)
                .coerceAtLeast(0.dp)
                .coerceAtMost(syncState.heldContentInsetTop)
            val syncBottomClearanceReclaim = syncState.heldContentInsetTop.coerceAtMost(38.dp)
            // The expanded prayer list needs the full bottom clearance as manual
            // scroll runway for its added row and the location card beneath it.
            val effectiveSyncBottomClearanceReclaim =
                if (showAllPrayers || keepExpansionScrollEnabled) {
                    0.dp
                } else {
                    syncBottomClearanceReclaim
                }
            val syncContentCompression =
                (syncState.heldContentInsetTop - syncTopInsetReclaim)
                    .coerceAtLeast(0.dp)
            // A persistent sync/prayer strip used to collapse Insights all the way to
            // 170dp on every phone. On tall portrait displays (Pixel 9 Pro included)
            // that made the dashboard finish roughly 40dp too early, leaving a large
            // empty band between Location and the floating navigation. Preserve the
            // normal 208dp compact strip on tall screens; genuinely short phones still
            // have the smaller escape hatch needed to keep Location reachable.
            val portraitInsightMinHeight =
                if (configuration.screenHeightDp >= 900) 208.dp else 170.dp
            val portraitInsightHeight = (portraitInsightRestingHeight - syncContentCompression)
                .coerceAtLeast(portraitInsightMinHeight)

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
                                prayedPrayers = prayedPrayersToday,
                                onTogglePrayer = com.starception.submission.util.PrayerTracker::togglePrayerStatus,
                                dailyReadingPlayback = DailyReadingPlaybackState(
                                    surahIndex = dailyReadingPlayer.currentSurahIndex,
                                    isPlaying = dailyReadingPlayer.isPlaying,
                                    isLoading = dailyReadingPlayer.isLoading,
                                    isDownloading = dailyReadingPlayer.isDownloading,
                                    downloadProgress = dailyReadingPlayer.downloadProgress,
                                    error = dailyReadingPlayer.downloadError,
                                ),
                                onDailyReadingPlayPause = { surahIndex ->
                                    if (dailyReadingPlayer.currentSurahIndex == surahIndex &&
                                        (dailyReadingPlayer.isPlaying || dailyReadingPlayer.currentPosition > 0)
                                    ) {
                                        dailyReadingPlayer.togglePlayPause()
                                    } else {
                                        dailyReadingPlayer.playSurah(surahIndex)
                                    }
                                },
                                onDailyReadingRetry = dailyReadingPlayer::retryDownload,
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
                                onFortressDuaClick = onFortressDuaClick,
                                onBukhariBookPlayClick = onBukhariBookPlayClick,
                                fortressDuasByChapter = contextualDuasByChapter,
                                goToMosqueDurationMinutes = { name -> notificationPreferences.getGoToMosqueDurationForPrayer(name) },
                                isInteractionBlocked = showCompassPopup || popupDialState != null || showLocationServiceDialog,
                                weatherThresholds = prayerWeatherThresholds,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LandscapeLocationWeatherTile(
                            locationString = location,
                            locationData = prayerTimes?.location,
                            thresholds = prayerWeatherThresholds,
                            onLongPress = { showWeatherThresholds = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Right column: compact header + all six prayer cards. The rows
                    // share the measured height so the last pair cannot fall behind
                    // the bottom edge or require a hidden initial scroll.
                    Column(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "Prayer times",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Today’s schedule",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            PrayerHeaderAction(
                                active = prayerTimeEditMode,
                                compact = true,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (prayerTimeEditMode) {
                                        revealedPrayerCard = null
                                        currentEditingTile = null
                                    }
                                    prayerTimeEditMode = !prayerTimeEditMode
                                },
                            )
                        }

                        // Prayer cards in a 2-column grid for landscape
                        for (i in orderedPrayers.indices step 2) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
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
                                        .fillMaxHeight(),
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
                                            .fillMaxHeight(),
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
                val dashboardTransition = updateTransition(
                    targetState = showAllPrayers,
                    label = "prayerDashboardExpansion",
                )
                // Keep the child measurement stable while row 3 is revealed. If the
                // tiles resize during expandVertically, its moving target produces a
                // visible settle at the end of the entrance.
                val tileHeight = 106.dp
                val buttonIconRotation by dashboardTransition.animateFloat(
                    transitionSpec = {
                        tween(durationMillis = 680, easing = FastOutSlowInEasing)
                    },
                    label = "prayerToggleRotation",
                ) { expanded ->
                    if (expanded) 180f else 0f
                }
                // Swipeable Big Tiles
                Box(
                    modifier = Modifier
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
                    prayedPrayers = prayedPrayersToday,
                    onTogglePrayer = com.starception.submission.util.PrayerTracker::togglePrayerStatus,
                    dailyReadingPlayback = DailyReadingPlaybackState(
                        surahIndex = dailyReadingPlayer.currentSurahIndex,
                        isPlaying = dailyReadingPlayer.isPlaying,
                        isLoading = dailyReadingPlayer.isLoading,
                        isDownloading = dailyReadingPlayer.isDownloading,
                        downloadProgress = dailyReadingPlayer.downloadProgress,
                        error = dailyReadingPlayer.downloadError,
                    ),
                    onDailyReadingPlayPause = { surahIndex ->
                        if (dailyReadingPlayer.currentSurahIndex == surahIndex &&
                            (dailyReadingPlayer.isPlaying || dailyReadingPlayer.currentPosition > 0)
                        ) {
                            dailyReadingPlayer.togglePlayPause()
                        } else {
                            dailyReadingPlayer.playSurah(surahIndex)
                        }
                    },
                    onDailyReadingRetry = dailyReadingPlayer::retryDownload,
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
                    portraitStripHeight = portraitInsightHeight,
                    // Expanded prayer mode scrolls naturally; keep Insights at its
                    // normal size instead of squeezing the carousel to fund row 3.
                    compactForExpandedPrayers = false,
                    onSurahClick = onSurahClick,
                    onSurahClickWithAyah = onSurahClickWithAyah,
                    onFortressDuaClick = onFortressDuaClick,
                    onBukhariBookPlayClick = onBukhariBookPlayClick,
                    fortressDuasByChapter = contextualDuasByChapter,
                    goToMosqueDurationMinutes = { name -> notificationPreferences.getGoToMosqueDurationForPrayer(name) },
                    isInteractionBlocked = showCompassPopup || popupDialState != null || showLocationServiceDialog,
                    weatherThresholds = prayerWeatherThresholds,
                )
                }

                // Treat adjustment guidance as part of the prayer section
                // header instead of a separate dashboard banner.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            text = "Prayer times",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Today’s schedule",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    PrayerHeaderAction(
                        active = prayerTimeEditMode,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (prayerTimeEditMode) {
                                revealedPrayerCard = null
                                currentEditingTile = null
                                prayerTimeEditMode = false
                            } else {
                                prayerTimeEditMode = true
                            }
                        },
                    )
                }

                // Expandable prayer layout - smart default view with expand option
                // The tile height participates in the shared dashboard
                // transition above, keeping every moving element synchronized.
                
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
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                .height(tileHeight),
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
                                .height(tileHeight),
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
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                .height(tileHeight),
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
                                .height(tileHeight),
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

                // Material 3 expressive expandable section with a deliberately
                // unhurried curve; this avoids the abrupt accordion-like jump.
                AnimatedVisibility(
                    visible = showAllPrayers,
                    enter = expandVertically(
                        animationSpec = tween(
                            durationMillis = 840,
                            easing = FastOutSlowInEasing,
                        ),
                        expandFrom = Alignment.Top,
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 500,
                            delayMillis = 40,
                        ),
                    ),
                    exit = shrinkVertically(
                        animationSpec = tween(
                            durationMillis = 680,
                            easing = FastOutSlowInEasing,
                        ),
                        shrinkTowards = Alignment.Top,
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 240),
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                    .height(tileHeight),
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
                                    .height(tileHeight),
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

                // Keep the toggle and location in one layout so the card can travel,
                // resize and round into the floating pin instead of cross-fading
                // between two unrelated components.
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // The location morph and row-3 expansion share the same state,
                    // duration and easing so neither can lead or catch up to the other.
                    val locationControlHeight by dashboardTransition.animateDp(
                        transitionSpec = {
                            tween(durationMillis = 840, easing = FastOutSlowInEasing)
                        },
                        label = "locationControlHeight",
                    ) { expanded ->
                        if (expanded) 40.dp else 92.dp
                    }
                    val locationCardWidth by dashboardTransition.animateDp(
                        transitionSpec = {
                            tween(durationMillis = 840, easing = FastOutSlowInEasing)
                        },
                        label = "locationCardWidth",
                    ) { expanded ->
                        if (expanded) 40.dp else maxWidth
                    }
                    val locationCardHeight by dashboardTransition.animateDp(
                        transitionSpec = {
                            tween(durationMillis = 840, easing = FastOutSlowInEasing)
                        },
                        label = "locationCardHeight",
                    ) { expanded ->
                        if (expanded) 40.dp else 52.dp
                    }
                    val locationCardOffsetY by dashboardTransition.animateDp(
                        transitionSpec = {
                            tween(durationMillis = 840, easing = FastOutSlowInEasing)
                        },
                        label = "locationCardOffsetY",
                    ) { expanded ->
                        // In the resting layout the Show All control starts at 2dp
                        // and ends at 34dp. A 36dp card offset leaves a compact 2dp
                        // gap, returning 4dp below the location card for navigation
                        // clearance without changing the dashboard's total height.
                        if (expanded) 0.dp else 36.dp
                    }
                    val locationCardEndInset by dashboardTransition.animateDp(
                        transitionSpec = {
                            tween(durationMillis = 840, easing = FastOutSlowInEasing)
                        },
                        label = "locationCardEndInset",
                    ) { expanded ->
                        if (expanded) 12.dp else 0.dp
                    }
                    val locationCardCornerRadius by dashboardTransition.animateDp(
                        transitionSpec = {
                            tween(durationMillis = 840, easing = FastOutSlowInEasing)
                        },
                        label = "locationCardCornerRadius",
                    ) { expanded ->
                        if (expanded) 20.dp else 16.dp
                    }
                    val locationMarkerInset by dashboardTransition.animateDp(
                        transitionSpec = {
                            tween(durationMillis = 840, easing = FastOutSlowInEasing)
                        },
                        label = "locationMarkerInset",
                    ) { expanded ->
                        // 9dp centers a 22dp marker in the 40dp floating surface.
                        if (expanded) 9.dp else 14.dp
                    }
                    val locationCardShape = RoundedCornerShape(locationCardCornerRadius)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(locationControlHeight),
                    ) {
                    TextButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            // One state starts (or reverses) the prayer-row expansion,
                            // dashboard scroll and location morph on the same frame.
                            showAllPrayers = !showAllPrayers
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                        // Default 40dp min-height around a 14dp label; the carousel
                        // uses the difference.
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = 2.dp)
                            .height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    rotationZ = buttonIconRotation
                                },
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showAllPrayers) "Show Less" else "Show All Prayers",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    // One surface owns both states. Anchoring it to the end makes the
                    // full card contract naturally into the pin beside Show Less.
                    val locationTileContent = MaterialTheme.colorScheme.onSurface
                    val locationTileSupporting = MaterialTheme.colorScheme.onSurfaceVariant
                    Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = -locationCardEndInset, y = locationCardOffsetY)
                        .width(locationCardWidth)
                        .height(locationCardHeight)
                        .zIndex(1f)
                        .clip(locationCardShape)
                        .combinedClickable(
                            onClick = {
                                if (showAllPrayers) {
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.TextHandleMove,
                                    )
                                    showAllPrayers = false
                                } else {
                                    showWeatherThresholds = true
                                }
                            },
                            onLongClick = {
                                if (showAllPrayers) {
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.TextHandleMove,
                                    )
                                    showAllPrayers = false
                                } else {
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.LongPress,
                                    )
                                    showWeatherThresholds = true
                                }
                            },
                        ),
                    shape = locationCardShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    // Follows the prayer tiles: outlined only in dark, where a card
                    // needs an edge to separate from the background. In light it was
                    // the one outlined card on the screen, and primary at 20% renders
                    // a cool blue hairline against these warm surfaces.
                    border = if (showAllPrayers) {
                        BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        )
                    } else if (LocalDarkTheme.current) {
                        BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                        )
                    } else {
                        null
                    },
                    shadowElevation = if (showAllPrayers) 3.dp else if (LocalDarkTheme.current) 0.dp else 1.dp,
                    ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                    // This is the same marker in both states. The surface's animated
                    // right-anchored width carries it from the card's leading edge to
                    // the floating position, so there is no icon handoff or blank frame.
                    Image(
                        painter = painterResource(R.drawable.ic_flaticon_location_marker),
                        contentDescription = if (showAllPrayers) {
                            "Collapse prayers and show location details"
                        } else {
                            "Prayer location"
                        },
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = locationMarkerInset)
                            .size(22.dp)
                            .zIndex(1f),
                    )

                    androidx.compose.animation.AnimatedVisibility(
                        visible = !showAllPrayers,
                        enter = fadeIn(
                            animationSpec = tween(durationMillis = 220, delayMillis = 150),
                        ),
                        exit = fadeOut(
                            animationSpec = tween(durationMillis = 180),
                        ),
                    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 46.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val locationData = prayerTimes?.location
                        val city = locationData?.city?.trim().orEmpty()
                        val area = locationData?.area?.trim().takeUnless { it.isNullOrEmpty() }
                            ?: locationData?.subLocality?.trim().takeUnless { it.isNullOrEmpty() }
                        val countryCode = locationData?.countryCode?.trim()?.uppercase()
                            ?.takeIf { it.length in 2..3 }
                        val locationTitle = city.ifBlank {
                            getLocationWithCountryCode(location, locationData)
                        }
                        val locationDetail = area
                            ?.takeUnless { it.equals(locationTitle, ignoreCase = true) }
                            ?: locationData?.administrativeArea?.trim()
                                ?.takeUnless {
                                    it.isEmpty() || it.equals(locationTitle, ignoreCase = true)
                                }
                            ?: locationData?.country?.trim().orEmpty()

                        // Check if location text contains Arabic (Unicode range 0600-06FF)
                        val containsArabic = (locationTitle + locationDetail)
                            .any { it in '\u0600'..'\u06FF' }

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

                        val currentWeatherState by rememberCurrentWeatherLoadState(locationData)

                        val supportingLocation = locationDetail.takeIf { it.isNotBlank() }
                            ?: countryCode.orEmpty()

                        // Name and chevron claim the row's spare width as a group. The
                        // continuously animated marker occupies the 32dp reserved by
                        // the outer row's start padding.
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                        Column(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = locationTitle,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = arabicFontFamily
                                        ?: MaterialTheme.typography.bodyLarge.fontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    lineHeight = 18.sp,
                                ),
                                color = locationTileContent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            if (supportingLocation.isNotBlank()) {
                                Text(
                                    text = supportingLocation,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                        letterSpacing = 0.sp,
                                    ),
                                    color = locationTileSupporting,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        // Sits directly against the location text: the column above
                        // uses fill = false, so it reports only the width it needs and
                        // this lands right after it rather than at the row's far edge.
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Weather details",
                            tint = locationTileSupporting.copy(alpha = 0.7f),
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .size(16.dp),
                        )
                        }

                        when (val weatherState = currentWeatherState) {
                            CurrentWeatherLoadState.Loading -> {
                                PortraitWeatherLoadingPlaceholder()
                            }
                            is CurrentWeatherLoadState.Available -> {
                            val weather = weatherState.weather
                            val conditionLabel = weatherConditionLabel(weather)
                            val temperatureLevel = temperatureThresholdLevel(
                                value = weather.temperatureCelsius,
                                threshold = prayerWeatherThresholds.temperatureCelsius,
                            )
                            val humidityLevel = humidityThresholdLevel(
                                value = weather.relativeHumidity,
                                threshold = prayerWeatherThresholds.humidity,
                            )
                            val rainLevel = rainThresholdLevel(
                                value = weather.precipitationProbability,
                                threshold = prayerWeatherThresholds.rainProbability,
                            )
                            val precipitationLabel = if (weather.precipitationProbability == 0) {
                                "No rain"
                            } else {
                                "${weather.precipitationProbability}% rain"
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Rule separating location identity from conditions, as
                            // in the dashboard reference.
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(32.dp)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            0.2f to MaterialTheme.colorScheme.primary.copy(
                                                alpha = 0.06f,
                                            ),
                                            0.5f to MaterialTheme.colorScheme.primary.copy(
                                                alpha = 0.30f,
                                            ),
                                            0.8f to MaterialTheme.colorScheme.primary.copy(
                                                alpha = 0.06f,
                                            ),
                                            1f to Color.Transparent,
                                        ),
                                        shape = RoundedCornerShape(50),
                                    ),
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(
                                    space = 5.dp,
                                    alignment = Alignment.End,
                                ),
                            ) {
                                // Monochrome takes the palette tint fully, so it reads
                                // at this size against a pale card the way the location
                                // pin beside it does. Fill and Flat are both light
                                // artwork and washed out here, which is what the tinted
                                // disc was previously compensating for.
                                if (temperatureLevel == WeatherThresholdLevel.Normal) {
                                    AnimatedCurrentWeatherIcon(
                                        weather = weather,
                                        styleOverride = MeteoconStyle.Monochrome,
                                        modifier = Modifier.size(32.dp),
                                    )
                                } else {
                                    AnimatedPrayerWeatherIcon(
                                        visual = PrayerWeatherVisual.Heat,
                                        level = temperatureLevel,
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                                Column(
                                    // Keep the three lines on a shared leading edge
                                    // so the icon and copy read as one compact unit.
                                    // The parent row anchors that unit to the card's
                                    // 14dp end inset.
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    // Keep these stacked. Setting temperature and
                                    // condition on one line widened this block to
                                    // ~135dp, and because it is unweighted it claims
                                    // that width first — which truncated the location
                                    // name to "Al Safo...". Stacked, the widest line
                                    // is ~68dp and the location keeps its room.
                                    Text(
                                        // The weather repository currently requests
                                        // Celsius explicitly, so always expose the unit
                                        // instead of leaving a bare degree ambiguous.
                                        text = "${weather.temperatureCelsius.roundToInt()}°C",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            lineHeight = 18.sp,
                                            platformStyle = PlatformTextStyle(
                                                includeFontPadding = false,
                                            ),
                                        ),
                                        color = locationTileContent,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = conditionLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.5.sp,
                                            lineHeight = 13.sp,
                                            letterSpacing = 0.sp,
                                            platformStyle = PlatformTextStyle(
                                                includeFontPadding = false,
                                            ),
                                        ),
                                        color = locationTileContent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val feelsLike = weather.apparentTemperatureCelsius
                                    val defaultSupportingText = if (feelsLike != null) {
                                        "Feels like ${feelsLike.roundToInt()}°C"
                                    } else {
                                        "${weather.relativeHumidity}% · $precipitationLabel"
                                    }
                                    val thresholdSupportingText = when {
                                        rainLevel != WeatherThresholdLevel.Normal ->
                                            "Rain ${weather.precipitationProbability}%"
                                        humidityLevel != WeatherThresholdLevel.Normal ->
                                            "Humidity ${weather.relativeHumidity}%"
                                        else -> null
                                    }
                                    var showThresholdText by remember(thresholdSupportingText) {
                                        mutableStateOf(false)
                                    }
                                    LaunchedEffect(thresholdSupportingText) {
                                        showThresholdText = false
                                        if (thresholdSupportingText != null) {
                                            while (true) {
                                                delay(4_500L)
                                                showThresholdText = true
                                                delay(3_500L)
                                                showThresholdText = false
                                            }
                                        }
                                    }
                                    // Measure both possible labels up front. This gives the
                                    // animated layer one stable slot instead of letting
                                    // AnimatedContent resize the weather column on every swap.
                                    Box(
                                        modifier = Modifier.height(14.dp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        listOfNotNull(
                                            defaultSupportingText,
                                            thresholdSupportingText,
                                        ).forEach { measuredText ->
                                            Text(
                                                text = measuredText,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.5.sp,
                                                    lineHeight = 13.sp,
                                                    letterSpacing = 0.sp,
                                                    platformStyle = PlatformTextStyle(
                                                        includeFontPadding = false,
                                                    ),
                                                ),
                                                maxLines = 1,
                                                modifier = Modifier
                                                    .alpha(0f)
                                                    .clearAndSetSemantics { },
                                            )
                                        }

                                        AnimatedContent(
                                            targetState = thresholdSupportingText
                                                ?.takeIf { showThresholdText }
                                                ?: defaultSupportingText,
                                            contentAlignment = Alignment.CenterStart,
                                            transitionSpec = {
                                                // Match the prayer-tile bell/weather morph:
                                                // fade through with a restrained scale, without
                                                // vertical travel that looks like a height change.
                                                (fadeIn(tween(220)) + scaleIn(
                                                    initialScale = 0.72f,
                                                    animationSpec = tween(
                                                        260,
                                                        easing = FastOutSlowInEasing,
                                                    ),
                                                )) togetherWith
                                                    (fadeOut(tween(150)) + scaleOut(
                                                        targetScale = 0.78f,
                                                        animationSpec = tween(190),
                                                    ))
                                            },
                                            modifier = Modifier.matchParentSize(),
                                            label = "locationWeatherSupportingText",
                                        ) { supportingText ->
                                        Text(
                                            text = supportingText,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.5.sp,
                                                lineHeight = 13.sp,
                                                letterSpacing = 0.sp,
                                                platformStyle = PlatformTextStyle(
                                                    includeFontPadding = false,
                                                ),
                                            ),
                                            color = locationTileSupporting,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        }
                                    }
                                }
                            }
                            }
                            CurrentWeatherLoadState.Unavailable -> Unit
                        }
                    }
                    }
                    }
                }
                }
                }

                Spacer(
                    modifier = Modifier.height(
                        // The floating bar already includes its own 8dp outer inset.
                        // Reserving another 10dp here left a large visible gutter
                        // below the final dashboard action in both layout states.
                        FloatingNavClearance - 6.dp - effectiveSyncBottomClearanceReclaim,
                    ),
                )
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

@Composable
private fun PrayerHeaderAction(
    active: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "prayerActionContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(durationMillis = 220),
        label = "prayerActionContent",
    )
    val iconColor = MaterialTheme.colorScheme.onPrimary
    val actionWidth by animateDpAsState(
        targetValue = when {
            compact && active -> 82.dp
            compact -> 126.dp
            active -> 96.dp
            else -> 148.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "prayerActionWidth",
    )

    Surface(
        modifier = Modifier
            .width(actionWidth)
            .height(if (compact) 36.dp else 44.dp),
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (active) 0.dp else 1.dp,
    ) {
        AnimatedContent(
            targetState = active,
            transitionSpec = {
                if (targetState) {
                    (
                        fadeIn(tween(durationMillis = 180, delayMillis = 40)) +
                            slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 5 }
                        ).togetherWith(
                        fadeOut(tween(durationMillis = 120)) +
                            slideOutVertically(tween(170)) { -it / 5 },
                    )
                } else {
                    (
                        fadeIn(tween(durationMillis = 180, delayMillis = 40)) +
                            slideInVertically(tween(220, easing = FastOutSlowInEasing)) { -it / 5 }
                        ).togetherWith(
                        fadeOut(tween(durationMillis = 120)) +
                            slideOutVertically(tween(170)) { it / 5 },
                    )
                }
            },
            contentAlignment = Alignment.Center,
            label = "prayerActionState",
        ) { isActive ->
            val stateIcon = if (isActive) Icons.Filled.Check else Icons.Outlined.Tune
            val stateLabel = if (isActive) "Done" else "Tune schedule"
            val iconContainerColor = if (isActive) {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.primary
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 5.dp else 6.dp, vertical = if (compact) 5.dp else 6.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 26.dp else 32.dp)
                        .clip(CircleShape)
                        .background(iconContainerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = stateIcon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(if (compact) 15.dp else 17.dp),
                    )
                }
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun weatherConditionLabel(weather: CurrentWeather): String = when (weather.weatherCode) {
    0 -> if (weather.isDay) "Clear sky" else "Clear night"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Foggy"
    in 51..57 -> "Drizzle"
    in 61..67 -> "Rain"
    in 71..77 -> "Snow"
    in 80..82 -> "Rain showers"
    85, 86 -> "Snow showers"
    in 95..99 -> "Thunderstorms"
    else -> "Cloudy"
}

@Composable
private fun weatherPlaceholderBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "weatherPlaceholder")
    val shimmerOffset by transition.animateFloat(
        initialValue = -180f,
        targetValue = 520f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "weatherPlaceholderOffset",
    )
    val placeholder = MaterialTheme.colorScheme.onSurfaceVariant
    return Brush.linearGradient(
        colors = listOf(
            placeholder.copy(alpha = 0.08f),
            placeholder.copy(alpha = 0.20f),
            placeholder.copy(alpha = 0.08f),
        ),
        start = Offset(shimmerOffset - 110f, 0f),
        end = Offset(shimmerOffset, 48f),
    )
}

@Composable
private fun WeatherPlaceholderBlock(
    modifier: Modifier,
    brush: Brush,
    cornerRadius: androidx.compose.ui.unit.Dp = 50.dp,
) {
    Box(
        modifier = modifier.background(
            brush = brush,
            shape = RoundedCornerShape(cornerRadius),
        ),
    )
}

@Composable
private fun PortraitWeatherLoadingPlaceholder() {
    val brush = weatherPlaceholderBrush()
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(6.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                WeatherPlaceholderBlock(Modifier.size(width = 52.dp, height = 8.dp), brush)
                WeatherPlaceholderBlock(Modifier.size(19.dp), brush)
                WeatherPlaceholderBlock(Modifier.size(width = 27.dp, height = 14.dp), brush)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                WeatherPlaceholderBlock(Modifier.size(16.dp), brush)
                WeatherPlaceholderBlock(Modifier.size(width = 22.dp, height = 7.dp), brush)
                Spacer(modifier = Modifier.width(2.dp))
                WeatherPlaceholderBlock(Modifier.size(16.dp), brush)
                WeatherPlaceholderBlock(Modifier.size(width = 36.dp, height = 7.dp), brush)
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        WeatherPlaceholderBlock(
            modifier = Modifier.size(width = 7.dp, height = 14.dp),
            brush = brush,
            cornerRadius = 4.dp,
        )
    }
}

@Composable
private fun LandscapeWeatherLineLoadingPlaceholder() {
    val brush = weatherPlaceholderBrush()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        WeatherPlaceholderBlock(Modifier.size(width = 48.dp, height = 7.dp), brush)
        WeatherPlaceholderBlock(Modifier.size(15.dp), brush)
        WeatherPlaceholderBlock(Modifier.size(width = 21.dp, height = 7.dp), brush)
        WeatherPlaceholderBlock(Modifier.size(15.dp), brush)
        WeatherPlaceholderBlock(Modifier.size(width = 34.dp, height = 7.dp), brush)
    }
}

@Composable
private fun LandscapeTemperatureLoadingPlaceholder() {
    val brush = weatherPlaceholderBrush()
    Row(
        modifier = Modifier.height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        WeatherPlaceholderBlock(Modifier.size(width = 25.dp, height = 15.dp), brush)
        WeatherPlaceholderBlock(Modifier.size(22.dp), brush)
    }
}

@Composable
private fun LandscapeLocationWeatherTile(
    locationString: String,
    locationData: com.starception.submission.prayer.model.Location?,
    thresholds: PrayerWeatherThresholds,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val markerPainter = remember(context) {
        BitmapPainter(
            image = ImageBitmap.imageResource(
                res = context.resources,
                id = R.drawable.ic_flaticon_location_marker,
            ),
            filterQuality = FilterQuality.High,
        )
    }

    val area = locationData?.area?.trim().takeUnless { it.isNullOrBlank() }
        ?: locationData?.subLocality?.trim().takeUnless { it.isNullOrBlank() }
    val city = locationData?.city?.trim().orEmpty()
    val placeLine = listOfNotNull(
        area,
        city.takeIf { it.isNotBlank() && !it.equals(area, ignoreCase = true) },
    ).joinToString(" · ").ifBlank {
        getLocationWithCountryCode(locationString, locationData)
    }
    val fallbackDetail = locationData?.countryCode?.trim()?.uppercase()
        ?.takeIf { it.length in 2..3 }
        ?: locationData?.country?.trim().orEmpty()

    val weatherState by rememberCurrentWeatherLoadState(locationData)

    Surface(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, primary.copy(alpha = 0.20f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = markerPainter,
                contentDescription = "Prayer location",
                colorFilter = ColorFilter.tint(primary),
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = placeLine,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Medium,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                when (val state = weatherState) {
                    CurrentWeatherLoadState.Loading -> {
                        LandscapeWeatherLineLoadingPlaceholder()
                    }
                    is CurrentWeatherLoadState.Available -> {
                    val current = state.weather
                    val conditionLabel = weatherConditionLabel(current)
                    val humidityLevel = humidityThresholdLevel(
                        value = current.relativeHumidity,
                        threshold = thresholds.humidity,
                    )
                    val rainLevel = rainThresholdLevel(
                        value = current.precipitationProbability,
                        threshold = thresholds.rainProbability,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "$conditionLabel ·",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                letterSpacing = 0.sp,
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                            ),
                            color = supportingColor,
                            maxLines = 1,
                        )
                        AnimatedPrayerWeatherIcon(
                            visual = PrayerWeatherVisual.Humidity,
                            level = humidityLevel,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "${current.relativeHumidity}% ·",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                letterSpacing = 0.sp,
                            ),
                            color = supportingColor,
                            maxLines = 1,
                        )
                        AnimatedPrayerWeatherIcon(
                            visual = PrayerWeatherVisual.Rain,
                            level = rainLevel,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = if (current.precipitationProbability == 0) {
                                "No rain"
                            } else {
                                "${current.precipitationProbability}% rain"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                letterSpacing = 0.sp,
                            ),
                            color = supportingColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    }
                    CurrentWeatherLoadState.Unavailable -> Text(
                        text = fallbackDetail,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                        color = supportingColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            when (val state = weatherState) {
                CurrentWeatherLoadState.Loading -> {
                    Spacer(modifier = Modifier.width(8.dp))
                    LandscapeTemperatureLoadingPlaceholder()
                }
                is CurrentWeatherLoadState.Available -> {
                val current = state.weather
                val temperatureLevel = temperatureThresholdLevel(
                    value = current.temperatureCelsius,
                    threshold = thresholds.temperatureCelsius,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .height(32.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = "${current.temperatureCelsius.roundToInt()}°C",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 19.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                        color = contentColor,
                        modifier = Modifier
                            .offset(y = 2.dp),
                    )
                    if (temperatureLevel == WeatherThresholdLevel.Normal) {
                        AnimatedCurrentWeatherIcon(
                            weather = current,
                            modifier = Modifier
                                .align(Alignment.Top)
                                .size(24.dp)
                                .offset(x = (-2).dp, y = 1.dp),
                        )
                    } else {
                        AnimatedPrayerWeatherIcon(
                            visual = PrayerWeatherVisual.Heat,
                            level = temperatureLevel,
                            modifier = Modifier
                                .align(Alignment.Top)
                                .size(24.dp)
                                .offset(x = (-2).dp, y = 1.dp),
                        )
                    }
                }
                }
                CurrentWeatherLoadState.Unavailable -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrayerWeatherThresholdSheet(
    thresholds: PrayerWeatherThresholds,
    onDismiss: () -> Unit,
    onSave: (PrayerWeatherThresholds) -> Unit,
) {
    var rain by remember(thresholds) { mutableIntStateOf(thresholds.rainProbability) }
    var humidity by remember(thresholds) { mutableIntStateOf(thresholds.humidity) }
    var temperature by remember(thresholds) { mutableIntStateOf(thresholds.temperatureCelsius) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = NiaBottomSheetDefaults.FloatingShape,
        containerColor = Color.Transparent,
        contentColor = NiaBottomSheetDefaults.contentColor(),
        scrimColor = NiaBottomSheetDefaults.scrimColor(),
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        NiaBottomSheetTheme {
            NiaBottomSheetFrame {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                ) {
                    NiaBottomSheetDragHandle(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 10.dp, bottom = 18.dp),
                    )

                    Text(
                        text = "Prayer weather alerts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Choose when forecast guidance should appear with an upcoming prayer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "Alert thresholds",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Guidance appears when any threshold is reached.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                        ),
                    ) {
                        Column {
                            WeatherThresholdControl(
                                weatherVisual = PrayerWeatherVisual.Rain,
                                label = "Rain chance",
                                helperText = "Alert at or above",
                                value = rain,
                                valueText = "$rain%",
                                range = 0..100,
                                step = 5,
                                onValueChange = { rain = it },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 76.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                            )
                            WeatherThresholdControl(
                                weatherVisual = PrayerWeatherVisual.Humidity,
                                label = "Humidity",
                                helperText = "Alert at or above",
                                value = humidity,
                                valueText = "$humidity%",
                                range = 0..99,
                                step = 5,
                                onValueChange = { humidity = it },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 76.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                            )
                            WeatherThresholdControl(
                                weatherVisual = PrayerWeatherVisual.Heat,
                                label = "Temperature",
                                helperText = "Alert at or above",
                                value = temperature,
                                valueText = "$temperature°C",
                                range = 20..50,
                                step = 1,
                                onValueChange = { temperature = it },
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        NiaOutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Text("Cancel")
                        }
                        NiaOutlinedButton(
                            onClick = {
                                onSave(
                                    PrayerWeatherThresholds(
                                        rainProbability = rain,
                                        humidity = humidity,
                                        temperatureCelsius = temperature,
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherThresholdControl(
    weatherVisual: PrayerWeatherVisual,
    label: String,
    helperText: String,
    value: Int,
    valueText: String,
    range: IntRange,
    step: Int,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedPrayerWeatherIcon(
            visual = weatherVisual,
            level = weatherThresholdPreviewLevel(weatherVisual, value),
            // Meteocons include breathing room in their animation canvas. A
            // 48dp host gives the visible artwork the same presence as the
            // 42dp threshold control without making the row feel oversized.
            modifier = Modifier.size(48.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.height(42.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        val updated = value.minus(step).coerceAtLeast(range.first)
                        onValueChange(updated)
                    },
                    enabled = value > range.first,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease $label",
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(52.dp),
                )
                IconButton(
                    onClick = {
                        val updated = value.plus(step).coerceAtMost(range.last)
                        onValueChange(updated)
                    },
                    enabled = value < range.last,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase $label",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
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

    // This label is rendered on one line beside the location icon. Keep the
    // formatter honest about the available width so it chooses the compact
    // area/city/country-code form before Compose has to add an ellipsis.
    val maxLength = 35

    // Extract available fields
    val area = locationData.area.takeIf { it.isNotEmpty() }
    val subLocality = locationData.subLocality.takeIf { it.isNotEmpty() }
    val city = locationData.city.takeIf { it.isNotEmpty() }
    val country = locationData.country.takeIf { it.isNotEmpty() }
    val countryCode = locationData.countryCode.takeIf { it.isNotEmpty() }

    // Keep area, city, and country in a single stable line. The UI truncates
    // the end with an ellipsis when the full value is wider than its card.
    val detailedArea = area ?: subLocality
    if (detailedArea != null) {
        val placeLine = listOfNotNull(city, country ?: countryCode)
            .distinct()
            .joinToString(", ")
        return if (placeLine.isNotEmpty()) "$detailedArea · $placeLine" else detailedArea
    }

    // PRIORITY 1: Area + City + Country + Country Code (most detailed)
    if (area != null && city != null && country != null && countryCode != null) {
        val format1 = "$area, $city, $country ($countryCode)"
        if (format1.length <= maxLength) {
            android.util.Log.i("LocationDisplay", "   ✅ P1: '$format1' (${format1.length} chars)")
            return format1
        }
        android.util.Log.d("LocationDisplay", "   ❌ P1 too long: $format1 (${format1.length} chars)")
    }

    // Keep the country visible when the detailed address is too long. This
    // gives the location tile a useful, stable identity instead of reducing it
    // to a city or two-letter country code.
    if (city != null && country != null) {
        val cityAndCountry = "$city, $country"
        if (cityAndCountry.length <= maxLength) {
            android.util.Log.i("LocationDisplay", "   ✅ City + country: '$cityAndCountry'")
            return cityAndCountry
        }
    }

    if (country != null) {
        android.util.Log.i("LocationDisplay", "   ✅ Country: '$country'")
        return country
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
