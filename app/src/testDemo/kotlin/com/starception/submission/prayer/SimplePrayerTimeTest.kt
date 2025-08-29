package com.starception.submission.prayer

import com.starception.submission.prayer.calculator.AstronomicalCalculator
import com.starception.submission.prayer.model.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Simple test of prayer time calculations for Dubai, UAE
 */
class SimplePrayerTimeTest {
    
    @Test
    fun testDubaiPrayerTimeCalculations() {
        val calculator = AstronomicalCalculator()
        
        // Dubai coordinates
        val latitude = 25.276987
        val longitude = 55.296249
        val timezone = 4.0 // UTC+4
        
        val location = Location(
            latitude = latitude,
            longitude = longitude,
            name = "Dubai",
            country = "UAE", 
            timeZoneOffset = timezone,
            altitude = 5.0
        )
        
        val date = LocalDate.of(2025, 8, 12)
        val julianDay = calculator.calculateJulianDay(date)
        
        println("=== Dubai Prayer Time Test - August 12, 2025 ===")
        println("Coordinates: $latitude, $longitude")
        println("Julian Day: $julianDay")
        println()
        
        // Test basic solar calculations
        val solarNoon = calculator.calculateSolarNoon(location, julianDay)
        val sunrise = calculator.calculateSunrise(location, julianDay)
        val sunset = calculator.calculateSunset(location, julianDay)
        
        println("Solar Calculations:")
        println("Solar Noon: ${formatDecimalHour(solarNoon)}")
        println("Sunrise: ${formatDecimalHour(sunrise)}")
        println("Sunset: ${formatDecimalHour(sunset)}")
        println()
        
        // Test different calculation methods
        testMethod("Umm al-Qura", calculator, location, julianDay, 18.5, null, 90, "4:27", "8:26")
        testMethod("Muslim World League", calculator, location, julianDay, 18.0, 17.0, null, "4:29", "8:13")
        testMethod("Egyptian Authority", calculator, location, julianDay, 19.5, 17.5, null, "4:22", "8:15")
        testMethod("ISNA", calculator, location, julianDay, 15.0, 15.0, null, "4:44", "8:03")
        testMethod("Karachi University", calculator, location, julianDay, 18.0, 18.0, null, "4:29", "8:18")
        testMethod("Shia Ithna Ashari", calculator, location, julianDay, 16.0, 14.0, null, "4:39", "7:58")
        testMethod("Tehran Institute", calculator, location, julianDay, 17.7, 14.0, null, "4:31", "7:58")
    }
    
    private fun testMethod(
        name: String,
        calculator: AstronomicalCalculator,
        location: Location,
        julianDay: Double,
        fajrAngle: Double,
        ishaAngle: Double?,
        ishaDelay: Int?,
        expectedFajr: String,
        expectedIsha: String
    ) {
        println("=== $name Method ===")
        
        // Calculate Fajr
        val fajrDecimal = calculator.calculateFajr(location, julianDay, fajrAngle)
        val fajrTime = calculator.decimalHourToLocalTime(fajrDecimal)
        
        // Calculate Isha
        val ishaDecimal = calculator.calculateIsha(location, julianDay, ishaAngle, ishaDelay)
        val ishaTime = calculator.decimalHourToLocalTime(ishaDecimal)
        
        println("Fajr: $fajrTime (expected: $expectedFajr)")
        println("Isha: $ishaTime (expected: $expectedIsha)")
        
        // Check if they're close to expected
        val fajrMatch = isTimeClose(fajrTime, parseTime(expectedFajr))
        val ishaMatch = isTimeClose(ishaTime, parseTime(expectedIsha))
        
        println("Fajr match: ${if (fajrMatch) "✓" else "✗"}")
        println("Isha match: ${if (ishaMatch) "✓" else "✗"}")
        println()
    }
    
    private fun formatDecimalHour(hour: Double): String {
        if (hour.isNaN()) return "NaN"
        
        val h = hour.toInt()
        val m = ((hour - h) * 60).toInt()
        return String.format("%02d:%02d", h, m)
    }
    
    private fun parseTime(timeStr: String): LocalTime {
        val parts = timeStr.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        return LocalTime.of(hour, minute)
    }
    
    private fun isTimeClose(actual: LocalTime?, expected: LocalTime, toleranceMinutes: Int = 5): Boolean {
        if (actual == null) return false
        
        val diffSeconds = kotlin.math.abs(actual.toSecondOfDay() - expected.toSecondOfDay())
        return diffSeconds <= toleranceMinutes * 60
    }
}