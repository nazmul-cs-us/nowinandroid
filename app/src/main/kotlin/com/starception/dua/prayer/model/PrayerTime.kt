package com.starception.dua.prayer.model

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
        
        // Find next prayer
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
            
            prayer.copy(
                isNext = index == nextPrayerIndex,
                isCurrently = isCurrently
            )
        }
    }
    
    fun getNextPrayer(): PrayerTime? {
        val now = LocalTime.now()
        return getAllPrayers().firstOrNull { it.time.isAfter(now) }
    }
}