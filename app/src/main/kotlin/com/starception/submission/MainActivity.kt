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
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
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
import com.starception.submission.core.ui.LocalTimeZone
import com.starception.submission.ui.NiaApp
import com.starception.submission.ui.rememberNiaAppState
import com.starception.submission.services.PrayerNotificationService
import com.starception.submission.util.isSystemInDarkTheme
import com.starception.submission.util.PermissionManager
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

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

    // EMERGENCY FIX: ViewModel disabled to prevent main thread blocking
    // TODO: Re-enable after fixing performance issues
    // private var viewModel: MainActivityViewModel? = null
    
    // PERMISSION MANAGEMENT - Handles location and notification permissions for prayer features
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("MainActivity", "EMERGENCY FIX: Skip splash screen completely")
        
        super.onCreate(savedInstanceState)
        
        // EDGE-TO-EDGE DISPLAY - Modern Android UI extending behind system bars
        enableEdgeToEdge()

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
        val viewModel: MainActivityViewModel by viewModels()
        
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val isSystemDarkTheme = resources.configuration.isSystemInDarkTheme
            
            NiaTheme(
                darkTheme = when (uiState) {
                    is MainActivityUiState.Success -> uiState.shouldUseDarkTheme(isSystemDarkTheme)
                    is MainActivityUiState.Loading -> isSystemDarkTheme
                },
                androidTheme = when (uiState) {
                    is MainActivityUiState.Success -> uiState.shouldUseAndroidTheme
                    is MainActivityUiState.Loading -> false
                },
                disableDynamicTheming = when (uiState) {
                    is MainActivityUiState.Success -> uiState.shouldDisableDynamicTheming
                    is MainActivityUiState.Loading -> true
                },
            ) {
                val appState = rememberNiaAppState(
                    networkMonitor = networkMonitor,
                    userNewsResourceRepository = userNewsResourceRepository,
                    timeZoneMonitor = timeZoneMonitor,
                )
                
                val currentTimeZone by appState.currentTimeZone.collectAsStateWithLifecycle()

                CompositionLocalProvider(
                    LocalAnalyticsHelper provides analyticsHelper,
                    LocalTimeZone provides currentTimeZone,
                ) {
                    NiaApp(appState)
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
        }
    }

    override fun onResume() {
        super.onResume()
        
        // NON-BLOCKING: Access JankStats lazily in background
        lifecycleScope.launch {
            try {
                lazyStats.get().isTrackingEnabled = true
                Log.d("MainActivity", "JankStats enabled successfully")
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to enable JankStats", e)
            }
        }
        
        // SAFE: Start prayer service after longer delay to prevent ANR
        lifecycleScope.launch {
            delay(5000) // Wait 5 seconds for app to be fully stable
            try {
                Log.d("MainActivity", "Starting prayer service after delay")
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
        Log.d("MainActivity", "Starting prayer service in background")
        
        lifecycleScope.launch {
            try {
                // Check if service is already running
                if (PrayerNotificationService.isServiceRunningInAnotherProcess(this@MainActivity)) {
                    Log.d("MainActivity", "Prayer service already running")
                    return@launch
                }
                
                // Start service
                val intent = Intent(this@MainActivity, PrayerNotificationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Log.d("MainActivity", "Prayer service started successfully")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error starting prayer service", e)
            }
        }
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
