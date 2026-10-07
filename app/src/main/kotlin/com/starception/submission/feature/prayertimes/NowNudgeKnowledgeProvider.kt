/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.feature.prayertimes

import android.content.Context
import android.content.SharedPreferences
import com.starception.submission.core.duadatabase.Dua
import com.starception.submission.core.duadatabase.DuaRepository
import com.starception.submission.core.hadithdatabase.BukhariLocalTranslationRepository
import com.starception.submission.core.hadithdatabase.HadithCollectionMetadata
import com.starception.submission.core.hadithdatabase.HadithDatabase
import com.starception.submission.core.hadithdatabase.HadithRepository
import com.starception.submission.core.model.data.BukhariBooks
import com.starception.submission.core.model.data.ShamayelBooks
import com.starception.submission.core.model.deenly.DeenlyActionIds
import com.starception.submission.core.model.deenly.DeenlyActivity
import com.starception.submission.core.model.deenly.DeenlyNudge
import com.starception.submission.core.model.deenly.DeenlyNudgeAction
import com.starception.submission.core.model.deenly.DeenlyNudgeCandidate
import com.starception.submission.core.model.deenly.DeenlyRankingContext
import com.starception.submission.core.model.deenly.DeenlyRankingSignals
import com.starception.submission.core.model.deenly.IslamicContentSource
import com.starception.submission.core.model.deenly.IslamicQuizDifficulty
import com.starception.submission.core.model.deenly.IslamicQuizQuestion
import com.starception.submission.core.model.deenly.rankDeenlyNudges
import com.starception.submission.core.qurandatabase.QuranTranslationRepository
import com.starception.submission.download.AssetRepository
import com.starception.submission.ml.DeenlyKnowledgeDecision
import com.starception.submission.ml.DeenlyKnowledgeModel
import com.starception.submission.ml.DeenlyKnowledgeTask
import com.starception.submission.ml.DeenlySourceEnvelope
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface NowNudgeKnowledgeTarget {
    data class QuranAyah(
        val surahNumber: Int,
        val ayahNumber: Int,
    ) : NowNudgeKnowledgeTarget

    data class Hadith(
        val collectionName: String,
        val databaseFile: String,
        val hadithNumber: Int,
    ) : NowNudgeKnowledgeTarget

    data class FortressDua(val dua: Dua) : NowNudgeKnowledgeTarget
}

internal data class GroundedKnowledgeSource(
    val id: String,
    val collection: String,
    val reference: String,
    val topic: String,
    val sourceText: String,
    val target: NowNudgeKnowledgeTarget,
)

internal data class NowNudgeKnowledgeCandidate(
    val nudge: DeenlyNudge,
    val target: NowNudgeKnowledgeTarget,
    val sourceId: String,
    val collection: String,
    val topic: String,
    val rankingScore: Float,
    val quizQuestion: IslamicQuizQuestion? = null,
)

internal data class NowNudgeKnowledgeRankingContext(
    val nowMinute: Int = 0,
    val nowEpochMillis: Long = System.currentTimeMillis(),
    val currentPrayer: String? = null,
    val nextPrayer: String? = null,
    val completedPrayerCount: Int = 0,
    val activity: DeenlyActivity = DeenlyActivity.UNKNOWN,
    val isMediaPlaying: Boolean = false,
    val hasVerifiedLocation: Boolean = false,
    val dismissedNudgeIds: Set<String> = emptySet(),
)

internal data class KnowledgeEngagementSnapshot(
    val impressionsToday: Int = 0,
    val minutesSinceLastShown: Int? = null,
    val collectionOpenedCount: Int = 0,
    val collectionDismissedCount: Int = 0,
) {
    val affinity: Float
        get() = (collectionOpenedCount + 1f) /
            (collectionOpenedCount + collectionDismissedCount + 2f)
}

internal interface KnowledgeEngagementStore {
    fun snapshot(
        source: GroundedKnowledgeSource,
        date: LocalDate,
        nowEpochMillis: Long,
    ): KnowledgeEngagementSnapshot

    fun recordImpression(candidate: NowNudgeKnowledgeCandidate, date: LocalDate, nowEpochMillis: Long)
    fun recordOpened(candidate: NowNudgeKnowledgeCandidate)
    fun recordDismissed(candidate: NowNudgeKnowledgeCandidate)
}

private object EmptyKnowledgeEngagementStore : KnowledgeEngagementStore {
    override fun snapshot(
        source: GroundedKnowledgeSource,
        date: LocalDate,
        nowEpochMillis: Long,
    ) = KnowledgeEngagementSnapshot()

    override fun recordImpression(
        candidate: NowNudgeKnowledgeCandidate,
        date: LocalDate,
        nowEpochMillis: Long,
    ) = Unit

    override fun recordOpened(candidate: NowNudgeKnowledgeCandidate) = Unit
    override fun recordDismissed(candidate: NowNudgeKnowledgeCandidate) = Unit
}

internal fun interface GroundedKnowledgeSourceLoader {
    suspend fun load(date: LocalDate, variation: Int): GroundedKnowledgeSource?

    suspend fun search(query: String, limit: Int): List<GroundedKnowledgeSource> = emptyList()
}

internal fun interface KnowledgeDecisionGenerator {
    suspend fun generate(
        task: DeenlyKnowledgeTask,
        source: DeenlySourceEnvelope,
    ): DeenlyKnowledgeDecision?
}

