package com.starception.submission.feature.prayertimes.weather

import android.content.Context
import android.util.Log
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.PrayerTimeOffsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class CurrentWeather(
    val temperatureCelsius: Double,
    val precipitationProbability: Int,
    val relativeHumidity: Int,
    val weatherCode: Int,
    val isDay: Boolean,
    /** Null when the provider omits it; the UI drops the "feels like" line then. */
    val apparentTemperatureCelsius: Double? = null,
)

data class PrayerWeatherForecast(
    val dateTime: LocalDateTime,
    val temperatureCelsius: Double,
    val precipitationProbability: Int,
    val relativeHumidity: Int,
    val weatherCode: Int,
)

data class PrayerWeatherInsight(
    val prayerName: String,
    val summary: String,
    val advice: String,
) {
    val compactText: String
        get() = "$prayerName forecast · $summary"

    val tileText: String
        get() {
            val conciseSummary = summary.replace("High humidity", "Humidity")
            return "$prayerName · $conciseSummary"
        }

    val notificationLine: String
        get() = "Weather • $summary"
}

data class PrayerForecastTarget(
    val prayerName: String,
    val date: LocalDate,
    val time: LocalTime,
)

object PrayerWeatherThresholdStore {
    private const val PREFS = "prayer_weather_preferences"
    private const val RAIN = "rain_probability_threshold"
    private const val HUMIDITY = "humidity_threshold"
    private const val TEMPERATURE = "temperature_threshold_celsius"

    fun load(context: Context): PrayerWeatherThresholds {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return PrayerWeatherThresholds(
            rainProbability = prefs.getInt(RAIN, 25).coerceIn(0, 100),
            humidity = prefs.getInt(HUMIDITY, 50).coerceIn(0, 99),
            temperatureCelsius = prefs.getInt(TEMPERATURE, 38).coerceIn(20, 50),
        )
    }

    fun save(context: Context, thresholds: PrayerWeatherThresholds) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(RAIN, thresholds.rainProbability.coerceIn(0, 100))
            .putInt(HUMIDITY, thresholds.humidity.coerceIn(0, 99))
            .putInt(TEMPERATURE, thresholds.temperatureCelsius.coerceIn(20, 50))
            .apply()
    }
}

/** Selects the next exact prayer occurrence, including offsets and day rollover. */
fun getUpcomingPrayerForecastTarget(
    prayerTimes: DayPrayerTimes,
    timeOffsets: PrayerTimeOffsets,
    now: LocalDateTime = LocalDateTime.now(),
): PrayerForecastTarget {
    val today = now.toLocalDate()
    val prayerOccurrences = listOf(
        "Fajr" to (prayerTimes.fajr to timeOffsets.fajr),
        "Dhuhr" to (prayerTimes.dhuhr to timeOffsets.dhuhr),
        "Asr" to (prayerTimes.asr to timeOffsets.asr),
        "Maghrib" to (prayerTimes.maghrib to timeOffsets.maghrib),
        "Isha" to (prayerTimes.isha to timeOffsets.isha),
    ).map { (name, timeAndOffset) ->
        val occurrence = LocalDateTime.of(today, timeAndOffset.first)
            .plusMinutes(timeAndOffset.second.toLong())
        PrayerForecastTarget(name, occurrence.toLocalDate(), occurrence.toLocalTime())
    }
    return prayerOccurrences.firstOrNull {
        LocalDateTime.of(it.date, it.time).isAfter(now)
    } ?: run {
        val tomorrowFajr = LocalDateTime.of(today.plusDays(1), prayerTimes.fajr)
            .plusMinutes(timeOffsets.fajr.toLong())
        PrayerForecastTarget(
            prayerName = "Fajr",
            date = tomorrowFajr.toLocalDate(),
            time = tomorrowFajr.toLocalTime(),
        )
    }
}

