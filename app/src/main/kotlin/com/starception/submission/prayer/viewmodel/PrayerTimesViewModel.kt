package com.starception.submission.prayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.prayer.model.AsrMadhhab
import com.starception.submission.prayer.model.AutoDetectedSettingsBackup
import com.starception.submission.prayer.model.CalculationMethod
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.Location
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.prayer.repository.PrayerSettingsRepository
import com.starception.submission.prayer.service.LocationService
import com.starception.submission.prayer.service.EnhancedLocationService
import com.starception.submission.prayer.service.PrayerTimeCalculatorService
import com.starception.submission.prayer.service.CountryPrayerMethodService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject

/**
 * PRAYER TIMES VIEW MODEL: Core state management for prayer times feature
 * 
 * This ViewModel orchestrates all prayer times functionality, managing state between
 * UI, location services, settings, and calculations. It provides reactive data flows
 * for real-time UI updates and handles complex location/calculation scenarios.
 * 
 * KEY RESPONSIBILITIES:
 * - Prayer time calculation and caching
 * - Location acquisition and management
 * - Settings synchronization and persistence
 * - Error handling and user feedback
 * - Automatic background updates
 * - Real-time prayer status tracking
 * 
 * ARCHITECTURE PATTERN:
 * Uses MVVM (Model-View-ViewModel) with reactive programming:
 * - StateFlow for UI state management
 * - Coroutines for async operations
 * - Repository pattern for data access
 * - Dependency injection with Hilt
 * 
 * LOCATION STRATEGIES:
 * 1. Cached prayer times (instant app startup)
 * 2. GPS location (when enabled and available)
 * 3. Manual location (user-selected)
 * 4. Default location (Mecca - always available)
 * 
 * AUTOMATIC UPDATES:
 * - Settings changes trigger recalculation
 * - GPS location updates every 30 minutes
 * - Cache validation for daily refresh
 * - Background location monitoring
 * 
 * ERROR HANDLING:
 * - Graceful fallbacks for all operations
 * - User-friendly error messages
 * - Permission and service availability checks
 * - Network timeout protection
 * 
 * PERFORMANCE OPTIMIZATIONS:
 * - Instant startup with cached times
 * - Background loading for smooth UX
 * - Smart caching to avoid repeated calculations
 * - Timeout protection for location services
 */
