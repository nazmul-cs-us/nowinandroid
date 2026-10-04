/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.feature.prayertimes.quiz

import com.starception.submission.core.model.deenly.IslamicQuizBank
import com.starception.submission.core.model.deenly.IslamicQuizQuestion
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Loads optional Quran metadata questions without making the game depend on the network. */
class IslamicQuizRepository(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun loadQuestions(): List<IslamicQuizQuestion> = withContext(Dispatchers.IO) {
        val remoteQuestions = runCatching { loadSurahQuestions() }.getOrDefault(emptyList())
        (remoteQuestions + IslamicQuizBank.questions)
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

    private companion object {
        const val SURAHS_URL = "https://api.alquran.cloud/v1/surah"
    }
}
