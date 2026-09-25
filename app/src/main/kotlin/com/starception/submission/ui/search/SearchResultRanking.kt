/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.ui.search

/**
 * An unfinished "Allah" prefix is also a strong prefix for several Surah names.
 *
 * For example, `ala` matches both "Allah Protects Believers" and "Al-Alaa". The
 * former is only a generic word match while the latter is a direct title match,
 * so showing five curated verses above the Surah section makes the search feel
 * incorrect. Keep those verses for a completed `allah` query, or when there is
 * no Surah match to promote.
 */
internal fun shouldSuppressAmbiguousAllahPrefixVerses(
    query: String,
    hasSurahMatches: Boolean,
): Boolean {
    if (!hasSurahMatches) return false

    val token = SearchTokenizer.parse(query).tokens.singleOrNull() ?: return false
    val searchKey = SearchTokenizer.transliterationKey(token)
    val allahKey = SearchTokenizer.transliterationKey("allah")
    return token.length in 2..4 && allahKey.startsWith(searchKey)
}
