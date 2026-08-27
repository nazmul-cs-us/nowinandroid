package com.starception.submission.prayer.service

import android.util.Log
import com.starception.submission.prayer.calculator.AstronomicalCalculator
import com.starception.submission.prayer.model.*
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toKotlinLocalDate

/**
 * Boundary between the shared calculation engine and this Android service.
 *
 * `AstronomicalCalculator` lives in `:core:prayer-engine` and speaks
 * kotlinx-datetime so it can compile for iOS; everything above it here still
 * speaks java.time. Converting in these two adapters keeps the seam in one
 * place, rather than sprinkling `.toJavaLocalTime()` through the schedule logic.
 *
 * They disappear when this service itself moves to kotlinx-datetime as part of
 * its feature slice.
 */
private fun AstronomicalCalculator.julianDayFor(date: LocalDate): Double =
    calculateJulianDay(date.toKotlinLocalDate())

private fun AstronomicalCalculator.decimalHourToJavaTime(decimalHour: Double): LocalTime? =
    decimalHourToLocalTime(decimalHour)?.toJavaLocalTime()

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
     * ENHANCED PRAYER CALCULATION LOGGING SYSTEM
     * Provides comprehensive visibility into all prayer time calculations
     */
    
    private fun logCalculationPhase(phase: String, details: String, data: Map<String, Any> = emptyMap()) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        Log.i(TAG, "")
        Log.i(TAG, "🔆 CALCULATION PHASE: $phase")
        Log.i(TAG, "⏰ Timestamp: $timestamp")
        Log.i(TAG, "📝 Details: $details")
        
        data.forEach { (key, value) ->
            Log.i(TAG, "📊 $key: $value")
        }
        Log.i(TAG, "")
    }
    
    private fun logPrayerTimeResult(prayerName: String, time: LocalTime?, offset: Int, method: String = "") {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        Log.i(TAG, "")
        Log.i(TAG, "🕌 PRAYER TIME RESULT: $prayerName")
        Log.i(TAG, "⏰ Timestamp: $timestamp")
        
        if (time != null) {
            Log.i(TAG, "✅ Calculated Time: $time")
            Log.i(TAG, "⏱️ User Offset: ${if (offset != 0) "$offset minutes" else "none"}")
            if (method.isNotEmpty()) {
                Log.i(TAG, "🔬 Method: $method")
            }
        } else {
            Log.e(TAG, "❌ Calculation Failed: Returned null")
            Log.e(TAG, "🔍 Likely Cause: High latitude or extreme calculation parameters")
        }
        Log.i(TAG, "")
    }
    
    private fun validateCalculationInputs(date: LocalDate, location: Location, settings: PrayerSettings): Boolean {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
            
        Log.i(TAG, "")
        Log.i(TAG, "🔍 INPUT VALIDATION")
        Log.i(TAG, "⏰ Timestamp: $timestamp")
        
        var isValid = true
        
        // Validate date
        val today = LocalDate.now()
        val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(today, date)
        if (Math.abs(daysDiff) > 365) {
            Log.w(TAG, "⚠️ Date warning: $date is ${Math.abs(daysDiff)} days from today")
        }
        Log.i(TAG, "📅 Date: $date (${daysDiff} days from today)")
        
        // Validate location
        if (!location.isValid()) {
            Log.e(TAG, "❌ Invalid location coordinates")
            Log.e(TAG, "📍 Latitude: ${location.latitude} (must be -90° to +90°)")
            Log.e(TAG, "📍 Longitude: ${location.longitude} (must be -180° to +180°)")
            isValid = false
        } else {
            Log.i(TAG, "✅ Location: ${location.getDisplayName()}")
            Log.i(TAG, "📍 Coordinates: ${location.latitude}°, ${location.longitude}°")
            Log.i(TAG, "🌍 Timezone: UTC${if (location.timeZoneOffset >= 0) "+" else ""}${location.timeZoneOffset}")
        }
        
        // Validate settings
        Log.i(TAG, "⚙️ Settings validation:")
        Log.i(TAG, "  🕌 Calculation Method: ${settings.calculationMethod.displayName}")
        Log.i(TAG, "  🤲 ASR Madhhab: ${settings.asrMadhhab.displayName} (shadow factor: ${settings.asrMadhhab.shadowFactor})")
        Log.i(TAG, "  🏔️ High Latitude: ${settings.highLatitudeAdjustment.name}")
        
        val customAngles = mutableListOf<String>()
        settings.customFajrAngle?.let { customAngles.add("Fajr: ${it}°") }
        settings.customIshaAngle?.let { customAngles.add("Isha: ${it}°") }
        if (customAngles.isNotEmpty()) {
            Log.i(TAG, "  🌅 Custom Angles: ${customAngles.joinToString(", ")}")
        }
        
        val nonZeroOffsets = listOf(
            "Fajr" to settings.timeOffsets.fajr,
            "Sunrise" to settings.timeOffsets.sunrise,
            "Dhuhr" to settings.timeOffsets.dhuhr,
            "Asr" to settings.timeOffsets.asr,
            "Maghrib" to settings.timeOffsets.maghrib,
            "Isha" to settings.timeOffsets.isha
        ).filter { it.second != 0 }
        
        if (nonZeroOffsets.isNotEmpty()) {
            Log.i(TAG, "  ⏱️ User Offsets: ${nonZeroOffsets.joinToString(", ") { "${it.first}: ${it.second}m" }}")
        } else {
            Log.i(TAG, "  ⏱️ User Offsets: None")
        }
        
        Log.i(TAG, if (isValid) "✅ All inputs valid" else "❌ Input validation failed")
        Log.i(TAG, "")
        
        return isValid
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
        val calculationStart = System.currentTimeMillis()
        
        Log.i(TAG, "")
        Log.i(TAG, "🌅 ISLAMIC PRAYER TIMES CALCULATION")
        Log.i(TAG, "=".repeat(80))
        Log.i(TAG, "🔆 Starting comprehensive prayer time calculation")
        Log.i(TAG, "")
        
        // STEP 1: INPUT VALIDATION
        logCalculationPhase(
            "INPUT_VALIDATION",
            "Validating calculation inputs for safety and accuracy",
            mapOf(
                "date" to date,
                "location" to location.getDisplayName(),
                "method" to settings.calculationMethod.displayName
            )
        )
        
        if (!validateCalculationInputs(date, location, settings)) {
            Log.e(TAG, "❌ CALCULATION ABORTED: Invalid inputs detected")
            Log.e(TAG, "")
            return null
        }
        
        // STEP 2: ASTRONOMICAL FOUNDATION
        logCalculationPhase(
            "ASTRONOMICAL_FOUNDATION",
            "Converting Gregorian date to Julian Day for astronomical precision",
            mapOf("inputDate" to date)
        )
        
        val julianDay = astronomicalCalculator.julianDayFor(date)
        
        if (julianDay.isNaN()) {
            Log.e(TAG, "❌ JULIAN DAY CALCULATION FAILED")
            Log.e(TAG, "")
            return null
        }
        
        Log.i(TAG, "✅ ASTRONOMICAL FOUNDATION: Julian Day calculated successfully")
        Log.i(TAG, "📊 Julian Day: $julianDay")
        Log.i(TAG, "🗓️ Corresponds to: $date")
        Log.i(TAG, "")
        
        // STEP 3: FUNDAMENTAL SOLAR CALCULATIONS
        logCalculationPhase(
            "FUNDAMENTAL_SOLAR_CALCULATIONS",
            "Calculating core solar positions for prayer time foundation",
            mapOf(
                "location" to location.getDisplayName(),
                "julianDay" to julianDay
            )
        )
        
        val solarNoon = astronomicalCalculator.calculateSolarNoon(location, julianDay)
        val sunrise = astronomicalCalculator.calculateSunrise(location, julianDay)
        val sunset = astronomicalCalculator.calculateSunset(location, julianDay)
        
        // Verify fundamental calculations succeeded
        if (solarNoon.isNaN() || sunrise.isNaN() || sunset.isNaN()) {
            Log.e(TAG, "❌ FUNDAMENTAL SOLAR CALCULATIONS FAILED")
            Log.e(TAG, "  Solar Noon: ${if (solarNoon.isNaN()) "FAILED" else "OK"}")
            Log.e(TAG, "  Sunrise: ${if (sunrise.isNaN()) "FAILED" else "OK"}")
            Log.e(TAG, "  Sunset: ${if (sunset.isNaN()) "FAILED" else "OK"}")
            Log.e(TAG, "")
            return null
        }
        
        val solarNoonTime = astronomicalCalculator.decimalHourToJavaTime(solarNoon)
        val sunriseTime = astronomicalCalculator.decimalHourToJavaTime(sunrise)
        val sunsetTime = astronomicalCalculator.decimalHourToJavaTime(sunset)
        
        Log.i(TAG, "✅ FUNDAMENTAL SOLAR CALCULATIONS: All positions calculated successfully")
        Log.i(TAG, "☀️ Solar Noon: $solarNoonTime (${solarNoon} decimal hours)")
        Log.i(TAG, "🌅 Sunrise: $sunriseTime (${sunrise} decimal hours)")
        Log.i(TAG, "🌆 Sunset: $sunsetTime (${sunset} decimal hours)")
        Log.i(TAG, "📊 Day Length: ${String.format("%.2f", sunset - sunrise)} hours")
        Log.i(TAG, "")
        
        // STEP 4: ISLAMIC PRAYER-SPECIFIC CALCULATIONS
        logCalculationPhase(
            "ISLAMIC_PRAYER_CALCULATIONS",
            "Applying Islamic astronomical rules for complex prayer calculations",
            mapOf(
                "method" to settings.calculationMethod.displayName,
                "madhhab" to settings.asrMadhhab.displayName,
                "highLatitudeMethod" to settings.highLatitudeAdjustment.name
            )
        )
        
        val fajrTime = calculateFajrWithAdjustments(location, julianDay, settings)
        val asrTime = calculateAsrWithAdjustments(location, julianDay, settings)
        val ishaTime = calculateIshaWithAdjustments(location, julianDay, settings, sunset)
        
        // Log individual prayer calculation results
        logPrayerTimeResult("FAJR", fajrTime, settings.timeOffsets.fajr, "${settings.getEffectiveFajrAngle()}° depression angle")
        logPrayerTimeResult("ASR", asrTime, settings.timeOffsets.asr, "${settings.asrMadhhab.displayName} (${settings.asrMadhhab.shadowFactor}x shadow)")
        logPrayerTimeResult("ISHA", ishaTime, settings.timeOffsets.isha, 
            if (settings.getEffectiveIshaAngle() != null) "${settings.getEffectiveIshaAngle()}° depression angle" 
            else "${settings.getEffectiveIshaDelay()} minutes after sunset")
        
        Log.i(TAG, "✅ ISLAMIC PRAYER CALCULATIONS: All prayers calculated")
        Log.i(TAG, "")
        
        // STEP 5: USER CUSTOMIZATION APPLICATION
        logCalculationPhase(
            "USER_CUSTOMIZATION",
            "Applying user-defined time adjustments and preferences",
            mapOf(
                "totalNonZeroOffsets" to settings.timeOffsets.run {
                    listOf(fajr, sunrise, dhuhr, asr, maghrib, isha).count { it != 0 }
                },
                "offsetsJson" to settings.timeOffsets.toString()
            )
        )
        
        // CRITICAL CHANGE: DayPrayerTimes now contains BASE astronomical times ONLY
        // User offsets are NOT applied here - they will be applied at UI display layer
        // This ensures:
        // 1. Clean separation: Calculation layer = base times, UI layer = apply offsets
        // 2. Interactive dial has correct base reference point
        // 3. "Restore Auto-Generated" simply sets offsets to 0
        // 4. No double-offset bugs

        // STEP 6: FINAL PRAYER TIME ASSEMBLY
        // Package BASE astronomical times without any user offset adjustments

        // FAJR: Base pre-dawn time (no user offset applied)
        val fajr = fajrTime

        // SUNRISE: Base astronomical sunrise time (no user offset applied)
        // Note: Sunrise is for reference/sunnah prayers, not one of the 5 obligatory prayers
        val sunriseAdjusted = astronomicalCalculator.decimalHourToJavaTime(sunrise)

        // DHUHR: Base solar noon time (no user offset applied)
        // This is when the sun reaches its zenith (highest point in the sky)
        val dhuhr = astronomicalCalculator.decimalHourToJavaTime(solarNoon)

        // ASR: Base afternoon shadow-based time (no user offset applied)
        val asr = asrTime

        // MAGHRIB: Base sunset time + method-specific offset ONLY
        // Method-specific offset (e.g., +3 minutes for some regions) is still applied
        // User personal offset will be applied at UI layer
        val maghrib = addMinutesToTime(
            astronomicalCalculator.decimalHourToJavaTime(sunset),
            settings.getEffectiveMaghribOffset()  // Method/country-specific offset (e.g., 4min for Iran)
        )

        // ISHA: Base night time (no user offset applied)
        val isha = ishaTime
        
        // STEP 6: FINAL VERIFICATION AND LOGGING
        logCalculationPhase(
            "FINAL_VERIFICATION",
            "Verifying all prayer times are valid and applying final adjustments"
        )
        
        Log.i(TAG, "🕰️ BASE ASTRONOMICAL PRAYER TIMES (no user offsets):")
        Log.i(TAG, "  🌄 Fajr: $fajr (base time)")
        Log.i(TAG, "  🌅 Sunrise: $sunriseAdjusted (base time)")
        Log.i(TAG, "  ☀️ Dhuhr: $dhuhr (base time)")
        Log.i(TAG, "  🌇 Asr: $asr (base time)")
        Log.i(TAG, "  🌆 Maghrib: $maghrib (includes method offset: ${settings.getEffectiveMaghribOffset()}m)")
        Log.i(TAG, "  🌙 Isha: $isha (base time)")
        Log.i(TAG, "")
        Log.i(TAG, "ℹ️ NOTE: User offsets will be applied at UI display layer")
        Log.i(TAG, "")
        
        // STEP 7: FINAL VALIDATION
        if (fajr == null || sunriseAdjusted == null || dhuhr == null || 
            asr == null || maghrib == null || isha == null) {
            
            val failedPrayers = mutableListOf<String>()
            if (fajr == null) failedPrayers.add("Fajr")
            if (sunriseAdjusted == null) failedPrayers.add("Sunrise")
            if (dhuhr == null) failedPrayers.add("Dhuhr")
            if (asr == null) failedPrayers.add("Asr")
            if (maghrib == null) failedPrayers.add("Maghrib")
            if (isha == null) failedPrayers.add("Isha")
            
            Log.e(TAG, "")
            Log.e(TAG, "❌ CALCULATION FAILURE DETECTED")
            Log.e(TAG, "📊 Failed Calculations: ${failedPrayers.joinToString(", ")}")
            Log.e(TAG, "🔍 Likely Causes:")
            Log.e(TAG, "  - High latitude location (polar regions)")
            Log.e(TAG, "  - Extreme calculation parameters")
            Log.e(TAG, "  - Invalid astronomical conditions")
            Log.e(TAG, "🛠️ Recommended Actions:")
            Log.e(TAG, "  - Check high latitude adjustment method")
            Log.e(TAG, "  - Verify calculation method compatibility")
            Log.e(TAG, "  - Consider using different calculation parameters")
            Log.e(TAG, "")
            return null
        }
        
        val calculationEnd = System.currentTimeMillis()
        val totalDuration = calculationEnd - calculationStart
        
        Log.i(TAG, "✅ PRAYER TIMES CALCULATION COMPLETED SUCCESSFULLY")
        Log.i(TAG, "⚡ Total Calculation Time: ${totalDuration}ms")
        Log.i(TAG, "🌍 Location: ${location.getDisplayName()}")
        Log.i(TAG, "📅 Date: $date")
        Log.i(TAG, "🕌 Method: ${settings.calculationMethod.displayName}")
        Log.i(TAG, "=".repeat(80))
        Log.i(TAG, "")
        
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
        val fajrAngle = settings.getEffectiveFajrAngle()
        
        logCalculationPhase(
            "FAJR_CALCULATION",
            "Calculating pre-dawn prayer time using astronomical depression angle",
            mapOf(
                "depressionAngle" to "$fajrAngle°",
                "calculationMethod" to settings.calculationMethod.displayName,
                "customAngle" to (settings.customFajrAngle?.let { "Yes (${it}°)" } ?: "No")
            )
        )
        
        // Attempt standard astronomical calculation
        val fajrStart = System.currentTimeMillis()
        val fajrDecimal = astronomicalCalculator.calculateFajr(location, julianDay, fajrAngle)
        val fajrDuration = System.currentTimeMillis() - fajrStart
        
        Log.i(TAG, "🔬 FAJR ASTRONOMICAL CALCULATION")
        Log.i(TAG, "  📊 Depression Angle: $fajrAngle° (sun below horizon)")
        Log.i(TAG, "  ⚡ Calculation Time: ${fajrDuration}ms")
        Log.i(TAG, "  📊 Raw Result: $fajrDecimal decimal hours")
        
        // Check if standard calculation succeeded
        if (!fajrDecimal.isNaN()) {
            val fajrTime = astronomicalCalculator.decimalHourToJavaTime(fajrDecimal)
            Log.i(TAG, "  ✅ FAJR SUCCESS: $fajrTime (standard astronomical calculation)")
            Log.i(TAG, "  🌅 Method: ${settings.calculationMethod.displayName} depression angle")
            Log.i(TAG, "")
            return fajrTime
        }
        
        // Standard calculation failed - apply high latitude adjustments
        Log.w(TAG, "")
        Log.w(TAG, "⚠️ FAJR CALCULATION FAILED - HIGH LATITUDE DETECTED")
        Log.w(TAG, "📊 Location: ${location.getDisplayName()} (${location.latitude}° N)")
        Log.w(TAG, "🔍 Analysis: Sun never reaches $fajrAngle° below horizon")
        Log.w(TAG, "🌅 Common in: Polar regions, extreme northern/southern locations")
        Log.w(TAG, "🛠️ Solution: Applying high latitude adjustment method")
        Log.w(TAG, "⚙️ Method: ${settings.highLatitudeAdjustment.name}")
        Log.w(TAG, "")
        
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
        val shadowFactor = settings.asrMadhhab.shadowFactor
        
        logCalculationPhase(
            "ASR_CALCULATION",
            "Calculating afternoon prayer time using shadow-based methodology",
            mapOf(
                "madhhab" to settings.asrMadhhab.displayName,
                "shadowFactor" to shadowFactor,
                "timing" to if (shadowFactor == 2) "Later (Hanafi)" else "Earlier (Shafi/Maliki/Hanbali)"
            )
        )
        
        val asrStart = System.currentTimeMillis()
        val asrDecimal = astronomicalCalculator.calculateAsr(location, julianDay, shadowFactor)
        val asrDuration = System.currentTimeMillis() - asrStart
        
        Log.i(TAG, "🔬 ASR SHADOW-BASED CALCULATION")
        Log.i(TAG, "  🌇 Shadow Method: ${settings.asrMadhhab.displayName}")
        Log.i(TAG, "  📏 Shadow Factor: ${shadowFactor}x object height")
        Log.i(TAG, "  ⚡ Calculation Time: ${asrDuration}ms")
        Log.i(TAG, "  📊 Raw Result: $asrDecimal decimal hours")
        
        if (!asrDecimal.isNaN()) {
            val asrTime = astronomicalCalculator.decimalHourToJavaTime(asrDecimal)
            Log.i(TAG, "  ✅ ASR SUCCESS: $asrTime (shadow-based calculation)")
            Log.i(TAG, "  📈 Interpretation: When shadow = ${shadowFactor} × object height")
            Log.i(TAG, "")
            return asrTime
        } else {
            Log.e(TAG, "  ❌ ASR CALCULATION FAILED: Extreme latitude or astronomical conditions")
            Log.e(TAG, "")
            return null
        }
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
        val ishaAngle = settings.getEffectiveIshaAngle()
        val ishaDelay = settings.getEffectiveIshaDelay()
        val isAngleBased = ishaAngle != null
        
        logCalculationPhase(
            "ISHA_CALCULATION",
            "Calculating night prayer time using ${if (isAngleBased) "angle-based" else "time-based"} method",
            mapOf(
                "calculationMethod" to settings.calculationMethod.displayName,
                "approach" to if (isAngleBased) "Depression angle" else "Fixed delay after sunset",
                "parameter" to if (isAngleBased) "$ishaAngle°" else "$ishaDelay minutes"
            )
        )
        
        val ishaStart = System.currentTimeMillis()
        val ishaDecimal = astronomicalCalculator.calculateIsha(
            location,
            julianDay,
            ishaAngle,
            ishaDelay,
            settings.getEffectiveMaghribOffset()  // Pass maghrib offset for correct Isha delay calculation
        )
        val ishaDuration = System.currentTimeMillis() - ishaStart
        
        Log.i(TAG, "🔬 ISHA NIGHT PRAYER CALCULATION")
        Log.i(TAG, "  🌙 Method: ${settings.calculationMethod.displayName}")
        if (isAngleBased) {
            Log.i(TAG, "  📊 Angle-Based: $ishaAngle° depression angle (sun below horizon)")
        } else {
            Log.i(TAG, "  ⏰ Time-Based: $ishaDelay minutes after sunset")
        }
        Log.i(TAG, "  ⚡ Calculation Time: ${ishaDuration}ms")
        Log.i(TAG, "  📊 Raw Result: $ishaDecimal decimal hours")
        
        if (!ishaDecimal.isNaN()) {
            val ishaTime = astronomicalCalculator.decimalHourToJavaTime(ishaDecimal)
            Log.i(TAG, "  ✅ ISHA SUCCESS: $ishaTime (${if (isAngleBased) "angle" else "time"}-based calculation)")
            Log.i(TAG, "")
            return ishaTime
        }
        
        // Standard calculation failed - apply high latitude adjustments
        Log.w(TAG, "")
        Log.w(TAG, "⚠️ ISHA CALCULATION FAILED - WHITE NIGHTS DETECTED")
        Log.w(TAG, "📊 Location: ${location.getDisplayName()} (${location.latitude}° N)")
        if (isAngleBased) {
            Log.w(TAG, "🔍 Analysis: Sun never reaches $ishaAngle° below horizon")
        } else {
            Log.w(TAG, "🔍 Analysis: Time-based calculation failed")
        }
        Log.w(TAG, "🌅 Common in: Polar regions during summer months ('white nights')")
        Log.w(TAG, "🛠️ Solution: Applying high latitude adjustment method")
        Log.w(TAG, "⚙️ Method: ${settings.highLatitudeAdjustment.name}")
        Log.w(TAG, "")
        
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
                
                astronomicalCalculator.decimalHourToJavaTime(time)
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
                
                astronomicalCalculator.decimalHourToJavaTime(time.let { if (it < 0) it + 24 else it % 24 })
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
                
                astronomicalCalculator.decimalHourToJavaTime(time.let { if (it < 0) it + 24 else it % 24 })
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