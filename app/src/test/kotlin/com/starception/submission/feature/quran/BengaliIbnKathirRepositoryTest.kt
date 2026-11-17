package com.starception.submission.feature.quran

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BengaliIbnKathirRepositoryTest {
    private fun chapter(surah: Int, text: String = "বাংলা তাফসীর"): String =
        """{"surah":$surah,"ayahCount":${BengaliIbnKathir.ayahCounts[surah - 1]},"passages":["$text"],"ayahPassage":[${List(BengaliIbnKathir.ayahCounts[surah - 1]) { 0 }.joinToString(",")}]}"""

    @Test fun groupedPassagesResolveToTheCorrectAyahWithoutChangingUnicode() = runBlocking {
        val repository = BengaliIbnKathirRepository { """{"surah":1,"ayahCount":7,"passages":["প্রথম পরিচ্ছেদ।\nআরবি: الحمد لله","দ্বিতীয়"],"ayahPassage":[0,0,1,1,1,1,1]}""" }
        assertEquals("প্রথম পরিচ্ছেদ।\nআরবি: الحمد لله", repository.text(1, 2))
        assertEquals("দ্বিতীয়", repository.text(1, 3))
        assertEquals("দ্বিতীয়", repository.text(1, 7))
    }
    @Test fun invalidAddressesNeverReadAFile() = runBlocking {
        var reads = 0
        val repository = BengaliIbnKathirRepository { reads++; chapter(it) }
        for ((surah, ayah) in listOf(0 to 1, 115 to 1, 1 to 0, 1 to 8, 114 to 7)) {
            try { repository.text(surah, ayah); fail("Accepted invalid address") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, reads)
    }
    @Test fun wrongChapterIsRejectedInsteadOfShowingAnotherSurah() = runBlocking {
        val repository = BengaliIbnKathirRepository { chapter(2) }
        try { repository.text(1, 1); fail("Wrong chapter accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun incompleteOrInvalidIndexesAndBlankPassagesAreRejected() = runBlocking {
        val valid = chapter(1)
        for (bad in listOf(valid.replace("\"ayahCount\":7", "\"ayahCount\":6"),
            valid.replace("[0,0,0,0,0,0,0]", "[0]"),
            valid.replace("[0,0,0,0,0,0,0]", "[0,0,0,0,0,0,1]"),
            valid.replace("বাংলা তাফসীর", ""))) {
            val repository = BengaliIbnKathirRepository { bad }
            try { repository.text(1, 1); fail("Invalid source accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun failedReadCanRetryAndDoesNotPoisonTheCache() = runBlocking {
        var reads = 0
        val repository = BengaliIbnKathirRepository { if (++reads == 1) error("offline") else chapter(it) }
        try { repository.text(1, 1); fail("Unexpected success") } catch (_: IllegalStateException) { }
        assertEquals("বাংলা তাফসীর", repository.text(1, 7))
        assertEquals("বাংলা তাফসীর", repository.text(1, 1))
        assertEquals(2, reads)
    }
    @Test fun chapterCacheIsBounded() = runBlocking {
        val reads = mutableListOf<Int>()
        val repository = BengaliIbnKathirRepository { reads += it; chapter(it) }
        repository.text(1, 1); repository.text(2, 1); repository.text(3, 1); repository.text(1, 1)
        assertEquals(listOf(1, 2, 3, 1), reads)
    }
    @Test fun fullDownloadVisitsEveryChapterAndReportsOnlyCompletedChapters() = runBlocking {
        val reads = mutableListOf<Int>(); val progress = mutableListOf<Int>()
        val repository = BengaliIbnKathirRepository { reads += it; chapter(it) }
        repository.downloadAll { count, total -> assertEquals(114, total); progress += count }
        assertEquals((1..114).toList(), reads)
        assertEquals((1..114).toList(), progress)
        assertEquals(6236, BengaliIbnKathir.ayahCounts.sum())
    }
    @Test fun interruptedFullDownloadDoesNotReportMissingChaptersAsSaved() = runBlocking {
        val progress = mutableListOf<Int>()
        val repository = BengaliIbnKathirRepository { if (it == 3) error("offline") else chapter(it) }
        try { repository.downloadAll { count, _ -> progress += count }; fail("Unexpected success") } catch (_: IllegalStateException) { }
        assertEquals(listOf(1, 2), progress)
    }
}
