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

import com.starception.submission.core.model.deenly.InsightSearchProgress
import com.starception.submission.core.model.data.HadithCollections
import com.starception.submission.core.model.deenly.PrayerHadithCandidate
import com.starception.submission.core.model.deenly.PrayerHadithSelection
import com.starception.submission.prayer.model.prayerHadithContext
import com.starception.submission.shared.ml.DeenlyKnowledgeRuntime
import kotlinx.datetime.LocalDate

internal data class PrayerHadithRecommendation(val source: PrayerHadithCandidate, val selectedByModel: Boolean)

internal suspend fun recommendPrayerHadith(
    prayerName: String?,
    date: LocalDate,
    onProgress: suspend (InsightSearchProgress) -> Unit = {},
): PrayerHadithRecommendation? {
    val context = prayerHadithContext(prayerName, date)
    val repository = createSharedHadithRepository()
    var searchFailures = 0
    val collections = repository.availableCollectionDatabaseFiles()
    val searched = mutableListOf<String>()
    val candidates = collections.flatMap { databaseFile ->
        val collection = HadithCollections.byDatabaseFile(databaseFile) ?: return@flatMap emptyList()
        onProgress(InsightSearchProgress(
            title = "Finding a Hadith for this prayer",
            detail = "Searching ${collection.name}…",
            completed = searched.size,
            total = collections.size,
            sources = searched.toList(),
            determinate = true,
        ))
        val hits = try { repository.searchCollectionHadiths(databaseFile, context.queries, 48) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { searchFailures++; emptyList() }
        searched += collection.name
        onProgress(InsightSearchProgress(
            title = "Checking prayer narrations",
            detail = "Finished ${collection.name}" +
                if (searchFailures > 0) " · $searchFailures collection(s) could not be read" else "",
            completed = searched.size,
            total = collections.size,
            sources = searched.toList(),
            determinate = true,
        ))
        hits.distinctBy { it.id }.mapNotNull { hadith ->
            val text = hadith.english.takeIf(String::isNotBlank) ?: hadith.arabic
            text.takeIf { it.length >= 40 && context.accepts(it) }?.let {
                PrayerHadithCandidate(databaseFile, collection.name, hadith.id, it)
            }
        }
    }
    val shortlist = PrayerHadithSelection.shortlist(candidates, date.toEpochDays().toInt())
    if (shortlist.isEmpty()) {
        check(searchFailures == 0) { "Unable to search available Hadith collections" }
        return null
    }
    if (DeenlyKnowledgeRuntime.isKnowledgeModelAvailable()) {
        onProgress(InsightSearchProgress(
            title = "Selecting a Hadith for this prayer",
            detail = "The model is reviewing ${shortlist.size} matching narrations for relevance.",
            sources = searched.toList(),
        ))
    }
    val selected = DeenlyKnowledgeRuntime.selectPrayerHadith(
        "Local date $date. ${context.heading(prayerName ?: "Salah")}. Recommend guidance relevant to this prayer.", shortlist,
    )
    return PrayerHadithRecommendation(selected ?: shortlist.first(), selected != null)
}
