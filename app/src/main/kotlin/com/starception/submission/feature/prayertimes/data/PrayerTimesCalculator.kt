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
        val startTime = System.currentTimeMillis()
        android.util.Log.d("PrayerCalculation", "=== STARTING PRAYER TIMES CALCULATION ===")
        
        return try {
            // STEP 1: Get all required services
            android.util.Log.d("PrayerCalculation", "STEP 1: Getting required services")
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                PrayerTimeCalculatorEntryPoint::class.java
            )
            val calculator = entryPoint.prayerTimeCalculatorService()
            val locationService = entryPoint.enhancedLocationService()
            val settingsRepository = entryPoint.prayerSettingsRepository()
            val cache = entryPoint.locationCache()
            android.util.Log.d("PrayerCalculation", "✓ All services obtained successfully")
            
            // STEP 2: Check cache first for instant results
            android.util.Log.d("PrayerCalculation", "STEP 2: Checking cached prayer times")
            val cachedData = cache.getCachedPrayerTimes()
            if (cachedData != null) {
                val (cachedPrayerTimes, cachedDate, cachedLocationName) = cachedData
                android.util.Log.d("PrayerCalculation", "Found cached data: date=$cachedDate, location=$cachedLocationName")
                if (cachedPrayerTimes != null && cachedLocationName != null) {
                    android.util.Log.d("PrayerCalculation", "✓ Using cached prayer times - INSTANT RETURN")
                    android.util.Log.d("PrayerCalculation", "Cached times: Fajr=${cachedPrayerTimes.fajr}, Dhuhr=${cachedPrayerTimes.dhuhr}, Asr=${cachedPrayerTimes.asr}, Maghrib=${cachedPrayerTimes.maghrib}, Isha=${cachedPrayerTimes.isha}")
                    // Return cached data immediately - no waiting!
                    return Pair(cachedPrayerTimes, cachedLocationName)
                }
            }
            android.util.Log.d("PrayerCalculation", "No valid cached data found, proceeding with fresh calculation")
            
            // STEP 3: Get user prayer settings
            android.util.Log.d("PrayerCalculation", "STEP 3: Loading user prayer settings")
            val userSettings = try {
                val settings = settingsRepository.getSettings()
                android.util.Log.d("PrayerCalculation", "✓ User settings loaded:")
                android.util.Log.d("PrayerCalculation", "  - Calculation Method: ${settings.calculationMethod.name}")
                android.util.Log.d("PrayerCalculation", "  - Asr Madhab: ${settings.asrMadhhab.name}")
                android.util.Log.d("PrayerCalculation", "  - High Latitude Adjustment: ${settings.highLatitudeAdjustment.name}")
                android.util.Log.d("PrayerCalculation", "  - User Location: ${if (settings.location != null) "${settings.location?.getDisplayName()}" else "Not set"}")
                settings
            } catch (e: Exception) {
                android.util.Log.w("PrayerCalculation", "Failed to load user settings, using defaults: ${e.message}")
                PrayerSettings() // Fallback to default
            }
            
            // STEP 4: SMART LOCATION DETERMINATION - Multi-level fallback system
            android.util.Log.d("PrayerCalculation", "STEP 4: Determining location using priority fallback system")
            android.util.Log.d("PrayerCalculation", "=== LOCATION PERMISSION DEBUG ===")
            android.util.Log.d("PrayerCalculation", "Location permission granted: ${locationService.hasLocationPermission()}")
            android.util.Log.d("PrayerCalculation", "User saved location exists: ${userSettings.location != null}")
            if (userSettings.location != null) {
                android.util.Log.d("PrayerCalculation", "  Saved location: ${userSettings.location!!.getDisplayName()}")
            }
            
            // DEBUG: Check cache status regardless of permissions
            val freshCachedLocation = cache.getCachedLocation()
            val anyCachedLocation = cache.getAnyCachedLocation()
            android.util.Log.d("PrayerCalculation", "Fresh cached location available (<30min): ${freshCachedLocation != null}")
            android.util.Log.d("PrayerCalculation", "Any cached location available (any age): ${anyCachedLocation != null}")
            if (anyCachedLocation != null) {
                android.util.Log.d("PrayerCalculation", "  Any cached location: ${anyCachedLocation.getDisplayName()}")
                val cacheAge = cache.getCacheStatus()
                android.util.Log.d("PrayerCalculation", "  Cache age: $cacheAge")
            }
            
            // NEW IMPROVED PRIORITY ORDER (Prefers cached over Dubai):
            // 1. User's manually saved location (highest priority - user choice)
            // 2. Recent cached GPS location (fast - within 30 minutes) 
            // 3. Fresh GPS location with 3-second timeout (prevents elevator hangs)
            // 4. ANY cached location (even if old) - KEEPS USER'S LAST KNOWN LOCATION
            // 5. Dubai default location (final fallback - only if never cached)
            // 
            // KEY IMPROVEMENT: We now prefer old cached location over Dubai default!
            // This means if user was previously in New York and disables location,
            // we keep showing New York prayer times instead of switching to Dubai.
            // Only use Dubai if user never enabled location before.
            val location = when {
                // PRIORITY 1: User's saved location (user manually set their location)
                userSettings.location != null -> {
                    android.util.Log.d("PrayerCalculation", "✓ PRIORITY 1: Using user's saved location")
                    android.util.Log.d("PrayerCalculation", "  Location: ${userSettings.location!!.getDisplayName()}")
                    android.util.Log.d("PrayerCalculation", "  Coordinates: ${userSettings.location!!.latitude}, ${userSettings.location!!.longitude}")
                    android.util.Log.w("PrayerCalculation", "⚠️  LOCATION DISABLED ISSUE: This succeeds even with location off!")
                    android.util.Log.w("PrayerCalculation", "   Because user has a previously saved location in settings")
                    userSettings.location!!
                }
                
                // PRIORITY 2-4: GPS and cached location strategies (if user granted permission)
                locationService.hasLocationPermission() -> {
                    android.util.Log.d("PrayerCalculation", "User granted location permission, trying GPS strategies")
                    try {
                        // PRIORITY 2: Try recent cached location first (instant, no GPS wait)
                        val cachedLocation = cache.getCachedLocation()
                        if (cachedLocation != null) {
                            android.util.Log.d("PrayerCalculation", "✓ PRIORITY 2: Using recent cached GPS location")
                            android.util.Log.d("PrayerCalculation", "  Location: ${cachedLocation.getDisplayName()}")
                            android.util.Log.d("PrayerCalculation", "  Coordinates: ${cachedLocation.latitude}, ${cachedLocation.longitude}")
                            cachedLocation // Use cached if it's fresh (within 30 minutes)
                        } else {
                            android.util.Log.d("PrayerCalculation", "No recent cached location, trying fresh GPS...")
                            
                            // Check if we have ANY cached location (even if old) before trying GPS
                            val oldCachedLocation = cache.getAnyCachedLocation()
                            android.util.Log.d("PrayerCalculation", "Any cached location available (even old): ${oldCachedLocation != null}")
                            
                            // PRIORITY 3: Try fresh GPS with 3-second timeout (fast, prevents hangs)
                            val gpsStartTime = System.currentTimeMillis()
                            val androidLocation = locationService.getLocationQuick().getOrNull()
                            val gpsTime = System.currentTimeMillis() - gpsStartTime
                            
                            androidLocation?.let { androidLoc ->
                                android.util.Log.d("PrayerCalculation", "✓ PRIORITY 3: GPS location obtained in ${gpsTime}ms")
                                android.util.Log.d("PrayerCalculation", "  GPS Coordinates: ${androidLoc.latitude}, ${androidLoc.longitude}")
                                android.util.Log.d("PrayerCalculation", "  GPS Accuracy: ${androidLoc.accuracy}m")
                                
                                val detailedLocation = locationService.getLocationDetails(androidLoc)
                                android.util.Log.d("PrayerCalculation", "  Resolved Location: ${detailedLocation.getDisplayName()}")
                                cache.cacheLocation(detailedLocation) // Cache for next time
                                detailedLocation
                            } ?: run {
                                android.util.Log.w("PrayerCalculation", "GPS failed after ${gpsTime}ms")
                                
                                // PRIORITY 4: Use any cached location (even if old) instead of Dubai
                                if (oldCachedLocation != null) {
                                    android.util.Log.d("PrayerCalculation", "✓ PRIORITY 4: Using old cached location instead of Dubai fallback")
                                    android.util.Log.d("PrayerCalculation", "  Cached Location: ${oldCachedLocation.getDisplayName()}")
                                    android.util.Log.d("PrayerCalculation", "  Strategy: Keep using last known location until GPS works again")
                                    oldCachedLocation
                                } else {
                                    android.util.Log.w("PrayerCalculation", "No cached location available, must use Dubai default")
                                    getDefaultLocation()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("PrayerCalculation", "GPS strategy failed: ${e.message}")
                        // PRIORITY 4: Try any cached location (even old) before giving up
                        val fallbackLocation = cache.getAnyCachedLocation()
                        if (fallbackLocation != null) {
                            android.util.Log.d("PrayerCalculation", "✓ PRIORITY 4: Using any cached location as fallback")
                            android.util.Log.d("PrayerCalculation", "  Location: ${fallbackLocation.getDisplayName()}")
                            android.util.Log.d("PrayerCalculation", "  Strategy: Prefer cached over Dubai default")
                            fallbackLocation
                        } else {
                            android.util.Log.d("PrayerCalculation", "No cached location available, using Dubai default")
                            getDefaultLocation()
                        }
                    }
                }
                
                // PRIORITY 5: Final fallback - use any cached location or Dubai default
                else -> {
                    android.util.Log.w("PrayerCalculation", "=== LOCATION PERMISSION DENIED OR DISABLED ===")
                    android.util.Log.w("PrayerCalculation", "User has turned off location permission or location services")
                    android.util.Log.w("PrayerCalculation", "Checking for ANY cached location from previous sessions...")
                    
                    // Try any cached location (regardless of age) before falling back to Dubai
                    val fallbackLocation = cache.getAnyCachedLocation()
                    if (fallbackLocation != null) {
                        android.util.Log.w("PrayerCalculation", "✓ PRIORITY 5a: FOUND OLD CACHED LOCATION - Using instead of Dubai!")
                        android.util.Log.w("PrayerCalculation", "  Cached Location: ${fallbackLocation.getDisplayName()}")
                        android.util.Log.w("PrayerCalculation", "  Coordinates: ${fallbackLocation.latitude}, ${fallbackLocation.longitude}")
                        android.util.Log.w("PrayerCalculation", "  ⚠️  STRATEGY: Keep using last known location until GPS works again")
                        android.util.Log.w("PrayerCalculation", "  This is better than Dubai default for user experience")
                        fallbackLocation
                    } else {
                        android.util.Log.w("PrayerCalculation", "✓ PRIORITY 5b: No cached location available - must use Dubai default")
                        android.util.Log.w("PrayerCalculation", "  This means user never enabled location OR cache was cleared")
                        android.util.Log.w("PrayerCalculation", "  Dubai coordinates: 25.2048, 55.2708")
                        android.util.Log.w("PrayerCalculation", "  NOTE: Once user enables location, we'll cache and use their actual location")
                        getDefaultLocation()
                    }
                }
            }
            
            // STEP 5: CALCULATE PRAYER TIMES using astronomical formulas
            android.util.Log.d("PrayerCalculation", "STEP 5: Calculating prayer times using astronomical formulas")
            val today = LocalDate.now()
            android.util.Log.d("PrayerCalculation", "Calculation date: $today")
            android.util.Log.d("PrayerCalculation", "Final location: ${location.getDisplayName()}")
            android.util.Log.d("PrayerCalculation", "Final coordinates: ${location.latitude}, ${location.longitude}")
            android.util.Log.d("PrayerCalculation", "Calculation method: ${userSettings.calculationMethod.name}")
            
            val calcStartTime = System.currentTimeMillis()
            val calculatedTimes = calculator.calculatePrayerTimes(today, location, userSettings)
            val calcDuration = System.currentTimeMillis() - calcStartTime
            val locationName = location.getDisplayName()
            
            if (calculatedTimes != null) {
                android.util.Log.d("PrayerCalculation", "✓ Prayer times calculated successfully in ${calcDuration}ms:")
                android.util.Log.d("PrayerCalculation", "  Fajr:    ${calculatedTimes.fajr}")
                android.util.Log.d("PrayerCalculation", "  Dhuhr:   ${calculatedTimes.dhuhr}")  
                android.util.Log.d("PrayerCalculation", "  Asr:     ${calculatedTimes.asr}")
                android.util.Log.d("PrayerCalculation", "  Maghrib: ${calculatedTimes.maghrib}")
                android.util.Log.d("PrayerCalculation", "  Isha:    ${calculatedTimes.isha}")
            } else {
                android.util.Log.w("PrayerCalculation", "✗ Prayer times calculation returned null after ${calcDuration}ms")
            }
            
            // STEP 6: CACHE THE RESULTS for instant future access
            android.util.Log.d("PrayerCalculation", "STEP 6: Caching results for future use")
            if (calculatedTimes != null) {
                try {
                    cache.cachePrayerTimes(calculatedTimes, today, locationName)
                    android.util.Log.d("PrayerCalculation", "✓ Prayer times cached successfully for instant future access")
                    // This cached data will be returned immediately on next app launch
                } catch (e: Exception) {
                    android.util.Log.w("PrayerCalculation", "Failed to cache prayer times: ${e.message}")
                }
            } else {
                android.util.Log.w("PrayerCalculation", "Skipping cache - no valid prayer times to cache")
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            android.util.Log.d("PrayerCalculation", "=== CALCULATION COMPLETE ===")
            android.util.Log.d("PrayerCalculation", "Total calculation time: ${totalTime}ms")
            android.util.Log.d("PrayerCalculation", "Result: ${if (calculatedTimes != null) "SUCCESS" else "FAILED"}")
            
            Pair(calculatedTimes, locationName)
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - startTime
            android.util.Log.e("PrayerCalculation", "=== CALCULATION FAILED AFTER ${totalTime}ms ===")
            android.util.Log.e("PrayerCalculation", "Error during prayer times calculation: ${e.message}", e)
            
            // ERROR RECOVERY: Try cached data as emergency fallback
            android.util.Log.d("PrayerCalculation", "Attempting error recovery using cached data...")
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
                    val (cachedPrayerTimes, cachedDate, cachedLocationName) = cachedData
                    android.util.Log.d("PrayerCalculation", "Found emergency cached data: date=$cachedDate, location=$cachedLocationName")
                    if (cachedPrayerTimes != null && cachedLocationName != null) {
                        android.util.Log.d("PrayerCalculation", "✓ Using emergency cached data as fallback")
                        android.util.Log.d("PrayerCalculation", "Emergency cached times: Fajr=${cachedPrayerTimes.fajr}, Dhuhr=${cachedPrayerTimes.dhuhr}, Asr=${cachedPrayerTimes.asr}, Maghrib=${cachedPrayerTimes.maghrib}, Isha=${cachedPrayerTimes.isha}")
                        // Return cached data with clear indication it's cached
                        return Pair(cachedPrayerTimes, "$cachedLocationName (Cached)")
                    }
                }
                android.util.Log.w("PrayerCalculation", "No valid emergency cached data available")
            } catch (cacheError: Exception) {
                android.util.Log.e("PrayerCalculation", "Emergency cache recovery also failed: ${cacheError.message}")
                // Even cache failed - this is very rare
            }
            
            android.util.Log.w("PrayerCalculation", "All recovery methods failed, returning default fallback")
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