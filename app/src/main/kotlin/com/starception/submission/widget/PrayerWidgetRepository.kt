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
import com.starception.submission.feature.prayertimes.weather.CurrentWeather
import com.starception.submission.feature.prayertimes.weather.CurrentWeatherRepository
import com.starception.submission.core.data.repository.UserDataRepository
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.Location
import com.starception.submission.feature.prayertimes.utils.applyOffsetToTime
import com.starception.submission.prayer.model.PrayerTimeOffsets
import com.starception.submission.prayer.repository.PrayerSettingsRepository
import com.starception.submission.prayer.service.PrayerTimeCalculatorService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.chrono.HijrahChronology
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale
import kotlin.math.roundToInt

/** A widget update must not hang on the network; prayer times matter, weather does not. */
private const val WEATHER_TIMEOUT_MS = 4_000L
private const val CURRENT_WEATHER_CACHE_PREFS = "prayer_widget_current_weather"
private const val CURRENT_WEATHER_FRESH_MS = 15 * 60 * 1_000L
private const val CURRENT_WEATHER_STALE_MS = 6 * 60 * 60 * 1_000L
private val currentWeatherMutex = Mutex()
private val prayerWeatherMutex = Mutex()

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
    /** Short status used by the reference timeline, e.g. "18m ago" or "in 2h 10m". */
    val relativeLabel: String? = null,
    /** Meteocon for the forecast hour nearest this prayer; null when unknown. */
    val weatherIcon: Bitmap? = null,
    /** Rounded degrees for that same hour, e.g. "38°"; null when unknown. */
    val temperature: String? = null,
    /** Compact Open-Meteo condition label for the detailed widget header. */
    val weatherSummary: String? = null,
)

/**
 * What the sky diagram needs to draw today's sun path: the solar events as minutes of the
 * day, where the clock stands, and enough astronomy (latitude, day of year) to place each
 * prayer at the sun's true altitude. [hijriDay] gives the moon its phase after sunset.
 */
internal data class WidgetSky(
    val fajr: Int,
    val sunrise: Int,
    val dhuhr: Int,
    val asr: Int,
    val maghrib: Int,
    val isha: Int,
    val now: Int,
    val latitude: Double,
    val dayOfYear: Int,
    val hijriDay: Int,
)

