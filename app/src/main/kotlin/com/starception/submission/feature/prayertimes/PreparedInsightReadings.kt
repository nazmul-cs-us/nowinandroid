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
import com.starception.submission.core.duadatabase.Dua
import com.starception.submission.core.model.deenly.DeenlyNudge
import com.starception.submission.core.model.deenly.DeenlyNudgeAction
import com.starception.submission.core.model.deenly.InsightReadAhead
import com.starception.submission.core.model.deenly.PrayerHadithCandidate
import com.starception.submission.core.model.deenly.packInsightFields
import com.starception.submission.core.model.deenly.unpackInsightFields
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Application-owned preparation; preferences retain both readings across process restarts. */
internal object PreparedInsightReadings {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var prayerCache: InsightReadAhead<PrayerHadithRecommendation>? = null
    private var aiCache: InsightReadAhead<NowNudgeKnowledgeCandidate>? = null

    private fun <T : Any> cache(context: Context, name: String, encode: (T) -> List<String>, decode: (List<String>) -> T): InsightReadAhead<T> {
        val prefs = context.applicationContext.getSharedPreferences("prepared_insight_readings_v1", Context.MODE_PRIVATE)
        return InsightReadAhead(scope, { packInsightFields(encode(it)) }, { decode(unpackInsightFields(it)) },
            { prefs.getString(name, null) }, { prefs.edit().putString(name, it).apply() })
    }

    fun prayer(context: Context): InsightReadAhead<PrayerHadithRecommendation> = prayerCache ?: cache(context, "prayer_v2", { item: PrayerHadithRecommendation ->
        listOf(item.source.databaseFile, item.source.collection, item.source.hadithNumber.toString(), item.source.text, item.selectedByModel.toString())
    }, { fields ->
        PrayerHadithRecommendation(PrayerHadithCandidate(fields[0], fields[1], fields[2].toInt(), fields[3]), fields[4].toBooleanStrict())
    }).also { prayerCache = it }

    fun ai(context: Context): InsightReadAhead<NowNudgeKnowledgeCandidate> = aiCache ?: cache(context, "ai", { item: NowNudgeKnowledgeCandidate ->
        val nudge = item.nudge
        listOf(nudge.id, nudge.action.name, nudge.label, nudge.prayerName.orEmpty(), nudge.actionId,
            nudge.supportingText.orEmpty(), nudge.sourceLabel.orEmpty(), item.sourceId, item.collection, item.topic, item.rankingScore.toString()) +
            when (val target = item.target) {
                is NowNudgeKnowledgeTarget.QuranAyah -> listOf("quran", target.surahNumber.toString(), target.ayahNumber.toString())
                is NowNudgeKnowledgeTarget.Hadith -> listOf("hadith", target.collectionName, target.databaseFile, target.hadithNumber.toString())
                is NowNudgeKnowledgeTarget.FortressDua -> with(target.dua) {
                    listOf("dua", id.toString(), chapterId.toString(), chapterTitle, position.toString(), arabic.orEmpty(),
                        transliteration.orEmpty(), translation.orEmpty(), this.context.orEmpty(), instruction.orEmpty(), note.orEmpty(),
                        postContext.orEmpty(), description.orEmpty(), audioUrl.orEmpty())
                }
            }
    }, { fields ->
        val target = when (fields[11]) {
            "quran" -> NowNudgeKnowledgeTarget.QuranAyah(fields[12].toInt(), fields[13].toInt())
            "hadith" -> NowNudgeKnowledgeTarget.Hadith(fields[12], fields[13], fields[14].toInt())
            "dua" -> NowNudgeKnowledgeTarget.FortressDua(Dua(fields[12].toInt(), fields[13].toInt(), fields[14], fields[15].toInt(),
                fields[16], fields[17], fields[18], fields[19], fields[20], fields[21], fields[22], fields[23], fields[24].ifEmpty { null }))
            else -> error("Unknown insight destination")
        }
        NowNudgeKnowledgeCandidate(DeenlyNudge(fields[0], DeenlyNudgeAction.valueOf(fields[1]), fields[2],
            fields[3].ifEmpty { null }, fields[4], fields[5].ifEmpty { null }, fields[6].ifEmpty { null }),
            target, fields[7], fields[8], fields[9], fields[10].toFloat())
    }).also { aiCache = it }
}
