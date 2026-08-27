/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.shared.weather

import com.starception.submission.core.logging.SharedLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "CurrentConditions"

/**
 * Present weather at a place.
 *
 * [weatherCode] is a WMO code; `prayerSkyWeather` in :core:images groups it into
 * the family whose artwork gets shown.
 */
data class CurrentConditions(
    val temperatureCelsius: Double?,
    val weatherCode: Int?,
)

/**
 * A minimal Open-Meteo client, enough to pick the sky artwork.
 *
 * Deliberately not a port of the app's `CurrentWeatherRepository`, which is 453
 * lines of caching, forecast targeting and threshold analysis bound to Context,
 * java.time and SharedPreferences. That belongs in shared code eventually; this
 * asks the one question the home page's artwork needs and no more.
 *
 * Requests the same fields from the same endpoint as Android, so both platforms
 * see the same conditions rather than subtly different ones.
 */
object CurrentConditionsClient {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(latitude: Double, longitude: Double): CurrentConditions? {
        // Four decimal places, matching Android. Coordinates at full precision
        // would defeat any caching upstream and leak more location than the
        // forecast needs.
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${latitude.toFixed4()}&longitude=${longitude.toFixed4()}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m," +
            "is_day,weather_code,precipitation_probability" +
            "&temperature_unit=celsius"

        val body = httpGet(url) ?: return null

        return runCatching {
            val current = json.parseToJsonElement(body).jsonObject["current"]?.jsonObject
            CurrentConditions(
                temperatureCelsius = current?.get("temperature_2m")
                    ?.jsonPrimitive?.doubleOrNull,
                weatherCode = current?.get("weather_code")?.jsonPrimitive?.intOrNull,
            )
        }.onFailure {
            SharedLog.w(TAG, "Could not parse weather response: ${it.message}")
        }.getOrNull()
    }
}

/**
 * Formats to four decimal places without `String.format`, which is JVM-only.
 */
private fun Double.toFixed4(): String {
    val scaled = kotlin.math.round(this * 10_000.0).toLong()
    val whole = scaled / 10_000
    val fraction = kotlin.math.abs(scaled % 10_000)
    val sign = if (this < 0 && whole == 0L) "-" else ""
    return "$sign$whole.${fraction.toString().padStart(4, '0')}"
}

/**
 * A plain GET returning the body, or `null` on any failure.
 *
 * `expect` rather than Ktor: this is one request, and adding a networking stack
 * to the project is a decision for when the news and sync layers move over, not
 * something to settle for a weather code. Android's own weather code already
 * uses a raw connection rather than Retrofit, so this mirrors it.
 */
internal expect suspend fun httpGet(url: String): String?
