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
import com.starception.submission.prayer.calculator.AstronomicalCalculator
import com.starception.submission.prayer.model.Location
import com.starception.submission.prayer.model.PrayerInstant
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
    val hour: Int,
    val minute: Int,
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
)

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
object PrayerSchedule {

    /**
     * Computes a day's schedule and resolves its status against [now].
     *
     * Angles default to the Muslim World League convention (Fajr 18°, Isha 17°).
     * [asrShadowFactor] is 1 for Shafi/Maliki/Hanbali and 2 for Hanafi.
     *
     * Any prayer the calculator cannot resolve — which happens at high latitudes
     * where the sun never reaches the required angle — is omitted rather than
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
        fajrAngle: Double = 18.0,
        ishaAngle: Double = 17.0,
        asrShadowFactor: Int = 1,
        nowHour: Int = -1,
        nowMinute: Int = -1,
        weatherCode: Int? = null,
    ): SharedPrayerDay {
        val calculator = AstronomicalCalculator()
        val location = Location(
            latitude = latitude,
            longitude = longitude,
            timeZoneOffset = timeZoneOffset,
        )
        val julianDay = calculator.calculateJulianDay(LocalDate(year, month, day))

        val computed = listOf(
            "Fajr" to calculator.calculateFajr(location, julianDay, fajrAngle),
            "Sunrise" to calculator.calculateSunrise(location, julianDay),
            "Dhuhr" to calculator.calculateSolarNoon(location, julianDay),
            "Asr" to calculator.calculateAsr(location, julianDay, asrShadowFactor),
            "Maghrib" to calculator.calculateSunset(location, julianDay),
            "Isha" to calculator.calculateIsha(location, julianDay, ishaAngle, null, 0),
        ).mapNotNull { (name, decimalHour) ->
            calculator.decimalHourToLocalTime(decimalHour)?.let { PrayerInstant(name, it) }
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
                hour = instant.time.hour,
                minute = instant.time.minute,
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
        )
    }
}
