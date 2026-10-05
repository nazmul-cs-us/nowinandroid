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
