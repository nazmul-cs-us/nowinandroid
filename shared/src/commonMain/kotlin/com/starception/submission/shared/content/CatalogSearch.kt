/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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
    val normalized = term.lowercase()
    val number = term.toIntOrNull()
    return buildList {
        QuranData.surahs.filter { surah ->
            surah.number == number ||
                normalized in surah.nameEnglish.lowercase() ||
                term in surah.nameArabic
        }.forEach { add(CatalogResult.Quran(it)) }
        BukhariBooks.all.filter { book ->
            book.id == number ||
                normalized in book.nameEnglish.lowercase() ||
                term in book.nameArabic
        }.forEach { add(CatalogResult.Bukhari(it)) }
    }
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
