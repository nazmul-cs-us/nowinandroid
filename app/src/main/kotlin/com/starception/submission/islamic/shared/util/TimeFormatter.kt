package com.starception.submission.islamic.shared.util

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Islamic Prayer Time Formatting Utilities
 * 
 * Provides consistent time formatting across the Islamic prayer application
 * with support for different time formats and Islamic conventions.
 */
object TimeFormatter {
    
    /**
     * Standard 12-hour format for prayer times
     */
    private val time12HourFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    
    /**
     * Standard 24-hour format for prayer times
     */
    private val time24HourFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    
    /**
     * Format prayer time for display
     * 
     * @param time The prayer time to format
     * @param use24Hour Whether to use 24-hour format (default: false)
     * @return Formatted time string
     */
    fun formatPrayerTime(time: LocalTime, use24Hour: Boolean = false): String {
        return if (use24Hour) {
            time.format(time24HourFormatter)
        } else {
            time.format(time12HourFormatter)
        }
    }
    
    /**
     * Format time until next prayer
     * 
     * @param hours Hours remaining
     * @param minutes Minutes remaining  
     * @return Human-readable time remaining string
     */
    fun formatTimeUntilPrayer(hours: Int, minutes: Int): String {
        return when {
            hours > 1 -> "${hours}h ${minutes}m"
            hours == 1 -> "1h ${minutes}m"
            minutes > 1 -> "${minutes}m"
            minutes == 1 -> "1m"
            else -> "Now"
        }
    }
    
    /**
     * Calculate and format time remaining until specific prayer time
     * 
     * @param prayerTime Target prayer time
     * @param currentTime Current time (defaults to now)
     * @return Formatted time remaining string
     */
    fun calculateTimeUntil(
        prayerTime: LocalTime,
        currentTime: LocalTime = LocalTime.now()
    ): String {
        val hoursUntil = if (prayerTime.isAfter(currentTime)) {
            prayerTime.hour - currentTime.hour
        } else {
            // Next day calculation
            24 - currentTime.hour + prayerTime.hour
        }
        
        val minutesUntil = if (prayerTime.isAfter(currentTime) && hoursUntil >= 0) {
            prayerTime.minute - currentTime.minute
        } else {
            // Handle minute rollover
            prayerTime.minute - currentTime.minute
        }
        
        // Adjust for negative minutes
        val adjustedHours = if (minutesUntil < 0) hoursUntil - 1 else hoursUntil
        val adjustedMinutes = if (minutesUntil < 0) 60 + minutesUntil else minutesUntil
        
        return formatTimeUntilPrayer(adjustedHours, adjustedMinutes)
    }
    
    /**
     * Get prayer time period description
     * 
     * @param prayerName Name of the prayer
     * @return Descriptive text for the prayer period
     */
    fun getPrayerPeriodDescription(prayerName: String): String {
        return when (prayerName.lowercase()) {
            "fajr" -> "Dawn • Before Sunrise"
            "dhuhr" -> "Noon • After Sun's Zenith"
            "asr" -> "Afternoon • Shadow Length"
            "maghrib" -> "Sunset • Just After Sunset"
            "isha" -> "Night • After Twilight"
            else -> "Islamic Prayer"
        }
    }
    
    /**
     * Get Islamic greeting for time of day
     * 
     * @param currentTime Current time
     * @return Appropriate Islamic greeting
     */
    fun getIslamicGreeting(currentTime: LocalTime = LocalTime.now()): String {
        return when (currentTime.hour) {
            in 0..5 -> "ٱلسَّلَامُ عَلَيْكُمْ" // As-salamu alaykum (Peace be upon you)
            in 6..11 -> "صَبَاحُ ٱلْخَيْر" // Sabah al-khayr (Good morning)
            in 12..16 -> "مَسَاءُ ٱلْخَيْر" // Masa' al-khayr (Good afternoon)
            in 17..20 -> "مَسَاءُ ٱلْخَيْر" // Masa' al-khayr (Good evening)
            else -> "ٱلسَّلَامُ عَلَيْكُمْ" // As-salamu alaykum (Peace be upon you)
        }
    }
}