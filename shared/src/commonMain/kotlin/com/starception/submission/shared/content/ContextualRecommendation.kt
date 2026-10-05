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

package com.starception.submission.shared.content

import com.starception.submission.core.model.data.BukhariBooks
import com.starception.submission.core.model.data.ShamayelBooks
import com.starception.submission.core.model.deenly.DeenlyActionIds
import com.starception.submission.feature.quran.QuranData
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * The shared port of Android's ContextualInsightRecommendation — the
 * "AI suggested" home tile. Friday stays dedicated to Al-Kahf; every other
 * day rotates between Quran, Fortress of the Muslim, Sahih al-Bukhari, and
 * Shama'il At-Tirmidhi with time-of-day windows, deterministic for the whole
 * day so the suggestion never moves while it is being read.
 */
data class ContextualRecommendation(
    val title: String,
    val supportingText: String,
    val footerText: String,
    val actionDescription: String,
    val target: Target,
) {
    sealed interface Target {
        data class Surah(val number: Int, val name: String) : Target
        data class FortressDua(val chapterId: Int, val position: Int) : Target
        data class Bukhari(val bookId: Int) : Target
        data class Shamayel(val bookId: Int) : Target
    }
}

/** Stable action identity shared with the Now Nudge ranker and event schema. */
val ContextualRecommendation.deenlyActionId: String
    get() = when (target) {
        is ContextualRecommendation.Target.Surah -> DeenlyActionIds.QURAN_OPEN_SURAH
        is ContextualRecommendation.Target.FortressDua -> DeenlyActionIds.DUA_OPEN_FORTRESS
        is ContextualRecommendation.Target.Bukhari -> DeenlyActionIds.HADITH_PLAY_BUKHARI_BOOK
        is ContextualRecommendation.Target.Shamayel -> DeenlyActionIds.HADITH_PLAY_TIRMIDHI_BOOK
    }

/** Fortress chapters used by the contextual dashboard recommendation. */
val CONTEXTUAL_DUA_CHAPTER_IDS = setOf(
    1, // Waking up
    27, // Morning and evening remembrance
    28, // Before sleeping
    29, // Stirring in the night
    30, // Fear or loneliness before sleep
    34, // Worry and grief
    35, // Anguish
    43, // Difficulty
    44, // After committing a sin
    46, // An unwanted outcome
    82, // Anger
    106, // Pleasing or displeasing news
    123, // Something pleasing happens
    126, // Fright
    129, // Repentance and forgiveness
    130, // Remembrance of Allah
)

private data class RecommendationWindow(
    val name: String,
    val surahCandidates: List<Int>,
    val duaChapterCandidates: List<Int>,
    val bukhariBookCandidates: List<Int>,
    val shamayelBookCandidates: List<Int>,
    val surahTitlePrefix: String,
)

/**
 * Builds the deterministic recommendation for [date] and [hour] of the day.
 * [fortressInvocationsByChapter] carries the already-loaded Fortress duas;
 * missing data gracefully falls back to Quran. [chapterTitles] supplies the
 * Fortress fallback titles.
 */
fun contextualRecommendation(
    date: LocalDate,
    hour: Int,
    fortressInvocationsByChapter: Map<Int, List<FortressInvocation>> = emptyMap(),
    chapterTitles: Map<Int, String> = emptyMap(),
): ContextualRecommendation {
    val dayName = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    val epochDay = date.toEpochDays().toInt()

    if (date.dayOfWeek == DayOfWeek.FRIDAY) {
        val surah = QuranData.surahs.first { it.number == 18 }
        return ContextualRecommendation(
            title = "Make time for ${surah.nameEnglish}",
            supportingText = surah.nameArabic,
            footerText = "Quran · Surah ${surah.number} · Friday",
            actionDescription = "Open ${surah.nameEnglish}",
            target = ContextualRecommendation.Target.Surah(
                number = surah.number,
                name = surah.nameEnglish,
            ),
        )
    }

    val window = recommendationWindow(hour)
    when (floorMod(date.dayOfYear, 3)) {
        0 -> {
            if (floorMod(epochDay, 2) == 1) {
                val candidateIndex = floorMod(epochDay, window.shamayelBookCandidates.size)
                val book = ShamayelBooks.all.firstOrNull { it.id == window.shamayelBookCandidates[candidateIndex] }
                if (book != null) {
                    return ContextualRecommendation(
                        title = "Discover: ${book.nameEnglish}",
                        supportingText = book.nameBengali,
                        footerText = "Shama'il At-Tirmidhi · Book ${book.id} · ${book.hadithCount} hadiths",
                        actionDescription = "Play ${book.nameEnglish} from Shama'il At-Tirmidhi",
                        target = ContextualRecommendation.Target.Shamayel(book.id),
                    )
                }
            }
            val candidateIndex = floorMod(epochDay, window.bukhariBookCandidates.size)
            val book = BukhariBooks.all.firstOrNull { it.id == window.bukhariBookCandidates[candidateIndex] }
            if (book != null) {
                return ContextualRecommendation(
                    title = "Listen: ${book.nameEnglish}",
                    supportingText = book.nameArabic,
                    footerText = "Sahih al-Bukhari · Book ${book.id} · ${book.hadithCount} hadiths",
                    actionDescription = "Play ${book.nameEnglish} from Sahih al-Bukhari",
                    target = ContextualRecommendation.Target.Bukhari(book.id),
                )
            }
        }

        1 -> {
            selectFortressDua(epochDay, window, fortressInvocationsByChapter, chapterTitles)?.let { (invocation, chapterTitle) ->
                return ContextualRecommendation(
                    title = contextualDuaTitle(invocation.chapterId, chapterTitle),
                    supportingText = invocation.previewText(chapterTitle),
                    footerText = "Fortress of the Muslim · Dua ${invocation.position} · ${window.name}",
                    actionDescription = "Open $chapterTitle",
                    target = ContextualRecommendation.Target.FortressDua(
                        chapterId = invocation.chapterId,
                        position = invocation.position,
                    ),
                )
            }
        }
    }

    val candidateIndex = floorMod(epochDay, window.surahCandidates.size)
    val surah = QuranData.surahs.first {
        it.number == window.surahCandidates[candidateIndex]
    }
    return ContextualRecommendation(
        title = "${window.surahTitlePrefix} ${surah.nameEnglish}",
        supportingText = surah.nameArabic,
        footerText = "Quran · Surah ${surah.number} · $dayName ${window.name}",
        actionDescription = "Open ${surah.nameEnglish}",
        target = ContextualRecommendation.Target.Surah(
            number = surah.number,
            name = surah.nameEnglish,
        ),
    )
}

