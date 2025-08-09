package com.starception.dua.prayer.calculator

import com.starception.dua.prayer.model.Location
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
        private const val JULIAN_EPOCH = 1721425.5
        private const val EARTH_RADIUS_KM = 6371.0
    }
    
    /**
     * Calculates Julian Day from given date
     */
    fun calculateJulianDay(date: LocalDate, time: LocalTime = LocalTime.MIDNIGHT): Double {
        val year = date.year
        val month = date.monthValue
        val day = date.dayOfMonth
        val hour = time.hour.toDouble()
        val minute = time.minute.toDouble()
        val second = time.second.toDouble()
        
        val ut = hour + minute / 60.0 + second / 3600.0
        
        return 367 * year - floor((7 * (year + floor((month + 9) / 12.0))) / 4.0) +
                floor((275 * month) / 9.0) + day + 1721013.5 + ut / 24.0
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
        val eot = Math.toDegrees(l / 15.0 - ra * 180.0 / PI / 15.0) * 60.0
        
        return eot
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
        val sunriseAltitude = -0.833 - 0.0347 * sqrt(location.altitude)
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
        val sunsetAltitude = -0.833 - 0.0347 * sqrt(location.altitude)
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
        val shadowAngle = atan(1.0 / (shadowFactor + tan(abs(latRad - declination))))
        val hourAngle = calculateHourAngle(location.latitude, declination, Math.toDegrees(shadowAngle))
        
        if (hourAngle.isNaN()) return Double.NaN
        
        return solarNoon + Math.toDegrees(hourAngle) / 15.0
    }
    
    /**
     * Converts decimal hour to LocalTime
     */
    fun decimalHourToLocalTime(decimalHour: Double): LocalTime? {
        if (decimalHour.isNaN() || decimalHour < 0 || decimalHour >= 24) return null
        
        val hours = floor(decimalHour).toInt()
        val minutes = ((decimalHour - hours) * 60).toInt()
        val seconds = (((decimalHour - hours) * 60 - minutes) * 60).toInt()
        
        return LocalTime.of(hours, minutes, seconds)
    }
}