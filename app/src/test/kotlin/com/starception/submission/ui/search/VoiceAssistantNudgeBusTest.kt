/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.ui.search

import com.starception.submission.core.model.deenly.DeenlyNudge
import com.starception.submission.core.model.deenly.DeenlyNudgeAction
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAssistantNudgeBusTest {
    @Test
    fun pullRequestStartsBotTurnAndCompletionPublishesResult() = runBlocking {
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            VoiceAssistantNudgeBus.suggestionRequests.first()
        }

        VoiceAssistantNudgeBus.requestSuggestion()

        assertTrue(VoiceAssistantNudgeBus.generatingSuggestion.value)
        assertTrue(request.await() > 0L)

        val ready = async(start = CoroutineStart.UNDISPATCHED) {
            VoiceAssistantNudgeBus.suggestionReady.first()
        }
        val nudge = DeenlyNudge(
            id = "model-knowledge-test",
            action = DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
            label = "A grounded reminder",
        )

        VoiceAssistantNudgeBus.completeSuggestion(nudge)

        assertEquals(nudge.id, ready.await())
        assertEquals(nudge, VoiceAssistantNudgeBus.nudge.value)
        assertFalse(VoiceAssistantNudgeBus.generatingSuggestion.value)
        VoiceAssistantNudgeBus.clear(nudge.id)
    }
}
