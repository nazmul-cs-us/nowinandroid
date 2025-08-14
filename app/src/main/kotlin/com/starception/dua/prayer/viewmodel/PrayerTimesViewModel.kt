package com.starception.dua.prayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.dua.prayer.model.AsrMadhhab
import com.starception.dua.prayer.model.CalculationMethod
import com.starception.dua.prayer.model.DayPrayerTimes
import com.starception.dua.prayer.model.Location
import com.starception.dua.prayer.model.PrayerSettings
import com.starception.dua.prayer.repository.PrayerSettingsRepository
import com.starception.dua.prayer.service.LocationService
import com.starception.dua.prayer.service.EnhancedLocationService
import com.starception.dua.prayer.service.PrayerTimeCalculatorService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * ViewModel for prayer times functionality
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
    
    private val _settings = MutableStateFlow(PrayerSettings()) // Use default settings initially
    val settings: StateFlow<PrayerSettings> = _settings.asStateFlow()
    
    init {
        // Load cached prayer times first for instant display
        loadCachedPrayerTimes()
        
        // Load initial settings asynchronously to prevent main thread blocking
        loadSettingsAsync()
        
        // Observe settings changes
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { newSettings ->
                _settings.value = newSettings
                calculatePrayerTimes(showLoading = false, clearLoadingImmediately = true) // Background update, no loading state
            }
        }
        
        // Calculate fresh prayer times (will update cache if needed)
        calculatePrayerTimes()
        
        // Start automatic location updates if GPS is enabled
        startAutomaticLocationUpdates()
    }
    
    /**
     * Load settings asynchronously to prevent blocking main thread during ViewModel initialization
     */
    private fun loadSettingsAsync() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = settingsRepository.getSettings()
                _settings.value = settings
                android.util.Log.d("PrayerTimesViewModel", "Settings loaded asynchronously")
            } catch (e: Exception) {
                android.util.Log.e("PrayerTimesViewModel", "Error loading settings asynchronously", e)
                // Provide default settings if loading fails
                _settings.value = PrayerSettings()
            }
        }
    }
    
    /**
     * Calculates prayer times for today
     */
    fun calculatePrayerTimes(date: LocalDate = LocalDate.now(), showLoading: Boolean = true, clearLoadingImmediately: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }
            
            try {
                val currentSettings = _settings.value
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
        settingsRepository.updateSettings(newSettings)
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
                        
                        // Update settings with new location
                        val updatedSettings = _settings.value.copy(
                            location = locationWithDetails,
                            useGpsLocation = true
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
                    if (_settings.value.useGpsLocation && enhancedLocationService.hasLocationPermission()) {
                        val result = enhancedLocationService.getCurrentLocation()
                        result.fold(
                            onSuccess = { androidLocation ->
                                val newLocation = enhancedLocationService.getLocationDetails(androidLocation)
                                val updatedSettings = _settings.value.copy(location = newLocation)
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
                            if (_settings.value.useGpsLocation && enhancedLocationService.hasLocationPermission()) {
                                val result = enhancedLocationService.getCurrentLocation()
                                result.fold(
                                    onSuccess = { androidLocation ->
                                        val newLocation = enhancedLocationService.getLocationDetails(androidLocation)
                                        val updatedSettings = _settings.value.copy(location = newLocation)
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
                                error = e.message ?: "Failed to refresh prayer times"
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
                    
                    val currentSettings = _settings.value
                    
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
            val currentSettings = _settings.value
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
 * UI state for prayer times screen
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