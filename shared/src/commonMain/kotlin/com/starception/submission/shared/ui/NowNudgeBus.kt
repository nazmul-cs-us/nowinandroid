/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.shared.ui

import com.starception.submission.core.model.deenly.DeenlyActionIds
import com.starception.submission.core.model.deenly.DeenlyNudge
import com.starception.submission.core.model.deenly.DeenlyNudgeAction
import com.starception.submission.shared.content.createSharedFortressRepository
import com.starception.submission.shared.content.FortressInvocation
import com.starception.submission.shared.hadith.searchEnglishHadithCollections
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Turn-based coordinator for the app-shell Now Nudge bot. Mirrors Android's
 * `VoiceAssistantNudgeBus`: suggestion generation belongs to the bot, not to
 * whichever screen supplied the context snapshot, so a turn started from a
 * pull-down or typed question keeps running even if the underlying screen
 * recomposes.
 *
 * The screen publishes its ranked ambient nudge via [updateAmbient]; a turn
 * started with [requestSuggestion] completes with that ambient nudge (pull
 * gesture) or with a retrieval answer built from the Fortress duas plus the
 * downloaded English hadith collections (typed question). The result is
 * published as [presentedNudge] plus a [suggestionReady] event carrying the
 * nudge id, which drives the bot's sparkle-then-reveal pacing on the platform
 * shell.
 */
object NowNudgeBus {
    private const val MIN_TURN_MILLIS = 2_800L
    private const val COMPOSER_SUGGESTION_COUNT = 8
    private const val TYPED_ANSWER_ID_PREFIX = "typed-fortress-dua-"
    private const val TYPED_HADITH_ID_PREFIX = "typed-hadith-"
    private const val FORTRESS_SEARCH_LIMIT_PER_TERM = 24
    private const val HADITH_SEARCH_LIMIT_PER_COLLECTION = 6
    private const val ANSWER_QUOTE_CHARS = 220

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var turnJob: Job? = null
    private var suggestionRequestId = 0L
    private val fortressRepository by lazy { createSharedFortressRepository() }

    private val _ambientNudge = MutableStateFlow<DeenlyNudge?>(null)
    private val _presentedNudge = MutableStateFlow<DeenlyNudge?>(null)
    private val _generating = MutableStateFlow(false)
    private val _suggestionReady = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val _actionRequests = MutableSharedFlow<DeenlyNudge>(extraBufferCapacity = 1)
    private val _dismissRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val _quizOpenRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The nudge the bot is currently presenting (turn result). */
    val presentedNudge: StateFlow<DeenlyNudge?> = _presentedNudge.asStateFlow()

    /** True while a suggestion turn is running. */
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    /** Emits the presented nudge id once a turn completes. */
    val suggestionReady: SharedFlow<String> = _suggestionReady.asSharedFlow()

    /** Carries the nudge whose action the screen should perform. */
    val actionRequests: SharedFlow<DeenlyNudge> = _actionRequests.asSharedFlow()

    /** Carries the id of a presented nudge the user explicitly dismissed. */
    val dismissRequests: SharedFlow<String> = _dismissRequests.asSharedFlow()

    /** Asks the hosting screen to open the shared Islamic quiz dialog. */
    val quizOpenRequests: SharedFlow<Unit> = _quizOpenRequests.asSharedFlow()

    /** Called by the hosting screen whenever its ranked (ambient) nudge changes. */
    fun updateAmbient(nudge: DeenlyNudge?) {
        _ambientNudge.value = nudge
    }

    /**
     * Starts one bot turn. A null prompt (the pull-down gesture or long press)
     * completes with the ambient ranked nudge; a typed prompt completes with a
     * retrieval answer from the Fortress duas and the English hadith
     * collections, or with nothing when no passage matches. Quiz commands open
     * the quiz immediately, matching Android's bus.
     */
    fun requestSuggestion(prompt: String? = null) {
        if (_generating.value) return
        val normalizedPrompt = prompt?.trim()?.takeIf(String::isNotEmpty)
        if (normalizedPrompt != null && normalizeCommand(normalizedPrompt) in QUIZ_COMMANDS) {
            requestQuiz()
            return
        }
        val requestId = ++suggestionRequestId
        _generating.value = true
        _presentedNudge.value = null
        turnJob?.cancel()
        turnJob = scope.launch {
            val startedAt = TimeSource.Monotonic.markNow()
            val result = runCatching {
                if (normalizedPrompt == null) {
                    _ambientNudge.value
                } else {
                    generateAnswer(normalizedPrompt)
                }
            }.getOrNull()
            // Keep the thinking pill on screen for at least two informative
            // message beats so instant results do not flash.
            val elapsedMillis = startedAt.elapsedNow().inWholeMilliseconds
            val remaining = MIN_TURN_MILLIS - elapsedMillis
            if (remaining > 0) delay(remaining)
            if (requestId != suggestionRequestId) return@launch
            _generating.value = false
            if (result != null) {
                _presentedNudge.value = result
                _suggestionReady.tryEmit(result.id)
            }
        }
    }

