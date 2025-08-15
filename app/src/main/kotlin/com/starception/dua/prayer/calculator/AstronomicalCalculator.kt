package com.starception.submission.prayer.calculator

import android.util.Log
import com.starception.submission.prayer.model.Location
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Astronomical calculations for prayer times based on sun position
 */
@Singleton
class AstronomicalCalculator @Inject constructor() {
    
    companion object {
        private const val TAG = "AstronomicalCalculator"
        private const val JULIAN_EPOCH = 1721425.5
        private const val EARTH_RADIUS_KM = 6371.0
    }
    
    /**
     * Calculates Julian Day from given date
     */
    fun calculateJulianDay(date: LocalDate, time: LocalTime = LocalTime.MIDNIGHT): Double {
        var year = date.year
        var month = date.monthValue
        val day = date.dayOfMonth
        val hour = time.hour.toDouble()
        val minute = time.minute.toDouble()
        val second = time.second.toDouble()
        
        // Adjust for January and February being months 13 and 14 of the previous year
        if (month <= 2) {
            year -= 1
            month += 12
        }
        
        val ut = hour + minute / 60.0 + second / 3600.0
        
        // More precise Julian Day calculation
        val a = floor(year / 100.0)
        val b = 2 - a + floor(a / 4.0) // Gregorian calendar correction
        
        return floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + 
               day + b - 1524.5 + ut / 24.0
    }
    
    /**
     * Calculates solar declination for given Julian Day
     */
    fun calculateSolarDeclination(julianDay: Double): Double {
        val n = julianDay - 2451545.0
        val l = (280.46 + 0.9856474 * n) % 360
        val g = Math.toRadians(357.528 + 0.9856003 * n)
        val lambdaSun = Math.toRadians(l + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val epsilon = Math.toRadians(23.439 - 0.0000004 * n)
        
        return asin(sin(epsilon) * sin(lambdaSun))
    }
    
    /**
     * Calculates equation of time for given Julian Day
     */
    fun calculateEquationOfTime(julianDay: Double): Double {
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
        
        return eot * 4.0 // Convert to minutes
    }
    
    /**
     * Calculates solar noon time
     */
    fun calculateSolarNoon(location: Location, julianDay: Double): Double {
        val eot = calculateEquationOfTime(julianDay)
        return 12.0 + location.timeZoneOffset - location.longitude / 15.0 - eot / 60.0
    }
    
    /**
     * Calculates hour angle for given altitude
     */
    fun calculateHourAngle(
        latitude: Double,
        declination: Double,
        altitude: Double
    ): Double {
        val latRad = Math.toRadians(latitude)
        val altRad = Math.toRadians(altitude)
        
        val cosH = (sin(altRad) - sin(latRad) * sin(declination)) / 
                   (cos(latRad) * cos(declination))
        
        // Check if sun reaches the required altitude
        if (cosH < -1.0 || cosH > 1.0) {
            return Double.NaN // Sun doesn't reach this altitude
        }
        
        return acos(cosH)
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