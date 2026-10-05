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

/** Events used to evaluate and periodically retrain the Now Nudge ranker. */
enum class DeenlyInteractionType {
    IMPRESSION,
    REVEALED,
    OPENED,
    COMPLETED,
    DISMISSED,
    IGNORED,
}

/**
 * Privacy-minimal training event.
 *
 * The event deliberately excludes Quran/Hadith text, speech, search terms, precise location, and
 * user identifiers. Product analytics can associate events with an existing consented anonymous
 * installation identifier outside this shared model when that is allowed by the privacy policy.
 */
data class DeenlyInteractionEvent(
    val eventId: String,
    val nudgeId: String,
    val actionId: String,
    val interactionType: DeenlyInteractionType,
    val occurredAtEpochMillis: Long,
    val modelVersion: String,
    val score: Float? = null,
    val rank: Int? = null,
)

/** Converts an observed outcome into the bounded target consumed by the offline trainer. */
fun DeenlyInteractionType.trainingReward(): Float = when (this) {
    DeenlyInteractionType.COMPLETED -> 1f
    DeenlyInteractionType.OPENED -> 0.7f
    DeenlyInteractionType.REVEALED -> 0.15f
    DeenlyInteractionType.IMPRESSION -> 0f
    DeenlyInteractionType.IGNORED -> 0f
    DeenlyInteractionType.DISMISSED -> 0f
}
