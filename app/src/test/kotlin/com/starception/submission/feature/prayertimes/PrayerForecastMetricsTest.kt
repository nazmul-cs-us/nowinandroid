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

package com.starception.submission.feature.prayertimes

import com.starception.submission.feature.prayertimes.weather.PrayerWeatherVisual
import com.starception.submission.feature.prayertimes.weather.prayerWeatherVisuals
import com.starception.submission.feature.prayertimes.weather.primaryPrayerWeatherVisual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerForecastMetricsTest {
    @Test
    fun weatherReplacesBellForCurrentAndNextPrayerOnly() {
        assertTrue(shouldReplacePrayerBellWithWeather("Current", prayerTimeEditMode = false))
        assertTrue(shouldReplacePrayerBellWithWeather("Next", prayerTimeEditMode = false))
        assertFalse(shouldReplacePrayerBellWithWeather("Upcoming", prayerTimeEditMode = false))
    }

    @Test
    fun scheduleEditingAlwaysKeepsNotificationBell() {
        assertFalse(shouldReplacePrayerBellWithWeather("Current", prayerTimeEditMode = true))
        assertFalse(shouldReplacePrayerBellWithWeather("Next", prayerTimeEditMode = true))
    }

    @Test
    fun onlyOneWeatherTileIsSelectedWithCurrentPrayerPreferred() {
        val statuses = mapOf("Dhuhr" to "Current", "Asr" to "Next")

        assertEquals(
            "Dhuhr",
            selectPrayerWeatherAlertTarget(statuses.keys, statuses::getValue),
        )
    }

    @Test
    fun nextPrayerIsSelectedWhenCurrentPrayerHasNoWeatherAlert() {
        val statuses = mapOf("Asr" to "Next", "Maghrib" to "Upcoming")

        assertEquals(
            "Asr",
            selectPrayerWeatherAlertTarget(statuses.keys, statuses::getValue),
        )
    }

    @Test
    fun heatAndHumidity_keepsBothThresholdValues() {
        assertEquals(
            "44°C · 62% RH",
            formatPrayerForecastMetrics("High humidity 62% · Hot 44°C"),
        )
    }

    @Test
    fun allWarnings_fitValueFirstForecastFormat() {
        assertEquals(
            "41°C · 84% RH · 65% rain",
            formatPrayerForecastMetrics("Rain 65% · High humidity 84% · Hot 41°C"),
        )
    }

    @Test
    fun missingForecast_hasNoMetrics() {
        assertNull(formatPrayerForecastMetrics(null))
    }

    @Test
    fun oneThreshold_usesItsOwnVisual() {
        assertEquals(
            PrayerWeatherVisual.Humidity,
            primaryPrayerWeatherVisual("High humidity 74%"),
        )
    }

    @Test
    fun severalThresholds_useOneMostActionableVisual() {
        assertEquals(
            PrayerWeatherVisual.Rain,
            primaryPrayerWeatherVisual("Rain 65% · High humidity 84% · Hot 41°C"),
        )
    }

    @Test
    fun severalThresholds_supplyEveryMorphingVisual() {
        assertEquals(
            listOf(
                PrayerWeatherVisual.Rain,
                PrayerWeatherVisual.Heat,
                PrayerWeatherVisual.Humidity,
            ),
            prayerWeatherVisuals("High humidity 84% · Hot 41°C · Rain 65%"),
        )
    }

    @Test
    fun compactForecast_keepsOnlyTheValueForItsPriorityIcon() {
        assertEquals(
            "65%",
            formatPrimaryPrayerForecastMetric("Rain 65% · High humidity 84% · Hot 41°C"),
        )
        assertEquals(
            "44°C",
            formatPrimaryPrayerForecastMetric("High humidity 62% · Hot 44°C"),
        )
        assertEquals(
            "74%",
            formatPrimaryPrayerForecastMetric("High humidity 74%"),
        )
    }

    @Test
    fun rotatingForecast_pairsEachIconWithItsOwnValue() {
        val summary = "Rain 65% · High humidity 84% · Hot 41°C"

        assertEquals("65%", formatPrayerForecastMetric(summary, PrayerWeatherVisual.Rain))
        assertEquals("41°C", formatPrayerForecastMetric(summary, PrayerWeatherVisual.Heat))
        assertEquals("84%", formatPrayerForecastMetric(summary, PrayerWeatherVisual.Humidity))
    }
}