@HiltViewModel
class PrayerTimesViewModel @Inject constructor(
    private val prayerCalculatorService: PrayerTimeCalculatorService,
    private val locationService: LocationService,
    private val enhancedLocationService: EnhancedLocationService,
    private val settingsRepository: PrayerSettingsRepository,
    private val countryPrayerMethodService: CountryPrayerMethodService
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrayerTimesUiState())
    val uiState: StateFlow<PrayerTimesUiState> = _uiState.asStateFlow()
    
    // Connect to repository's settings flow with proper scoping to prevent ANR
    val settings: StateFlow<PrayerSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily, // Only start when first subscriber appears
            initialValue = PrayerSettings() // Default settings to prevent blocking
        )
    
    init {
        // Load cached prayer times first for instant display
        loadCachedPrayerTimes()
        
        
        // Observe settings changes and recalculate prayer times when settings change (async to prevent ANR)
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.settingsFlow
                .drop(1) // Skip initial value to avoid immediate recalculation
                .collect { newSettings ->
                    android.util.Log.i("PrayerTimesViewModel", "🚨 VIEWMODEL: SETTINGS FLOW UPDATE RECEIVED!")
                    android.util.Log.i("PrayerTimesViewModel", "   - Method: ${newSettings.calculationMethod.displayName}")
                    android.util.Log.i("PrayerTimesViewModel", "   - Auto-detected: ${newSettings.isMethodAutoDetected}")
                    android.util.Log.i("PrayerTimesViewModel", "   - Custom Fajr Angle: ${newSettings.customFajrAngle}")
                    android.util.Log.i("PrayerTimesViewModel", "   - Time: ${java.time.LocalDateTime.now()}")
                    
                    // Delay to prevent blocking during app startup
                    kotlinx.coroutines.delay(100) 
                    android.util.Log.i("PrayerTimesViewModel", "📤 Starting UI prayer time recalculation...")
                    val startTime = System.currentTimeMillis()
                    calculatePrayerTimes(showLoading = false, clearLoadingImmediately = true) // Background update, no loading state
                    val duration = System.currentTimeMillis() - startTime
                    android.util.Log.i("PrayerTimesViewModel", "✅ UI prayer times recalculated in ${duration}ms")
                }
        }
        
        // Calculate fresh prayer times (will update cache if needed) - async to prevent ANR during startup
        viewModelScope.launch(Dispatchers.IO) {
            // Use non-blocking settings access to prevent ANR during ViewModel initialization
            val currentSettings = settingsRepository.getSettings() // NON-BLOCKING: Use current settings (may be defaults)
            android.util.Log.w("PrayerTimesViewModel", "🔥 STARTING INITIALIZATION (NON-BLOCKING) - Custom Isha: ${currentSettings.customIshaAngle}")
            
            // TEMPORARILY DISABLED: All auto-detection during initialization to prevent ANR
            android.util.Log.d("PrayerTimesViewModel", "⚠️ Auto-detection DISABLED during initialization to prevent ANR")
            
            // Try to auto-configure prayer method based on location during initial startup
            // tryInitialLocationBasedConfiguration()
            
            // If no auto-detection happened, test with UAE location for demo purposes
            // kotlinx.coroutines.delay(2000) // Wait a bit for initial config
            // val currentSettings = settings.value
            
            // Check if user has customizations before testing with UAE location
            // val hasCustomAngles = currentSettings.customFajrAngle != null ||
            //                     currentSettings.customIshaAngle != null ||
            //                     currentSettings.customIshaDelay != null
            
            // Only skip if user has explicitly made changes AND we have backup data to distinguish from fresh install
            // val hasUserCustomizations = hasCustomAngles ||
            //                           // Skip if user previously had auto-detection but manually changed settings 
            //                           (currentSettings.originalAutoDetectedSettingsJson != null && 
            //                            (!currentSettings.isMethodAutoDetected || 
            //                             !currentSettings.isMadhhabAutoDetected || 
            //                             !currentSettings.areCustomAnglesAutoDetected))
            
            // if (!currentSettings.isMethodAutoDetected && 
            //     currentSettings.calculationMethod == CalculationMethod.MUSLIM_WORLD_LEAGUE &&
            //     !hasUserCustomizations) {
            //     android.util.Log.d("PrayerTimesViewModel", "No auto-detection occurred, testing with UAE location")
            //     testAutoDetectionWithUAELocation()
            // } else if (hasUserCustomizations) {
            //     android.util.Log.w("PrayerTimesViewModel", "🔥 Skipping UAE test - user has customizations")
            // }
            
            calculatePrayerTimes()
        }
        
        // Start automatic location updates if GPS is enabled
        startAutomaticLocationUpdates()
    }
    
    /**
     * Load settings asynchronously to prevent blocking main thread during ViewModel initialization
     */
    
    /**
     * Calculates prayer times for today
     */
    fun calculatePrayerTimes(date: LocalDate = LocalDate.now(), showLoading: Boolean = true, clearLoadingImmediately: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }
            
            try {
                val currentSettings = settings.value
                val location = getCurrentLocation(currentSettings)
                
                // ROBUST LOGGING: Check what settings ViewModel is using for calculation
                android.util.Log.i("PrayerTimesViewModel", "📋 VIEWMODEL SETTINGS FOR CALCULATION:")
                android.util.Log.i("PrayerTimesViewModel", "   - Method: ${currentSettings.calculationMethod.displayName}")
                android.util.Log.i("PrayerTimesViewModel", "   - Custom Fajr: ${currentSettings.customFajrAngle}")
                android.util.Log.i("PrayerTimesViewModel", "   - Custom Isha: ${currentSettings.customIshaAngle}")
                android.util.Log.i("PrayerTimesViewModel", "   - Fajr Offset: ${currentSettings.timeOffsets.fajr}")
                android.util.Log.i("PrayerTimesViewModel", "   - Dhuhr Offset: ${currentSettings.timeOffsets.dhuhr}")
                android.util.Log.i("PrayerTimesViewModel", "   - Asr Offset: ${currentSettings.timeOffsets.asr}")
                android.util.Log.i("PrayerTimesViewModel", "   - Auto-detected: ${currentSettings.isMethodAutoDetected}")
                
                val prayerTimes = prayerCalculatorService.calculatePrayerTimes(date, location, currentSettings)
                
                prayerTimes?.let { times ->
                    val timeUntilNext = prayerCalculatorService.getTimeUntilNextPrayer(times)
                    val isUsingDefault = (currentSettings.location == null)
                    
                    // Cache the calculated prayer times for quick loading next time
                    settingsRepository.cachePrayerTimes(times)
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = if (showLoading && clearLoadingImmediately) false else _uiState.value.isLoading,
                        prayerTimes = times,
                        timeUntilNext = timeUntilNext,
                        location = location,
                        calculationMethod = currentSettings.calculationMethod,
                        error = if (isUsingDefault) "Using default location (${location.getDisplayName()}). Tap 'Get Location' for accurate times." else null
                    )
                } ?: run {
                    _uiState.value = _uiState.value.copy(
                        isLoading = if (showLoading && clearLoadingImmediately) false else _uiState.value.isLoading,
                        error = "Failed to calculate prayer times for ${location.getDisplayName()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = if (showLoading && clearLoadingImmediately) false else _uiState.value.isLoading,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }
    
    /**
     * Updates prayer settings
     */
    fun updateSettings(newSettings: PrayerSettings) {
        android.util.Log.w("PrayerTimesViewModel", "🔥 UPDATE SETTINGS CALLED - Method: ${newSettings.calculationMethod.name}")
        android.util.Log.w("PrayerTimesViewModel", "🔥 UPDATE SETTINGS - ASR: ${newSettings.asrMadhhab.name}")
        android.util.Log.w("PrayerTimesViewModel", "🔥 UPDATE SETTINGS - Custom Isha: ${newSettings.customIshaAngle}")
        settingsRepository.updateSettings(newSettings, forceCommit = true) // User-triggered action needs immediate persistence
        android.util.Log.w("PrayerTimesViewModel", "🔥 Repository updateSettings completed")
    }
    
    /**
     * Updates settings and clears auto-detection info when user manually changes calculation method or madhhab
     */
    fun updateSettingsManually(newSettings: PrayerSettings) {
        val currentSettings = settings.value
        
        // Clear auto-detection flags if user manually changed calculation method, madhhab, or custom angles
        // BUT preserve backup JSON and country information for restore functionality
        val clearedAutoDetectionSettings = if (
            newSettings.calculationMethod != currentSettings.calculationMethod ||
            newSettings.asrMadhhab != currentSettings.asrMadhhab ||
            newSettings.customFajrAngle != currentSettings.customFajrAngle ||
            newSettings.customIshaAngle != currentSettings.customIshaAngle ||
            newSettings.customIshaDelay != currentSettings.customIshaDelay
        ) {
            android.util.Log.i("PrayerTimesViewModel", "🔧 Manual settings change detected - preserving backup for restore")
            newSettings.copy(
                isMethodAutoDetected = false,
                isMadhhabAutoDetected = false,
                areCustomAnglesAutoDetected = false,
                // PRESERVE backup JSON and country data for restore functionality
                originalAutoDetectedSettingsJson = currentSettings.originalAutoDetectedSettingsJson,
                autoDetectedCountryName = currentSettings.autoDetectedCountryName,
                autoDetectedCountryCode = currentSettings.autoDetectedCountryCode
            )
        } else {
            newSettings
        }
        
        updateSettings(clearedAutoDetectionSettings)
    }
    
    /**
     * Requests current GPS location with enhanced accuracy
     */
    fun requestCurrentLocation() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingLocation = true)
            
            try {
                if (!enhancedLocationService.hasLocationPermission()) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingLocation = false,
                        error = "Location permission required. Tap 'Grant Permission' to allow location access."
                    )
                    return@launch
                }
                
                if (!enhancedLocationService.isLocationEnabled()) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingLocation = false,
                        error = "Please enable location services in device settings"
                    )
                    return@launch
                }
                
                // Try high accuracy location first
                val result = enhancedLocationService.getCurrentLocationHighAccuracy()
                result.fold(
                    onSuccess = { androidLocation ->
                        val locationWithDetails = enhancedLocationService.getLocationDetails(androidLocation)
                        
                        // Get country-specific prayer method recommendations
                        android.util.Log.d("PrayerTimesViewModel", "Calling countryPrayerMethodService for location: ${androidLocation.latitude}, ${androidLocation.longitude}")
                        val locationBasedSettings = countryPrayerMethodService.getPrayerMethodForLocation(androidLocation)
                        android.util.Log.d("PrayerTimesViewModel", "Got location-based settings: ${locationBasedSettings.countryName}, isAutoDetected: ${locationBasedSettings.isAutoDetected}")
                        
                        // Update settings with new location and auto-detected prayer method
                        val shouldAutoUpdate = shouldAutoUpdateMethod(locationBasedSettings)
                        android.util.Log.d("PrayerTimesViewModel", "🔍 Auto-detected angles: Fajr=${locationBasedSettings.customFajrAngle}°, Isha=${locationBasedSettings.customIshaAngle}°, IshaDelay=${locationBasedSettings.customIshaOffset}min")
                        val updatedSettings = settings.value.copy(
                            location = locationWithDetails,
                            // Only auto-update calculation method if user hasn't manually changed it
                            calculationMethod = if (shouldAutoUpdate) {
                                locationBasedSettings.calculationMethod
                            } else {
                                settings.value.calculationMethod
                            },
                            asrMadhhab = if (shouldAutoUpdate) {
                                locationBasedSettings.madhhab
                            } else {
                                settings.value.asrMadhhab
                            },
                            // Auto-populate custom angle fields from JSON data
                            customFajrAngle = if (shouldAutoUpdate && locationBasedSettings.isAutoDetected) {
                                locationBasedSettings.customFajrAngle
                            } else {
                                settings.value.customFajrAngle
                            },
                            customIshaAngle = if (shouldAutoUpdate && locationBasedSettings.isAutoDetected) {
                                locationBasedSettings.customIshaAngle
                            } else {
                                settings.value.customIshaAngle
                            },
                            customIshaDelay = if (shouldAutoUpdate && locationBasedSettings.isAutoDetected) {
                                locationBasedSettings.customIshaOffset
                            } else {
                                settings.value.customIshaDelay
                            },
                            // Store auto-detection information
                            isMethodAutoDetected = shouldAutoUpdate && locationBasedSettings.isAutoDetected,
                            isMadhhabAutoDetected = shouldAutoUpdate && locationBasedSettings.isAutoDetected,
                            areCustomAnglesAutoDetected = shouldAutoUpdate && locationBasedSettings.isAutoDetected && 
                                (locationBasedSettings.customFajrAngle != null || locationBasedSettings.customIshaAngle != null || locationBasedSettings.customIshaOffset != null),
                            autoDetectedCountryName = if (locationBasedSettings.isAutoDetected) {
                                locationBasedSettings.countryName
                            } else {
                                null
                            },
                            autoDetectedCountryCode = if (locationBasedSettings.isAutoDetected) {
                                locationBasedSettings.countryCode
                            } else {
                                null
                            }
                            // Don't override user's useGpsLocation preference
                        )
                        android.util.Log.d("PrayerTimesViewModel", "💾 Final settings to save: Fajr=${updatedSettings.customFajrAngle}°, Isha=${updatedSettings.customIshaAngle}°, IshaDelay=${updatedSettings.customIshaDelay}min, autoDetected=${updatedSettings.areCustomAnglesAutoDetected}")
                        updateSettings(updatedSettings)
                        
                        // Show user feedback about auto-detected settings
                        if (locationBasedSettings.isAutoDetected) {
                            android.util.Log.i("PrayerTimesViewModel", 
                                "Auto-detected ${locationBasedSettings.countryName}: " +
                                "${locationBasedSettings.calculationMethod.displayName} + ${locationBasedSettings.madhhab.displayName}"
                            )
                        }
                        
                        _uiState.value = _uiState.value.copy(
                            isLoadingLocation = false,
                            location = locationWithDetails,
                            error = null
                        )
                        
                        // Recalculate prayer times with new accurate location (show loading for manual refresh)
                        calculatePrayerTimes(showLoading = true, clearLoadingImmediately = true)
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoadingLocation = false,
                            error = when (exception) {
                                is SecurityException -> "Location permission denied"
                                is IllegalStateException -> "Location services are disabled"
                                else -> "Failed to get accurate location: ${exception.message}"
                            }
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingLocation = false,
                    error = e.message ?: "Location error"
                )
            }
        }
    }
    
    /**
     * Searches for locations by name using enhanced location service
     */
    fun searchLocation(query: String, onResult: (List<Location>) -> Unit) {
        viewModelScope.launch {
            try {
                val result = enhancedLocationService.searchLocation(query)
                result.fold(
                    onSuccess = { locations ->
                        onResult(locations)
                    },
                    onFailure = {
                        // Fallback to basic location service if enhanced search fails
                        try {
                            val fallbackResult = locationService.searchLocation(query)
                            fallbackResult.fold(
                                onSuccess = { locations -> onResult(locations) },
                                onFailure = { onResult(emptyList()) }
                            )
                        } catch (e: Exception) {
                            onResult(emptyList())
                        }
                    }
                )
            } catch (e: Exception) {
                onResult(emptyList())
            }
        }
    }
    
    /**
     * Tries to configure prayer method based on location during initial app startup
     * This gives users auto-configuration even if GPS is disabled for ongoing updates
     */
    private suspend fun tryInitialLocationBasedConfiguration() {
        try {
            // Only attempt if current method is default and no country has been detected yet
            val currentSettings = settingsRepository.getLoadedSettings() // NEW: Use loaded settings
            
            // Skip if already auto-detected or user has made manual customizations
            val hasCustomAngles = currentSettings.customFajrAngle != null ||
                                currentSettings.customIshaAngle != null ||
                                currentSettings.customIshaDelay != null
            
            // Only skip if user has explicitly made changes AND we have backup data to distinguish from fresh install
            val hasUserCustomizations = hasCustomAngles ||
                                      // Skip if user previously had auto-detection but manually changed settings 
                                      (currentSettings.originalAutoDetectedSettingsJson != null && 
                                       (!currentSettings.isMethodAutoDetected || 
                                        !currentSettings.isMadhhabAutoDetected || 
                                        !currentSettings.areCustomAnglesAutoDetected))
            
            android.util.Log.w("PrayerTimesViewModel", "🔥 AUTO-DETECTION CHECK:")
            android.util.Log.w("PrayerTimesViewModel", "🔥 customFajrAngle: ${currentSettings.customFajrAngle}")
            android.util.Log.w("PrayerTimesViewModel", "🔥 customIshaAngle: ${currentSettings.customIshaAngle}")
            android.util.Log.w("PrayerTimesViewModel", "🔥 customIshaDelay: ${currentSettings.customIshaDelay}")
            android.util.Log.w("PrayerTimesViewModel", "🔥 hasCustomAngles: $hasCustomAngles")
            android.util.Log.w("PrayerTimesViewModel", "🔥 originalAutoDetectedSettingsJson != null: ${currentSettings.originalAutoDetectedSettingsJson != null}")
            android.util.Log.w("PrayerTimesViewModel", "🔥 hasUserCustomizations: $hasUserCustomizations")
            
            if (currentSettings.isMethodAutoDetected || 
                currentSettings.calculationMethod != CalculationMethod.MUSLIM_WORLD_LEAGUE ||
                currentSettings.autoDetectedCountryName != null ||
                hasUserCustomizations) {
                android.util.Log.w("PrayerTimesViewModel", "🔥 Skipping initial location config - user has customizations or already configured")
                android.util.Log.w("PrayerTimesViewModel", "🔥 customFajrAngle: ${currentSettings.customFajrAngle}, customIshaAngle: ${currentSettings.customIshaAngle}")
                android.util.Log.w("PrayerTimesViewModel", "🔥 isMethodAutoDetected: ${currentSettings.isMethodAutoDetected}, autoDetectedCountryName: ${currentSettings.autoDetectedCountryName}")
                return // Already configured or user has customizations
            }
            
            // Attempt to get location for initial configuration
            android.util.Log.d("PrayerTimesViewModel", "Attempting initial location-based configuration...")
            if (enhancedLocationService.hasLocationPermission() && enhancedLocationService.isLocationEnabled()) {
                val result = enhancedLocationService.getCurrentLocation()
                result.fold(
                    onSuccess = { androidLocation ->
                        android.util.Log.d("PrayerTimesViewModel", "Got initial location: ${androidLocation.latitude}, ${androidLocation.longitude}")
                        android.util.Log.d("PrayerTimesViewModel", "🚀 Calling updateLocationWithAutoMethod for angle detection")
                        updateLocationWithAutoMethod(androidLocation)
                    },
                    onFailure = { exception ->
                        android.util.Log.d("PrayerTimesViewModel", "Initial location failed: ${exception.message}")
                    }
                )
            } else {
                android.util.Log.d("PrayerTimesViewModel", "No location permission or location disabled")
            }
        } catch (e: Exception) {
            android.util.Log.d("PrayerTimesViewModel", "Initial location configuration error: ${e.message}")
        }
    }

    /**
     * Manual test function to demonstrate auto-detection with UAE location
     * This can be used for testing without requiring actual GPS location
     */
    fun testAutoDetectionWithUAELocation() {
        viewModelScope.launch {
            try {
                android.util.Log.d("PrayerTimesViewModel", "Testing auto-detection with UAE location (Dubai)")
                
                // Create a test location for UAE (Dubai coordinates)
                val testLocation = android.location.Location("test").apply {
                    latitude = 25.2048  // Dubai latitude
                    longitude = 55.2708 // Dubai longitude
                }
                
                // Trigger the country detection
                updateLocationWithAutoMethod(testLocation)
                
                android.util.Log.d("PrayerTimesViewModel", "Test auto-detection completed")
            } catch (e: Exception) {
                android.util.Log.e("PrayerTimesViewModel", "Test auto-detection failed: ${e.message}", e)
            }
        }
    }

    /**
     * Clears current error
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Gets current location based on settings with fallback to default location
     */
    private suspend fun getCurrentLocation(settings: PrayerSettings): Location {
        // If user has set a manual location, use it
        if (!settings.useGpsLocation && settings.location != null) {
            return settings.location
        }
        
        // If GPS is preferred and we have permission, try to get current location with enhanced accuracy
        if (settings.useGpsLocation && enhancedLocationService.hasLocationPermission()) {
            val androidLocation = enhancedLocationService.getBestAvailableLocation().getOrNull()
            if (androidLocation != null) {
                return enhancedLocationService.getLocationDetails(androidLocation)
            }
        }
        
        // Fall back to saved location or default location
        return settings.location ?: getDefaultLocation()
    }
    
    /**
     * Provides default location (Mecca) when no location is set
     */
    private fun getDefaultLocation(): Location {
        return Location(
            latitude = 21.4225,
            longitude = 39.8262,
            timeZoneOffset = 3.0, // UTC+3 for Saudi Arabia
            city = "Mecca",
            country = "Saudi Arabia"
        )
    }
    
    /**
     * Refreshes prayer times
     */
    fun refresh(showLoading: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            
            try {
                if (showLoading) {
                    // For manual refresh: show loading state with minimum duration to prevent glitch
                    val startTime = System.currentTimeMillis()
                    val minLoadingDuration = 800L // Minimum 800ms loading display
                    
                    // Clear cache to force fresh calculation
                    settingsRepository.clearPrayerTimesCache()
                    
                    // If using GPS, get fresh location first
                    if (settings.value.useGpsLocation && enhancedLocationService.hasLocationPermission()) {
                        val result = enhancedLocationService.getCurrentLocation()
                        result.fold(
                            onSuccess = { androidLocation ->
                                updateLocationWithAutoMethod(androidLocation)
                            },
                            onFailure = {
                                // Continue with existing location if GPS fails
                            }
                        )
                    }
                    
                    // Calculate fresh prayer times with updated location - don't clear loading immediately
                    calculatePrayerTimes(showLoading = true, clearLoadingImmediately = false)
                    
                    // Ensure minimum loading duration to prevent visual glitch
                    val elapsedTime = System.currentTimeMillis() - startTime
                    val remainingTime = minLoadingDuration - elapsedTime
                    if (remainingTime > 0) {
                        kotlinx.coroutines.delay(remainingTime)
                    }
                    
                    // Now clear the loading state
                    _uiState.value = _uiState.value.copy(isLoading = false)
                } else {
                    // For pull-to-refresh: end animation immediately for smooth UX
                    _uiState.value = _uiState.value.copy(isRefreshing = false)
                    
                    // Do heavy work in background without blocking animation
                    launch {
                        try {
                            // Clear cache to force fresh calculation
                            settingsRepository.clearPrayerTimesCache()
                            
                            // If using GPS, get fresh location first
                            if (settings.value.useGpsLocation && enhancedLocationService.hasLocationPermission()) {
                                val result = enhancedLocationService.getCurrentLocation()
                                result.fold(
                                    onSuccess = { androidLocation ->
                                        updateLocationWithAutoMethod(androidLocation)
                                    },
                                    onFailure = {
                                        // Continue with existing location if GPS fails
                                    }
                                )
                            }
                            
                            // Calculate fresh prayer times with updated location
                            calculatePrayerTimes(showLoading = false, clearLoadingImmediately = true)
                        } catch (e: Exception) {
                            _uiState.value = _uiState.value.copy(
                                error = e.message ?: "Failed to update prayer times"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.message ?: "Failed to refresh prayer times"
                )
            }
        }
    }
    
    /**
     * Starts automatic location updates for better accuracy
     */
    private fun startAutomaticLocationUpdates() {
        viewModelScope.launch {
            while (true) {
                try {
                    // Wait 30 minutes between location updates
                    kotlinx.coroutines.delay(30 * 60 * 1000L)
                    
                    val currentSettings = settings.value
                    
                    // Only update if GPS is enabled and we have permissions
                    if (currentSettings.useGpsLocation && 
                        enhancedLocationService.hasLocationPermission() && 
                        enhancedLocationService.isLocationEnabled()) {
                        
                        // Get current location silently (no UI updates for background refresh)
                        enhancedLocationService.getCurrentLocation().fold(
                            onSuccess = { androidLocation ->
                                val newLocation = enhancedLocationService.getLocationDetails(androidLocation)
                                val existingLocation = currentSettings.location
                                
                                // Only update if location has changed significantly (>100m)
                                if (existingLocation == null || hasLocationChangedSignificantly(existingLocation, newLocation)) {
                                    // Update location with auto-method detection
                                    updateLocationWithAutoMethod(androidLocation)
                                    
                                    // Silently recalculate prayer times with new location (no loading state)
                                    calculatePrayerTimes(showLoading = false, clearLoadingImmediately = true)
                                }
                            },
                            onFailure = { 
                                // Ignore failures in background updates
                            }
                        )
                    }
                } catch (e: Exception) {
                    // Continue the loop even if an update fails
                }
            }
        }
    }
    
    /**
     * Checks if location has changed significantly (>100 meters)
     */
    private fun hasLocationChangedSignificantly(oldLocation: Location, newLocation: Location): Boolean {
        val distance = calculateDistance(
            oldLocation.latitude, oldLocation.longitude,
            newLocation.latitude, newLocation.longitude
        )
        return distance > 100.0 // 100 meters threshold
    }
    
    /**
     * Calculates distance between two coordinates in meters
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // Earth's radius in meters
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * Determines whether to auto-update calculation method based on location
     * Only auto-updates if user hasn't manually customized their method
     */
    private suspend fun shouldAutoUpdateMethod(locationBasedSettings: com.starception.submission.prayer.service.LocationBasedPrayerSettings): Boolean {
        val currentSettings = settingsRepository.getLoadedSettings() // NEW: Use loaded settings
        
        // Check if user has made manual customizations
        val hasCustomAngles = currentSettings.customFajrAngle != null ||
                            currentSettings.customIshaAngle != null ||
                            currentSettings.customIshaDelay != null
        
        // Check if user has manually changed settings (has backup data but flags are false)
        val hasUserCustomizations = hasCustomAngles ||
                                   (currentSettings.originalAutoDetectedSettingsJson != null && 
                                    (!currentSettings.isMethodAutoDetected || 
                                     !currentSettings.isMadhhabAutoDetected || 
                                     !currentSettings.areCustomAnglesAutoDetected))
        
        android.util.Log.w("PrayerTimesViewModel", "🔥 shouldAutoUpdateMethod CHECK:")
        android.util.Log.w("PrayerTimesViewModel", "  customFajrAngle: ${currentSettings.customFajrAngle}")
        android.util.Log.w("PrayerTimesViewModel", "  customIshaAngle: ${currentSettings.customIshaAngle}")
        android.util.Log.w("PrayerTimesViewModel", "  hasCustomAngles: $hasCustomAngles")
        android.util.Log.w("PrayerTimesViewModel", "  hasUserCustomizations: $hasUserCustomizations")
        android.util.Log.w("PrayerTimesViewModel", "  isAutoDetected: ${locationBasedSettings.isAutoDetected}")
        android.util.Log.w("PrayerTimesViewModel", "  currentMethod: ${currentSettings.calculationMethod}")
        android.util.Log.w("PrayerTimesViewModel", "  currentLocation: ${currentSettings.location}")
        
        // Auto-update ONLY if:
        // 1. Location was auto-detected successfully
        // 2. Current method is still using defaults (likely first time or using regional defaults)
        // 3. User hasn't explicitly made customizations
        val result = locationBasedSettings.isAutoDetected && 
                   (currentSettings.calculationMethod == CalculationMethod.MUSLIM_WORLD_LEAGUE || 
                    currentSettings.location == null) &&
                   !hasUserCustomizations  // NEW: Don't override user customizations
        
        android.util.Log.w("PrayerTimesViewModel", "🔥 shouldAutoUpdateMethod RESULT: $result")
        return result
    }
    
    /**
     * Updates location with country-specific prayer method detection (for background updates)
     */
    private suspend fun updateLocationWithAutoMethod(androidLocation: android.location.Location) {
        try {
            android.util.Log.w("PrayerTimesViewModel", "🔥 updateLocationWithAutoMethod CALLED")
            val beforeSettings = settingsRepository.getLoadedSettings() // Check loaded settings
            android.util.Log.w("PrayerTimesViewModel", "  Current Custom Isha BEFORE: ${beforeSettings.customIshaAngle}")
            
            val locationWithDetails = enhancedLocationService.getLocationDetails(androidLocation)
            val locationBasedSettings = countryPrayerMethodService.getPrayerMethodForLocation(androidLocation)
            
            val shouldAutoUpdate = shouldAutoUpdateMethod(locationBasedSettings)
            android.util.Log.w("PrayerTimesViewModel", "🔥 Processing angles - shouldAutoUpdate: $shouldAutoUpdate")
            android.util.Log.w("PrayerTimesViewModel", "🔥 Fajr=${locationBasedSettings.customFajrAngle}°, Isha=${locationBasedSettings.customIshaAngle}°, IshaDelay=${locationBasedSettings.customIshaOffset}min")
            val currentSettings = settingsRepository.getLoadedSettings() // NEW: Use loaded settings
            val updatedSettings = currentSettings.copy(
                location = locationWithDetails,
                calculationMethod = if (shouldAutoUpdate) {
                    locationBasedSettings.calculationMethod
                } else {
                    currentSettings.calculationMethod
                },
                asrMadhhab = if (shouldAutoUpdate) {
                    locationBasedSettings.madhhab
                } else {
                    currentSettings.asrMadhhab
                },
                // Auto-populate custom angle fields from JSON data
                customFajrAngle = if (shouldAutoUpdate && locationBasedSettings.isAutoDetected) {
                    locationBasedSettings.customFajrAngle
                } else {
                    currentSettings.customFajrAngle
                },
                customIshaAngle = if (shouldAutoUpdate && locationBasedSettings.isAutoDetected) {
                    locationBasedSettings.customIshaAngle
                } else {
                    currentSettings.customIshaAngle
                },
                customIshaDelay = if (shouldAutoUpdate && locationBasedSettings.isAutoDetected) {
                    locationBasedSettings.customIshaOffset
                } else {
                    currentSettings.customIshaDelay
                },
                // Store auto-detection information
                isMethodAutoDetected = shouldAutoUpdate && locationBasedSettings.isAutoDetected,
                isMadhhabAutoDetected = shouldAutoUpdate && locationBasedSettings.isAutoDetected,
                areCustomAnglesAutoDetected = shouldAutoUpdate && locationBasedSettings.isAutoDetected && 
                    (locationBasedSettings.customFajrAngle != null || locationBasedSettings.customIshaAngle != null || locationBasedSettings.customIshaOffset != null),
                autoDetectedCountryName = if (locationBasedSettings.isAutoDetected) {
                    locationBasedSettings.countryName
                } else {
                    null
                },
                autoDetectedCountryCode = if (locationBasedSettings.isAutoDetected) {
                    locationBasedSettings.countryCode
                } else {
                    null
                }
            )
            
            // Create JSON backup if this is a new auto-detection
            val finalSettings = if (shouldAutoUpdate && locationBasedSettings.isAutoDetected && 
                                   currentSettings.originalAutoDetectedSettingsJson == null) {
                val backupJson = createSettingsBackup(updatedSettings, locationBasedSettings.countryName, locationBasedSettings.countryCode)
                android.util.Log.i("PrayerTimesViewModel", "💾 Creating backup for auto-detected settings: ${locationBasedSettings.countryName}")
                updatedSettings.copy(originalAutoDetectedSettingsJson = backupJson)
            } else {
                updatedSettings
            }
            
            android.util.Log.w("PrayerTimesViewModel", "🔥 FINAL: Saving angles - Fajr=${finalSettings.customFajrAngle}°, Isha=${finalSettings.customIshaAngle}°, IshaDelay=${finalSettings.customIshaDelay}min, autoDetected=${finalSettings.areCustomAnglesAutoDetected}")
            // Use non-blocking update during initialization to prevent ANR
            settingsRepository.updateSettings(finalSettings, forceCommit = false) // Background initialization, use async apply()
            android.util.Log.w("PrayerTimesViewModel", "🔥 updateLocationWithAutoMethod COMPLETED")
            android.util.Log.w("PrayerTimesViewModel", "  Current Custom Isha AFTER: ${settings.value.customIshaAngle}")
            
            if (locationBasedSettings.isAutoDetected) {
                android.util.Log.i("PrayerTimesViewModel", 
                    "Background auto-update for ${locationBasedSettings.countryName}: " +
                    "${locationBasedSettings.calculationMethod.displayName} + ${locationBasedSettings.madhhab.displayName}"
                )
            }
        } catch (e: Exception) {
            // Fallback to simple location update if country detection fails
            val locationWithDetails = enhancedLocationService.getLocationDetails(androidLocation)
            val updatedSettings = settings.value.copy(location = locationWithDetails)
            updateSettings(updatedSettings)
        }
    }
    
    /**
     * Loads cached prayer times for instant display on app startup
     */
    private fun loadCachedPrayerTimes() {
        val cachedPrayerTimes = settingsRepository.getCachedPrayerTimes()
        cachedPrayerTimes?.let { times ->
            val currentSettings = settings.value
            val timeUntilNext = prayerCalculatorService.getTimeUntilNextPrayer(times)
            
            _uiState.value = _uiState.value.copy(
                prayerTimes = times,
                timeUntilNext = timeUntilNext,
                location = times.location,
                calculationMethod = currentSettings.calculationMethod,
                error = null
            )
        }
    }

    // =============================================
    // BACKUP & RESTORE SYSTEM FOR AUTO-DETECTED SETTINGS
    // =============================================
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Creates a JSON backup of the current settings after auto-detection
     */
    private fun createSettingsBackup(settings: PrayerSettings, countryName: String, countryCode: String): String {
        val backup = AutoDetectedSettingsBackup(
            calculationMethod = settings.calculationMethod,
            asrMadhhab = settings.asrMadhhab,
            customFajrAngle = settings.customFajrAngle,
            customIshaAngle = settings.customIshaAngle,
            customIshaDelay = settings.customIshaDelay,
            timeOffsets = settings.timeOffsets,
            countryName = countryName,
            countryCode = countryCode
        )
        return json.encodeToString(backup)
    }
    
    /**
     * Checks if current settings differ from the backed-up original settings
     */
    fun hasSettingsChanged(): Boolean {
        android.util.Log.w("PrayerTimesViewModel", "🔍🔍 CHECKING IF SETTINGS CHANGED (for restore button visibility)")
        
        val current = settings.value
        val backupJson = current.originalAutoDetectedSettingsJson
        
        if (backupJson == null) {
            android.util.Log.w("PrayerTimesViewModel", "❌ No backup JSON available - restore button will NOT show")
            return false
        }
        
        android.util.Log.w("PrayerTimesViewModel", "📋 BACKUP JSON EXISTS - analyzing changes...")
        
        try {
            val backup = json.decodeFromString<AutoDetectedSettingsBackup>(backupJson)
            
            android.util.Log.w("PrayerTimesViewModel", "📋 COMPARISON - CURRENT vs ORIGINAL AUTO-DETECTED:")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Calculation Method: ${current.calculationMethod} vs ${backup.calculationMethod} -> Changed: ${current.calculationMethod != backup.calculationMethod}")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Asr Madhhab: ${current.asrMadhhab} vs ${backup.asrMadhhab} -> Changed: ${current.asrMadhhab != backup.asrMadhhab}")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Custom Fajr Angle: ${current.customFajrAngle} vs ${backup.customFajrAngle} -> Changed: ${current.customFajrAngle != backup.customFajrAngle}")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Custom Isha Angle: ${current.customIshaAngle} vs ${backup.customIshaAngle} -> Changed: ${current.customIshaAngle != backup.customIshaAngle}")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Custom Isha Delay: ${current.customIshaDelay} vs ${backup.customIshaDelay} -> Changed: ${current.customIshaDelay != backup.customIshaDelay}")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Time Offsets: ${current.timeOffsets} vs ${backup.timeOffsets} -> Changed: ${current.timeOffsets != backup.timeOffsets}")
            
            val hasChanged = current.calculationMethod != backup.calculationMethod ||
                           current.asrMadhhab != backup.asrMadhhab ||
                           current.customFajrAngle != backup.customFajrAngle ||
                           current.customIshaAngle != backup.customIshaAngle ||
                           current.customIshaDelay != backup.customIshaDelay ||
                           current.timeOffsets != backup.timeOffsets
                           
            android.util.Log.w("PrayerTimesViewModel", "🎯 FINAL RESULT: Settings have changed = $hasChanged")
            android.util.Log.w("PrayerTimesViewModel", "🎯 RESTORE BUTTON VISIBILITY: ${if (hasChanged) "WILL SHOW" else "WILL NOT SHOW"}")
            
            return hasChanged
        } catch (e: Exception) {
            android.util.Log.e("PrayerTimesViewModel", "❌ Failed to parse backup JSON for comparison", e)
            return false
        }
    }
    
    /**
     * Restores settings from the JSON backup
     */
    fun restoreAutoDetectedSettings() {
        android.util.Log.w("PrayerTimesViewModel", "🔄🔄 RESTORE AUTO-DETECTED SETTINGS CALLED")
        
        val current = settings.value
        android.util.Log.w("PrayerTimesViewModel", "📋 CURRENT SETTINGS STATE BEFORE RESTORE:")
        android.util.Log.w("PrayerTimesViewModel", "   📋 Calculation Method: ${current.calculationMethod}")
        android.util.Log.w("PrayerTimesViewModel", "   📋 Asr Madhhab: ${current.asrMadhhab}")
        android.util.Log.w("PrayerTimesViewModel", "   📋 Custom Fajr Angle: ${current.customFajrAngle}")
        android.util.Log.w("PrayerTimesViewModel", "   📋 Custom Isha Angle: ${current.customIshaAngle}")
        android.util.Log.w("PrayerTimesViewModel", "   📋 Custom Isha Delay: ${current.customIshaDelay}")
        android.util.Log.w("PrayerTimesViewModel", "   📋 Is Method Auto-Detected: ${current.isMethodAutoDetected}")
        android.util.Log.w("PrayerTimesViewModel", "   📋 Auto-Detected Country: ${current.autoDetectedCountryName}")
        
        val backupJson = current.originalAutoDetectedSettingsJson
        if (backupJson == null) {
            android.util.Log.e("PrayerTimesViewModel", "❌ No backup JSON found - cannot restore")
            return
        }
        
        android.util.Log.w("PrayerTimesViewModel", "📋 FOUND BACKUP JSON (${backupJson.length} chars): ${backupJson.take(200)}...")
        
        try {
            val backup = json.decodeFromString<AutoDetectedSettingsBackup>(backupJson)
            
            android.util.Log.w("PrayerTimesViewModel", "📋 PARSED BACKUP DATA:")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Country: ${backup.countryName} (${backup.countryCode})")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Calculation Method: ${backup.calculationMethod}")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Asr Madhhab: ${backup.asrMadhhab}")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Custom Fajr Angle: ${backup.customFajrAngle}")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Custom Isha Angle: ${backup.customIshaAngle}")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Custom Isha Delay: ${backup.customIshaDelay}")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Time Offsets: ${backup.timeOffsets}")
            
            android.util.Log.i("PrayerTimesViewModel", "🔄 Restoring auto-detected settings for ${backup.countryName}")
            
            val restoredSettings = current.copy(
                calculationMethod = backup.calculationMethod,
                asrMadhhab = backup.asrMadhhab,
                customFajrAngle = backup.customFajrAngle,
                customIshaAngle = backup.customIshaAngle,
                customIshaDelay = backup.customIshaDelay,
                timeOffsets = backup.timeOffsets,
                // Keep the backup and country info
                autoDetectedCountryName = backup.countryName,
                autoDetectedCountryCode = backup.countryCode,
                // Reset auto-detection flags since we're restoring
                isMethodAutoDetected = true,
                isMadhhabAutoDetected = true,
                areCustomAnglesAutoDetected = backup.customFajrAngle != null || backup.customIshaAngle != null || backup.customIshaDelay != null
            )
            
            android.util.Log.w("PrayerTimesViewModel", "📋 RESTORED SETTINGS TO APPLY:")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Calculation Method: ${restoredSettings.calculationMethod}")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Asr Madhhab: ${restoredSettings.asrMadhhab}")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Custom Fajr Angle: ${restoredSettings.customFajrAngle}")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Custom Isha Angle: ${restoredSettings.customIshaAngle}")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Custom Isha Delay: ${restoredSettings.customIshaDelay}")
            android.util.Log.w("PrayerTimesViewModel", "   📋 Are Custom Angles Auto-Detected: ${restoredSettings.areCustomAnglesAutoDetected}")
            
            updateSettings(restoredSettings)
            android.util.Log.i("PrayerTimesViewModel", "✅ Successfully restored settings to original auto-detected values")
        } catch (e: Exception) {
            android.util.Log.e("PrayerTimesViewModel", "❌ Failed to restore settings from backup", e)
        }
    }
    
    /**
     * SAVE CURRENT SETTINGS: Forces immediate save of current settings
     * 
     * This function ensures the current settings are saved immediately,
     * typically called when the user navigates away from the settings page
     * to prevent losing any changes they made.
     */
    fun saveCurrentSettings() {
        android.util.Log.w("PrayerTimesViewModel", "🔥 💾 SAVE CURRENT SETTINGS CALLED")
        android.util.Log.w("PrayerTimesViewModel", "🔥 Current settings - Method: ${settings.value.calculationMethod.name}")
        android.util.Log.w("PrayerTimesViewModel", "🔥 Current settings - ASR: ${settings.value.asrMadhhab.name}")
        android.util.Log.w("PrayerTimesViewModel", "🔥 Current settings - Custom Isha: ${settings.value.customIshaAngle}")
        updateSettings(settings.value)
    }
}

