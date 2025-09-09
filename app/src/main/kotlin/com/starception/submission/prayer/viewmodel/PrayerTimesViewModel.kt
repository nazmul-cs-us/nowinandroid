package com.starception.submission.prayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.prayer.model.AsrMadhhab
import com.starception.submission.prayer.model.CalculationMethod
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.Location
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.prayer.repository.PrayerSettingsRepository
import com.starception.submission.prayer.service.LocationService
import com.starception.submission.prayer.service.EnhancedLocationService
import com.starception.submission.prayer.service.PrayerTimeCalculatorService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    private val settingsRepository: PrayerSettingsRepository
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
            settingsRepository.settingsFlow.collect { newSettings ->
                android.util.Log.d("PrayerTimesViewModel", "Settings changed - Method: ${newSettings.calculationMethod.name}")
                // Delay to prevent blocking during app startup
                kotlinx.coroutines.delay(100) 
                calculatePrayerTimes(showLoading = false, clearLoadingImmediately = true) // Background update, no loading state
            }
        }
        
        // Calculate fresh prayer times (will update cache if needed) - async to prevent ANR during startup
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(200) // Extra delay for startup
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
        android.util.Log.d("PrayerTimesViewModel", "updateSettings called - Method: ${newSettings.calculationMethod.name}")
        settingsRepository.updateSettings(newSettings, forceCommit = true) // User-triggered action needs immediate persistence
        android.util.Log.d("PrayerTimesViewModel", "Repository updateSettings completed")
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
                        
                        // Update settings with new location (preserve user's GPS preference)
                        val updatedSettings = settings.value.copy(
                            location = locationWithDetails
                            // Don't override user's useGpsLocation preference
                        )
                        updateSettings(updatedSettings)
                        
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
                                val newLocation = enhancedLocationService.getLocationDetails(androidLocation)
                                val updatedSettings = settings.value.copy(location = newLocation)
                                updateSettings(updatedSettings)
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
                                        val newLocation = enhancedLocationService.getLocationDetails(androidLocation)
                                        val updatedSettings = settings.value.copy(location = newLocation)
                                        updateSettings(updatedSettings)
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
                                    val updatedSettings = currentSettings.copy(location = newLocation)
                                    updateSettings(updatedSettings)
                                    
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