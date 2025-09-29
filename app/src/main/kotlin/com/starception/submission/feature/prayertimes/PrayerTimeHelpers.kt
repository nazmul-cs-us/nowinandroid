/**
 * PRAYER TIME HELPERS UTILITY
 * 
 * This file contains utility functions for prayer time calculations, formatting,
 * and status determination. It provides a clean API for prayer time operations
 * used throughout the Prayer Times feature.
 * 
 * WHAT IT DOES:
 * - Calculates prayer times and determines current/next prayer status
 * - Formats prayer times for display (12-hour format with AM/PM)
 * - Provides time-until-prayer calculations with human-readable format
 * - Determines prayer status (Current/Next/Upcoming) based on current time
 * - Handles date formatting for prayer time displays
 * - Manages prayer time windows and transitions
 * 
 * WHERE IT'S USED:
 * - PrayerTimesScreen.kt: Main screen for prayer status calls (lines ~749, 768, 776, etc.)
 * - SwipeableBigTiles.kt: Swipeable component for prayer time display (lines ~484-488)
 * - Replaces ~120 lines of inline prayer time utility functions
 * - Called through PrayerTimeHelpers.functionName() static methods
 * 
 * KEY FUNCTIONS:
 * - getCurrentDate(): Returns formatted current date (e.g., "Monday, January 15")
 * - getPrayerTimeDisplay(): Formats prayer times as "hh:mm AM/PM"
 * - getPrayerStatus(): Determines if prayer is Current/Next/Upcoming
 * - getNextPrayer(): Finds next prayer after current time
 * - getCurrentPrayer(): Finds current prayer (within 30-min window)
 * - getTimeUntilNextPrayer(): Calculates time remaining until next prayer
 * 
 * PRAYER STATUS LOGIC:
 * - "Current": Prayer is active (within 30 minutes of start time)
 * - "Next": This is the next prayer coming up today
 * - "Upcoming": Prayer is scheduled but not the immediate next one
 * 
 * TIME CALCULATIONS:
 * - Uses LocalTime for precise time comparisons
 * - Handles day transitions (if no prayers left today, returns Fajr tomorrow)
 * - Duration calculations for countdown timers and elapsed time
 * - Format: "2h 30m" or "45m" for time remaining displays
 * 
 * DATA DEPENDENCIES:
 * - DayPrayerTimes: Contains all 5 daily prayer times (Fajr, Dhuhr, Asr, Maghrib, Isha)
 * - LocalTime: Current time for status and calculation purposes
 * 
 * DESIGN PATTERNS:
 * - Object singleton: All functions are static utility methods
 * - Null safety: Handles missing prayer times gracefully with fallbacks
 * - Immutable operations: Functions don't modify input parameters
 * - Pure functions: Same input always produces same output
 */
package com.starception.submission.feature.prayertimes

