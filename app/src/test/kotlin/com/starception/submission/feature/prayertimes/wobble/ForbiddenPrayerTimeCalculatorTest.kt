package com.starception.submission.feature.prayertimes.wobble

import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.Location
import com.starception.submission.prayer.model.PrayerTimeOffsets
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForbiddenPrayerTimeCalculatorTest {
    private val schedule = DayPrayerTimes(
        date = LocalDateTime.of(2026, 8, 30, 0, 0),
        fajr = LocalTime.of(4, 37),
        sunrise = LocalTime.of(5, 54),
        dhuhr = LocalTime.of(12, 20),
        asr = LocalTime.of(15, 53),
        maghrib = LocalTime.of(18, 47),
        isha = LocalTime.of(20, 5),
        location = Location(latitude = 25.2048, longitude = 55.2708, timeZoneOffset = 4.0),
    )
    private val offsets = PrayerTimeOffsets(dhuhr = 3)

    private fun stateAt(
        hour: Int,
        minute: Int,
        hasPrayedAsr: Boolean = false,
    ) = calculateForbiddenPrayerTimeState(
        currentTime = LocalTime.of(hour, minute),
        prayerTimes = schedule,
        timeOffsets = offsets,
        hasPrayedAsr = hasPrayedAsr,
    )

    @Test
    fun fajrPeriod_runsUntilFifteenMinutesAfterSunrise() {
        assertEquals("fajr-sunrise", stateAt(4, 37).periodKey)
        assertEquals("fajr-sunrise", stateAt(6, 8).periodKey)
        assertFalse(stateAt(6, 9).isActive)
    }

    @Test
    fun zenithPeriod_runsFromSolarNoonUntilAdjustedDhuhr() {
        assertFalse(stateAt(12, 19).isActive)
        assertEquals("solar-zenith", stateAt(12, 20).periodKey)
        assertEquals("solar-zenith", stateAt(12, 22).periodKey)
        assertFalse(stateAt(12, 23).isActive)
    }

    @Test
    fun asrPeriod_startsOnlyAfterAsrIsRecordedAndRunsUntilMaghrib() {
        assertFalse(stateAt(15, 53).isActive)
        assertEquals("asr-sunset", stateAt(15, 53, hasPrayedAsr = true).periodKey)
        assertEquals("asr-sunset", stateAt(18, 46, hasPrayedAsr = true).periodKey)
        assertFalse(stateAt(18, 47, hasPrayedAsr = true).isActive)
    }

    @Test
    fun outsideRestrictedPeriods_isInactive() {
        assertFalse(stateAt(3, 0).isActive)
        assertFalse(stateAt(10, 0).isActive)
        assertFalse(stateAt(19, 0).isActive)
    }

    @Test
    fun activeWarningNamesVoluntaryPrayerAndEndTime() {
        val state = stateAt(16, 0, hasPrayedAsr = true)
        assertTrue(state.isActive)
        assertTrue(state.displayText.contains("voluntary prayer"))
        assertTrue(state.displayText.contains("6:47"))
    }
}
