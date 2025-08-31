package com.starception.submission.feature.prayertimes.utils

import java.text.SimpleDateFormat
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