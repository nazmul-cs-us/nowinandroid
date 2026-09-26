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

package com.starception.submission.shared.quran

data class QuranVerse(
    val id: Int,
    val surahNumber: Int,
    val numberInSurah: Int,
    val arabicText: String,
    val page: Int,
    val juz: Int,
    val translation: String = "",
)

interface QuranVerseRepository {
    suspend fun getVersesBySurah(surahNumber: Int): List<QuranVerse> =
        getVersesBySurah(surahNumber, QuranTranslationLanguage.English)

    suspend fun getVersesBySurah(
        surahNumber: Int,
        language: QuranTranslationLanguage,
    ): List<QuranVerse>
}

expect fun createQuranVerseRepository(): QuranVerseRepository

/**
 * Translation languages shipped on the CDN (databases/quran/quran_XX.db).
 * Arabic-only reading selects [None]; the English DB is bundled with the app
 * so it also works offline on first run.
 */
enum class QuranTranslationLanguage(
    val code: String,
    val displayName: String,
) {
    English("en", "English"),
    Bengali("bn", "বাংলা"),
    Spanish("es", "Español"),
    French("fr", "Français"),
    Indonesian("id", "Indonesia"),
    Russian("ru", "Русский"),
    Swedish("sv", "Svenska"),
    Turkish("tr", "Türkçe"),
    Urdu("ur", "اردو"),
    Chinese("zh", "中文"),
    Transliteration("transliteration", "Roman transliteration"),
    None("", "Arabic only"),
    ;

    companion object {
        fun fromCode(code: String): QuranTranslationLanguage =
            entries.firstOrNull { it.code == code } ?: English
    }
}

private val ArabicMarks = Regex("[\\u0640\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")

internal fun cleanQuranText(text: String): String = text.trim().removePrefix("\uFEFF")

internal fun filterQuranVerses(
    verses: List<QuranVerse>,
    query: String,
): List<QuranVerse> {
    val term = normalizeArabicSearch(query)
    if (term.isBlank()) return verses
    return verses.filter { verse ->
        verse.numberInSurah.toString() == term ||
            normalizeArabicSearch(verse.arabicText).contains(term) ||
            verse.translation.lowercase().contains(query.trim().lowercase())
    }
}

internal fun QuranVerse.metadataLabel(): String =
    "Ayah $numberInSurah · Page $page · Juz $juz"

private fun normalizeArabicSearch(value: String): String = value
    .trim()
    .lowercase()
    .replace(ArabicMarks, "")
    .replace('ٱ', 'ا')
    .replace('أ', 'ا')
    .replace('إ', 'ا')
    .replace('آ', 'ا')
