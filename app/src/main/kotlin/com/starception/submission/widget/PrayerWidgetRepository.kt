/*
 * Copyright 2026 Starception
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

package com.starception.submission.widget

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateFormat
import android.util.Log
import com.starception.submission.feature.prayertimes.SmartContentUtils
import com.starception.submission.feature.prayertimes.prayerWindowProgress
import com.starception.submission.feature.prayertimes.weather.CurrentWeatherRepository
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.Location
import com.starception.submission.prayer.model.PrayerTimeOffsets
import com.starception.submission.prayer.repository.PrayerSettingsRepository
import com.starception.submission.prayer.service.PrayerTimeCalculatorService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.chrono.HijrahChronology
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** A widget update must not hang on the network; prayer times matter, weather does not. */
private const val WEATHER_TIMEOUT_MS = 4_000L

/**
 * Widget-side view of one prayer. Times arrive pre-formatted because a Glance
 * composable renders into RemoteViews on the launcher's process and cannot run
 * locale/format work at draw time.
 */
internal data class WidgetPrayer(
    val name: String,
    val time: String,
    val isNext: Boolean,
    val isPast: Boolean,
    /** Meteocon for the forecast hour nearest this prayer; null when unknown. */
    val weatherIcon: Bitmap? = null,
    /**
     * Frames of the same Meteocon for ViewFlipper playback. Only populated for the next
     * prayer: every frame is a bitmap crossing Binder, so animating all five would blow
     * the RemoteViews budget for no real gain — one focal animation reads better than
     * five competing ones anyway.
     */
    val weatherFrames: List<Bitmap> = emptyList(),
    /** Rounded degrees for that same hour, e.g. "38°"; null when unknown. */
    val temperature: String? = null,
)

internal sealed interface PrayerWidgetState {

    /**
     * Shown before the app has ever resolved a location — the widget can only offer a
     * tap target into the app, since prayer times are undefined without coordinates.
     */
    data object Unavailable : PrayerWidgetState

    data class Available(
        val place: String,
        val dateLabel: String,
        val nextPrayer: WidgetPrayer,
        val countdown: String,
        val sunrise: String,
        val prayers: List<WidgetPrayer>,
        /**
         * The three lines the "Prayer now" tile shows, straight from the same generator:
         * a phase headline ("Go to Mosque for Fajr" / "Best Time to Pray Fajr" /
         * "Make Time for Fajr"), how long since that prayer began, and the next prayer
         * countdown. Null when no prayer is currently being tracked, e.g. more than
         * twelve hours after Isha, which is exactly when the tile shows nothing either.
         */
        val insight: PrayerInsight?,
        /**
         * Position between the last prayer and the next, 0..1. The same value the
         * "Prayer now" tile draws its timeline from.
         */
        val windowProgress: Float?,
    ) : PrayerWidgetState
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PrayerWidgetEntryPoint {
    fun prayerSettingsRepository(): PrayerSettingsRepository
    fun prayerTimeCalculatorService(): PrayerTimeCalculatorService
}

private const val TAG = "PrayerWidget"

/**
 * Loads today's prayer times for the home-screen widget.
 *
 * The widget runs from a broadcast, so this deliberately never touches GPS or the
 * network. It reads what the app already persisted:
 *
 *  1. Today's cached prayer times, if the cache is still for today — the common path.
 *  2. Otherwise the cached location plus the user's saved calculation settings, fed
 *     back through the same calculator the app uses, so the widget rolls over to the
 *     new day on its own even if the app has not been opened.
 *
 * Only when neither is available (fresh install, location never resolved) does it
 * report [PrayerWidgetState.Unavailable].
 */
internal suspend fun loadPrayerWidgetState(context: Context): PrayerWidgetState {
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        PrayerWidgetEntryPoint::class.java,
    )
    val repository = entryPoint.prayerSettingsRepository()

    val prayerTimes = repository.getCachedPrayerTimes()
        ?: recalculateForToday(repository, entryPoint.prayerTimeCalculatorService())
        ?: return PrayerWidgetState.Unavailable

