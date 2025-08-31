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
 * PRAYER TIMES CALCULATOR: Main engine for calculating prayer times with caching
 * 
 * This class coordinates between location services, settings, and calculation engines
 * to provide fast and reliable prayer times.
 * 
 * KEY IMPROVEMENTS:
 * - Uses 3-second location timeout (prevents elevator hangs)
 * - Intelligent caching system (instant subsequent loads)
 * - Smart fallback strategy (cached → GPS → default location)
 * - Handles all error cases gracefully
 * 
 * MAIN METHODS:
 * - calculateDefaultPrayerTimes() - Primary calculation with caching
 * - calculatePrayerTimes() - Direct calculation for specific location
 * 
 * EDIT THIS TO:
 * - Change calculation priorities
 * - Modify caching behavior
 * - Add new location sources
 */
class PrayerTimesCalculator(private val context: Context) {
    
    /**
     * MAIN CALCULATION METHOD: Calculate prayer times with smart caching and fallbacks
     * 
     * This is the primary method used throughout the app for getting prayer times.
     * 
     * CALCULATION FLOW:
     * 1. Check cache first (instant if available)
     * 2. Get user settings (saved location preference, calculation method)
     * 3. Determine location (user saved → GPS with 3s timeout → cached → Dubai default)
     * 4. Calculate prayer times using astronomical formulas
     * 5. Cache results for future use
     * 6. Handle all errors gracefully with fallbacks
     * 
     * CACHING STRATEGY:
     * - Returns cached data immediately if available for today
     * - Caches new calculations for instant future access
     * - Uses cached location when GPS is slow/unavailable
     * 
     * ERROR HANDLING:
     * - Falls back to cached data on calculation errors
     * - Uses Dubai as final location fallback
     * - Never returns null (always provides some prayer times)
     * 
     * EDIT THIS TO:
     * - Change calculation priority order
     * - Modify caching behavior
     * - Add new location sources
     * - Change error handling strategy
     */
    suspend fun calculateDefaultPrayerTimes(): Pair<DayPrayerTimes?, String> {
        return try {
            // STEP 1: Get all required services
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                PrayerTimeCalculatorEntryPoint::class.java
            )
            val calculator = entryPoint.prayerTimeCalculatorService()
            val locationService = entryPoint.enhancedLocationService()
            val settingsRepository = entryPoint.prayerSettingsRepository()
            val cache = entryPoint.locationCache()
            
            // STEP 2: Check cache first for instant results
            val cachedData = cache.getCachedPrayerTimes()
            if (cachedData != null) {
                val (cachedPrayerTimes, cachedDate, cachedLocationName) = cachedData
                if (cachedPrayerTimes != null && cachedLocationName != null) {
                    // Return cached data immediately - no waiting!
                    return Pair(cachedPrayerTimes, cachedLocationName)
                }
            }
            
            // Get user prayer settings
            val userSettings = try {
                settingsRepository.getSettings()
            } catch (e: Exception) {
                PrayerSettings() // Fallback to default
            }
            
            // STEP 4: SMART LOCATION DETERMINATION - Multi-level fallback system
            // 
            // PRIORITY ORDER:
            // 1. User's manually saved location (highest priority - user choice)
            // 2. Recent cached GPS location (fast - within 30 minutes)
            // 3. Fresh GPS location with 3-second timeout (prevents elevator hangs)
            // 4. Any available cached location (even if old)
            // 5. Dubai default location (final fallback)
            // 
            // EDIT THIS PRIORITY ORDER to change location selection behavior
            val location = when {
                // PRIORITY 1: User's saved location (user manually set their location)
                userSettings.location != null -> {
                    userSettings.location!!
                }
                
                // PRIORITY 2-4: GPS and cached location strategies (if user granted permission)
                locationService.hasLocationPermission() -> {
                    try {
                        // PRIORITY 2: Try recent cached location first (instant, no GPS wait)
                        val cachedLocation = cache.getCachedLocation()
                        if (cachedLocation != null) {
                            cachedLocation // Use cached if it's fresh (within 30 minutes)
                        } else {
                            // PRIORITY 3: Try fresh GPS with 3-second timeout (fast, prevents hangs)
                            val androidLocation = locationService.getLocationQuick().getOrNull()
                            androidLocation?.let { androidLoc ->
                                val detailedLocation = locationService.getLocationDetails(androidLoc)
                                cache.cacheLocation(detailedLocation) // Cache for next time
                                detailedLocation
                            } ?: getDefaultLocation() // Fallback if GPS fails
                        }
                    } catch (e: Exception) {
                        // PRIORITY 4: Try any cached location before giving up
                        cache.getCachedLocation() ?: getDefaultLocation()
                    }
                }
                
                // PRIORITY 5: Final fallback - use any cached location or Dubai default
                else -> cache.getCachedLocation() ?: getDefaultLocation()
            }
            
            // STEP 5: CALCULATE PRAYER TIMES using astronomical formulas
            val today = LocalDate.now()
            val calculatedTimes = calculator.calculatePrayerTimes(today, location, userSettings)
            val locationName = location.getDisplayName()
            
            // STEP 6: CACHE THE RESULTS for instant future access
            if (calculatedTimes != null) {
                cache.cachePrayerTimes(calculatedTimes, today, locationName)
                // This cached data will be returned immediately on next app launch
            }
            
            Pair(calculatedTimes, locationName)
        } catch (e: Exception) {
            // ERROR RECOVERY: Try cached data as emergency fallback
            // 
            // This ensures the app never completely fails - it will show something
            // even if all location and calculation services fail.
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    PrayerTimeCalculatorEntryPoint::class.java
                )
                val cache = entryPoint.locationCache()
                val cachedData = cache.getCachedPrayerTimes()
                
                if (cachedData != null) {
                    val (cachedPrayerTimes, _, cachedLocationName) = cachedData
                    if (cachedPrayerTimes != null && cachedLocationName != null) {
                        // Return cached data with clear indication it's cached
                        return Pair(cachedPrayerTimes, "$cachedLocationName (Cached)")
                    }
                }
            } catch (cacheError: Exception) {
                // Even cache failed - this is very rare
            }
            
            // ABSOLUTE FINAL FALLBACK: Return null but with clear location indicator
            // This will trigger the app to show Dubai prayer times from PrayerTimeCalculatorService
            Pair(null, "Dubai, UAE (Default)")
        }
    }
    
    /**
     * DEFAULT LOCATION PROVIDER: Provides Dubai coordinates as reliable fallback
     * 
     * This ensures the app always has a location to calculate prayer times with,
     * even when GPS fails or user denies location permission.
     * 
     * WHY DUBAI:
     * - Central location in Muslim world
     * - Well-known prayer time reference
     * - Reliable timezone (UAE +4)
     * 
     * EDIT THIS TO:
     * - Change default city (coordinates, timezone)
     * - Add multiple default locations based on region
     * - Use user's country as default
     */
    private fun getDefaultLocation(): Location {
        return Location(
            latitude = 25.2048,    // Dubai coordinates - EDIT these for different default city
            longitude = 55.2708,
            timeZoneOffset = 4.0,   // UAE timezone (+4 GMT) - EDIT for different timezone
            city = "Dubai",         // EDIT for different default city
            country = "UAE"         // EDIT for different default country
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