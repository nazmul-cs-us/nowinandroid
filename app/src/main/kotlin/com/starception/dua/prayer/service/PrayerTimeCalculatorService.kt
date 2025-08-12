package com.starception.dua.prayer.service

import android.util.Log
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
    companion object {
        private const val TAG = "PrayerTimeCalculator"
    }
    
    /**
     * Calculates prayer times for a specific date and location
     */
    fun calculatePrayerTimes(
        date: LocalDate,
        location: Location,
        settings: PrayerSettings
    ): DayPrayerTimes? {
        Log.d(TAG, "Calculating prayer times for ${location.getDisplayName()}")
        Log.d(TAG, "Location: lat=${location.latitude}, lng=${location.longitude}, tz=${location.timeZoneOffset}")
        Log.d(TAG, "Date: $date")
        
        if (!location.isValid()) {
            Log.e(TAG, "Invalid location coordinates: lat=${location.latitude}, lng=${location.longitude}")
            return null
        }
        
        val julianDay = astronomicalCalculator.calculateJulianDay(date)
        Log.d(TAG, "Julian day: $julianDay")
        
        // Calculate basic times
        val solarNoon = astronomicalCalculator.calculateSolarNoon(location, julianDay)
        val sunrise = astronomicalCalculator.calculateSunrise(location, julianDay)
        val sunset = astronomicalCalculator.calculateSunset(location, julianDay)
        
        Log.d(TAG, "Solar noon: $solarNoon")
        Log.d(TAG, "Sunrise: $sunrise")
        Log.d(TAG, "Sunset: $sunset")
        
        // Calculate prayer-specific times
        val fajrTime = calculateFajrWithAdjustments(location, julianDay, settings)
        val asrTime = calculateAsrWithAdjustments(location, julianDay, settings)
        val ishaTime = calculateIshaWithAdjustments(location, julianDay, settings, sunset)
        
        Log.d(TAG, "Calculated prayer times:")
        Log.d(TAG, "  Fajr: $fajrTime")
        Log.d(TAG, "  Asr: $asrTime") 
        Log.d(TAG, "  Isha: $ishaTime")
        
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
            addMinutesToTime(
                astronomicalCalculator.decimalHourToLocalTime(sunset), 
                settings.calculationMethod.maghribOffset
            ),
            offsets.maghrib
        )
        val isha = addMinutesToTime(ishaTime, offsets.isha)
        
        Log.d(TAG, "Final prayer times after adjustments:")
        Log.d(TAG, "  Fajr: $fajr")
        Log.d(TAG, "  Sunrise: $sunriseAdjusted") 
        Log.d(TAG, "  Dhuhr: $dhuhr")
        Log.d(TAG, "  Asr: $asr")
        Log.d(TAG, "  Maghrib: $maghrib")
        Log.d(TAG, "  Isha: $isha")
        
        // Validate all times are calculated
        if (fajr == null || sunriseAdjusted == null || dhuhr == null || 
            asr == null || maghrib == null || isha == null) {
            Log.e(TAG, "Some prayer times are null - calculation failed")
            Log.e(TAG, "Null times: fajr=${fajr == null}, sunrise=${sunriseAdjusted == null}, dhuhr=${dhuhr == null}, asr=${asr == null}, maghrib=${maghrib == null}, isha=${isha == null}")
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
        Log.d(TAG, "Calculating Fajr with angle: $fajrAngle")
        
        val fajrDecimal = astronomicalCalculator.calculateFajr(location, julianDay, fajrAngle)
        Log.d(TAG, "Fajr decimal hour: $fajrDecimal")
        
        if (!fajrDecimal.isNaN()) {
            val fajrTime = astronomicalCalculator.decimalHourToLocalTime(fajrDecimal)
            Log.d(TAG, "Fajr time: $fajrTime")
            return fajrTime
        }
        
        Log.w(TAG, "Fajr calculation returned NaN, applying high latitude adjustment")
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
        Log.d(TAG, "Calculating Asr with shadow factor: $shadowFactor")
        
        val asrDecimal = astronomicalCalculator.calculateAsr(location, julianDay, shadowFactor)
        Log.d(TAG, "Asr decimal hour: $asrDecimal")
        
        val asrTime = astronomicalCalculator.decimalHourToLocalTime(asrDecimal)
        Log.d(TAG, "Asr time: $asrTime")
        
        return asrTime
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
        Log.d(TAG, "Calculating Isha with angle: $ishaAngle, delay: $ishaDelay")
        
        val ishaDecimal = astronomicalCalculator.calculateIsha(location, julianDay, ishaAngle, ishaDelay)
        Log.d(TAG, "Isha decimal hour: $ishaDecimal")
        
        if (!ishaDecimal.isNaN()) {
            val ishaTime = astronomicalCalculator.decimalHourToLocalTime(ishaDecimal)
            Log.d(TAG, "Isha time: $ishaTime")
            return ishaTime
        }
        
        Log.w(TAG, "Isha calculation returned NaN, applying high latitude adjustment")
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
     * Gets time remaining until next prayer, or time since last prayer if all prayers have passed
     */
    fun getTimeUntilNextPrayer(prayerTimes: DayPrayerTimes): String? {
        val now = LocalTime.now()
        val nextPrayer = getNextPrayer(prayerTimes)
        
        if (nextPrayer != null) {
            // There's still a prayer remaining today
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
        } else {
            // All prayers have passed, show time since last prayer (Isha)
            val ishaTime = prayerTimes.isha
            if (now.isAfter(ishaTime)) {
                val minutesSince = java.time.Duration.between(ishaTime, now).toMinutes()
                val hours = minutesSince / 60
                val minutes = minutesSince % 60
                
                return when {
                    hours > 0 -> "${hours}h ${minutes}m ago"
                    minutes > 0 -> "${minutes}m ago"
                    else -> "just now"
                }
            }
            return null
        }
    }
}