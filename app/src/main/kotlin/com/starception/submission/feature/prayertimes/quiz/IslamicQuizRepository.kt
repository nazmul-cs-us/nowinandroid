/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.feature.prayertimes.quiz

import android.content.Context
import com.starception.submission.core.model.deenly.IslamicQuizBank
import com.starception.submission.core.model.deenly.IslamicContentSource
import com.starception.submission.core.model.deenly.IslamicQuizDifficulty
import com.starception.submission.core.model.deenly.IslamicQuizQuestion
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Loads optional Quran metadata questions without making the game depend on the network.
 *
 * The offline Arabic bank is the Dorar.net-sourced "IslamicQuizAPI" collection
 * (5,820 questions across tafseer, aqeedah, hadith, fiqh, history and Arabic
 * language, in three difficulty levels) bundled with the APK. It parses once
 * per process and mixes into the same shuffled pool as the curated English
 * bank and the optional online surah questions.
 */
class IslamicQuizRepository(
    private val context: Context? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun loadQuestions(): List<IslamicQuizQuestion> = withContext(Dispatchers.IO) {
        val remoteQuestions = runCatching { loadSurahQuestions() }.getOrDefault(emptyList())
        val arabicBank = loadArabicBankQuestions()
        (remoteQuestions + IslamicQuizBank.questions + arabicBank)
            .distinctBy { it.id }
            .shuffled()
    }

    private fun loadSurahQuestions(): List<IslamicQuizQuestion> {
        val connection = URL(SURAHS_URL).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            connection.requestMethod = "GET"
            if (connection.responseCode !in 200..299) return emptyList()

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val surahs = json.decodeFromString<SurahResponse>(response).data
            surahs.shuffled().mapNotNull { answer ->
                val distractors = surahs
                    .filter { it.number != answer.number && it.numberOfAyahs != answer.numberOfAyahs }
                    .shuffled()
                    .take(3)
                if (distractors.size < 3) return@mapNotNull null

                val options = (distractors + answer).shuffled()
                IslamicQuizQuestion(
                    id = "online-surah-${answer.number}",
                    prompt = "Which surah has ${answer.numberOfAyahs} ayahs?",
                    options = options.map { it.englishName },
                    correctOption = options.indexOfFirst { it.number == answer.number },
                    explanation = "${answer.englishName} has ${answer.numberOfAyahs} ayahs.",
                    sourceLabel = "Al Quran Cloud surah metadata",
                    sourceUrl = "https://quran.com/${answer.number}",
                )
            }.take(6)
        } finally {
            connection.disconnect()
        }
    }

    private fun loadArabicBankQuestions(): List<IslamicQuizQuestion> {
        val appContext = context ?: return emptyList()
        cachedArabicBank?.let { return it }
        val parsed = runCatching {
            appContext.assets.open(ARABIC_BANK_ASSET).bufferedReader().use { reader ->
                json.decodeFromString<List<DorarBankQuestion>>(reader.readText())
            }
        }.getOrDefault(emptyList())
        val mapped = parsed.mapNotNull { record ->
            if (record.options.size != 3) return@mapNotNull null
            if (record.correctOption !in 0..2) return@mapNotNull null
            IslamicQuizQuestion(
                id = record.id,
                prompt = record.prompt,
                options = record.options,
                correctOption = record.correctOption,
                explanation = "المصدر: الدرر السنية · ${record.categoryArabic} · ${record.topic}",
                sourceLabel = "الدرر السنية · ${record.categoryArabic}",
                sourceUrl = record.sourceUrl,
                sourceCollection = IslamicContentSource.GENERAL,
                sourceReference = "الدرر السنية · ${record.categoryArabic} · ${record.topic}",
                difficulty = when (record.level) {
                    1 -> IslamicQuizDifficulty.BEGINNER
                    2 -> IslamicQuizDifficulty.INTERMEDIATE
                    else -> IslamicQuizDifficulty.ADVANCED
                },
                topic = record.topic,
            )
        }
        if (mapped.isNotEmpty()) cachedArabicBank = mapped
        return mapped
    }

    @Serializable
    private data class SurahResponse(
        val data: List<SurahRecord> = emptyList(),
    )

    @Serializable
    private data class SurahRecord(
        val number: Int,
        val englishName: String,
        @SerialName("numberOfAyahs") val numberOfAyahs: Int,
    )

    @Serializable
    private data class DorarBankQuestion(
        val id: String,
        val prompt: String,
        val options: List<String>,
        val correctOption: Int,
        val sourceUrl: String = "",
        val category: String = "",
        val categoryArabic: String = "",
        val topic: String = "",
        val level: Int = 1,
    )

    private companion object {
        const val SURAHS_URL = "https://api.alquran.cloud/v1/surah"
        const val ARABIC_BANK_ASSET = "json/islamic_quiz_bank.json"

        /** Parsing 5,820 records takes a moment; do it once per process. */
        @Volatile
        var cachedArabicBank: List<IslamicQuizQuestion>? = null
    }
}
