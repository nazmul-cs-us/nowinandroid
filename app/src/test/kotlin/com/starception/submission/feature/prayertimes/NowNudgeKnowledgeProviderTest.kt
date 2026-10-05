/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.feature.prayertimes

import com.starception.submission.core.model.deenly.DeenlyActionIds
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
        var capturedVariation: Int? = null
        val provider = NowNudgeKnowledgeProvider(
            sourceLoader = GroundedKnowledgeSourceLoader { _, variation ->
                capturedVariation = variation
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
        assertEquals(3, capturedVariation)
        assertEquals("From Surah Al-Fatihah", candidate.nudge.label)
        assertEquals(source.sourceText, candidate.nudge.supportingText)
        assertEquals(source.reference, candidate.nudge.sourceLabel)
        assertEquals(DeenlyActionIds.LEARNING_OPEN_KNOWLEDGE, candidate.nudge.actionId)
        assertSame(target, candidate.target)
    }

    @Test
    fun missingOrInvalidModelDecisionKeepsTheExistingNudgeFallback() = runBlocking {
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

        assertNull(unavailableModel.load(date))
        assertNull(questionInsteadOfKnowledge.load(date))
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
