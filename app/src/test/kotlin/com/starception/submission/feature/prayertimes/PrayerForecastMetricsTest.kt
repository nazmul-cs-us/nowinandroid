package com.starception.submission.feature.prayertimes

import com.starception.submission.feature.prayertimes.weather.PrayerWeatherVisual
import com.starception.submission.feature.prayertimes.weather.primaryPrayerWeatherVisual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrayerForecastMetricsTest {
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
}
