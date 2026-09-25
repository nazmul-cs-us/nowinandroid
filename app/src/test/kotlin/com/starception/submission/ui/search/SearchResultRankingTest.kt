/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