/**
 * Turns one immutable database record into a grounded Now Nudge.
 *
 * The model may supply the title. If it is unavailable or invalid, a neutral title is built from
 * database metadata. The model never supplies the body, citation, identifier, or destination;
 * those values stay attached to the exact record selected by [GroundedKnowledgeSourceLoader].
 */
internal class NowNudgeKnowledgeProvider(
    private val sourceLoader: GroundedKnowledgeSourceLoader,
    private val decisionGenerator: KnowledgeDecisionGenerator,
    private val engagementStore: KnowledgeEngagementStore = EmptyKnowledgeEngagementStore,
) {
    suspend fun load(
        date: LocalDate,
        variation: Int = 0,
        appSituation: String? = null,
        query: String? = null,
        rankingContext: NowNudgeKnowledgeRankingContext = NowNudgeKnowledgeRankingContext(),
        task: DeenlyKnowledgeTask = DeenlyKnowledgeTask.KNOWLEDGE,
    ): NowNudgeKnowledgeCandidate? {
        val sources = withContext(Dispatchers.IO) {
            buildList {
                query?.takeIf(String::isNotBlank)?.let { searchQuery ->
                    addAll(sourceLoader.search(searchQuery, QUERY_SEARCH_LIMIT))
                }
                val poolSize = if (query.isNullOrBlank()) {
                    KNOWLEDGE_CANDIDATE_POOL_SIZE
                } else {
                    QUERY_CANDIDATE_POOL_SIZE
                }
                repeat(poolSize) { offset ->
                    sourceLoader.load(date, variation + offset)?.let(::add)
                }
            }.distinctBy(GroundedKnowledgeSource::id)
        }
        val rankedSources = rankSources(date, sources, rankingContext, query)
        val rankedSource = rankedSources.firstOrNull()
            // Dismissals are honored until the current candidate window is
            // exhausted. At that point prefer a fresh ranking over returning
            // no model turn and leaving the bot with nothing to present.
            ?: rankingContext.dismissedNudgeIds
                .takeIf(Set<String>::isNotEmpty)
                ?.let {
                    rankSources(
                        date = date,
                        sources = sources,
                        context = rankingContext.copy(dismissedNudgeIds = emptySet()),
                        query = query,
                    ).firstOrNull()
                }
            ?: return null
        val source = rankedSource.source
        val envelope = DeenlySourceEnvelope(
            collection = source.collection,
            reference = source.reference,
            topic = source.topic,
            appSituation = appSituation,
            sourceText = source.sourceText,
            sourceTextSha256 = source.sourceText.sha256(),
        )
        if (task == DeenlyKnowledgeTask.ANSWER) {
            return answerFromSources(
                date = date,
                sources = rankedSources.take(ANSWER_SOURCE_ATTEMPTS),
                userQuestion = query.orEmpty(),
                fallback = {
                    buildCandidate(
                        date = date,
                        source = source,
                        decision = decisionGenerator.generate(DeenlyKnowledgeTask.KNOWLEDGE, envelope),
                        rankingScore = rankedSource.score,
                    )
                },
            )
        }
        val decision = decisionGenerator.generate(task, envelope)
        return when {
            task == DeenlyKnowledgeTask.QUESTION &&
                decision is DeenlyKnowledgeDecision.GroundedQuestion &&
                decision.isGroundedIn(source.sourceText) -> buildGroundedQuestionCandidate(
                date = date,
                source = source,
                decision = decision,
                rankingScore = rankedSource.score,
            )
            task == DeenlyKnowledgeTask.QUESTION -> buildQuestionCandidate(
                date = date,
                source = source,
                rankingScore = rankedSource.score,
            )
            else -> buildCandidate(
                date = date,
                source = source,
                decision = decision,
                rankingScore = rankedSource.score,
            )
        }
    }

    /**
     * Answers the typed question from the highest-ranked retrieved sources.
     *
     * Each attempt hands the model one immutable source and accepts only an exact
     * quote. An honest "unanswered" or any validation failure moves to the next
     * source; when every attempt is exhausted the caller falls back to the
     * verbatim source card so the user always sees a grounded result.
     */
    private suspend fun answerFromSources(
        date: LocalDate,
        sources: List<RankedKnowledgeSource>,
        userQuestion: String,
        fallback: suspend () -> NowNudgeKnowledgeCandidate?,
    ): NowNudgeKnowledgeCandidate? {
        val question = userQuestion.trim()
        if (question.isEmpty()) return fallback()
        sources.forEach { ranked ->
            val source = ranked.source
            val decision = decisionGenerator.generate(
                task = DeenlyKnowledgeTask.ANSWER,
                source = DeenlySourceEnvelope(
                    collection = source.collection,
                    reference = source.reference,
                    topic = source.topic,
                    userQuestion = question,
                    sourceText = source.sourceText,
                    sourceTextSha256 = source.sourceText.sha256(),
                ),
            )
            if (decision is DeenlyKnowledgeDecision.GroundedAnswer) {
                return buildAnswerCandidate(
                    date = date,
                    source = source,
                    decision = decision,
                    rankingScore = ranked.score,
                )
            }
        }
        return fallback()
    }

    fun recordImpression(
        candidate: NowNudgeKnowledgeCandidate,
        date: LocalDate,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) = engagementStore.recordImpression(candidate, date, nowEpochMillis)

    fun recordOpened(candidate: NowNudgeKnowledgeCandidate) =
        engagementStore.recordOpened(candidate)

    fun recordDismissed(candidate: NowNudgeKnowledgeCandidate) =
        engagementStore.recordDismissed(candidate)

    private fun rankSources(
        date: LocalDate,
        sources: List<GroundedKnowledgeSource>,
        context: NowNudgeKnowledgeRankingContext,
        query: String? = null,
    ): List<RankedKnowledgeSource> {
        val typedQuery = query?.takeIf(String::isNotBlank)
        val eligibleSources = if (typedQuery == null) {
            sources
        } else {
            sources.filter { it.queryMatch(typedQuery) > 0f }
        }
        val byNudgeId = eligibleSources.associateBy { source -> source.nudgeId(date) }
        return rankDeenlyNudges(
            candidates = eligibleSources.map { source ->
                val engagement = engagementStore.snapshot(
                    source = source,
                    date = date,
                    nowEpochMillis = context.nowEpochMillis,
                )
                DeenlyNudgeCandidate(
                    nudge = DeenlyNudge(
                        id = source.nudgeId(date),
                        action = DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
                        label = source.topic,
                        actionId = DeenlyActionIds.LEARNING_OPEN_KNOWLEDGE,
                    ),
                    signals = DeenlyRankingSignals(
                        basePriority = 0.5f,
                        contextMatch = typedQuery
                            ?.let(source::queryMatch)
                            ?: source.contextMatch(context),
                        userAffinity = engagement.affinity,
                        freshness = engagement.minutesSinceLastShown
                            ?.let { (it / MINUTES_FOR_FULL_FRESHNESS).coerceIn(0f, 1f) }
                            ?: 1f,
                        expectedCompletion = source.expectedCompletion(),
                    ),
                    minutesSinceLastShown = engagement.minutesSinceLastShown,
                    impressionsToday = engagement.impressionsToday,
                )
            },
            context = DeenlyRankingContext(
                isMediaPlaying = context.isMediaPlaying,
                dismissedNudgeIds = context.dismissedNudgeIds,
            ),
        ).mapNotNull { ranked ->
            byNudgeId[ranked.candidate.nudge.id]?.let { source ->
                RankedKnowledgeSource(source = source, score = ranked.score)
            }
        }
    }

    private data class RankedKnowledgeSource(
        val source: GroundedKnowledgeSource,
        val score: Float,
    )

    companion object {
        fun create(
            context: Context,
            assetRepository: AssetRepository,
            duaRepository: DuaRepository,
        ): NowNudgeKnowledgeProvider {
            val appContext = context.applicationContext
            val model = DeenlyKnowledgeModel(appContext)
            return NowNudgeKnowledgeProvider(
                sourceLoader = AndroidGroundedKnowledgeSourceLoader(
                    context = appContext,
                    assetRepository = assetRepository,
                    duaRepository = duaRepository,
                ),
                decisionGenerator = KnowledgeDecisionGenerator(model::generate),
                engagementStore = AndroidKnowledgeEngagementStore(appContext),
            )
        }

        internal fun buildCandidate(
            date: LocalDate,
            source: GroundedKnowledgeSource,
            decision: DeenlyKnowledgeDecision?,
            rankingScore: Float = 0f,
        ): NowNudgeKnowledgeCandidate {
            val title = (decision as? DeenlyKnowledgeDecision.Knowledge)
                ?.title
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.take(MAX_TITLE_CHARS)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: source.fallbackTitle()
            return NowNudgeKnowledgeCandidate(
                nudge = DeenlyNudge(
                    id = source.nudgeId(date),
                    action = DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
                    label = title,
                    actionId = DeenlyActionIds.LEARNING_OPEN_KNOWLEDGE,
                    supportingText = source.sourceText,
                    sourceLabel = source.reference,
                ),
                target = source.target,
                sourceId = source.id,
                collection = source.collection,
                topic = source.topic,
                rankingScore = rankingScore,
            )
        }

        internal fun buildQuestionCandidate(
            date: LocalDate,
            source: GroundedKnowledgeSource,
            rankingScore: Float = 0f,
        ): NowNudgeKnowledgeCandidate {
            val answer = source.sourceLocationAnswer()
            val options = (SOURCE_LOCATION_OPTIONS + answer)
                .distinct()
                .filterNot { it == answer }
                .sortedBy { option -> "${source.id}|$option".sha256() }
                .take(3)
                .plus(answer)
                .sortedBy { option -> "${date}|${source.id}|$option".sha256() }
            val promptLead = when (source.target) {
                is NowNudgeKnowledgeTarget.QuranAyah ->
                    "Which source contains this translated passage?"
                is NowNudgeKnowledgeTarget.Hadith ->
                    "Which Hadith collection records this narration?"
                is NowNudgeKnowledgeTarget.FortressDua ->
                    "Which dua collection contains this invocation?"
            }
            val question = IslamicQuizQuestion(
                id = "model-question-$date-${source.id}",
                prompt = "$promptLead\n\n${source.sourceText}",
                options = options,
                correctOption = options.indexOf(answer),
                explanation = "This passage is recorded as ${source.reference}.",
                sourceLabel = source.reference,
                sourceUrl = source.sourceUrl(),
                sourceCollection = source.contentSource(),
                sourceReference = source.reference,
                difficulty = IslamicQuizDifficulty.INTERMEDIATE,
                topic = source.topic,
                sourceText = source.sourceText,
                sourceTextSha256 = source.sourceText.sha256(),
            )
            return NowNudgeKnowledgeCandidate(
                nudge = DeenlyNudge(
                    id = source.nudgeId(date),
                    action = DeenlyNudgeAction.PLAY_QUIZ,
                    label = promptLead,
                    actionId = DeenlyActionIds.LEARNING_START_QUIZ,
                    supportingText = source.sourceText,
                    sourceLabel = "Tap to answer",
                ),
                target = source.target,
                sourceId = source.id,
                collection = source.collection,
                topic = source.topic,
                rankingScore = rankingScore,
                quizQuestion = question,
            )
        }

        internal fun buildGroundedQuestionCandidate(
            date: LocalDate,
            source: GroundedKnowledgeSource,
            decision: DeenlyKnowledgeDecision.GroundedQuestion,
            rankingScore: Float = 0f,
        ): NowNudgeKnowledgeCandidate {
            require(decision.isGroundedIn(source.sourceText)) {
                "A generated question must be grounded in the immutable source text"
            }
            val correctOption = decision.options.indexOf(decision.answer)
            val question = IslamicQuizQuestion(
                id = "model-global-question-$date-${source.id}",
                prompt = decision.question,
                options = decision.options,
                correctOption = correctOption,
                explanation = "${decision.answer}. Evidence: \u201c${decision.evidence}\u201d",
                sourceLabel = source.reference,
                sourceUrl = source.sourceUrl(),
                sourceCollection = source.contentSource(),
                sourceReference = source.reference,
                difficulty = decision.questionKind.toDifficulty(),
                topic = source.topic,
                sourceText = source.sourceText,
                sourceTextSha256 = source.sourceText.sha256(),
            )
            return NowNudgeKnowledgeCandidate(
                nudge = DeenlyNudge(
                    id = "model-global-question-$date-${source.id}",
                    action = DeenlyNudgeAction.PLAY_QUIZ,
                    label = decision.question,
                    actionId = DeenlyActionIds.LEARNING_START_QUIZ,
                    supportingText = decision.options.joinToString(separator = "  \u2022  "),
                    sourceLabel = "Tap to answer",
                ),
                target = source.target,
                sourceId = source.id,
                collection = source.collection,
                topic = source.topic,
                rankingScore = rankingScore,
                quizQuestion = question,
            )
        }

        /**
         * A grounded direct answer: the label is neutral metadata (never model
         * generated), the supporting text leads with the verified quote and
         * follows with the complete verbatim source, and the citation remains
         * the exact database reference so the card opens the real record.
         */
        internal fun buildAnswerCandidate(
            date: LocalDate,
            source: GroundedKnowledgeSource,
            decision: DeenlyKnowledgeDecision.GroundedAnswer,
            rankingScore: Float = 0f,
        ): NowNudgeKnowledgeCandidate {
            val answerTopic = source.topic
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(MAX_TITLE_CHARS)
                .trim()
            return NowNudgeKnowledgeCandidate(
                nudge = DeenlyNudge(
                    id = "model-answer-$date-${source.id}",
                    action = DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
                    label = if (answerTopic.isNotEmpty()) "About $answerTopic" else "Your question",
                    actionId = DeenlyActionIds.LEARNING_OPEN_KNOWLEDGE,
                    supportingText = "\u201c${decision.answer}\u201d\n\n${source.sourceText}",
                    sourceLabel = source.reference,
                ),
                target = source.target,
                sourceId = source.id,
                collection = source.collection,
                topic = source.topic,
                rankingScore = rankingScore,
            )
        }

        private const val MAX_TITLE_CHARS = 84
        private const val KNOWLEDGE_CANDIDATE_POOL_SIZE = 22
        private const val QUERY_CANDIDATE_POOL_SIZE = 64
        // The typed-question search spans Fortress duas, Quran ayahs, and every
        // Hadith collection; ranking happens after retrieval, so the cap only
        // bounds how many verified sources are worth ranking at all.
        private const val QUERY_SEARCH_LIMIT = 32
        private const val ANSWER_SOURCE_ATTEMPTS = 3
        private const val MINUTES_FOR_FULL_FRESHNESS = 24f * 60f
        private val SOURCE_LOCATION_OPTIONS = listOf(
            "The Quran",
            "Sahih al-Bukhari",
            "Sahih Muslim",
            "Sunan Abu Dawud",
            "Jami' at-Tirmidhi",
            "Sunan an-Nasa'i",
            "Sunan Ibn Majah",
            "Muwatta Malik",
            "Musnad Ahmad",
            "Sunan ad-Darimi",
            "Shama'il At-Tirmidhi",
        )
    }
}

