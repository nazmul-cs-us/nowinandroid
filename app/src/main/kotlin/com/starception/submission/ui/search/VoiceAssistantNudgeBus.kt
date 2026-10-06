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

package com.starception.submission.ui.search

import android.util.Log
import com.starception.submission.core.model.deenly.DeenlyNudge
import com.starception.submission.core.model.deenly.DeenlyNudgeAction
import com.starception.submission.core.model.deenly.IslamicQuizQuestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Connects home-screen nudge ranking to the app-shell voice assistant control. */
object VoiceAssistantNudgeBus {
    // Suggestion generation belongs to the app-shell bot, not to whichever
    // destination happened to receive the pull gesture. A screen may provide
    // the context snapshot, but navigation must not cancel the in-flight turn.
    private val suggestionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _nudge = MutableStateFlow<DeenlyNudge?>(null)
    val nudge = _nudge.asStateFlow()

    private val _actionRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val actionRequests = _actionRequests.asSharedFlow()

    private val _quizOpen = MutableStateFlow(false)
    val quizOpen = _quizOpen.asStateFlow()

    private val _generatedQuizQuestion = MutableStateFlow<IslamicQuizQuestion?>(null)
    val generatedQuizQuestion = _generatedQuizQuestion.asStateFlow()

    private val _dismissRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val dismissRequests = _dismissRequests.asSharedFlow()

    private val _suggestionRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val suggestionRequests = _suggestionRequests.asSharedFlow()

    private val _suggestionReady = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val suggestionReady = _suggestionReady.asSharedFlow()

    private val _generatingSuggestion = MutableStateFlow(false)
    val generatingSuggestion = _generatingSuggestion.asStateFlow()

    private var suggestionRequestId = 0L
    private var suggestionRequestHandler: ((Long) -> Unit)? = null

    fun show(nudge: DeenlyNudge?) {
        _nudge.value = nudge
    }

    fun clear(id: String? = null) {
        if (id == null || _nudge.value?.id == id) {
            _nudge.value = null
        }
    }

    fun requestAction() {
        val currentNudge = _nudge.value ?: return
        if (currentNudge.action == DeenlyNudgeAction.PLAY_QUIZ) {
            requestQuiz()
        } else {
            _actionRequests.tryEmit(currentNudge.id)
        }
    }

    fun requestQuiz() {
        _nudge.value = null
        _quizOpen.value = true
    }

    fun closeQuiz() {
        _quizOpen.value = false
        _generatedQuizQuestion.value = null
    }

    fun setGeneratedQuizQuestion(question: IslamicQuizQuestion?) {
        _generatedQuizQuestion.value = question
    }

    fun requestDismiss() {
        val id = _nudge.value?.id ?: return
        _dismissRequests.tryEmit(id)
        // A destination may no longer be composed to acknowledge the request.
        // The app-shell close control must still always dismiss immediately.
        clear(id)
    }

    /** Starts one bot turn from the voice button's pull-down gesture. */
    fun requestSuggestion() {
        if (_generatingSuggestion.value) return
        _generatedQuizQuestion.value = null
        _generatingSuggestion.value = true
        suggestionRequestId += 1
        val requestId = suggestionRequestId
        val handler = suggestionRequestHandler
        when {
            handler != null -> {
                if (runCatching { handler(requestId) }.isFailure) {
                    failSuggestion(requestId)
                }
            }
            _suggestionRequests.subscriptionCount.value > 0 -> {
                if (!_suggestionRequests.tryEmit(requestId)) {
                    failSuggestion(requestId)
                }
            }
            else -> _generatingSuggestion.value = false
        }
    }

    /**
     * Installs the most recent context-aware generator supplied by Home.
     * It intentionally survives Home leaving composition so the app-shell bot
     * can generate from any top-level destination.
     */
    fun setSuggestionRequestHandler(handler: (Long) -> Unit) {
        suggestionRequestHandler = handler
    }

    /** Resets a turn if a registered handler cannot start it. */
    fun failSuggestion(requestId: Long) {
        if (requestId == suggestionRequestId) {
            _generatingSuggestion.value = false
        }
    }

    /**
     * Runs a request after the originating destination leaves composition.
     * The generator receives immutable context snapshots captured by that
     * destination before this app-level coroutine is launched.
     */
    fun processSuggestion(
        requestId: Long,
        generate: suspend () -> DeenlyNudge?,
    ) {
        suggestionScope.launch {
            val generation = runCatching {
                // Resolve Main lazily so local JVM tests can initialize the bus
                // without an Android Main dispatcher, while Compose state still
                // changes on the UI thread in the running app.
                withContext(Dispatchers.Main.immediate) { generate() }
            }
            generation.exceptionOrNull()?.let { error ->
                Log.e(TAG, "Now Nudge generation failed", error)
            }
            completeSuggestion(requestId, generation.getOrNull())
        }
    }

    /** Publishes the context-ranked result and tells the voice button to reveal it. */
    fun completeSuggestion(nudge: DeenlyNudge?) {
        completeSuggestion(suggestionRequestId, nudge)
    }

    private fun completeSuggestion(requestId: Long, nudge: DeenlyNudge?) {
        if (requestId != suggestionRequestId) return
        if (nudge != null) {
            _nudge.value = nudge
            _suggestionReady.tryEmit(nudge.id)
        }
        _generatingSuggestion.value = false
    }

    private const val TAG = "VoiceAssistantNudgeBus"
}