    /**
     * Cancels the in-flight generation and returns the bot to its resting
     * button. Swiping the thinking card calls this so the user is never stuck
     * waiting on a turn they no longer want. The request id is bumped, so any
     * result the cancelled job still produces is discarded as stale.
     */
    fun cancelSuggestion() {
        suggestionRequestId += 1
        turnJob?.cancel()
        turnJob = null
        _generating.value = false
        _presentedNudge.value = null
    }

    /** Performs the presented nudge's action, falling back to the ambient one. */
    fun requestAction() {
        val nudge = _presentedNudge.value ?: _ambientNudge.value ?: return
        _presentedNudge.value = null
        _actionRequests.tryEmit(nudge)
    }

    /** Dismisses the presented nudge without performing its action. */
    fun requestDismiss() {
        val id = _presentedNudge.value?.id ?: return
        _presentedNudge.value = null
        _dismissRequests.tryEmit(id)
    }

    /** Opens the shared quiz dialog, matching Android's immediate quiz path. */
    fun requestQuiz() {
        suggestionRequestId += 1
        turnJob?.cancel()
        turnJob = null
        _generating.value = false
        _presentedNudge.value = null
        _quizOpenRequests.tryEmit(Unit)
    }

    /**
     * Supplies the composer's suggestion chips: Fortress of the Muslim chapter
     * titles, shuffled, the same source Android uses.
     */
    fun requestComposerSuggestions(onReady: (List<String>) -> Unit) {
        scope.launch {
            val titles = runCatching {
                fortressRepository.getChapters()
                    .filter { it.title.isNotBlank() }
                    .map { it.title }
                    .shuffled()
                    .take(COMPOSER_SUGGESTION_COUNT)
            }.getOrDefault(emptyList())
            onReady(titles)
        }
    }

    private suspend fun generateAnswer(prompt: String): DeenlyNudge? {
        val terms = answerQueryTerms(prompt)
        if (terms.isEmpty()) return null
        val candidates = fortressCandidates(terms) + hadithCandidates(terms)
        val best = selectAnswerCandidate(candidates, prompt) ?: return null
        return best.toNudge()
    }

    private suspend fun fortressCandidates(terms: List<String>): List<AnswerCandidate> {
        val byId = linkedMapOf<Int, FortressInvocation>()
        for (term in terms) {
            runCatching {
                fortressRepository.searchFortressInvocations(term, FORTRESS_SEARCH_LIMIT_PER_TERM)
            }.getOrDefault(emptyList()).forEach { invocation ->
                if (byId[invocation.id] == null) byId[invocation.id] = invocation
            }
        }
        val chapterTitles = runCatching { fortressRepository.getChapters() }
            .getOrDefault(emptyList())
            .associate { it.id to it.title }
        return byId.values.map { invocation ->
            val chapterTitle = chapterTitles[invocation.chapterId].orEmpty()
                .ifBlank { "Fortress of the Muslim" }
            AnswerCandidate(
                candidateId = "fortress-${invocation.chapterId}-${invocation.id}",
                searchable = "$chapterTitle ${invocation.description} ${invocation.translation}",
                quoteText = invocation.translation,
                label = invocation.description.trim().takeIf(String::isNotBlank) ?: chapterTitle,
                sourceLabel = chapterTitle,
                kind = AnswerCandidate.Kind.FortressDua(
                    chapterId = invocation.chapterId,
                    invocationId = invocation.id,
                ),
            )
        }
    }

