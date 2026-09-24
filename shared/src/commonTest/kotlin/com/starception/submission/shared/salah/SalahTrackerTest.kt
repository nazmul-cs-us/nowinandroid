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

package com.starception.submission.shared.salah

import com.starception.submission.shared.storage.InMemoryKeyValueStore
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SalahTrackerTest {

    private val today = LocalDate(2026, 8, 27)
    private val tomorrow = LocalDate(2026, 8, 28)

    @Test
    fun startsEmpty() {
        assertTrue(SalahTracker(InMemoryKeyValueStore()).completed(today).isEmpty())
    }

    @Test
    fun togglingMarksAndUnmarks() {
        val tracker = SalahTracker(InMemoryKeyValueStore())
        assertEquals(setOf("Fajr"), tracker.toggle(today, "Fajr"))
        assertEquals(setOf("Fajr", "Asr"), tracker.toggle(today, "Asr"))
        assertEquals(setOf("Asr"), tracker.toggle(today, "Fajr"))
    }

    @Test
    fun eachDateIsSeparate() {
        val tracker = SalahTracker(InMemoryKeyValueStore())
        tracker.toggle(today, "Fajr")
        // A new day is empty because nothing was ever written for it, so no
        // midnight reset has to run for this to hold.
        assertTrue(tracker.completed(tomorrow).isEmpty())
        assertEquals(setOf("Fajr"), tracker.completed(today))
    }

    @Test
    fun survivesReconstruction() {
        val store = InMemoryKeyValueStore()
        SalahTracker(store).toggle(today, "Maghrib")
        assertEquals(setOf("Maghrib"), SalahTracker(store).completed(today))
    }

    @Test
    fun nextUnprayedIsByOrderNotByTime() {
        // Someone who prayed Dhuhr and Asr but never marked Fajr should still be
        // prompted about Fajr, not skipped past it.
        val progress = SalahProgress.from(setOf("Dhuhr", "Asr"))
        assertEquals("Fajr", progress.nextUnprayed)
        assertEquals(3, progress.remainingCount)
    }

    @Test
    fun readsSensiblyAtEachCount() {
        assertEquals("No prayers marked", SalahProgress.from(emptySet()).headline)
        assertEquals("1 prayer complete", SalahProgress.from(setOf("Fajr")).headline)
        assertEquals("3 prayers complete", SalahProgress.from(setOf("Fajr", "Dhuhr", "Asr")).headline)

        val all = SalahProgress.from(FARD_PRAYERS.toSet())
        assertEquals("All prayers complete", all.headline)
        assertEquals("Nothing remaining today", all.detail)
        assertNull(all.nextUnprayed)
    }
}
