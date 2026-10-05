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

import com.starception.submission.core.model.deenly.DeenlyNudge
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Connects home-screen nudge ranking to the app-shell voice assistant control. */
object VoiceAssistantNudgeBus {
    private val _nudge = MutableStateFlow<DeenlyNudge?>(null)
    val nudge = _nudge.asStateFlow()

    private val _actionRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val actionRequests = _actionRequests.asSharedFlow()

    private val _quizOpen = MutableStateFlow(false)
    val quizOpen = _quizOpen.asStateFlow()

    private val _dismissRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val dismissRequests = _dismissRequests.asSharedFlow()

    private val _suggestionRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val suggestionRequests = _suggestionRequests.asSharedFlow()

    private val _suggestionReady = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val suggestionReady = _suggestionReady.asSharedFlow()

    private val _generatingSuggestion = MutableStateFlow(false)
    val generatingSuggestion = _generatingSuggestion.asStateFlow()

    private var suggestionRequestId = 0L

    fun show(nudge: DeenlyNudge?) {
        _nudge.value = nudge
    }

    fun clear(id: String? = null) {
        if (id == null || _nudge.value?.id == id) {
            _nudge.value = null
        }
    }

    fun requestAction() {
        _nudge.value?.id?.let(_actionRequests::tryEmit)
    }

    fun requestQuiz() {
        _nudge.value = null
        _quizOpen.value = true
    }

    fun closeQuiz() {
        _quizOpen.value = false
    }

    fun requestDismiss() {
        _nudge.value?.id?.let(_dismissRequests::tryEmit)
    }

    /** Starts one bot turn from the voice button's pull-down gesture. */
    fun requestSuggestion() {
        if (_generatingSuggestion.value) return
        _generatingSuggestion.value = true
        suggestionRequestId += 1
        if (!_suggestionRequests.tryEmit(suggestionRequestId)) {
            _generatingSuggestion.value = false
        }
    }

    /** Publishes the context-ranked result and tells the voice button to reveal it. */
    fun completeSuggestion(nudge: DeenlyNudge?) {
        if (nudge != null) {
            _nudge.value = nudge
            _suggestionReady.tryEmit(nudge.id)
        }
        _generatingSuggestion.value = false
    }
}