    private suspend fun hadithCandidates(terms: List<String>): List<AnswerCandidate> {
        val hits = runCatching {
            searchEnglishHadithCollections(terms, HADITH_SEARCH_LIMIT_PER_COLLECTION)
        }.getOrDefault(emptyList())
        return hits.map { hit ->
            val reference = "${hit.collectionName} ${hit.hadithId}"
            AnswerCandidate(
                candidateId = "${hit.databaseFile}-${hit.hadithId}",
                searchable = "${hit.collectionName} $reference ${hit.text}",
                quoteText = bestAnswerSentence(hit.text, terms),
                label = "About ${hit.collectionName}",
                sourceLabel = reference,
                kind = when (hit.databaseFile) {
                    "sahih_bukhari" -> AnswerCandidate.Kind.BukhariHadith(hit.hadithId)
                    else -> AnswerCandidate.Kind.CollectionHadith(
                        databaseFile = hit.databaseFile,
                        hadithId = hit.hadithId,
                    )
                },
            )
        }
    }

    private fun AnswerCandidate.toNudge(): DeenlyNudge = when (val k = kind) {
        is AnswerCandidate.Kind.FortressDua -> DeenlyNudge(
            id = "$TYPED_ANSWER_ID_PREFIX${k.chapterId}-${k.invocationId}",
            action = DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
            actionId = DeenlyActionIds.DUA_OPEN_FORTRESS,
            label = label,
            supportingText = "\u201C${quoteText.trim().take(ANSWER_QUOTE_CHARS)}\u201D",
            sourceLabel = sourceLabel,
        )

        is AnswerCandidate.Kind.BukhariHadith -> DeenlyNudge(
            id = "$TYPED_HADITH_ID_PREFIX${"sahih_bukhari"}-${k.hadithId}",
            action = DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
            actionId = DeenlyActionIds.LEARNING_OPEN_KNOWLEDGE,
            label = label,
            supportingText = "\u201C${quoteText.trim().take(ANSWER_QUOTE_CHARS)}\u201D",
            sourceLabel = sourceLabel,
        )

        is AnswerCandidate.Kind.CollectionHadith -> DeenlyNudge(
            id = "$TYPED_HADITH_ID_PREFIX${k.databaseFile}-${k.hadithId}",
            action = DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
            actionId = DeenlyActionIds.LEARNING_OPEN_KNOWLEDGE,
            label = label,
            supportingText = "\u201C${quoteText.trim().take(ANSWER_QUOTE_CHARS)}\u201D",
            sourceLabel = sourceLabel,
        )
    }

    private fun normalizeCommand(prompt: String): String = prompt
        .lowercase()
        .replace(Regex("[^a-z ]"), "")
        .replace(Regex("\\s+"), " ")

    private val QUIZ_COMMANDS = setOf(
        "begin quiz",
        "open quiz",
        "start a quiz",
        "start quiz",
    )
}

/** One retrievable passage the typed question may be answered from. */
internal data class AnswerCandidate(
    val candidateId: String,
    val searchable: String,
    val quoteText: String,
    val label: String,
    val sourceLabel: String,
    val kind: Kind,
) {
    internal sealed interface Kind {
        data class FortressDua(val chapterId: Int, val invocationId: Int) : Kind

        data class BukhariHadith(val hadithId: Int) : Kind

        data class CollectionHadith(val databaseFile: String, val hadithId: Int) : Kind
    }
}

/**
 * Picks the passage that best answers the typed question. Prophet-attribute
 * questions ("the Prophet's favourite food") prefer passages that state a
 * preference directly — "Rasulullah loved halwa and honey", from ANY
 * collection — over passages that merely contain the question's words; every
 * other question ranks by whole-word overlap, then by brevity so the card
 * quotes cleanly.
 */
internal fun selectAnswerCandidate(
    candidates: List<AnswerCandidate>,
    query: String,
): AnswerCandidate? {
    val terms = answerQueryTerms(query)
    if (terms.isEmpty() || candidates.isEmpty()) return null
    val preferenceQuestion = isProphetAttributeQuestion(query)
    val scored = candidates
        .map { candidate -> candidate to answerOverlapScore(candidate.searchable, terms) }
        .filter { (_, score) -> score > 0f }
    if (scored.isEmpty()) return null
    return scored.sortedWith(
        compareByDescending<Pair<AnswerCandidate, Float>> { (candidate, score) ->
            if (
                preferenceQuestion &&
                containsDirectPreferenceStatement(candidate.searchable) &&
                score >= DIRECT_PREFERENCE_PROMOTION_MIN_OVERLAP
            ) {
                1
            } else {
                0
            }
        }.thenByDescending { (_, score) -> score }
            .thenBy { (candidate, _) -> candidate.quoteText.length },
    ).first().first
}

