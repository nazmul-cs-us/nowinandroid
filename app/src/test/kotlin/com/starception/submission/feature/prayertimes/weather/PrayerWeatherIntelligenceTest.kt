/*
 * Copyright 2026 The Android Open Source Project
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PrayerWeatherIntelligenceTest {
    @Test
    fun normalConditions_doNotCreateInsight() {
        val insight = PrayerWeatherIntelligence.create(
            prayerName = "Asr",
            forecast = forecast(temperature = 30.0, rain = 10, humidity = 50),
        )

        assertNull(insight)
    }

    @Test
    fun rainAtThreshold_createsTravelAdvice() {
        val insight = PrayerWeatherIntelligence.create(
            prayerName = "Maghrib",
            forecast = forecast(temperature = 29.0, rain = 25, humidity = 50),
        )

        assertEquals("Maghrib forecast · Rain 25%", insight?.compactText)
        assertTrue(insight?.advice?.contains("rain protection") == true)
    }

    @Test
    fun humidityAboveFifty_createsInsight() {
        val insight = PrayerWeatherIntelligence.create(
            prayerName = "Fajr",
            forecast = forecast(temperature = 25.0, rain = 5, humidity = 51),
        )

        assertEquals("Fajr forecast · High humidity 51%", insight?.compactText)
    }

    @Test
    fun customThresholds_areUsed() {
        val insight = PrayerWeatherIntelligence.create(
            prayerName = "Isha",
            forecast = forecast(temperature = 33.0, rain = 20, humidity = 60),
            thresholds = PrayerWeatherThresholds(
                rainProbability = 75,
                humidity = 70,
                temperatureCelsius = 32,
            ),
        )

        assertEquals("Isha forecast · Hot 33°C", insight?.compactText)
    }

    @Test
    fun severeConditions_combineIntoOneConciseInsight() {
        val insight = PrayerWeatherIntelligence.create(
            prayerName = "Dhuhr",
            forecast = forecast(temperature = 41.7, rain = 65, humidity = 84),
        )

        assertEquals(
            "Rain 65% · High humidity 84% · Hot 41°C",
            insight?.summary,
        )
        assertTrue(insight?.advice?.contains("water and rain protection") == true)
    }

    @Test
    fun warningDelay_usesStrongestCrossedThreshold() {
        val thresholds = PrayerWeatherThresholds()

        assertEquals(
            1_250L,
            prayerWeatherWarningDelayMillis(
                "Rain 25% · High humidity 51% · Hot 43°C",
                thresholds,
            ),
        )
        assertEquals(
            3_000L,
            prayerWeatherWarningDelayMillis("Rain 25%", thresholds),
        )
        assertEquals(
            5_000L,
            prayerWeatherWarningDelayMillis("Rain 0%", thresholds),
        )
    }

    private fun forecast(
        temperature: Double,
        rain: Int,
        humidity: Int,
    ) = PrayerWeatherForecast(
        dateTime = LocalDateTime.of(2026, 8, 12, 12, 0),
        temperatureCelsius = temperature,
        precipitationProbability = rain,
        relativeHumidity = humidity,
        weatherCode = 0,
    )
}
