/*
 * Copyright 2021 The Android Open Source Project
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

package com.starception.submission.shared.content

import com.starception.submission.core.model.data.BukhariBook
import com.starception.submission.core.model.data.BukhariBooks
import com.starception.submission.feature.quran.QuranData
import com.starception.submission.feature.quran.Surah
import kotlinx.datetime.LocalDate

sealed interface CatalogResult {
    data class Quran(val surah: Surah) : CatalogResult
    data class Bukhari(val book: BukhariBook) : CatalogResult
}

fun searchCatalog(query: String): List<CatalogResult> {
    val term = query.trim()
    if (term.isEmpty()) return emptyList()
    val searchTerms = expandedSearchQueries(term)
    val number = term.toIntOrNull()
    return buildList {
        QuranData.surahs.filter { surah ->
            surah.number == number ||
                searchTerms.any { matchesCatalogName(surah.nameEnglish, it) } ||
                term in surah.nameArabic
        }.forEach { add(CatalogResult.Quran(it)) }
        BukhariBooks.all.filter { book ->
            book.id == number ||
                searchTerms.any { matchesCatalogName(book.nameEnglish, it) } ||
                term in book.nameArabic
        }.forEach { add(CatalogResult.Bukhari(it)) }
    }
}

private val SearchSynonymGroups = listOf(
    listOf("prayer", "salah", "salat", "namaz"),
    listOf("adhan", "azan", "athan", "call to prayer"),
    listOf("dua", "duaa", "supplication", "invocation"),
    listOf("dhikr", "zikr", "remembrance"),
    listOf("wudu", "wudhu", "ablution"),
    listOf("qibla", "kibla", "prayer direction"),
    listOf("fasting", "sawm", "roza"),
    listOf("charity", "zakat", "almsgiving"),
)

internal fun expandedSearchQueries(query: String): List<String> {
    val normalized = normalizeSearchText(query)
    if (normalized.isEmpty()) return emptyList()
    val exactGroup = SearchSynonymGroups.firstOrNull { normalized in it }
    if (exactGroup != null) {
        return (listOf(normalized) + exactGroup).distinct()
    }
    val canonicalPhrase = normalized.split(' ').joinToString(" ") { token ->
        SearchSynonymGroups.firstOrNull { token in it }?.first() ?: token
    }
    return listOf(normalized, canonicalPhrase).distinct()
}

internal fun canonicalSearchQuery(query: String): String {
    val normalized = normalizeSearchText(query)
    val exactGroup = SearchSynonymGroups.firstOrNull { normalized in it }
    if (exactGroup != null) return exactGroup.first()
    return normalized.split(' ').joinToString(" ") { token ->
        SearchSynonymGroups.firstOrNull { token in it }?.first() ?: token
    }
}

private fun normalizeSearchText(value: String): String = buildString(value.length) {
    value.lowercase().forEach { character ->
        when {
            character.isLetterOrDigit() -> append(character)
            character == '\'' || character == '’' || character == 'ʼ' -> Unit
            isNotEmpty() && last() != ' ' -> append(' ')
        }
    }
}.trim()

private fun matchesCatalogName(name: String, query: String): Boolean {
    val normalizedName = normalizeSearchText(name)
    if (query in normalizedName) return true
    if (' ' in query || query.length < 3) return false
    val tolerance = if (query.length < 5) 1 else 2
    return normalizedName.split(' ').any { word ->
        word.length >= 3 && editDistanceAtMost(word, query, tolerance)
    }
}

private fun editDistanceAtMost(left: String, right: String, limit: Int): Boolean {
    if (kotlin.math.abs(left.length - right.length) > limit) return false
    var previous = IntArray(right.length + 1) { it }
    left.forEachIndexed { leftIndex, leftChar ->
        val current = IntArray(right.length + 1)
        current[0] = leftIndex + 1
        right.forEachIndexed { rightIndex, rightChar ->
            current[rightIndex + 1] = minOf(
                current[rightIndex] + 1,
                previous[rightIndex + 1] + 1,
                previous[rightIndex] + if (leftChar == rightChar) 0 else 1,
            )
        }
        if (current.minOrNull()!! > limit) return false
        previous = current
    }
    return previous[right.length] <= limit
}

data class DailyRecommendation(
    val title: String,
    val category: String,
    val summary: String,
    val reason: String,
    val surahNumber: Int? = null,
    val bukhariBookId: Int? = null,
)

/** A deterministic on-device rotation. It does not call or impersonate a remote AI service. */
fun dailyRecommendation(date: LocalDate): DailyRecommendation = when (date.day % 3) {
    0 -> DailyRecommendation(
        title = "Read Surah Al-Kahf",
        category = "Quran",
        summary = "A chapter centered on faith, patience, knowledge, and responsible power.",
        reason = "Selected from the shared Quran catalog by today's date.",
        surahNumber = 18,
    )
    1 -> DailyRecommendation(
        title = "Make time for remembrance",
        category = "Hadith collection",
        summary = "Browse Sahih al-Bukhari's Invocations collection for a focused study session.",
        reason = "Selected from the shared Bukhari catalog by today's date.",
        bukhariBookId = 80,
    )
    else -> DailyRecommendation(
        title = "Begin with intention",
        category = "Hadith collection",
        summary = "Explore the Book of Revelation, which opens with the narration on intentions.",
        reason = "Selected from the shared Bukhari catalog by today's date.",
        bukhariBookId = 1,
    )
}