/** Tokenizes a typed question into searchable terms, mirroring the app module. */
internal fun answerQueryTerms(prompt: String): List<String> = prompt
    .lowercase()
    .split(Regex("[^a-z0-9]+"))
    .filter { it.length >= 3 && it !in ANSWER_STOP_WORDS && it != "dua" }
    .map(::stemAnswerToken)
    .distinct()
    .take(3)

private fun stemAnswerToken(token: String): String = when {
    token == "fasting" -> "fast"
    token.length > 3 && token.endsWith("s") && !token.endsWith("ss") -> token.dropLast(1)
    else -> token
}

/**
 * Whole-word overlap between the question's terms and a passage. Substring
 * hits are false overlap — "disliked" is not "liked" and the "heat" of
 * Hellfire is not "eat" — so a passage that only substring-matches the
 * question never outranks a real preference narration.
 */
internal fun answerOverlapScore(searchable: String, terms: List<String>): Float {
    if (terms.isEmpty()) return 0f
    val lower = searchable.lowercase()
    var matchedWeight = 0f
    var totalWeight = 0f
    for (term in terms) {
        val weight = if (term in ANSWER_SYNONYMS) 1.5f else 1f
        totalWeight += weight
        val matched = matchesWholeWord(lower, term) ||
            ANSWER_SYNONYMS[term].orEmpty().any { matchesWholeWord(lower, it) }
        if (matched) matchedWeight += weight
    }
    return if (totalWeight <= 0f) 0f else matchedWeight / totalWeight
}

/**
 * True when the passage states a preference directly — "Rasulullah loved
 * halwa and honey" — rather than describing behavior around the topic
 * ("never criticized any food ... if he liked it"). Whole-word verb matching
 * keeps "disliked" out of "liked" hits while "disliked X" still counts as its
 * own (negative) preference statement.
 */
internal fun containsDirectPreferenceStatement(text: String): Boolean {
    val lower = text.lowercase()
    for (verb in DIRECT_PREFERENCE_VERBS) {
        for (match in wholeWordMatches(lower, verb)) {
            val index = match.first
            val prefixStart = (index - PREFERENCE_CONDITIONAL_WINDOW_CHARS).coerceAtLeast(0)
            val prefix = lower.substring(prefixStart, index)
            val after = lower.substring(index + verb.length)
            if (!after.startsWith(" by ") && PREFERENCE_CONDITIONAL_MARKERS.none(prefix::contains)) {
                return true
            }
        }
    }
    return false
}

/**
 * True when the question asks about the Prophet's person — his preferences,
 * food, appearance, dress, or habits. Only these attribute-intent questions
 * prefer the direct-preference passages; questions about what the Prophet
 * said or taught rank by plain overlap.
 */
internal fun isProphetAttributeQuestion(prompt: String): Boolean {
    val terms = prompt
        .lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 3 }
    return terms.any { it in PROPHET_QUESTION_TERMS } &&
        terms.any { it in PROPHET_ATTRIBUTE_INTENT_TERMS }
}

/** The sentence of [text] that overlaps the most question terms, for the card quote. */
internal fun bestAnswerSentence(text: String, terms: List<String>): String {
    val sentences = Regex("[^.!?]+[.!?]+(?=\\s|[\"'\\u201d]|$)|[^.!?]+\$")
        .findAll(text)
        .map { it.value.trim() }
        .filter { it.isNotEmpty() }
    val best = sentences.maxByOrNull { sentence ->
        val lower = sentence.lowercase()
        terms.count { term ->
            matchesWholeWord(lower, term) ||
                ANSWER_SYNONYMS[term].orEmpty().any { matchesWholeWord(lower, it) }
        }
    } ?: return text.take(ANSWER_QUOTE_SENTENCE_CHARS)
    return best.take(ANSWER_QUOTE_SENTENCE_CHARS)
}

private const val ANSWER_QUOTE_SENTENCE_CHARS = 200
internal const val DIRECT_PREFERENCE_PROMOTION_MIN_OVERLAP = 0.5f
private const val PREFERENCE_CONDITIONAL_WINDOW_CHARS = 28

