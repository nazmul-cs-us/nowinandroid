package com.starception.submission.prayer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalTime

/**
 * Mirrors DayPrayerTimesTest in the app module, which pins the same behaviour for
 * the java.time implementation this replaces. The two must agree; if they ever
 * diverge, one of the three bugs fixed in 9a77e487b has come back on one platform.
 */
class PrayerWindowsTest {

    private val schedule = listOf(
        PrayerInstant("Fajr", LocalTime(4, 37)),
        PrayerInstant("Dhuhr", LocalTime(12, 20)),
        PrayerInstant("Asr", LocalTime(15, 53)),
        PrayerInstant("Maghrib", LocalTime(18, 47)),
        PrayerInstant("Isha", LocalTime(20, 5)),
    )

    private fun currentAt(now: LocalTime, prayers: List<PrayerInstant> = schedule): String? =
        PrayerWindows.annotate(now, prayers).firstOrNull { it.isCurrent }?.name

    private fun nextAt(now: LocalTime): String? =
        PrayerWindows.annotate(now, schedule).firstOrNull { it.isNext }?.name

    @Test
    fun betweenPrayers_currentIsTheOneJustPassed() {
        assertEquals("Fajr", currentAt(LocalTime(5, 0)))
        assertEquals("Dhuhr", currentAt(LocalTime(13, 0)))
        assertEquals("Asr", currentAt(LocalTime(16, 30)))
        assertEquals("Maghrib", currentAt(LocalTime(19, 0)))
    }

    @Test
    fun beforeFajr_noPrayerIsCurrent() {
        assertNull(currentAt(LocalTime(3, 0)))
    }

    @Test
    fun exactlyAtPrayerTime_thatPrayerBecomesCurrent() {
        assertEquals("Asr", currentAt(LocalTime(15, 53)))
        assertEquals("Dhuhr", currentAt(LocalTime(15, 52)))
    }

    @Test
    fun isha_isCurrentForTwoHoursThenStops() {
        assertEquals("Isha", currentAt(LocalTime(20, 6)))
        assertEquals("Isha", currentAt(LocalTime(22, 4)))
        assertNull(currentAt(LocalTime(22, 6)))
    }

    @Test
    fun isha_isCurrent_whenItsWindowWrapsPastMidnight() {
        val lateIsha = schedule.dropLast(1) + PrayerInstant("Isha", LocalTime(23, 0))
        assertEquals("Isha", currentAt(LocalTime(23, 30), lateIsha))
        assertEquals("Isha", currentAt(LocalTime(0, 30), lateIsha))
        assertNull(currentAt(LocalTime(1, 1), lateIsha))
    }

    @Test
    fun everyMinuteOfTheDayHasAtMostOneCurrentPrayer() {
        for (minute in 0 until 24 * 60) {
            val now = LocalTime.fromSecondOfDay(minute * 60)
            val current = PrayerWindows.annotate(now, schedule).filter { it.isCurrent }
            assertTrue(current.size <= 1, "$now had ${current.size} current prayers")
            if (now >= LocalTime(4, 37) && now < LocalTime(22, 5)) {
                assertEquals(1, current.size, "$now had no current prayer")
            }
        }
    }

    @Test
    fun nextPrayer_fallsBackToTomorrowsFirst() {
        assertEquals("Asr", nextAt(LocalTime(13, 0)))
        assertEquals("Fajr", nextAt(LocalTime(23, 0)))
    }

    @Test
    fun countdown_countsAcrossMidnight() {
        // 23:00 to an 04:37 Fajr is 5h37m. The java.time implementation used to
        // report 5h36m, a nanosecond short.
        assertEquals("5h 37m", PrayerWindows.formatCountdown(PrayerWindows.minutesUntil(LocalTime(23, 0), LocalTime(4, 37))))
        assertEquals("2h 53m", PrayerWindows.formatCountdown(PrayerWindows.minutesUntil(LocalTime(13, 0), LocalTime(15, 53))))
        assertEquals("53m", PrayerWindows.formatCountdown(PrayerWindows.minutesUntil(LocalTime(15, 0), LocalTime(15, 53))))
    }

    @Test
    fun plusHours_wrapsPastMidnight() {
        assertEquals(LocalTime(1, 0), PrayerWindows.plusHours(LocalTime(23, 0), 2))
        assertEquals(LocalTime(22, 5), PrayerWindows.plusHours(LocalTime(20, 5), 2))
    }
}
