package com.starception.submission.shared.ui

import androidx.compose.runtime.mutableStateMapOf
import com.starception.submission.shared.ml.DeenlyKnowledgeRuntime
import com.starception.submission.shared.storage.platformKeyValueStore
import kotlinx.coroutines.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Keep yesterday's grounded evidence visible until today's page turn is ready. */
internal object HomePageReflections {
    val evidence = mutableStateMapOf<Int, HomeReflectionEvidence>()
    val cards = mutableStateMapOf<Int, DeenlyKnowledgeCard>()
    private val attempts = mutableSetOf<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val store by lazy { platformKeyValueStore() }
    @Serializable private data class Stored(val day: String, val evidence: HomeReflectionEvidence, val card: DeenlyKnowledgeCard? = null)

    fun prepare(day: LocalDate, page: Int) {
        if (page !in 0..4) return
        val key = "$day:$page"
        val saved = runCatching {
            Json.decodeFromString<Stored>(store.getString("home_reflection_$page").orEmpty())
        }.getOrNull()
        if (evidence[page] == null && saved != null) evidence[page] = saved.evidence
        saved?.card?.let { if (cards[page] == null) cards[page] = it }
        if ((saved?.day == day.toString() && saved.card != null) || key in attempts || !DeenlyKnowledgeRuntime.isKnowledgeModelAvailable()) return
        attempts += key
        scope.launch {
            try {
                val card = generateInsightKnowledgeCard(day, homeFeedKnowledgeQuery(page), emptyMap(), variation = page) ?: return@launch
                val result = HomeReflectionEvidence(card.sourceText, card.reference)
                evidence[page] = result
                cards[page] = card
                store.putString("home_reflection_$page", Json.encodeToString(Stored(day.toString(), result, card)))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { attempts -= key }
        }
    }
}