private fun DeenlyKnowledgeDecision.GroundedQuestion.isGroundedIn(sourceText: String): Boolean =
    question.isNotBlank() &&
        answer.isNotBlank() &&
        evidence.isNotBlank() &&
        answer in sourceText &&
        evidence in sourceText &&
        answer in evidence &&
        options.size == 4 &&
        options.distinctBy(String::lowercase).size == 4 &&
        options.count { it == answer } == 1

private fun String.toDifficulty(): IslamicQuizDifficulty = when (this) {
    "person", "place", "food", "color", "number", "object", "time" ->
        IslamicQuizDifficulty.BEGINNER
    "action", "description" -> IslamicQuizDifficulty.INTERMEDIATE
    else -> IslamicQuizDifficulty.ADVANCED
}

private fun GroundedKnowledgeSource.sourceLocationAnswer(): String = when (val destination = target) {
    is NowNudgeKnowledgeTarget.QuranAyah -> "The Quran"
    is NowNudgeKnowledgeTarget.Hadith -> destination.collectionName
    is NowNudgeKnowledgeTarget.FortressDua -> "Fortress of the Muslim"
}

private fun GroundedKnowledgeSource.sourceUrl(): String = when (val destination = target) {
    is NowNudgeKnowledgeTarget.QuranAyah ->
        "https://quran.com/${destination.surahNumber}/${destination.ayahNumber}"
    is NowNudgeKnowledgeTarget.Hadith ->
        "hadith://${destination.databaseFile.removeSuffix(".db")}/${destination.hadithNumber}"
    is NowNudgeKnowledgeTarget.FortressDua -> "deenly://dua/${destination.dua.id}"
}

