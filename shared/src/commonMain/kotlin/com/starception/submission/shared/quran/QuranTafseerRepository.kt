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

/**
 * Tafseer + word meanings for one ayah, read from the enhanced Quran database
 * (databases/quran/quran_enhanced.db on the CDN). Field layout matches the
 * Android app's QuranAyahTafseer rows.
 */
data class AyahTafseer(
    val surahNumber: Int,
    val ayahNumber: Int,
    val ayahText: String,
    val tafseerSaadi: String,
    val tafseerMoysar: String,
    val tafseerBaghawi: String,
    val ayahMeanings: String,
) {
    companion object {
        val EMPTY = AyahTafseer(0, 0, "", "", "", "", "")
    }
}

/** Reads per-ayah tafseer and word meanings. */
interface QuranTafseerRepository {
    suspend fun getTafseer(surahNumber: Int, ayahNumber: Int): AyahTafseer?
}

expect fun createQuranTafseerRepository(): QuranTafseerRepository
