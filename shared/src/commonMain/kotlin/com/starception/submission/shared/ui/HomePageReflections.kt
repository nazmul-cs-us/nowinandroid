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

    val readings = mutableStateMapOf<Int, List<DeenlyKnowledgeCard>>()
    val loadingReadings = mutableStateMapOf<Int, Boolean>()
    private val readingAttempts = mutableSetOf<String>()
    private var readingDay: LocalDate? = null
    @Serializable private data class StoredReadings(val day: String, val cards: List<DeenlyKnowledgeCard>)

    fun prepareReadings(day: LocalDate, page: Int, more: Boolean = false) {
        if (readingDay != day) {
            readingDay = day
            readings.clear(); loadingReadings.clear(); readingAttempts.clear()
        }
        if (page !in 0..4 || loadingReadings[page] == true) return
        val storageKey = "home_further_readings_$page"
        if (readings[page] == null) runCatching {
            Json.decodeFromString<StoredReadings>(store.getString(storageKey).orEmpty())
        }.getOrNull()?.takeIf { it.day == day.toString() }?.let { readings[page] = it.cards }
        val current = readings[page].orEmpty()
        if ((!more && current.isNotEmpty()) || current.size >= 18 || !DeenlyKnowledgeRuntime.isKnowledgeModelAvailable()) return
        val key = "$day:$page:${current.size}"
        if (!readingAttempts.add(key)) return
        loadingReadings[page] = true
        scope.launch {
            try {
                val kinds = if (current.isEmpty()) HomeReadingKind.entries else listOf(HomeReadingKind.entries[(current.size - 3).coerceAtLeast(0) % 3])
                for (kind in kinds) {
                    val card = generateInsightKnowledgeCard(day, homeFeedKnowledgeQuery(page), emptyMap(),
                        variation = page * 19 + current.size * 31 + kind.ordinal, sourceKind = kind,
                        readingSurah = if (page == 2 && kind == HomeReadingKind.Quran)
                            com.starception.submission.feature.quran.dailyReading(day).number else null) ?: continue
                    if (readingDay != day) return@launch
                    val updated = (readings[page].orEmpty() + card).distinctBy { it.target }
                    readings[page] = updated
                    store.putString(storageKey, Json.encodeToString(StoredReadings(day.toString(), updated)))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { readingAttempts.remove(key) }
            finally { if (readingDay == day) loadingReadings[page] = false }
        }
    }

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
                val card = generateInsightKnowledgeCard(day, homeFeedKnowledgeQuery(page), emptyMap(), variation = page,
                    sourceKind = if (page == 2) HomeReadingKind.Quran else null,
                    readingSurah = if (page == 2) com.starception.submission.feature.quran.dailyReading(day).number else null) ?: return@launch
                val result = HomeReflectionEvidence(card.sourceText, card.reference, card.title,
                    (card.target as? DeenlyKnowledgeTarget.Surah)?.number)
                evidence[page] = result
                cards[page] = card
                store.putString("home_reflection_$page", Json.encodeToString(Stored(day.toString(), result, card)))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { attempts -= key }
        }
    }
}
