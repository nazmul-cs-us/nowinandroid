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
    fun exactlyAtPrayerTime_thatPrayerBecomesCurrent() {
        // The window is [start, end): a prayer is current from the instant it
        // begins. Previously both bounds were strict, so this instant belonged to
        // no prayer at all and the UI showed nothing.
        assertEquals("Asr", currentPrayerAt(LocalTime.of(15, 53)))
        assertEquals("Dhuhr", currentPrayerAt(LocalTime.of(15, 52)))
        assertEquals("Asr", currentPrayerAt(LocalTime.of(15, 54)))
    }

    @Test
    fun everyMinuteOfTheDayHasAtMostOneCurrentPrayer() {
        // Guards the handover between adjacent windows: no minute may report two
        // current prayers, and no minute between Fajr and the end of Isha may
        // report none.
        var minute = LocalTime.of(0, 0)
        repeat(24 * 60) {
            val current = schedule().getActualPrayers(minute).filter { it.isCurrently }
            assertTrue("$minute had ${current.size} current prayers", current.size <= 1)
            if (minute >= LocalTime.of(4, 37) && minute < LocalTime.of(22, 5)) {
                assertEquals("$minute had no current prayer", 1, current.size)
            }
            minute = minute.plusMinutes(1)
        }
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
    fun isha_isNotCurrentAfterMidnight_whenItsWindowClosedBefore() {
        // A 20:05 Isha closes at 22:05, well before midnight, so nothing is
        // current at 00:30.
        assertNull(currentPrayerAt(LocalTime.of(0, 30)))
    }

    @Test
    fun isha_isCurrent_whenTwoHourWindowWrapsPastMidnight() {
        // A late Isha's window runs past midnight (23:00 + 2h = 01:00), so `end`
        // is earlier than `start` and membership is the union of the two spans.
        // This previously failed at every instant, leaving a late Isha never
        // current at all.
        val lateIsha = schedule(isha = LocalTime.of(23, 0))
        fun currentAt(t: LocalTime) =
            lateIsha.getActualPrayers(t).firstOrNull { it.isCurrently }?.name

        assertEquals("Isha", currentAt(LocalTime.of(23, 0)))
        assertEquals("Isha", currentAt(LocalTime.of(23, 30)))
        assertEquals("Isha", currentAt(LocalTime.of(0, 30)))
        assertEquals("Isha", currentAt(LocalTime.of(0, 59)))
        // ...and closes on time on the far side of midnight.
        assertNull(currentAt(LocalTime.of(1, 1)))
        assertNull(currentAt(LocalTime.of(3, 0)))
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
    fun timeUntilNextPrayer_isExactCrossingMidnight() {
        // 23:00 to an 04:37 Fajr is 5h37m. The cross-midnight branch used to sum
        // (now -> 23:59:59.999999999) + (00:00 -> Fajr), a nanosecond short, and
        // truncated to 5h36m.
        assertEquals("5h 37m", schedule().getTimeUntilNextPrayer(LocalTime.of(23, 0)))
        assertEquals("4h 38m", schedule().getTimeUntilNextPrayer(LocalTime.of(23, 59)))
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
