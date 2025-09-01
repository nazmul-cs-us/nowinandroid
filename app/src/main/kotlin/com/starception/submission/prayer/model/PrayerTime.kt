package com.starception.submission.prayer.model

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * PRAYER TIME MODEL: Represents a single Islamic prayer with its timing and status
 * 
 * This data class represents one prayer (Fajr, Dhuhr, Asr, Maghrib, or Isha) with its
 * calculated time and current status relative to the user's current time.
 * 
 * STATUS INDICATORS:
 * - isNext: This is the upcoming prayer that hasn't occurred yet
 * - isCurrently: This prayer time is currently active (between this prayer and the next)
 * 
 * USAGE:
 * - Prayer time display in UI
 * - Notification scheduling
 * - Prayer status tracking
 * - Time-until-next calculations
 * 
 * @param name Prayer name (e.g., "Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
 * @param time The calculated prayer time for this day
 * @param isNext True if this is the next prayer to occur
 * @param isCurrently True if we're currently in this prayer's time window
 */
data class PrayerTime(
    val name: String,
    val time: LocalTime,
    val isNext: Boolean = false,
    val isCurrently: Boolean = false
)

/**
 * DAILY PRAYER TIMES: Complete set of prayer times for a specific day
 * 
 * This data class contains all prayer times calculated for a specific day and location.
 * It includes both obligatory prayers (Fajr, Dhuhr, Asr, Maghrib, Isha) and astronomical
 * events (Sunrise) needed for calculations.
 * 
 * KEY FEATURES:
 * - All prayer times for one day
 * - Smart next/current prayer detection
 * - Time-until-next calculations
 * - Location-aware calculations
 * 
 * PRAYER TIMES INCLUDED:
 * - Fajr: Dawn prayer (before sunrise)
 * - Sunrise: Astronomical event (not a prayer, but important for calculations)
 * - Dhuhr: Noon prayer (after sun passes meridian)
 * - Asr: Afternoon prayer (when shadow equals object length)
 * - Maghrib: Sunset prayer (just after sunset)
 * - Isha: Night prayer (when twilight ends)
 * 
 * @param date The specific day these times are calculated for
 * @param fajr Fajr (dawn) prayer time
 * @param sunrise Sunrise time (not a prayer, but used for calculations)
 * @param dhuhr Dhuhr (noon) prayer time
 * @param asr Asr (afternoon) prayer time
 * @param maghrib Maghrib (sunset) prayer time
 * @param isha Isha (night) prayer time
 * @param location The location these times were calculated for
 */
data class DayPrayerTimes(
    val date: LocalDateTime,
    val fajr: LocalTime,
    val sunrise: LocalTime,
    val dhuhr: LocalTime,
    val asr: LocalTime,
    val maghrib: LocalTime,
    val isha: LocalTime,
    val location: Location
) {
    /**
     * GET ALL PRAYERS: Returns all prayer times including sunrise with current status
     * 
     * This function returns all prayer times and astronomical events (including sunrise)
     * with their current status (next/current) calculated based on the current time.
     * 
     * LOGIC:
     * - Compares current time with each prayer time
     * - Marks the next upcoming prayer as "isNext"
     * - Marks the currently active prayer window as "isCurrently"
     * - Handles day rollover (when no prayers left today, next is tomorrow's Fajr)
     * 
     * CURRENT PRAYER LOGIC:
     * - For Fajr-Maghrib: Active between this prayer and the next prayer
     * - For Isha: Active for up to 2 hours after Isha time (reasonable window)
     * 
     * @return List of all prayer times with status indicators
     */
    fun getAllPrayers(): List<PrayerTime> {
        val now = LocalTime.now()
        val prayers = listOf(
            PrayerTime("Fajr", fajr),
            PrayerTime("Sunrise", sunrise),
            PrayerTime("Dhuhr", dhuhr),
            PrayerTime("Asr", asr),
            PrayerTime("Maghrib", maghrib),
            PrayerTime("Isha", isha)
        )
        
        // Find next prayer today
        val nextPrayerIndex = prayers.indexOfFirst { it.time.isAfter(now) }
        
        return prayers.mapIndexed { index, prayer ->
            val isCurrently = when {
                // For Fajr to Maghrib prayers, check if we're between this prayer and the next
                index < prayers.size - 1 && now.isAfter(prayer.time) && now.isBefore(prayers[index + 1].time) -> true
                // For Isha prayer, only show as current for a reasonable time after it starts (max 2 hours)
                index == prayers.size - 1 && now.isAfter(prayer.time) && now.isBefore(prayer.time.plusHours(2)) -> true
                else -> false
            }
            
            val isNext = when {
                // If there's a next prayer today, mark it
                index == nextPrayerIndex -> true
                // If no prayers left today and this is Fajr, it might be tomorrow's next prayer
                nextPrayerIndex == -1 && index == 0 -> true
                else -> false
            }
            
            prayer.copy(
                isNext = isNext,
                isCurrently = isCurrently
            )
        }
    }
    
    /**
     * GET ACTUAL PRAYERS: Returns only obligatory prayers (excludes sunrise)
     * 
     * This function returns only the five daily obligatory prayers, excluding
     * astronomical events like sunrise. This is useful for UI displays that
     * only want to show actual prayer times.
     * 
     * PRAYERS INCLUDED:
     * - Fajr (dawn prayer)
     * - Dhuhr (noon prayer)  
     * - Asr (afternoon prayer)
     * - Maghrib (sunset prayer)
     * - Isha (night prayer)
     * 
     * PRAYERS EXCLUDED:
     * - Sunrise (astronomical event, not a prayer)
     * 
     * STATUS CALCULATION:
     * Uses same logic as getAllPrayers() but only for obligatory prayers.
     * 
     * @return List of obligatory prayers with status indicators
     */
    fun getActualPrayers(): List<PrayerTime> {
        val now = LocalTime.now()
        val actualPrayers = listOf(
            PrayerTime("Fajr", fajr),
            PrayerTime("Dhuhr", dhuhr),
            PrayerTime("Asr", asr),
            PrayerTime("Maghrib", maghrib),
            PrayerTime("Isha", isha)
        )
        
        // Find next prayer today
        val nextPrayerIndex = actualPrayers.indexOfFirst { it.time.isAfter(now) }
        
        return actualPrayers.mapIndexed { index, prayer ->
            val isCurrently = when {
                // For Fajr to Maghrib prayers, check if we're between this prayer and the next
                index < actualPrayers.size - 1 && now.isAfter(prayer.time) && now.isBefore(actualPrayers[index + 1].time) -> true
                // For Isha prayer, only show as current for a reasonable time after it starts (max 2 hours)
                index == actualPrayers.size - 1 && now.isAfter(prayer.time) && now.isBefore(prayer.time.plusHours(2)) -> true
                else -> false
            }
            
            val isNext = when {
                // If there's a next prayer today, mark it
                index == nextPrayerIndex -> true
                // If no prayers left today and this is Fajr, it might be tomorrow's next prayer
                nextPrayerIndex == -1 && index == 0 -> true
                else -> false
            }
            
            prayer.copy(
                isNext = isNext,
                isCurrently = isCurrently
            )
        }
    }
    
    /**
     * GET NEXT PRAYER: Finds the next upcoming prayer
     * 
     * This function determines which prayer is coming up next, handling day rollover
     * when no more prayers remain today.
     * 
     * LOGIC:
     * 1. Look for next prayer today (after current time)
     * 2. If no prayers left today, next prayer is tomorrow's Fajr
     * 3. Handles the cyclical nature of daily prayers
     * 
     * PRAYER CYCLE:
     * Fajr → Dhuhr → Asr → Maghrib → Isha → (next day) Fajr → ...
     * 
     * @return Next prayer to occur, or null if calculation fails
     */
    fun getNextPrayer(): PrayerTime? {
        val now = LocalTime.now()
        
        // First try to find a prayer today that's after current time
        val todayNextPrayer = getActualPrayers().firstOrNull { it.time.isAfter(now) }
        if (todayNextPrayer != null) {
            return todayNextPrayer
        }
        
        // If no prayers left today, the next prayer is tomorrow's Fajr
        // This handles the cyclical nature: Fajr -> Dhuhr -> Asr -> Maghrib -> Isha -> (next day) Fajr
        return PrayerTime("Fajr", fajr, isNext = true)
    }
    
    /**
     * GET TIME UNTIL NEXT PRAYER: Calculates remaining time until next prayer
     * 
     * This function calculates how much time remains until the next prayer,
     * providing a human-readable format for UI display.
     * 
     * TIME CALCULATION:
     * - For prayers today: Calculate duration from now to prayer time
     * - For tomorrow's Fajr: Calculate duration across day boundary
     * - Handles edge cases like prayers occurring right now
     * 
     * FORMAT LOGIC:
     * - Hours and minutes: "2h 30m"
     * - Minutes only: "15m"
     * - Immediate: "Now"
     * 
     * EDGE CASES:
     * - When prayer is happening right now
     * - When next prayer is tomorrow (day rollover)
     * - When calculation fails
     * 
     * @return Formatted time string (e.g., "2h 30m", "15m", "Now") or null if calculation fails
     */
    fun getTimeUntilNextPrayer(): String? {
        val nextPrayer = getNextPrayer() ?: return null
        val now = LocalTime.now()
        
        return if (nextPrayer.time.isAfter(now)) {
            // Next prayer is today
            val duration = java.time.Duration.between(now, nextPrayer.time)
            val hours = duration.toHours()
            val minutes = duration.toMinutesPart()
            
            when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "Now"
            }
        } else {
            // Next prayer is tomorrow's Fajr
            val duration = java.time.Duration.between(now, LocalTime.MAX) + 
                         java.time.Duration.between(LocalTime.MIN, nextPrayer.time)
            val hours = duration.toHours()
            val minutes = duration.toMinutesPart()
            
            when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "Now"
            }
        }
    }
}