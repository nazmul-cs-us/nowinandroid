package com.starception.submission.shared.content

import com.starception.submission.shared.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BengaliTafsirPreferenceTest {
    @Test fun bengaliReadersDefaultToThePublishedEdition() {
        val store = SharedContentStore(InMemoryKeyValueStore())
        store.saveQuranTranslationLanguage("bn")
        assertEquals(14, store.quranTafsirBook())
    }
    @Test fun explicitBookChoiceSurvivesRecreatingTheReaderAndOverridesTheLanguageDefault() {
        val values = InMemoryKeyValueStore(); val store = SharedContentStore(values)
        store.saveQuranTranslationLanguage("bn")
        store.saveQuranTafsirBook(0)
        assertEquals(0, SharedContentStore(values).quranTafsirBook())
        store.saveQuranTafsirBook(4)
        assertEquals(14, SharedContentStore(values).quranTafsirBook())
    }
    @Test fun invalidStoredBooksRecoverToALanguageAppropriateDefault() {
        val values = InMemoryKeyValueStore()
        values.putString("quran_tafsir_book", "unknown")
        assertEquals(5, SharedContentStore(values).quranTafsirBook())
        assertFailsWith<IllegalArgumentException> { SharedContentStore(values).saveQuranTafsirBook(99) }
    }
    @Test fun allPublishedLanguagesDefaultToTheirOwnEditionAndPersistAnExplicitChoice() {
        for (edition in com.starception.submission.feature.quran.PublishedTafsirs.editions) {
            val values = InMemoryKeyValueStore(); val store = SharedContentStore(values)
            store.saveQuranTranslationLanguage(edition.languageCode)
            assertEquals(com.starception.submission.feature.quran.PublishedTafsirs.defaultStorageIndex(edition.languageCode), store.quranTafsirBook())
            store.saveQuranTafsirBook(edition.storageIndex)
            store.saveQuranTranslationLanguage("sv")
            assertEquals(edition.storageIndex, SharedContentStore(values).quranTafsirBook())
        }
    }
    @Test fun retiredBengaliIndexMigratesEvenWhenTheReadingLanguageHasChanged() {
        val values = InMemoryKeyValueStore(); val store = SharedContentStore(values)
        values.putString("quran_tafsir_book", "4"); store.saveQuranTranslationLanguage("zh")
        assertEquals(14, SharedContentStore(values).quranTafsirBook())
        store.saveQuranTafsirBook(0)
        assertEquals(0, SharedContentStore(values).quranTafsirBook())
    }
}
