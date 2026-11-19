package com.starception.submission.feature.quran

import org.junit.Assert.*
import org.junit.Test

class PublishedTafsirCatalogTest {
    @Test fun everySupportedTranslationLanguageExceptSwedishHasAPublishedEdition() {
        assertEquals(setOf("bn", "en", "hi", "es", "fr", "id", "ru", "tr", "ur", "zh"), PublishedTafsirs.editions.map { it.languageCode }.toSet())
        assertNull(PublishedTafsirs.forLanguage("sv"))
        assertNull(PublishedTafsirs.forLanguage("transliteration"))
        assertNull(PublishedTafsirs.forLanguage("ar")) // Existing Arabic books remain in their original reader.
    }
    @Test fun legacyBengaliSelectionsAndPathsRemainCompatible() {
        val bengali = PublishedTafsirs.forStorageIndex(4)!!
        assertEquals(BengaliIbnKathir.BOOK_KEY, bengali.bookKey)
        assertEquals(BengaliIbnKathir.chapterKey(114), bengali.chapterKey(114))
        assertEquals(BengaliIbnKathir.PUBLISHER, bengali.attribution)
    }
    @Test fun editionKeysIndexesCategoriesAndCachePathsAreUniqueAndRoundTrip() {
        val editions = PublishedTafsirs.editions
        assertEquals(editions.size, editions.map { it.bookKey }.toSet().size)
        assertEquals(editions.size, editions.map { it.storageIndex }.toSet().size)
        assertEquals(editions.size, editions.map { it.category }.toSet().size)
        assertEquals(editions.size, editions.map { it.chapterKey(1) }.toSet().size)
        editions.forEach {
            assertEquals(it, PublishedTafsirs.forBook(it.bookKey))
            assertEquals(it, PublishedTafsirs.forCategory(it.category))
            assertEquals(it, PublishedTafsirs.forStorageIndex(it.storageIndex))
            assertEquals(it.languageCode, PublishedTafsirs.forStorageIndex(PublishedTafsirs.defaultStorageIndex(it.languageCode))?.languageCode)
        }
    }
    @Test fun bengaliDefaultsToZakariaWithoutRenamingOrRemovingIbnKathir() {
        assertEquals("zakaria_bn", PublishedTafsirs.forLanguage("bn")?.bookKey)
        assertEquals(14, PublishedTafsirs.defaultStorageIndex("bn"))
        assertEquals("ibn_kathir_bn", PublishedTafsirs.forStorageIndex(4)?.bookKey)
        assertEquals(setOf("zakaria_bn", "ibn_kathir_bn"), PublishedTafsirs.editions.filter { it.languageCode == "bn" }.map { it.bookKey }.toSet())
    }
    @Test fun savedBengaliBookChoiceSurvivesTheNewDefaultAndUnknownKeysRecoverSafely() {
        assertEquals("zakaria_bn", PublishedTafsirs.preferredBook("bn", null))
        assertEquals("ibn_kathir_bn", PublishedTafsirs.preferredBook("bn", "ibn_kathir_bn"))
        assertEquals("zakaria_bn", PublishedTafsirs.preferredBook("bn", "unknown"))
        assertEquals("baghawi", PublishedTafsirs.preferredBook("bn", "baghawi"))
        assertEquals("saadi", PublishedTafsirs.preferredBook("sv", null))
    }
    @Test fun languagesWithoutAPublishedEditionKeepTheExistingArabicReader() {
        assertEquals(0, PublishedTafsirs.defaultStorageIndex("sv"))
        assertEquals(0, PublishedTafsirs.defaultStorageIndex(""))
        assertFalse(PublishedTafsirs.validStorageIndex(99))
        assertTrue(PublishedTafsirs.validStorageIndex(0))
        assertTrue(PublishedTafsirs.validStorageIndex(3))
    }
    @Test fun chapterKeysRejectInvalidAddresses() {
        for (edition in PublishedTafsirs.editions) {
            for (invalid in listOf(0, 115, -1)) {
                try { edition.chapterKey(invalid); fail("Invalid chapter accepted") } catch (_: IllegalArgumentException) { }
            }
        }
    }
}
