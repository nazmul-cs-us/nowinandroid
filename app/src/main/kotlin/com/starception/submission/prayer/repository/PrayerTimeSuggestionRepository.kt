package com.starception.submission.prayer.repository

import android.util.Log
import com.starception.submission.prayer.api.AladhanApiService
import com.starception.submission.prayer.api.AladhanPrayerTimes
import com.starception.submission.prayer.model.DayPrayerSuggestions
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.Location
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.prayer.model.PrayerTimeSuggestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing AI-powered prayer time suggestions.
 *
 * This repository:
 * 1. Fetches reference prayer times from Aladhan API
 * 2. Compares them with our calculated times
 * 3. Generates offset suggestions for each prayer
 * 4. Caches results to avoid excessive API calls
 */
@Singleton
class PrayerTimeSuggestionRepository @Inject constructor(
    private val aladhanApiService: AladhanApiService
) {
    companion object {
        private const val TAG = "PrayerSuggestionRepo"
        private const val CACHE_VALIDITY_HOURS = 6L // Refresh suggestions every 6 hours
    }

    private val _suggestions = MutableStateFlow(DayPrayerSuggestions.EMPTY)
    val suggestions: StateFlow<DayPrayerSuggestions> = _suggestions.asStateFlow()

    // Cache to avoid excessive API calls
    private var cachedDate: LocalDate? = null
    private var cachedLocation: Location? = null
    private var lastFetchTime: Long = 0

    /**
     * Fetches and calculates prayer time suggestions.
     *
     * @param ourCalculatedTimes Our locally calculated prayer times
     * @param settings User's prayer settings (includes current offsets)
     * @return DayPrayerSuggestions with suggestions for each prayer
     */
    suspend fun fetchSuggestions(
        ourCalculatedTimes: DayPrayerTimes,
        settings: PrayerSettings
    ): DayPrayerSuggestions {
        val location = ourCalculatedTimes.location
        val date = ourCalculatedTimes.date.toLocalDate()

        // Check cache validity
        if (isCacheValid(date, location)) {
            Log.d(TAG, "📦 Using cached suggestions")
            return _suggestions.value
        }

        Log.i(TAG, "🔄 Fetching new AI suggestions from Aladhan API")
        _suggestions.value = DayPrayerSuggestions.LOADING

        try {
            // Fetch reference times from Aladhan API
            val referenceTimes = aladhanApiService.fetchPrayerTimes(
                location = location,
                date = date,
                calculationMethod = settings.calculationMethod
            )

            if (referenceTimes == null) {
                Log.e(TAG, "❌ Failed to fetch reference times")
                val errorResult = DayPrayerSuggestions(
                    fajr = null,
                    dhuhr = null,
                    asr = null,
                    maghrib = null,
                    isha = null,
                    error = "Failed to fetch reference times"
                )
                _suggestions.value = errorResult
                return errorResult
            }

            // Calculate suggestions by comparing reference with our calculated times
            val suggestions = calculateSuggestions(
                ourTimes = ourCalculatedTimes,
                referenceTimes = referenceTimes,
                currentOffsets = settings.timeOffsets
            )

            // Update cache
            cachedDate = date
            cachedLocation = location
            lastFetchTime = System.currentTimeMillis()

            _suggestions.value = suggestions

            Log.i(TAG, "✨ AI suggestions calculated successfully")
            logSuggestions(suggestions)

            return suggestions

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error calculating suggestions: ${e.message}", e)
            val errorResult = DayPrayerSuggestions(
                fajr = null,
                dhuhr = null,
                asr = null,
                maghrib = null,
                isha = null,
                error = e.message
            )
            _suggestions.value = errorResult
            return errorResult
        }
    }

    /**
     * Calculates offset suggestions by comparing our times with reference times.
     */
    private fun calculateSuggestions(
        ourTimes: DayPrayerTimes,
        referenceTimes: AladhanPrayerTimes,
        currentOffsets: com.starception.submission.prayer.model.PrayerTimeOffsets
    ): DayPrayerSuggestions {
        Log.d(TAG, "📊 Calculating suggestions...")
        Log.d(TAG, "   Our times vs Reference times:")

        return DayPrayerSuggestions(
            fajr = createSuggestion(
                prayerName = "Fajr",
                ourTime = ourTimes.fajr,
                referenceTime = referenceTimes.fajr,
                currentOffset = currentOffsets.fajr
            ),
            dhuhr = createSuggestion(
                prayerName = "Dhuhr",
                ourTime = ourTimes.dhuhr,
                referenceTime = referenceTimes.dhuhr,
                currentOffset = currentOffsets.dhuhr
            ),
            asr = createSuggestion(
                prayerName = "Asr",
                ourTime = ourTimes.asr,
                referenceTime = referenceTimes.asr,
                currentOffset = currentOffsets.asr
            ),
            maghrib = createSuggestion(
                prayerName = "Maghrib",
                ourTime = ourTimes.maghrib,
                referenceTime = referenceTimes.maghrib,
                currentOffset = currentOffsets.maghrib
            ),
            isha = createSuggestion(
                prayerName = "Isha",
                ourTime = ourTimes.isha,
                referenceTime = referenceTimes.isha,
                currentOffset = currentOffsets.isha
            )
        )
    }

    /**
     * Creates a suggestion for a single prayer time.
     *
     * The suggested offset = referenceTime - ourCalculatedTime
     * This tells the user how much to adjust to match the reference.
     */
    private fun createSuggestion(
        prayerName: String,
        ourTime: LocalTime,
        referenceTime: LocalTime?,
        currentOffset: Int
    ): PrayerTimeSuggestion? {
        if (referenceTime == null) {
            Log.w(TAG, "   ⚠️ $prayerName: No reference time available")
            return null
        }

        // Calculate difference: how many minutes is reference ahead/behind our time
        val diffMinutes = Duration.between(ourTime, referenceTime).toMinutes().toInt()

        Log.d(TAG, "   $prayerName: Our=$ourTime, Ref=$referenceTime, Diff=${diffMinutes}m, Current=${currentOffset}m")

        return PrayerTimeSuggestion(
            prayerName = prayerName,
            suggestedOffset = diffMinutes,
            currentOffset = currentOffset,
            ourCalculatedTime = ourTime,
            referenceTime = referenceTime,
            differenceMinutes = diffMinutes
        )
    }

    /**
     * Checks if cached suggestions are still valid.
     */
    private fun isCacheValid(date: LocalDate, location: Location): Boolean {
        // Check if date changed
        if (cachedDate != date) {
            Log.d(TAG, "🔄 Cache invalid: Date changed")
            return false
        }

        // Check if location changed significantly
        if (cachedLocation == null) return false

        val latDiff = kotlin.math.abs(cachedLocation!!.latitude - location.latitude)
        val lonDiff = kotlin.math.abs(cachedLocation!!.longitude - location.longitude)

        if (latDiff > 0.1 || lonDiff > 0.1) {
            Log.d(TAG, "🔄 Cache invalid: Location changed")
            return false
        }

        // Check if cache expired
        val hoursSinceLastFetch = (System.currentTimeMillis() - lastFetchTime) / (1000 * 60 * 60)
        if (hoursSinceLastFetch >= CACHE_VALIDITY_HOURS) {
            Log.d(TAG, "🔄 Cache invalid: Expired")
            return false
        }

        return true
    }

    /**
     * Logs suggestions summary.
     */
    private fun logSuggestions(suggestions: DayPrayerSuggestions) {
        Log.i(TAG, "📊 AI SUGGESTIONS SUMMARY:")

        listOfNotNull(
            suggestions.fajr,
            suggestions.dhuhr,
            suggestions.asr,
            suggestions.maghrib,
            suggestions.isha
        ).forEach { suggestion ->
            val status = if (suggestion.hasDifferentSuggestion()) {
                "📌 DIFFERENT (current: ${suggestion.getFormattedCurrentOffset()}, suggest: ${suggestion.getFormattedSuggestion()})"
            } else {
                "✅ MATCH (${suggestion.getFormattedCurrentOffset()})"
            }
            Log.i(TAG, "   ${suggestion.prayerName}: $status")
        }
    }

    /**
     * Clears cached suggestions.
     */
    fun clearCache() {
        cachedDate = null
        cachedLocation = null
        lastFetchTime = 0
        _suggestions.value = DayPrayerSuggestions.EMPTY
        Log.d(TAG, "🗑️ Cache cleared")
    }
}
