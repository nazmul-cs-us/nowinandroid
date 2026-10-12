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

package com.starception.submission.shared.voice

import com.starception.submission.shared.hadith.createSharedHadithRepository
import com.starception.submission.shared.translation.SharedTranslationService
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The detached hadith narration queue — the shared counterpart of Android's
 * MediaSession-backed playlist. The queue owns the narration lifecycle: it
 * keeps speaking when the user leaves the hadith screen, auto-advances to the
 * next hadith as each narration finishes, and surfaces its state so screens
 * (lock screen, Control Center, the hadith pager) can follow along.
 */
object NarrationQueue {
    /** Which corpus the queue narrates; each fetches through its repository. */
    enum class Collection { BUKHARI, SHAMAYEL }

    data class NarrationState(
        val collection: Collection = Collection.BUKHARI,
        val bookId: Int? = null,
        val hadithId: Int? = null,
        val playing: Boolean = false,
        val paused: Boolean = false,
        /** Index of the current hadith within the queue, for the "n of total" chip. */
        val index: Int = 0,
        val queueSize: Int = 0,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val synthesizer = PlatformSpeechSynthesizer()
    private val repository by lazy { createSharedHadithRepository() }

    /** One queue pass. Each speak call carries the token it was started with;
     * stale completions from a stopped or skipped utterance never advance. */
    private var generation = 0L
    private var queueJob: Job? = null
    private var queueIds: List<Int> = emptyList()
    private var isQueue = false
    private var translationLanguage = "en"
    private var translationProvider = SharedTranslationService.PROVIDER_AUTO
    private var pendingAdvance = false

    private val _state = MutableStateFlow(NarrationState())

    /** The queue's playback state; screens and the lock screen observe this. */
    val state: StateFlow<NarrationState> = _state.asStateFlow()

    /** Narrates [hadithIds] from [startIndex] to the end, one hadith at a time. */
    fun startQueue(
        collection: Collection,
        bookId: Int,
        hadithIds: List<Int>,
        startIndex: Int,
        language: String,
        provider: String,
    ) {
        if (hadithIds.isEmpty()) return
        val clamped = startIndex.coerceIn(0, hadithIds.size - 1)
        begin(
            collection = collection,
            bookId = bookId,
            ids = hadithIds,
            index = clamped,
            queue = true,
            language = language,
            provider = provider,
        )
    }

    /** Narrates one hadith; the queue stays idle afterwards. */
    fun narrateSingle(
        collection: Collection,
        bookId: Int,
        hadithId: Int,
        language: String,
        provider: String,
    ) {
        begin(
            collection = collection,
            bookId = bookId,
            ids = listOf(hadithId),
            index = 0,
            queue = false,
            language = language,
            provider = provider,
        )
    }

    /** Pauses when speaking and resumes when paused — the lock-screen toggle. */
    fun togglePause() {
        val current = _state.value
        if (current.paused) {
            synthesizer.resume()
            _state.value = current.copy(paused = false, playing = true)
        } else if (current.playing) {
            synthesizer.pause()
            _state.value = current.copy(paused = true, playing = false)
        }
    }

    /** Jumps to the next hadith in the queue (lock-screen next-track). */
    fun skipNext() {
        val current = _state.value
        if (!current.playing && !current.paused) return
        if (!isQueue) return
        val nextIndex = current.index + 1
        if (nextIndex >= queueIds.size) {
            stop()
            return
        }
        restartAt(nextIndex)
    }

    /** Jumps back one hadith (lock-screen previous-track). */
    fun skipPrevious() {
        val current = _state.value
        if (!current.playing && !current.paused) return
        if (!isQueue) return
        val previous = (current.index - 1).coerceAtLeast(0)
        if (previous == current.index) return
        restartAt(previous)
    }

    /** Stops the narration and clears the state — screens show an idle player. */
    fun stop() {
        generation += 1
        pendingAdvance = false
        queueJob?.cancel()
        queueJob = null
        synthesizer.stop()
        queueIds = emptyList()
        isQueue = false
        _state.value = NarrationState()
    }

    private fun begin(
        collection: Collection,
        bookId: Int,
        ids: List<Int>,
        index: Int,
        queue: Boolean,
        language: String,
        provider: String,
    ) {
        generation += 1
        pendingAdvance = false
        queueJob?.cancel()
        synthesizer.stop()
        queueIds = ids
        isQueue = queue
        translationLanguage = language
        translationProvider = provider
        _state.value = NarrationState(
            collection = collection,
            bookId = bookId,
            hadithId = ids.getOrNull(index),
            playing = true,
            index = index,
            queueSize = ids.size,
        )
        narrateAt(index)
    }

    private fun restartAt(index: Int) {
        val token = generation
        generation += 1
        pendingAdvance = false
        synthesizer.stop()
        _state.value = _state.value.copy(
            hadithId = queueIds.getOrNull(index),
            playing = true,
            paused = false,
            index = index,
        )
        narrateAt(index)
    }

    private fun narrateAt(index: Int) {
        val token = generation
        val hadithId = queueIds.getOrNull(index) ?: run {
            stop()
            return
        }
        val bookId = _state.value.bookId ?: return
        _state.value = _state.value.copy(
            hadithId = hadithId,
            playing = true,
            paused = false,
            index = index,
        )
        queueJob = scope.launch {
            val hadith = try {
                when (_state.value.collection) {
                    Collection.SHAMAYEL -> repository.getShamayelHadith(hadithId)
                    Collection.BUKHARI -> repository.getHadith(hadithId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (hadith == null || hadith.english.isBlank()) {
                advanceOrFinish(token, index)
                return@launch
            }
            val text = if (translationLanguage == "en") {
                hadith.english
            } else {
                try {
                    SharedTranslationService.translateFromEnglish(
                        text = hadith.english,
                        targetLang = translationLanguage,
                        provider = translationProvider,
                    ).takeIf { it.isNotBlank() } ?: hadith.english
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    hadith.english
                }
            }
            if (token != generation) return@launch
            val started = synthesizer.speak(
                text = text,
                language = narrationLanguage(translationLanguage),
            ) { error ->
                if (token != generation) return@speak
                if (error == null) {
                    advanceOrFinish(token, index)
                } else {
                    stop()
                }
            }
            if (!started && token == generation) {
                stop()
            }
        }
    }

    private fun advanceOrFinish(token: Long, finishedIndex: Int) {
        if (token != generation) return
        if (!isQueue || finishedIndex + 1 >= queueIds.size) {
            stop()
            return
        }
        narrateAt(finishedIndex + 1)
    }

    private fun narrationLanguage(language: String): String = when (language) {
        "ar" -> "ar-SA"
        "bn" -> "bn-BD"
        "zh" -> "zh-CN"
        "en" -> "en-US"
        "es" -> "es-ES"
        "fr" -> "fr-FR"
        "id" -> "id-ID"
        "ru" -> "ru-RU"
        "sv" -> "sv-SE"
        "tr" -> "tr-TR"
        else -> "en-US"
    }
}
