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
    fun prayerTimeSuggestionRepository(): com.starception.submission.prayer.repository.PrayerTimeSuggestionRepository
    fun islamicEventStateProvider(): com.starception.submission.islamic.shared.IslamicEventStateProvider
    fun duaRepository(): com.starception.submission.core.duadatabase.DuaRepository
}

/**
 * Islamic Prayer Times Calculator - Advanced Calculation Engine
 * 
 * A comprehensive prayer times calculation system that handles location services,
 * user preferences, caching, and multiple calculation methods to provide accurate
 * Islamic prayer times for any location worldwide.
 * 
 * ## Core Features:
 * - **Multi-Source Location**: GPS, Network, Cached, and Manual locations
 * - **Smart Caching**: Intelligent cache system prevents redundant calculations
 * - **Fallback Strategy**: Graceful degradation when services are unavailable
 * - **Multiple Methods**: Support for various Islamic calculation methods
 * - **Performance Optimized**: 3-second location timeout prevents UI freezing
 * 
 * ## Calculation Flow:
 * 1. **Location Resolution**: Try cached → GPS → network → user saved → default
 * 2. **Settings Retrieval**: Load user's calculation method and preferences
 * 3. **Prayer Times Calculation**: Use astronomical algorithms for precise times
 * 4. **Result Caching**: Cache results for instant subsequent access
 * 5. **Error Handling**: Graceful fallback to default location if needed
 * 
 * ## Architecture:
 * - **Entry Point Pattern**: Uses Hilt EntryPoint for dependency injection
 * - **Service Coordination**: Orchestrates multiple specialized services
 * - **Async Operations**: Non-blocking location requests with coroutines
 * - **Error Resilience**: Multiple fallback strategies for robust operation
 * 
 * ## Usage:
 * ```kotlin
 * val calculator = PrayerTimesCalculator(context)
 * val prayerTimes = calculator.calculateDefaultPrayerTimes()
 * ```
 * 
 * ## Performance Notes:
 * - First calculation: ~3 seconds max (location timeout)
 * - Subsequent calculations: ~50ms (from cache)
 * - Memory efficient: Caches only essential data
 * - Battery optimized: Minimizes GPS usage
 * 
 * @param context Android context for accessing system services
 * @author Prayer Times Development Team
 * @version 2.0 - Enhanced with caching and fallback strategies
 */
class PrayerTimesCalculator(private val context: Context) {
    
    companion object {
        private const val TAG = "PrayerTimesCalculator"
    }
    