/** The next solar event shown beside the refresh action in the detailed widget header. */
internal data class WidgetSolarEvent(
    val label: String,
    val time: String,
    val isSunset: Boolean,
    /** Dedicated Meteocons Fill artwork; it keeps its original multicolour palette. */
    val icon: Bitmap? = null,
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
        /** Actual conditions at refresh time; never substituted with a solar event. */
        val currentWeather: WidgetWeather?,
        val nextPrayer: WidgetPrayer,
        val countdown: String,
        val solarEvent: WidgetSolarEvent,
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
        /** A source-backed devotional reading for the tall dashboard widget. */
        val reminder: DailyReminder,
        /** Solar/prayer-derived palette for the animated foliage decorating tall cards. */
        val dayPhase: WidgetDayPhase,
        val daylightLabel: String,
        val nightLabel: String,
        val sky: WidgetSky,
        /**
         * Current position across the five prayer anchors, from Fajr (0f) to Isha (1f).
         * This keeps the day/night marker spatially aligned with the prayer journey above
         * it instead of treating noon as the centre regardless of today's prayer times.
         */
        val prayerTimelineProgress: Float,
    ) : PrayerWidgetState
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PrayerWidgetEntryPoint {
    fun userDataRepository(): UserDataRepository
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
/**
 * The last computed state, reused for a short window.
 *
 * A resize re-runs provideGlance with nothing about the prayers, weather or reminder
 * changed, yet computing them again cost ~0.8s — time during which the launcher shows the
 * previous rendering stretched to the new cells. Within [STATE_REUSE_MS] the state is
 * handed back as is; the countdown drifts by at most that long before the next update.
 */
private var cachedWidgetState: Pair<Long, PrayerWidgetState>? = null
private val cachedWidgetStateMutex = Mutex()
private const val STATE_REUSE_MS = 45_000L

internal suspend fun loadPrayerWidgetStateCached(context: Context): PrayerWidgetState =
    cachedWidgetStateMutex.withLock {
        val now = android.os.SystemClock.elapsedRealtime()
        cachedWidgetState?.takeIf { (at, state) ->
            state is PrayerWidgetState.Available && now - at < STATE_REUSE_MS
        }?.let { return@withLock it.second }
        loadPrayerWidgetState(context).also { cachedWidgetState = now to it }
    }

internal suspend fun loadPrayerWidgetState(context: Context): PrayerWidgetState {
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        PrayerWidgetEntryPoint::class.java,
    )
    val repository = entryPoint.prayerSettingsRepository()
    val calculator = entryPoint.prayerTimeCalculatorService()

    val basePrayerTimes = (
        repository.getCachedPrayerTimes()
            ?: recalculateForDate(repository, calculator, LocalDate.now())
        )
        ?: return PrayerWidgetState.Unavailable
    val timeOffsets = repository.getCalculationSettingsFromStorage().timeOffsets
    val prayerTimes = basePrayerTimes.withUserOffsets(timeOffsets)
    val now = LocalTime.now()
    val insight = prayerTimes.toInsight(repository)
    val actualPrayers = prayerTimes.getActualPrayers()
    val currentPrayerName = insight?.caption
        ?.takeIf { caption -> actualPrayers.any { it.name.equals(caption, ignoreCase = true) } }
        ?: actualPrayers.lastOrNull { it.time <= now }?.name
        // Before today's Fajr, the active prayer window is still last night's Isha.
        ?: "Isha"
    val tomorrowSunrise = if (now >= prayerTimes.maghrib) {
        recalculateForDate(repository, calculator, LocalDate.now().plusDays(1))
            ?.withUserOffsets(timeOffsets)
            ?.sunrise
    } else {
        null
    }

    val (weatherPair, reminder) = coroutineScope {
        val current = async { loadCurrentWeather(context, prayerTimes) }
        val forecasts = async { loadPrayerWeather(context, prayerTimes) }
        val devotional = async {
            DailyReminderRepository.load(
                context = context,
                offset = 0,
                prayerName = currentPrayerName,
            )
        }
        (current.await() to forecasts.await()) to devotional.await()
    }

    return prayerTimes.toWidgetState(
        context = context,
        currentWeather = weatherPair.first,
        weather = weatherPair.second,
        insight = insight,
        now = now,
        tomorrowSunrise = tomorrowSunrise,
        reminder = reminder,
    )
}

/**
 * The user's per-prayer adjustments, applied.
 *
 * The cache holds the astronomical result, not what the app displays: the screens apply
 * the offsets themselves when they render. The widget read the cache straight and so
 * showed times minutes apart from the app for anyone who had tuned their schedule — with
 * a +3m Dhuhr and a +15m Fajr set, the card was wrong by exactly those amounts. Applied
 * once, here, so everything downstream — the schedule, the countdown, the insight and the
 * weather lookup — works from the same times the app shows.
 */
private fun DayPrayerTimes.withUserOffsets(offsets: PrayerTimeOffsets): DayPrayerTimes = copy(
    fajr = applyOffsetToTime(fajr, offsets.fajr),
    sunrise = applyOffsetToTime(sunrise, offsets.sunrise),
    dhuhr = applyOffsetToTime(dhuhr, offsets.dhuhr),
    asr = applyOffsetToTime(asr, offsets.asr),
    maghrib = applyOffsetToTime(maghrib, offsets.maghrib),
    isha = applyOffsetToTime(isha, offsets.isha),
)

/**
 * Meteocon and temperature for the hour nearest each prayer.
 *
 * Weather is strictly decoration here: the network call is capped and every failure
 * degrades to an empty map, so the widget still renders its prayer times when the
 * forecast is unavailable. Open-Meteo responses are cached by the repository, so
 * repeated widget updates within the cache window cost nothing.
 */
