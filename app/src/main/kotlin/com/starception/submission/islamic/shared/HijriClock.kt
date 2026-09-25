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

package com.starception.submission.islamic.shared

import android.util.Log
import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * Thin wrapper around java.time.chrono.HijrahChronology (Umm al-Qura per JDK docs).
 * Returns null on the rare locales / out-of-range dates where the JDK chronology throws,
 * so callers can degrade gracefully instead of crashing the UI.
 */
object HijriClock {

    data class Today(val day: Int, val month: Int, val year: Int)

    fun today(now: LocalDate = LocalDate.now()): Today? = try {
        val hd: HijrahDate = HijrahChronology.INSTANCE.date(now)
        Today(
            day = hd.get(ChronoField.DAY_OF_MONTH),
            month = hd.get(ChronoField.MONTH_OF_YEAR),
            year = hd.get(ChronoField.YEAR),
        )
    } catch (e: Exception) {
        Log.w("HijriClock", "HijrahChronology lookup failed for $now", e)
        null
    }
}
