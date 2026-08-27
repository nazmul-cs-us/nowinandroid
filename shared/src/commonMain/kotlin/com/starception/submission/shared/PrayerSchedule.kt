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

import com.starception.submission.prayer.calculator.AstronomicalCalculator
import com.starception.submission.prayer.model.Location
import kotlinx.datetime.LocalDate

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
) {
    /** Zero-padded 24-hour form, e.g. `04:37`. */
    val display: String
        get() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

/**
 * Facade over [AstronomicalCalculator] for the iOS host.
 *
 * The calculator speaks in decimal hours and needs angles, shadow factors and
 * offsets chosen per calculation method. That is domain knowledge and belongs in
 * Kotlin, not scattered through Swift — so this returns a finished schedule and
 * the host only formats it.
 *
 * Deliberately minimal: it takes the parameters directly rather than reaching for
 * `PrayerSettings`, which still lives in `app` on java.time. It grows into the
 * real shared entry point as the prayer slice moves over in phase 4.
 */
object PrayerSchedule {

    /**
     * Computes today's schedule for a location.
     *
     * Angles default to the Muslim World League convention (Fajr 18°, Isha 17°).
     * [asrShadowFactor] is 1 for Shafi/Maliki/Hanbali and 2 for Hanafi.
     *
     * Any prayer the calculator cannot resolve — which happens at high latitudes
     * where the sun never reaches the required angle — is omitted rather than
     * reported as a wrong time.
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
    ): List<SharedPrayerSlot> {
        val calculator = AstronomicalCalculator()
        val location = Location(
            latitude = latitude,
            longitude = longitude,
            timeZoneOffset = timeZoneOffset,
        )
        val julianDay = calculator.calculateJulianDay(LocalDate(year, month, day))

        val decimalHours = listOf(
            "Fajr" to calculator.calculateFajr(location, julianDay, fajrAngle),
            "Sunrise" to calculator.calculateSunrise(location, julianDay),
            "Dhuhr" to calculator.calculateSolarNoon(location, julianDay),
            "Asr" to calculator.calculateAsr(location, julianDay, asrShadowFactor),
            "Maghrib" to calculator.calculateSunset(location, julianDay),
            "Isha" to calculator.calculateIsha(location, julianDay, ishaAngle, null, 0),
        )

        return decimalHours.mapNotNull { (name, hour) ->
            calculator.decimalHourToLocalTime(hour)?.let {
                SharedPrayerSlot(name = name, hour = it.hour, minute = it.minute)
            }
        }
    }
}
