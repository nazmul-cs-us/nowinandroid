package com.starception.submission.util

import android.util.Log

/**
 * Centralized logging utility for the entire app.
 * Set ENABLE_LOGGING to false to disable all logs except prayer time calculations.
 */
object AppLogger {
    
    // Master switch for all logs (except prayer time calculation)
    private const val ENABLE_LOGGING = false
    
    // Prayer time calculation logs are always enabled
    private const val ENABLE_PRAYER_CALCULATION_LOGS = true
    
    // Prayer time related tags
    private val PRAYER_CALCULATION_TAGS = setOf(
        "PrayerTimeCalculator",
        "PrayerTimesCalculator",
        "AstronomicalCalculator",
        "PrayerTimeCalculatorService",
        "CountryPrayerMethodService",
        "LocationService",
        "EnhancedLocationService"
    )
    
    fun d(tag: String, message: String) {
        if (shouldLog(tag)) {
            Log.d(tag, message)
        }
    }
    
    fun i(tag: String, message: String) {
        if (shouldLog(tag)) {
            Log.i(tag, message)
        }
    }
    
    fun w(tag: String, message: String) {
        if (shouldLog(tag)) {
            Log.w(tag, message)
        }
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (shouldLog(tag)) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }
    
    fun v(tag: String, message: String) {
        if (shouldLog(tag)) {
            Log.v(tag, message)
        }
    }
    
    private fun shouldLog(tag: String): Boolean {
        // Always allow prayer calculation logs
        if (ENABLE_PRAYER_CALCULATION_LOGS && PRAYER_CALCULATION_TAGS.contains(tag)) {
            return true
        }
        // Otherwise, respect the master switch
        return ENABLE_LOGGING
    }
}

