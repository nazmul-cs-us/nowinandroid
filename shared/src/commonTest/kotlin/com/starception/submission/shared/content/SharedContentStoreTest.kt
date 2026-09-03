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
        assertEquals(setOf(1182, 2018), restored.bookmarkedNewsIds())
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

    @Test
    fun canonicalFeedStateRoundTrips() {
        val keyValues = InMemoryKeyValueStore()
        val content = SharedContentStore(keyValues)

        content.setFollowedTopicIds(setOf(25, 7, -1))
        content.setTopicFollowed(11, followed = true)
        content.toggleFollowedTopic(25)
        content.setBookmarkedNewsIds(setOf(101, 2001, 0))
        content.setNewsBookmarked(1182, bookmarked = true)
        content.toggleNewsBookmark(101)
        content.markNewsViewed(2001)
        content.markNewsViewed(-1)
        content.setOnboardingHidden(true)
        content.setTopicOrder(listOf(25, 7, 25, -1, 11))

        val restored = SharedContentStore(keyValues)
        assertEquals(setOf(7, 11), restored.followedTopicIds())
        assertEquals(setOf(2001, 1182), restored.bookmarkedNewsIds())
        assertEquals(setOf(2001), restored.viewedNewsIds())
        assertTrue(restored.onboardingHidden())
        assertEquals(listOf(25, 7, 11), restored.topicOrder())
    }

    @Test
    fun legacyBookmarksMigrateOnceToGeneratedNewsIds() {
        val keyValues = InMemoryKeyValueStore().apply {
            putString("shared_saved_surahs", "1|114")
            putString("shared_saved_bukhari", "3|80")
            putString(
                "shared_saved_topic_articles",
                "11:128|11:167|11:127|25:182|8:3|99:1",
            )
            putString("shared_bookmarked_news_ids", "42")
        }
        val content = SharedContentStore(keyValues)

        assertEquals(setOf(42, 101, 140, 1182, 2001, 2114), content.bookmarkedNewsIds())
        assertEquals(setOf(3, 80), content.savedBukhariBooks())

        content.setNewsBookmarked(2001, bookmarked = false)
        assertFalse(2001 in SharedContentStore(keyValues).bookmarkedNewsIds())
        assertEquals(setOf(114), content.bookmarkedSurahs())
    }

    @Test
    fun legacyTogglesSynchronizeCanonicalNewsBookmarks() {
        val content = SharedContentStore(InMemoryKeyValueStore())

        content.toggleSurah(18)
        assertTrue(2018 in content.bookmarkedNewsIds())
        content.toggleSurah(18)
        assertFalse(2018 in content.bookmarkedNewsIds())

        content.toggleTopicArticle(11, 128)
        content.toggleTopicArticle(25, 182)
        assertTrue(101 in content.bookmarkedNewsIds())
        assertTrue(1182 in content.bookmarkedNewsIds())
        content.toggleTopicArticle(11, 128)
        assertFalse(101 in content.bookmarkedNewsIds())
    }

    @Test
    fun canonicalNewsBookmarksSynchronizeSurahsWithoutChangingBukhariBooks() {
        val content = SharedContentStore(InMemoryKeyValueStore())
        content.toggleBukhariBook(80)

        content.setBookmarkedNewsIds(setOf(42, 2001, 2114))
        assertEquals(setOf(1, 114), content.bookmarkedSurahs())

        content.setNewsBookmarked(2001, bookmarked = false)
        assertEquals(setOf(114), content.bookmarkedSurahs())

        content.toggleNewsBookmark(2055)
        assertEquals(setOf(55, 114), content.bookmarkedSurahs())
        content.toggleNewsBookmark(2055)
        assertEquals(setOf(114), content.bookmarkedSurahs())

        content.setBookmarkedNewsIds(setOf(42))
        assertTrue(content.bookmarkedSurahs().isEmpty())
        assertEquals(setOf(80), content.savedBukhariBooks())
    }
}
