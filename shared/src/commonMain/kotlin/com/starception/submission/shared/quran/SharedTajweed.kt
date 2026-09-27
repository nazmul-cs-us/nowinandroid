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

package com.starception.submission.shared.quran

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One tajweed annotation: a rule key and a character range, authored against
 * the Uthmani text — the shared counterpart of Android's TajweedAnnotation.
 */
data class SharedTajweedAnnotation(
    val ruleKey: String,
    val startIndex: Int,
    val endIndex: Int,
)

/**
 * The tajweed rules and their colors, mirroring Android's TajweedRule enum
 * (keyed by the json keys tajweed.json uses). Colors are ARGB longs.
 */
object SharedTajweedRules {
    val colors: Map<String, Long> = mapOf(
        "ghunnah" to 0xFFFF7E1E,
        "hamzat_wasl" to 0xFF808080,
        "idghaam_ghunnah" to 0xFF9400D3,
        "idghaam_no_ghunnah" to 0xFF00008B,
        "idghaam_mutajanisayn" to 0xFF8B4513,
        "idghaam_mutaqaribayn" to 0xFF006400,
        "idghaam_shafawi" to 0xFFFF1493,
        "ikhfa" to 0xFF00CED1,
        "ikhfa_shafawi" to 0xFF32CD32,
        "iqlab" to 0xFF4169E1,
        "lam_shamsiyyah" to 0xFFDAA520,
        "madd_2" to 0xFFE57373,
        "madd_6" to 0xFFFF0000,
        "madd_246" to 0xFFDC143C,
        "madd_muttasil" to 0xFFFF4500,
        "madd_munfasil" to 0xFFFF6347,
        "qalqalah" to 0xFF228B22,
        "silent" to 0xFF696969,
    )
}

/**
 * Loads tajweed.json (the 5.5 MB CDN asset) and indexes its annotations
 * per surah, like Android's TajweedRepository — parsed once, cached.
 */
interface SharedTajweedRepository {
    /** Annotations for every ayah of a surah, or null when the asset is unavailable. */
    suspend fun annotationsForSurah(surahNumber: Int): Map<Int, List<SharedTajweedAnnotation>>?
}

expect fun createSharedTajweedRepository(): SharedTajweedRepository

/**
 * Parses the tajweed.json payload (the CDN/Android format: an array of
 * {surah, ayah, annotations:[{rule,start,end}]}) into a per-surah index.
 */
internal fun parseTajweedJson(jsonText: String): Map<Int, Map<Int, List<SharedTajweedAnnotation>>> {
    val array = Json.parseToJsonElement(jsonText).jsonArray
    val perSurah = mutableMapOf<Int, MutableMap<Int, List<SharedTajweedAnnotation>>>()
    for (element in array) {
        val ayahObject = element.jsonObject
        val surahNumber = ayahObject["surah"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
        val ayahNumber = ayahObject["ayah"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
        val annotationsArray = ayahObject["annotations"]?.jsonArray ?: continue
        val annotations = buildList {
            for (annotationElement in annotationsArray) {
                val annotation = annotationElement.jsonObject
                val rule = annotation["rule"]?.jsonPrimitive?.content ?: continue
                val start = annotation["start"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
                val end = annotation["end"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
                if (rule in SharedTajweedRules.colors) {
                    add(SharedTajweedAnnotation(rule, start, end))
                }
            }
        }
        perSurah.getOrPut(surahNumber) { mutableMapOf() }[ayahNumber] = annotations
    }
    return perSurah
}
