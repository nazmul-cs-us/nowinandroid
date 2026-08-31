/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.starception.submission.shared.quran

data class QuranVerse(
    val id: Int,
    val surahNumber: Int,
    val numberInSurah: Int,
    val arabicText: String,
    val page: Int,
    val juz: Int,
)

interface QuranVerseRepository {
    suspend fun getVersesBySurah(surahNumber: Int): List<QuranVerse>
}

expect fun createQuranVerseRepository(): QuranVerseRepository

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
            normalizeArabicSearch(verse.arabicText).contains(term)
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
