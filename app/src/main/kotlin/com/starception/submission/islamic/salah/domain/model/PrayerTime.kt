package com.starception.submission.islamic.salah.domain.model

import java.time.LocalTime

/**
 * Islamic Prayer Time Model
 * 
 * Represents a single Islamic prayer (Salah) with its precise timing and current status.
 * This is the core data model used throughout the Islamic prayer system.
 * 
 * ## Islamic Prayer Context
 * 
 * The five daily obligatory prayers (Salah):
 * - **Fajr** (الفجر): Dawn prayer - before sunrise
 * - **Dhuhr** (الظهر): Noon prayer - after sun's zenith  
 * - **Asr** (العصر): Afternoon prayer - based on shadow length
 * - **Maghrib** (المغرب): Sunset prayer - just after sunset
 * - **Isha** (العشاء): Night prayer - after twilight ends
 * 
 * @param name Prayer name (Fajr, Dhuhr, Asr, Maghrib, Isha)
 * @param arabicName Arabic name of the prayer
 * @param time Calculated prayer time
 * @param isNext True if this is the next upcoming prayer
 * @param isCurrently True if currently within this prayer's window
 */
data class PrayerTime(
    val name: String,
    val arabicName: String,
    val time: LocalTime,
    val isNext: Boolean = false,
    val isCurrently: Boolean = false
)

/**
 * Prayer Names Enumeration
 * 
 * Contains all Islamic prayer names with their Arabic equivalents
 */
object PrayerNames {
    const val FAJR = "Fajr"
    const val SUNRISE = "Sunrise"
    const val DHUHR = "Dhuhr"
    const val ASR = "Asr"
    const val MAGHRIB = "Maghrib"
    const val ISHA = "Isha"
    
    const val FAJR_ARABIC = "الفجر"
    const val SUNRISE_ARABIC = "الشروق"
    const val DHUHR_ARABIC = "الظهر"
    const val ASR_ARABIC = "العصر"
    const val MAGHRIB_ARABIC = "المغرب"
    const val ISHA_ARABIC = "العشاء"
    
    /**
     * Get Arabic name for prayer
     */
    fun getArabicName(prayerName: String): String = when (prayerName) {
        FAJR -> FAJR_ARABIC
        SUNRISE -> SUNRISE_ARABIC
        DHUHR -> DHUHR_ARABIC
        ASR -> ASR_ARABIC
        MAGHRIB -> MAGHRIB_ARABIC
        ISHA -> ISHA_ARABIC
        else -> prayerName
    }
}