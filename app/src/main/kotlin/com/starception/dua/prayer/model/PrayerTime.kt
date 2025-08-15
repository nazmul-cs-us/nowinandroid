package com.starception.submission.prayer.model

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Represents a specific prayer time
 */
data class PrayerTime(
    val name: String,
    val time: LocalTime,
    val isNext: Boolean = false,
    val isCurrently: Boolean = false
)

/**
 * Represents all prayer times for a specific day
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
                // For Isha prayer, check if we're past Isha but before midnight (we don't show "currently" after midnight)
                index == prayers.size - 1 && now.isAfter(prayer.time) && nextPrayerIndex == -1 && now.hour < 24 -> {
                    // Only show Isha as "currently" until around 2-3 AM, then no prayer is "current"
                    val hoursSinceIsha = if (now.hour >= prayer.time.hour) {
                        now.hour - prayer.time.hour
                    } else {
                        24 - prayer.time.hour + now.hour
                    }
                    hoursSinceIsha < 4  // Show as current for max 4 hours after Isha
                }
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
     * Get only actual prayers (excluding sunrise and other astronomical events)
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
                // For Isha prayer, check if we're past Isha but before midnight
                index == actualPrayers.size - 1 && now.isAfter(prayer.time) && nextPrayerIndex == -1 && now.hour < 24 -> {
                    val hoursSinceIsha = if (now.hour >= prayer.time.hour) {
                        now.hour - prayer.time.hour
                    } else {
                        24 - prayer.time.hour + now.hour
                    }
                    hoursSinceIsha < 4  // Show as current for max 4 hours after Isha
                }
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