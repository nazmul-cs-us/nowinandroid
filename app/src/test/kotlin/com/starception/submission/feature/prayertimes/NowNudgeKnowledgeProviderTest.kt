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
    fun questionTaskBuildsVerifiedOptionsAroundTheExactSource() = runBlocking {
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
        val question = candidate.quizQuestion!!

        assertEquals(DeenlyNudgeAction.PLAY_QUIZ, candidate.nudge.action)
        assertEquals(DeenlyActionIds.LEARNING_START_QUIZ, candidate.nudge.actionId)
        assertEquals(source.sourceText, candidate.nudge.supportingText)
        assertEquals(source.sourceText, question.sourceText)
        assertTrue(question.prompt.endsWith(source.sourceText))
        assertEquals(4, question.options.distinct().size)
        assertEquals("The Quran", question.options[question.correctOption])
        assertEquals(source.reference, question.sourceLabel)
        assertEquals(source.sourceText.sha256(), question.sourceTextSha256)
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
    fun ungroundedModelQuestionFallsBackToSourceLocationQuestion() = runBlocking {
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

        assertTrue(candidate.quizQuestion!!.prompt.startsWith("Which source"))
        assertEquals("The Quran", candidate.quizQuestion!!.options[candidate.quizQuestion!!.correctOption])
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

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString(separator = "") { "%02x".format(it) }
}
