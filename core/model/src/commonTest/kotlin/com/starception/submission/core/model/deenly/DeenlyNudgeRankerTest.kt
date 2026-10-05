/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package com.starception.submission.core.model.deenly

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeenlyNudgeRankerTest {
    @Test
    fun catalogCoversEveryFeatureAreaWithUniqueStableIds() {
        assertEquals(
            DeenlyActionCatalog.all.size,
            DeenlyActionCatalog.all.map(DeenlyActionDefinition::id).distinct().size,
        )
        assertEquals(
            DeenlyFeatureArea.entries.toSet(),
            DeenlyActionCatalog.all.map(DeenlyActionDefinition::featureArea).toSet(),
        )
    }

    @Test
    fun feelingBlessedCollectionsAreSeparateAudioActions() {
        val bukhari = DeenlyActionCatalog.definition(
            DeenlyActionIds.HADITH_FEELING_BLESSED_BUKHARI,
        )
        val tirmidhi = DeenlyActionCatalog.definition(
            DeenlyActionIds.HADITH_FEELING_BLESSED_TIRMIDHI,
        )

        assertNotNull(bukhari)
        assertNotNull(tirmidhi)
        assertTrue(bukhari.startsAudio)
        assertTrue(tirmidhi.startsAudio)
        assertEquals(DeenlyFeatureArea.HADITH, bukhari.featureArea)
        assertEquals(DeenlyCompletionSignal.PLAYBACK_COMPLETED, tirmidhi.completionSignal)
    }

    @Test
    fun higherContextualValueWinsRegardlessOfInputOrder() {
        val weaker = candidate(
            id = "open-search",
            actionId = DeenlyActionIds.SEARCH_OPEN,
            signals = DeenlyRankingSignals(contextMatch = 0.1f),
        )
        val stronger = candidate(
            id = "resume-quran",
            actionId = DeenlyActionIds.QURAN_RESUME_READING,
            signals = DeenlyRankingSignals(
                contextMatch = 0.9f,
                unfinishedProgress = 0.8f,
            ),
        )

        val result = rankDeenlyNudges(listOf(weaker, stronger), DeenlyRankingContext())

        assertEquals("resume-quran", result.first().candidate.nudge.id)
        assertEquals(DEENLY_STARTER_RANKING_MODEL.version, result.first().modelVersion)
    }

    @Test
    fun activeMediaSuppressesBothFeelingBlessedCandidates() {
        val result = rankDeenlyNudges(
            candidates = listOf(
                candidate("bukhari", DeenlyActionIds.HADITH_FEELING_BLESSED_BUKHARI),
                candidate("tirmidhi", DeenlyActionIds.HADITH_FEELING_BLESSED_TIRMIDHI),
                candidate("saved", DeenlyActionIds.SAVED_OPEN),
            ),
            context = DeenlyRankingContext(isMediaPlaying = true),
        )

        assertEquals(listOf("saved"), result.map { it.candidate.nudge.id })
    }

    @Test
    fun cooldownDismissalAndOfflineAvailabilityAreHardEligibilityRules() {
        val result = rankDeenlyNudges(
            candidates = listOf(
                candidate(
                    id = "cooldown",
                    actionId = DeenlyActionIds.QURAN_RESUME_READING,
                    minutesSinceLastShown = 10,
                ),
                candidate(
                    id = "dismissed",
                    actionId = DeenlyActionIds.SAVED_OPEN,
                ),
                candidate(
                    id = "network",
                    actionId = DeenlyActionIds.DOWNLOAD_REQUIRED_CONTENT,
                    isAvailableOffline = false,
                ),
                candidate(
                    id = "offline-ready",
                    actionId = DeenlyActionIds.DOWNLOAD_REQUIRED_CONTENT,
                    isAvailableOffline = true,
                ),
            ),
            context = DeenlyRankingContext(
                isOnline = false,
                dismissedNudgeIds = setOf("dismissed"),
            ),
        )

        assertEquals(listOf("offline-ready"), result.map { it.candidate.nudge.id })
    }

    @Test
    fun interactionOutcomesProduceBoundedTrainingRewards() {
        assertEquals(1f, DeenlyInteractionType.COMPLETED.trainingReward())
        assertEquals(0.7f, DeenlyInteractionType.OPENED.trainingReward())
        assertEquals(0.15f, DeenlyInteractionType.REVEALED.trainingReward())
        assertEquals(0f, DeenlyInteractionType.DISMISSED.trainingReward())
        assertTrue(DeenlyInteractionType.entries.all { it.trainingReward() in 0f..1f })
    }

    private fun candidate(
        id: String,
        actionId: String,
        signals: DeenlyRankingSignals = DeenlyRankingSignals(),
        minutesSinceLastShown: Int? = null,
        isAvailableOffline: Boolean = true,
    ) = DeenlyNudgeCandidate(
        nudge = DeenlyNudge(
            id = id,
            action = DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
            label = id,
            actionId = actionId,
        ),
        signals = signals,
        minutesSinceLastShown = minutesSinceLastShown,
        isAvailableOffline = isAvailableOffline,
    )
}
