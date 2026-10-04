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
import kotlin.test.assertNull

class DeenlyNudgeTest {
    @Test
    fun drivingOffersTravelDuaBeforeOtherActions() {
        val result = selectDeenlyNudge(
            baseContext().copy(
                activity = DeenlyActivity.DRIVING,
                currentPrayer = "Dhuhr",
                currentPrayerMinute = 12 * 60,
                nowMinute = 12 * 60 + 20,
            ),
        )

        assertEquals(DeenlyNudgeAction.PLAY_TRAVEL_DUA, result?.action)
    }

    @Test
    fun activeMediaSuppressesDrivingSuggestion() {
        val result = selectDeenlyNudge(
            baseContext().copy(
                activity = DeenlyActivity.DRIVING,
                isMediaPlaying = true,
            ),
        )

        assertNull(result)
    }

    @Test
    fun launchGreetingPrecedesNormalPrayerSuggestions() {
        val greeting = DeenlyNudge(
            id = "launch-greeting",
            action = DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
            label = "Assalamu alaikum",
        )
        val result = selectDeenlyNudge(
            baseContext().copy(
                launchNudge = greeting,
                currentPrayer = "Dhuhr",
                currentPrayerMinute = 12 * 60,
                nowMinute = 12 * 60 + 20,
            ),
        )

        assertEquals(greeting, result)
    }

    @Test
    fun unmarkedPrayerIsOfferedOnlyAfterTwelveMinutes() {
        assertNull(
            selectDeenlyNudge(
                baseContext().copy(
                    currentPrayer = "Asr",
                    currentPrayerMinute = 15 * 60,
                    nowMinute = 15 * 60 + 11,
                ),
            ),
        )
        assertEquals(
            DeenlyNudgeAction.MARK_PRAYED,
            selectDeenlyNudge(
                baseContext().copy(
                    currentPrayer = "Asr",
                    currentPrayerMinute = 15 * 60,
                    nowMinute = 15 * 60 + 12,
                ),
            )?.action,
        )
    }

    @Test
    fun completedOrCurrentlyPrayingSuppressesMarkSuggestion() {
        val completed = baseContext().copy(
            currentPrayer = "Maghrib",
            currentPrayerMinute = 18 * 60,
            nowMinute = 18 * 60 + 20,
            completedPrayers = setOf("Maghrib"),
        )
        val praying = completed.copy(
            completedPrayers = emptySet(),
            activity = DeenlyActivity.PRAYING,
        )

        assertNull(selectDeenlyNudge(completed))
        assertNull(selectDeenlyNudge(praying))
    }

    @Test
    fun qiblaRequiresVerifiedLocationAndTightTimeWindow() {
        val eligible = baseContext().copy(
            nowMinute = 17 * 60 + 50,
            nextPrayer = "Maghrib",
            nextPrayerMinute = 18 * 60,
            hasVerifiedLocation = true,
        )

        assertEquals(DeenlyNudgeAction.OPEN_QIBLA, selectDeenlyNudge(eligible)?.action)
        assertNull(selectDeenlyNudge(eligible.copy(hasVerifiedLocation = false)))
        assertNull(selectDeenlyNudge(eligible.copy(nowMinute = 17 * 60 + 47)))
    }

    @Test
    fun dismissedSuggestionDoesNotFallThroughToDuplicateLowerPriorityAction() {
        val result = selectDeenlyNudge(
            baseContext().copy(
                currentPrayer = "Isha",
                currentPrayerMinute = 20 * 60,
                nowMinute = 20 * 60 + 20,
                dismissedIds = setOf("mark-isha"),
            ),
        )

        assertNull(result)
    }

    @Test
    fun quizIsTheLowPriorityFallbackWhenAvailable() {
        val result = selectDeenlyNudge(
            baseContext().copy(hasQuizAvailable = true),
        )

        assertEquals(DeenlyNudgeAction.PLAY_QUIZ, result?.action)
    }

    @Test
    fun fallbackSuggestionsRotateEveryFourHours() {
        val contextual = DeenlyNudge(
            id = "contextual-reading",
            action = DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
            label = "Read a contextual recommendation",
        )
        val context = baseContext().copy(
            nowMinute = 0,
            hasQuizAvailable = true,
            fallbackNudges = listOf(contextual),
        )

        assertEquals(DeenlyNudgeAction.PLAY_QUIZ, selectDeenlyNudge(context)?.action)
        assertEquals(
            DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
            selectDeenlyNudge(context.copy(nowMinute = 4 * 60))?.action,
        )
    }

    @Test
    fun dismissedFallbackRotatesToRemainingSuggestion() {
        val contextual = DeenlyNudge(
            id = "contextual-reading",
            action = DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
            label = "Read a contextual recommendation",
        )

        val result = selectDeenlyNudge(
            baseContext().copy(
                hasQuizAvailable = true,
                fallbackNudges = listOf(contextual),
                dismissedIds = setOf("islamic-quiz-hint"),
            ),
        )

        assertEquals(DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION, result?.action)
    }

    private fun baseContext() = DeenlyNudgeContext(nowMinute = 12 * 60)
}
