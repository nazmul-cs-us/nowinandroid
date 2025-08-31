package com.starception.submission.prayer.cache

import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.Location
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple in-memory cache for location and prayer times to provide fast fallback
 */
@Singleton
class LocationCache @Inject constructor() {
    
    private var cachedLocation: Location? = null
    private var cachedLocationTime: LocalDateTime? = null
    private var cachedPrayerTimes: DayPrayerTimes? = null
    private var cachedPrayerTimesDate: LocalDate? = null
    private var cachedLocationName: String? = null
    
    /**
     * Cache location data
     */
    fun cacheLocation(location: Location) {
        cachedLocation = location
        cachedLocationTime = LocalDateTime.now()
    }
    
    /**
     * Cache prayer times data
     */
    fun cachePrayerTimes(prayerTimes: DayPrayerTimes, date: LocalDate, locationName: String) {
        cachedPrayerTimes = prayerTimes
        cachedPrayerTimesDate = date
        cachedLocationName = locationName
    }
    
    /**
     * Get cached location if it's recent (within 30 minutes)
     */
    fun getCachedLocation(): Location? {
        val location = cachedLocation
        val time = cachedLocationTime
        
        return if (location != null && time != null) {
            val ageMinutes = java.time.Duration.between(time, LocalDateTime.now()).toMinutes()
            if (ageMinutes <= 30) location else null
        } else null
    }
    
    /**
     * Get cached prayer times if they're for today
     */
    fun getCachedPrayerTimes(): Triple<DayPrayerTimes?, LocalDate?, String?>? {
        val today = LocalDate.now()
        return if (cachedPrayerTimesDate == today && cachedPrayerTimes != null) {
            Triple(cachedPrayerTimes, cachedPrayerTimesDate, cachedLocationName)
        } else null
    }
    
    /**
     * Check if we have valid cached data for today
     */
    fun hasValidCachedData(): Boolean {
        val today = LocalDate.now()
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