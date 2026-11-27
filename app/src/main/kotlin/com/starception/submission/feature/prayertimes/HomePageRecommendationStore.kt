package com.starception.submission.feature.prayertimes

import android.content.Context
import android.content.SharedPreferences
import com.starception.submission.core.model.deenly.packInsightFields
import com.starception.submission.core.model.deenly.unpackInsightFields

/** Last grounded recommendation for each Home cover page, retained across app launches. */
internal class HomePageRecommendationStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("home_page_recommendations_v1", Context.MODE_PRIVATE),
    )

    fun restore(day: String, state: HomePageGenerationState, contextKey: String = "default") {
        (0 until 5 + 5 * KnowledgeSourceKind.entries.size).forEach { page ->
            val stored = runCatching {
                val fields = unpackInsightFields(preferences.getString("page_$page", null) ?: return@runCatching null)
                if (fields.size !in 2..3) return@runCatching null
                Triple(fields[0], if (fields.size == 3) fields[1] else "legacy",
                    PreparedInsightReadings.decodeAiCandidate(fields.last()))
            }.getOrNull() ?: return@forEach
            if (state.generated[page] == null) state.generated[page] = stored.third
            // Yesterday's recommendation stays visible while today's turn runs.
            if (stored.first == day && stored.second == contextKey) {
                state.resolvedSlots[page] = true
                if (stored.third.modelGenerated) state.attempted[page] = true
            }
        }
        for (page in 0..4) {
            if (state.additionalRecommendations[page] != null) continue
            val fields = runCatching { unpackInsightFields(preferences.getString("more_$page", "").orEmpty()) }
                .getOrNull() ?: continue
            if (fields.getOrNull(0) != day || fields.getOrNull(1) != contextKey) continue
            val readings = fields.drop(2).mapNotNull {
                runCatching { PreparedInsightReadings.decodeAiCandidate(it) }.getOrNull()
            }.distinctBy(NowNudgeKnowledgeCandidate::sourceId)
            if (readings.isNotEmpty()) state.additionalRecommendations[page] = readings
        }
    }

    fun save(day: String, page: Int, candidate: NowNudgeKnowledgeCandidate, contextKey: String = "default") {
        require(page in 0 until 5 + 5 * KnowledgeSourceKind.entries.size)
        val value = packInsightFields(listOf(day, contextKey, PreparedInsightReadings.encodeAiCandidate(candidate)))
        preferences.edit().putString("page_$page", value).apply()
    }

    fun saveAdditional(day: String, page: Int, readings: List<NowNudgeKnowledgeCandidate>, contextKey: String) {
        require(page in 0..4)
        val value = packInsightFields(listOf(day, contextKey) + readings.map(PreparedInsightReadings::encodeAiCandidate))
        preferences.edit().putString("more_$page", value).apply()
    }
}
