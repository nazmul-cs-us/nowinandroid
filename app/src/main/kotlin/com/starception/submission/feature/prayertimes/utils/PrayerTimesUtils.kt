package com.starception.submission.feature.prayertimes.utils

import com.starception.submission.prayer.model.PrayerTimeOffsets
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Utility functions for prayer times formatting and calculations
 */

/**
 * Format LocalTime to user-friendly string (12-hour format)
 */
fun formatTime(time: LocalTime): String {
    val formatter = DateTimeFormatter.ofPattern("h:mm a")
    return time.format(formatter)
}

/**
 * Get current date formatted for display
 */
fun getCurrentDate(): String {
    val formatter = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
    return formatter.format(Date())
}

/**
 * Calculate Qibla direction using great circle formula
 * @param lat1 Current location latitude
 * @param lon1 Current location longitude
 * @param lat2 Kaaba latitude (21.4225)
 * @param lon2 Kaaba longitude (39.8262)
 * @return Qibla direction in degrees
 */
fun calculateQiblaDirection(
    lat1: Double, lon1: Double,
    lat2: Double = 21.4225, // Kaaba coordinates
    lon2: Double = 39.8262
): Double {
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val deltaLon = Math.toRadians(lon2 - lon1)

    val y = kotlin.math.sin(deltaLon)
    val x = kotlin.math.cos(lat1Rad) * kotlin.math.tan(lat2Rad) -
             kotlin.math.sin(lat1Rad) * kotlin.math.cos(deltaLon)

    var qibla = Math.toDegrees(kotlin.math.atan2(y, x))

    // ANGLE NORMALIZATION: Ensure result is between 0° and 360°
    // atan2 can return negative angles, so we add 360° if negative
    if (qibla < 0) qibla += 360.0

    return qibla  // Direction to Qibla in degrees from North
}

/**
 * PRAYER TIME OFFSET APPLICATION
 *
 * These functions apply user offset adjustments to base prayer times at the UI layer.
 *
 * ARCHITECTURE CHANGE (January 2025):
 * - DayPrayerTimes now contains BASE astronomical times only (no offsets)
 * - User offsets are applied HERE at the UI layer, not in calculation service
 * - This ensures clean separation: Calculation = base times, UI = apply offsets
 *
 * BENEFITS:
 * 1. Interactive dial has correct base reference point
 * 2. "Restore Auto-Generated Settings" simply sets offsets to 0
 * 3. No double-offset bugs
 * 4. Clear data flow: calculate base → store offsets → display adjusted
 */

/**
 * Apply offset to a base prayer time
 *
 * @param baseTime The astronomical base time (no offset applied)
 * @param offsetMinutes The user adjustment in minutes (positive = later, negative = earlier)
 * @return Adjusted time = baseTime + offsetMinutes
 */
fun applyOffsetToTime(baseTime: LocalTime, offsetMinutes: Int): LocalTime {
    if (offsetMinutes == 0) return baseTime

    val adjustedDateTime = LocalDateTime.of(LocalDate.now(), baseTime)
        .plusMinutes(offsetMinutes.toLong())

    return adjustedDateTime.toLocalTime()
}

/**
 * Get adjusted prayer time for a specific prayer
 *
 * @param prayerName Prayer name (e.g., "Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
 * @param baseTime The base astronomical time for this prayer
 * @param offsets User offset settings
 * @return Adjusted time = baseTime + corresponding offset
 */
fun getAdjustedPrayerTime(
    prayerName: String,
    baseTime: LocalTime,
    offsets: PrayerTimeOffsets
): LocalTime {
    val offset = when (prayerName.lowercase()) {
        "fajr" -> offsets.fajr
        "sunrise" -> offsets.sunrise
        "dhuhr" -> offsets.dhuhr
        "asr" -> offsets.asr
        "maghrib" -> offsets.maghrib
        "isha" -> offsets.isha
        else -> 0
    }

    return applyOffsetToTime(baseTime, offset)
}

/**
 * Format adjusted prayer time with offset indicator
 *
 * @param baseTime The base astronomical time
 * @param offsetMinutes The user adjustment in minutes
 * @param format12Hour If true, uses 12-hour format; if false, uses 24-hour format
 * @return Formatted string like "12:30 PM" or "12:30 PM (+5m)" if offset is non-zero
 */
fun formatAdjustedTime(
    baseTime: LocalTime,
    offsetMinutes: Int,
    format12Hour: Boolean = true,
    showOffset: Boolean = false
): String {
    val adjustedTime = applyOffsetToTime(baseTime, offsetMinutes)

    val timeString = if (format12Hour) {
        val hour12 = if (adjustedTime.hour == 0) 12
                     else if (adjustedTime.hour > 12) adjustedTime.hour - 12
                     else adjustedTime.hour
        val amPm = if (adjustedTime.hour < 12) "AM" else "PM"
        String.format("%d:%02d %s", hour12, adjustedTime.minute, amPm)
    } else {
        String.format("%02d:%02d", adjustedTime.hour, adjustedTime.minute)
    }

    return if (showOffset && offsetMinutes != 0) {
        val offsetSign = if (offsetMinutes > 0) "+" else ""
        "$timeString (${offsetSign}${offsetMinutes}m)"
    } else {
        timeString
    }
}