private fun floorMod(value: Int, mod: Int): Int = ((value % mod) + mod) % mod

private fun recommendationWindow(hour: Int): RecommendationWindow = when (hour) {
    in 5..10 -> RecommendationWindow(
        name = "morning",
        surahCandidates = listOf(93, 94, 91),
        duaChapterCandidates = listOf(1, 27, 130),
        bukhariBookCandidates = listOf(2, 3, 80),
        shamayelBookCandidates = listOf(1, 34, 40),
        surahTitlePrefix = "Begin gently with",
    )

    in 11..15 -> RecommendationWindow(
        name = "afternoon",
        surahCandidates = listOf(55, 49, 103),
        duaChapterCandidates = listOf(43, 44, 129, 130),
        bukhariBookCandidates = listOf(8, 9, 78),
        shamayelBookCandidates = listOf(24, 28, 47),
        surahTitlePrefix = "Pause and reflect with",
    )

    in 16..18 -> RecommendationWindow(
        name = "evening",
        surahCandidates = listOf(103, 92, 55),
        duaChapterCandidates = listOf(27, 106, 123, 129),
        bukhariBookCandidates = listOf(66, 80, 81),
        shamayelBookCandidates = listOf(35, 36, 48),
        surahTitlePrefix = "Reset your evening with",
    )

    in 19..22 -> RecommendationWindow(
        name = "night",
        surahCandidates = listOf(67, 32, 112),
        duaChapterCandidates = listOf(28, 29, 30, 34),
        bukhariBookCandidates = listOf(19, 80, 81),
        shamayelBookCandidates = listOf(38, 39, 40),
        surahTitlePrefix = "Close the day with",
    )

    else -> RecommendationWindow(
        name = "quiet hours",
        surahCandidates = listOf(73, 67, 32),
        duaChapterCandidates = listOf(34, 35, 126, 129),
        bukhariBookCandidates = listOf(19, 80, 81),
        shamayelBookCandidates = listOf(39, 45, 56),
        surahTitlePrefix = "Take a quiet moment with",
    )
}

private fun selectFortressDua(
    epochDay: Int,
    window: RecommendationWindow,
    invocationsByChapter: Map<Int, List<FortressInvocation>>,
    chapterTitles: Map<Int, String>,
): Pair<FortressInvocation, String>? {
    val availableChapters = window.duaChapterCandidates.filter {
        !invocationsByChapter[it].isNullOrEmpty()
    }
    if (availableChapters.isEmpty()) return null
    val chapterIndex = floorMod(epochDay, availableChapters.size)
    val chapterId = availableChapters[chapterIndex]
    val invocations = invocationsByChapter.getValue(chapterId)
    val duaIndex = floorMod(epochDay + chapterId, invocations.size)
    val chapterTitle = chapterTitles[chapterId] ?: "Fortress of the Muslim"
    return invocations[duaIndex] to chapterTitle
}

private fun FortressInvocation.previewText(fallback: String): String = sequenceOf(
    translation,
    transliteration,
    arabic,
)
    .map { it.replace(Regex("\\s+"), " ").trim() }
    .firstOrNull { it.isNotBlank() }
    ?: fallback

private fun contextualDuaTitle(chapterId: Int, fallback: String): String = when (chapterId) {
    1 -> "Start the day with this dua"
    27 -> "Morning and evening remembrance"
    28 -> "A dua before sleep"
    29 -> "When waking in the night"
    30 -> "For calm before sleep"
    34 -> "For worry and grief"
    35 -> "For moments of anguish"
    43 -> "When something feels difficult"
    44 -> "Return to Allah after a mistake"
    46 -> "When plans do not work out"
    82 -> "For a moment of anger"
    106 -> "Remember Allah in every outcome"
    123 -> "When something brings joy"
    126 -> "When feeling frightened"
    129 -> "Repentance and forgiveness"
    130 -> "Remembering Allah"
    else -> fallback
}
