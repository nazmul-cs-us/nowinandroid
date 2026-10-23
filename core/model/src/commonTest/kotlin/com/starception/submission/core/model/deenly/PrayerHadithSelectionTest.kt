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

package com.starception.submission.core.model.deenly

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrayerHadithSelectionTest {
    private fun source(book: String, number: Int) = PrayerHadithCandidate(book, book, number,
        "A stored narration about the Friday prayer and its virtues. ".repeat(15))

    @Test
    fun modelCanOnlySelectAnOfferedSourceAndCannotReplaceItsWording() {
        val first = source("bukhari.db", 1)
        val second = source("muslim.db", 1)
        val candidates = listOf(first, second)
        assertEquals(second, PrayerHadithSelection.selected(second.sourceId, candidates))
        assertEquals(second.text, PrayerHadithSelection.selected(second.sourceId, candidates)?.text)
        assertNull(PrayerHadithSelection.selected("bukhari.db#999", candidates))
        assertNull(PrayerHadithSelection.selected("none", candidates))
        assertNull(PrayerHadithSelection.selected("A model-written narration", candidates))
        assertNull(PrayerHadithSelection.selected(first.sourceId, listOf(first, first)))
    }

    @Test
    fun longNarrationsRemainEligibleWhileOnlyThePromptExcerptIsBounded() {
        val candidate = source("bukhari.db", 1).copy(text = "Friday prayer narration. ".repeat(200))
        val shortlist = PrayerHadithSelection.shortlist(listOf(candidate), 0)
        assertEquals(candidate.text, shortlist.single().text)
        assertTrue(PrayerHadithSelection.prompt("Friday", shortlist).length < 1000)
    }

    @Test
    fun collectionIdentityPreventsSameNumberFromColliding() {
        val shortlist = PrayerHadithSelection.shortlist(listOf(source("a.db", 1), source("b.db", 1)), 0)
        assertEquals(2, shortlist.size)
        assertEquals(2, shortlist.map { it.sourceId }.toSet().size)
    }

    @Test
    fun manyMatchesFromOneBookDoNotDisplaceTheOtherBooks() {
        val candidates = (1..20).map { source("a.db", it) } + source("b.db", 1) + source("c.db", 1)
        val shortlist = PrayerHadithSelection.shortlist(candidates, 0)
        assertEquals(setOf("a.db", "b.db", "c.db"), shortlist.take(3).map { it.databaseFile }.toSet())
        assertTrue(shortlist.size <= 8)
        assertTrue(PrayerHadithSelection.prompt("Friday, Jumu'ah", shortlist).contains("Friday, Jumu'ah"))
    }
}
