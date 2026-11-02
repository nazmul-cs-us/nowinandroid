package com.starception.submission.shared.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotEquals

class ReciterSelectionTest {
    @Test
    fun selectionChangesNotifyExistingReadersAfterPreferencesAreSaved() {
        val store = com.starception.submission.shared.storage.InMemoryKeyValueStore()
        val reader = QuranReciterPreferences(store)
        val settings = QuranReciterPreferences(store)
        val source = QuranReciterSource("1-2", "Yasser Al-Dosari", "https://example.org/", setOf(1))
        val before = QuranReciterPreferences.changes.value
        settings.selectSource(source)
        assertNotEquals(before, QuranReciterPreferences.changes.value)
        assertEquals(source, reader.selectedSource())
        val selected = QuranReciterPreferences.changes.value
        settings.selectSource(null)
        assertNotEquals(selected, QuranReciterPreferences.changes.value)
        assertNull(reader.selectedSource())
    }

    @Test
    fun onlyAvailableSurahsResolve() {
        val source = QuranReciterSource(
            id = "example-1",
            displayName = "Example",
            baseUrl = "https://example.org/recitations/",
            availableSurahs = setOf(1, 2),
        )
        assertEquals("https://example.org/recitations/001.mp3", source.urlForSurah(1))
        assertNull(source.urlForSurah(3))
        assertNull(source.urlForSurah(115))
    }

    @Test
    fun pickerFiltersUnavailableAndDuplicateReciters() {
        val available = QuranReciterSource("a", "Alpha", "https://example.org/a", setOf(2))
        val unavailable = QuranReciterSource("b", "Beta", "https://example.org/b", setOf(1))
        assertEquals(
            listOf(available),
            selectableQuranReciters(2, listOf(unavailable, available, available)),
        )
    }

    @Test
    fun selectedSourceSurvivesRecreationAndDefaultClearsMetadata() {
        val store = com.starception.submission.shared.storage.InMemoryKeyValueStore()
        val source = QuranReciterSource("1-2", "Example", "https://example.org/", setOf(1, 114), "Hafs")
        QuranReciterPreferences(store).selectSource(source)
        val recreated = QuranReciterPreferences(store)
        assertEquals(source, recreated.selectedSource())
        assertEquals("1-2", recreated.selectedReciterId())
        recreated.select(null)
        assertNull(recreated.selectedSource())
        assertNull(recreated.selectedReciterId())
    }

    @Test
    fun damagedPreferenceDoesNotBreakPlayback() {
        val store = com.starception.submission.shared.storage.InMemoryKeyValueStore()
        store.putString("quran_reciter_source", "invalid json")
        assertNull(QuranReciterPreferences(store).selectedSource())
    }
}
