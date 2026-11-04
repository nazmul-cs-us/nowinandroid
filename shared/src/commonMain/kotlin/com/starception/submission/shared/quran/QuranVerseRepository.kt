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

data class QuranSurahMetadata(
    val nameTranslation: String,
    val totalVerses: Int,
)

interface QuranVerseRepository {
    suspend fun getVersesBySurah(surahNumber: Int): List<QuranVerse> =
        getVersesBySurah(surahNumber, QuranTranslationLanguage.English)

    suspend fun getVersesBySurah(
        surahNumber: Int,
        language: QuranTranslationLanguage,
    ): List<QuranVerse>

    suspend fun getSurahMetadata(surahNumber: Int): QuranSurahMetadata? = null

    /** Full-text ayah search (Arabic), for the app-level search suggestions. */
    suspend fun searchAyahs(query: String, limit: Int = 12): List<QuranVerse> = emptyList()
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
    Hindi("hi", "हिन्दी"),
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

internal const val QURAN_BISMILLAH = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"

/** Compare opening letters only, preserving the remaining ayah's vocalization. */
private fun leadingBismillahEnd(text: String): Int? {
    val opening = text.trimStart()
    val leadingSpace = text.length - opening.length
    val letters = "بسماللهالرحمنالرحيم"
    var matched = 0
    fun mark(char: Char): Boolean = char in '\u064B'..'\u065F' || char == '\u0670' ||
        char in '\u06D6'..'\u06DC' || char in '\u06DF'..'\u06E4' || char in '\u06E7'..'\u06E8' || char in '\u06EA'..'\u06ED'
    for (index in opening.indices) {
        val char = opening[index]
        if (char.isWhitespace() || char == 'ـ' || mark(char)) continue
        val letter = if (char in "ٱأإآ") 'ا' else char
        if (letter != letters[matched]) return null
        matched++
        if (matched == letters.length) {
            var end = index + 1
            while (end < opening.length && (opening[end].isWhitespace() || opening[end] == 'ـ' || mark(opening[end]))) end++
            return leadingSpace + end
        }
    }
    return null
}

internal fun hasLeadingBismillah(text: String): Boolean = leadingBismillahEnd(cleanQuranText(text)) != null

internal fun removeLeadingBismillah(text: String): String = cleanQuranText(text).let { clean ->
    leadingBismillahEnd(clean)?.let { clean.substring(it) } ?: clean
}

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
