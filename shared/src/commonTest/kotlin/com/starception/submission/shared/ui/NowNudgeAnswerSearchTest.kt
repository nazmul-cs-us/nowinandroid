/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowNudgeAnswerSearchTest {
    private val etiquette = AnswerCandidate(
        candidateId = "sahih_bukhari-5195",
        searchable = "Foods Sahih al-Bukhari 5195 Narrated Abu Huraira: The Prophet " +
            "never criticized any food (he was invited to) but he used to eat if he " +
            "liked the food, and leave it if he disliked it.",
        quoteText = "The Prophet never criticized any food (he was invited to) but he " +
            "used to eat if he liked the food.",
        label = "About Sahih al-Bukhari",
        sourceLabel = "Sahih al-Bukhari 5195",
        kind = AnswerCandidate.Kind.BukhariHadith(5195),
    )

    private val shamayelPreference = AnswerCandidate(
        candidateId = "shamayel-121",
        searchable = "What Rasulullah ate and drank Shama'il At-Tirmidhi 121 'Ayeshah " +
            "Radiyallahu 'Anha says: Rasulullah loved halwa and honey.",
        quoteText = "'Ayeshah Radiyallahu 'Anha says: Rasulullah loved halwa and honey.",
        label = "About Shama'il At-Tirmidhi",
        sourceLabel = "Shama'il At-Tirmidhi 121",
        kind = AnswerCandidate.Kind.CollectionHadith("shamayele_tirmidhi_complete", 121),
    )

    private val abuDawudPreference = AnswerCandidate(
        candidateId = "sunan_abu_dawud-3784",
        searchable = "Sunan Abu Dawud 3784 Narrated Abdullah ibn Abbas: The food the " +
            "Messenger of Allah liked best was tharid made from bread and tharid " +
            "made from Hays.",
        quoteText = "The food the Messenger of Allah liked best was tharid made from " +
            "bread and tharid made from Hays.",
        label = "About Sunan Abu Dawud",
        sourceLabel = "Sunan Abu Dawud 3784",
        kind = AnswerCandidate.Kind.CollectionHadith("sunan_abu_dawud", 3784),
    )

    private val quranHeat = AnswerCandidate(
        candidateId = "quran-9-81",
        searchable = "At-Tawbah Quran 9:81 Those who remained behind rejoiced ... and " +
            "disliked to strive with their wealth and their lives ... Do not go forth " +
            "in the heat. Say, The fire of Hell is more intensive in heat.",
        quoteText = "The fire of Hell is more intensive in heat.",
        label = "About Quran",
        sourceLabel = "Quran 9:81",
        kind = AnswerCandidate.Kind.CollectionHadith("quran", 81),
    )

    @Test
    fun termsTokenizeWithStemmingAndStopWords() {
        assertEquals(
            listOf("prophet", "favourite", "food"),
            answerQueryTerms("prophet's favourite food?"),
        )
        // "dua" is excluded as a search term, matching the app module: it is
        // a Fortress topic bonus, not a corpus token.
        assertEquals(
            listOf("for", "travel"),
            answerQueryTerms("what is the dua for travel?"),
        )
    }

    @Test
    fun overlapUsesWholeWordsNotSubstrings() {
        val terms = listOf("prophet", "favourite", "food")
        // "disliked" contains "liked" and "heat" contains "eat" — neither may
        // count as a match for the question's terms.
        assertTrue(answerOverlapScore(etiquette.searchable, terms) > 0f)
        assertTrue(answerOverlapScore(quranHeat.searchable, terms) < 0.5f)
        assertTrue(answerOverlapScore(shamayelPreference.searchable, terms) > 0.5f)
    }

    @Test
    fun directPreferenceDetectionRejectsConditionalEtiquette() {
        assertTrue(containsDirectPreferenceStatement(shamayelPreference.searchable))
        assertTrue(containsDirectPreferenceStatement(abuDawudPreference.searchable))
        assertFalse(containsDirectPreferenceStatement(etiquette.searchable))
    }

    @Test
    fun prophetAttributeQuestionsAreDetected() {
        assertTrue(isProphetAttributeQuestion("prophet's favourite food?"))
        assertTrue(isProphetAttributeQuestion("How did the Messenger eat?"))
        assertFalse(isProphetAttributeQuestion("what did the Prophet say about patience?"))
    }

    @Test
    fun preferencePassagesWinProphetAttributeQuestions() {
        val best = selectAnswerCandidate(
            candidates = listOf(etiquette, shamayelPreference, abuDawudPreference, quranHeat),
            query = "prophet's favourite food?",
        )
        // Both direct-preference narrations outrank the full-word etiquette
        // hadith; between them the higher-overlap Abu Dawud tharid narration
        // wins.
        assertEquals(abuDawudPreference.candidateId, best?.candidateId)
    }

    @Test
    fun nonAttributeQuestionsRankByOverlapOnly() {
        val patienceFortress = AnswerCandidate(
            candidateId = "fortress-9-41",
            searchable = "Distress Fortress of the Muslim, invocation: O Allah I seek " +
                "refuge in You from worry and grief.",
            quoteText = "O Allah I seek refuge in You from worry and grief.",
            label = "Distress",
            sourceLabel = "Fortress of the Muslim",
            kind = AnswerCandidate.Kind.FortressDua(9, 41),
        )
        val best = selectAnswerCandidate(
            candidates = listOf(etiquette, patienceFortress),
            query = "dua for worry",
        )
        assertEquals(patienceFortress.candidateId, best?.candidateId)
    }

    @Test
    fun noOverlapReturnsNothing() {
        assertEquals(
            null,
            selectAnswerCandidate(listOf(etiquette), "rotation of the moon"),
        )
    }

    @Test
    fun answerSentencePicksTheMostOverlappingSentence() {
        val text = "Narrated Anas: Allah's Messenger went to his slave tailor, and he " +
            "was offered a dish of gourd of which he started eating. I have loved to " +
            "eat gourd since I saw Allah's Messenger eating it."
        val sentence = bestAnswerSentence(text, listOf("prophet", "favourite", "food"))
        assertTrue(sentence.contains("loved to eat gourd"))
    }
}