private suspend fun loadCurrentWeather(
    context: Context,
    prayerTimes: DayPrayerTimes,
): WidgetWeather? = currentWeatherMutex.withLock {
    val latitude = prayerTimes.location.latitude
    val longitude = prayerTimes.location.longitude
    readCachedCurrentWeather(
        context = context,
        latitude = latitude,
        longitude = longitude,
        maximumAgeMs = CURRENT_WEATHER_FRESH_MS,
    )?.let { return@withLock it }

    val live = try {
        withTimeout(WEATHER_TIMEOUT_MS) {
            CurrentWeatherRepository.get(latitude = latitude, longitude = longitude)
        }
    } catch (e: Exception) {
        Log.w(TAG, "Widget current weather unavailable", e)
        null
    }

    if (live != null) {
        cacheCurrentWeather(context, latitude, longitude, live)
        return@withLock live.toWidgetWeather(context)
    }

    // App widget processes are routinely reclaimed. A disk-backed last-known value keeps
    // a transient provider error from turning the weather pill into unrelated solar data.
    readCachedCurrentWeather(
        context = context,
        latitude = latitude,
        longitude = longitude,
        maximumAgeMs = CURRENT_WEATHER_STALE_MS,
    )
}

private fun CurrentWeather.toWidgetWeather(context: Context): WidgetWeather = WidgetWeather(
    icon = WidgetMeteocons.forWeather(context, weatherCode, isDay),
    temperature = "${temperatureCelsius.roundToInt()}°",
    summary = weatherCode.widgetWeatherSummary(),
)

private fun cacheCurrentWeather(
    context: Context,
    latitude: Double,
    longitude: Double,
    weather: CurrentWeather,
) {
    context.getSharedPreferences(CURRENT_WEATHER_CACHE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString("latitude", latitude.toString())
        .putString("longitude", longitude.toString())
        .putFloat("temperature", weather.temperatureCelsius.toFloat())
        .putInt("weather_code", weather.weatherCode)
        .putBoolean("is_day", weather.isDay)
        .putLong("fetched_at", System.currentTimeMillis())
        .apply()
}

private fun readCachedCurrentWeather(
    context: Context,
    latitude: Double,
    longitude: Double,
    maximumAgeMs: Long,
): WidgetWeather? {
    val prefs = context.getSharedPreferences(CURRENT_WEATHER_CACHE_PREFS, Context.MODE_PRIVATE)
    val cachedLatitude = prefs.getString("latitude", null)?.toDoubleOrNull() ?: return null
    val cachedLongitude = prefs.getString("longitude", null)?.toDoubleOrNull() ?: return null
    val fetchedAt = prefs.getLong("fetched_at", 0L)
    val sameArea = kotlin.math.abs(cachedLatitude - latitude) < 0.02 &&
        kotlin.math.abs(cachedLongitude - longitude) < 0.02
    val age = System.currentTimeMillis() - fetchedAt
    if (!sameArea || fetchedAt == 0L || age !in 0..maximumAgeMs) return null

    val weatherCode = prefs.getInt("weather_code", Int.MIN_VALUE)
    if (weatherCode == Int.MIN_VALUE || !prefs.contains("temperature")) return null
    return WidgetWeather(
        icon = WidgetMeteocons.forWeather(
            context = context,
            weatherCode = weatherCode,
            isDay = prefs.getBoolean("is_day", true),
        ),
        temperature = "${prefs.getFloat("temperature", 0f).roundToInt()}°",
        summary = weatherCode.widgetWeatherSummary(),
    )
}

private suspend fun loadPrayerWeather(
    context: Context,
    prayerTimes: DayPrayerTimes,
): Map<String, WidgetWeather> = prayerWeatherMutex.withLock {
    try {
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
                    temperature = "${forecast.temperatureCelsius.roundToInt()}°",
                    summary = forecast.weatherCode.widgetWeatherSummary(),
                )
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Widget weather unavailable, rendering prayer times only", e)
        emptyMap()
    }
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

    /**
     * [nextPrayerInfo] worded the way the home carousel's "Prayer now" tile words it:
     * "Next • Asr in 42m" → "Next Prayer · Asr in 42m".
     *
     * The generator emits the notification's phrasing, where "Next •" is a prefix on a
     * line of its own. The tile drops that prefix and puts the countdown under a "Next
     * Prayer" heading instead — see SwipeableBigTiles.kt, which parses the same string
     * the same way. This keeps the widget reading as the tile's sibling rather than as a
     * notification that wandered onto the home screen.
     */
    val nextPrayerLine: String
        get() {
            val detail = nextPrayerInfo
                .substringAfter('•', nextPrayerInfo)
                .removePrefix("Next")
                .trim(' ', '·', '•')

            return if (detail.isBlank()) nextPrayerInfo else "Next Prayer · $detail"
        }
}

