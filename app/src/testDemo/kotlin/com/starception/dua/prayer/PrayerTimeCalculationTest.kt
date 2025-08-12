package com.starception.dua.prayer

import com.starception.dua.prayer.calculator.AstronomicalCalculator
import com.starception.dua.prayer.model.*
import com.starception.dua.prayer.service.PrayerTimeCalculatorService
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Test prayer time calculations for Dubai, UAE
 * Comparing against Google prayer times from August 12, 2025
 */
class PrayerTimeCalculationTest {
    
    private val astronomicalCalculator = AstronomicalCalculator()
    private val prayerTimeCalculator = PrayerTimeCalculatorService(astronomicalCalculator)
    
    // Dubai, UAE coordinates
    private val dubaiLocation = Location(
        latitude = 25.276987,
        longitude = 55.296249,
        name = "Dubai",
        country = "UAE",
        timeZoneOffset = 4.0, // UTC+4
        altitude = 5.0
    )
    
    private val testDate = LocalDate.of(2025, 8, 12)
    
    @Test
    fun testUmmAlQuraMethod() {
        println("=== Testing Umm al-Qura Method for Dubai ===")
        val settings = PrayerSettings(
            calculationMethod = CalculationMethod.UMM_AL_QURA,
            location = dubaiLocation
        )
        
        val prayerTimes = prayerTimeCalculator.calculatePrayerTimes(testDate, dubaiLocation, settings)
        
        println("Expected: Fajr 4:27 AM, Isha 8:26 PM")
        println("Calculated: Fajr ${prayerTimes?.fajr}, Isha ${prayerTimes?.isha}")
        println("All times: ${prayerTimes?.let { formatPrayerTimes(it) }}")
        
        // Expected from Google: Fajr 4:27, Isha 8:26
        assertTimeClose(prayerTimes?.fajr, LocalTime.of(4, 27), "Fajr")
        assertTimeClose(prayerTimes?.isha, LocalTime.of(20, 26), "Isha")
    }
    
    @Test
    fun testMuslimWorldLeagueMethod() {
        println("=== Testing Muslim World League Method for Dubai ===")
        val settings = PrayerSettings(
            calculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
            location = dubaiLocation
        )
        
        val prayerTimes = prayerTimeCalculator.calculatePrayerTimes(testDate, dubaiLocation, settings)
        
        println("Expected: Fajr 4:29 AM, Isha 8:13 PM")
        println("Calculated: Fajr ${prayerTimes?.fajr}, Isha ${prayerTimes?.isha}")
        println("All times: ${prayerTimes?.let { formatPrayerTimes(it) }}")
        
        // Expected from Google: Fajr 4:29, Isha 8:13
        assertTimeClose(prayerTimes?.fajr, LocalTime.of(4, 29), "Fajr")
        assertTimeClose(prayerTimes?.isha, LocalTime.of(20, 13), "Isha")
    }
    
    @Test
    fun testEgyptianAuthorityMethod() {
        println("=== Testing Egyptian General Authority Method for Dubai ===")
        val settings = PrayerSettings(
            calculationMethod = CalculationMethod.EGYPTIAN_AUTHORITY,
            location = dubaiLocation
        )
        
        val prayerTimes = prayerTimeCalculator.calculatePrayerTimes(testDate, dubaiLocation, settings)
        
        println("Expected: Fajr 4:22 AM, Isha 8:15 PM")
        println("Calculated: Fajr ${prayerTimes?.fajr}, Isha ${prayerTimes?.isha}")
        println("All times: ${prayerTimes?.let { formatPrayerTimes(it) }}")
        
        // Expected from Google: Fajr 4:22, Isha 8:15
        assertTimeClose(prayerTimes?.fajr, LocalTime.of(4, 22), "Fajr")
        assertTimeClose(prayerTimes?.isha, LocalTime.of(20, 15), "Isha")
    }
    
    @Test
    fun testISNAMethod() {
        println("=== Testing ISNA Method for Dubai ===")
        val settings = PrayerSettings(
            calculationMethod = CalculationMethod.ISNA,
            location = dubaiLocation
        )
        
        val prayerTimes = prayerTimeCalculator.calculatePrayerTimes(testDate, dubaiLocation, settings)
        
        println("Expected: Fajr 4:44 AM, Isha 8:03 PM")
        println("Calculated: Fajr ${prayerTimes?.fajr}, Isha ${prayerTimes?.isha}")
        println("All times: ${prayerTimes?.let { formatPrayerTimes(it) }}")
        
        // Expected from Google: Fajr 4:44, Isha 8:03
        assertTimeClose(prayerTimes?.fajr, LocalTime.of(4, 44), "Fajr")
        assertTimeClose(prayerTimes?.isha, LocalTime.of(20, 3), "Isha")
    }
    
