package com.starception.submission.feature.prayertimes.weather

import android.util.Log
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class CurrentWeather(
    val temperatureCelsius: Double,
    val precipitationProbability: Int,
    val weatherCode: Int,
    val isDay: Boolean,
)

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
                        "&current=temperature_2m,is_day,weather_code,precipitation_probability" +
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
                    weatherCode = current["weather_code"]
                        ?.jsonPrimitive?.intOrNull
                        ?: 0,
                    isDay = current["is_day"]?.jsonPrimitive?.intOrNull != 0,
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
}
