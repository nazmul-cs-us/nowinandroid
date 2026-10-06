/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.ml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeenlyKnowledgeModelTest {
    @Test
    fun parserAcceptsOnlyBoundedQuestionSchema() {
        assertEquals(
            DeenlyKnowledgeDecision.SourceLocationQuestion,
            DeenlyKnowledgeModel.parseDecision(
                """{"contentType":"question","questionKind":"source_location"}""",
                DeenlyKnowledgeTask.QUESTION,
            ),
        )
        assertNull(
            DeenlyKnowledgeModel.parseDecision(
                """{"contentType":"question","questionKind":"source_location","answer":"Quran"}""",
                DeenlyKnowledgeTask.QUESTION,
            ),
        )
    }

    @Test
    fun parserAcceptsSelfContainedQuestionWithExactAnswerAndEvidence() {
        val source = "The month of Ramadan is the one in which the Quran was revealed."
        assertEquals(
            DeenlyKnowledgeDecision.GroundedQuestion(
                questionKind = "time",
                question = "Which month is explicitly connected with the Quran's revelation?",
                answer = "Ramadan",
                evidence = "The month of Ramadan is the one in which the Quran was revealed.",
                options = listOf("Muharram", "Rajab", "Ramadan", "Shawwal"),
            ),
            DeenlyKnowledgeModel.parseDecision(
                rawOutput = """{"contentType":"question","questionKind":"time","question":"Which month is explicitly connected with the Quran's revelation?","answer":"Ramadan","evidence":"The month of Ramadan is the one in which the Quran was revealed.","options":["Muharram","Rajab","Ramadan","Shawwal"]}""",
                expectedTask = DeenlyKnowledgeTask.QUESTION,
                sourceText = source,
            ),
        )
    }

    @Test
    fun parserInsertsVerifiedAnswerWhenModelReturnsFourUniqueDistractors() {
        val source = "Amina planted five trees beside the path."
        val decision = DeenlyKnowledgeModel.parseDecision(
            rawOutput = """{"contentType":"question","questionKind":"number","question":"How many trees did Amina plant beside the path?","answer":"five","evidence":"Amina planted five trees beside the path.","options":["two","three","four","seven"]}""",
            expectedTask = DeenlyKnowledgeTask.QUESTION,
            sourceText = source,
        ) as DeenlyKnowledgeDecision.GroundedQuestion

        assertEquals(4, decision.options.size)
        assertEquals(4, decision.options.distinctBy(String::lowercase).size)
        assertEquals(1, decision.options.count { it == decision.answer })
    }

    @Test
    fun parserRejectsUngroundedOrPassageDependentQuestions() {
        val source = "He preferred dates."
        val ungroundedAnswer = """{"contentType":"question","questionKind":"food","question":"Which food did he prefer?","answer":"honey","evidence":"He preferred dates.","options":["dates","honey","bread","milk"]}"""
        val passageDependent = """{"contentType":"question","questionKind":"food","question":"According to this narration, which food was preferred?","answer":"dates","evidence":"He preferred dates.","options":["dates","honey","bread","milk"]}"""

        assertNull(
            DeenlyKnowledgeModel.parseDecision(
                ungroundedAnswer,
                DeenlyKnowledgeTask.QUESTION,
                source,
            ),
        )
        assertNull(
            DeenlyKnowledgeModel.parseDecision(
                passageDependent,
                DeenlyKnowledgeTask.QUESTION,
                source,
            ),
        )
    }

    @Test
    fun parserRejectsGeneratedSourceOrArabicFields() {
        assertNull(
            DeenlyKnowledgeModel.parseDecision(
                """{"contentType":"knowledge","title":"Mercy","body":"changed"}""",
                DeenlyKnowledgeTask.KNOWLEDGE,
            ),
        )
        assertNull(
            DeenlyKnowledgeModel.parseDecision(
                """{"contentType":"knowledge","title":"Mercy","arabicText":"generated"}""",
                DeenlyKnowledgeTask.KNOWLEDGE,
            ),
        )
    }

    @Test
    fun parserRejectsUnboundedOrControlCharacterTitles() {
        assertNull(
            DeenlyKnowledgeModel.parseDecision(
                """{"contentType":"knowledge","title":"${"a".repeat(85)}"}""",
                DeenlyKnowledgeTask.KNOWLEDGE,
            ),
        )
        assertNull(
            DeenlyKnowledgeModel.parseDecision(
                """{"contentType":"knowledge","title":"unsafe\ncontrol"}""",
                DeenlyKnowledgeTask.KNOWLEDGE,
            ),
        )
    }
}