private fun GroundedKnowledgeSource.contentSource(): IslamicContentSource = when {
    target is NowNudgeKnowledgeTarget.QuranAyah -> IslamicContentSource.QURAN
    target is NowNudgeKnowledgeTarget.FortressDua -> IslamicContentSource.GENERAL
    collection == "sahih_al_bukhari" -> IslamicContentSource.SAHIH_AL_BUKHARI
    collection == "shamayel_at_tirmidhi" -> IslamicContentSource.SHAMAYEL_AT_TIRMIDHI
    else -> IslamicContentSource.GENERAL
}

private fun GroundedKnowledgeSource.fallbackTitle(): String {
    val groundedTopic = topic
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_FALLBACK_TOPIC_CHARS)
        .trim()
    return if (groundedTopic.isNotEmpty()) {
        "From $groundedTopic"
    } else {
        "Grounded knowledge"
    }
}

private const val MAX_FALLBACK_TOPIC_CHARS = 72

private fun GroundedKnowledgeSource.nudgeId(date: LocalDate): String =
    "model-knowledge-$date-$id"

private fun GroundedKnowledgeSource.expectedCompletion(): Float = when {
    sourceText.length <= 320 -> 1f
    sourceText.length <= 700 -> 0.82f
    sourceText.length <= 1_200 -> 0.65f
    sourceText.length <= 1_800 -> 0.48f
    else -> 0.32f
}

