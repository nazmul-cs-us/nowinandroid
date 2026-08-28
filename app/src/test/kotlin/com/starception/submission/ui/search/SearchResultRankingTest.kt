package com.starception.submission.ui.search

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchResultRankingTest {

    @Test
    fun incompleteAllahPrefix_withSurahMatches_suppressesGenericVerses() {
        assertTrue(
            shouldSuppressAmbiguousAllahPrefixVerses(
                query = "ala",
                hasSurahMatches = true,
            ),
        )
    }

    @Test
    fun completedAllahQuery_keepsVerseMatches() {
        assertFalse(
            shouldSuppressAmbiguousAllahPrefixVerses(
                query = "allah",
                hasSurahMatches = true,
            ),
        )
    }

    @Test
    fun incompletePrefix_withoutSurahMatches_keepsVerseMatches() {
        assertFalse(
            shouldSuppressAmbiguousAllahPrefixVerses(
                query = "ala",
                hasSurahMatches = false,
            ),
        )
    }

    @Test
    fun multiWordVerseQuery_keepsVerseMatches() {
        assertFalse(
            shouldSuppressAmbiguousAllahPrefixVerses(
                query = "allah protects",
                hasSurahMatches = true,
            ),
        )
    }
}
