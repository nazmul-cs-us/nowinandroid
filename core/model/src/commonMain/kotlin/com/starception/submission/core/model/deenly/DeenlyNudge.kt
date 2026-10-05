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

package com.starception.submission.core.model.deenly

enum class DeenlyNudgeAction {
    MARK_PRAYED,
    OPEN_QIBLA,
    PLAY_TRAVEL_DUA,
    PLAY_QUIZ,
    OPEN_CONTEXTUAL_RECOMMENDATION,
}

enum class DeenlyActivity {
    DRIVING,
    PRAYING,
    OTHER,
    UNKNOWN,
}

data class DeenlyNudge(
    val id: String,
    val action: DeenlyNudgeAction,
    val label: String,
    val prayerName: String? = null,
    /** Stable catalog ID used by the cross-platform ranker and interaction telemetry. */
    val actionId: String = action.defaultActionId(),
    /** Optional immutable source text shown below a model-created knowledge title. */
    val supportingText: String? = null,
    /** Human-readable citation for [supportingText]. */
    val sourceLabel: String? = null,
)

data class DeenlyNudgeContext(
    val nowMinute: Int,
    val currentPrayer: String? = null,
    val currentPrayerMinute: Int? = null,
    val nextPrayer: String? = null,
    val nextPrayerMinute: Int? = null,
    val completedPrayers: Set<String> = emptySet(),
    val activity: DeenlyActivity = DeenlyActivity.UNKNOWN,
    val isMediaPlaying: Boolean = false,
    val hasVerifiedLocation: Boolean = false,
    val hasQuizAvailable: Boolean = false,
    val launchNudge: DeenlyNudge? = null,
    /** A grounded learning item selected outside the cross-platform context model. */
    val knowledgeNudge: DeenlyNudge? = null,
    val fallbackNudges: List<DeenlyNudge> = emptyList(),
    val dismissedIds: Set<String> = emptySet(),
)

/**
 * Selects at most one high-confidence action, then rotates through explicitly supplied fallback
 * content instead of repeating generic dashboard information.
 */
fun selectDeenlyNudge(context: DeenlyNudgeContext): DeenlyNudge? {
    if (context.activity == DeenlyActivity.DRIVING) {
        if (context.isMediaPlaying) return null
        val candidate = DeenlyNudge(
            id = "travel-dua-driving",
            action = DeenlyNudgeAction.PLAY_TRAVEL_DUA,
            label = "Play travel dua",
        )
        return candidate.takeIf { it.id !in context.dismissedIds }
    }
    if (context.activity == DeenlyActivity.PRAYING) return null

    context.launchNudge
        ?.takeIf { it.id !in context.dismissedIds }
        ?.let { return it }

    val currentPrayer = context.currentPrayer
    val currentPrayerMinute = context.currentPrayerMinute
    if (
        currentPrayer != null &&
        currentPrayerMinute != null &&
        currentPrayer !in context.completedPrayers
    ) {
        val minutesSinceStart = forwardMinutes(currentPrayerMinute, context.nowMinute)
        if (minutesSinceStart in 12..75) {
            val candidate = DeenlyNudge(
                id = "mark-${currentPrayer.lowercase()}",
                action = DeenlyNudgeAction.MARK_PRAYED,
                label = "Mark $currentPrayer prayed",
                prayerName = currentPrayer,
            )
            if (candidate.id !in context.dismissedIds) return candidate
        }
    }

    val nextPrayer = context.nextPrayer
    val nextPrayerMinute = context.nextPrayerMinute
    if (context.hasVerifiedLocation && nextPrayer != null && nextPrayerMinute != null) {
        val minutesUntilStart = forwardMinutes(context.nowMinute, nextPrayerMinute)
        if (minutesUntilStart in 5..12) {
            val candidate = DeenlyNudge(
                id = "qibla-${nextPrayer.lowercase()}",
                action = DeenlyNudgeAction.OPEN_QIBLA,
                label = "Qibla for $nextPrayer",
                prayerName = nextPrayer,
            )
            if (candidate.id !in context.dismissedIds) return candidate
        }
    }

    context.knowledgeNudge
        ?.takeIf { it.id !in context.dismissedIds }
        ?.let { return it }

    val fallbackCandidates = buildList {
        if (context.hasQuizAvailable) {
            add(
                DeenlyNudge(
                    id = "islamic-quiz-hint",
                    action = DeenlyNudgeAction.PLAY_QUIZ,
                    label = "Hold voice button for a quiz",
                ),
            )
        }
        addAll(context.fallbackNudges)
    }.filterNot { it.id in context.dismissedIds }
    if (fallbackCandidates.isNotEmpty()) {
        val rotationSlot = context.nowMinute / FALLBACK_ROTATION_MINUTES
        val explorationIndex = rotationSlot % fallbackCandidates.size
        return rankDeenlyNudges(
            candidates = fallbackCandidates.mapIndexed { index, nudge ->
                DeenlyNudgeCandidate(
                    nudge = nudge,
                    signals = DeenlyRankingSignals(
                        // Preserve the existing four-hour exploration rotation while routing the
                        // decision through the model that app-wide providers will also use.
                        contextMatch = if (index == explorationIndex) 1f else 0f,
                    ),
                )
            },
            context = DeenlyRankingContext(
                isMediaPlaying = context.isMediaPlaying,
                dismissedNudgeIds = context.dismissedIds,
            ),
        ).firstOrNull()?.candidate?.nudge
    }

    return null
}

private fun forwardMinutes(fromMinute: Int, toMinute: Int): Int =
    (toMinute - fromMinute + MINUTES_PER_DAY) % MINUTES_PER_DAY

private const val MINUTES_PER_DAY = 24 * 60
private const val FALLBACK_ROTATION_MINUTES = 4 * 60
