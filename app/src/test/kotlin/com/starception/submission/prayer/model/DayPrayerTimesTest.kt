package com.starception.submission.prayer.model

import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization tests for [DayPrayerTimes].
 *
 * These pin the behaviour of the current `java.time` implementation ahead of the
 * migration to `kotlinx-datetime` for the iOS port. They are deliberately
 * descriptive rather than prescriptive: where the current behaviour looks wrong
 * (see [isha_isNotCurrent_whenTwoHourWindowWrapsPastMidnight] and
 * [timeUntilNextPrayer_losesASecondCrossingMidnight]) the test records what the
 * code does today, so the migration can be proven behaviour-preserving. Fixing
 * those is a separate decision from porting them.
 *
 * Times are the real Dubai schedule observed on device, so the expectations here
 * match what the dashboard shows.
 */
class DayPrayerTimesTest {

    private fun schedule(
        fajr: LocalTime = LocalTime.of(4, 37),
        sunrise: LocalTime = LocalTime.of(5, 54),
        dhuhr: LocalTime = LocalTime.of(12, 20),
        asr: LocalTime = LocalTime.of(15, 53),
        maghrib: LocalTime = LocalTime.of(18, 47),
        isha: LocalTime = LocalTime.of(20, 5),
    ) = DayPrayerTimes(
        date = LocalDateTime.of(2026, 8, 26, 0, 0),
        fajr = fajr,
        sunrise = sunrise,
        dhuhr = dhuhr,
        asr = asr,
        maghrib = maghrib,
        isha = isha,
        location = Location(latitude = 25.2048, longitude = 55.2708, timeZoneOffset = 4.0),
    )

    private fun currentPrayerAt(now: LocalTime): String? =
        schedule().getActualPrayers(now).firstOrNull { it.isCurrently }?.name

    private fun nextPrayerAt(now: LocalTime): String? =
        schedule().getActualPrayers(now).firstOrNull { it.isNext }?.name

    // --- current prayer windows ---------------------------------------------

    @Test
    fun betweenPrayers_currentIsTheOneJustPassed() {
        assertEquals("Fajr", currentPrayerAt(LocalTime.of(5, 0)))
        assertEquals("Dhuhr", currentPrayerAt(LocalTime.of(13, 0)))
        assertEquals("Asr", currentPrayerAt(LocalTime.of(16, 30)))
        assertEquals("Maghrib", currentPrayerAt(LocalTime.of(19, 0)))
    }

    @Test
    fun beforeFajr_noPrayerIsCurrent() {
        assertNull(currentPrayerAt(LocalTime.of(3, 0)))
    }

    @Test
    fun exactlyAtPrayerTime_noPrayerIsCurrent() {
        // Pre-existing quirk, pinned deliberately. Both bounds are strict:
        // Dhuhr stays current while now < Asr, and Asr becomes current only once
        // now > Asr. At exactly Asr neither holds, so the boundary instant
        // belongs to no prayer at all.
        assertNull(currentPrayerAt(LocalTime.of(15, 53)))
        assertEquals("Dhuhr", currentPrayerAt(LocalTime.of(15, 52)))
        assertEquals("Asr", currentPrayerAt(LocalTime.of(15, 54)))
    }

    // --- the Isha two-hour window -------------------------------------------

    @Test
    fun isha_isCurrentForTwoHoursAfterItStarts() {
        assertEquals("Isha", currentPrayerAt(LocalTime.of(20, 6)))
        assertEquals("Isha", currentPrayerAt(LocalTime.of(22, 4)))
    }

    @Test
    fun isha_stopsBeingCurrentAfterTwoHours() {
        assertNull(currentPrayerAt(LocalTime.of(22, 6)))
    }

    @Test
    fun isha_isNotCurrentAfterMidnight() {
        // now.isAfter(isha) is a plain same-day comparison, so 00:30 is not
        // "after" 20:05 and no prayer is current.
        assertNull(currentPrayerAt(LocalTime.of(0, 30)))
    }

    @Test
    fun isha_isNotCurrent_whenTwoHourWindowWrapsPastMidnight() {
        // Pre-existing quirk, pinned deliberately. For a late Isha, plusHours(2)
        // wraps around midnight (23:00 + 2h = 01:00), so the isBefore check fails
        // immediately and Isha never reads as current at all -- not even one
        // minute after it starts.
        val lateIsha = schedule(isha = LocalTime.of(23, 0))
        val current = lateIsha.getActualPrayers(LocalTime.of(23, 30))
            .firstOrNull { it.isCurrently }?.name
        assertNull(current)
    }

    // --- next prayer ---------------------------------------------------------

    @Test
    fun nextPrayer_isTheFirstStillAhead() {
        assertEquals("Fajr", nextPrayerAt(LocalTime.of(3, 0)))
        assertEquals("Asr", nextPrayerAt(LocalTime.of(13, 0)))
        assertEquals("Isha", nextPrayerAt(LocalTime.of(19, 0)))
    }

    @Test
    fun afterIsha_nextPrayerFallsBackToFajr() {
        assertEquals("Fajr", nextPrayerAt(LocalTime.of(23, 0)))
        assertEquals("Fajr", schedule().getNextPrayer(LocalTime.of(23, 0))?.name)
    }

    // --- countdown -----------------------------------------------------------

    @Test
    fun timeUntilNextPrayer_sameDay() {
        assertEquals("2h 53m", schedule().getTimeUntilNextPrayer(LocalTime.of(13, 0)))
        assertEquals("53m", schedule().getTimeUntilNextPrayer(LocalTime.of(15, 0)))
    }

    @Test
    fun timeUntilNextPrayer_losesASecondCrossingMidnight() {
        // Pre-existing quirk, pinned deliberately. The cross-midnight branch sums
        // (now -> 23:59:59.999999999) + (00:00 -> Fajr), which is one nanosecond
        // short of the true span, so the minute is truncated downward: the real
        // gap from 23:00 to 04:37 is 5h37m, but this reports 5h36m.
        assertEquals("5h 36m", schedule().getTimeUntilNextPrayer(LocalTime.of(23, 0)))
    }

    // --- injection defaults --------------------------------------------------

    @Test
    fun defaultArgument_stillUsesTheSystemClock() {
        // Guards the additive change: existing zero-arg call sites must keep
        // working and must not throw.
        assertTrue(schedule().getActualPrayers().size == 5)
        assertFalse(schedule().getAllPrayers().isEmpty())
    }
}
