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

package com.starception.submission.core.images

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HomeArtworkScheduleTest {
    private val dubai = TimeZone.of("Asia/Dubai")
    private val anchor = Instant.parse("2026-11-07T19:30:00Z") // 23:30 locally

    @Test
    fun dailyChangesAtLocalMidnightRatherThanUtcOrAfter24Hours() {
        val schedule = HomeArtworkSchedule(anchor = anchor)
        assertEquals(0, schedule.rotationAt(Instant.parse("2026-11-07T19:59:59Z"), dubai))
        assertEquals(1, schedule.rotationAt(Instant.parse("2026-11-07T20:00:00Z"), dubai))
        assertEquals(1, schedule.rotationAt(Instant.parse("2026-11-08T19:00:00Z"), dubai))
        assertEquals(4, schedule.rotationAt(Instant.parse("2026-11-11T08:00:00Z"), dubai))
    }

    @Test
    fun hourlyWaitsAFullHourAndWeeklyWaitsSevenLocalDays() {
        val hourly = HomeArtworkSchedule(ArtworkFrequency.HOURLY, anchor)
        assertEquals(0, hourly.rotationAt(Instant.parse("2026-11-07T20:29:59Z"), dubai))
        assertEquals(1, hourly.rotationAt(Instant.parse("2026-11-07T20:30:00Z"), dubai))
        val weekly = HomeArtworkSchedule(ArtworkFrequency.WEEKLY, anchor)
        assertEquals(0, weekly.rotationAt(Instant.parse("2026-11-13T19:59:59Z"), dubai))
        assertEquals(1, weekly.rotationAt(Instant.parse("2026-11-13T20:00:00Z"), dubai))
    }

    @Test
    fun monthlyUsesCalendarMonthsIncludingLeapYears() {
        val schedule = HomeArtworkSchedule(ArtworkFrequency.MONTHLY, Instant.parse("2028-01-31T12:00:00Z"))
        assertEquals(1, schedule.rotationAt(Instant.parse("2028-02-29T12:00:00Z"), dubai))
        assertEquals(2, schedule.rotationAt(Instant.parse("2028-02-29T20:00:00Z"), dubai))
    }

    @Test
    fun dailyStillChangesOnShortDayAcrossDaylightSaving() {
        val zone = TimeZone.of("America/New_York")
        val schedule = HomeArtworkSchedule(anchor = Instant.parse("2026-03-08T05:00:00Z"))
        assertEquals(1, schedule.rotationAt(Instant.parse("2026-03-09T04:00:00Z"), zone))
    }

    @Test
    fun changingFrequencyKeepsCurrentSetAndFixedSurvivesRelaunch() {
        val now = Instant.parse("2026-11-10T10:00:00Z")
        val initial = HomeArtworkSchedule(anchor = anchor)
        val fixed = initial.withFrequency(ArtworkFrequency.FIXED, now, dubai)
        assertEquals(initial.rotationAt(now, dubai), fixed.rotationAt(now, dubai))
        val restored = HomeArtworkSchedule.decode(fixed.encode())
        assertEquals(fixed, restored)
        assertEquals(3, restored.rotationAt(Instant.parse("2027-11-10T10:00:00Z"), dubai))
        val resumed = restored.withFrequency(ArtworkFrequency.DAILY, now, dubai)
        assertEquals(3, resumed.rotationAt(now, dubai))
        assertEquals(4, resumed.rotationAt(Instant.parse("2026-11-11T10:00:00Z"), dubai))
    }

    @Test
    fun manualRefreshPersistsAndPreservesEveryCadenceAndItsNextBoundary() {
        val now = Instant.parse("2026-11-10T10:00:00Z")
        for (frequency in ArtworkFrequency.entries) {
            val original = HomeArtworkSchedule(frequency, anchor, rotation = 2)
            var saved = original.encode()
            val settings = HomeArtworkSettings(saved) { saved = it }
            settings.refreshNow()
            val refreshed = HomeArtworkSchedule.decode(saved)
            assertEquals(original.anchor, refreshed.anchor)
            assertEquals(frequency, refreshed.frequency)
            assertEquals(original.rotationAt(now, dubai) + 1, refreshed.rotationAt(now, dubai))
            val tomorrow = Instant.parse("2026-11-11T10:00:00Z")
            assertEquals(original.rotationAt(tomorrow, dubai) - original.rotationAt(now, dubai),
                refreshed.rotationAt(tomorrow, dubai) - refreshed.rotationAt(now, dubai))
            assertEquals(refreshed, HomeArtworkSettings(saved) {}.schedule)
            for (page in 0..4) {
                assertNotEquals(homeArtworkIndex(page, original.rotationAt(now, dubai)),
                    homeArtworkIndex(page, refreshed.rotationAt(now, dubai)))
            }
        }
    }

    @Test
    fun missingOrCorruptPreferenceDefaultsToDailyWithoutCrashing() {
        for (saved in listOf(null, "", "WEEKLY|bad|1", "UNKNOWN|0|0", "DAILY|0|-1")) {
            val schedule = HomeArtworkSchedule.decode(saved, anchor)
            assertEquals(ArtworkFrequency.DAILY, schedule.frequency)
            assertEquals(anchor, schedule.anchor)
            assertEquals(0, schedule.rotation)
        }
    }

    @Test
    fun clockMovingBackDoesNotProduceInvalidArtwork() {
        val schedule = HomeArtworkSchedule(anchor = anchor, rotation = 2)
        assertEquals(2, schedule.rotationAt(Instant.parse("2025-01-01T00:00:00Z"), dubai))
    }

    @Test
    fun everyPageHasDifferentArtAndAllPagesChangeAtEachRotation() {
        for (rotation in 0..100) {
            val indices = (0..4).map { homeArtworkIndex(it, rotation) }
            assertEquals(5, indices.toSet().size)
            indices.forEachIndexed { page, index ->
                assertTrue(index in homeArtworkGallery.indices)
                assertNotEquals(index, homeArtworkIndex(page, rotation + 1))
            }
        }
        assertTrue(homeArtworkIndex(4, Int.MAX_VALUE) in homeArtworkGallery.indices)
    }
}
