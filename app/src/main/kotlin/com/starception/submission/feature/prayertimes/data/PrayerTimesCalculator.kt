package com.starception.submission.feature.prayertimes.data

import android.content.Context
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.Location
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.prayer.service.PrayerTimeCalculatorService
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate

/**
 * EntryPoint for accessing prayer services without ViewModel
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PrayerTimeCalculatorEntryPoint {
    fun prayerTimeCalculatorService(): PrayerTimeCalculatorService
    fun enhancedLocationService(): com.starception.submission.prayer.service.EnhancedLocationService
    fun prayerSettingsRepository(): com.starception.submission.prayer.repository.PrayerSettingsRepository
    fun locationCache(): com.starception.submission.prayer.cache.LocationCache
}

/**
 * Calculator class that handles prayer times calculation with proper dependency injection
 */
class PrayerTimesCalculator(private val context: Context) {
    
    /**
     * Calculate prayer times using user settings and current location with caching
     * @return Pair of DayPrayerTimes and location display name
     */
    suspend fun calculateDefaultPrayerTimes(): Pair<DayPrayerTimes?, String> {
        return try {
            // Get services via EntryPoint
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                PrayerTimeCalculatorEntryPoint::class.java
            )
            val calculator = entryPoint.prayerTimeCalculatorService()
            val locationService = entryPoint.enhancedLocationService()
            val settingsRepository = entryPoint.prayerSettingsRepository()
            val cache = entryPoint.locationCache()
            
            // Check if we have valid cached data for today
            val cachedData = cache.getCachedPrayerTimes()
            if (cachedData != null) {
                val (cachedPrayerTimes, cachedDate, cachedLocationName) = cachedData
                if (cachedPrayerTimes != null && cachedLocationName != null) {
                    return Pair(cachedPrayerTimes, cachedLocationName)
                }
            }
            
            // Get user prayer settings
            val userSettings = try {
                settingsRepository.getSettings()
            } catch (e: Exception) {
                PrayerSettings() // Fallback to default
            }
            
            // Determine location to use with smart fallback strategy
            val location = when {
                // Use user's saved location if available
                userSettings.location != null -> {
                    userSettings.location!!
                }
                // Try to get current GPS location if permission granted (with quick timeout)
                locationService.hasLocationPermission() -> {
                    try {
                        // First try cached location if recent
                        val cachedLocation = cache.getCachedLocation()
                        if (cachedLocation != null) {
                            cachedLocation
                        } else {
                            // Use the quick location method with 3-second timeout
                            val androidLocation = locationService.getLocationQuick().getOrNull()
                            androidLocation?.let { androidLoc ->
                                val detailedLocation = locationService.getLocationDetails(androidLoc)
                                cache.cacheLocation(detailedLocation) // Cache for future use
                                detailedLocation
                            } ?: getDefaultLocation()
                        }
                    } catch (e: Exception) {
                        // Try cached location before falling back to default
                        cache.getCachedLocation() ?: getDefaultLocation()
                    }
                }
                // Fallback to cached location or default
                else -> cache.getCachedLocation() ?: getDefaultLocation()
            }
            
            // Calculate for today
            val today = LocalDate.now()
            val calculatedTimes = calculator.calculatePrayerTimes(today, location, userSettings)
            val locationName = location.getDisplayName()
            
            // Cache the results
            if (calculatedTimes != null) {
                cache.cachePrayerTimes(calculatedTimes, today, locationName)
            }
            
            Pair(calculatedTimes, locationName)
        } catch (e: Exception) {
            // Try to return cached data as last resort
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                PrayerTimeCalculatorEntryPoint::class.java
            )
            val cache = entryPoint.locationCache()
            val cachedData = cache.getCachedPrayerTimes()
            
            if (cachedData != null) {
                val (cachedPrayerTimes, _, cachedLocationName) = cachedData
                if (cachedPrayerTimes != null && cachedLocationName != null) {
                    return Pair(cachedPrayerTimes, "$cachedLocationName (Cached)")
                }
            }
            
            // Final fallback
            Pair(null, "Dubai, UAE (Default)")
        }
    }
    
    /**
     * Get default location (Dubai) as fallback
     */
    private fun getDefaultLocation(): Location {
        return Location(
            latitude = 25.2048,  // Dubai coordinates as fallback
            longitude = 55.2708,
            timeZoneOffset = 4.0, // UAE timezone
            city = "Dubai",
            country = "UAE"
        )
    }
    
    /**
     * Calculate prayer times for specific location and settings
     * @param location The geographic location
     * @param settings Prayer calculation settings
     * @param date The date to calculate for (defaults to today)
     * @return Pair of DayPrayerTimes and location display name
     */
    suspend fun calculatePrayerTimes(
        location: Location,
        settings: PrayerSettings = PrayerSettings(),
        date: LocalDate = LocalDate.now()
    ): Pair<DayPrayerTimes?, String> {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                PrayerTimeCalculatorEntryPoint::class.java
            )
            val calculator = entryPoint.prayerTimeCalculatorService()
            
            val calculatedTimes = calculator.calculatePrayerTimes(date, location, settings)
            
            Pair(calculatedTimes, location.getDisplayName())
        } catch (e: Exception) {
            Pair(null, location.getDisplayName())
        }
    }
}