internal data class WidgetWeather(
    val icon: Bitmap?,
    val temperature: String,
    val summary: String,
)

private fun Int.widgetWeatherSummary(): String = when (this) {
    0 -> "Clear sky"
    1, 2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Foggy"
    in 51..57 -> "Drizzle"
    in 61..67, in 80..82 -> "Rain"
    in 71..77, 85, 86 -> "Snow"
    in 95..99 -> "Thunderstorms"
    else -> "Cloudy"
}

private suspend fun recalculateForDate(
    repository: PrayerSettingsRepository,
    calculator: PrayerTimeCalculatorService,
    date: LocalDate,
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
        calculator.calculatePrayerTimes(date, location, settings)
    } catch (e: Exception) {
        Log.e(TAG, "Widget prayer time recalculation failed for $date", e)
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
        // Deliberately zero: withUserOffsets has already applied the adjustments to
        // these times at the load site, and the generator would otherwise apply them a
        // second time, moving every prayer by twice what the user set.
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
    currentWeather: WidgetWeather?,
    weather: Map<String, WidgetWeather>,
    insight: PrayerInsight?,
    now: LocalTime,
    tomorrowSunrise: LocalTime?,
    reminder: DailyReminder,
): PrayerWidgetState.Available {
    val formatter = timeFormatter(context)

    val prayers = getActualPrayers().map { prayer ->
        val isPast = !prayer.isNext && prayer.time.isBefore(now)
        val minutesSince = if (isPast) {
            Duration.between(prayer.time, now).toMinutes().coerceAtLeast(0L)
        } else {
            0L
        }
        WidgetPrayer(
            name = prayer.name,
            time = prayer.time.format(formatter),
            isNext = prayer.isNext,
            // A prayer that is "next" while already behind the clock is tomorrow's
            // Fajr rolling over, so it must not be dimmed as past.
            isPast = isPast,
            relativeLabel = when {
                prayer.isNext -> countdownTo(prayer.time, now)
                // Recent prayers keep a useful elapsed label. Older completed prayers
                // use the reference's compact check treatment instead.
                isPast && minutesSince <= 150L -> "${durationLabel(minutesSince)} ago"
                else -> null
            },
            weatherIcon = weather[prayer.name]?.icon,
            temperature = weather[prayer.name]?.temperature,
            weatherSummary = weather[prayer.name]?.summary,
        )
    }
    val next = prayers.firstOrNull { it.isNext } ?: prayers.first()
    val showSunset = now >= sunrise && now < maghrib
    val solarEventWithoutArtwork = if (showSunset) {
        WidgetSolarEvent(
            label = "Sunset",
            time = maghrib.format(formatter),
            isSunset = true,
        )
    } else {
        WidgetSolarEvent(
            label = "Sunrise",
            // Once today's sun has set, the next relevant event is tomorrow's sunrise.
            // Fall back to today's clock time only if that one-off calculation fails.
            time = (tomorrowSunrise ?: sunrise).format(formatter),
            isSunset = false,
        )
    }
    val solarEvent = solarEventWithoutArtwork.copy(
        icon = WidgetMeteocons.forSolarEvent(context, solarEventWithoutArtwork.isSunset),
    )
    val daylightMinutes = Duration.between(sunrise, maghrib).toMinutes().coerceAtLeast(0L)
    val nightMinutes = (Duration.ofDays(1).toMinutes() - daylightMinutes).coerceAtLeast(0L)

    return PrayerWidgetState.Available(
        place = location.shortLabel(),
        dateLabel = hijriDateLabel(LocalDate.now()),
        currentWeather = currentWeather,
        nextPrayer = next,
        countdown = countdownTo(getActualPrayers().first { it.name == next.name }.time, now),
        solarEvent = solarEvent,
        prayers = prayers,
        insight = insight,
        windowProgress = prayerWindowProgress(this, now),
        reminder = reminder,
        dayPhase = widgetDayPhase(now),
        daylightLabel = "Daylight ${durationLabel(daylightMinutes)}",
        nightLabel = "Night ${durationLabel(nightMinutes)}",
        sky = WidgetSky(
            fajr = fajr.toSecondOfDay() / 60,
            sunrise = sunrise.toSecondOfDay() / 60,
            dhuhr = dhuhr.toSecondOfDay() / 60,
            asr = asr.toSecondOfDay() / 60,
            maghrib = maghrib.toSecondOfDay() / 60,
            isha = isha.toSecondOfDay() / 60,
            now = now.toSecondOfDay() / 60,
            latitude = location.latitude,
            dayOfYear = LocalDate.now().dayOfYear,
            hijriDay = runCatching {
                HijrahChronology.INSTANCE.date(LocalDate.now()).get(ChronoField.DAY_OF_MONTH)
            }.getOrDefault(1),
        ),
        prayerTimelineProgress = prayerTimelineProgress(now),
    )
}

