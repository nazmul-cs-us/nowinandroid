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

package com.starception.submission.shared.quran

import kotlin.test.Test
import kotlin.test.assertEquals

class QuranVerseRepositoryTest {
    private val verses = listOf(
        QuranVerse(1, 1, 1, "\uFEFFبِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ", 1, 1),
        QuranVerse(2, 1, 2, "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ", 1, 1),
    )

    @Test
    fun cleansDatabaseBomAndFormatsMetadata() {
        assertEquals("بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ", cleanQuranText(verses.first().arabicText))
        assertEquals("Ayah 2 · Page 1 · Juz 1", verses.last().metadataLabel())
    }

    @Test
    fun searchesArabicWithoutRequiringDatabaseDiacritics() {
        assertEquals(listOf(verses.first()), filterQuranVerses(verses, "الله الرحمن"))
        assertEquals(listOf(verses.last()), filterQuranVerses(verses, "الحمد لله"))
    }

    @Test
    fun searchesByExactAyahNumberAndPreservesOrderForBlankQuery() {
        assertEquals(listOf(verses.last()), filterQuranVerses(verses, "2"))
        assertEquals(verses, filterQuranVerses(verses, "  "))
    }
}
