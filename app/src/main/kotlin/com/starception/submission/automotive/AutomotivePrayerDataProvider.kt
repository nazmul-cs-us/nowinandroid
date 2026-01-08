package com.starception.submission.automotive

import android.content.Context
import android.util.Log
import com.starception.submission.prayer.calculator.AstronomicalCalculator
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.Location
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.prayer.repository.PrayerSettingsRepository
import com.starception.submission.prayer.service.PrayerTimeCalculatorService
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.*

/**
 * Android Auto Prayer Data Provider
 *
 * Provides prayer times and Qibla direction data for Android Auto screens.
 * This class bridges the gap between Car context and the app's prayer infrastructure.
 *
 * Uses Hilt EntryPoint to access singleton instances from the main application.
 */
class AutomotivePrayerDataProvider(private val context: Context) {

    companion object {
        private const val TAG = "AutomotivePrayerData"

        // Kaaba coordinates for Qibla calculation
        const val KAABA_LATITUDE = 21.4225
        const val KAABA_LONGITUDE = 39.8262
    }

    /**
     * Hilt EntryPoint to access app's singleton dependencies
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AutomotiveEntryPoint {
        fun prayerSettingsRepository(): PrayerSettingsRepository
        fun astronomicalCalculator(): AstronomicalCalculator
        fun prayerTimeCalculatorService(): PrayerTimeCalculatorService
    }

    private val entryPoint: AutomotiveEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AutomotiveEntryPoint::class.java
        )
    }

    private val repository: PrayerSettingsRepository by lazy {
        entryPoint.prayerSettingsRepository()
    }

    private val prayerService: PrayerTimeCalculatorService by lazy {
        entryPoint.prayerTimeCalculatorService()
    }

    /**
     * Data class for automotive prayer time display
     */
    data class AutomotivePrayerTime(
        val name: String,
        val time: LocalTime,
        val isNext: Boolean = false,
        val isCurrent: Boolean = false
    )

    /**
     * Data class for Qibla direction information
     */
    data class QiblaInfo(
        val direction: Double,
        val distance: Double,
        val compassDirection: String,
        val locationName: String,
        val latitude: Double,
        val longitude: Double
    )

    /**
     * Data class combining prayer times with location info
     */
    data class AutomotivePrayerData(
        val prayerTimes: List<AutomotivePrayerTime>,
        val locationName: String,
        val calculationMethod: String,
        val nextPrayerCountdown: String?
    )

    /**
     * Gets today's prayer times using the app's existing calculation infrastructure
     */
    @Suppress("DEPRECATION")
    suspend fun getPrayerTimes(): AutomotivePrayerData = withContext(Dispatchers.IO) {
        Log.i(TAG, "🚗 Android Auto: Loading prayer times")

        try {
            // First try to get cached prayer times for instant display
            val cachedTimes = repository.getCachedPrayerTimes()

            if (cachedTimes != null) {
                Log.i(TAG, "✅ Using cached prayer times")
                return@withContext convertToAutomotiveData(cachedTimes)
            }

            // No cached times, need to calculate
            Log.i(TAG, "📊 Calculating prayer times...")

            val settings = repository.getSettings()
            val location = getLocation(settings)

            if (location == null) {
                Log.w(TAG, "⚠️ No location available, using defaults")
                return@withContext getDefaultPrayerData()
            }

            val dayPrayerTimes = prayerService.calculatePrayerTimes(
                LocalDate.now(),
                location,
                settings
            )

            if (dayPrayerTimes != null) {
                Log.i(TAG, "✅ Prayer times calculated successfully")
                return@withContext convertToAutomotiveData(dayPrayerTimes)
            }

            Log.w(TAG, "⚠️ Calculation failed, using defaults")
            return@withContext getDefaultPrayerData()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting prayer times", e)
            return@withContext getDefaultPrayerData()
        }
    }

