package com.starception.dua.prayer.service

import com.starception.dua.prayer.calculator.AstronomicalCalculator
import com.starception.dua.prayer.model.*
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main service for calculating Islamic prayer times
 */
@Singleton
class PrayerTimeCalculatorService @Inject constructor(
    private val astronomicalCalculator: AstronomicalCalculator
) {
    
    /**
     * Calculates prayer times for a specific date and location
     */
    fun calculatePrayerTimes(
        date: LocalDate,
        location: Location,
        settings: PrayerSettings
    ): DayPrayerTimes? {
        if (!location.isValid()) return null
        
        val julianDay = astronomicalCalculator.calculateJulianDay(date)
        
        // Calculate basic times
        val solarNoon = astronomicalCalculator.calculateSolarNoon(location, julianDay)
        val sunrise = astronomicalCalculator.calculateSunrise(location, julianDay)
        val sunset = astronomicalCalculator.calculateSunset(location, julianDay)
        
        // Calculate prayer-specific times
        val fajrTime = calculateFajrWithAdjustments(location, julianDay, settings)
        val asrTime = calculateAsrWithAdjustments(location, julianDay, settings)
        val ishaTime = calculateIshaWithAdjustments(location, julianDay, settings, sunset)
        
        // Apply user offsets
        val offsets = settings.timeOffsets
        
        val fajr = addMinutesToTime(fajrTime, offsets.fajr)
        val sunriseAdjusted = addMinutesToTime(
            astronomicalCalculator.decimalHourToLocalTime(sunrise), 
            offsets.sunrise
        )
        val dhuhr = addMinutesToTime(
            astronomicalCalculator.decimalHourToLocalTime(solarNoon), 
            offsets.dhuhr
        )
        val asr = addMinutesToTime(asrTime, offsets.asr)
        val maghrib = addMinutesToTime(
            astronomicalCalculator.decimalHourToLocalTime(sunset), 
            offsets.maghrib
        )
        val isha = addMinutesToTime(ishaTime, offsets.isha)
        
        // Validate all times are calculated
        if (fajr == null || sunriseAdjusted == null || dhuhr == null || 
            asr == null || maghrib == null || isha == null) {
            return null
        }
        
        return DayPrayerTimes(
            date = date.atStartOfDay(),
            fajr = fajr,
            sunrise = sunriseAdjusted,
            dhuhr = dhuhr,
            asr = asr,
            maghrib = maghrib,
            isha = isha,
            location = location
        )
    }
    
    /**
     * Calculates Fajr time with high-latitude adjustments
     */
    private fun calculateFajrWithAdjustments(
        location: Location,
        julianDay: Double,
        settings: PrayerSettings
    ): LocalTime? {
        val fajrAngle = settings.getEffectiveFajrAngle()
        val fajrDecimal = astronomicalCalculator.calculateFajr(location, julianDay, fajrAngle)
        
        if (!fajrDecimal.isNaN()) {
            return astronomicalCalculator.decimalHourToLocalTime(fajrDecimal)
        }
        
        // Apply high latitude adjustment
        return applyHighLatitudeAdjustment(
            location, julianDay, settings, "fajr", fajrAngle
        )
    }
    
    /**
     * Calculates Asr time with madhhab considerations
     */
    private fun calculateAsrWithAdjustments(
        location: Location,
        julianDay: Double,
        settings: PrayerSettings
    ): LocalTime? {
        val shadowFactor = settings.asrMadhhab.shadowFactor
        val asrDecimal = astronomicalCalculator.calculateAsr(location, julianDay, shadowFactor)
        
        return astronomicalCalculator.decimalHourToLocalTime(asrDecimal)
    }
    
    /**
     * Calculates Isha time with high-latitude adjustments
     */
    private fun calculateIshaWithAdjustments(
        location: Location,
        julianDay: Double,
        settings: PrayerSettings,
        sunset: Double
    ): LocalTime? {
        val ishaAngle = settings.getEffectiveIshaAngle()
        val ishaDelay = settings.getEffectiveIshaDelay()
        
        val ishaDecimal = astronomicalCalculator.calculateIsha(location, julianDay, ishaAngle, ishaDelay)
        
        if (!ishaDecimal.isNaN()) {
            return astronomicalCalculator.decimalHourToLocalTime(ishaDecimal)
        }
        
        // Apply high latitude adjustment
        return applyHighLatitudeAdjustment(
            location, julianDay, settings, "isha", ishaAngle ?: 0.0
        )
    }
    
    /**
     * Applies high latitude adjustments when normal calculation fails
     */
    private fun applyHighLatitudeAdjustment(
        location: Location,
        julianDay: Double,
        settings: PrayerSettings,
        prayer: String,
        angle: Double
    ): LocalTime? {
        val sunrise = astronomicalCalculator.calculateSunrise(location, julianDay)
        val sunset = astronomicalCalculator.calculateSunset(location, julianDay)
        
        if (sunrise.isNaN() || sunset.isNaN()) return null
        
        return when (settings.highLatitudeAdjustment) {
            HighLatitudeAdjustment.MIDDLE_OF_NIGHT -> {
                val midNight = if (sunset < sunrise) {
                    (sunset + sunrise + 24) / 2.0 % 24
                } else {
                    (sunset + sunrise) / 2.0
                }
                
                val time = if (prayer == "fajr") {
                    midNight - (midNight - sunrise) / 2.0
                } else {
                    midNight + (sunset - midNight) / 2.0
                }
                
                astronomicalCalculator.decimalHourToLocalTime(time)
            }
            
            HighLatitudeAdjustment.ONE_SEVENTH_OF_NIGHT -> {
                val nightDuration = if (sunset < sunrise) {
                    24 - sunrise + sunset
                } else {
                    sunset - sunrise
                }
                
                val oneSeventhOfNight = nightDuration / 7.0
                
                val time = if (prayer == "fajr") {
                    sunrise - oneSeventhOfNight
                } else {
                    sunset + oneSeventhOfNight
                }
                
                astronomicalCalculator.decimalHourToLocalTime(time.let { if (it < 0) it + 24 else it % 24 })
            }
            
            HighLatitudeAdjustment.ANGLE_BASED -> {
                // Use a fixed portion of the night based on the angle
                val nightDuration = if (sunset < sunrise) {
                    24 - sunrise + sunset
                } else {
                    sunset - sunrise
                }
                
                val portion = angle / 60.0 // Rough approximation
                val timeOffset = nightDuration * portion
                
                val time = if (prayer == "fajr") {
                    sunrise - timeOffset
                } else {
                    sunset + timeOffset
                }
                
                astronomicalCalculator.decimalHourToLocalTime(time.let { if (it < 0) it + 24 else it % 24 })
            }
            
            else -> null
        }
    }
    
    /**
     * Adds minutes to a LocalTime
     */
    private fun addMinutesToTime(time: LocalTime?, minutes: Int): LocalTime? {
        return time?.plusMinutes(minutes.toLong())
    }
    
    /**
     * Gets the next prayer time from current time
     */
    fun getNextPrayer(prayerTimes: DayPrayerTimes): PrayerTime? {
        return prayerTimes.getNextPrayer()
    }
    
    /**
     * Gets time remaining until next prayer
     */
    fun getTimeUntilNextPrayer(prayerTimes: DayPrayerTimes): String? {
        val nextPrayer = getNextPrayer(prayerTimes) ?: return null
        val now = LocalTime.now()
        
        val minutesUntil = if (nextPrayer.time.isAfter(now)) {
            java.time.Duration.between(now, nextPrayer.time).toMinutes()
        } else {
            // Next prayer is tomorrow
            java.time.Duration.between(now, nextPrayer.time.plusHours(24)).toMinutes()
        }
        
        val hours = minutesUntil / 60
        val minutes = minutesUntil % 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "Now"
        }
    }
}