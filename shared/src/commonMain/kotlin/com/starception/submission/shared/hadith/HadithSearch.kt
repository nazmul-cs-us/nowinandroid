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