    /**
     * Gets Qibla direction information
     */
    @Suppress("DEPRECATION")
    suspend fun getQiblaInfo(): QiblaInfo = withContext(Dispatchers.IO) {
        Log.i(TAG, "🧭 Android Auto: Calculating Qibla direction")

        try {
            val settings = repository.getSettings()
            val location = getLocation(settings)

            if (location != null) {
                val direction = calculateQiblaDirection(location.latitude, location.longitude)
                val distance = calculateDistance(
                    location.latitude, location.longitude,
                    KAABA_LATITUDE, KAABA_LONGITUDE
                )

                Log.i(TAG, "✅ Qibla calculated: ${direction.toInt()}° at ${distance.toInt()} km")

                return@withContext QiblaInfo(
                    direction = direction,
                    distance = distance,
                    compassDirection = getCompassDirection(direction),
                    locationName = location.getDisplayName(),
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            }

            // Fallback to cached location
            val cachedTimes = repository.getCachedPrayerTimes()
            if (cachedTimes != null) {
                val loc = cachedTimes.location
                val direction = calculateQiblaDirection(loc.latitude, loc.longitude)
                val distance = calculateDistance(
                    loc.latitude, loc.longitude,
                    KAABA_LATITUDE, KAABA_LONGITUDE
                )

                return@withContext QiblaInfo(
                    direction = direction,
                    distance = distance,
                    compassDirection = getCompassDirection(direction),
                    locationName = loc.getDisplayName(),
                    latitude = loc.latitude,
                    longitude = loc.longitude
                )
            }

            // Return default (will use GPS when available)
            Log.w(TAG, "⚠️ No location for Qibla, using placeholder")
            return@withContext QiblaInfo(
                direction = 0.0,
                distance = 0.0,
                compassDirection = "N",
                locationName = "Location unavailable",
                latitude = 0.0,
                longitude = 0.0
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error calculating Qibla", e)
            return@withContext QiblaInfo(
                direction = 0.0,
                distance = 0.0,
                compassDirection = "N",
                locationName = "Error",
                latitude = 0.0,
                longitude = 0.0
            )
        }
    }

    /**
     * Gets location from settings or cached data
     */
    private fun getLocation(settings: PrayerSettings): Location? {
        // First check if there's a manually set location
        if (!settings.useGpsLocation && settings.location != null) {
            return settings.location
        }

        // Check cached prayer times for location
        val cachedTimes = repository.getCachedPrayerTimes()
        if (cachedTimes != null) {
            return cachedTimes.location
        }

        // Check location preferences
        val locationPrefs = repository.getLocationPreferences()
        if (locationPrefs.location != null) {
            return locationPrefs.location
        }

        return null
    }

    /**
     * Converts DayPrayerTimes to automotive format
     */
    @Suppress("DEPRECATION")
    private fun convertToAutomotiveData(dayPrayerTimes: DayPrayerTimes): AutomotivePrayerData {
        val now = LocalTime.now()
        val settings = repository.getSettings()

        // Apply user offsets to base times
        val fajr = dayPrayerTimes.fajr.plusMinutes(settings.timeOffsets.fajr.toLong())
        val sunrise = dayPrayerTimes.sunrise.plusMinutes(settings.timeOffsets.sunrise.toLong())
        val dhuhr = dayPrayerTimes.dhuhr.plusMinutes(settings.timeOffsets.dhuhr.toLong())
        val asr = dayPrayerTimes.asr.plusMinutes(settings.timeOffsets.asr.toLong())
        val maghrib = dayPrayerTimes.maghrib.plusMinutes(settings.timeOffsets.maghrib.toLong())
        val isha = dayPrayerTimes.isha.plusMinutes(settings.timeOffsets.isha.toLong())

        val prayerList = listOf(
            AutomotivePrayerTime("Fajr", fajr),
            AutomotivePrayerTime("Dhuhr", dhuhr),
            AutomotivePrayerTime("Asr", asr),
            AutomotivePrayerTime("Maghrib", maghrib),
            AutomotivePrayerTime("Isha", isha)
        )

        // Determine next and current prayer
        val nextIndex = prayerList.indexOfFirst { it.time.isAfter(now) }
        val prayersWithStatus = prayerList.mapIndexed { index, prayer ->
            val isNext = index == nextIndex || (nextIndex == -1 && index == 0)
            val isCurrent = if (index < prayerList.size - 1) {
                now.isAfter(prayer.time) && now.isBefore(prayerList[index + 1].time)
            } else {
                now.isAfter(prayer.time) && now.isBefore(prayer.time.plusHours(2))
            }
            prayer.copy(isNext = isNext, isCurrent = isCurrent)
        }

        // Calculate countdown to next prayer
        val nextPrayer = prayersWithStatus.find { it.isNext }
        val countdown = nextPrayer?.let { calculateTimeUntil(it.time) }

        return AutomotivePrayerData(
            prayerTimes = prayersWithStatus,
            locationName = dayPrayerTimes.location.getDisplayName(),
            calculationMethod = settings.calculationMethod.displayName,
            nextPrayerCountdown = countdown
        )
    }

    /**
     * Gets default prayer data when calculation fails
     */
    private fun getDefaultPrayerData(): AutomotivePrayerData {
        val now = LocalTime.now()

        // Reasonable default times
        val defaults = listOf(
            AutomotivePrayerTime("Fajr", LocalTime.of(5, 30)),
            AutomotivePrayerTime("Dhuhr", LocalTime.of(12, 30)),
            AutomotivePrayerTime("Asr", LocalTime.of(15, 45)),
            AutomotivePrayerTime("Maghrib", LocalTime.of(18, 30)),
            AutomotivePrayerTime("Isha", LocalTime.of(20, 0))
        )

        val nextIndex = defaults.indexOfFirst { it.time.isAfter(now) }
        val withStatus = defaults.mapIndexed { index, prayer ->
            prayer.copy(isNext = index == nextIndex || (nextIndex == -1 && index == 0))
        }

        return AutomotivePrayerData(
            prayerTimes = withStatus,
            locationName = "Location unavailable",
            calculationMethod = "Default",
            nextPrayerCountdown = withStatus.find { it.isNext }?.let { calculateTimeUntil(it.time) }
        )
    }

    /**
     * Calculates time until a given prayer time
     */
    private fun calculateTimeUntil(prayerTime: LocalTime): String {
        val now = LocalTime.now()

        return if (prayerTime.isAfter(now)) {
            val duration = java.time.Duration.between(now, prayerTime)
            val hours = duration.toHours()
            val minutes = duration.toMinutesPart()

            when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "Now"
            }
        } else {
            // Next day
            val duration = java.time.Duration.between(now, LocalTime.MAX) +
                    java.time.Duration.between(LocalTime.MIN, prayerTime)
            val hours = duration.toHours()
            val minutes = duration.toMinutesPart()

            when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "Now"
            }
        }
    }