/** Interpolates the current time between the five equally spaced prayer columns. */
private fun DayPrayerTimes.prayerTimelineProgress(now: LocalTime): Float {
    val prayerTimes = getActualPrayers().map { it.time }
    if (prayerTimes.size < 2 || now <= prayerTimes.first()) return 0f
    if (now >= prayerTimes.last()) return 1f

    val previousIndex = prayerTimes.indexOfLast { it <= now }.coerceAtLeast(0)
    val start = prayerTimes[previousIndex]
    val end = prayerTimes[previousIndex + 1]
    val segmentSeconds = Duration.between(start, end).seconds.coerceAtLeast(1L)
    val elapsedSeconds = Duration.between(start, now).seconds.coerceIn(0L, segmentSeconds)
    val segmentProgress = elapsedSeconds.toFloat() / segmentSeconds.toFloat()

    return ((previousIndex + segmentProgress) / (prayerTimes.size - 1).toFloat())
        .coerceIn(0f, 1f)
}

/**
 * Uses today's calculated solar anchors rather than fixed clock hours, so the foliage
 * follows local seasons and latitude. The palette changes on normal widget refreshes and
 * prayer-boundary refreshes while the launcher-side sway continues between updates.
 */
private fun DayPrayerTimes.widgetDayPhase(now: LocalTime): WidgetDayPhase = when {
    now < fajr -> WidgetDayPhase.NIGHT
    now < sunrise.plusHours(1) -> WidgetDayPhase.DAWN
    now < asr -> WidgetDayPhase.DAY
    now < maghrib -> WidgetDayPhase.AFTERNOON
    now < isha -> WidgetDayPhase.SUNSET
    else -> WidgetDayPhase.NIGHT
}

private fun durationLabel(minutes: Long): String {
    val hours = minutes / 60
    val remainder = minutes % 60
    return when {
        hours == 0L -> "${remainder}m"
        remainder == 0L -> "${hours}h"
        else -> "${hours}h ${remainder}m"
    }
}

/** Compact Gregorian and Umm al-Qura dates used beneath the widget's location header. */
private fun hijriDateLabel(date: LocalDate): String = runCatching {
    val gregorian = date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH))
    val hijrah = HijrahChronology.INSTANCE.date(date)
    val month = HIJRI_MONTHS.getOrNull(hijrah.get(ChronoField.MONTH_OF_YEAR) - 1)
        ?: DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH).format(hijrah)
    "$gregorian  •  ${hijrah.get(ChronoField.DAY_OF_MONTH)} $month ${hijrah.get(ChronoField.YEAR)}"
}.getOrElse {
    date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH))
}

// The platform's English Hijri names abbreviate ("Rabiʻ II"); the widget spells them out
// the way the supplied design does ("Rabi' al-Awwal").
private val HIJRI_MONTHS = listOf(
    "Muharram",
    "Safar",
    "Rabi' al-Awwal",
    "Rabi' al-Thani",
    "Jumada al-Ula",
    "Jumada al-Akhirah",
    "Rajab",
    "Sha'ban",
    "Ramadan",
    "Shawwal",
    "Dhu al-Qi'dah",
    "Dhu al-Hijjah",
)

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
