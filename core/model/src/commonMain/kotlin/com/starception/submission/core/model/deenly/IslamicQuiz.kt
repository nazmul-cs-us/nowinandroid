/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.core.model.deenly

enum class IslamicContentSource {
    QURAN,
    SAHIH_AL_BUKHARI,
    SHAMAYEL_AT_TIRMIDHI,
    GENERAL,
}

enum class IslamicQuizDifficulty {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
}

/** A source-grounded reading card shown when active-recall questions are not the best fit. */
data class IslamicKnowledgeCard(
    val id: String,
    val title: String,
    val body: String,
    val sourceLabel: String,
    val sourceUrl: String,
    val sourceCollection: IslamicContentSource,
    val sourceReference: String = sourceLabel,
    val topic: String = "general",
    /** Canonical Arabic source, carried separately and never generated or rewritten. */
    val arabicText: String? = null,
    val sourceTextSha256: String? = null,
    val arabicTextSha256: String? = null,
)

/** A short, citation-backed question suitable for the Deenly nudge game. */
data class IslamicQuizQuestion(
    val id: String,
    val prompt: String,
    val options: List<String>,
    val correctOption: Int,
    val explanation: String,
    val sourceLabel: String,
    val sourceUrl: String,
    val sourceCollection: IslamicContentSource = IslamicContentSource.GENERAL,
    val sourceReference: String = sourceLabel,
    val difficulty: IslamicQuizDifficulty = IslamicQuizDifficulty.BEGINNER,
    val topic: String = "general",
    /** Exact source text used to construct the prompt. */
    val sourceText: String? = null,
    /** Canonical Arabic ayah when [sourceCollection] is Quran. */
    val sourceArabic: String? = null,
    val sourceTextSha256: String? = null,
    val sourceArabicSha256: String? = null,
)

/**
 * Offline-first starter questions. A remote provider can replace this list while preserving the
 * same model and citations; the app never needs to show an uncited answer.
 */
object IslamicQuizBank {
    val questions: List<IslamicQuizQuestion> = listOf(
        IslamicQuizQuestion(
            id = "pillars-of-islam",
            prompt = "How many pillars are there in Islam?",
            options = listOf("Three", "Five", "Seven", "Ten"),
            correctOption = 1,
            explanation = "The well-known hadith describes Islam as being built on five pillars.",
            sourceLabel = "Sahih al-Bukhari 8",
            sourceUrl = "https://sunnah.com/bukhari:8",
            sourceCollection = IslamicContentSource.SAHIH_AL_BUKHARI,
            sourceReference = "Sahih al-Bukhari 8",
            topic = "Pillars of Islam",
        ),
        IslamicQuizQuestion(
            id = "first-surah",
            prompt = "Which surah opens the Quran?",
            options = listOf("Al-Baqarah", "Al-Ikhlas", "Al-Fatihah", "An-Nas"),
            correctOption = 2,
            explanation = "Al-Fatihah is the opening surah of the Quran.",
            sourceLabel = "Quran 1",
            sourceUrl = "https://quran.com/1",
            sourceCollection = IslamicContentSource.QURAN,
            sourceReference = "Quran 1",
            topic = "Surah names",
        ),
        IslamicQuizQuestion(
            id = "qibla-direction",
            prompt = "What direction do Muslims face during salah?",
            options = listOf("Mount Uhud", "The Kaaba", "The Jordan River", "Mount Sinai"),
            correctOption = 1,
            explanation = "The Kaaba in Makkah is the qibla for Muslims around the world.",
            sourceLabel = "Quran 2:144",
            sourceUrl = "https://quran.com/2/144",
            sourceCollection = IslamicContentSource.QURAN,
            sourceReference = "Quran 2:144",
            topic = "Qibla",
        ),
        IslamicQuizQuestion(
            id = "fasting-month",
            prompt = "In which month is the obligatory fasting of Ramadan observed?",
            options = listOf("Muharram", "Rajab", "Sha'ban", "Ramadan"),
            correctOption = 3,
            explanation = "The Quran identifies Ramadan as the month in which the Quran was revealed.",
            sourceLabel = "Quran 2:185",
            sourceUrl = "https://quran.com/2/185",
            sourceCollection = IslamicContentSource.QURAN,
            sourceReference = "Quran 2:185",
            topic = "Fasting",
        ),
    )
}
