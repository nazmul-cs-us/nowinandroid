package com.starception.submission.prayer.cache

import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.Location
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LOCATION & PRAYER TIMES CACHE: Fast fallback system for reliable app performance
 * 
 * This cache prevents the app from showing "Calculating..." forever by storing recent data.
 * 
 * CACHE BENEFITS:
 * - Instant app startup (shows cached prayer times immediately)
 * - Works in elevators/poor signal (uses cached location)
 * - Battery efficient (avoids repeated GPS/network calls)
 * - Offline functionality (works without internet/GPS)
 * 
 * CACHE STORAGE:
 * - Location data (with 30-minute validity)
 * - Prayer times (valid for the current day)
 * - Location names (for display)
 * 
 * EDIT THIS TO:
 * - Change cache validity periods
 * - Add new cache types
 * - Modify cache behavior
 */
@Singleton
class LocationCache @Inject constructor() {
    
    // CACHED DATA STORAGE - These hold the most recent successful data
    private var cachedLocation: Location? = null          // Last known GPS location
    private var cachedLocationTime: LocalDateTime? = null // When location was cached
    private var cachedPrayerTimes: DayPrayerTimes? = null // Today's calculated prayer times
    private var cachedPrayerTimesDate: LocalDate? = null  // Date prayer times were calculated for
    private var cachedLocationName: String? = null        // Human-readable location name
    
    /**
     * CACHE LOCATION: Store successful GPS location for fast future access
     * 
     * This saves GPS location data so we don't need to wait for GPS every time.
     * 
     * WHEN TO USE:
     * - After successful GPS location fetch
     * - When user manually sets location
     * - After geocoding address to coordinates
     * 
     * EDIT THIS TO:
     * - Add location validation
     * - Store additional location metadata
     */
    fun cacheLocation(location: Location) {
        cachedLocation = location
        cachedLocationTime = LocalDateTime.now() // Mark when we cached this location
    }
    
    /**
     * CACHE PRAYER TIMES: Store calculated prayer times for instant future access
     * 
     * This saves calculated prayer times so app starts instantly with cached data.
     * 
     * CACHE STRATEGY:
     * - Stores prayer times for specific date (usually today)
     * - Includes location name for display
     * - Automatically expires at midnight (new day = new calculations needed)
     * 
     * EDIT THIS TO:
     * - Cache multiple days
     * - Add prayer time validation
     * - Store calculation settings used
     */
    fun cachePrayerTimes(prayerTimes: DayPrayerTimes, date: LocalDate, locationName: String) {
        cachedPrayerTimes = prayerTimes      // The calculated prayer times
        cachedPrayerTimesDate = date         // Date these times are for
        cachedLocationName = locationName    // Where these times were calculated for
    }
    
    /**
     * GET CACHED LOCATION: Retrieve recent location data without GPS wait
     * 
     * This provides fast location access by checking if cached location is still fresh.
     * 
     * FRESHNESS POLICY:
     * - Location valid for 30 minutes (assumes user hasn't moved far)
     * - Returns null if too old (triggers new GPS request)
     * - Returns null if no cached data available
     * 
     * EDIT THIS TO:
     * - Change validity period (currently 30 minutes)
     * - Add location accuracy checks
     * - Implement smart expiry based on movement
     */
    fun getCachedLocation(): Location? {
        val location = cachedLocation
        val time = cachedLocationTime
        
        return if (location != null && time != null) {
            // Check how old the cached location is
            val ageMinutes = java.time.Duration.between(time, LocalDateTime.now()).toMinutes()
            
            // Return location only if it's fresh (within 30 minutes)
            if (ageMinutes <= 30) location else null // EDIT: Change 30 to adjust validity period
        } else null
    }
    
    /**
     * GET CACHED PRAYER TIMES: Retrieve today's prayer times for instant app startup
     * 
     * This enables instant app startup by showing cached prayer times immediately.
     * 
     * VALIDITY CHECK:
     * - Only returns data if it's for today's date
     * - Returns null if data is from yesterday (triggers new calculation)
     * - Returns null if no cached data available
     * 
     * RETURN FORMAT:
     * - Triple(prayer_times, date, location_name)
     * - All three values or null if invalid
     * 
     * EDIT THIS TO:
     * - Cache multiple days of prayer times
     * - Add timezone-aware date checking
     * - Return partial data when some is missing
     */
    fun getCachedPrayerTimes(): Triple<DayPrayerTimes?, LocalDate?, String?>? {
        val today = LocalDate.now()
        
        // Only return cached data if it's for today and actually exists
        return if (cachedPrayerTimesDate == today && cachedPrayerTimes != null) {
            Triple(cachedPrayerTimes, cachedPrayerTimesDate, cachedLocationName)
        } else {
            null // Data is stale or missing
        }
    }
    
    /**
     * CACHE VALIDITY CHECKER: Quick check if we have fresh prayer data
     * 
     * This is a fast way to check if cached data is available without retrieving it.
     * 
     * USED FOR:
     * - Deciding whether to show loading screen
     * - Determining if new calculation is needed
     * - UI state management
     * 
     * EDIT THIS TO:
     * - Add more validity criteria
     * - Check cache quality/accuracy
     * - Include location cache status
     */
    fun hasValidCachedData(): Boolean {
        val today = LocalDate.now()
        // Valid = has prayer times for today
        return cachedPrayerTimesDate == today && cachedPrayerTimes != null
    }
    
    /**
     * Clear all cached data
     */
    fun clearCache() {
        cachedLocation = null
        cachedLocationTime = null
        cachedPrayerTimes = null
        cachedPrayerTimesDate = null
        cachedLocationName = null
    }
    
    /**
     * Get cache status for debugging
     */
    fun getCacheStatus(): String {
        val locationAge = cachedLocationTime?.let { 
            java.time.Duration.between(it, LocalDateTime.now()).toMinutes()
        }
        val prayerTimesValid = hasValidCachedData()
        
        return "Location: ${if (cachedLocation != null) "cached (${locationAge}min ago)" else "none"}, " +
               "Prayer times: ${if (prayerTimesValid) "cached for today" else "none"}"
    }
}