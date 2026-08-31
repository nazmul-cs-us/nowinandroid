package com.starception.submission.shared.quran

import kotlin.test.Test
import kotlin.test.assertEquals

class QuranVerseRepositoryTest {
    private val verses = listOf(
        QuranVerse(1, 1, 1, "\uFEFFبِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ", 1, 1),
        QuranVerse(2, 1, 2, "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ", 1, 1),
    )

    @Test
    fun cleansDatabaseBomAndFormatsMetadata() {
        assertEquals("بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ", cleanQuranText(verses.first().arabicText))
        assertEquals("Ayah 2 · Page 1 · Juz 1", verses.last().metadataLabel())
    }

    @Test
    fun searchesArabicWithoutRequiringDatabaseDiacritics() {
        assertEquals(listOf(verses.first()), filterQuranVerses(verses, "الله الرحمن"))
        assertEquals(listOf(verses.last()), filterQuranVerses(verses, "الحمد لله"))
    }

    @Test
    fun searchesByExactAyahNumberAndPreservesOrderForBlankQuery() {
        assertEquals(listOf(verses.last()), filterQuranVerses(verses, "2"))
        assertEquals(verses, filterQuranVerses(verses, "  "))
    }
}
