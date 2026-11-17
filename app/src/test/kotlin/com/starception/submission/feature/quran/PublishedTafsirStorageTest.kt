package com.starception.submission.feature.quran

import com.starception.submission.download.AssetDownloadViewModel
import org.junit.Assert.*
import org.junit.Test

class PublishedTafsirStorageTest {
    @Test fun everyPublishedEditionAppearsInTheTafsirStorageGroupWithItsOwnLanguage() {
        PublishedTafsirs.editions.forEach { edition ->
            assertEquals("Tafsir", AssetDownloadViewModel.categoryGroup(edition.category))
            assertTrue(AssetDownloadViewModel.formatCategoryName(edition.category).contains(edition.englishLanguageName))
        }
    }
    @Test fun storageSortingDoesNotMergeTafsirWithAnotherContentGroup() {
        val keys = listOf("quran_core", "quran_tafsir_en", "hadith_bukhari", "quran_audio_arabic", "model_tts_en",
            "model_now_nudge", "model_whisper", "other", "unknown")
        val groups = keys.groupBy(AssetDownloadViewModel::categoryGroup)
            .toSortedMap(compareBy { AssetDownloadViewModel.groupSortOrder(it) })
        assertEquals(keys.size, groups.values.sumOf { it.size })
        assertEquals(listOf("Holy Quran", "Tafsir"), groups.keys.take(2))
    }
}