    return prayerTimes.toWidgetState(
        context = context,
        weather = loadPrayerWeather(context, prayerTimes),
        insight = prayerTimes.toInsight(repository),
    )
}

/**
 * Meteocon and temperature for the hour nearest each prayer.
 *
 * Weather is strictly decoration here: the network call is capped and every failure
 * degrades to an empty map, so the widget still renders its prayer times when the
 * forecast is unavailable. Open-Meteo responses are cached by the repository, so
 * repeated widget updates within the cache window cost nothing.
 */
private suspend fun loadPrayerWeather(
    context: Context,
    prayerTimes: DayPrayerTimes,
): Map<String, WidgetWeather> = try {
    withTimeout(WEATHER_TIMEOUT_MS) {
        val prayers = prayerTimes.getActualPrayers().associate { it.name to it.time }
        CurrentWeatherRepository.getPrayerForecasts(
            latitude = prayerTimes.location.latitude,
            longitude = prayerTimes.location.longitude,
            date = LocalDate.now(),
            times = prayers,
        ).mapValues { (_, forecast) ->
            // Open-Meteo's hourly block carries no is_day flag, so daylight is derived
            // from the prayer schedule itself: between sunrise and maghrib is day.
            // Getting this wrong swaps a sun icon for a moon.
            val isDay = forecast.dateTime.toLocalTime()
                .let { it >= prayerTimes.sunrise && it < prayerTimes.maghrib }

            WidgetWeather(
                icon = WidgetMeteocons.forWeather(context, forecast.weatherCode, isDay),
                frames = WidgetMeteocons.animationFrames(context, forecast.weatherCode, isDay),
                temperature = "${forecast.temperatureCelsius.roundToInt()}°",
            )
        }
    }
} catch (e: Exception) {
    Log.w(TAG, "Widget weather unavailable, rendering prayer times only", e)
    emptyMap()
}

/** Mirrors the "Prayer now" tile's headline, elapsed line and next-prayer line. */
internal data class PrayerInsight(
    val title: String,
    val elapsed: String,
    val nextPrayerInfo: String,
) {
    /** Prayer this insight is about, e.g. "Asr" — the last word of [elapsed]. */
    val caption: String
        get() = elapsed.substringAfterLast(' ')
}

private data class WidgetWeather(
    val icon: Bitmap?,
    val frames: List<Bitmap>,
    val temperature: String,
)

private suspend fun recalculateForToday(
    repository: PrayerSettingsRepository,
    calculator: PrayerTimeCalculatorService,
): DayPrayerTimes? {
    val location = repository.getCachedLocation()
        ?: repository.getLoadedLocationPreferences().location
        ?: return null

    // Force the settings flow to finish loading before reading the combined snapshot;
    // in a cold process getSettings() would otherwise hand back bare defaults and the
    // widget would show times calculated with the wrong method.
    repository.getLoadedCalculationSettings()

    @Suppress("DEPRECATION")
    val settings = repository.getSettings()

    return try {
        calculator.calculatePrayerTimes(LocalDate.now(), location, settings)
    } catch (e: Exception) {
        Log.e(TAG, "Widget prayer time recalculation failed", e)
        null
    }
}

/**
 * Reuses the prayer screen's own content generator so the widget and the "Prayer now"
 * tile can never word the same moment differently.
 */
internal fun DayPrayerTimes.toInsight(repository: PrayerSettingsRepository): PrayerInsight? {
    val notifications = repository.getNotificationPreferences()

    return SmartContentUtils.getNotificationSyncContent(
        prayerTimes = this,
        currentTime = LocalTime.now(),
        // Deliberately zero. The generator applies offsets to whatever times it is
        // given, and these times came from the cache with offsets already applied —
        // passing them again would shift every prayer by twice the user's adjustment.
        timeOffsets = PrayerTimeOffsets(),
        goToMosqueDurationMinutes = { prayer ->
            when (prayer) {
                "Fajr" -> notifications.fajrGoToMosqueDuration
                "Dhuhr" -> notifications.dhuhrGoToMosqueDuration
                "Asr" -> notifications.asrGoToMosqueDuration
                "Maghrib" -> notifications.maghribGoToMosqueDuration
                "Isha" -> notifications.ishaGoToMosqueDuration
                else -> 20
            }
        },
    )?.let {
        PrayerInsight(
            title = it.title,
            elapsed = it.content,
            nextPrayerInfo = it.nextPrayerInfo,
        )
    }
}