/** Selects the exact prayer occurrence represented by Prayer Now. */
fun getCurrentPrayerForecastTarget(
    prayerTimes: DayPrayerTimes,
    timeOffsets: PrayerTimeOffsets,
    now: LocalDateTime = LocalDateTime.now(),
): PrayerForecastTarget {
    val today = now.toLocalDate()
    val prayerOccurrences = listOf(
        "Fajr" to (prayerTimes.fajr to timeOffsets.fajr),
        "Dhuhr" to (prayerTimes.dhuhr to timeOffsets.dhuhr),
        "Asr" to (prayerTimes.asr to timeOffsets.asr),
        "Maghrib" to (prayerTimes.maghrib to timeOffsets.maghrib),
        "Isha" to (prayerTimes.isha to timeOffsets.isha),
    ).map { (name, timeAndOffset) ->
        val occurrence = LocalDateTime.of(today, timeAndOffset.first)
            .plusMinutes(timeAndOffset.second.toLong())
        PrayerForecastTarget(name, occurrence.toLocalDate(), occurrence.toLocalTime())
    }
    return prayerOccurrences.lastOrNull {
        !LocalDateTime.of(it.date, it.time).isAfter(now)
    } ?: run {
        val previousIsha = LocalDateTime.of(today.minusDays(1), prayerTimes.isha)
            .plusMinutes(timeOffsets.isha.toLong())
        PrayerForecastTarget("Isha", previousIsha.toLocalDate(), previousIsha.toLocalTime())
    }
}

/** Turns an hourly forecast into guidance only when conditions deserve attention. */
object PrayerWeatherIntelligence {
    fun create(
        prayerName: String,
        forecast: PrayerWeatherForecast,
        thresholds: PrayerWeatherThresholds = PrayerWeatherThresholds(),
    ): PrayerWeatherInsight? {
        val rainWeatherCode = forecast.weatherCode in 51..67 ||
            forecast.weatherCode in 80..82 ||
            forecast.weatherCode in 95..99
        val rainLikely = rainWeatherCode ||
            forecast.precipitationProbability >= thresholds.rainProbability
        val highHumidity = forecast.relativeHumidity > thresholds.humidity
        val highTemperature = forecast.temperatureCelsius >= thresholds.temperatureCelsius

        if (!rainLikely && !highHumidity && !highTemperature) return null

        val conditions = buildList {
            if (rainLikely) add("Rain ${forecast.precipitationProbability}%")
            if (highHumidity) add("High humidity ${forecast.relativeHumidity}%")
            if (highTemperature) add("Hot ${forecast.temperatureCelsius.toInt()}°C")
        }
        val advice = when {
            rainLikely && highTemperature -> "Allow extra travel time; carry water and rain protection."
            rainLikely -> "Allow extra travel time and carry rain protection."
            highTemperature -> "Carry water and limit prolonged exposure to direct sunlight."
            else -> "Allow a little extra time to arrive comfortably."
        }
        return PrayerWeatherInsight(
            prayerName = prayerName,
            summary = conditions.joinToString(" · "),
            advice = advice,
        )
    }
}

/** Lightweight current-condition client with a short in-memory cache. */
object CurrentWeatherRepository {
    private const val CACHE_DURATION_MS = 15 * 60 * 1_000L
    private const val STALE_CACHE_LIMIT_MS = 6 * 60 * 60 * 1_000L
    private const val TAG = "CurrentWeather"

    private data class CacheEntry(
        val latitudeBucket: Int,
        val longitudeBucket: Int,
        val weather: CurrentWeather,
        val fetchedAtMillis: Long,
    )

    @Volatile
    private var cache: CacheEntry? = null

    private data class ForecastCacheEntry(
        val latitudeBucket: Int,
        val longitudeBucket: Int,
        val forecasts: List<PrayerWeatherForecast>,
        val fetchedAtMillis: Long,
    )

    @Volatile
    private var forecastCache: ForecastCacheEntry? = null

