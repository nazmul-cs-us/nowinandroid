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

package com.starception.submission.shared.hadith

/** One English hadith passage that matched the typed question's tokens. */
data class HadithSearchHit(
    val databaseFile: String,
    val hadithId: Int,
    val collectionName: String,
    val text: String,
)

/**
 * Multi-token AND search over the app's English hadith collections (Bukhari,
 * Muslim, Muwatta Malik, Abu Dawud, an-Nasa'i, Ibn Majah, at-Tirmidhi). Every
 * token must appear in the same passage; shorter passages rank first so the
 * answer card quotes cleanly. Collections that are not downloaded resolve to
 * no hits instead of failing the turn.
 */
expect suspend fun searchEnglishHadithCollections(tokens: List<String>, limit: Int): List<HadithSearchHit>

/**
 * Android's CORPUS_SEARCH_SYNONYMS, shared so both platforms expand a token
 * into the same corpus phrasings. A typed "favourite" must also match the
 * "loved"/"liked" narrations, a typed "prophet" must match "Messenger",
 * "Apostle" and "Rasulullah", and food questions must reach the "used to
 * eat/drink" passages that never say "food".
 */
internal val HADITH_CORPUS_SEARCH_SYNONYMS = mapOf(
    "favorite" to listOf("loved", "liked"),
    "favourite" to listOf("loved", "liked"),
    "love" to listOf("liked", "beloved"),
    "hate" to listOf("disliked", "detested"),
    "prophet" to listOf("messenger", "apostle", "rasulullah"),
    "messenger" to listOf("prophet", "apostle", "rasulullah"),
    "apostle" to listOf("prophet", "messenger", "rasulullah"),
    "muhammad" to listOf("prophet", "messenger", "rasulullah"),
    "food" to listOf("eat", "drink", "meal"),
    "drink" to listOf("food", "eat"),
)

/** One token plus its corpus-synonym alternatives, Android's SearchSlot. */
internal fun hadithSearchSlotVariants(token: String): List<String> =
    listOf(token) + HADITH_CORPUS_SEARCH_SYNONYMS[token].orEmpty()

/**
 * Android's search plan order, strictest first: the full AND, then the
 * token pairs, then the single most important token. Each step only runs
 * when the previous found nothing.
 */
internal fun buildHadithSearchPlans(slots: List<List<String>>): List<List<List<String>>> = buildList {
    add(slots)
    when (slots.size) {
        3 -> {
            add(listOf(slots[0], slots[1]))
            add(listOf(slots[0], slots[2]))
            add(listOf(slots[1], slots[2]))
            add(listOf(slots[0]))
        }
        2 -> {
            add(listOf(slots[0]))
            add(listOf(slots[1]))
        }
    }
}.distinct()
