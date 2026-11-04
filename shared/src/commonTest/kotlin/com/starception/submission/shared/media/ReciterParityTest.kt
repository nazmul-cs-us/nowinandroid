package com.starception.submission.shared.audio

import com.starception.submission.shared.storage.InMemoryKeyValueStore
import com.starception.submission.shared.content.SharedContentStore
import kotlin.test.*

class ReciterParityTest {
    @Test fun sudaisAliasesAndPunctuationResolveTheSameRecording() {
        val source = QuranReciterSource("54-100", "Abdul Rahman Al-Sudais", "https://example.org", setOf(1), "Hafs")
        listOf("Alsudaes", "Sudays", "Abdul-Rahman", "as sudais", "Hafs").forEach { assertTrue(matchesQuranReciterSearch(source, it), it) }
        assertFalse(matchesQuranReciterSearch(source, "Minshawi"))
    }
    @Test fun choosingReciterAlsoChoosesArabicAudio() {
        val store = InMemoryKeyValueStore()
        val content = SharedContentStore(store)
        content.saveQuranTranslationLanguage("bn")
        val preferences = QuranReciterPreferences(store)
        preferences.selectSource(QuranReciterSource("54-100", "Al-Sudais", "https://example.org", setOf(1)))
        assertEquals("ar", content.quranTranslationLanguage())
        assertEquals("54-100", preferences.selectedReciterId())
    }
}
