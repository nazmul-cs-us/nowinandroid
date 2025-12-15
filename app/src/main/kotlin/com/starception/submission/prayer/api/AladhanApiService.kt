package com.starception.submission.prayer.api

import android.util.Log
import com.starception.submission.prayer.model.CalculationMethod
import com.starception.submission.prayer.model.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service to fetch prayer times from Aladhan API for AI-powered suggestions.
 *
 * This provides reference prayer times that we compare against our calculated times
 * to suggest offsets to users.
 *
 * API Documentation: https://aladhan.com/prayer-times-api
 */
@Singleton
class AladhanApiService @Inject constructor() {

    companion object {
        private const val TAG = "AladhanApiService"
        private const val BASE_URL = "https://api.aladhan.com/v1"
        private const val TIMEOUT_MS = 10000
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Fetches prayer times from Aladhan API for a given location and date.
     *
     * @param location User's location with coordinates
     * @param date Date to fetch prayer times for
     * @param calculationMethod The calculation method to use (for consistency with user's setting)
     * @return AladhanPrayerTimes if successful, null if failed
     */
    suspend fun fetchPrayerTimes(
        location: Location,
        date: LocalDate,
        calculationMethod: CalculationMethod
    ): AladhanPrayerTimes? = withContext(Dispatchers.IO) {
        try {
            val methodCode = getAladhanMethodCode(calculationMethod)
            val dateString = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))

            val urlString = "$BASE_URL/timings/$dateString" +
                "?latitude=${location.latitude}" +
                "&longitude=${location.longitude}" +
                "&method=$methodCode"

            Log.d(TAG, "🌐 Fetching from Aladhan API: $urlString")

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "❌ API error: HTTP $responseCode")
                return@withContext null
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            Log.d(TAG, "✅ API response received (${responseBody.length} chars)")

            val response = json.decodeFromString<AladhanApiResponse>(responseBody)

            if (response.code == 200 && response.data != null) {
                val timings = response.data.timings

                val prayerTimes = AladhanPrayerTimes(
                    fajr = parseTimeString(timings.fajr),
                    sunrise = parseTimeString(timings.sunrise),
                    dhuhr = parseTimeString(timings.dhuhr),
                    asr = parseTimeString(timings.asr),
                    maghrib = parseTimeString(timings.maghrib),
                    isha = parseTimeString(timings.isha),
                    date = date,
                    method = calculationMethod
                )

                Log.i(TAG, "✨ AI Reference times fetched successfully:")
                Log.i(TAG, "   Fajr: ${prayerTimes.fajr}")
                Log.i(TAG, "   Dhuhr: ${prayerTimes.dhuhr}")
                Log.i(TAG, "   Asr: ${prayerTimes.asr}")
                Log.i(TAG, "   Maghrib: ${prayerTimes.maghrib}")
                Log.i(TAG, "   Isha: ${prayerTimes.isha}")

                return@withContext prayerTimes
            } else {
                Log.e(TAG, "❌ API returned error code: ${response.code}")
                return@withContext null
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to fetch from Aladhan API: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Parses time string from API (format: "HH:mm" or "HH:mm (timezone)")
     */
    private fun parseTimeString(timeString: String): LocalTime? {
        return try {
            // API returns format like "05:23" or "05:23 (PKT)"
            val cleanTime = timeString.split(" ").first().trim()
            LocalTime.parse(cleanTime, DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to parse time: $timeString")
            null
        }
    }

    /**
     * Maps our CalculationMethod enum to Aladhan API method codes.
     *
     * Aladhan API method codes:
     * 0 - Jafari / Shia Ithna-Ashari
     * 1 - University of Islamic Sciences, Karachi
     * 2 - Islamic Society of North America (ISNA)
     * 3 - Muslim World League (MWL)
     * 4 - Umm Al-Qura University, Makkah
     * 5 - Egyptian General Authority of Survey
     * 7 - Institute of Geophysics, University of Tehran
     * 8 - Gulf Region
     * 9 - Kuwait
     * 10 - Qatar
     * 11 - Majlis Ugama Islam Singapura, Singapore
     * 12 - Union Organization islamic de France
     * 13 - Diyanet İşleri Başkanlığı, Turkey
     * 14 - Spiritual Administration of Muslims of Russia
     */
    private fun getAladhanMethodCode(method: CalculationMethod): Int {
        return when (method) {
            CalculationMethod.SHIA_ITHNA_ASHARI -> 0
            CalculationMethod.UNIVERSITY_OF_ISLAMIC_SCIENCES -> 1
            CalculationMethod.ISNA -> 2
            CalculationMethod.MUSLIM_WORLD_LEAGUE -> 3
            CalculationMethod.UMM_AL_QURA -> 4
            CalculationMethod.EGYPTIAN_AUTHORITY -> 5
            CalculationMethod.INSTITUTE_OF_GEOPHYSICS_TEHRAN -> 7
            CalculationMethod.UAE_IACAD -> 8 // Gulf Region method
            CalculationMethod.MUIS -> 11
        }
    }
}

/**
 * Response model from Aladhan API
 */
@Serializable
data class AladhanApiResponse(
    val code: Int,
    val status: String,
    val data: AladhanData? = null
)

@Serializable
data class AladhanData(
    val timings: AladhanTimings,
    val date: AladhanDate,
    val meta: AladhanMeta
)

@Serializable
data class AladhanTimings(
    @SerialName("Fajr") val fajr: String,
    @SerialName("Sunrise") val sunrise: String,
    @SerialName("Dhuhr") val dhuhr: String,
    @SerialName("Asr") val asr: String,
    @SerialName("Sunset") val sunset: String = "",
    @SerialName("Maghrib") val maghrib: String,
    @SerialName("Isha") val isha: String,
    @SerialName("Imsak") val imsak: String = "",
    @SerialName("Midnight") val midnight: String = "",
    @SerialName("Firstthird") val firstthird: String = "",
    @SerialName("Lastthird") val lastthird: String = ""
)

@Serializable
data class AladhanDate(
    val readable: String,
    val timestamp: String,
    val gregorian: AladhanGregorian? = null,
    val hijri: AladhanHijri? = null
)

@Serializable
data class AladhanGregorian(
    val date: String,
    val format: String,
    val day: String,
    val weekday: AladhanWeekday? = null,
    val month: AladhanMonth? = null,
    val year: String,
    val designation: AladhanDesignation? = null,
    val lunarSighting: Boolean = false
)

@Serializable
data class AladhanDesignation(
    val abbreviated: String = "",
    val expanded: String = ""
)

@Serializable
data class AladhanHijri(
    val date: String,
    val format: String,
    val day: String,
    val weekday: AladhanWeekday? = null,
    val month: AladhanMonth? = null,
    val year: String,
    val designation: AladhanDesignation? = null,
    val holidays: List<String> = emptyList(),
    val adjustedHolidays: List<String> = emptyList(),
    val method: String? = null
)

@Serializable
data class AladhanWeekday(
    val en: String = "",
    val ar: String = ""
)

@Serializable
data class AladhanMonth(
    val number: Int = 0,
    val en: String = "",
    val ar: String = ""
)

@Serializable
data class AladhanMeta(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val method: AladhanMethodInfo? = null,
    val latitudeAdjustmentMethod: String? = null,
    val midnightMode: String? = null,
    val school: String? = null,
    val offset: AladhanOffset? = null
)

@Serializable
data class AladhanOffset(
    @SerialName("Imsak") val imsak: Int = 0,
    @SerialName("Fajr") val fajr: Int = 0,
    @SerialName("Sunrise") val sunrise: Int = 0,
    @SerialName("Dhuhr") val dhuhr: Int = 0,
    @SerialName("Asr") val asr: Int = 0,
    @SerialName("Maghrib") val maghrib: Int = 0,
    @SerialName("Sunset") val sunset: Int = 0,
    @SerialName("Isha") val isha: Int = 0,
    @SerialName("Midnight") val midnight: Int = 0
)

@Serializable
data class AladhanMethodInfo(
    val id: Int,
    val name: String,
    val params: AladhanMethodParams? = null,
    val location: AladhanLocation? = null
)

@Serializable
data class AladhanLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

@Serializable
data class AladhanMethodParams(
    @SerialName("Fajr") val fajr: kotlinx.serialization.json.JsonPrimitive? = null,
    @SerialName("Isha") val isha: kotlinx.serialization.json.JsonPrimitive? = null
)

/**
 * Parsed prayer times from Aladhan API
 */
data class AladhanPrayerTimes(
    val fajr: LocalTime?,
    val sunrise: LocalTime?,
    val dhuhr: LocalTime?,
    val asr: LocalTime?,
    val maghrib: LocalTime?,
    val isha: LocalTime?,
    val date: LocalDate,
    val method: CalculationMethod
)
