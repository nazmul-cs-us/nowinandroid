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

package com.starception.submission.shared.hadith

import com.starception.submission.core.model.data.HadithCollections
import com.starception.submission.core.model.deenly.PrayerHadithCandidate
import com.starception.submission.core.model.deenly.PrayerHadithSelection
import com.starception.submission.prayer.model.prayerHadithContext
import com.starception.submission.shared.ml.DeenlyKnowledgeRuntime
import kotlinx.datetime.LocalDate

internal data class PrayerHadithRecommendation(val source: PrayerHadithCandidate, val selectedByModel: Boolean)

internal suspend fun recommendPrayerHadith(prayerName: String?, date: LocalDate): PrayerHadithRecommendation? {
    val context = prayerHadithContext(prayerName, date)
    val repository = createSharedHadithRepository()
    val candidates = repository.availableCollectionDatabaseFiles().flatMap { databaseFile ->
        val collection = HadithCollections.byDatabaseFile(databaseFile) ?: return@flatMap emptyList()
        val hits = try { repository.searchCollectionHadiths(databaseFile, context.queries, 12) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { emptyList() }
        hits.distinctBy { it.id }.mapNotNull { hadith ->
            val text = hadith.english.takeIf(String::isNotBlank) ?: hadith.arabic
            text.takeIf { it.length in 40..3000 && context.accepts(it) }?.let {
                PrayerHadithCandidate(databaseFile, collection.name, hadith.id, it)
            }
        }
    }
    val shortlist = PrayerHadithSelection.shortlist(candidates, date.toEpochDays().toInt())
    if (shortlist.isEmpty()) return null
    val selected = DeenlyKnowledgeRuntime.selectPrayerHadith(
        "Local date $date. ${context.heading(prayerName ?: "Salah")}. Recommend guidance relevant to this prayer.", shortlist,
    )
    return PrayerHadithRecommendation(selected ?: shortlist.first(), selected != null)
}
