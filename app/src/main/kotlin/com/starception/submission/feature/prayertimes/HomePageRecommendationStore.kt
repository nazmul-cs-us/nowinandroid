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

    fun restore(day: String, state: HomePageGenerationState) {
        (0..4).forEach { page ->
            val stored = runCatching {
                val fields = unpackInsightFields(preferences.getString("page_$page", null) ?: return@runCatching null)
                if (fields.size != 2) return@runCatching null
                fields[0] to PreparedInsightReadings.decodeAiCandidate(fields[1])
            }.getOrNull() ?: return@forEach
            if (state.generated[page] == null) state.generated[page] = stored.second
            // Yesterday's recommendation stays visible while today's turn runs.
            if (stored.first == day) state.attempted[page] = true
        }
    }

    fun save(day: String, page: Int, candidate: NowNudgeKnowledgeCandidate) {
        require(page in 0..4)
        val value = packInsightFields(listOf(day, PreparedInsightReadings.encodeAiCandidate(candidate)))
        preferences.edit().putString("page_$page", value).apply()
    }
}
