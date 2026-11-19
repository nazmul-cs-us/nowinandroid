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
    @Test fun retiredBengaliEditionIsNotSelectableAndMigratesToZakaria() {
        assertNull(PublishedTafsirs.forBook("ibn_kathir_bn"))
        assertNull(PublishedTafsirs.forStorageIndex(4))
        assertNull(PublishedTafsirs.forCategory("quran_tafsir_bn"))
        assertTrue(PublishedTafsirs.isRetiredCategory("quran_tafsir_bn"))
        assertEquals(14, PublishedTafsirs.migrateStorageIndex(4))
        assertFalse(PublishedTafsirs.isRetiredCategory("quran_tafsir_bn_zakaria"))
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
    @Test fun bengaliOffersOnlyZakaria() {
        assertEquals("zakaria_bn", PublishedTafsirs.forLanguage("bn")?.bookKey)
        assertEquals(14, PublishedTafsirs.defaultStorageIndex("bn"))
        assertEquals(setOf("zakaria_bn"), PublishedTafsirs.editions.filter { it.languageCode == "bn" }.map { it.bookKey }.toSet())
    }
    @Test fun savedBengaliBookChoiceSurvivesTheNewDefaultAndUnknownKeysRecoverSafely() {
        assertEquals("zakaria_bn", PublishedTafsirs.preferredBook("bn", null))
        assertEquals("zakaria_bn", PublishedTafsirs.preferredBook("bn", "ibn_kathir_bn"))
        assertEquals("zakaria_bn", PublishedTafsirs.preferredBook("en", "ibn_kathir_bn"))
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
