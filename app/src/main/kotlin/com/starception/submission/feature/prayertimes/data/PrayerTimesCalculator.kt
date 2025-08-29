package com.starception.submission.feature.prayertimes.data

import android.content.Context
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.Location
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.prayer.service.PrayerTimeCalculatorService
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate

/**
 * EntryPoint for accessing PrayerTimeCalculatorService without ViewModel
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PrayerTimeCalculatorEntryPoint {
    fun prayerTimeCalculatorService(): PrayerTimeCalculatorService
}

/**
 * Calculator class that handles prayer times calculation with proper dependency injection
 */
class PrayerTimesCalculator(private val context: Context) {
    
    /**
     * Calculate prayer times using default settings and location
     * @return Pair of DayPrayerTimes and location display name
     */
    suspend fun calculateDefaultPrayerTimes(): Pair<DayPrayerTimes?, String> {
        return try {
            // Get prayer calculator service via EntryPoint
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                PrayerTimeCalculatorEntryPoint::class.java
            )
            val calculator = entryPoint.prayerTimeCalculatorService()
            
            // Use default location (Dubai) - can be improved with GPS later
            val defaultLocation = Location(
                latitude = 25.2048,  // Dubai coordinates as default
                longitude = 55.2708,
                timeZoneOffset = 4.0, // UAE timezone
                city = "Dubai",
                country = "UAE"
            )
            
            // Default prayer settings
            val settings = PrayerSettings()
            
            // Calculate for today
            val today = LocalDate.now()
            val calculatedTimes = calculator.calculatePrayerTimes(today, defaultLocation, settings)
            
            Pair(calculatedTimes, defaultLocation.getDisplayName())
        } catch (e: Exception) {
            // Return null if calculation fails
            Pair(null, "Unknown Location")
        }
    }
    
    /**
     * Calculate prayer times for specific location and settings
     * @param location The geographic location
     * @param settings Prayer calculation settings
     * @param date The date to calculate for (defaults to today)
     * @return Pair of DayPrayerTimes and location display name
     */
    suspend fun calculatePrayerTimes(
        location: Location,
        settings: PrayerSettings = PrayerSettings(),
        date: LocalDate = LocalDate.now()
    ): Pair<DayPrayerTimes?, String> {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                PrayerTimeCalculatorEntryPoint::class.java
            )
            val calculator = entryPoint.prayerTimeCalculatorService()
            
            val calculatedTimes = calculator.calculatePrayerTimes(date, location, settings)
            
            Pair(calculatedTimes, location.getDisplayName())
        } catch (e: Exception) {
            Pair(null, location.getDisplayName())
        }
    }
}