private fun GroundedKnowledgeSource.contextMatch(
    context: NowNudgeKnowledgeRankingContext,
): Float {
    val searchable = "$topic $sourceText".lowercase()
    val signalGroups = buildList {
        val hour = context.nowMinute / 60
        add(
            when (hour) {
                in 4..10 -> listOf("morning", "dawn", "fajr", "sunrise")
                in 11..15 -> listOf("noon", "midday", "dhuhr", "asr")
                in 16..20 -> listOf("evening", "sunset", "maghrib")
                else -> listOf("night", "isha", "sleep")
            },
        )
        (context.currentPrayer ?: context.nextPrayer)?.let { prayer ->
            add(prayer.contextKeywords())
            add(listOf("prayer", "pray", "salah", "mosque", "masjid", "worship"))
        }
        when (context.activity) {
            DeenlyActivity.DRIVING ->
                add(listOf("travel", "journey", "road", "ride", "mount"))
            DeenlyActivity.PRAYING ->
                add(listOf("prayer", "pray", "salah", "prostration", "worship"))
            DeenlyActivity.OTHER,
            DeenlyActivity.UNKNOWN,
            -> Unit
        }
        if (context.completedPrayerCount < 5) {
            add(listOf("prayer", "pray", "salah", "worship"))
        }
        if (context.isMediaPlaying) {
            add(listOf("listen", "recite", "recitation", "voice", "heard"))
        }
        if (context.hasVerifiedLocation) {
            add(listOf("mosque", "masjid", "qibla", "makkah", "journey"))
        }
    }
    if (signalGroups.isEmpty()) return 0f
    val matchingGroups = signalGroups.count { keywords ->
        keywords.any(searchable::contains)
    }
    return matchingGroups.toFloat() / signalGroups.size
}

private fun GroundedKnowledgeSource.queryMatch(query: String?): Float {
    val terms = query
        ?.lowercase()
        ?.split(Regex("[^a-z0-9]+"))
        ?.filter { it.length >= 3 && it !in QUERY_STOP_WORDS }
        ?.distinct()
        .orEmpty()
    if (terms.isEmpty()) return 0f

    val searchable = "$topic $reference $sourceText".lowercase()
    val matchingTerms = terms.count { term ->
        (term == "dua" && target is NowNudgeKnowledgeTarget.FortressDua) ||
            QUERY_SYNONYMS[term].orEmpty().plus(term).any(searchable::contains)
    }
    return matchingTerms.toFloat() / terms.size
}

private val QUERY_SYNONYMS = mapOf(
    "dua" to listOf("supplication", "invoke", "call upon", "prayer"),
    "fast" to listOf("fasting", "ramadan"),
    "fasting" to listOf("fast", "ramadan"),
    "mercy" to listOf("merciful", "forgive", "forgiveness"),
    "pray" to listOf("prayer", "salah", "worship"),
    "prayer" to listOf("pray", "salah", "worship"),
)

private val QUERY_STOP_WORDS = setOf(
    "about",
    "does",
    "from",
    "have",
    "please",
    "tell",
    "that",
    "the",
    "this",
    "what",
    "when",
    "where",
    "which",
    "who",
    "why",
    "with",
)

private fun String.contextKeywords(): List<String> = when (lowercase()) {
    "fajr" -> listOf("fajr", "dawn", "morning")
    "dhuhr", "zuhr" -> listOf("dhuhr", "zuhr", "noon", "midday")
    "asr" -> listOf("asr", "afternoon")
    "maghrib" -> listOf("maghrib", "sunset", "evening")
    "isha" -> listOf("isha", "night")
    else -> listOf(lowercase())
}

