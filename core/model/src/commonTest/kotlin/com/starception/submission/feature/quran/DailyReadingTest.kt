package com.starception.submission.feature.quran

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

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
