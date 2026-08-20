/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.starception.submission.download.AssetDownloadScreen
import com.starception.submission.download.DownloadScreenState
import androidx.compose.ui.Modifier
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.metrics.performance.JankStats
import androidx.tracing.trace
import androidx.fragment.app.FragmentActivity
import com.starception.submission.MainActivityUiState.Loading
import com.starception.submission.core.analytics.AnalyticsHelper
import com.starception.submission.core.analytics.LocalAnalyticsHelper
import com.starception.submission.core.data.repository.UserNewsResourceRepository
import com.starception.submission.core.data.util.NetworkMonitor
import com.starception.submission.core.data.util.TimeZoneMonitor
import com.starception.submission.core.designsystem.theme.NiaTheme
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.core.ui.LocalTimeZone
import com.starception.submission.ui.NiaApp
import com.starception.submission.widget.WidgetNavigationBus
import com.starception.submission.ui.rememberNiaAppState
import com.starception.submission.services.PrayerNotificationService
import com.starception.submission.prayer.repository.PrayerSettingsRepository
import com.starception.submission.prayer.service.CountryPrayerMethodService
import android.location.Location as AndroidLocation
import com.starception.submission.download.AssetDownloadViewModel
import com.starception.submission.util.isSystemInDarkTheme
import com.starception.submission.util.PermissionManager
import com.starception.submission.util.ActivityBasedDuaHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.delay
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.remember
import com.starception.submission.util.GoogleSampleNotificationManager
import android.app.NotificationManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * MAIN ACTIVITY: Entry point for the Islamic prayer times app
 * 
 * This activity serves as the foundation for the entire application, handling:
 * 
 * CURRENT STATE (Emergency Fix Mode):
 * - Simplified startup to prevent ANR (Application Not Responding)
 * - Lazy injection to avoid main thread blocking
 * - Disabled splash screen for faster startup
 * - Minimal permission handling
 * 
 * APP COORDINATION:
 * - Hosts the main Compose UI with NiaApp
 * - Manages system services (analytics, network monitoring)
 * - Handles theme and edge-to-edge display
 * 
 * PRAYER FEATURES:
 * - Initializes prayer notification service
 * - Manages location and notification permissions
 * - Coordinates between prayer times and main app
 * 
 * EDIT THIS TO:
 * - Re-enable features after fixing performance issues
 * - Add new initialization steps
 * - Modify service startup sequence
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainActivityViewModel by lazy {
        ViewModelProvider(this)[MainActivityViewModel::class.java]
    }

    // Volume keys turn Mushaf pages while the Surah reader is in foreground.
    // Convention: VOLUME_DOWN = forward (next page), VOLUME_UP = back. The
    // MushafKeyBus is bound only while MushafPagerView is composed, so volume
    // keeps its normal function on every other screen.
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (com.starception.submission.feature.surah.MushafKeyBus.handleNext()) return true
            }
            android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                if (com.starception.submission.feature.surah.MushafKeyBus.handlePrev()) return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // LAZY DEPENDENCY INJECTION - Prevents main thread blocking during startup
    @Inject
    lateinit var lazyStats: dagger.Lazy<JankStats>        // Performance monitoring (lazy loaded)

    @Inject
    lateinit var networkMonitor: NetworkMonitor           // Internet connectivity monitoring

    @Inject
    lateinit var timeZoneMonitor: TimeZoneMonitor        // System timezone change detection

    @Inject
    lateinit var analyticsHelper: AnalyticsHelper        // App usage analytics

    @Inject
    lateinit var userNewsResourceRepository: UserNewsResourceRepository  // News data access

    @Inject
    lateinit var prayerNotificationServiceManager: com.starception.submission.prayer.service.PrayerNotificationServiceManager

    @Inject
    lateinit var prayerSettingsRepository: PrayerSettingsRepository

    // EMERGENCY FIX: ViewModel disabled to prevent main thread blocking
    // TODO: Re-enable after fixing performance issues
    // private var viewModel: MainActivityViewModel? = null
    
    // PERMISSION MANAGEMENT - Handles location and notification permissions for prayer features
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("MainActivity", "═══════════════════════════════════════════")
        Log.d("MainActivity", "🏠 MAIN ACTIVITY onCreate START")
        Log.d("MainActivity", "═══════════════════════════════════════════")
        val isNightMode = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        Log.d("MainActivity", "   • System dark mode: $isNightMode")
        Log.d("MainActivity", "   • savedInstanceState: ${if (savedInstanceState != null) "EXISTS" else "NULL"}")

        super.onCreate(savedInstanceState)
        
        // EDGE-TO-EDGE DISPLAY - Modern Android UI extending behind system bars
        enableEdgeToEdge()
        
        // Initialize ActivityTracker to load saved notification mode preference and start detection
        com.starception.submission.util.ActivityTracker.initialize(this, startDetectionNow = true)
        Log.d("MainActivity", "✅ ActivityTracker initialized with activity detection started")

        // Schedule WorkManager to keep activity detection alive (survives Doze mode)
        com.starception.submission.worker.ActivityDetectionWorker.schedule(this)
        Log.d("MainActivity", "✅ Activity detection keep-alive worker scheduled")

        // PERMISSION HANDLING (Currently disabled for performance)
        // TODO: Re-enable after optimizing to prevent ANR
        // lifecycleScope.launch {
        //     delay(2000)  // Wait 2 seconds before requesting permissions
        //     try {
        //         permissionManager = PermissionManager(this@MainActivity)
        //         permissionManager.checkAndRequestLocationPermissions()  // For accurate prayer times
        //     } catch (e: Exception) {
        //         Log.e("MainActivity", "Error initializing permissions", e)
        //     }
        // }
        
        // DISABLED: No ViewModel initialization to prevent any blocking
        // lifecycleScope.launch {
        //     delay(3000)
        //     try {
        //         viewModel = viewModels<MainActivityViewModel>().value
        //         Log.d("MainActivity", "ViewModel initialized for theme support")
        //     } catch (e: Exception) {
        //         Log.w("MainActivity", "ViewModel initialization failed, using static theme", e)
        //     }
        // }

        // Initialize ViewModel for theme handling (but without splash screen blocking)
        val downloadViewModel: AssetDownloadViewModel by viewModels()
        // Handle deep link for course sharing
        val deepLinkCourseId = handleCourseDeepLink(intent)

        // A tap on the Daily Reminder widget names the hadith or dua it was showing.
        // Posted to a bus rather than threaded down as another parameter: the
        // NavController that can act on it is several composables below here, and
        // deepLinkCourseId already shows what that plumbing costs.
        WidgetNavigationBus.consume(intent)?.let(WidgetNavigationBus::request)

        // GOOGLE SAMPLE LIVE UPDATE - Initialize for prayer notifications
        if (Build.VERSION.SDK_INT >= 35) { // Android 16+ (BAKLAVA = 35)
            lifecycleScope.launch {
                delay(2000) // Wait 2 seconds after app start
                try {
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    GoogleSampleNotificationManager.initialize(this@MainActivity, notificationManager)
                    Log.d("MainActivity", "🧪 GOOGLE SAMPLE: Initialized for prayer notifications")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to initialize Google sample notification manager: ${e.message}")
                }
            }
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            // Compose-reactive variant — re-evaluates when the system flips uiMode,
            // unlike `resources.configuration.isSystemInDarkTheme` which only reads
            // the Configuration once at composition and leaves the body theme stale
            // after dark↔light switches.
            val isSystemDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

            // IMPORTANT: rememberNiaAppState must be OUTSIDE NiaTheme to prevent
            // navigation state loss when theme changes. Theme recomposition inside
            // NiaTheme was causing the NavHost to be recreated, losing the Settings screen.
            val appState = rememberNiaAppState(
                networkMonitor = networkMonitor,
                userNewsResourceRepository = userNewsResourceRepository,
                timeZoneMonitor = timeZoneMonitor,
            )

            val currentTimeZone by appState.currentTimeZone.collectAsStateWithLifecycle()

            val resolvedDarkTheme = when (uiState) {
                is MainActivityUiState.Success -> (uiState as MainActivityUiState.Success).shouldUseDarkTheme(isSystemDarkTheme)
                is MainActivityUiState.Loading -> isSystemDarkTheme
            }
            val resolvedBrand = when (uiState) {
                is MainActivityUiState.Success -> (uiState as MainActivityUiState.Success).themeBrand
                is MainActivityUiState.Loading -> ThemeBrand.COASTAL
            }
            val resolvedDisableDynamic = when (uiState) {
                is MainActivityUiState.Success -> (uiState as MainActivityUiState.Success).shouldDisableDynamicTheming
                is MainActivityUiState.Loading -> true
            }
            val resolvedCustomSeed = when (uiState) {
                is MainActivityUiState.Success -> (uiState as MainActivityUiState.Success).customThemeColor
                is MainActivityUiState.Loading -> 0
            }
            val resolvedCustomSecondary = when (uiState) {
                is MainActivityUiState.Success -> (uiState as MainActivityUiState.Success).customSecondaryColor
                is MainActivityUiState.Loading -> 0
            }
            val resolvedCustomTertiary = when (uiState) {
                is MainActivityUiState.Success -> (uiState as MainActivityUiState.Success).customTertiaryColor
                is MainActivityUiState.Loading -> 0
            }
            // Mirror the active theme into ThemeColorBridge so inner ComposeViews
            // (inside the SearchBar's AndroidView island) can re-apply the same
            // brand instead of falling back to NiaTheme's COASTAL default.
            androidx.compose.runtime.LaunchedEffect(
                resolvedBrand,
                resolvedDarkTheme,
                resolvedDisableDynamic,
                resolvedCustomSeed,
                resolvedCustomSecondary,
                resolvedCustomTertiary,
            ) {
                com.starception.submission.util.ThemeColorBridge.updateThemeConfig(
                    brand = resolvedBrand,
                    darkTheme = resolvedDarkTheme,
                    disableDynamicTheming = resolvedDisableDynamic,
                    customSeedColor = resolvedCustomSeed,
                    customSecondaryColor = resolvedCustomSecondary,
                    customTertiaryColor = resolvedCustomTertiary,
                )
            }
            NiaTheme(
                darkTheme = resolvedDarkTheme,
                themeBrand = resolvedBrand,
                customSeedColor = if (resolvedCustomSeed != 0) androidx.compose.ui.graphics.Color(resolvedCustomSeed) else androidx.compose.ui.graphics.Color.Unspecified,
                customSecondaryColor = if (resolvedCustomSecondary != 0) androidx.compose.ui.graphics.Color(resolvedCustomSecondary) else androidx.compose.ui.graphics.Color.Unspecified,
                customTertiaryColor = if (resolvedCustomTertiary != 0) androidx.compose.ui.graphics.Color(resolvedCustomTertiary) else androidx.compose.ui.graphics.Color.Unspecified,
                disableDynamicTheming = resolvedDisableDynamic,
            ) {
                // Update theme color bridge so View-based components can access theme colors
                com.starception.submission.util.ThemeColorBridge.UpdateColors()

                // First-launch content setup: block ONLY when required content is missing
                // (NeedsDownload). On AllReady/Loading/Error we open straight to the app, so normal
                // launches have no delay. Once setup is done (downloaded or skipped → onReady) we
                // never show it again this session.
                val contentState by downloadViewModel.screenState.collectAsStateWithLifecycle()
                var contentSetupDone by rememberSaveable { mutableStateOf(false) }
                val showContentSetup = contentState is DownloadScreenState.NeedsDownload && !contentSetupDone

                if (showContentSetup) {
                    // Re-check on resume so a decision made in init() while assets were still
                    // settling self-corrects to AllReady once required content is actually present.
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { downloadViewModel.recheck() }
                    AssetDownloadScreen(
                        viewModel = downloadViewModel,
                        onReady = { contentSetupDone = true },
                    )
                } else {
                    CompositionLocalProvider(
                        LocalAnalyticsHelper provides analyticsHelper,
                        LocalTimeZone provides currentTimeZone,
                    ) {
                        NiaApp(
                            appState = appState,
                            mainViewModel = viewModel,
                            deepLinkCourseId = deepLinkCourseId,
                        )
                    }
                }
            }
        }
        
        Log.d("MainActivity", "ULTRA-MINIMAL onCreate completed")
    }

    // REMOVED: Helper function not needed for minimal version
    /**
     * Handle permission request results
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            PermissionManager.LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                    Log.d("MainActivity", "Location permissions granted")
                    // Check if location services are enabled
                    permissionManager.checkLocationServices()
                } else {
                    Log.d("MainActivity", "Location permissions denied")
                }
            }
            PermissionManager.NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.d("MainActivity", "Notification permission granted")
                } else {
                    Log.d("MainActivity", "Notification permission denied")
                }
            }
            PermissionManager.ACTIVITY_RECOGNITION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.d("MainActivity", "Activity recognition permission granted")
                } else {
                    Log.d("MainActivity", "Activity recognition permission denied")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshMissingBukhariPrompt()

        // NON-BLOCKING: Access JankStats lazily in background
        lifecycleScope.launch {
            try {
                lazyStats.get().isTrackingEnabled = true
                Log.d("MainActivity", "JankStats enabled successfully")
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to enable JankStats", e)
            }
        }

        // SAFE: Start prayer service after delay to prevent ANR
        lifecycleScope.launch {
            delay(3000) // Wait 3 seconds for app to be stable
            try {
                Log.d("MainActivity", "App is ready, checking prayer service status")
                startPrayerServiceIfNeeded()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to start prayer service, continuing without it", e)
                // Don't crash if service fails - just continue without notifications
            }
        }

        Log.d("MainActivity", "NON-BLOCKING onResume completed")
    }

    override fun onPause() {
        super.onPause()
        
        // NON-BLOCKING: Access JankStats lazily in background
        lifecycleScope.launch {
            try {
                lazyStats.get().isTrackingEnabled = false
                Log.d("MainActivity", "JankStats disabled successfully")
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to disable JankStats", e)
            }
        }
        
        Log.d("MainActivity", "NON-BLOCKING onPause completed")
    }
    
    /**
     * NON-BLOCKING: Start prayer service in background coroutine
     */
    private fun startPrayerServiceIfNeeded() {
        Log.d("MainActivity", "🚀 Starting prayer notification system in background")
        
        lifecycleScope.launch {
            try {
                // Start auto-detection FIRST (independent of service and settings dialog)
                Log.d("MainActivity", "📍 Step 1: Starting location-based auto-detection...")
                startLocationBasedAutoDetection()
                Log.d("MainActivity", "✅ Step 1: Auto-detection completed")
                
                // Initialize the complete prayer notification system
                // This includes both foreground service and backup notifications
                Log.d("MainActivity", "⏰ Step 2: Initializing prayer notification system...")
                prayerNotificationServiceManager.initializeNotificationSystem()
                Log.d("MainActivity", "✅ Step 2: Notification system initialized")
                
                // DISABLED: Activity detection now merged into PrayerNotificationService
                // The sensor HandlerThread is created in PrayerNotificationService.onCreate() and passed
                // to ActivityTracker via setSensorHandler() for reliable background sensor operation.
                // This eliminates the need for a separate ActivityBasedDuaService.
                // Previous approach had two foreground services, but sharing notification IDs caused issues.
                // Now: Single unified foreground service with integrated activity detection.
                Log.d("MainActivity", "🎯 Step 3: Activity detection integrated into prayer service (no separate service)")

                Log.d("MainActivity", "🎉 Prayer notification system initialized successfully!")
                Log.d("MainActivity", "   - Foreground service: Running (with integrated activity detection)")
                Log.d("MainActivity", "   - Backup notifications: Scheduled")
                Log.d("MainActivity", "   - Boot receiver: Ready")
                Log.d("MainActivity", "   - Activity detection: Running (via PrayerNotificationService HandlerThread)")
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Error starting prayer notification system", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Start activity detection service for automatic dua playing
     */
    private fun startActivityDetectionService() {
        try {
            ActivityBasedDuaHelper.startActivityDetection(this)
            Log.d("MainActivity", "✅ Activity detection service started successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error starting activity detection service", e)
        }
    }
    
    /**
     * Start location-based auto-detection independent of Prayer Settings dialog
     * This runs once at app startup to configure prayer methods automatically
     */
    private suspend fun startLocationBasedAutoDetection() {
        try {
            Log.d("MainActivity", "🌍 Starting location-based auto-detection")
            
            // Use the Hilt singleton repository (per-country store lives here too)
            val settingsRepository = prayerSettingsRepository
            val countryService = CountryPrayerMethodService(this)
            
            // Read the location only. The repository owns the consent decision; calculation
            // method values must never be used here to bypass that country-switch flow.
            val currentSettings = settingsRepository.getSettings()

            // Get current location from settings or location services
            val location = currentSettings.location
            if (location == null) {
                Log.d("MainActivity", "📍 No location available for auto-detection")
                return
            }
            
            Log.d("MainActivity", "📍 Auto-detecting for location: ${location.getDisplayName()}")
            
            // Detect country and prayer method with timeout
            withContext(Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(5000L) {
                    // Create Android Location from our Location model
                    val androidLocation = AndroidLocation("").apply {
                        latitude = location.latitude
                        longitude = location.longitude
                    }
                    
                    val detectionResult = countryService.getPrayerMethodForLocation(androidLocation)
                    
                    if (detectionResult.isAutoDetected) {
                        Log.i("MainActivity", "🎯 Auto-detection successful: ${detectionResult.countryName}")
                        Log.i("MainActivity", "🕌 Method: ${detectionResult.calculationMethod.name}")
                        Log.i("MainActivity", "📿 Madhhab: ${detectionResult.madhhab.name}")
                        
                        // This initializes a first-ever country, or publishes a proposal when the
                        // detected country differs from the active one. Only the sheet's Apply
                        // action is allowed to mutate settings on a later country change.
                        settingsRepository.onCountryDetected(detectionResult.countryCode)
                        Log.i("MainActivity", "✅ Country detection handed to consent flow")
                    } else {
                        Log.d("MainActivity", "ℹ️ No auto-detection available for this location")
                    }
                } ?: run {
                    Log.w("MainActivity", "⚠️ Auto-detection timed out")
                }
            }
            
        } catch (e: Exception) {
            Log.w("MainActivity", "Auto-detection failed: ${e.message}")
        }
    }

    /**
     * A widget tap that arrives while the app is already running.
     *
     * onCreate only sees the launch intent, so without this a tap on the reminder while
     * the app sits in the background would bring it forward on whatever screen it was
     * last on and ignore the hadith entirely.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        WidgetNavigationBus.consume(intent)?.let(WidgetNavigationBus::request)
    }

    /**
     * Handle course deep link from share intent
     * Deep link format: starception://course/{courseId}
     */
    private fun handleCourseDeepLink(intent: Intent?): String? {
        if (intent == null) return null

        val data = intent.data ?: return null
        Log.d("MainActivity", "🔗 Deep link received: $data")

        // Check if this is a course deep link
        if (data.scheme == "starception" && data.host == "course") {
            val courseId = data.pathSegments.firstOrNull()
            if (courseId != null) {
                Log.d("MainActivity", "📚 Opening course: $courseId")
                return courseId
            }
        }
        return null
    }
}

/**
 * The default light scrim, as defined by androidx and the platform:
 * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:activity/activity/src/main/java/androidx/activity/EdgeToEdge.kt;l=35-38;drc=27e7d52e8604a080133e8b842db10c89b4482598
 */
private val lightScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)

/**
 * The default dark scrim, as defined by androidx and the platform:
 * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:activity/activity/src/main/java/androidx/activity/EdgeToEdge.kt;l=40-44;drc=27e7d52e8604a080133e8b842db10c89b4482598
 */
private val darkScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/**
 * Class for the system theme settings.
 * This wrapping class allows us to combine all the changes and prevent unnecessary recompositions.
 */
data class ThemeSettings(
    val darkTheme: Boolean,
    val androidTheme: Boolean,
    val disableDynamicTheming: Boolean,
)
