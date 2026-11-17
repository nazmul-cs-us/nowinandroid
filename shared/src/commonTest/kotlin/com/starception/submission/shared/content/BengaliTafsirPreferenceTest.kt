package com.starception.submission.shared.content

import com.starception.submission.shared.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BengaliTafsirPreferenceTest {
    @Test fun bengaliReadersDefaultToThePublishedEdition() {
        val store = SharedContentStore(InMemoryKeyValueStore())
        store.saveQuranTranslationLanguage("bn")
        assertEquals(4, store.quranTafsirBook())
    }
    @Test fun explicitBookChoiceSurvivesRecreatingTheReaderAndOverridesTheLanguageDefault() {
        val values = InMemoryKeyValueStore(); val store = SharedContentStore(values)
        store.saveQuranTranslationLanguage("bn")
        store.saveQuranTafsirBook(0)
        assertEquals(0, SharedContentStore(values).quranTafsirBook())
        store.saveQuranTafsirBook(4)
        assertEquals(4, SharedContentStore(values).quranTafsirBook())
    }
    @Test fun invalidStoredBooksRecoverToALanguageAppropriateDefault() {
        val values = InMemoryKeyValueStore()
        values.putString("quran_tafsir_book", "unknown")
        assertEquals(0, SharedContentStore(values).quranTafsirBook())
        assertFailsWith<IllegalArgumentException> { SharedContentStore(values).saveQuranTafsirBook(99) }
    }
}
