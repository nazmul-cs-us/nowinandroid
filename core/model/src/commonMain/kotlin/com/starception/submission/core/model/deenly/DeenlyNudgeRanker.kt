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

/** Inputs produced by feature-owned candidate providers. All values are normalized to [0, 1]. */
data class DeenlyRankingSignals(
    val basePriority: Float = 0.5f,
    val contextMatch: Float = 0f,
    val unfinishedProgress: Float = 0f,
    val userAffinity: Float = 0f,
    val freshness: Float = 1f,
    val expectedCompletion: Float = 0f,
)

data class DeenlyNudgeCandidate(
    val nudge: DeenlyNudge,
    val signals: DeenlyRankingSignals = DeenlyRankingSignals(),
    val minutesSinceLastShown: Int? = null,
    val impressionsToday: Int = 0,
    val isAvailableOffline: Boolean = true,
)

data class DeenlyRankingContext(
    val isMediaPlaying: Boolean = false,
    val isOnline: Boolean = true,
    val dismissedNudgeIds: Set<String> = emptySet(),
    val recentActionIds: List<String> = emptyList(),
)

/** Export target for the offline trainer. A trained model only needs to replace these weights. */
data class DeenlyRankingWeights(
    val intercept: Float = 0f,
    val basePriority: Float = 1f,
    val contextMatch: Float = 1.8f,
    val unfinishedProgress: Float = 1.4f,
    val userAffinity: Float = 1f,
    val freshness: Float = 0.6f,
    val expectedCompletion: Float = 1.2f,
    val repeatImpressionPenalty: Float = 0.35f,
    val recentActionPenalty: Float = 0.8f,
)

data class DeenlyRankingModel(
    val version: String,
    val schemaVersion: Int = DEENLY_RANKING_SCHEMA_VERSION,
    val weights: DeenlyRankingWeights,
)

/** Safe cold-start model used until a validated trained model is bundled with the app. */
val DEENLY_STARTER_RANKING_MODEL = DeenlyRankingModel(
    version = "nudge-ltr-starter-v1",
    weights = DeenlyRankingWeights(),
)

data class RankedDeenlyNudge(
    val candidate: DeenlyNudgeCandidate,
    val score: Float,
    val modelVersion: String,
)

/**
 * Tiny cross-platform linear learning-to-rank model.
 *
 * It performs only a few multiplies per candidate, needs no ML runtime, and uses the same code on
 * Android and iOS. Eligibility is deliberately applied before scoring so a trained weight file can
 * never override media, offline, dismissal, or cooldown safeguards.
 */
fun rankDeenlyNudges(
    candidates: List<DeenlyNudgeCandidate>,
    context: DeenlyRankingContext,
    model: DeenlyRankingModel = DEENLY_STARTER_RANKING_MODEL,
): List<RankedDeenlyNudge> {
    require(model.schemaVersion == DEENLY_RANKING_SCHEMA_VERSION) {
        "Unsupported Now Nudge ranking schema: ${model.schemaVersion}"
    }
    val weights = model.weights
    return candidates
        .asSequence()
        .filterNot { it.nudge.id in context.dismissedNudgeIds }
        .filter { candidate ->
            val definition = DeenlyActionCatalog.definition(candidate.nudge.actionId)
            definition?.startsAudio != true || !context.isMediaPlaying
        }
        .filter { candidate ->
            val definition = DeenlyActionCatalog.definition(candidate.nudge.actionId)
            definition?.requiresNetwork != true || context.isOnline || candidate.isAvailableOffline
        }
        .filter { candidate ->
            val cooldown = DeenlyActionCatalog.definition(candidate.nudge.actionId)
                ?.defaultCooldownMinutes ?: 0
            candidate.minutesSinceLastShown == null || candidate.minutesSinceLastShown >= cooldown
        }
        .map { candidate ->
            val signals = candidate.signals
            val recentPenalty = if (candidate.nudge.actionId in context.recentActionIds) {
                weights.recentActionPenalty
            } else {
                0f
            }
            RankedDeenlyNudge(
                candidate = candidate,
                modelVersion = model.version,
                score = weights.intercept +
                    (weights.basePriority * signals.basePriority.unitValue()) +
                    (weights.contextMatch * signals.contextMatch.unitValue()) +
                    (weights.unfinishedProgress * signals.unfinishedProgress.unitValue()) +
                    (weights.userAffinity * signals.userAffinity.unitValue()) +
                    (weights.freshness * signals.freshness.unitValue()) +
                    (weights.expectedCompletion * signals.expectedCompletion.unitValue()) -
                    (weights.repeatImpressionPenalty * candidate.impressionsToday.coerceAtLeast(0)) -
                    recentPenalty,
            )
        }
        .sortedWith(
            compareByDescending<RankedDeenlyNudge>(RankedDeenlyNudge::score)
                .thenBy { it.candidate.nudge.actionId }
                .thenBy { it.candidate.nudge.id },
        )
        .toList()
}

private fun Float.unitValue(): Float = coerceIn(0f, 1f)

const val DEENLY_RANKING_SCHEMA_VERSION = 1
