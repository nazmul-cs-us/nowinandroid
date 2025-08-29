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

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    // NON-BLOCKING FIX: Use lazy injection to prevent main thread blocking
    @Inject
    lateinit var lazyStats: dagger.Lazy<JankStats>

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var timeZoneMonitor: TimeZoneMonitor

    @Inject
    lateinit var analyticsHelper: AnalyticsHelper

    @Inject
    lateinit var userNewsResourceRepository: UserNewsResourceRepository

    // ULTRA-MINIMAL: Remove ViewModel entirely - it's causing blocking during by viewModels()
    // private val viewModel: MainActivityViewModel by viewModels()
    
    // Permission manager for handling location and notification permissions
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("MainActivity", "ULTRA-MINIMAL onCreate - no ViewModel, no blocking operations")
        
        // ULTRA-MINIMAL: Absolute minimum onCreate
        super.onCreate(savedInstanceState)
        
        // Basic edge to edge
        enableEdgeToEdge()

        // Initialize permission manager in background to avoid blocking
        lifecycleScope.launch {
            delay(2000) // Wait longer for UI to be fully stable
            try {
                permissionManager = PermissionManager(this@MainActivity)
                permissionManager.checkAndRequestLocationPermissions()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error initializing permissions", e)
            }
        }

        // MINIMAL: Static theme settings to prevent any blocking
        setContent {
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
                NiaTheme(
                    darkTheme = resources.configuration.isSystemInDarkTheme,
                    androidTheme = false,
                    disableDynamicTheming = true,
                ) {
                    NiaApp(appState)
                }
            }
        }
        
        Log.d("MainActivity", "ULTRA-MINIMAL onCreate completed")
    }
    
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
        
        // NON-BLOCKING: Start service in background after UI is stable
        lifecycleScope.launch {
            delay(1000) // Wait for UI to be fully loaded
            startPrayerServiceIfNeeded()
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
