package com.starception.submission.shared.salah

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.starception.submission.shared.storage.InMemoryKeyValueStore
import kotlinx.datetime.LocalDate

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
