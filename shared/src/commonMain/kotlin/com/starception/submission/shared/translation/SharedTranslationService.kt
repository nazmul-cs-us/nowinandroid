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

package com.starception.submission.shared.translation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Hadith translation — the shared counterpart of Android's
 * TranslationService.translateFromEnglish: Reverso → Google provider chain
 * with chunking for long texts and an in-memory cache.
 */
object SharedTranslationService {
    const val PROVIDER_AUTO = "auto"
    const val PROVIDER_GOOGLE = "google"
    const val PROVIDER_REVERSO = "reverso"

    /** Android's hadith-screen translation languages. */
    val languages = listOf(
        "en" to "English",
        "ar" to "العربية",
        "bn" to "বাংলা",
        "zh" to "中文",
        "es" to "Español",
        "fr" to "Français",
        "id" to "Indonesia",
        "ru" to "Русский",
        "sv" to "Svenska",
        "tr" to "Türkçe",
        "ur" to "اردو",
    )

    fun displayName(code: String): String =
        languages.firstOrNull { it.first == code }?.second ?: code

    /** Reverso's supported target set (Android's reversoSupportedLanguages). */
    private val reversoSupported = setOf(
        "ar", "zh", "fr", "de", "he", "it", "ja", "ko", "nl", "pl",
        "pt", "ro", "ru", "es", "tr", "uk",
    )

    private val cache = mutableMapOf<String, String>()

    suspend fun translateFromEnglish(text: String, targetLang: String, provider: String): String {
        if (targetLang == "en" || text.isBlank()) return text
        val cacheKey = "$provider|$targetLang|${text.hashCode()}"
        cache[cacheKey]?.let { return it }
        val translated = when (provider) {
            PROVIDER_GOOGLE -> translateWithGoogle(text, targetLang)
            PROVIDER_REVERSO ->
                if (targetLang in reversoSupported) translateWithReverso(text, targetLang) else null
            else -> {
                // Auto: Reverso first, Google fallback.
                val reverso = if (targetLang in reversoSupported) {
                    translateWithReverso(text, targetLang)
                } else {
                    null
                }
                reverso ?: translateWithGoogle(text, targetLang)
            }
        }
        if (translated != null) {
            cache[cacheKey] = translated
            return translated
        }
        return text
    }

    /** Google's free endpoint; long text splits into 1500-char chunks. */
    private suspend fun translateWithGoogle(text: String, targetLang: String): String? {
        val chunks = text.chunked(1500)
        val translated = chunks.map { chunk -> translateGoogleChunk(chunk, targetLang) }
        return if (translated.all { it != null }) translated.joinToString("") else null
    }

    private suspend fun translateGoogleChunk(chunk: String, targetLang: String): String? {
        val encoded = buildString(chunk.length * 2) {
            for (char in chunk) {
                val unreserved = char in 'A'..'Z' || char in 'a'..'z' ||
                    char in '0'..'9' || char == '-' || char == '_' ||
                    char == '.' || char == '~'
                when {
                    unreserved -> append(char)
                    char == ' ' -> append("%20")
                    else -> {
                        val hex = char.code.toString(16).uppercase()
                        if (hex.length < 2) append('0')
                        append('%')
                        append(hex)
                    }
                }
            }
        }
        val url = "https://translate.googleapis.com/translate_a/single?" +
            "client=gtx&sl=en&tl=$targetLang&dt=t&q=$encoded"
        val response = httpGetText(url) ?: return null
        // Google responds with nested arrays: the first holds sentence
        // pairs whose first element is the translated text.
        return runCatching {
            val root = Json.parseToJsonElement(response).jsonArray
            val sentences = root.firstOrNull()?.jsonArray ?: return@runCatching null
            val builder = StringBuilder()
            for (sentence in sentences) {
                val translated = sentence.jsonArray.firstOrNull()
                    ?.jsonPrimitive?.content ?: continue
                builder.append(translated)
            }
            builder.toString().takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /** Reverso's mobile API, as used by the Android app. */
    private suspend fun translateWithReverso(text: String, targetLang: String): String? {
        val escaped = text
            .replace("\\\\", "\\\\\\\\")
            .replace("\"", "\\\\\"")
            .replace("\n", "\\\\n")
        val body = """{"input":"$escaped","from":"en","to":"$targetLang",""" +
            """"format":"text","options":{"sentenceSplitter":true,""" +
            """"origin":"reversomobile","contextResults":false,"languageDetection":false}}"""
        val response = httpPostJson(
            url = "https://api.reverso.net/translate/v1/translation",
            jsonBody = body,
        ) ?: return null
        return runCatching {
            response.substringAfter("\"translation\":\"").substringBefore("\",")
                .unescapeJson()
        }.getOrNull()
    }

    private fun String.unescapeJson(): String = this
        .replace("\\\\n", "\n")
        .replace("\\\\t", "\t")
        .replace("\\\\\"", "\"")
        .replace("\\\\\\\\", "\\\\")
}
