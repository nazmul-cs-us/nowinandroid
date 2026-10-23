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

package com.starception.submission.feature.prayertimes

import android.content.Context
import com.starception.submission.core.hadithdatabase.HadithDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import com.starception.submission.core.model.data.HadithCollections
import com.starception.submission.core.model.deenly.PrayerHadithCandidate
import com.starception.submission.core.model.deenly.PrayerHadithSelection
import com.starception.submission.ml.DeenlyKnowledgeModel
import com.starception.submission.prayer.model.prayerHadithContext
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PrayerHadithRecommendation(val source: PrayerHadithCandidate, val selectedByModel: Boolean)

internal object PrayerHadithRecommendationRepository {
    suspend fun load(context: Context, prayerName: String, date: LocalDate): PrayerHadithRecommendation? =
        withContext(Dispatchers.IO) {
            val prayer = prayerHadithContext(prayerName, kotlinx.datetime.LocalDate(date.year, date.monthValue, date.dayOfMonth))
            var arabicSql = "COALESCE(text_arabic, '')"
            ((0x064B..0x0652).map(Int::toChar) + listOf('\u0670', '\u0640')).forEach { mark ->
                arabicSql = "replace($arabicSql, '$mark', '')"
            }
            val where = prayer.queries.joinToString(" OR ") { "(text_plain LIKE ? OR $arabicSql LIKE ?)" }
            val arguments = prayer.queries.flatMap { listOf("%$it%", "%$it%") }.toTypedArray()
            val candidates = HadithCollections.all
                .filter { HadithDatabase.isDatabaseAvailable(context, it.databaseFile) }
                .flatMap { collection ->
                    val hits = try {
                        HadithDatabase.getInstance(context, collection.databaseFile).hadithDao().searchHadithsRanked(
                            SimpleSQLiteQuery("SELECT * FROM hadiths WHERE $where ORDER BY id ASC LIMIT 12", arguments),
                        )
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { emptyList() }
                    hits.mapNotNull { hadith ->
                        val text = hadith.textPlain?.takeIf(String::isNotBlank) ?: hadith.textArabic
                        text.takeIf { it.length in 40..3000 && prayer.accepts(it) }?.let {
                            PrayerHadithCandidate(collection.databaseFile, collection.name, hadith.id, it)
                        }
                    }
                }
            val shortlist = PrayerHadithSelection.shortlist(candidates, date.toEpochDay().toInt())
            if (shortlist.isEmpty()) return@withContext null
            val selected = DeenlyKnowledgeModel(context).selectPrayerHadith(
                "Local date $date. ${prayer.heading(prayerName)}. Recommend guidance relevant to this prayer.", shortlist,
            )
            PrayerHadithRecommendation(selected ?: shortlist.first(), selected != null)
        }
}
