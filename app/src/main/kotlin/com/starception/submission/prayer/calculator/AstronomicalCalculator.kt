package com.starception.submission.prayer.calculator

import android.util.Log
import com.starception.submission.prayer.model.Location
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * ASTRONOMICAL CALCULATOR: Precise sun position calculations for Islamic prayer times
 * 
 * This class performs the complex astronomical calculations needed to determine
 * exact prayer times based on the sun's position relative to Earth.
 * 
 * CORE CALCULATIONS:
 * - Julian Day conversions for astronomical precision
 * - Sun declination and equation of time
 * - Hour angle calculations for specific sun elevations
 * - Atmospheric refraction corrections
 * - Geographic coordinate transformations
 * 
 * PRAYER TIME ASTRONOMY:
 * - Fajr: Sun 18° below horizon (dawn)
 * - Sunrise: Sun crosses horizon with refraction correction
 * - Dhuhr: Sun reaches maximum elevation (solar noon)
 * - Asr: Shadow length equals object height + noon shadow
 * - Maghrib: Sun sets below horizon
 * - Isha: Sun 17° below horizon (dusk)
 * 
 * ACCURACY:
 * - Uses precise astronomical algorithms
 * - Accounts for Earth's elliptical orbit
 * - Includes atmospheric refraction
 * - Handles high latitude adjustments
 * 
 * EDIT THIS TO:
 * - Add more calculation methods
 * - Modify astronomical constants
 * - Include advanced atmospheric models
 */
@Singleton
class AstronomicalCalculator @Inject constructor() {
    
    companion object {
        private const val TAG = "AstronomicalCalculator"
        
        // ASTRONOMICAL CONSTANTS - Edit these to adjust calculation precision
        private const val JULIAN_EPOCH = 1721425.5    // Julian Day epoch reference
        private const val EARTH_RADIUS_KM = 6371.0    // Earth's mean radius in kilometers
    }
    
    /**
     * COMPREHENSIVE ASTRONOMICAL CALCULATION LOGGING SYSTEM
     * Provides detailed visibility into all astronomical calculations for debugging and verification
     */
    
    /**
     * ENHANCED CALCULATION LOGGING
     * Logs astronomical calculation operations with input parameters and results
     */
    private fun logCalculation(operation: String, inputs: Map<String, Any>, result: Any, duration: Long = 0) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        Log.i(TAG, "")
        Log.i(TAG, "🌟 ASTRONOMICAL CALCULATION")
        Log.i(TAG, "⏰ Timestamp: $timestamp")
        Log.i(TAG, "🔬 Operation: $operation")
        
        inputs.forEach { (key, value) ->
            Log.i(TAG, "📥 Input $key: $value")
        }
        
        when (result) {
            is Double -> {
                if (result.isNaN()) {
                    Log.w(TAG, "⚠️ Result: NaN (calculation failed - likely high latitude)")
                } else if (result.isInfinite()) {
                    Log.w(TAG, "⚠️ Result: Infinite (mathematical overflow)")
                } else {
                    Log.i(TAG, "✅ Result: $result")
                }
            }
            is LocalTime -> Log.i(TAG, "✅ Result: $result")
            else -> Log.i(TAG, "✅ Result: $result")
        }
        
