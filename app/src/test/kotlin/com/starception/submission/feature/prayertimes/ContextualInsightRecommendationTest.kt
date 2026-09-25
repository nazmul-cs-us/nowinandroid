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

package com.starception.submission.feature.prayertimes

import com.starception.submission.core.duadatabase.Dua
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class ContextualInsightRecommendationTest {

    @Test
    fun fridayAlwaysRecommendsAlKahf() {
        val result = buildContextualInsightRecommendation(
            date = LocalDate.of(2026, 8, 7),
            time = LocalTime.of(18, 0),
            fortressDuasByChapter = mapOf(27 to listOf(sampleDua())),
            locale = Locale.ENGLISH,
        )

        val target = result.target as ContextualRecommendationTarget.Surah
        assertEquals(18, target.number)
        assertEquals("Quran · Surah 18 · Friday", result.footerText)
    }

    @Test
    fun evenDayUsesTimeAppropriateFortressDuaWhenAvailable() {
        val dua = sampleDua(chapterId = 27, translation = "We have entered the evening.")
        val result = buildContextualInsightRecommendation(
            date = LocalDate.of(2026, 8, 2),
            time = LocalTime.of(17, 30),
            fortressDuasByChapter = mapOf(27 to listOf(dua)),
            locale = Locale.ENGLISH,
        )

        val target = result.target as ContextualRecommendationTarget.FortressDua
        assertEquals(dua, target.dua)
        assertEquals("We have entered the evening.", result.supportingText)
        assertTrue(result.footerText.startsWith("Fortress of the Muslim"))
    }

    @Test
    fun oddDayUsesQuranRecommendation() {
        val result = buildContextualInsightRecommendation(
            date = LocalDate.of(2026, 8, 3),
            time = LocalTime.of(17, 30),
            fortressDuasByChapter = mapOf(27 to listOf(sampleDua())),
            locale = Locale.ENGLISH,
        )

        assertTrue(result.target is ContextualRecommendationTarget.Surah)
        assertTrue(result.footerText.startsWith("Quran · Surah"))
    }

    @Test
    fun bukhariDayRecommendsPlayableCanonicalBook() {
        val result = buildContextualInsightRecommendation(
            date = LocalDate.of(2026, 8, 25),
            time = LocalTime.of(17, 30),
            fortressDuasByChapter = emptyMap(),
            locale = Locale.ENGLISH,
        )

        val target = result.target as ContextualRecommendationTarget.Bukhari
        assertTrue(target.book.id in listOf(66, 80, 81))
        assertEquals(
            target.book.hadithCount,
            target.book.lastHadithId - target.book.firstHadithId + 1,
        )
        assertTrue(result.footerText.startsWith("Sahih al-Bukhari · Book"))
    }

    @Test
    fun alternateHadithDayRecommendsPlayableShamayelBook() {
        val result = buildContextualInsightRecommendation(
            date = LocalDate.of(2026, 8, 4),
            time = LocalTime.of(17, 30),
            fortressDuasByChapter = emptyMap(),
            locale = Locale.ENGLISH,
        )

        val target = result.target as ContextualRecommendationTarget.Shamayel
        assertTrue(target.book.id in listOf(35, 36, 48))
        assertEquals(
            target.book.hadithCount,
            target.book.lastHadithId - target.book.firstHadithId + 1,
        )
        assertTrue(result.footerText.startsWith("Shama'il At-Tirmidhi · Book"))
    }

    @Test
    fun missingFortressDataFallsBackToQuran() {
        val result = buildContextualInsightRecommendation(
            date = LocalDate.of(2026, 8, 2),
            time = LocalTime.of(17, 30),
            fortressDuasByChapter = emptyMap(),
            locale = Locale.ENGLISH,
        )

        assertTrue(result.target is ContextualRecommendationTarget.Surah)
    }

    @Test
    fun drivingRecommendationAlwaysTargetsDirectBukhariPlayback() {
        val result = buildDrivingInsightRecommendation(
            date = LocalDate.of(2026, 8, 30),
            time = LocalTime.of(17, 30),
        )

        val target = result.target as ContextualRecommendationTarget.Bukhari
        assertTrue(target.book.id in listOf(66, 80, 81))
        assertTrue(result.title.startsWith("Listen:"))
        assertTrue(result.footerText.startsWith("Driving mode"))
    }

    private fun sampleDua(
        chapterId: Int = 27,
        translation: String = "A contextual dua.",
    ) = Dua(
        id = 1,
        chapterId = chapterId,
        chapterTitle = "Words of remembrance for morning and evening",
        position = 1,
        arabic = "ذِكْرٌ",
        transliteration = "Dhikr",
        translation = translation,
        context = null,
        instruction = null,
        note = null,
        postContext = null,
        description = null,
    )
}
