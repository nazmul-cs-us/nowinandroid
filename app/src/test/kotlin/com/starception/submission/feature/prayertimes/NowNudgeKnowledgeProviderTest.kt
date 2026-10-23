/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.feature.prayertimes

import com.starception.submission.core.model.deenly.DeenlyActionIds
import com.starception.submission.core.model.deenly.DeenlyActivity
import com.starception.submission.core.model.deenly.DeenlyNudgeAction
import com.starception.submission.ml.DeenlyKnowledgeDecision
import com.starception.submission.ml.DeenlyKnowledgeTask
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NowNudgeKnowledgeProviderTest {
    private val date = LocalDate.of(2026, 10, 5)
    private val target = NowNudgeKnowledgeTarget.QuranAyah(1, 4)
    private val source = GroundedKnowledgeSource(
        id = "quran-1-4",
        collection = "quran",
        reference = "Quran 1:4",
        topic = "The Opener",
        sourceText = "Sovereign of the Day of Recompense",
        target = target,
    )

    @Test
    fun progressTracksActualReadsBeforePreparingTheGroundedSuggestion() = runBlocking {
        var reads = 0
        val updates = mutableListOf<com.starception.submission.core.model.deenly.InsightSearchProgress>()
        val provider = NowNudgeKnowledgeProvider(
            sourceLoader = GroundedKnowledgeSourceLoader { _, _ -> reads++; source },
            decisionGenerator = KnowledgeDecisionGenerator { _, _ ->
                assertEquals("Preparing your suggestion", updates.last().title)
                null
            },
        )
        provider.load(date, onProgress = { updates += it })
        val completed = updates.last { it.determinate }
        assertEquals(reads, completed.completed)
        assertEquals(reads, completed.total)
        assertTrue(completed.detail.contains("1 unique"))
        assertEquals(listOf(source.collection), updates.last().sources)
        assertTrue(updates.last().detail.contains(source.reference))
        assertEquals(source.sourceText, updates.last().previewText)
    }

    @Test
    fun generatedTitleWrapsTheExactSourceAndDestination() = runBlocking {
        var capturedSourceText: String? = null
        var capturedHash: String? = null
        var capturedSituation: String? = null
        val capturedVariations = mutableListOf<Int>()
        val provider = NowNudgeKnowledgeProvider(
            sourceLoader = GroundedKnowledgeSourceLoader { _, variation ->
                capturedVariations += variation
                source
            },
            decisionGenerator = KnowledgeDecisionGenerator { task, envelope ->
                assertEquals(DeenlyKnowledgeTask.KNOWLEDGE, task)
                capturedSourceText = envelope.sourceText
                capturedHash = envelope.sourceTextSha256
                capturedSituation = envelope.appSituation
                DeenlyKnowledgeDecision.Knowledge("  From Surah Al-Fatihah  ")
            },
        )

        val candidate = provider.load(
            date = date,
            variation = 3,
            appSituation = "Maghrib is current; audio idle",
        )!!

        assertEquals(source.sourceText, capturedSourceText)
        assertEquals(source.sourceText.sha256(), capturedHash)
        assertEquals("Maghrib is current; audio idle", capturedSituation)
        assertEquals(3, capturedVariations.first())
        assertTrue(capturedVariations.size > 1)
        assertEquals("From Surah Al-Fatihah", candidate.nudge.label)
        assertEquals(source.sourceText, candidate.nudge.supportingText)
        assertEquals(source.reference, candidate.nudge.sourceLabel)
        assertEquals(DeenlyActionIds.LEARNING_OPEN_KNOWLEDGE, candidate.nudge.actionId)
        assertSame(target, candidate.target)
    }

    @Test
    fun sourceLocationModelOutputFallsBackToTheGroundedKnowledgeCard() = runBlocking {
        val provider = NowNudgeKnowledgeProvider(
            sourceLoader = GroundedKnowledgeSourceLoader { _, _ -> source },
            decisionGenerator = KnowledgeDecisionGenerator { task, _ ->
                assertEquals(DeenlyKnowledgeTask.QUESTION, task)
                DeenlyKnowledgeDecision.SourceLocationQuestion
            },
        )

        val candidate = provider.load(
            date = date,
            task = DeenlyKnowledgeTask.QUESTION,
        )!!

        // "Which collection is this from" trivia was retired as a fallback; the
        // user sees the source itself instead.
        assertNull(candidate.quizQuestion)
        assertEquals(DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION, candidate.nudge.action)
        assertEquals(DeenlyActionIds.LEARNING_OPEN_KNOWLEDGE, candidate.nudge.actionId)
        assertEquals(source.sourceText, candidate.nudge.supportingText)
        assertEquals(source.reference, candidate.nudge.sourceLabel)
    }

    @Test
    fun groundedModelQuestionIsShownAsASelfContainedGlobalQuiz() = runBlocking {
        val questionSource = source.copy(
            id = "quran-2-185",
            reference = "Quran 2:185",
            sourceText = "The month of Ramadan is the one in which the Quran was revealed.",
        )
        val decision = DeenlyKnowledgeDecision.GroundedQuestion(
            questionKind = "time",
            question = "Which month is explicitly connected with the Quran's revelation?",
            answer = "Ramadan",
            evidence = questionSource.sourceText,
            options = listOf("Muharram", "Ramadan", "Rajab", "Shawwal"),
        )
        val provider = NowNudgeKnowledgeProvider(
            sourceLoader = GroundedKnowledgeSourceLoader { _, _ -> questionSource },
            decisionGenerator = KnowledgeDecisionGenerator { _, _ -> decision },
        )

        val candidate = provider.load(date, task = DeenlyKnowledgeTask.QUESTION)!!
        val question = candidate.quizQuestion!!

        assertEquals(decision.question, candidate.nudge.label)
        assertEquals("Muharram  \u2022  Ramadan  \u2022  Rajab  \u2022  Shawwal", candidate.nudge.supportingText)
        assertEquals(decision.question, question.prompt)
        assertEquals("Ramadan", question.options[question.correctOption])
        assertTrue(question.explanation.contains(decision.evidence))
        assertEquals(questionSource.reference, question.sourceLabel)
        assertEquals(questionSource.sourceText, question.sourceText)
    }

    @Test
    fun ungroundedModelQuestionFallsBackToTheGroundedKnowledgeCard() = runBlocking {
        val provider = NowNudgeKnowledgeProvider(
            sourceLoader = GroundedKnowledgeSourceLoader { _, _ -> source },
            decisionGenerator = KnowledgeDecisionGenerator { _, _ ->
                DeenlyKnowledgeDecision.GroundedQuestion(
                    questionKind = "food",
                    question = "Which food was preferred?",
                    answer = "honey",
                    evidence = "Honey was preferred.",
                    options = listOf("dates", "honey", "bread", "milk"),
                )
            },
        )

        val candidate = provider.load(date, task = DeenlyKnowledgeTask.QUESTION)!!

        // The ungrounded question never reaches the user; the verbatim source
        // card is shown instead of the retired source-location trivia.
        assertNull(candidate.quizQuestion)
        assertEquals(source.sourceText, candidate.nudge.supportingText)
        assertEquals(source.reference, candidate.nudge.sourceLabel)
    }

    @Test
    fun multipleGroundedSourcesAreRankedBeforeOneModelInvocation() = runBlocking {
        val general = source.copy(
            id = "quran-general",
            reference = "Quran 1:4",
            topic = "The Opener",
            sourceText = "Sovereign of the Day of Recompense",
        )
        val travel = GroundedKnowledgeSource(
            id = "hadith-travel",
            collection = "sahih_muslim",
            reference = "Sahih Muslim 1",
            topic = "Travel",
            sourceText = "Remember Allah throughout a journey and while travelling on the road.",
            target = NowNudgeKnowledgeTarget.Hadith("Sahih Muslim", "sahih_muslim.db", 1),
        )
        var modelInvocations = 0
        var selectedReference: String? = null
        val provider = NowNudgeKnowledgeProvider(
            sourceLoader = GroundedKnowledgeSourceLoader { _, variation ->
                if (variation % 2 == 0) general else travel
            },
            decisionGenerator = KnowledgeDecisionGenerator { _, envelope ->
                modelInvocations += 1
                selectedReference = envelope.reference
                DeenlyKnowledgeDecision.Knowledge("A grounded travel reminder")
            },
        )

        val result = provider.load(
            date = date,
            rankingContext = NowNudgeKnowledgeRankingContext(
                nowMinute = 12 * 60,
                completedPrayerCount = 5,
                activity = DeenlyActivity.DRIVING,
            ),
        )

        assertEquals(1, modelInvocations)
        assertEquals(travel.reference, selectedReference)
        assertEquals(travel.sourceText, result?.nudge?.supportingText)
    }

    @Test
    fun typedQuestionRanksMatchingTrustedSource() = runBlocking {
        val unrelated = source.copy(
            id = "unrelated",
            reference = "Quran 1:4",
            topic = "The Opener",
            sourceText = "Sovereign of the Day of Recompense",
        )
        val patience = source.copy(
            id = "patience",
            reference = "Quran 2:153",
            topic = "Patience",
            sourceText = "Seek help through patience and prayer.",
        )
        var selectedReference: String? = null
        val provider = NowNudgeKnowledgeProvider(
            sourceLoader = GroundedKnowledgeSourceLoader { _, variation ->
                if (variation % 2 == 0) unrelated else patience
            },
            decisionGenerator = KnowledgeDecisionGenerator { _, envelope ->
                selectedReference = envelope.reference
                DeenlyKnowledgeDecision.Knowledge("Patience and prayer")
            },
        )

        val result = provider.load(
            date = date,
            query = "What does the Quran say about patience?",
        )

        assertEquals(patience.reference, selectedReference)
        assertEquals(patience.sourceText, result?.nudge?.supportingText)
    }

    @Test
    fun dismissedCandidateIsRemovedBeforeModelInference() = runBlocking {
        val dismissed = source.copy(id = "dismissed", topic = "Travel")
        val eligible = source.copy(id = "eligible", reference = "Quran 2:186")
        var selectedReference: String? = null
        val provider = NowNudgeKnowledgeProvider(
            sourceLoader = GroundedKnowledgeSourceLoader { _, variation ->
                if (variation % 2 == 0) dismissed else eligible
            },
            decisionGenerator = KnowledgeDecisionGenerator { _, envelope ->
                selectedReference = envelope.reference
                DeenlyKnowledgeDecision.Knowledge("Grounded knowledge")
            },
        )

        provider.load(
            date = date,
            rankingContext = NowNudgeKnowledgeRankingContext(
                dismissedNudgeIds = setOf("model-knowledge-$date-${dismissed.id}"),
            ),
        )

        assertEquals(eligible.reference, selectedReference)
    }

    @Test
    fun exhaustedDismissedWindowStillReturnsAGroundedCandidate() = runBlocking {
        val provider = NowNudgeKnowledgeProvider(
            sourceLoader = GroundedKnowledgeSourceLoader { _, _ -> source },
            decisionGenerator = KnowledgeDecisionGenerator { _, _ ->
                DeenlyKnowledgeDecision.Knowledge("A grounded reminder")
            },
        )

        val result = provider.load(
            date = date,
            rankingContext = NowNudgeKnowledgeRankingContext(
                dismissedNudgeIds = setOf("model-knowledge-$date-${source.id}"),
            ),
        )

        assertEquals(source.sourceText, result?.nudge?.supportingText)
        assertEquals(source.reference, result?.nudge?.sourceLabel)
    }

    @Test
    fun collectionEngagementRaisesUserAffinity() = runBlocking {
        val lessPreferred = source.copy(
            id = "less-preferred",
            collection = "collection_a",
            reference = "Collection A 1",
        )
        val preferred = source.copy(
            id = "preferred",
            collection = "collection_b",
            reference = "Collection B 1",
        )
        var selectedReference: String? = null
        val engagementStore = object : KnowledgeEngagementStore {
            override fun snapshot(
                source: GroundedKnowledgeSource,
                date: LocalDate,
                nowEpochMillis: Long,
            ) = if (source.collection == preferred.collection) {
                KnowledgeEngagementSnapshot(collectionOpenedCount = 4)
            } else {
                KnowledgeEngagementSnapshot(collectionDismissedCount = 4)
            }

            override fun recordImpression(
                candidate: NowNudgeKnowledgeCandidate,
                date: LocalDate,
                nowEpochMillis: Long,
            ) = Unit

            override fun recordOpened(candidate: NowNudgeKnowledgeCandidate) = Unit
            override fun recordDismissed(candidate: NowNudgeKnowledgeCandidate) = Unit
        }
        val provider = NowNudgeKnowledgeProvider(
            sourceLoader = GroundedKnowledgeSourceLoader { _, variation ->
                if (variation % 2 == 0) lessPreferred else preferred
            },
            decisionGenerator = KnowledgeDecisionGenerator { _, envelope ->
                selectedReference = envelope.reference
                DeenlyKnowledgeDecision.Knowledge("Grounded knowledge")
            },
            engagementStore = engagementStore,
        )

        provider.load(date)

        assertEquals(preferred.reference, selectedReference)
    }

    @Test
    fun missingOrInvalidModelDecisionStillReturnsExactGroundedKnowledge() = runBlocking {
        val unavailableModel = NowNudgeKnowledgeProvider(
            sourceLoader = GroundedKnowledgeSourceLoader { _, _ -> source },
            decisionGenerator = KnowledgeDecisionGenerator { _, _ -> null },
        )
        val questionInsteadOfKnowledge = NowNudgeKnowledgeProvider(
            sourceLoader = GroundedKnowledgeSourceLoader { _, _ -> source },
            decisionGenerator = KnowledgeDecisionGenerator { _, _ ->
                DeenlyKnowledgeDecision.SourceLocationQuestion
            },
        )

        val unavailableResult = unavailableModel.load(date)!!
        val invalidResult = questionInsteadOfKnowledge.load(date)!!

        assertEquals("From ${source.topic}", unavailableResult.nudge.label)
        assertEquals(source.sourceText, unavailableResult.nudge.supportingText)
        assertEquals(source.reference, unavailableResult.nudge.sourceLabel)
        assertSame(source.target, unavailableResult.target)
        assertEquals("From ${source.topic}", invalidResult.nudge.label)
        assertEquals(source.sourceText, invalidResult.nudge.supportingText)
        assertEquals(source.reference, invalidResult.nudge.sourceLabel)
        assertSame(source.target, invalidResult.target)
    }

    @Test
    fun firstGreetingKeepsKnowledgeBodyCitationAndDestination() {
        val greeting = com.starception.submission.core.model.deenly.DeenlyNudge(
            id = "launch-greeting",
            action = com.starception.submission.core.model.deenly.DeenlyNudgeAction
                .OPEN_CONTEXTUAL_RECOMMENDATION,
            label = "Assalamu alaikum. Good morning",
        )
        val knowledge = NowNudgeKnowledgeProvider.buildCandidate(
            date = date,
            source = source,
            decision = DeenlyKnowledgeDecision.Knowledge("From Surah Al-Fatihah"),
        )

        val combined = combineLaunchGreetingWithKnowledge(
            greeting = greeting,
            knowledge = knowledge.nudge,
            includeGreeting = true,
        )

        assertEquals(greeting.label, combined.label)
        assertEquals(knowledge.nudge.id, combined.id)
        assertEquals(source.sourceText, combined.supportingText)
        assertEquals(source.reference, combined.sourceLabel)
        assertEquals(DeenlyActionIds.LEARNING_OPEN_KNOWLEDGE, combined.actionId)
        assertSame(source.target, knowledge.target)
    }

    @Test
    fun missingDatabaseSourceDoesNotInvokeTheModel() = runBlocking {
        var invoked = false
        val provider = NowNudgeKnowledgeProvider(
            sourceLoader = GroundedKnowledgeSourceLoader { _, _ -> null },
            decisionGenerator = KnowledgeDecisionGenerator { _, _ ->
                invoked = true
                DeenlyKnowledgeDecision.Knowledge("Should not be used")
            },
        )

        assertNull(provider.load(date))
        assertEquals(false, invoked)
    }

    @Test
    fun englishCardsRejectArabicOnlyLegacyHadithText() {
        assertFalse(
            "حدثنا يزيد قال أخبرنا المستلم بن سعيد عن أبيه عن جده".isEnglishGroundedText(),
        )
        assertTrue(
            "The Messenger of Allah taught his companions this grounded reminder."
                .isEnglishGroundedText(),
        )
    }

    // Fixtures reproduce the "prophet favourite food?" incident: a Bukhari
    // food-etiquette narration matches every query token, while the Shama'il
    // characteristics corpus holds the narrations that state the preference.

    private val foodQuestion = "prophet favourite food?"

    private val etiquetteHadith = GroundedKnowledgeSource(
        id = "sahih_bukhari-5195",
        collection = "sahih_al_bukhari",
        reference = "Sahih al-Bukhari 5195",
        topic = "Foods",
        sourceText = "Narrated Abu Huraira: The Prophet (ﷺ) never criticized any food " +
            "(he was invited to) but he used to eat if he liked the food, and leave it " +
            "if he disliked it.",
        target = NowNudgeKnowledgeTarget.Hadith(
            collectionName = "Sahih al-Bukhari",
            databaseFile = "sahih_bukhari.db",
            hadithNumber = 5195,
        ),
    )

    private val shamayelPreferenceHadith = GroundedKnowledgeSource(
        id = "shamayele_tirmidhi_complete-121",
        collection = "shama'il_at_tirmidhi",
        reference = "Shama'il At-Tirmidhi 121",
        topic = "What Rasulullah Sallallahu 'Alayhi Wasallam ate and drank",
        sourceText = "'Ayeshah Radiyallahu 'Anha says: \"Rasulullah SaIlallahu " +
            "'Alayhi Wasallam loved halwa and honey\".",
        target = NowNudgeKnowledgeTarget.Hadith(
            collectionName = "Shama'il At-Tirmidhi",
            databaseFile = "shamayele_tirmidhi_complete.db",
            hadithNumber = 121,
        ),
    )

    // The same preference family exists in Bukhari: "I have loved to eat gourd
    // since I saw Allah's Messenger eating it" (Bukhari 5219). Preference
    // narrations from ANY collection compete for the first attempts.
    private val bukhariPreferenceHadith = GroundedKnowledgeSource(
        id = "sahih_bukhari-5219",
        collection = "sahih_al_bukhari",
        reference = "Sahih al-Bukhari 5219",
        topic = "Foods",
        sourceText = "Narrated Anas: Allah's Messenger (ﷺ) went to (the house of) " +
            "his slave tailor, and he was offered (a dish of) gourd of which he " +
            "started eating. I have loved to eat gourd since I saw Allah's " +
            "Messenger (ﷺ) eating it.",
        target = NowNudgeKnowledgeTarget.Hadith(
            collectionName = "Sahih al-Bukhari",
            databaseFile = "sahih_bukhari.db",
            hadithNumber = 5219,
        ),
    )

    // A Shama'il source that only mentions the Prophet: matches one term of
    // the food question, so it must NOT be promoted over real matches.
    private val weakShamayelHadith = GroundedKnowledgeSource(
        id = "shamayele_tirmidhi_complete-160",
        collection = "shama'il_at_tirmidhi",
        reference = "Shama'il At-Tirmidhi 160",
        topic = "The Prophet's character",
        sourceText = "Rasulullah Sallallahu 'Alayhi Wasallam was the most " +
            "truthful of people.",
        target = NowNudgeKnowledgeTarget.Hadith(
            collectionName = "Shama'il At-Tirmidhi",
            databaseFile = "shamayele_tirmidhi_complete.db",
            hadithNumber = 160,
        ),
    )

    private fun ranked(source: GroundedKnowledgeSource, score: Float = 0.5f) =
        RankedKnowledgeSource(source = source, score = score)

    @Test
    fun prophetAttributeQuestionDetection() {
        assertTrue(isProphetAttributeQuestion(foodQuestion))
        assertTrue(isProphetAttributeQuestion("What did the Messenger eat?"))
        assertTrue(isProphetAttributeQuestion("How did the Prophet pray?"))
        // Teachings and duas route through the general hadith, Quran, and
        // Fortress order, not the Prophet's-person path.
        assertFalse(isProphetAttributeQuestion("what did the Prophet say about patience?"))
        assertFalse(isProphetAttributeQuestion("Prophet's dua for travel"))
        assertFalse(isProphetAttributeQuestion("what to say before eating"))
        assertFalse(isProphetAttributeQuestion(null))
    }

    @Test
    fun directPreferenceStatementsExcludeConditionalEtiquette() {
        assertTrue(containsDirectPreferenceStatement(shamayelPreferenceHadith.sourceText))
        assertTrue(containsDirectPreferenceStatement(bukhariPreferenceHadith.sourceText))
        assertFalse(containsDirectPreferenceStatement(etiquetteHadith.sourceText))
        // "disliked X" is itself a (negative) preference statement.
        assertTrue(containsDirectPreferenceStatement("He disliked bitter food."))
    }

    @Test
    fun preferenceQuestionsTryDirectPreferenceSourcesFromAnyCollectionFirst() {
        // The pipeline hands attempts in overlap order: full-overlap sources
        // (etiquette, Bukhari's gourd narration) before the partial-overlap
        // Shama'il narration.
        val attempts = listOf(
            ranked(etiquetteHadith),
            ranked(bukhariPreferenceHadith),
            ranked(shamayelPreferenceHadith),
        )
        val ordered = orderAnswerAttempts(attempts, foodQuestion)

        // Both preference narrations front the etiquette hadith, keeping their
        // overlap order: Bukhari's gourd narration (full overlap) first, then
        // Shama'il's halwa-and-honey narration.
        assertEquals(bukhariPreferenceHadith.id, ordered[0].source.id)
        assertEquals(shamayelPreferenceHadith.id, ordered[1].source.id)
        assertEquals(etiquetteHadith.id, ordered[2].source.id)
    }

    @Test
    fun weakMatchesAreNotPromotedByPreferenceAlone() {
        val attempts = listOf(
            ranked(etiquetteHadith),
            ranked(weakShamayelHadith),
        )
        val ordered = orderAnswerAttempts(attempts, foodQuestion)

        // The weak Shama'il match carries only one of the question's terms;
        // the full-overlap etiquette hadith keeps the first attempt.
        assertEquals(etiquetteHadith.id, ordered.first().source.id)
    }

    @Test
    fun nonProphetQuestionsKeepTheOverlapOrder() {
        val attempts = listOf(
            ranked(etiquetteHadith),
            ranked(shamayelPreferenceHadith),
            ranked(bukhariPreferenceHadith),
        )
        val ordered = orderAnswerAttempts(attempts, "what to say before eating")

        assertEquals(etiquetteHadith.id, ordered.first().source.id)
    }

    @Test
    fun compactPreferenceSpansScoreAboveVerboseEtiquetteQuotes() {
        val honeyAnswer = groundedAnswerQuality(
            answer = "honey",
            evidence = "'Ayeshah Radiyallahu 'Anha says: \"Rasulullah SaIlallahu " +
                "'Alayhi Wasallam loved halwa and honey\".",
            source = shamayelPreferenceHadith,
            query = foodQuestion,
        )
        val etiquetteAnswer = groundedAnswerQuality(
            answer = "but he used to eat if he liked the food",
            evidence = "The Prophet (ﷺ) never criticized any food (he was invited to) " +
                "but he used to eat if he liked the food",
            source = etiquetteHadith,
            query = foodQuestion,
        )

        assertTrue(honeyAnswer >= GOOD_ANSWER_SCORE)
        assertTrue(etiquetteAnswer < GOOD_ANSWER_SCORE)
        assertTrue(honeyAnswer > etiquetteAnswer)
    }

    @Test
    fun preferenceAdjacencyRequiresTheVerbBeforeTheAnswerSpan() {
        assertTrue(
            isPreferenceAdjacentAnswer(
                answer = "honey",
                evidence = "Rasulullah Sallallahu 'Alayhi Wasallam loved halwa and honey",
            ),
        )
        assertFalse(
            isPreferenceAdjacentAnswer(
                answer = "but he used to eat if he liked the food",
                evidence = "The Prophet never criticized any food (he was invited to) " +
                    "but he used to eat if he liked the food",
            ),
        )
        // "he used to eat if he liked the food": the conditional clause
        // follows a preference verb but is etiquette, not a stated favourite.
        assertFalse(
            isPreferenceAdjacentAnswer(
                answer = "if he liked the food",
                evidence = "he used to eat if he liked the food, and leave it if he disliked it",
            ),
        )
    }

    @Test
    fun etiquetteQuoteDoesNotStopTheAnswerSearch() = runBlocking {
        val requestedReferences = mutableListOf<String>()
        val provider = NowNudgeKnowledgeProvider(
            sourceLoader = object : GroundedKnowledgeSourceLoader {
                override suspend fun load(date: LocalDate, variation: Int): GroundedKnowledgeSource? = null

                override suspend fun search(
                    query: String,
                    limit: Int,
                ): List<GroundedKnowledgeSource> = listOf(etiquetteHadith, shamayelPreferenceHadith)
            },
            decisionGenerator = KnowledgeDecisionGenerator { _, envelope ->
                requestedReferences += envelope.reference
                when (envelope.reference) {
                    shamayelPreferenceHadith.reference ->
                        DeenlyKnowledgeDecision.GroundedAnswer(
                            answer = "honey",
                            evidence = "'Ayeshah Radiyallahu 'Anha says: \"Rasulullah " +
                                "SaIlallahu 'Alayhi Wasallam loved halwa and honey\".",
                        )
                    else ->
                        DeenlyKnowledgeDecision.GroundedAnswer(
                            answer = "but he used to eat if he liked the food",
                            evidence = "The Prophet (ﷺ) never criticized any food " +
                                "(he was invited to) but he used to eat if he liked the food",
                        )
                }
            },
        )

        val candidate = provider.load(
            date = date,
            query = foodQuestion,
            task = DeenlyKnowledgeTask.ANSWER,
        )!!

        // The Shama'il preference narration is tried first, so its answer wins
        // even though the etiquette hadith would also have grounded.
        assertEquals(shamayelPreferenceHadith.reference, requestedReferences.first())
        assertEquals(shamayelPreferenceHadith.reference, candidate.nudge.sourceLabel)
        assertTrue(candidate.nudge.supportingText!!.startsWith("\u201Choney\u201D"))
    }

    @Test
    fun lowQualityGroundedAnswerStillPresentsWhenNothingBetterGrounds() = runBlocking {
        val provider = NowNudgeKnowledgeProvider(
            sourceLoader = object : GroundedKnowledgeSourceLoader {
                override suspend fun load(date: LocalDate, variation: Int): GroundedKnowledgeSource? = null

                override suspend fun search(
                    query: String,
                    limit: Int,
                ): List<GroundedKnowledgeSource> = listOf(etiquetteHadith)
            },
            decisionGenerator = KnowledgeDecisionGenerator { _, _ ->
                DeenlyKnowledgeDecision.GroundedAnswer(
                    answer = "but he used to eat if he liked the food",
                    evidence = "The Prophet (ﷺ) never criticized any food (he was invited to) " +
                        "but he used to eat if he liked the food",
                )
            },
        )

        val candidate = provider.load(
            date = date,
            query = foodQuestion,
            task = DeenlyKnowledgeTask.ANSWER,
        )!!

        // Never worse than before: a grounded answer beats the fallback card.
        assertEquals(etiquetteHadith.reference, candidate.nudge.sourceLabel)
        assertTrue(
            candidate.nudge.supportingText!!.startsWith(
                "\u201Cbut he used to eat if he liked the food\u201D",
            ),
        )
    }

    @Test
    fun compactConditionalSpansDoNotStopThePreferenceSearch() = runBlocking {
        // "if he liked the food" is a compact, span-valid quote from the
        // etiquette narration — but it answers nothing. It must not trigger
        // the early stop; every attempt keeps running and the best grounded
        // answer is still presented.
        val etiquetteVariant = etiquetteHadith.copy(
            id = "sahih_bukhari-5243",
            reference = "Sahih al-Bukhari 5243",
            target = NowNudgeKnowledgeTarget.Hadith(
                collectionName = "Sahih al-Bukhari",
                databaseFile = "sahih_bukhari.db",
                hadithNumber = 5243,
            ),
        )
        val invocations = mutableListOf<String>()
        val provider = NowNudgeKnowledgeProvider(
            sourceLoader = object : GroundedKnowledgeSourceLoader {
                override suspend fun load(date: LocalDate, variation: Int): GroundedKnowledgeSource? = null

                override suspend fun search(
                    query: String,
                    limit: Int,
                ): List<GroundedKnowledgeSource> = listOf(etiquetteHadith, etiquetteVariant)
            },
            decisionGenerator = KnowledgeDecisionGenerator { _, envelope ->
                invocations += envelope.reference
                DeenlyKnowledgeDecision.GroundedAnswer(
                    answer = "if he liked the food",
                    evidence = "he used to eat if he liked the food, and leave it " +
                        "if he disliked it",
                )
            },
        )

        val candidate = provider.load(
            date = date,
            query = foodQuestion,
            task = DeenlyKnowledgeTask.ANSWER,
        )!!

        // Both sources were tried (no early stop on the conditional span).
        assertEquals(2, invocations.size)
        assertTrue(candidate.nudge.supportingText!!.startsWith("\u201Cif he liked the food\u201D"))
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString(separator = "") { "%02x".format(it) }
}
