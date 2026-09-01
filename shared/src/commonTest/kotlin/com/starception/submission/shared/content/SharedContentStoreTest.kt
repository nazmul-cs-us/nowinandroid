package com.starception.submission.shared.content

import com.starception.submission.shared.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedContentStoreTest {
    @Test
    fun stateRoundTripsAcrossStoreInstances() {
        val keyValues = InMemoryKeyValueStore()
        val content = SharedContentStore(keyValues)

        content.saveProfile(LocalProfile("Amina", 20))
        content.toggleSurah(18)
        content.toggleBukhariBook(80)
        content.toggleTopicArticle(25, 182)
        content.toggleInterest("Quran")
        content.toggleLesson(1)

        val restored = SharedContentStore(keyValues)
        assertEquals(LocalProfile("Amina", 20), restored.profile())
        assertEquals(setOf(18), restored.bookmarkedSurahs())
        assertEquals(setOf(80), restored.savedBukhariBooks())
        assertEquals(setOf("25:182"), restored.bookmarkedTopicArticles())
        assertEquals(setOf("7"), restored.interests())
        assertEquals(setOf(1), restored.completedLessons())
    }

    @Test
    fun togglesRemoveExistingValuesAndRejectInvalidCatalogIds() {
        val content = SharedContentStore(InMemoryKeyValueStore())

        assertTrue(18 in content.toggleSurah(18))
        assertFalse(18 in content.toggleSurah(18))
        assertTrue(content.toggleSurah(999).isEmpty())
        assertTrue(content.toggleBukhariBook(0).isEmpty())
        assertTrue("11:5" in content.toggleTopicArticle(11, 5))
        assertFalse("11:5" in content.toggleTopicArticle(11, 5))
    }

    @Test
    fun legacyInterestNamesMigrateToAndroidTopicIds() {
        val keyValues = InMemoryKeyValueStore().apply {
            putString("shared_interests", "Quran|Hadith|Travel|Family|Learning|Character")
        }

        assertEquals(
            setOf("7", "8", "25", "29", "34", "36"),
            SharedContentStore(keyValues).interests(),
        )
    }
}
