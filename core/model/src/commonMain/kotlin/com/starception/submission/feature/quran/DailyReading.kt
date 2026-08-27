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

package com.starception.submission.feature.quran

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/** Al-Kahf, recited on Fridays. */
private const val AL_KAHF = 18

/**
 * Which surah the home page suggests today.
 *
 * Friday is Al-Kahf, by the practice of reciting it on that day. Every other day
 * rotates through the 114 by day of year, so the suggestion is stable for a given
 * date rather than changing on each launch — a suggestion that moved while you
 * looked at it would be no suggestion at all.
 *
 * Shared so both platforms suggest the same surah on the same day. The rule is
 * small, but two copies of it would be two chances to get Friday wrong.
 *
 * Lives in :core:model beside QuranData rather than in shared/, because `app`
 * depends on :core:model but not on shared/ — and making it do so would pull
 * Compose Multiplatform into the Android application for one date calculation.
 */
fun dailyReading(date: LocalDate): Surah =
    if (date.dayOfWeek == DayOfWeek.FRIDAY) {
        QuranData.surahs.first { it.number == AL_KAHF }
    } else {
        QuranData.surahs[(date.dayOfYear - 1) % QuranData.surahs.size]
    }

/** Reads as `Surah 18 · Meccan`, matching the Android tile. */
fun Surah.subtitle(): String = "Surah $number · $revelationType"

/** True when [date] is the day Al-Kahf is suggested, for callers that highlight it. */
fun isAlKahfDay(date: LocalDate): Boolean = date.dayOfWeek.isoDayNumber == DayOfWeek.FRIDAY.isoDayNumber