private fun DayPrayerTimes.toWidgetState(
    context: Context,
    weather: Map<String, WidgetWeather>,
    insight: PrayerInsight?,
): PrayerWidgetState.Available {
    val formatter = timeFormatter(context)
    val now = LocalTime.now()

    val prayers = getActualPrayers().map { prayer ->
        WidgetPrayer(
            name = prayer.name,
            time = prayer.time.format(formatter),
            isNext = prayer.isNext,
            // A prayer that is "next" while already behind the clock is tomorrow's
            // Fajr rolling over, so it must not be dimmed as past.
            isPast = !prayer.isNext && prayer.time.isBefore(now),
            weatherIcon = weather[prayer.name]?.icon,
            weatherFrames = if (prayer.isNext) {
                weather[prayer.name]?.frames.orEmpty()
            } else {
                emptyList()
            },
            temperature = weather[prayer.name]?.temperature,
        )
    }
    val next = prayers.firstOrNull { it.isNext } ?: prayers.first()

    return PrayerWidgetState.Available(
        place = location.shortLabel(),
        dateLabel = hijriDateLabel(LocalDate.now()),
        nextPrayer = next,
        countdown = countdownTo(getActualPrayers().first { it.name == next.name }.time, now),
        sunrise = sunrise.format(formatter),
        prayers = prayers,
        insight = insight,
        windowProgress = prayerWindowProgress(this, now),
    )
}

/** Arabic weekday and Umm al-Qura date used beneath the widget's location header. */
private fun hijriDateLabel(date: LocalDate): String = runCatching {
    DateTimeFormatter
        .ofPattern("EEEE، d MMMM yyyy هـ", Locale.forLanguageTag("ar"))
        .withChronology(HijrahChronology.INSTANCE)
        .format(date)
}.getOrElse {
    date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
}

private fun timeFormatter(context: Context): DateTimeFormatter = DateTimeFormatter.ofPattern(
    if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
    Locale.getDefault(),
)

/**
 * Wall-clock distance to [target], wrapping past midnight so the hours after Isha
 * count down to tomorrow's Fajr instead of reporting a negative span.
 */
private fun countdownTo(target: LocalTime, now: LocalTime): String {
    val minutes = Duration.between(now, target).toMinutes().let { raw ->
        if (raw < 0) raw + Duration.ofDays(1).toMinutes() else raw
    }
    val hours = minutes / 60
    val remainder = minutes % 60

    return when {
        minutes <= 0L -> "now"
        hours == 0L -> "in ${remainder}m"
        remainder == 0L -> "in ${hours}h"
        else -> "in ${hours}h ${remainder}m"
    }
}

/**
 * The app's own [Location.getDisplayName] expands to "Area, City, Country (CC)", which
 * needs more width than a 2-cell widget has. Take the most specific name available and
 * stop there.
 */
private fun Location.shortLabel(): String = listOf(area, subLocality, city, administrativeArea, country)
    .firstOrNull { it.isNotBlank() }
    ?: "Prayer times"


/**
 * The current prayer insight, for widgets that render through the ported sample layouts.
 *
 * Those layouts load their own data rather than receiving [PrayerWidgetState], so this
 * gives them the same generator the prayer screen and the hero widget use. Returns null
 * when no prayer is being tracked, and callers should fall back to their own copy.
 */
internal suspend fun livePrayerInsight(context: Context): PrayerInsight? = try {
    val repository = EntryPointAccessors
        .fromApplication(context.applicationContext, PrayerWidgetEntryPoint::class.java)
        .prayerSettingsRepository()

    repository.getCachedPrayerTimes()?.toInsight(repository)
} catch (e: Exception) {
    Log.w(TAG, "Live prayer insight unavailable", e)
    null
}
