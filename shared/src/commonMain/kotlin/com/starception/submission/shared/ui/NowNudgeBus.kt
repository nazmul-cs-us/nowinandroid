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
 * gesture) or with a retrieval answer built from the Fortress of the Muslim
 * database (typed question). The result is published as [presentedNudge] plus a
 * [suggestionReady] event carrying the nudge id, which drives the bot's
 * sparkle-then-reveal pacing on the platform shell.
 */
object NowNudgeBus {
    private const val MIN_TURN_MILLIS = 2_800L
    private const val COMPOSER_SUGGESTION_COUNT = 8
    private const val TYPED_ANSWER_ID_PREFIX = "typed-fortress-dua-"

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
     * retrieval answer from the Fortress database, or with nothing when no
     * invocation matches. Quiz commands open the quiz immediately, matching
     * Android's bus.
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
                    generateFortressAnswer(normalizedPrompt)
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

    private suspend fun generateFortressAnswer(prompt: String): DeenlyNudge? {
        val matches = fortressRepository.searchFortressInvocations(query = prompt, limit = 1)
        val best = matches.firstOrNull() ?: return null
        val chapterTitle = fortressRepository.getChapters()
            .firstOrNull { it.id == best.chapterId }
            ?.title
            .orEmpty()
            .ifBlank { "Fortress of the Muslim" }
        val title = best.description.trim().takeIf(String::isNotBlank) ?: chapterTitle
        return DeenlyNudge(
            id = "$TYPED_ANSWER_ID_PREFIX${best.chapterId}-${best.id}",
            action = DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
            actionId = DeenlyActionIds.DUA_OPEN_FORTRESS,
            label = title,
            supportingText = "\u201C${best.translation.trim()}\u201D",
            sourceLabel = chapterTitle,
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