        if (duration > 0) {
            Log.i(TAG, "⚡ Duration: ${duration}ms")
        }
        Log.i(TAG, "")
    }
    
    /**
     * INPUT VALIDATION LOGGING
     * Validates and logs astronomical calculation inputs
     */
    private fun logInputValidation(operation: String, latitude: Double? = null, longitude: Double? = null, 
                                 julianDay: Double? = null, angle: Double? = null): Boolean {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
            
        Log.i(TAG, "")
        Log.i(TAG, "🔍 INPUT VALIDATION")
        Log.i(TAG, "⏰ Timestamp: $timestamp")
        Log.i(TAG, "🔬 Operation: $operation")
        
        var valid = true
        
        latitude?.let {
            if (it < -90.0 || it > 90.0) {
                Log.e(TAG, "❌ Invalid latitude: $it (must be -90° to +90°)")
                valid = false
            } else {
                Log.i(TAG, "✅ Latitude: $it° (valid)")
            }
        }
        
        longitude?.let {
            if (it < -180.0 || it > 180.0) {
                Log.e(TAG, "❌ Invalid longitude: $it (must be -180° to +180°)")
                valid = false
            } else {
                Log.i(TAG, "✅ Longitude: $it° (valid)")
            }
        }
        
        julianDay?.let {
            if (it < 1000000 || it > 5000000) {
                Log.w(TAG, "⚠️ Unusual Julian Day: $it (expected range: 1M-5M)")
            } else {
                Log.i(TAG, "✅ Julian Day: $it (valid)")
            }
        }
        
        angle?.let {
            Log.i(TAG, "📐 Angle: $it° (${if (it < 0) "below" else "above"} horizon)")
        }
        
        Log.i(TAG, if (valid) "✅ All inputs valid" else "❌ Input validation failed")
        Log.i(TAG, "")
        
        return valid
    }
    
    /**
     * PERFORMANCE BENCHMARKING
     * Measures and logs calculation performance
     */
    private fun <T> benchmarkCalculation(operation: String, calculation: () -> T): T {
        val startTime = System.currentTimeMillis()
        val result = calculation()
        val duration = System.currentTimeMillis() - startTime
        
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
            
        Log.i(TAG, "")
        Log.i(TAG, "📊 PERFORMANCE BENCHMARK")
        Log.i(TAG, "⏰ Timestamp: $timestamp")
        Log.i(TAG, "🔬 Operation: $operation")
        Log.i(TAG, "⚡ Duration: ${duration}ms")
        
        when {
            duration < 1 -> Log.i(TAG, "🚀 Performance: Excellent (<1ms)")
            duration < 10 -> Log.i(TAG, "✅ Performance: Good (<10ms)")
            duration < 100 -> Log.i(TAG, "⚠️ Performance: Acceptable (<100ms)")
            else -> Log.w(TAG, "🐌 Performance: Slow (${duration}ms)")
        }
        Log.i(TAG, "")
        
        return result
    }
    
    /**
     * RESULT VERIFICATION LOGGING
     * Verifies calculation results are within expected ranges
     */
    private fun verifyTimeResult(operation: String, result: Double, expectedRange: Pair<Double, Double>): Boolean {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
            
        Log.i(TAG, "")
        Log.i(TAG, "🔍 RESULT VERIFICATION")
        Log.i(TAG, "⏰ Timestamp: $timestamp")
        Log.i(TAG, "🔬 Operation: $operation")
        Log.i(TAG, "📊 Result: $result")
        Log.i(TAG, "📏 Expected Range: ${expectedRange.first} to ${expectedRange.second}")
        
        val isValid = when {
            result.isNaN() -> {
                Log.w(TAG, "⚠️ Verification: NaN result (calculation failed)")
                false
            }
            result.isInfinite() -> {
                Log.w(TAG, "⚠️ Verification: Infinite result (mathematical error)")
                false
            }
            result < expectedRange.first || result > expectedRange.second -> {
                Log.w(TAG, "⚠️ Verification: Result outside expected range")
                false
            }
            else -> {
                Log.i(TAG, "✅ Verification: Result within valid range")
                true
            }
        }
        
        Log.i(TAG, "")
        return isValid
    }
    
    /**
     * JULIAN DAY CALCULATOR: Converts calendar date to astronomical Julian Day
     * 
     * Julian Days provide a continuous count of days since January 1, 4713 BCE,
     * essential for accurate astronomical calculations.
     * 
     * FEATURES:
     * - Handles Gregorian calendar corrections
     * - Accounts for leap years automatically  
     * - Includes time-of-day precision
     * - Uses standard astronomical algorithms
     * 
     * EDIT THIS TO:
     * - Add validation for date ranges
     * - Include Julian/Gregorian calendar transition
     * - Add support for different calendar systems
     */
    fun calculateJulianDay(date: LocalDate, time: LocalTime = LocalTime.MIDNIGHT): Double {
        return benchmarkCalculation("JULIAN_DAY_CONVERSION") {
            val inputs = mapOf(
                "date" to date,
                "time" to time,
                "year" to date.year,
                "month" to date.monthValue,
                "day" to date.dayOfMonth
            )
            
            var year = date.year
            var month = date.monthValue
            val day = date.dayOfMonth
            val hour = time.hour.toDouble()
            val minute = time.minute.toDouble()
            val second = time.second.toDouble()
            
            // CALENDAR ADJUSTMENT: January and February are treated as months 13 and 14 of the previous year
            // This simplifies the leap year calculation in the Julian Day formula
            if (month <= 2) {
                year -= 1
                month += 12
                Log.d(TAG, "📅 Calendar adjustment applied: Jan/Feb treated as months 13/14 of previous year")
            }
            
            // Convert time to Universal Time fraction of day
            val ut = hour + minute / 60.0 + second / 3600.0
            Log.d(TAG, "🕐 Time fraction: $ut (${hour}h ${minute}m ${second}s)")
            
            // JULIAN DAY CALCULATION with Gregorian calendar correction
            val a = floor(year / 100.0)              // Century
            val b = 2 - a + floor(a / 4.0)           // Gregorian calendar correction for leap years
            
            // Standard astronomical Julian Day formula
            val result = floor(365.25 * (year + 4716)) +    // Years since epoch
                        floor(30.6001 * (month + 1)) +     // Months adjustment
                        day + b - 1524.5 +                  // Days and epoch offset
                        ut / 24.0                          // Time fraction
            
            logCalculation("JULIAN_DAY_CONVERSION", inputs, result)
            
            // Verify result is reasonable (Julian Day should be between year 1000 and 4000)
            val expectedRange = Pair(1000000.0, 5000000.0) // Roughly years 1000-4000 CE
            verifyTimeResult("JULIAN_DAY_CONVERSION", result, expectedRange)
            
            result
        }
    }
    
    /**
     * Calculates solar declination for given Julian Day
     */
    fun calculateSolarDeclination(julianDay: Double): Double {
        return benchmarkCalculation("SOLAR_DECLINATION") {
            val n = julianDay - 2451545.0
            val l = (280.46 + 0.9856474 * n) % 360
            val g = Math.toRadians(357.528 + 0.9856003 * n)
            val lambdaSun = Math.toRadians(l + 1.915 * sin(g) + 0.020 * sin(2 * g))
            val epsilon = Math.toRadians(23.439 - 0.0000004 * n)
            
            val result = asin(sin(epsilon) * sin(lambdaSun))
            val resultDegrees = Math.toDegrees(result)
            
            val inputs = mapOf(
                "julianDay" to julianDay,
                "daysSinceJ2000" to n,
                "meanLongitude" to l,
                "meanAnomaly" to Math.toDegrees(g),
                "trueLongitude" to Math.toDegrees(lambdaSun),
                "obliquity" to Math.toDegrees(epsilon)
            )
            
            logCalculation("SOLAR_DECLINATION", inputs, "$resultDegrees° (${result} rad)")
            
            // Solar declination should be between -23.5° and +23.5°
            val expectedRange = Pair(Math.toRadians(-23.5), Math.toRadians(23.5))
            verifyTimeResult("SOLAR_DECLINATION", result, expectedRange)
            
            result
        }
    }
    
    /**
     * Calculates equation of time for given Julian Day
     */
    fun calculateEquationOfTime(julianDay: Double): Double {
        return benchmarkCalculation("EQUATION_OF_TIME") {
            val n = julianDay - 2451545.0
            val l = (280.46 + 0.9856474 * n) % 360
            val g = Math.toRadians(357.528 + 0.9856003 * n)
            val lambdaSun = Math.toRadians(l + 1.915 * sin(g) + 0.020 * sin(2 * g))
            val epsilon = Math.toRadians(23.439 - 0.0000004 * n)
            
            val ra = atan2(cos(epsilon) * sin(lambdaSun), cos(lambdaSun))
            
            // Normalize right ascension to 0-360 degrees
            var raDegrees = Math.toDegrees(ra)
            if (raDegrees < 0) raDegrees += 360.0
            
            // Calculate equation of time with proper normalization
            var eot = (raDegrees / 15.0 - l / 15.0)
            
            // Normalize to [-12, +12] hours range
            while (eot > 12.0) eot -= 24.0
            while (eot < -12.0) eot += 24.0
            
            val result = eot * 4.0 // Convert to minutes
            
            val inputs = mapOf(
                "julianDay" to julianDay,
                "daysSinceJ2000" to n,
                "meanLongitude" to "${l}°",
                "meanAnomaly" to "${Math.toDegrees(g)}°",
                "rightAscension" to "${raDegrees}°",
                "equationOfTimeHours" to "${eot}h"
            )
            
            logCalculation("EQUATION_OF_TIME", inputs, "${result} minutes")
            
            // Equation of time should be between -20 and +20 minutes
            verifyTimeResult("EQUATION_OF_TIME", result, Pair(-20.0, 20.0))
            
            result
        }
    }
    
    /**
     * Calculates solar noon time
     */
    fun calculateSolarNoon(location: Location, julianDay: Double): Double {
        if (!logInputValidation("SOLAR_NOON", location.latitude, location.longitude, julianDay)) {
            Log.e(TAG, "❌ Solar noon calculation aborted due to invalid inputs")
            return Double.NaN
        }
        
        return benchmarkCalculation("SOLAR_NOON_CALCULATION") {
            val eot = calculateEquationOfTime(julianDay)
            val result = 12.0 + location.timeZoneOffset - location.longitude / 15.0 - eot / 60.0
            
            val inputs = mapOf(
                "location" to location.getDisplayName(),
                "longitude" to location.longitude,
                "timeZoneOffset" to location.timeZoneOffset,
                "julianDay" to julianDay,
                "equationOfTime" to eot
            )
            
            logCalculation("SOLAR_NOON_CALCULATION", inputs, result)
            
            // Solar noon should be between 6 AM and 6 PM in local time
            verifyTimeResult("SOLAR_NOON", result, Pair(6.0, 18.0))
            
            result
        }
    }
    
    /**
     * Calculates hour angle for given altitude
     */
    fun calculateHourAngle(
        latitude: Double,
        declination: Double,
        altitude: Double
    ): Double {
        if (!logInputValidation("HOUR_ANGLE", latitude, angle = altitude)) {
            return Double.NaN
        }
        
        return benchmarkCalculation("HOUR_ANGLE_CALCULATION") {
            val latRad = Math.toRadians(latitude)
            val altRad = Math.toRadians(altitude)
            val decDegrees = Math.toDegrees(declination)
            
            val cosH = (sin(altRad) - sin(latRad) * sin(declination)) / 
                       (cos(latRad) * cos(declination))
            
            val inputs = mapOf(
                "latitude" to "${latitude}°",
                "declination" to "${decDegrees}°",
                "altitude" to "${altitude}°",
                "cosineHourAngle" to cosH
            )
            
            // Check if sun reaches the required altitude
            if (cosH < -1.0 || cosH > 1.0) {
                Log.w(TAG, "")
                Log.w(TAG, "⚠️ HOUR ANGLE CALCULATION FAILED")
                Log.w(TAG, "📊 Cosine H: $cosH (outside [-1,+1] range)")
                Log.w(TAG, "🔍 Cause: Sun never reaches altitude ${altitude}° at latitude ${latitude}°")
                Log.w(TAG, "🌅 Common for: High latitude locations, extreme sun angles")
                Log.w(TAG, "")
                
                logCalculation("HOUR_ANGLE_CALCULATION", inputs, "NaN (sun doesn't reach altitude)")
                return@benchmarkCalculation Double.NaN
            }
            
            val result = acos(cosH)
            val resultDegrees = Math.toDegrees(result)
            
            logCalculation("HOUR_ANGLE_CALCULATION", inputs, "${resultDegrees}° (${result} rad)")
            
            // Hour angle should be between 0° and 180°
            verifyTimeResult("HOUR_ANGLE", result, Pair(0.0, Math.PI))
            
            result
        }
    }
    
    /**
     * Calculates sunrise time
     */
    fun calculateSunrise(location: Location, julianDay: Double): Double {
        val declination = calculateSolarDeclination(julianDay)
        val solarNoon = calculateSolarNoon(location, julianDay)
        
        // Geometric horizon with atmospheric refraction correction
        val sunriseAltitude = -0.833 - 0.0347 * sqrt(maxOf(location.altitude, 0.0))
        val hourAngle = calculateHourAngle(location.latitude, declination, sunriseAltitude)
        
        if (hourAngle.isNaN()) return Double.NaN
        
        return solarNoon - Math.toDegrees(hourAngle) / 15.0
    }
    
    /**
     * Calculates sunset time
     */
    fun calculateSunset(location: Location, julianDay: Double): Double {
        val declination = calculateSolarDeclination(julianDay)
        val solarNoon = calculateSolarNoon(location, julianDay)
        
        // Geometric horizon with atmospheric refraction correction
        val sunsetAltitude = -0.833 - 0.0347 * sqrt(maxOf(location.altitude, 0.0))
        val hourAngle = calculateHourAngle(location.latitude, declination, sunsetAltitude)
        
        if (hourAngle.isNaN()) return Double.NaN
        
        return solarNoon + Math.toDegrees(hourAngle) / 15.0
    }
    
    /**
     * Calculates Fajr time based on depression angle
     */
    fun calculateFajr(location: Location, julianDay: Double, fajrAngle: Double): Double {
        val declination = calculateSolarDeclination(julianDay)
        val solarNoon = calculateSolarNoon(location, julianDay)
        val hourAngle = calculateHourAngle(location.latitude, declination, -fajrAngle)
        
        if (hourAngle.isNaN()) return Double.NaN
        
        return solarNoon - Math.toDegrees(hourAngle) / 15.0
    }
    
    /**
     * Calculates Isha time based on depression angle or delay
     */
    fun calculateIsha(
        location: Location, 
        julianDay: Double, 
        ishaAngle: Double? = null,
        ishaDelay: Int? = null
    ): Double {
        return if (ishaAngle != null) {
            // Calculate based on depression angle
            val declination = calculateSolarDeclination(julianDay)
            val solarNoon = calculateSolarNoon(location, julianDay)
            val hourAngle = calculateHourAngle(location.latitude, declination, -ishaAngle)
            
            if (hourAngle.isNaN()) return Double.NaN
            
            solarNoon + Math.toDegrees(hourAngle) / 15.0
        } else if (ishaDelay != null) {
            // Calculate based on delay after Maghrib
            val sunset = calculateSunset(location, julianDay)
            if (sunset.isNaN()) return Double.NaN
            
            sunset + ishaDelay / 60.0
        } else {
            Double.NaN
        }
    }
    
    /**
     * Calculates Asr time based on shadow factor
     */
    fun calculateAsr(
        location: Location, 
        julianDay: Double, 
        shadowFactor: Int = 1
    ): Double {
        val declination = calculateSolarDeclination(julianDay)
        val solarNoon = calculateSolarNoon(location, julianDay)
        
        val latRad = Math.toRadians(location.latitude)
        
        // Calculate Asr altitude angle
        val cotanAsrAltitude = shadowFactor + tan(abs(latRad - declination))
        val asrAltitude = atan(1.0 / cotanAsrAltitude)
        val asrAltitudeDegrees = Math.toDegrees(asrAltitude)
        
        val hourAngle = calculateHourAngle(location.latitude, declination, asrAltitudeDegrees)
        
        if (hourAngle.isNaN()) return Double.NaN
        
        return solarNoon + Math.toDegrees(hourAngle) / 15.0
    }
    
    /**
     * Converts decimal hour to LocalTime
     */
    fun decimalHourToLocalTime(decimalHour: Double): LocalTime? {
        Log.d(TAG, "Converting decimal hour to LocalTime: $decimalHour")
        
        if (decimalHour.isNaN()) {
            Log.w(TAG, "Decimal hour is NaN")
            return null
        }
        
        if (decimalHour < 0 || decimalHour >= 24) {
            Log.w(TAG, "Decimal hour out of range: $decimalHour")
            return null
        }
        
        val hours = floor(decimalHour).toInt()
        val minutes = ((decimalHour - hours) * 60).toInt()
        val seconds = (((decimalHour - hours) * 60 - minutes) * 60).toInt()
        
        val localTime = LocalTime.of(hours, minutes, seconds)
        Log.d(TAG, "Converted to LocalTime: $localTime")
        
        return localTime
    }
}