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

package com.starception.submission.widget

import android.content.Context
import android.util.Log
import com.starception.submission.core.qurandatabase.QuranTranslationRepository
import com.starception.submission.download.AssetRepository
import java.time.LocalDate

/** A short, source-backed Quran translation shared by the prayer poster and hero widget. */
internal data class WidgetAyah(
    val text: String,
    val citation: String,
    /** Exact in-app destination for the translation shown beside the poster's more button. */
    val target: WidgetNavigationTarget?,
)

/**
 * Selects one readable English ayah per day from the app's downloaded Quran database.
 *
 * The date chooses a deterministic starting surah and ayah, so every prayer widget agrees
 * for the whole day. A few nearby surahs are searched when the first verse is too long for
 * the compact glass card. The fixed verse is only an offline fallback for a device that has
 * not downloaded the English Quran database yet.
 */
internal object DailyAyahRepository {
    private const val TAG = "DailyAyahRepository"
    private const val MIN_TEXT_LENGTH = 24
    private const val MAX_TEXT_LENGTH = 105
    private const val SURAH_ATTEMPTS = 12

    suspend fun load(
        context: Context,
        assetRepository: AssetRepository,
        date: LocalDate = LocalDate.now(),
    ): WidgetAyah {
        return runCatching {
            val repository = QuranTranslationRepository(
                context = context.applicationContext,
                translationCode = "en",
                assetRepository = assetRepository,
            )
            val surahs = repository.getAllSurahsWithCounts().filter { it.ayahCount > 0 }
            if (surahs.isEmpty()) return@runCatching null

            val daySeed = date.toEpochDay()
            repeat(minOf(SURAH_ATTEMPTS, surahs.size)) { attempt ->
                val surahIndex = Math.floorMod(daySeed * 37L + attempt * 29L, surahs.size.toLong())
                    .toInt()
                val surah = surahs[surahIndex]
                val candidates = repository.getAyahsBySurahNumber(surah.number)
                    .map { ayah -> ayah to ayah.text.toWidgetAyahText() }
                    .filter { (_, text) -> text.length in MIN_TEXT_LENGTH..MAX_TEXT_LENGTH }
                if (candidates.isNotEmpty()) {
                    val ayahIndex = Math.floorMod(daySeed * 53L + attempt * 17L, candidates.size.toLong())
                        .toInt()
                    val (ayah, text) = candidates[ayahIndex]
                    val surahName = surah.nameEnglish.ifBlank { "Surah ${surah.number}" }
                    return@runCatching WidgetAyah(
                        text = text,
                        citation = "$surahName ${surah.number}:${ayah.numberInSurah}",
                        target = WidgetNavigationTarget.Surah(
                            surahNumber = surah.number,
                            ayahNumber = ayah.numberInSurah,
                        ),
                    )
                }
            }
            null
        }.onFailure { error ->
            Log.w(TAG, "Daily Quran verse unavailable; using the offline fallback", error)
        }.getOrNull() ?: FALLBACK
    }

    private fun String.toWidgetAyahText(): String =
        replace(Regex("\\[[^]]*]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private val FALLBACK = WidgetAyah(
        text = "And to Allah belongs the east and the west. Wherever you turn, there is the Face of Allah.",
        citation = "Al-Baqarah 2:115",
        target = WidgetNavigationTarget.Surah(surahNumber = 2, ayahNumber = 115),
    )
}
