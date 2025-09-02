package com.starception.submission.prayer.service

import android.util.Log
import com.starception.submission.prayer.calculator.AstronomicalCalculator
import com.starception.submission.prayer.model.*
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
     * MAIN PRAYER TIMES CALCULATION FLOW
     * 
     * This is the core method that orchestrates the complete Islamic prayer time calculation process.
     * The calculation follows these key steps:
     * 
     * 1. INPUT VALIDATION: Verify location coordinates are valid
     * 2. ASTRONOMICAL FOUNDATION: Convert date to Julian Day for precise astronomical calculations
     * 3. SOLAR POSITION CALCULATION: Calculate fundamental sun positions (noon, sunrise, sunset)
     * 4. PRAYER-SPECIFIC CALCULATIONS: Apply Islamic prayer time rules for Fajr, Asr, and Isha
     * 5. USER CUSTOMIZATION: Apply user-defined time offsets and calculation method preferences
     * 6. FINAL ASSEMBLY: Combine all calculated times into a complete day's prayer schedule
     * 
     * @param date The date to calculate prayer times for
     * @param location Geographic coordinates and timezone information
     * @param settings User preferences including calculation method, school of thought, and time offsets
     * @return Complete prayer times for the day, or null if calculation fails
     */
    fun calculatePrayerTimes(
        date: LocalDate,
        location: Location,
        settings: PrayerSettings
    ): DayPrayerTimes? {
        Log.d(TAG, "=== STARTING PRAYER TIMES CALCULATION ===")
        Log.d(TAG, "Location: ${location.getDisplayName()}")
        Log.d(TAG, "Coordinates: lat=${location.latitude}, lng=${location.longitude}, tz=${location.timeZoneOffset}")
        Log.d(TAG, "Date: $date")
        Log.d(TAG, "Calculation Method: ${settings.calculationMethod}")
        
        // STEP 1: INPUT VALIDATION
        // Verify that the provided coordinates are within valid geographic ranges
        // Latitude: -90° to +90°, Longitude: -180° to +180°
        if (!location.isValid()) {
            Log.e(TAG, "❌ VALIDATION FAILED: Invalid location coordinates")
            return null
        }
        
        // STEP 2: ASTRONOMICAL FOUNDATION
        // Convert the Gregorian date to Julian Day Number for precise astronomical calculations
        // Julian Day provides a continuous count of days since the beginning of the Julian Period
        val julianDay = astronomicalCalculator.calculateJulianDay(date)
        Log.d(TAG, "✓ Julian day calculated: $julianDay")
        
        // STEP 3: FUNDAMENTAL SOLAR CALCULATIONS
        // Calculate the three key solar positions that form the foundation for all prayer times:
        // - Solar Noon: When the sun reaches its highest point (Dhuhr prayer time)
        // - Sunrise: Dawn transition, used for Fajr calculations and as Sunrise prayer time
        // - Sunset: Dusk transition, immediate Maghrib time and basis for Isha calculations
        val solarNoon = astronomicalCalculator.calculateSolarNoon(location, julianDay)
        val sunrise = astronomicalCalculator.calculateSunrise(location, julianDay)
        val sunset = astronomicalCalculator.calculateSunset(location, julianDay)
        
        Log.d(TAG, "✓ Solar positions calculated:")
        Log.d(TAG, "  Solar Noon: $solarNoon (Dhuhr base time)")
        Log.d(TAG, "  Sunrise: $sunrise (Dawn transition)")
        Log.d(TAG, "  Sunset: $sunset (Maghrib time)")
        
        // STEP 4: ISLAMIC PRAYER-SPECIFIC CALCULATIONS
        // Apply Islamic astronomical rules for the three prayers that require complex calculations:
        // - Fajr: Pre-dawn prayer based on sun's angle below horizon (typically -15° to -19.5°)
        // - Asr: Afternoon prayer based on shadow length relative to object height
        // - Isha: Night prayer based on sun's angle below horizon or fixed time after sunset
        val fajrTime = calculateFajrWithAdjustments(location, julianDay, settings)
        val asrTime = calculateAsrWithAdjustments(location, julianDay, settings)
        val ishaTime = calculateIshaWithAdjustments(location, julianDay, settings, sunset)
        
        Log.d(TAG, "✓ Islamic prayer calculations completed:")
        Log.d(TAG, "  Fajr (Pre-dawn): $fajrTime")
        Log.d(TAG, "  Asr (Afternoon): $asrTime") 
        Log.d(TAG, "  Isha (Night): $ishaTime")
        
        // STEP 5: USER CUSTOMIZATION APPLICATION
        // Apply user-defined time adjustments to accommodate local customs,
        // mosque schedules, or personal preferences
        val offsets = settings.timeOffsets
        Log.d(TAG, "✓ Applying user time offsets: $offsets")
        
        // STEP 6: FINAL PRAYER TIME ASSEMBLY
        // Apply individual user time offsets to each prayer time.
        // Each prayer can be adjusted independently to match local mosque times or personal preference.
        
        // FAJR: Apply user offset to the calculated pre-dawn time
        val fajr = addMinutesToTime(fajrTime, offsets.fajr)
        
        // SUNRISE: Convert astronomical sunrise to LocalTime and apply user offset
        // Note: Sunrise is for reference/sunnah prayers, not one of the 5 obligatory prayers
        val sunriseAdjusted = addMinutesToTime(
            astronomicalCalculator.decimalHourToLocalTime(sunrise), 
            offsets.sunrise
        )
        
        // DHUHR: Use solar noon as base time and apply user offset
        // This is when the sun reaches its zenith (highest point in the sky)
        val dhuhr = addMinutesToTime(
            astronomicalCalculator.decimalHourToLocalTime(solarNoon), 
            offsets.dhuhr
        )
        
        // ASR: Apply user offset to the calculated afternoon shadow-based time
        val asr = addMinutesToTime(asrTime, offsets.asr)
        
        // MAGHRIB: Apply both calculation method offset AND user offset
        // First apply the method-specific offset (e.g., +3 minutes for some methods)
        // Then apply the user's personal adjustment
        val maghrib = addMinutesToTime(
            addMinutesToTime(
                astronomicalCalculator.decimalHourToLocalTime(sunset), 
                settings.calculationMethod.maghribOffset  // Method-specific adjustment
            ),
            offsets.maghrib  // User personal adjustment
        )
        
        // ISHA: Apply user offset to the calculated night time
        val isha = addMinutesToTime(ishaTime, offsets.isha)
        
        Log.d(TAG, "✓ STEP 6: Final prayer times after all adjustments:")
        Log.d(TAG, "  Fajr: $fajr (offset: ${offsets.fajr}min)")
        Log.d(TAG, "  Sunrise: $sunriseAdjusted (offset: ${offsets.sunrise}min)") 
        Log.d(TAG, "  Dhuhr: $dhuhr (offset: ${offsets.dhuhr}min)")
        Log.d(TAG, "  Asr: $asr (offset: ${offsets.asr}min)")
        Log.d(TAG, "  Maghrib: $maghrib (method: ${settings.calculationMethod.maghribOffset}min + user: ${offsets.maghrib}min)")
        Log.d(TAG, "  Isha: $isha (offset: ${offsets.isha}min)")
        
        // STEP 7: FINAL VALIDATION
        // Ensure all prayer times were successfully calculated before returning results
        // If any calculation failed (returned null), the entire calculation is considered failed
        if (fajr == null || sunriseAdjusted == null || dhuhr == null || 
            asr == null || maghrib == null || isha == null) {
            Log.e(TAG, "❌ CALCULATION FAILED: Some prayer times are null")
            Log.e(TAG, "Failed times: fajr=${fajr == null}, sunrise=${sunriseAdjusted == null}, dhuhr=${dhuhr == null}, asr=${asr == null}, maghrib=${maghrib == null}, isha=${isha == null}")
            return null
        }
        
        Log.d(TAG, "✅ PRAYER TIMES CALCULATION COMPLETED SUCCESSFULLY")
        
        // STEP 8: RETURN COMPLETE PRAYER SCHEDULE
        // Package all calculated times into a comprehensive day's prayer schedule
        return DayPrayerTimes(
            date = date.atStartOfDay(),  // Include the date these times are calculated for
            fajr = fajr,                 // Pre-dawn prayer
            sunrise = sunriseAdjusted,   // Sunrise time (for reference)
            dhuhr = dhuhr,               // Noon prayer
            asr = asr,                   // Afternoon prayer
            maghrib = maghrib,           // Sunset prayer
            isha = isha,                 // Night prayer
            location = location          // Location context for these calculations
        )
    }
    
    /**
     * FAJR PRAYER TIME CALCULATION WITH HIGH-LATITUDE ADJUSTMENTS
     * 
     * Fajr is the pre-dawn prayer that occurs when the sun is at a specific angle below the horizon.
     * This calculation is complex because:
     * 
     * 1. ANGLE-BASED CALCULATION: Uses the sun's depression angle below the horizon
     *    - Different Islamic organizations use different angles (15°, 18°, 19.5°, etc.)
     *    - The angle determines when "true dawn" (Fajr Sadiq) begins
     * 
     * 2. HIGH-LATITUDE CHALLENGES: In extreme latitudes (>48°), normal calculations may fail
     *    - During certain times of year, the sun may never reach the required angle
     *    - Special adjustment methods are needed for these locations
     * 
     * 3. SEASONAL VARIATIONS: The time varies significantly throughout the year
     *    - Summer: Fajr occurs very early (dawn comes early)
     *    - Winter: Fajr occurs later (dawn comes later)
     * 
     * @param location Geographic coordinates and timezone
     * @param julianDay Julian day number for the calculation date
     * @param settings User preferences including calculation method and angle
     * @return LocalTime for Fajr prayer, or null if calculation impossible
     */
    private fun calculateFajrWithAdjustments(
        location: Location,
        julianDay: Double,
        settings: PrayerSettings
    ): LocalTime? {
        // Get the depression angle for Fajr based on user's selected calculation method
        // Common angles: -15° (Egypt), -18° (Makkah), -19.5° (Karachi), etc.
        val fajrAngle = settings.getEffectiveFajrAngle()
        Log.d(TAG, "⏰ FAJR CALCULATION: Using depression angle: $fajrAngle°")
        Log.d(TAG, "  Method: ${settings.calculationMethod}")
        
        // Attempt standard astronomical calculation
        // This calculates when the sun reaches the specified angle below the horizon
        val fajrDecimal = astronomicalCalculator.calculateFajr(location, julianDay, fajrAngle)
        Log.d(TAG, "  Astronomical result: $fajrDecimal (decimal hour)")
        
        // Check if standard calculation succeeded
        if (!fajrDecimal.isNaN()) {
            val fajrTime = astronomicalCalculator.decimalHourToLocalTime(fajrDecimal)
            Log.d(TAG, "✅ FAJR TIME: $fajrTime (standard calculation)")
            return fajrTime
        }
        
        // Standard calculation failed - likely due to high latitude location
        // Apply special adjustment methods for extreme latitudes
        Log.w(TAG, "⚠️ FAJR: Standard calculation failed (NaN result)")
        Log.w(TAG, "  Applying high latitude adjustment for lat=${location.latitude}")
        Log.w(TAG, "  Adjustment method: ${settings.highLatitudeAdjustment}")
        
        return applyHighLatitudeAdjustment(
            location, julianDay, settings, "fajr", fajrAngle
        )
    }
    
    /**
     * ASR PRAYER TIME CALCULATION WITH MADHHAB (SCHOOL OF THOUGHT) CONSIDERATIONS
     * 
     * Asr is the afternoon prayer calculated based on shadow length relative to object height.
     * This calculation has unique characteristics:
     * 
     * 1. SHADOW-BASED CALCULATION: Unlike other prayers, Asr uses shadow geometry
     *    - Measures when an object's shadow reaches a specific length relative to the object
     *    - Based on the physical observation method used historically by Islamic scholars
     * 
     * 2. MADHHAB DIFFERENCES: Different schools of Islamic jurisprudence use different shadow factors
     *    - HANAFI: Shadow length = Object height + Original shadow (factor ≈ 2.0)
     *    - SHAFI/MALIKI/HANBALI: Shadow length = Object height + Original shadow (factor ≈ 1.0)
     *    - This creates about 15-30 minutes difference in prayer time
     * 
     * 3. GEOMETRIC PRECISION: Calculation involves:
     *    - Sun's declination angle for the date
     *    - Location's latitude
     *    - Trigonometric calculations for shadow angles
     *    - Conversion to clock time
     * 
     * 4. RELIABILITY: Generally very reliable calculation (rarely fails) as it's based
     *    on sun elevation angles that are always achievable in inhabited areas
     * 
     * @param location Geographic coordinates and timezone
     * @param julianDay Julian day number for the calculation date  
     * @param settings User preferences including madhhab selection
     * @return LocalTime for Asr prayer (very rarely null)
     */
    private fun calculateAsrWithAdjustments(
        location: Location,
        julianDay: Double,
        settings: PrayerSettings
    ): LocalTime? {
        // Get shadow factor based on user's selected madhhab (school of thought)
        // Hanafi: 2.0 (later time), Shafi/Maliki/Hanbali: 1.0 (earlier time)
        val shadowFactor = settings.asrMadhhab.shadowFactor
        Log.d(TAG, "⏰ ASR CALCULATION: Using shadow factor: $shadowFactor")
        Log.d(TAG, "  Madhhab: ${settings.asrMadhhab} (${if (shadowFactor == 2.0) "later" else "earlier"} Asr)")
        
        // Calculate when object's shadow equals (shadowFactor × object height + original noon shadow)
        // This involves complex trigonometry with sun's position and geographic location
        val asrDecimal = astronomicalCalculator.calculateAsr(location, julianDay, shadowFactor)
        Log.d(TAG, "  Astronomical result: $asrDecimal (decimal hour)")
        
        // Convert decimal hour to clock time
        val asrTime = astronomicalCalculator.decimalHourToLocalTime(asrDecimal)
        Log.d(TAG, "✅ ASR TIME: $asrTime (shadow-based calculation)")
        
        return asrTime
    }
    
    /**
     * ISHA PRAYER TIME CALCULATION WITH HIGH-LATITUDE ADJUSTMENTS
     * 
     * Isha is the night prayer that occurs when the sky becomes fully dark after sunset.
     * This is the most complex prayer time calculation because:
     * 
     * 1. DUAL CALCULATION METHODS: Isha can be calculated using either:
     *    - ANGLE-BASED: Sun depression angle below horizon (like Fajr)
     *    - TIME-BASED: Fixed interval after Maghrib (sunset)
     *    - Different calculation methods use different approaches
     * 
     * 2. METHOD-SPECIFIC LOGIC:
     *    - University of Islamic Sciences, Karachi: 18° depression angle
     *    - Islamic Society of North America: 15° depression angle  
     *    - Umm Al-Qura (Makkah): 90 minutes after Maghrib (fixed time)
     *    - Egyptian General Authority: 17.5° depression angle
     * 
     * 3. HIGH-LATITUDE CHALLENGES: More severe than Fajr
     *    - In summer at high latitudes, true darkness may never occur
     *    - "White nights" phenomenon in extreme northern/southern locations
     *    - Requires sophisticated adjustment algorithms
     * 
     * 4. SEASONAL EXTREMES: 
     *    - Summer: May be very late or impossible to calculate astronomically
     *    - Winter: Usually reliable calculation
     * 
     * @param location Geographic coordinates and timezone
     * @param julianDay Julian day number for the calculation date
     * @param settings User preferences including calculation method
     * @param sunset Sunset time in decimal hours (needed for time-based calculations)
     * @return LocalTime for Isha prayer, or null if calculation impossible
     */
    private fun calculateIshaWithAdjustments(
        location: Location,
        julianDay: Double,
        settings: PrayerSettings,
        sunset: Double
    ): LocalTime? {
        // Get the calculation parameters based on user's selected method
        val ishaAngle = settings.getEffectiveIshaAngle()        // Depression angle (if angle-based)
        val ishaDelay = settings.getEffectiveIshaDelay()        // Fixed minutes after sunset (if time-based)
        
        Log.d(TAG, "⏰ ISHA CALCULATION: Method: ${settings.calculationMethod}")
        Log.d(TAG, "  Angle: $ishaAngle° (null = time-based method)")
        Log.d(TAG, "  Fixed delay: $ishaDelay minutes (null = angle-based method)")
        
        // Attempt calculation using the method-specific approach
        // The calculator will automatically choose angle-based or time-based calculation
        val ishaDecimal = astronomicalCalculator.calculateIsha(location, julianDay, ishaAngle, ishaDelay)
        Log.d(TAG, "  Astronomical result: $ishaDecimal (decimal hour)")
        
        // Check if standard calculation succeeded
        if (!ishaDecimal.isNaN()) {
            val ishaTime = astronomicalCalculator.decimalHourToLocalTime(ishaDecimal)
            Log.d(TAG, "✅ ISHA TIME: $ishaTime (${if (ishaAngle != null) "angle" else "time"}-based calculation)")
            return ishaTime
        }
        
        // Standard calculation failed - apply high latitude adjustments
        // This is more common for Isha than other prayers due to "white nights"
        Log.w(TAG, "⚠️ ISHA: Standard calculation failed (NaN result)")
        Log.w(TAG, "  Likely cause: High latitude location with insufficient darkness")
        Log.w(TAG, "  Applying high latitude adjustment for lat=${location.latitude}")
        Log.w(TAG, "  Adjustment method: ${settings.highLatitudeAdjustment}")
        
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
     * Gets the next prayer time from current time (including tomorrow's Fajr if all today's prayers have passed)
     */
    fun getNextPrayer(prayerTimes: DayPrayerTimes): PrayerTime? {
        val todayNext = prayerTimes.getNextPrayer()
        if (todayNext != null) {
            return todayNext
        }
        
        // All prayers have passed, return tomorrow's Fajr
        return PrayerTime(
            name = "Fajr",
            time = prayerTimes.fajr,
            isNext = true,
            isCurrently = false
        )
    }
    
    /**
     * Gets time remaining until next prayer (including tomorrow's Fajr if all today's prayers have passed)
     */
    fun getTimeUntilNextPrayer(prayerTimes: DayPrayerTimes): String? {
        val now = LocalTime.now()
        val nextPrayer = getNextPrayer(prayerTimes)
        
        if (nextPrayer != null) {
            // There's still a prayer remaining today
            val minutesUntil = java.time.Duration.between(now, nextPrayer.time).toMinutes()
            
            val hours = minutesUntil / 60
            val minutes = minutesUntil % 60
            
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "Now"
            }
        } else {
            // All prayers have passed, calculate time until tomorrow's Fajr
            val fajrTime = prayerTimes.fajr
            val minutesUntilTomorrowFajr = java.time.Duration.between(now, fajrTime.plusHours(24)).toMinutes()
            val hours = minutesUntilTomorrowFajr / 60
            val minutes = minutesUntilTomorrowFajr % 60
            
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "Now"
            }
        }
    }
}