/**
 * PRAYER TIMES UI STATE: Complete state representation for prayer times screen
 * 
 * This data class represents all possible states of the prayer times UI,
 * enabling reactive UI updates and proper loading/error state management.
 * 
 * STATE MANAGEMENT:
 * - Uses immutable data class pattern for predictable state changes
 * - Each field represents a specific aspect of the UI
 * - Supports multiple loading states for better UX
 * 
 * LOADING STATES:
 * - isLoading: Prayer time calculations in progress
 * - isLoadingLocation: GPS/location acquisition in progress
 * - isRefreshing: Pull-to-refresh or manual refresh in progress
 * 
 * DATA STATES:
 * - prayerTimes: Calculated prayer times for current day
 * - timeUntilNext: Human-readable time until next prayer
 * - location: Current location used for calculations
 * - calculationMethod: Selected calculation method for transparency
 * 
 * ERROR HANDLING:
 * - error: User-friendly error message (null when no error)
 * - Designed to show actionable error messages to guide users
 * 
 * UI RENDERING LOGIC:
 * - Non-null prayerTimes = show prayer times
 * - isLoading = show loading indicators
 * - error != null = show error message with actions
 * - Multiple states can be active (e.g., showing data while refreshing)
 * 
 * @param isLoading True when prayer calculations are in progress
 * @param isLoadingLocation True when acquiring GPS location
 * @param isRefreshing True during pull-to-refresh or manual refresh
 * @param prayerTimes Calculated prayer times for the day (null if none available)
 * @param timeUntilNext Human-readable time until next prayer (e.g., "2h 30m")
 * @param location Location used for calculations (for display purposes)
 * @param calculationMethod Calculation method used (for transparency)
 * @param error User-friendly error message (null when no error)
 */
data class PrayerTimesUiState(
    val isLoading: Boolean = false,
    val isLoadingLocation: Boolean = false,
    val isRefreshing: Boolean = false,
    val prayerTimes: DayPrayerTimes? = null,
    val timeUntilNext: String? = null,
    val location: Location? = null,
    val calculationMethod: CalculationMethod? = null,
    val error: String? = null
)