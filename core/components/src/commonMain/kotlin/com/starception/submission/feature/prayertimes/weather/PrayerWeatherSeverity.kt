/*
 * Copyright 2022 The Android Open Source Project
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

package com.starception.submission.feature.prayertimes.weather

/**
 * How severe the weather is for a prayer, from the forecast summary.
 *
 * Split out of AnimatedPrayerWeatherIcon so the shared tile can decide whether a
 * condition is worth flagging. Deciding is arithmetic on the summary text and
 * the user's thresholds; drawing it is animated vector drawables and BlendMode,
 * which stay on Android with the icon.
 */

enum class PrayerWeatherVisual {
    Rain,
    Heat,
    Humidity,
}

enum class WeatherThresholdLevel {
    Normal,
    Alert,
    Severe,
}

enum class MeteoconStyle {
    Fill,
    Flat,
    Monochrome,
}


/** Returns every noteworthy visual in preparation-first priority order. */
fun prayerWeatherVisuals(summary: String?): List<PrayerWeatherVisual> {
    if (summary.isNullOrBlank()) return emptyList()
    return buildList {
        if (summary.contains("rain", ignoreCase = true)) add(PrayerWeatherVisual.Rain)
        if (summary.contains("hot", ignoreCase = true) || '°' in summary) {
            add(PrayerWeatherVisual.Heat)
        }
        if (summary.contains("humidity", ignoreCase = true)) {
            add(PrayerWeatherVisual.Humidity)
        }
    }
}

/** The first actionable condition remains the fallback for compact static surfaces. */
fun primaryPrayerWeatherVisual(summary: String?): PrayerWeatherVisual? =
    prayerWeatherVisuals(summary).firstOrNull()

fun temperatureThresholdLevel(
    value: Double,
    threshold: Int,
): WeatherThresholdLevel = thresholdLevel(value, threshold, severeDelta = 5)

fun humidityThresholdLevel(
    value: Int,
    threshold: Int,
): WeatherThresholdLevel = thresholdLevel(value.toDouble(), threshold, severeDelta = 15)

fun rainThresholdLevel(
    value: Int,
    threshold: Int,
): WeatherThresholdLevel = thresholdLevel(value.toDouble(), threshold, severeDelta = 30)

/** Visual feedback for the threshold value currently being edited in settings. */
fun weatherThresholdPreviewLevel(
    visual: PrayerWeatherVisual,
    value: Int,
): WeatherThresholdLevel = when (visual) {
    PrayerWeatherVisual.Rain -> when {
        value >= 60 -> WeatherThresholdLevel.Severe
        value >= 30 -> WeatherThresholdLevel.Alert
        else -> WeatherThresholdLevel.Normal
    }
    PrayerWeatherVisual.Humidity -> when {
        value >= 75 -> WeatherThresholdLevel.Severe
        value >= 50 -> WeatherThresholdLevel.Alert
        else -> WeatherThresholdLevel.Normal
    }
    PrayerWeatherVisual.Heat -> when {
        value >= 38 -> WeatherThresholdLevel.Severe
        value >= 30 -> WeatherThresholdLevel.Alert
        else -> WeatherThresholdLevel.Normal
    }
}

fun prayerWeatherThresholdLevel(
    summary: String?,
    thresholds: PrayerWeatherThresholds,
): WeatherThresholdLevel = primaryPrayerWeatherVisual(summary)?.let { visual ->
    prayerWeatherThresholdLevel(summary, thresholds, visual)
} ?: WeatherThresholdLevel.Normal

/** Uses the strongest active condition to prioritize time-sensitive guidance. */
fun highestPrayerWeatherThresholdLevel(
    summary: String?,
    thresholds: PrayerWeatherThresholds,
): WeatherThresholdLevel = prayerWeatherVisuals(summary)
    .maxOfOrNull { visual ->
        prayerWeatherThresholdLevel(summary, thresholds, visual)
    } ?: WeatherThresholdLevel.Normal

/**
 * Severe weather surfaces almost immediately, ordinary threshold alerts wait
 * long enough for the home screen to settle, and provider-only advisories keep
 * the original five-second pacing.
 */
fun prayerWeatherWarningDelayMillis(
    summary: String?,
    thresholds: PrayerWeatherThresholds,
): Long = when (highestPrayerWeatherThresholdLevel(summary, thresholds)) {
    WeatherThresholdLevel.Severe -> 1_250L
    WeatherThresholdLevel.Alert -> 3_000L
    WeatherThresholdLevel.Normal -> 5_000L
}

/** Resolves severity independently for each condition in a multi-threshold forecast. */
fun prayerWeatherThresholdLevel(
    summary: String?,
    thresholds: PrayerWeatherThresholds,
    visual: PrayerWeatherVisual,
): WeatherThresholdLevel {
    if (summary.isNullOrBlank()) return WeatherThresholdLevel.Normal
    val conditions = summary.split('·').map(String::trim)
    return when (visual) {
        PrayerWeatherVisual.Rain -> conditions
            .firstOrNull { it.contains("rain", ignoreCase = true) }
            ?.firstNumber()
            ?.let { rainThresholdLevel(it.toInt(), thresholds.rainProbability) }
        PrayerWeatherVisual.Heat -> conditions
            .firstOrNull { it.contains("hot", ignoreCase = true) || '°' in it }
            ?.firstNumber()
            ?.let { temperatureThresholdLevel(it, thresholds.temperatureCelsius) }
        PrayerWeatherVisual.Humidity -> conditions
            .firstOrNull { it.contains("humidity", ignoreCase = true) }
            ?.firstNumber()
            ?.let { humidityThresholdLevel(it.toInt(), thresholds.humidity) }
    } ?: WeatherThresholdLevel.Normal
}

private fun thresholdLevel(
    value: Double,
    threshold: Int,
    severeDelta: Int,
): WeatherThresholdLevel = when {
    value >= threshold + severeDelta -> WeatherThresholdLevel.Severe
    value >= threshold -> WeatherThresholdLevel.Alert
    else -> WeatherThresholdLevel.Normal
}

private fun String.firstNumber(): Double? = Regex("""\d+(?:\.\d+)?""")
    .find(this)
    ?.value
    ?.toDoubleOrNull()