private class AndroidKnowledgeEngagementStore(context: Context) : KnowledgeEngagementStore {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    override fun snapshot(
        source: GroundedKnowledgeSource,
        date: LocalDate,
        nowEpochMillis: Long,
    ): KnowledgeEngagementSnapshot {
        prepareDay(date)
        val sourceKey = source.id.storageKey()
        val collectionKey = source.collection.storageKey()
        val lastShown = preferences.getLong("$LAST_SHOWN_PREFIX$sourceKey", 0L)
            .takeIf { it > 0L }
        val minutesSinceLastShown = lastShown?.let { timestamp ->
            ((nowEpochMillis - timestamp).coerceAtLeast(0L) / MILLIS_PER_MINUTE)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }
        return KnowledgeEngagementSnapshot(
            impressionsToday = preferences.getInt("$IMPRESSION_PREFIX$sourceKey", 0),
            minutesSinceLastShown = minutesSinceLastShown,
            collectionOpenedCount = preferences.getInt("$OPENED_PREFIX$collectionKey", 0),
            collectionDismissedCount = preferences.getInt("$DISMISSED_PREFIX$collectionKey", 0),
        )
    }

    @Synchronized
    override fun recordImpression(
        candidate: NowNudgeKnowledgeCandidate,
        date: LocalDate,
        nowEpochMillis: Long,
    ) {
        prepareDay(date)
        val sourceKey = candidate.sourceId.storageKey()
        val impressionKey = "$IMPRESSION_PREFIX$sourceKey"
        preferences.edit()
            .putInt(
                impressionKey,
                (preferences.getInt(impressionKey, 0) + 1).coerceAtMost(MAX_COUNTER),
            )
            .putLong("$LAST_SHOWN_PREFIX$sourceKey", nowEpochMillis)
            .apply()
    }

    @Synchronized
    override fun recordOpened(candidate: NowNudgeKnowledgeCandidate) {
        incrementCollectionCounter(OPENED_PREFIX, candidate.collection)
    }

    @Synchronized
    override fun recordDismissed(candidate: NowNudgeKnowledgeCandidate) {
        incrementCollectionCounter(DISMISSED_PREFIX, candidate.collection)
    }

    private fun incrementCollectionCounter(prefix: String, collection: String) {
        val key = "$prefix${collection.storageKey()}"
        preferences.edit()
            .putInt(key, (preferences.getInt(key, 0) + 1).coerceAtMost(MAX_COUNTER))
            .apply()
    }

    private fun prepareDay(date: LocalDate) {
        val day = date.toString()
        if (preferences.getString(DAY_KEY, null) == day) return
        val editor = preferences.edit().putString(DAY_KEY, day)
        preferences.all.keys
            .filter { it.startsWith(IMPRESSION_PREFIX) }
            .forEach(editor::remove)
        editor.apply()
    }

    private fun String.storageKey(): String = sha256().take(STORAGE_KEY_CHARS)

    private companion object {
        const val PREFERENCES_NAME = "now_nudge_knowledge_ranker"
        const val DAY_KEY = "impression_day"
        const val IMPRESSION_PREFIX = "impression."
        const val LAST_SHOWN_PREFIX = "last_shown."
        const val OPENED_PREFIX = "opened."
        const val DISMISSED_PREFIX = "dismissed."
        const val MILLIS_PER_MINUTE = 60_000L
        const val STORAGE_KEY_CHARS = 16
        const val MAX_COUNTER = 10_000
    }
}

