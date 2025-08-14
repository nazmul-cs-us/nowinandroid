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

package com.starception.dua

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
import com.starception.dua.MainActivityUiState.Loading
import com.starception.dua.core.analytics.AnalyticsHelper
import com.starception.dua.core.analytics.LocalAnalyticsHelper
import com.starception.dua.core.data.repository.UserNewsResourceRepository
import com.starception.dua.core.data.util.NetworkMonitor
import com.starception.dua.core.data.util.TimeZoneMonitor
import com.starception.dua.core.designsystem.theme.NiaTheme
import com.starception.dua.core.ui.LocalTimeZone
import com.starception.dua.core.ui.TrackDisposableJank
import com.starception.dua.navigation.NiaNavHost
import com.starception.dua.ui.NiaApp
import com.starception.dua.ui.NiaAppState
import com.starception.dua.ui.NiaAppState.Companion.savedStateHandle
import com.starception.dua.ui.rememberNiaAppState
import com.starception.dua.util.AnrPreventionConfig
import com.starception.dua.services.PrayerNotificationService
import com.starception.dua.util.isSystemInDarkTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.delay


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Lazily inject [JankStats], which is used to track jank throughout the app.
     * Using lazy injection to prevent main thread blocking during startup
     */
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

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("MainActivity", "onCreate started with modern Hilt optimization")
        
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // We keep this as a mutable state, so that we can track changes inside the composition.
        // This allows us to react to dark/light mode changes.
        var themeSettings by mutableStateOf(
            ThemeSettings(
                darkTheme = resources.configuration.isSystemInDarkTheme,
                androidTheme = Loading.shouldUseAndroidTheme,
                disableDynamicTheming = Loading.shouldDisableDynamicTheming,
            ),
        )

        // Update the uiState with modern optimization patterns
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    isSystemInDarkTheme(),
                    viewModel.uiState,
                ) { systemDark, uiState ->
                    ThemeSettings(
                        darkTheme = uiState.shouldUseDarkTheme(systemDark),
                        androidTheme = uiState.shouldUseAndroidTheme,
                        disableDynamicTheming = uiState.shouldDisableDynamicTheming,
                    )
                }
                    .onEach { themeSettings = it }
                    .map { it.darkTheme }
                    .distinctUntilChanged()
                    .collect { darkTheme ->
                        trace("niaEdgeToEdge") {
                            // Turn off the decor fitting system windows, which allows us to handle insets,
                            // including IME animations, and go edge-to-edge.
                            // This is the same parameters as the default enableEdgeToEdge call, but we manually
                            // resolve whether or not to show dark theme using uiState, since it can be different
                            // than the configuration's dark theme value based on the user preference.
                            enableEdgeToEdge(
                                statusBarStyle = SystemBarStyle.auto(
                                    lightScrim = android.graphics.Color.TRANSPARENT,
                                    darkScrim = android.graphics.Color.TRANSPARENT,
                                ) { darkTheme },
                                navigationBarStyle = SystemBarStyle.auto(
                                    lightScrim = lightScrim,
                                    darkScrim = darkScrim,
                                ) { darkTheme },
                            )
                        }
                    }
            }
        }

        // Keep the splash screen on-screen until the UI state is loaded. This condition is
        // evaluated each time the app needs to be redrawn so it should be fast to avoid blocking
        // the UI.
        var splashStartTime = System.currentTimeMillis()
        splashScreen.setKeepOnScreenCondition { 
            val shouldKeep = viewModel.uiState.value.shouldKeepSplashScreen()
            val splashDuration = System.currentTimeMillis() - splashStartTime
            
            // Add fallback: force hide splash screen after 15 seconds to prevent getting stuck
            val forceHide = splashDuration > 15000
            
            if (forceHide) {
                Log.w("MainActivity", "Forcing splash screen to hide after ${splashDuration}ms timeout")
            }
            
            Log.d("MainActivity", "Splash screen condition: $shouldKeep, duration: ${splashDuration}ms, forceHide: $forceHide")
            
            shouldKeep && !forceHide
        }
        
        // Monitor UI state changes to start service once splash screen is hidden
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is MainActivityUiState.Success -> {
                            Log.d("MainActivity", "UI loaded successfully, waiting before starting service")
                            
                            // Wait a bit more to ensure UI is fully stable
                            delay(2000)
                            
                            // Only start service if not already running
                            if (!PrayerNotificationService.isServiceRunningInAnotherProcess(this@MainActivity)) {
                                startPrayerServiceIfNeeded()
                                
                                Log.d("MainActivity", "Prayer service started after UI load")
                            } else {
                                Log.d("MainActivity", "Prayer service already running in another process")
                            }
                        }
                        is MainActivityUiState.Loading -> {
                            Log.d("MainActivity", "UI still loading, waiting...")
                        }
                    }
                }
            }
        }

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
                    darkTheme = themeSettings.darkTheme,
                    androidTheme = themeSettings.androidTheme,
                    disableDynamicTheming = themeSettings.disableDynamicTheming,
                ) {
                    // Full app functionality restored
                    NiaApp(appState)
                }
            }
        }
        
        Log.d("MainActivity", "Modern onCreate completed with optimized Hilt")
    }

    override fun onResume() {
        super.onResume()
        
        // Use lazy stats access to prevent blocking
        lazyStats.get().isTrackingEnabled = true
        
        Log.d("MainActivity", "onResume completed with modern optimization")
    }
    
    /**
     * Start prayer notification service when appropriate
     * Enabled for notifications while maintaining ANR prevention
     */
    private fun startPrayerServiceIfNeeded() {
        Log.d("MainActivity", "Starting prayer service for notifications")
        
        // Check if service is already running to prevent conflicts
        if (PrayerNotificationService.isServiceRunningInAnotherProcess(this)) {
            Log.w("MainActivity", "Service already running in another process, skipping startup to prevent conflicts")
            return
        }
        
        // Start the service in a background coroutine to prevent ANR
        lifecycleScope.launch {
            try {
                // Add delay to ensure UI is fully loaded before starting service
                delay(1000)
                
                // Start service in background to prevent main thread blocking
                val intent = Intent(this@MainActivity, PrayerNotificationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Log.d("MainActivity", "Prayer service started in background for notifications")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error starting prayer service", e)
            }
        }
    }
    

    override fun onPause() {
        super.onPause()
        
        // Use lazy stats access to prevent blocking  
        lazyStats.get().isTrackingEnabled = false
        
        Log.d("MainActivity", "onPause completed with modern optimization")
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
