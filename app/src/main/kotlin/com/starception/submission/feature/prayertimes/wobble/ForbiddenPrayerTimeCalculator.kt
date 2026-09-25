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

package com.starception.submission.feature.prayertimes.wobble

import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.PrayerTimeOffsets
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SUNRISE_SAFETY_MINUTES = 15L

/**
 * Calculates the three daily periods in which voluntary prayer is restricted:
 * Fajr through 15 minutes after sunrise, the solar-zenith interval, and Asr
 * through Maghrib after the user has recorded Asr as prayed. Obligatory and
 * otherwise excepted prayers are deliberately not described as forbidden by
 * the UI.
 */
fun calculateForbiddenPrayerTimeState(
    currentTime: LocalTime,
    prayerTimes: DayPrayerTimes?,
    timeOffsets: PrayerTimeOffsets,
    hasPrayedAsr: Boolean = false,
): ForbiddenPrayerTimeState {
    if (prayerTimes == null) return ForbiddenPrayerTimeState()

    val fajr = prayerTimes.fajr.plusMinutes(timeOffsets.fajr.toLong())
    val sunriseRestrictionEnd = prayerTimes.sunrise
        .plusMinutes(timeOffsets.sunrise.toLong())
        .plusMinutes(SUNRISE_SAFETY_MINUTES)

    // DayPrayerTimes.dhuhr is the calculated solar noon. A positive Dhuhr
    // adjustment represents the app's local safety interval after zenith. With
    // no adjustment, retain the single minute containing the calculated zenith
    // instead of producing an impossible zero-length warning.
    val solarZenith = prayerTimes.dhuhr
    val displayedDhuhr = prayerTimes.dhuhr.plusMinutes(timeOffsets.dhuhr.toLong())
    val zenithRestrictionEnd = if (displayedDhuhr.isAfter(solarZenith)) {
        displayedDhuhr
    } else {
        solarZenith.plusMinutes(1)
    }

    val asr = prayerTimes.asr.plusMinutes(timeOffsets.asr.toLong())
    val maghrib = prayerTimes.maghrib.plusMinutes(timeOffsets.maghrib.toLong())

    return when {
        currentTime.isWithin(fajr, sunriseRestrictionEnd) -> warning(
            periodKey = "fajr-sunrise",
            end = sunriseRestrictionEnd,
        )
        currentTime.isWithin(solarZenith, zenithRestrictionEnd) -> warning(
            periodKey = "solar-zenith",
            end = zenithRestrictionEnd,
        )
        hasPrayedAsr && currentTime.isWithin(asr, maghrib) -> warning(
            periodKey = "asr-sunset",
            end = maghrib,
        )
        else -> ForbiddenPrayerTimeState()
    }
}

private fun warning(periodKey: String, end: LocalTime): ForbiddenPrayerTimeState =
    ForbiddenPrayerTimeState(
        isActive = true,
        periodKey = periodKey,
        displayText = when (periodKey) {
            "fajr-sunrise" -> "After Fajr · No voluntary prayer until ${end.displayTime()}"
            "solar-zenith" -> "Solar noon · No voluntary prayer until ${end.displayTime()}"
            "asr-sunset" -> "After Asr · No voluntary prayer until ${end.displayTime()}"
            else -> "No voluntary prayer until ${end.displayTime()}"
        },
    )

private fun LocalTime.isWithin(startInclusive: LocalTime, endExclusive: LocalTime): Boolean =
    this >= startInclusive && this < endExclusive

private fun LocalTime.displayTime(): String = format(
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()),
)