    suspend fun get(latitude: Double, longitude: Double): CurrentWeather? =
        withContext(Dispatchers.IO) {
            val latitudeBucket = (latitude * 100).toInt()
            val longitudeBucket = (longitude * 100).toInt()
            val now = System.currentTimeMillis()
            val cached = cache
            val sameArea = cached?.latitudeBucket == latitudeBucket &&
                cached.longitudeBucket == longitudeBucket

            if (cached != null && sameArea && now - cached.fetchedAtMillis < CACHE_DURATION_MS) {
                return@withContext cached.weather
            }

            try {
                val latitudeValue = String.format(Locale.US, "%.4f", latitude)
                val longitudeValue = String.format(Locale.US, "%.4f", longitude)
                val endpoint = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=$latitudeValue&longitude=$longitudeValue" +
                        "&current=temperature_2m,apparent_temperature,relative_humidity_2m," +
                        "is_day,weather_code,precipitation_probability" +
                        "&temperature_unit=celsius",
                )
                val connection = endpoint.openConnection() as HttpsURLConnection
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "Starception-Android/1.0")

                val response = try {
                    if (connection.responseCode !in 200..299) {
                        error("Weather request failed with HTTP ${connection.responseCode}")
                    }
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }

                val current = Json.parseToJsonElement(response)
                    .jsonObject["current"]
                    ?.jsonObject
                    ?: error("Weather response has no current conditions")
                val weather = CurrentWeather(
                    temperatureCelsius = current["temperature_2m"]
                        ?.jsonPrimitive?.doubleOrNull
                        ?: error("Weather response has no temperature"),
                    precipitationProbability = current["precipitation_probability"]
                        ?.jsonPrimitive?.intOrNull
                        ?.coerceIn(0, 100)
                        ?: 0,
                    relativeHumidity = current["relative_humidity_2m"]
                        ?.jsonPrimitive?.intOrNull
                        ?.coerceIn(0, 100)
                        ?: 0,
                    weatherCode = current["weather_code"]
                        ?.jsonPrimitive?.intOrNull
                        ?: 0,
                    isDay = current["is_day"]?.jsonPrimitive?.intOrNull != 0,
                    apparentTemperatureCelsius = current["apparent_temperature"]
                        ?.jsonPrimitive?.doubleOrNull,
                )
                cache = CacheEntry(
                    latitudeBucket = latitudeBucket,
                    longitudeBucket = longitudeBucket,
                    weather = weather,
                    fetchedAtMillis = now,
                )
                weather
            } catch (error: Exception) {
                Log.w(TAG, "Unable to load current conditions", error)
                if (
                    cached != null &&
                    sameArea &&
                    now - cached.fetchedAtMillis < STALE_CACHE_LIMIT_MS
                ) {
                    cached.weather
                } else {
                    null
                }
            }
        }

    /** Gets noteworthy conditions for the forecast hour nearest a prayer time. */
    suspend fun getPrayerInsight(
        latitude: Double,
        longitude: Double,
        prayerName: String,
        prayerDate: LocalDate,
        prayerTime: LocalTime,
        forceRefresh: Boolean = false,
        thresholds: PrayerWeatherThresholds = PrayerWeatherThresholds(),
    ): PrayerWeatherInsight? = withContext(Dispatchers.IO) {
        val forecasts = getHourlyForecast(latitude, longitude, forceRefresh)
            ?: return@withContext null
        val target = LocalDateTime.of(prayerDate, prayerTime)
        val nearest = forecasts.minByOrNull {
            kotlin.math.abs(java.time.Duration.between(target, it.dateTime).toMinutes())
        }?.takeIf {
            kotlin.math.abs(java.time.Duration.between(target, it.dateTime).toMinutes()) <= 60
        } ?: return@withContext null
        PrayerWeatherIntelligence.create(prayerName, nearest, thresholds)
    }

    /**
     * Forecast for the hour nearest each of [times], keyed by prayer name.
     *
     * [getPrayerInsight] answers "is anything noteworthy about this prayer's weather",
     * which is the wrong question for a display that wants an icon and a temperature
     * for every prayer regardless of whether it crosses a threshold. This returns the
     * raw hourly reading instead, and does it in a single fetch for all five prayers
     * rather than one round trip each.
     *
     * A prayer is omitted when no forecast hour lands within an hour of it, which is
     * how yesterday's cached window degrades rather than reporting a wrong sky.
     */
    suspend fun getPrayerForecasts(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
        times: Map<String, LocalTime>,
    ): Map<String, PrayerWeatherForecast> = withContext(Dispatchers.IO) {
        val forecasts = getHourlyForecast(latitude, longitude, forceRefresh = false)
            ?: return@withContext emptyMap()

        times.mapNotNull { (name, time) ->
            val target = LocalDateTime.of(date, time)
            forecasts
                .minByOrNull {
                    kotlin.math.abs(java.time.Duration.between(target, it.dateTime).toMinutes())
                }
                ?.takeIf {
                    kotlin.math.abs(
                        java.time.Duration.between(target, it.dateTime).toMinutes(),
                    ) <= 60
                }
                ?.let { name to it }
        }.toMap()
    }

    private fun getHourlyForecast(
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean,
    ): List<PrayerWeatherForecast>? {
        val latitudeBucket = (latitude * 100).toInt()
        val longitudeBucket = (longitude * 100).toInt()
        val now = System.currentTimeMillis()
        val cached = forecastCache
        val sameArea = cached?.latitudeBucket == latitudeBucket &&
            cached.longitudeBucket == longitudeBucket
        if (
            !forceRefresh && cached != null && sameArea &&
            now - cached.fetchedAtMillis < CACHE_DURATION_MS
        ) {
            return cached.forecasts
        }

        return try {
            val latitudeValue = String.format(Locale.US, "%.4f", latitude)
            val longitudeValue = String.format(Locale.US, "%.4f", longitude)
            val endpoint = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitudeValue&longitude=$longitudeValue" +
                    "&hourly=temperature_2m,relative_humidity_2m," +
                    "precipitation_probability,weather_code" +
                    "&temperature_unit=celsius&timezone=auto&past_days=1&forecast_days=3",
            )
            val connection = endpoint.openConnection() as HttpsURLConnection
            connection.connectTimeout = 3_000
            connection.readTimeout = 3_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Starception-Android/1.0")
            val response = try {
                if (connection.responseCode !in 200..299) {
                    error("Weather forecast request failed with HTTP ${connection.responseCode}")
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }

            val hourly = Json.parseToJsonElement(response).jsonObject["hourly"]?.jsonObject
                ?: error("Weather response has no hourly forecast")
            val times = hourly["time"]?.jsonArray ?: error("Forecast has no times")
            val temperatures = hourly["temperature_2m"]?.jsonArray
                ?: error("Forecast has no temperatures")
            val humidities = hourly["relative_humidity_2m"]?.jsonArray
                ?: error("Forecast has no humidity")
            val precipitation = hourly["precipitation_probability"]?.jsonArray
                ?: error("Forecast has no precipitation probability")
            val weatherCodes = hourly["weather_code"]?.jsonArray
                ?: error("Forecast has no weather codes")
            val count = minOf(
                times.size,
                temperatures.size,
                humidities.size,
                precipitation.size,
                weatherCodes.size,
            )
            val forecasts = buildList {
                repeat(count) { index ->
                    val dateTime = runCatching {
                        LocalDateTime.parse(times[index].jsonPrimitive.content)
                    }.getOrNull() ?: return@repeat
                    val temperature = temperatures[index].jsonPrimitive.doubleOrNull
                        ?: return@repeat
                    add(
                        PrayerWeatherForecast(
                            dateTime = dateTime,
                            temperatureCelsius = temperature,
                            precipitationProbability = precipitation[index].jsonPrimitive.intOrNull
                                ?.coerceIn(0, 100) ?: 0,
                            relativeHumidity = humidities[index].jsonPrimitive.intOrNull
                                ?.coerceIn(0, 100) ?: 0,
                            weatherCode = weatherCodes[index].jsonPrimitive.intOrNull ?: 0,
                        ),
                    )
                }
            }
            forecastCache = ForecastCacheEntry(
                latitudeBucket = latitudeBucket,
                longitudeBucket = longitudeBucket,
                forecasts = forecasts,
                fetchedAtMillis = now,
            )
            forecasts
        } catch (error: Exception) {
            Log.w(TAG, "Unable to load hourly prayer forecast", error)
            if (
                cached != null && sameArea &&
                now - cached.fetchedAtMillis < STALE_CACHE_LIMIT_MS
            ) {
                cached.forecasts
            } else {
                null
            }
        }
    }
}