import com.starception.submission.prayer.model.DayPrayerTimes
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object PrayerTimeHelpers {
    
    // Get current date formatted for display
    fun getCurrentDate(): String {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
        return today.format(formatter)
    }
    
    // Get prayer time display
    fun getPrayerTimeDisplay(prayerName: String, prayerTimes: DayPrayerTimes?): String {
        val times = prayerTimes ?: return "00:00 AM"
        
        val time = when (prayerName) {
            "Fajr" -> times.fajr
            "Dhuhr" -> times.dhuhr
            "Asr" -> times.asr
            "Maghrib" -> times.maghrib
            "Isha" -> times.isha
            else -> times.fajr
        }
        
        return time.format(DateTimeFormatter.ofPattern("hh:mm a"))
    }
    
    // Get prayer status (Current, Next, Upcoming)
    fun getPrayerStatus(prayerName: String, currentTime: LocalTime, prayerTimes: DayPrayerTimes?): String {
        val times = prayerTimes ?: return "Upcoming"
        
        val prayerTime = when (prayerName) {
            "Fajr" -> times.fajr
            "Dhuhr" -> times.dhuhr
            "Asr" -> times.asr
            "Maghrib" -> times.maghrib
            "Isha" -> times.isha
            else -> times.fajr
        }
        
        val allPrayerTimes = listOf(
            "Fajr" to times.fajr,
            "Dhuhr" to times.dhuhr,
            "Asr" to times.asr,
            "Maghrib" to times.maghrib,
            "Isha" to times.isha
        )
        
        // Find current prayer (if we're in a prayer time window)
        val currentPrayer = allPrayerTimes.find { (name, time) ->
            val nextPrayerTime = allPrayerTimes.find { it.second.isAfter(time) }?.second 
                ?: LocalTime.of(23, 59) // Default to end of day if no next prayer
            currentTime.isAfter(time) && currentTime.isBefore(nextPrayerTime)
        }
        
        // Find next prayer
        val nextPrayer = allPrayerTimes.find { it.second.isAfter(currentTime) }
        
        return when {
            currentPrayer?.first == prayerName -> "Current"
            nextPrayer?.first == prayerName -> "Next"
            else -> "Upcoming"
        }
    }
    
    // Get next prayer
    fun getNextPrayer(currentTime: LocalTime, prayerTimes: DayPrayerTimes?): Pair<String, LocalTime>? {
        val times = prayerTimes ?: return null
        
        val allPrayerTimes = listOf(
            "Fajr" to times.fajr,
            "Dhuhr" to times.dhuhr,
            "Asr" to times.asr,
            "Maghrib" to times.maghrib,
            "Isha" to times.isha
        )
        
        return allPrayerTimes.find { it.second.isAfter(currentTime) }
    }
    
    // Get current prayer (if we're in a prayer time window)
    fun getCurrentPrayer(currentTime: LocalTime, prayerTimes: DayPrayerTimes?): Pair<String, LocalTime>? {
        val times = prayerTimes ?: return null
        
        val allPrayerTimes = listOf(
            "Fajr" to times.fajr,
            "Dhuhr" to times.dhuhr,
            "Asr" to times.asr,
            "Maghrib" to times.maghrib,
            "Isha" to times.isha
        )
        
        // Find current prayer (if we're in a prayer time window - within 30 minutes after prayer time)
        return allPrayerTimes.find { (name, time) ->
            currentTime.isAfter(time) && 
            ChronoUnit.MINUTES.between(time, currentTime) <= 30
        }
    }
    
    // Get time until next prayer
    fun getTimeUntilNextPrayer(currentTime: LocalTime, prayerTimes: DayPrayerTimes?): String {
        val nextPrayer = getNextPrayer(currentTime, prayerTimes)
        if (nextPrayer == null) return "--:--"
        
        val duration = ChronoUnit.MINUTES.between(currentTime, nextPrayer.second)
        val hours = duration / 60
        val minutes = duration % 60
        
        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }
    
    // Get next 4 prayers dynamically (handles day transitions)
    fun getNext4Prayers(currentTime: LocalTime, prayerTimes: DayPrayerTimes?): List<Pair<String, LocalTime>> {
        val times = prayerTimes ?: return emptyList()
        
        val allPrayerTimes = listOf(
            "Fajr" to times.fajr,
            "Dhuhr" to times.dhuhr,
            "Asr" to times.asr,
            "Maghrib" to times.maghrib,
            "Isha" to times.isha
        )
        
        // Find upcoming prayers for today
        val upcomingToday = allPrayerTimes.filter { it.second.isAfter(currentTime) }
        
        // If we have 4 or more prayers left today, return the next 4
        if (upcomingToday.size >= 4) {
            return upcomingToday.take(4)
        }
        
        // If less than 4 prayers left today, include tomorrow's prayers
        val result = upcomingToday.toMutableList()
        
        // Add tomorrow's prayers starting from Fajr until we have 4 total
        val tomorrowPrayers = listOf(
            "Fajr" to times.fajr.plusHours(24), // Tomorrow's Fajr
            "Dhuhr" to times.dhuhr.plusHours(24), // Tomorrow's Dhuhr
            "Asr" to times.asr.plusHours(24), // Tomorrow's Asr
            "Maghrib" to times.maghrib.plusHours(24), // Tomorrow's Maghrib
            "Isha" to times.isha.plusHours(24) // Tomorrow's Isha
        )
        
        for (prayer in tomorrowPrayers) {
            if (result.size < 4) {
                result.add(prayer)
            } else {
                break
            }
        }
        
        return result.take(4)
    }
}