private fun matchesWholeWord(lowerText: String, term: String): Boolean =
    wholeWordMatches(lowerText, term).any()

private fun wholeWordMatches(lowerText: String, term: String): List<Pair<Int, Int>> {
    if (term.isEmpty()) return emptyList()
    val regex = WHOLE_WORD_REGEXES.getOrPut(term) {
        Regex("\\b${Regex.escape(term)}\\b")
    }
    return regex.findAll(lowerText).map { it.range.first to it.range.last + 1 }.toList()
}

private val WHOLE_WORD_REGEXES = mutableMapOf<String, Regex>()

private val ANSWER_SYNONYMS = mapOf(
    "dua" to listOf("supplication", "invoke", "call upon", "prayer"),
    "fast" to listOf("fasting", "ramadan"),
    "fasting" to listOf("fast", "ramadan"),
    "mercy" to listOf("merciful", "forgive", "forgiveness"),
    "pray" to listOf("prayer", "salah", "worship"),
    "prayer" to listOf("pray", "salah", "worship"),
    "favorite" to listOf("loved", "preferred", "liked", "best"),
    "favourite" to listOf("loved", "preferred", "liked", "best"),
    // The narrations name him "Allah's Messenger" and "Rasulullah"; a typed
    // "prophet" must match those phrasings.
    "prophet" to listOf("messenger", "apostle", "rasulullah"),
    "messenger" to listOf("prophet", "apostle", "rasulullah"),
    "apostle" to listOf("prophet", "messenger", "rasulullah"),
    "muhammad" to listOf("prophet", "messenger", "rasulullah"),
    "food" to listOf("eat", "drink", "meal"),
    "drink" to listOf("food", "eat"),
)

private val ANSWER_STOP_WORDS = setOf(
    "about",
    "does",
    "did",
    "from",
    "have",
    "had",
    "has",
    "please",
    "tell",
    "that",
    "the",
    "this",
    "was",
    "were",
    "what",
    "when",
    "where",
    "which",
    "who",
    "why",
    "with",
)

private val PROPHET_QUESTION_TERMS = setOf(
    "prophet",
    "messenger",
    "apostle",
    "muhammad",
    "rasulullah",
)

private val PROPHET_ATTRIBUTE_INTENT_TERMS = setOf(
    "how",
    "favourite", "favourites", "favorite", "favorites", "prefer", "prefers",
    "preferred", "preference", "love", "loves", "loved", "like", "likes", "liked",
    "food", "foods", "eat", "eats", "eating", "ate", "meal", "meals",
    "drink", "drinks", "drinking", "drank",
    "wear", "wears", "wore", "wearing", "dress", "dressed", "clothes", "clothing",
    "appearance", "look", "looks", "looked", "height", "hair", "beard", "face",
    "walk", "walked", "walking", "sleep", "sleeps", "slept", "habit", "habits",
    "routine", "daily", "pray", "prays", "prayed", "prayer", "prayers",
)

private val DIRECT_PREFERENCE_VERBS = listOf(
    "loved",
    "loves",
    "liked",
    "likes",
    "disliked",
    "dislikes",
    "preferred",
    "prefers",
    "favourite",
    "favorite",
)

private val PREFERENCE_CONDITIONAL_MARKERS = listOf(
    "never",
    " not",
    "if ",
    "unless",
    "without",
    "whether",
    "even",
)

/**
 * Chapter routing for typed Fortress answers: the answer nudge encodes the
 * destination chapter in its id so the screen can open the exact chapter
 * instead of the generic recommendation destination.
 */
internal fun DeenlyNudge.typedFortressChapterId(): Int? {
    if (actionId != DeenlyActionIds.DUA_OPEN_FORTRESS) return null
    if (!id.startsWith("typed-fortress-dua-")) return null
    return id.removePrefix("typed-fortress-dua-")
        .substringBefore('-')
        .toIntOrNull()
}

/**
 * Hadith routing for typed answers: a Bukhari answer opens the exact hadith
 * detail screen; answers from other collections fall back to the generic
 * recommendation destination.
 */
internal fun DeenlyNudge.typedBukhariHadithId(): Int? {
    val prefix = "typed-hadith-sahih_bukhari-"
    if (!id.startsWith(prefix)) return null
    return id.removePrefix(prefix).toIntOrNull()
}
