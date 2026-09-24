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

package com.starception.submission.feature.quran

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailyReadingTest {

    @Test
    fun fridayIsAlKahf() {
        // 2026-08-28 is a Friday.
        val surah = dailyReading(LocalDate(2026, 8, 28))
        assertEquals(18, surah.number)
        assertEquals("Al-Kahf", surah.nameEnglish)
    }

    @Test
    fun otherDaysRotateByDayOfYear() {
        // 2026-08-27 is a Thursday, day 239. The rotation is zero-based on the
        // day, so (239 - 1) % 114 = 10, which is the eleventh surah.
        assertEquals(11, dailyReading(LocalDate(2026, 8, 27)).number)
        // The day before is the one before it, confirming the step is daily.
        assertEquals(10, dailyReading(LocalDate(2026, 8, 26)).number)
    }

    @Test
    fun theSuggestionIsStableForADate() {
        val date = LocalDate(2026, 3, 3)
        assertEquals(dailyReading(date).number, dailyReading(date).number)
    }

    @Test
    fun everyDayOfAYearResolves() {
        // The modulo has to keep the index in range on day 365 as well as day 1.
        var date = LocalDate(2026, 1, 1)
        repeat(365) {
            val surah = dailyReading(date)
            assertTrue(surah.number in 1..114, "day $date gave surah ${surah.number}")
            date = LocalDate.fromEpochDays(date.toEpochDays() + 1)
        }
    }

    @Test
    fun subtitleMatchesTheAndroidTile() {
        assertEquals("Surah 18 · Meccan", dailyReading(LocalDate(2026, 8, 28)).subtitle())
    }
}
