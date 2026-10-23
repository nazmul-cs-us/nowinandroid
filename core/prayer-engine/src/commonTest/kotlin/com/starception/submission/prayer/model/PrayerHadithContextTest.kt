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

package com.starception.submission.prayer.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrayerHadithContextTest {
    @Test
    fun fridayMiddayUsesJumuahRatherThanOrdinaryDhuhr() {
        val context = prayerHadithContext("Dhuhr", LocalDate(2026, 10, 23))
        assertTrue(context.isFriday)
        assertTrue(context.isJumuahPrayer)
        assertEquals("Friday prayer", context.queries.first())
        assertFalse(context.queries.contains("Dhuhr prayer"))
        assertTrue(context.heading("Dhuhr").contains("JUMU'AH"))
        assertFalse(context.accepts("Prayer in congregation has a greater reward."))
        assertTrue(context.accepts("Attend the Friday prayer."))
        assertTrue(context.accepts("صَلَاةُ الْجُمُعَةِ"))
    }

    @Test
    fun fridayIsRecognizedThroughoutTheDayWithoutRenamingOtherPrayers() {
        for (prayer in listOf("Fajr", "Asr", "Maghrib", "Isha")) {
            val context = prayerHadithContext(prayer, LocalDate(2026, 10, 23))
            assertTrue(context.isFriday)
            assertFalse(context.isJumuahPrayer)
            assertTrue(context.heading(prayer).contains(prayer.uppercase()))
            assertTrue(context.heading(prayer).contains("FRIDAY"))
        }
    }

    @Test
    fun newLocalDateRestoresTheNormalPrayerContext() {
        for (date in listOf(LocalDate(2026, 10, 22), LocalDate(2026, 10, 24))) {
            val context = prayerHadithContext("Dhuhr", date)
            assertFalse(context.isFriday)
            assertEquals("Dhuhr prayer", context.queries.first())
            assertTrue(context.accepts("A narration about midday prayer."))
        }
    }

    @Test
    fun jumuahSpellingVariantsAreRecognized() {
        for (name in listOf("Jumu'ah", "Jumu’ah", "Jumah", "Jummah")) {
            assertTrue(prayerHadithContext(name, LocalDate(2026, 10, 23)).isJumuahPrayer)
        }
    }
}
