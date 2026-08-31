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

package com.starception.submission.shared

import com.starception.submission.core.images.PrayerSkyPhase
import com.starception.submission.core.images.PrayerSkyWeather
import com.starception.submission.core.images.prayerSkyPhase
import com.starception.submission.core.images.prayerSkyWeather
import com.starception.submission.core.images.weatherConditionLabel
import com.starception.submission.prayer.calculator.AstronomicalCalculator
import com.starception.submission.prayer.model.CountryPrayerDefaults
import com.starception.submission.prayer.model.HighLatitudeAdjustment
import com.starception.submission.prayer.model.Location
import com.starception.submission.prayer.model.PrayerInstant
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.prayer.model.getPrayerNameInLocalLanguage
import com.starception.submission.prayer.model.PrayerWindows
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * One prayer in a day's schedule, flattened for Objective-C interop.
 *
 * Hour and minute rather than a date type: kotlinx-datetime's `LocalTime` does
 * cross into Swift, but keeping the boundary primitive means `iosApp/` needs no
 * knowledge of the Kotlin date library at all.
 */
data class SharedPrayerSlot(
    val name: String,
    /** The prayer's name in the local language, e.g. ٱلْعَصْر in the Gulf. */
    val localName: String = "",
    val hour: Int,
    val minute: Int,
    /** Whether this event has started today, used to order the dashboard by relevance. */
    val hasStarted: Boolean = false,
    val isCurrent: Boolean = false,
    val isNext: Boolean = false,
) {
    /** Zero-padded 24-hour form, e.g. `04:37`. */
    val display: String
        get() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

/**
 * A day's schedule with its status resolved against a moment in time.
 */
data class SharedPrayerDay(
    val slots: List<SharedPrayerSlot>,
    val currentPrayer: String?,
    val nextPrayer: String?,
    /** Time until [nextPrayer], as `5h 37m`, `37m` or `Now`. */
    val countdown: String,
    /** Which sky artwork suits this moment. */
    val skyPhase: PrayerSkyPhase,
    /** Which sky artwork suits the forecast. */
    val skyWeather: PrayerSkyWeather,
    /** Present temperature in Celsius, when the forecast has arrived. */
    val temperatureCelsius: Double? = null,
    /** A short description of the sky, e.g. "Clear sky". */
    val conditionLabel: String = "",
    /** Current local minute of the day, used to render the active-prayer timeline. */
    val nowMinute: Int = 0,
)

/**
 * Displays the latest event first, then wraps through the rest of the day.
 *
 * This mirrors the Android home page: after sunset, for example, Maghrib is the
 * first card rather than being hidden behind a permanently Fajr-first list.
 */
fun SharedPrayerDay.dashboardSlots(): List<SharedPrayerSlot> {
    if (slots.isEmpty()) return emptyList()
    val firstIndex = slots.indexOfLast { it.hasStarted }
    if (firstIndex < 0) return slots
    return List(slots.size) { offset -> slots[(firstIndex + offset) % slots.size] }
}

/**
 * Facade over the shared prayer engine for UI hosts.
 *
 * The calculator speaks in decimal hours and needs angles, shadow factors and
 * offsets chosen per calculation method; [PrayerWindows] decides what the
 * resulting schedule *means*. Both are domain knowledge and belong in Kotlin
 * rather than scattered through Swift, so this returns a finished, annotated day
 * and the host only formats it.
 *
 * Deliberately minimal: it takes parameters directly rather than reaching for
 * `PrayerSettings`, which still lives in `app` on java.time. It grows into the
 * real shared entry point as the prayer slice moves over.
 */
private const val NEAREST_LATITUDE = 48.5

object PrayerSchedule {

    /**
     * Computes a day's schedule and resolves its status against [now].
     *
     * [defaults] carries the country's calculation method, Asr shadow factor and
     * any per-prayer offsets its authority publishes. Without it the Muslim World
     * League convention is used, which is a reasonable global default but wrong
     * for plenty of places — passing the real country is what makes the times
     * correct rather than merely plausible.
     *
     * A configured high-latitude rule supplies Fajr or Isha only when its normal
     * calculation fails. Any prayer still unresolved is omitted rather than
     * reported as a wrong time. Sunrise is excluded from the current/next
     * reckoning: it is an astronomical event shown for reference, not a prayer.
     */
    fun forDate(
        year: Int,
        month: Int,
        day: Int,
        latitude: Double,
        longitude: Double,
        timeZoneOffset: Double,
        defaults: CountryPrayerDefaults? = null,
        settings: PrayerSettings = PrayerSettings(),
        countryCode: String? = null,
        isFriday: Boolean = false,
        nowHour: Int = -1,
        nowMinute: Int = -1,
        weatherCode: Int? = null,
        temperatureCelsius: Double? = null,
    ): SharedPrayerDay {
        val calculator = AstronomicalCalculator()
        val location = Location(
            latitude = latitude,
            longitude = longitude,
            timeZoneOffset = timeZoneOffset,
        )
        val julianDay = calculator.calculateJulianDay(LocalDate(year, month, day))

        // The user's settings decide the calculation; the country contributes
        // only its published per-prayer offsets, which apply regardless of which
        // method was chosen.
        val method = settings.calculationMethod
        val fajrAngle = settings.customFajrAngle ?: method.fajrAngle
        val shadowFactor = settings.asrMadhhab.shadowFactor

        val sunrise = calculator.calculateSunrise(location, julianDay)
        val sunset = calculator.calculateSunset(location, julianDay)
        val ishaAngle = settings.getEffectiveIshaAngle()
        val rawFajr = calculator.calculateFajr(location, julianDay, fajrAngle)
        val rawIsha = calculator.calculateIsha(
            location,
            julianDay,
            ishaAngle,
            settings.customIshaDelay ?: method.ishaDelay,
            settings.customMaghribOffset ?: method.maghribOffset,
        )

        fun adjustedTwilight(isFajr: Boolean, angle: Double): Double {
            if (settings.highLatitudeAdjustment == HighLatitudeAdjustment.NONE) return Double.NaN

            if (settings.highLatitudeAdjustment == HighLatitudeAdjustment.NEAREST_LATITUDE) {
                val nearest = location.copy(
                    latitude = location.latitude.coerceIn(-NEAREST_LATITUDE, NEAREST_LATITUDE),
                )
                return if (isFajr) {
                    calculator.calculateFajr(nearest, julianDay, angle)
                } else {
                    calculator.calculateIsha(
                        nearest,
                        julianDay,
                        ishaAngle,
                        settings.customIshaDelay ?: method.ishaDelay,
                        settings.customMaghribOffset ?: method.maghribOffset,
                    )
                }
            }

            // Divide the night from today's sunset to tomorrow's sunrise. These
            // methods cannot produce a time when either boundary is unavailable.
            if (sunrise.isNaN() || sunset.isNaN()) return Double.NaN
            val nightHours = ((sunrise - sunset) % 24.0 + 24.0) % 24.0
            val portion = when (settings.highLatitudeAdjustment) {
                HighLatitudeAdjustment.MIDDLE_OF_NIGHT -> 0.5
                HighLatitudeAdjustment.ONE_SEVENTH_OF_NIGHT -> 1.0 / 7.0
                HighLatitudeAdjustment.ANGLE_BASED -> (angle / 60.0).coerceIn(0.0, 1.0)
                else -> return Double.NaN
            }
            val adjustment = nightHours * portion
            return if (isFajr) sunrise - adjustment else sunset + adjustment
        }

        val fajr = rawFajr.takeUnless { it.isNaN() }
            ?: adjustedTwilight(isFajr = true, angle = fajrAngle)
        val isha = rawIsha.takeUnless { it.isNaN() }
            ?: adjustedTwilight(isFajr = false, angle = ishaAngle ?: 0.0)

        val computed = listOf(
            "Fajr" to fajr,
            "Sunrise" to sunrise,
            "Dhuhr" to calculator.calculateSolarNoon(location, julianDay),
            "Asr" to calculator.calculateAsr(location, julianDay, shadowFactor),
            "Maghrib" to sunset,
            "Isha" to isha,
        ).mapNotNull { (name, decimalHour) ->
            calculator.decimalHourToLocalTime(decimalHour)?.let { instant ->
                // The country's published adjustment and the user's own both
                // apply: the first is what the local authority announces, the
                // second is the user reconciling it with their mosque.
                PrayerInstant(
                    name,
                    instant.plusMinutes(
                        defaults.offsetFor(name) + settings.timeOffsets.getOffset(name),
                    ),
                )
            }
        }

        // Callers may pin the moment; otherwise use the device clock. Pinning is
        // what makes this testable at an arbitrary time rather than only at
        // whatever time the suite happens to run.
        val now = if (nowHour in 0..23 && nowMinute in 0..59) {
            LocalTime(nowHour, nowMinute)
        } else {
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
        }

        val prayersOnly = computed.filter { it.name != "Sunrise" }
        val annotated = PrayerWindows.annotate(now, prayersOnly).associateBy { it.name }

        val slots = computed.map { instant ->
            val status = annotated[instant.name]
            SharedPrayerSlot(
                name = instant.name,
                localName = getPrayerNameInLocalLanguage(
                    englishName = instant.name,
                    countryCode = countryCode,
                    isFriday = isFriday,
                ),
                hour = instant.time.hour,
                minute = instant.time.minute,
                hasStarted = instant.time <= now,
                isCurrent = status?.isCurrent == true,
                isNext = status?.isNext == true,
            )
        }

        fun minuteOf(name: String) = computed.firstOrNull { it.name == name }
            ?.time?.let { it.hour * 60 + it.minute }

        val next = annotated.values.firstOrNull { it.isNext }
        return SharedPrayerDay(
            slots = slots,
            currentPrayer = annotated.values.firstOrNull { it.isCurrent }?.name,
            nextPrayer = next?.name,
            countdown = next
                ?.let { PrayerWindows.formatCountdown(PrayerWindows.minutesUntil(now, it.time)) }
                .orEmpty(),
            skyPhase = prayerSkyPhase(
                nowMinute = now.hour * 60 + now.minute,
                fajrMinute = minuteOf("Fajr") ?: 300,
                sunriseMinute = minuteOf("Sunrise") ?: 390,
                asrMinute = minuteOf("Asr") ?: 930,
                maghribMinute = minuteOf("Maghrib") ?: 1_080,
                ishaMinute = minuteOf("Isha") ?: 1_200,
            ),
            skyWeather = prayerSkyWeather(weatherCode),
            temperatureCelsius = temperatureCelsius,
            conditionLabel = weatherConditionLabel(weatherCode),
            nowMinute = now.hour * 60 + now.minute,
        )
    }
}

/**
 * The country's published adjustment for a prayer, in minutes.
 *
 * Keys in the source data are lowercase prayer names; the schedule uses
 * capitalised ones.
 */
private fun CountryPrayerDefaults?.offsetFor(prayerName: String): Int =
    this?.timeOffsets?.get(prayerName.lowercase()) ?: 0

/** Adds minutes, wrapping past midnight. */
private fun LocalTime.plusMinutes(minutes: Int): LocalTime =
    if (minutes == 0) {
        this
    } else {
        LocalTime.fromSecondOfDay(
            (toSecondOfDay() + minutes * 60).mod(24 * 60 * 60),
        )
    }
