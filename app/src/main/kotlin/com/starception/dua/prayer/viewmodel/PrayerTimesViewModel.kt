package com.starception.dua.prayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.dua.prayer.model.DayPrayerTimes
import com.starception.dua.prayer.model.Location
import com.starception.dua.prayer.model.PrayerSettings
import com.starception.dua.prayer.repository.PrayerSettingsRepository
import com.starception.dua.prayer.service.LocationService
import com.starception.dua.prayer.service.PrayerTimeCalculatorService
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val settingsRepository: PrayerSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrayerTimesUiState())
    val uiState: StateFlow<PrayerTimesUiState> = _uiState.asStateFlow()
    
    private val _settings = MutableStateFlow(settingsRepository.getSettings())
    val settings: StateFlow<PrayerSettings> = _settings.asStateFlow()
    
    init {
        // Observe settings changes
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { newSettings ->
                _settings.value = newSettings
                calculatePrayerTimes()
            }
        }
        
        // Initial calculation
        calculatePrayerTimes()
    }
    
    /**
     * Calculates prayer times for today
     */
    fun calculatePrayerTimes(date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val currentSettings = _settings.value
                val location = getCurrentLocation(currentSettings)
                
                location?.let { loc ->
                    val prayerTimes = prayerCalculatorService.calculatePrayerTimes(date, loc, currentSettings)
                    
                    prayerTimes?.let { times ->
                        val timeUntilNext = prayerCalculatorService.getTimeUntilNextPrayer(times)
                        
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            prayerTimes = times,
                            timeUntilNext = timeUntilNext,
                            location = loc,
                            error = null
                        )
                    } ?: run {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Failed to calculate prayer times"
                        )
                    }
                } ?: run {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Location not available"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
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
     * Requests current GPS location
     */
    fun requestCurrentLocation() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingLocation = true)
            
            try {
                if (!locationService.hasLocationPermission()) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingLocation = false,
                        error = "Location permission required"
                    )
                    return@launch
                }
                
                val result = locationService.getCurrentLocation()
                result.fold(
                    onSuccess = { location ->
                        val locationWithDetails = locationService.getLocationDetails(location)
                        
                        // Update settings with new location
                        val updatedSettings = _settings.value.copy(
                            location = locationWithDetails,
                            useGpsLocation = true
                        )
                        updateSettings(updatedSettings)
                        
                        _uiState.value = _uiState.value.copy(
                            isLoadingLocation = false,
                            location = locationWithDetails
                        )
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoadingLocation = false,
                            error = exception.message ?: "Failed to get location"
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
     * Searches for locations by name
     */
    fun searchLocation(query: String, onResult: (List<Location>) -> Unit) {
        viewModelScope.launch {
            try {
                val result = locationService.searchLocation(query)
                result.fold(
                    onSuccess = { locations ->
                        onResult(locations)
                    },
                    onFailure = {
                        onResult(emptyList())
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
     * Gets current location based on settings
     */
    private suspend fun getCurrentLocation(settings: PrayerSettings): Location? {
        return if (settings.useGpsLocation) {
            if (locationService.hasLocationPermission()) {
                locationService.getCurrentLocation().getOrNull() ?: settings.location
            } else {
                settings.location
            }
        } else {
            settings.location
        }
    }
    
    /**
     * Refreshes prayer times
     */
    fun refresh() {
        calculatePrayerTimes()
    }
}

/**
 * UI state for prayer times screen
 */
data class PrayerTimesUiState(
    val isLoading: Boolean = false,
    val isLoadingLocation: Boolean = false,
    val prayerTimes: DayPrayerTimes? = null,
    val timeUntilNext: String? = null,
    val location: Location? = null,
    val error: String? = null
)