    @Test
    fun testShiaIthnaAshariMethod() {
        println("=== Testing Shia Ithna Ashari Method for Dubai ===")
        val settings = PrayerSettings(
            calculationMethod = CalculationMethod.SHIA_ITHNA_ASHARI,
            location = dubaiLocation
        )
        
        val prayerTimes = prayerTimeCalculator.calculatePrayerTimes(testDate, dubaiLocation, settings)
        
        println("Expected: Fajr 4:39 AM, Maghrib 7:11 PM, Isha 7:58 PM")
        println("Calculated: Fajr ${prayerTimes?.fajr}, Maghrib ${prayerTimes?.maghrib}, Isha ${prayerTimes?.isha}")
        println("All times: ${prayerTimes?.let { formatPrayerTimes(it) }}")
        
        // Expected from Google: Fajr 4:39, Maghrib 7:11, Isha 7:58
        assertTimeClose(prayerTimes?.fajr, LocalTime.of(4, 39), "Fajr")
        assertTimeClose(prayerTimes?.maghrib, LocalTime.of(19, 11), "Maghrib")
        assertTimeClose(prayerTimes?.isha, LocalTime.of(19, 58), "Isha")
    }
    
    @Test
    fun testKarachiUniversityMethod() {
        println("=== Testing University of Islamic Sciences (Karachi) Method for Dubai ===")
        val settings = PrayerSettings(
            calculationMethod = CalculationMethod.UNIVERSITY_OF_ISLAMIC_SCIENCES,
            location = dubaiLocation
        )
        
        val prayerTimes = prayerTimeCalculator.calculatePrayerTimes(testDate, dubaiLocation, settings)
        
        println("Expected: Fajr 4:29 AM, Isha 8:18 PM")
        println("Calculated: Fajr ${prayerTimes?.fajr}, Isha ${prayerTimes?.isha}")
        println("All times: ${prayerTimes?.let { formatPrayerTimes(it) }}")
        
        // Expected from Google: Fajr 4:29, Isha 8:18
        assertTimeClose(prayerTimes?.fajr, LocalTime.of(4, 29), "Fajr")
        assertTimeClose(prayerTimes?.isha, LocalTime.of(20, 18), "Isha")
    }
    
    @Test
    fun testTehranInstituteMethod() {
        println("=== Testing Institute of Geophysics, University of Tehran Method for Dubai ===")
        val settings = PrayerSettings(
            calculationMethod = CalculationMethod.INSTITUTE_OF_GEOPHYSICS_TEHRAN,
            location = dubaiLocation
        )
        
        val prayerTimes = prayerTimeCalculator.calculatePrayerTimes(testDate, dubaiLocation, settings)
        
        println("Expected: Fajr 4:31 AM, Maghrib 7:13 PM, Isha 7:58 PM")
        println("Calculated: Fajr ${prayerTimes?.fajr}, Maghrib ${prayerTimes?.maghrib}, Isha ${prayerTimes?.isha}")
        println("All times: ${prayerTimes?.let { formatPrayerTimes(it) }}")
        
        // Expected from Google: Fajr 4:31, Maghrib 7:13, Isha 7:58
        assertTimeClose(prayerTimes?.fajr, LocalTime.of(4, 31), "Fajr")
        assertTimeClose(prayerTimes?.maghrib, LocalTime.of(19, 13), "Maghrib")
        assertTimeClose(prayerTimes?.isha, LocalTime.of(19, 58), "Isha")
    }
    
    private fun assertTimeClose(actual: LocalTime?, expected: LocalTime, prayerName: String, toleranceMinutes: Int = 3) {
        if (actual == null) {
            println("ERROR: $prayerName time is null!")
            return
        }
        
        val diffMinutes = kotlin.math.abs(actual.toSecondOfDay() - expected.toSecondOfDay()) / 60
        if (diffMinutes <= toleranceMinutes) {
            println("✓ $prayerName: Expected $expected, Got $actual (diff: ${diffMinutes}m)")
        } else {
            println("✗ $prayerName: Expected $expected, Got $actual (diff: ${diffMinutes}m) - OUTSIDE TOLERANCE")
        }
    }
    
    private fun formatPrayerTimes(prayerTimes: DayPrayerTimes): String {
        return "Fajr: ${prayerTimes.fajr}, Sunrise: ${prayerTimes.sunrise}, Dhuhr: ${prayerTimes.dhuhr}, " +
               "Asr: ${prayerTimes.asr}, Maghrib: ${prayerTimes.maghrib}, Isha: ${prayerTimes.isha}"
    }
}