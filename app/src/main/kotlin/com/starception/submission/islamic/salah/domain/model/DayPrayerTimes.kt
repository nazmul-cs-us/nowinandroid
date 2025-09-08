package com.starception.submission.islamic.salah.domain.model

import com.starception.submission.prayer.model.Location
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Daily Islamic Prayer Times Model
 * 
 * Contains all prayer times for a specific day with intelligent status tracking.
 * This model provides the complete daily prayer schedule with real-time status updates.
 * 
 * @param date The specific date and time these prayers are calculated for
 * @param fajr Dawn prayer time
 * @param sunrise Sunrise time (for calculation reference)
 * @param dhuhr Noon prayer time
 * @param asr Afternoon prayer time
 * @param maghrib Sunset prayer time
 * @param isha Night prayer time
 * @param location Geographic location used for calculations
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
     * Get all prayer times including sunrise with status calculation
     */
    fun getAllPrayers(): List<PrayerTime> {
        val now = LocalTime.now()
        val prayers = listOf(
            PrayerTime(
                name = PrayerNames.FAJR,
                arabicName = PrayerNames.FAJR_ARABIC,
                time = fajr
            ),
            PrayerTime(
                name = PrayerNames.SUNRISE,
                arabicName = PrayerNames.SUNRISE_ARABIC,
                time = sunrise
            ),
            PrayerTime(
                name = PrayerNames.DHUHR,
                arabicName = PrayerNames.DHUHR_ARABIC,
                time = dhuhr
            ),
            PrayerTime(
                name = PrayerNames.ASR,
                arabicName = PrayerNames.ASR_ARABIC,
                time = asr
            ),
            PrayerTime(
                name = PrayerNames.MAGHRIB,
                arabicName = PrayerNames.MAGHRIB_ARABIC,
                time = maghrib
            ),
            PrayerTime(
                name = PrayerNames.ISHA,
                arabicName = PrayerNames.ISHA_ARABIC,
                time = isha
            )
        )
        
        return applyStatusToList(prayers, now)
    }
    
    /**
     * Get only the five obligatory prayers (excludes sunrise)
     */
    fun getActualPrayers(): List<PrayerTime> {
        val now = LocalTime.now()
        val prayers = listOf(
            PrayerTime(
                name = PrayerNames.FAJR,
                arabicName = PrayerNames.FAJR_ARABIC,
                time = fajr
            ),
            PrayerTime(
                name = PrayerNames.DHUHR,
                arabicName = PrayerNames.DHUHR_ARABIC,
                time = dhuhr
            ),
            PrayerTime(
                name = PrayerNames.ASR,
                arabicName = PrayerNames.ASR_ARABIC,
                time = asr
            ),
            PrayerTime(
                name = PrayerNames.MAGHRIB,
                arabicName = PrayerNames.MAGHRIB_ARABIC,
                time = maghrib
            ),
            PrayerTime(
                name = PrayerNames.ISHA,
                arabicName = PrayerNames.ISHA_ARABIC,
                time = isha
            )
        )
        
        return applyStatusToList(prayers, now)
    }
    
    /**
     * Get the next upcoming prayer
     */
    fun getNextPrayer(): PrayerTime? {
        return getActualPrayers().find { it.isNext }
    }
    
    /**
     * Get the currently active prayer
     */
    fun getCurrentPrayer(): PrayerTime? {
        return getActualPrayers().find { it.isCurrently }
    }
    
    /**
     * Calculate time remaining until next prayer
     */
    fun getTimeUntilNextPrayer(): String {
        val nextPrayer = getNextPrayer() ?: return "Unknown"
        val now = LocalTime.now()
        
        val hoursUntil = if (nextPrayer.time.isAfter(now)) {
            nextPrayer.time.hour - now.hour
        } else {
            // Next day
            24 - now.hour + nextPrayer.time.hour
        }
        
        val minutesUntil = if (nextPrayer.time.isAfter(now)) {
            nextPrayer.time.minute - now.minute
        } else {
            // Next day calculation
            nextPrayer.time.minute - now.minute
        }
        
        return when {
            hoursUntil > 1 -> "${hoursUntil}h ${minutesUntil}m"
            hoursUntil == 1 -> "1h ${minutesUntil}m"
            minutesUntil > 0 -> "${minutesUntil}m"
            else -> "Now"
        }
    }
    
    /**
     * Apply status calculation to a list of prayers
     */
    private fun applyStatusToList(prayers: List<PrayerTime>, now: LocalTime): List<PrayerTime> {
        val nextPrayerIndex = prayers.indexOfFirst { it.time.isAfter(now) }
        
        return prayers.mapIndexed { index, prayer ->
            val isCurrently = when {
                // For prayers before Isha, check if we're between this prayer and the next
                index < prayers.size - 1 && now.isAfter(prayer.time) && now.isBefore(prayers[index + 1].time) -> true
                // For Isha prayer, only show as current for max 2 hours
                index == prayers.size - 1 && now.isAfter(prayer.time) && now.isBefore(prayer.time.plusHours(2)) -> true
                else -> false
            }
            
            val isNext = when {
                // If there's a next prayer today, mark it
                index == nextPrayerIndex -> true
                // If no prayers left today and this is Fajr, it's tomorrow's next prayer
                nextPrayerIndex == -1 && index == 0 -> true
                else -> false
            }
            
            prayer.copy(
                isNext = isNext,
                isCurrently = isCurrently
            )
        }
    }
}