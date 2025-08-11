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
                // For the last prayer (Isha), it's currently if we're past its time and no next prayer today
                index == prayers.size - 1 && now.isAfter(prayer.time) && nextPrayerIndex == -1 -> true
                // For other prayers, check if we're between this prayer and the next one
                index > 0 && now.isAfter(prayers[index - 1].time) && now.isBefore(prayer.time) -> true
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