    /**
     * Calculates Qibla direction (bearing to Kaaba)
     */
    private fun calculateQiblaDirection(lat1: Double, lon1: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(KAABA_LATITUDE)
        val deltaLonRad = Math.toRadians(KAABA_LONGITUDE - lon1)

        val x = sin(deltaLonRad) * cos(lat2Rad)
        val y = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLonRad)

        val bearingRad = atan2(x, y)
        val bearingDeg = Math.toDegrees(bearingRad)

        return (bearingDeg + 360) % 360
    }

    /**
     * Calculates great circle distance between two points
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // km

        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLatRad = Math.toRadians(lat2 - lat1)
        val deltaLonRad = Math.toRadians(lon2 - lon1)

        val a = sin(deltaLatRad / 2) * sin(deltaLatRad / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLonRad / 2) * sin(deltaLonRad / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * Converts bearing to compass direction
     */
    private fun getCompassDirection(bearing: Double): String {
        return when {
            bearing >= 337.5 || bearing < 22.5 -> "North"
            bearing < 67.5 -> "Northeast"
            bearing < 112.5 -> "East"
            bearing < 157.5 -> "Southeast"
            bearing < 202.5 -> "South"
            bearing < 247.5 -> "Southwest"
            bearing < 292.5 -> "West"
            bearing < 337.5 -> "Northwest"
            else -> "North"
        }
    }
}
