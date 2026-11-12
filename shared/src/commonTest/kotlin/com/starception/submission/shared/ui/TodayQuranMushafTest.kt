package com.starception.submission.shared.ui

import com.starception.submission.shared.quran.QuranVerse
import com.starception.submission.shared.quran.SharedTajweedAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TodayQuranMushafTest {
    @Test fun separateBismillahPreservesTheVerseAndItsTajweedOffsets() {
        val prefix = "بسم الله الرحمن الرحيم "
        val verse = QuranVerse(id = 5673, surahNumber = 78, numberInSurah = 1,
            arabicText = prefix + "عَمَّ يَتَسَاءَلُونَ", page = 582, juz = 30)
        val annotation = SharedTajweedAnnotation("ghunnah", prefix.length, prefix.length + 4)
        val content = prepareHomeMushaf(78, listOf(verse), mapOf(1 to listOf(
            SharedTajweedAnnotation("ghunnah", 0, 3), annotation,
        )))
        assertEquals("عَمَّ يَتَسَاءَلُونَ", content.verses.single().arabicText)
        assertEquals(listOf(annotation.copy(startIndex = 0, endIndex = 4)), content.tajweed[1])
    }

    @Test fun fatihaAndTawbahKeepTheirExistingOpeningText() {
        val verses = listOf(QuranVerse(id = 1, surahNumber = 1, numberInSurah = 1,
            arabicText = "بسم الله الرحمن الرحيم", page = 1, juz = 1))
        val tajweed = mapOf(1 to listOf(SharedTajweedAnnotation("ghunnah", 0, 3)))
        for (surah in listOf(1, 9)) {
            val content = prepareHomeMushaf(surah, verses, tajweed)
            assertSame(verses, content.verses)
            assertSame(tajweed, content.tajweed)
        }
    }
}