private class AndroidGroundedKnowledgeSourceLoader(
    context: Context,
    private val assetRepository: AssetRepository,
    private val duaRepository: DuaRepository,
) : GroundedKnowledgeSourceLoader {
    private val appContext = context.applicationContext
    private val quranRepository by lazy {
        QuranTranslationRepository(appContext, "en", assetRepository)
    }
    private val hadithRepository by lazy {
        HadithRepository(appContext, assetRepository)
    }
    private val bukhariTranslations by lazy {
        BukhariLocalTranslationRepository.getInstance(appContext, assetRepository)
    }

    override suspend fun load(date: LocalDate, variation: Int): GroundedKnowledgeSource? {
        val selectionDate = date.plusDays(variation.coerceAtLeast(0).toLong())
        val sourceCount = HADITH_COLLECTIONS.size + 1
        val firstSource = Math.floorMod(selectionDate.toEpochDay(), sourceCount.toLong()).toInt()
        repeat(sourceCount) { offset ->
            val sourceIndex = (firstSource + offset) % sourceCount
            val source = runCatching {
                if (sourceIndex == 0) {
                    loadQuran(selectionDate)
                } else {
                    loadHadith(selectionDate, HADITH_COLLECTIONS[sourceIndex - 1])
                }
            }.getOrNull()
            if (source != null) return source
        }
        return null
    }

    override suspend fun search(query: String, limit: Int): List<GroundedKnowledgeSource> {
        val tokens = query
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in QUERY_STOP_WORDS && it != "dua" }
            .map { if (it == "fasting") "fast" else it }
            .distinct()
            .take(3)
        if (tokens.isEmpty()) return emptyList()

        val results = mutableListOf<GroundedKnowledgeSource>()

        // Fortress of the Muslim invocations.
        runCatching {
            duaRepository.searchDuasMultiToken(tokens, DUA_SEARCH_LIMIT).mapNotNull { dua ->
                val sourceText = dua.translation?.takeIf(String::isEnglishGroundedText)
                    ?: return@mapNotNull null
                GroundedKnowledgeSource(
                    id = "fortress-dua-${dua.id}",
                    collection = "fortress_of_the_muslim",
                    reference = "Fortress of the Muslim, ${dua.chapterTitle}",
                    topic = dua.chapterTitle,
                    sourceText = sourceText,
                    target = NowNudgeKnowledgeTarget.FortressDua(dua),
                )
            }
        }.getOrDefault(emptyList()).let(results::addAll)

        // Quran translation ayahs.
        runCatching {
            quranRepository.searchAyahsMultiToken(
                t0 = tokens[0],
                t1 = tokens.getOrElse(1) { "" },
                t2 = tokens.getOrElse(2) { "" },
                limit = QURAN_SEARCH_LIMIT,
            )
        }.getOrDefault(emptyList()).forEach { (surah, ayah) ->
            val sourceText = ayah.text.takeIf(String::isEnglishGroundedText) ?: return@forEach
            val topic = surah.nameTranslation?.takeIf(String::isNotBlank)
                ?: surah.nameEnglish?.takeIf(String::isNotBlank)
                ?: "Surah ${surah.number}"
            results += GroundedKnowledgeSource(
                id = "quran-${surah.number}-${ayah.numberInSurah}",
                collection = "quran",
                reference = "Quran ${surah.number}:${ayah.numberInSurah}",
                topic = topic,
                sourceText = sourceText,
                target = NowNudgeKnowledgeTarget.QuranAyah(
                    surahNumber = surah.number,
                    ayahNumber = ayah.numberInSurah,
                ),
            )
        }

        // Every downloaded Hadith collection, capped per collection so one large
        // collection cannot crowd out the rest before ranking runs.
        HADITH_COLLECTIONS.forEach { collection ->
            if (!HadithDatabase.isDatabaseAvailable(appContext, collection.databaseFile)) {
                return@forEach
            }
            runCatching {
                val hits = HadithDatabase
                    .getInstance(appContext, collection.databaseFile, assetRepository)
                    .hadithDao()
                    .searchHadithsMultiToken(
                        t0 = tokens[0],
                        t1 = tokens.getOrElse(1) { "" },
                        t2 = tokens.getOrElse(2) { "" },
                        limit = HADITH_SEARCH_LIMIT_PER_COLLECTION,
                    )
                val metadata = HadithDatabase.getCollectionMetadata(
                    appContext,
                    collection.databaseFile,
                )
                hits.mapNotNull { hit ->
                    mapHadithToGroundedSource(collection, hit.id, metadata)
                }
            }.getOrDefault(emptyList()).let(results::addAll)
        }

        return results.distinctBy(GroundedKnowledgeSource::id).take(limit)
    }

    private suspend fun loadQuran(date: LocalDate): GroundedKnowledgeSource? {
        val firstReference = Math.floorMod(
            date.toEpochDay() * 37L,
            QURAN_REFERENCES.size.toLong(),
        ).toInt()
        repeat(QURAN_REFERENCES.size) { offset ->
            val reference = QURAN_REFERENCES[(firstReference + offset) % QURAN_REFERENCES.size]
            val ayah = quranRepository.getAyahsBySurahNumber(reference.surahNumber)
                .firstOrNull { it.numberInSurah == reference.ayahNumber }
                ?: return@repeat
            val sourceText = ayah.text.takeIf(String::isGroundedText) ?: return@repeat
            val surah = quranRepository.getSurahByNumber(reference.surahNumber)
            val topic = surah?.nameTranslation
                ?.takeIf(String::isNotBlank)
                ?: surah?.nameEnglish?.takeIf(String::isNotBlank)
                ?: "Surah ${reference.surahNumber}"
            return GroundedKnowledgeSource(
                id = "quran-${reference.surahNumber}-${reference.ayahNumber}",
                collection = "quran",
                reference = "Quran ${reference.surahNumber}:${reference.ayahNumber}",
                topic = topic,
                sourceText = sourceText,
                target = NowNudgeKnowledgeTarget.QuranAyah(
                    surahNumber = reference.surahNumber,
                    ayahNumber = reference.ayahNumber,
                ),
            )
        }
        return null
    }

    private suspend fun loadHadith(
        date: LocalDate,
        collection: HadithCollectionSpec,
    ): GroundedKnowledgeSource? {
        if (!HadithDatabase.isDatabaseAvailable(appContext, collection.databaseFile)) return null
        val metadata = HadithDatabase.getCollectionMetadata(appContext, collection.databaseFile)
        val lastHadithId = collection.lastHadithId
            ?: metadata?.hadithCount?.takeIf { it > 0 }
            ?: return null
        val firstId = Math.floorMod(
            date.toEpochDay() * 53L + collection.databaseFile.hashCode(),
            lastHadithId.toLong(),
        ).toInt() + 1

        repeat(minOf(lastHadithId, MAX_HADITH_ATTEMPTS)) { attempt ->
            val hadithId = Math.floorMod(
                firstId - 1 + (attempt * HADITH_PROBE_STEP),
                lastHadithId,
            ) + 1
            mapHadithToGroundedSource(collection, hadithId, metadata)?.let { return it }
        }
        return null
    }

    /**
     * One immutable hadith record as a grounded source. Shared by the daily
     * rotation and the typed-question search so both flows present the exact
     * same text, topic, reference, and navigation target for a hadith ID.
     */
    private suspend fun mapHadithToGroundedSource(
        collection: HadithCollectionSpec,
        hadithId: Int,
        metadata: HadithCollectionMetadata?,
    ): GroundedKnowledgeSource? {
        val hadith = runCatching {
            hadithRepository.getHadith(collection.databaseFile, hadithId)
        }.getOrNull() ?: return null

        val bukhari = if (collection.databaseFile == BUKHARI_DATABASE) {
            bukhariTranslations.loadTranslations()
            bukhariTranslations.getTranslation(hadithId)
        } else {
            null
        }
        val sourceText = when (collection.databaseFile) {
            BUKHARI_DATABASE -> bukhari?.englishText
            SHAMAYEL_DATABASE -> hadith.englishText
            else -> hadith.englishText ?: hadith.textPlain
        }?.takeIf(String::isEnglishGroundedText) ?: return null

        val displayName = metadata?.nameEnglish
            ?.takeIf(String::isNotBlank)
            ?: collection.displayName
        val topic = when (collection.databaseFile) {
            BUKHARI_DATABASE -> bukhari?.bookName
                ?: BukhariBooks.findByHadithId(hadithId)?.nameEnglish
            SHAMAYEL_DATABASE -> ShamayelBooks.findByHadithId(hadithId)?.nameEnglish
            else -> displayName
        }?.takeIf(String::isNotBlank) ?: displayName
        val reference = if (bukhari != null) {
            "Sahih al-Bukhari, Volume ${bukhari.volumeNumber}, " +
                "Book ${bukhari.bookNumber}, Hadith ${bukhari.hadithNumber}"
        } else {
            "$displayName $hadithId"
        }
        return GroundedKnowledgeSource(
            id = "${collection.databaseFile.removeSuffix(".db")}-$hadithId",
            collection = collection.modelCollection,
            reference = reference,
            topic = topic,
            sourceText = sourceText,
            target = NowNudgeKnowledgeTarget.Hadith(
                collectionName = displayName,
                databaseFile = collection.databaseFile,
                hadithNumber = hadithId,
            ),
        )
    }

    private data class QuranReference(val surahNumber: Int, val ayahNumber: Int)

    private data class HadithCollectionSpec(
        val databaseFile: String,
        val displayName: String,
        val modelCollection: String,
        val lastHadithId: Int? = null,
    )

    private companion object {
        const val BUKHARI_DATABASE = "sahih_bukhari.db"
        const val SHAMAYEL_DATABASE = "shamayele_tirmidhi_complete.db"
        const val MAX_HADITH_ATTEMPTS = 64
        const val HADITH_PROBE_STEP = 37
        const val DUA_SEARCH_LIMIT = 8
        const val QURAN_SEARCH_LIMIT = 4
        const val HADITH_SEARCH_LIMIT_PER_COLLECTION = 2

        val QURAN_REFERENCES = listOf(
            QuranReference(1, 4),
            QuranReference(2, 115),
            QuranReference(2, 152),
            QuranReference(2, 183),
            QuranReference(2, 185),
            QuranReference(2, 186),
            QuranReference(2, 187),
            QuranReference(2, 286),
            QuranReference(3, 139),
            QuranReference(13, 28),
            QuranReference(16, 90),
            QuranReference(39, 53),
            QuranReference(49, 13),
            QuranReference(55, 13),
            QuranReference(94, 5),
            QuranReference(94, 6),
            QuranReference(103, 3),
        )

        val HADITH_COLLECTIONS = listOf(
            HadithCollectionSpec(BUKHARI_DATABASE, "Sahih al-Bukhari", "sahih_al_bukhari", 7_277),
            HadithCollectionSpec("sahih_muslim.db", "Sahih Muslim", "sahih_muslim"),
            HadithCollectionSpec("sunan_abu_dawud.db", "Sunan Abu Dawud", "sunan_abu_dawud"),
            HadithCollectionSpec("sunan_tirmidhi.db", "Jami' at-Tirmidhi", "jami'_at-tirmidhi"),
            HadithCollectionSpec("sunan_nasai.db", "Sunan an-Nasa'i", "sunan_an-nasa'i"),
            HadithCollectionSpec("sunan_ibn_majah.db", "Sunan Ibn Majah", "sunan_ibn_majah"),
            HadithCollectionSpec("muwatta_malik.db", "Muwatta Malik", "muwatta_malik"),
            HadithCollectionSpec("musnad_ahmad.db", "Musnad Ahmad", "musnad_ahmad"),
            HadithCollectionSpec("sunan_darimi.db", "Sunan ad-Darimi", "sunan_ad-darimi"),
            HadithCollectionSpec(SHAMAYEL_DATABASE, "Shama'il At-Tirmidhi", "shamayel_at_tirmidhi", 322),
        )
    }
}

private fun String.isGroundedText(): Boolean = isNotBlank() && length >= 12

/** Prevents Arabic-only legacy fields from being presented as an English bot suggestion. */
internal fun String.isEnglishGroundedText(): Boolean {
    if (!isGroundedText()) return false
    val latinLetters = count { it in 'A'..'Z' || it in 'a'..'z' }
    val arabicLetters = count { character ->
        character.code in 0x0600..0x06FF ||
            character.code in 0x0750..0x077F ||
            character.code in 0x08A0..0x08FF
    }
    return latinLetters >= 12 && latinLetters > arabicLetters
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray())
    .joinToString(separator = "") { "%02x".format(it) }