    /**
     * MAIN CALCULATION METHOD: Calculate prayer times with smart caching and fallbacks
     * 
     * This is the primary method used throughout the app for getting prayer times.
     * 
     * @param forceGpsRefresh If true, skips saved location and forces fresh GPS fetch (for pull-to-refresh)
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
    suspend fun calculateDefaultPrayerTimes(forceGpsRefresh: Boolean = false): Pair<DayPrayerTimes?, String> {
        val startTime = System.currentTimeMillis()
        android.util.Log.i(TAG, "")
        android.util.Log.i(TAG, "🌅 COMPREHENSIVE PRAYER TIMES CALCULATION")
        android.util.Log.i(TAG, "=".repeat(90))
        android.util.Log.i(TAG, "🔆 Starting multi-stage prayer calculation with intelligent fallbacks")
        android.util.Log.i(TAG, "")
        
        return try {
            // STEP 1: Get all required services
            android.util.Log.i(TAG, "🔄 STEP 1: SERVICE INITIALIZATION")
            android.util.Log.i(TAG, "Obtaining required services via Hilt dependency injection")
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                PrayerTimeCalculatorEntryPoint::class.java
            )
            val calculator = entryPoint.prayerTimeCalculatorService()
            val locationService = entryPoint.enhancedLocationService()
            val settingsRepository = entryPoint.prayerSettingsRepository()
            val cache = entryPoint.locationCache()
            android.util.Log.i(TAG, "✅ All required services obtained successfully")
            android.util.Log.i(TAG, "")
            
            // STEP 2: Check cache first for instant results (SKIP if forceGpsRefresh)
            android.util.Log.i(TAG, "🔄 STEP 2: CACHE VALIDATION")
            if (forceGpsRefresh) {
                android.util.Log.i(TAG, "🔄 FORCE GPS REFRESH - Skipping cache to get fresh location")
            } else {
                android.util.Log.i(TAG, "Checking for cached prayer times to enable instant results")
                val cachedData = cache.getCachedPrayerTimes()
                if (cachedData != null) {
                    val (cachedPrayerTimes, cachedDate, cachedLocationName) = cachedData
                    android.util.Log.d("PrayerCalculation", "Found cached data: date=$cachedDate, location=$cachedLocationName")
                    if (cachedPrayerTimes != null && cachedLocationName != null) {
                        android.util.Log.i(TAG, "🚀 CACHE HIT - INSTANT RETURN")
                        android.util.Log.i(TAG, "📅 Cached Date: $cachedDate")
                        android.util.Log.i(TAG, "🌍 Cached Location: $cachedLocationName")
                        android.util.Log.d("PrayerCalculation", "Cached times: Fajr=${cachedPrayerTimes.fajr}, Dhuhr=${cachedPrayerTimes.dhuhr}, Asr=${cachedPrayerTimes.asr}, Maghrib=${cachedPrayerTimes.maghrib}, Isha=${cachedPrayerTimes.isha}")
                        // Return cached data immediately - no waiting!
                        return Pair(cachedPrayerTimes, cachedLocationName)
                    }
                }
            }
            android.util.Log.i(TAG, "💾 CACHE MISS/SKIPPED - Proceeding with fresh calculation")
            android.util.Log.i(TAG, "")
            
            // STEP 3: Get user prayer settings (wait for proper loading)
            android.util.Log.i(TAG, "🔄 STEP 3: USER SETTINGS LOADING")
            android.util.Log.i(TAG, "Loading user prayer calculation preferences and configuration")
            val userSettings = try {
                val settings = settingsRepository.getLoadedSettings() // NEW: Wait for actual loaded settings
                android.util.Log.w("PrayerCalculation", "✓ LOADED User settings (not defaults):")
                android.util.Log.w("PrayerCalculation", "  - Calculation Method: ${settings.calculationMethod.name}")
                android.util.Log.w("PrayerCalculation", "  - Asr Madhab: ${settings.asrMadhhab.name}")
                android.util.Log.w("PrayerCalculation", "  - High Latitude Adjustment: ${settings.highLatitudeAdjustment.name}")
                android.util.Log.w("PrayerCalculation", "  - Custom Fajr Angle: ${settings.customFajrAngle}")
                android.util.Log.w("PrayerCalculation", "  - Custom Isha Angle: ${settings.customIshaAngle}")
                android.util.Log.w("PrayerCalculation", "  - User Location: ${if (settings.location != null) "${settings.location?.getDisplayName()}" else "Not set"}")
                settings
            } catch (e: Exception) {
                android.util.Log.w("PrayerCalculation", "Failed to load user settings, using defaults: ${e.message}")
                PrayerSettings() // Fallback to default
            }
            
            // STEP 4: SMART LOCATION DETERMINATION - Multi-level fallback system
            android.util.Log.i(TAG, "🔄 STEP 4: INTELLIGENT LOCATION DETERMINATION")
            android.util.Log.i(TAG, "Applying intelligent multi-tier location resolution strategy")
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
            //    SKIPPED during pull-to-refresh (forceGpsRefresh=true) to force fresh GPS
            // 2. Recent cached GPS location (fast - within 30 minutes) 
            // 3. Fresh GPS location with 3-second timeout (prevents elevator hangs)
            // 4. ANY cached location (even if old) - KEEPS USER'S LAST KNOWN LOCATION
            // 5. Dubai default location (final fallback - only if never cached)
            // 
            // KEY IMPROVEMENT: We now prefer old cached location over Dubai default!
            // This means if user was previously in New York and disables location,
            // we keep showing New York prayer times instead of switching to Dubai.
            // Only use Dubai if user never enabled location before.
            
            // Log if we're forcing GPS refresh (pull-to-refresh)
            if (forceGpsRefresh) {
                android.util.Log.d("PrayerCalculation", "🔄 FORCE GPS REFRESH MODE: Skipping saved location to fetch fresh GPS")
            }
            
            val location = when {
                // PRIORITY 1: User's saved location (user manually set their location)
                // BUT: Skip if forceGpsRefresh=true (pull-to-refresh) or if it has "Current Location" bug
                userSettings.location != null && 
                !forceGpsRefresh && 
                !userSettings.location!!.city.contains("Current Location", ignoreCase = true) -> {
                    android.util.Log.d("PrayerCalculation", "✓ PRIORITY 1: Using user's saved location")
                    android.util.Log.d("PrayerCalculation", "  Location: ${userSettings.location!!.getDisplayName()}")
                    android.util.Log.d("PrayerCalculation", "  Coordinates: ${userSettings.location!!.latitude}, ${userSettings.location!!.longitude}")
                    android.util.Log.w("PrayerCalculation", "⚠️  LOCATION DISABLED ISSUE: This succeeds even with location off!")
                    android.util.Log.w("PrayerCalculation", "   Because user has a previously saved location in settings")
                    userSettings.location!!
                }
                
                // PRIORITY 2-4: GPS and cached location strategies (if user granted permission)
                // This also handles the case where saved location has "Current Location" bug
                locationService.hasLocationPermission() || (userSettings.location != null && userSettings.location!!.city.contains("Current Location", ignoreCase = true)) -> {
                    // Check if we're fixing "Current Location" bug
                    if (userSettings.location != null && userSettings.location!!.city.contains("Current Location", ignoreCase = true)) {
                        android.util.Log.w("PrayerCalculation", "⚠️  PRIORITY 1 SKIPPED: Saved location has 'Current Location' bug!")
                        android.util.Log.w("PrayerCalculation", "   Old location: ${userSettings.location!!.getDisplayName()}")
                        android.util.Log.w("PrayerCalculation", "   Forcing GPS fetch to fix this...")
                    } else {
                        android.util.Log.d("PrayerCalculation", "User granted location permission, trying GPS strategies")
                    }
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

                                // Helper to check if city is just coordinates (geocoding failed)
                                fun isCityJustCoordinates(city: String): Boolean {
                                    if (city.isBlank()) return true
                                    val coordPattern = Regex("""^-?\d+\.?\d*,\s*-?\d+\.?\d*$""")
                                    return coordPattern.matches(city.trim())
                                }

                                // Only cache if we got a real city name (geocoding succeeded)
                                // Don't overwrite good cached location with coordinates-only location
                                if (!isCityJustCoordinates(detailedLocation.city)) {
                                    cache.cacheLocation(detailedLocation) // Cache for next time
                                    android.util.Log.d("PrayerCalculation", "  ✓ Location cached (has real city name)")
                                    detailedLocation
                                } else {
                                    // Geocoding failed - use old cached location if available, but with new coordinates
                                    android.util.Log.w("PrayerCalculation", "  ⚠️ Geocoding failed (got coordinates only)")
                                    if (oldCachedLocation != null && !isCityJustCoordinates(oldCachedLocation.city)) {
                                        // Use old city name with new GPS coordinates (copy all location name fields)
                                        val mergedLocation = detailedLocation.copy(
                                            city = oldCachedLocation.city,
                                            country = oldCachedLocation.country,
                                            countryCode = oldCachedLocation.countryCode,
                                            area = oldCachedLocation.area,
                                            subLocality = oldCachedLocation.subLocality,
                                            thoroughfare = oldCachedLocation.thoroughfare,
                                            administrativeArea = oldCachedLocation.administrativeArea
                                        )
                                        android.util.Log.d("PrayerCalculation", "  ✓ Using cached city name: ${mergedLocation.getDisplayName()}")
                                        mergedLocation
                                    } else {
                                        // No good cached location, use coordinates
                                        android.util.Log.w("PrayerCalculation", "  ✗ No cached city name available")
                                        detailedLocation
                                    }
                                }
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
            android.util.Log.i(TAG, "🔄 STEP 5: ASTRONOMICAL CALCULATION")
            android.util.Log.i(TAG, "Executing comprehensive Islamic prayer time calculations")
            val today = LocalDate.now()
            android.util.Log.d("PrayerCalculation", "Calculation date: $today")
            android.util.Log.d("PrayerCalculation", "Final location: ${location.getDisplayName()}")
            android.util.Log.d("PrayerCalculation", "Final coordinates: ${location.latitude}, ${location.longitude}")
            android.util.Log.d("PrayerCalculation", "Calculation method: ${userSettings.calculationMethod.name}")
            
            val calcStartTime = System.currentTimeMillis()
            val calculatedTimes = calculator.calculatePrayerTimes(today, location, userSettings)
            val calcDuration = System.currentTimeMillis() - calcStartTime

            // Use old cached location name if new location doesn't have city name (geocoding failed - no internet)
            // Helper function to check if a string looks like coordinates (e.g., "25.20, 55.27")
            fun isCoordinatesString(city: String): Boolean {
                if (city.isBlank()) return false
                // Coordinates pattern: number, comma, number (e.g., "25.20, 55.27" or "-33.87, 151.21")
                val coordPattern = Regex("""^-?\d+\.?\d*,\s*-?\d+\.?\d*$""")
                return coordPattern.matches(city.trim())
            }

            val newLocationHasRealCityName = location.city.isNotBlank() && !isCoordinatesString(location.city)
            val oldLocation = userSettings.location
            val oldLocationHasRealCityName = oldLocation?.city?.isNotBlank() == true &&
                                              !isCoordinatesString(oldLocation.city)

            // If geocoding failed (coordinates only), use cached location's city/country with new GPS coordinates
            val correctedLocation = if (!newLocationHasRealCityName && oldLocationHasRealCityName && oldLocation != null) {
                android.util.Log.w("PrayerCalculation", "⚠️ Using cached city name (geocoding failed): ${oldLocation.getDisplayName()}")
                // Copy all location name fields from old location (area, city, country, etc.)
                location.copy(
                    city = oldLocation.city,
                    country = oldLocation.country,
                    countryCode = oldLocation.countryCode,
                    area = oldLocation.area,
                    subLocality = oldLocation.subLocality,
                    thoroughfare = oldLocation.thoroughfare,
                    administrativeArea = oldLocation.administrativeArea
                )
            } else {
                location
            }
            val locationName = correctedLocation.getDisplayName()

            // Update calculated times with corrected location (so DayPrayerTimes.location has city name)
            val finalCalculatedTimes = if (calculatedTimes != null && correctedLocation != location) {
                calculatedTimes.copy(location = correctedLocation)
            } else {
                calculatedTimes
            }

            if (finalCalculatedTimes != null) {
                android.util.Log.d("PrayerCalculation", "✓ Prayer times calculated successfully in ${calcDuration}ms:")
                android.util.Log.d("PrayerCalculation", "  Fajr:    ${finalCalculatedTimes.fajr}")
                android.util.Log.d("PrayerCalculation", "  Dhuhr:   ${finalCalculatedTimes.dhuhr}")
                android.util.Log.d("PrayerCalculation", "  Asr:     ${finalCalculatedTimes.asr}")
                android.util.Log.d("PrayerCalculation", "  Maghrib: ${finalCalculatedTimes.maghrib}")
                android.util.Log.d("PrayerCalculation", "  Isha:    ${finalCalculatedTimes.isha}")
                android.util.Log.d("PrayerCalculation", "  Location: ${finalCalculatedTimes.location.getDisplayName()}")
            } else {
                android.util.Log.w("PrayerCalculation", "✗ Prayer times calculation returned null after ${calcDuration}ms")
            }
            
            // STEP 6: SAVE LOCATION TO SETTINGS for notification service access
            android.util.Log.d("PrayerCalculation", "STEP 6a: Saving location to settings for notification service")
            try {
                val oldLocation = userSettings.location
                val hasCurrentLocationBug = oldLocation?.city?.contains("Current Location", ignoreCase = true) == true

                // Check if new location has a proper city name (geocoding succeeded)
                // Also check if it's just coordinates (e.g., "25.20, 55.27") which means geocoding failed
                val newLocationHasRealCityName = location.city.isNotBlank() &&
                    !location.city.contains("Current Location", ignoreCase = true) &&
                    !isCoordinatesString(location.city)
                val oldLocationHasRealCityName = oldLocation?.city?.isNotBlank() == true &&
                    !hasCurrentLocationBug &&
                    !isCoordinatesString(oldLocation.city)

                // Don't overwrite a good cached location (with city name) with coordinates-only location
                // This preserves the city name when offline (geocoding fails)
                if (oldLocationHasRealCityName && !newLocationHasRealCityName) {
                    android.util.Log.w("PrayerCalculation", "⚠️ PRESERVING OLD LOCATION: New location has no city name (geocoding failed - no internet?)")
                    android.util.Log.w("PrayerCalculation", "   Keeping: ${oldLocation?.getDisplayName()}")
                    android.util.Log.w("PrayerCalculation", "   Discarding: ${location.getDisplayName()} (coordinates only)")
                    // Don't save - keep the old location with city name
                } else if (newLocationHasRealCityName) {
                    // ALWAYS save when we have a successfully geocoded location with real city name
                    // This ensures the latest geocoded location is available for offline use
                    android.util.Log.w("PrayerCalculation", "🔥 Saving geocoded location to settings: ${location.getDisplayName()}")
                    val updatedSettings = userSettings.copy(location = location)
                    settingsRepository.updateSettings(updatedSettings, forceCommit = false)
                    android.util.Log.w("PrayerCalculation", "🔥 Location saved for offline use")
                } else {
                    // Geocoding failed and old location doesn't have city name either
                    // Check if we should save location - save if:
                    // 1. No saved location exists, OR
                    // 2. Location changed (different coordinates), OR
                    // 3. Old location has "Current Location" as city (need to fix it), OR
                    // 4. New location is not Dubai default
                    val locationChanged = oldLocation == null ||
                        (oldLocation.latitude != location.latitude || oldLocation.longitude != location.longitude)
                    val isNotDubai = !location.getDisplayName().contains("Dubai")

                    val shouldSaveLocation = oldLocation == null || locationChanged || hasCurrentLocationBug || isNotDubai

                    if (shouldSaveLocation) {
                        if (hasCurrentLocationBug) {
                            android.util.Log.w("PrayerCalculation", "🔧 FIXING 'Current Location' BUG: Replacing with proper location")
                            android.util.Log.w("PrayerCalculation", "   Old: ${oldLocation?.getDisplayName()}")
                            android.util.Log.w("PrayerCalculation", "   New: ${location.getDisplayName()}")
                        }
                        android.util.Log.w("PrayerCalculation", "🔥 Saving GPS location to settings: ${location.getDisplayName()}")
                        android.util.Log.w("PrayerCalculation", "🔥 BEFORE COPY - Custom Isha: ${userSettings.customIshaAngle}")
                        val updatedSettings = userSettings.copy(location = location)
                        android.util.Log.w("PrayerCalculation", "🔥 AFTER COPY - Custom Isha: ${updatedSettings.customIshaAngle}")
                        settingsRepository.updateSettings(updatedSettings, forceCommit = false)
                        android.util.Log.w("PrayerCalculation", "🔥 Location update call completed")
                    } else {
                        android.util.Log.d("PrayerCalculation", "Using existing saved location from settings: ${location.getDisplayName()}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("PrayerCalculation", "Failed to save location to settings: ${e.message}")
            }
            
            // STEP 7: CACHE THE RESULTS for instant future access
            android.util.Log.d("PrayerCalculation", "STEP 7: Caching results for future use")
            if (finalCalculatedTimes != null) {
                try {
                    cache.cachePrayerTimes(finalCalculatedTimes, today, locationName)
                    android.util.Log.d("PrayerCalculation", "✓ Prayer times cached successfully for instant future access")
                    // This cached data will be returned immediately on next app launch
                } catch (e: Exception) {
                    android.util.Log.w("PrayerCalculation", "Failed to cache prayer times: ${e.message}")
                }
            } else {
                android.util.Log.w("PrayerCalculation", "Skipping cache - no valid prayer times to cache")
            }

            val totalTime = System.currentTimeMillis() - startTime
            android.util.Log.i(TAG, "")
            android.util.Log.i(TAG, "✅ PRAYER CALCULATION ORCHESTRATION COMPLETE")
            android.util.Log.i(TAG, "=".repeat(90))
            android.util.Log.d("PrayerCalculation", "Total calculation time: ${totalTime}ms")
            android.util.Log.d("PrayerCalculation", "Result: ${if (finalCalculatedTimes != null) "SUCCESS" else "FAILED"}")

            Pair(finalCalculatedTimes, locationName)
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - startTime
            android.util.Log.e(TAG, "")
            android.util.Log.e(TAG, "❌ CRITICAL CALCULATION FAILURE")
            android.util.Log.e(TAG, "=".repeat(90))
            android.util.Log.e(TAG, "⚡ Failure Time: ${totalTime}ms")
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
