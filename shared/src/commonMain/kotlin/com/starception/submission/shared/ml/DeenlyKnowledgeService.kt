/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.shared.ml

import com.starception.submission.core.model.deenly.PrayerHadithCandidate
import com.starception.submission.core.model.deenly.PrayerHadithSelection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.resume

/**
 * The platform inference engine for the Deenly GGUFs. iOS implements it with
 * the in-process llama.cpp runner (Android's app module keeps its own
 * process-based runner); prompts, schemas, and output validation stay here so
 * both platforms apply identical rules.
 */
interface DeenlyKnowledgeService {

    /** The task's local GGUF path, or null when it was never downloaded. */
    fun modelPathForTask(task: String): String?

    /**
     * One single-turn, grammar-guided completion. The raw model output is
     * delivered on the main thread; null means the model failed to load or
     * the generation timed out.
     */
    fun run(
        modelPath: String,
        task: String,
        systemPrompt: String,
        userPrompt: String,
        onComplete: (String?) -> Unit,
    )
}

/** Task wire names, mirroring Android's DeenlyKnowledgeTask. */
object DeenlyKnowledgeTasks {
    const val KNOWLEDGE = "knowledge"
    const val QUESTION = "question"
    const val ANSWER = "answer"
}

/**
 * Shared entry point for the on-device suggestion generation. The platform
 * shell installs [service] at startup and (iOS) a [downloadModels] hook that
 * fetches the GGUF categories through the shared CDN downloader.
 */
object DeenlyKnowledgeRuntime {

    var service: DeenlyKnowledgeService? = null

    /** Downloads the suggestion models; reports byte progress; true on success. */
    var downloadModels: (suspend (onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit) -> Boolean)? = null

    // The download outlives the tile that started it: a ~986 MB fetch must
    // survive swiping away, tab switches, and recomposition, so it runs on
    // the runtime's own scope rather than a composition scope.
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _downloadProgress = MutableStateFlow<Float?>(null)
    private val _downloadCompleted = MutableStateFlow(0)

    /** Download fraction while fetching, or null when idle. */
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    /** Bumped each time a download finishes; tiles re-check the model on it. */
    val downloadCompleted: StateFlow<Int> = _downloadCompleted.asStateFlow()

    /** Starts the category download; a second call while running is a no-op. */
    fun startModelDownload() {
        val hook = downloadModels ?: return
        if (_downloadProgress.value != null) return
        _downloadProgress.value = 0f
        downloadScope.launch {
            val complete = runCatching {
                hook { downloadedBytes, totalBytes ->
                    _downloadProgress.value = if (totalBytes > 0) {
                        (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
            }.getOrDefault(false)
            _downloadProgress.value = null
            if (complete) {
                _downloadCompleted.value = _downloadCompleted.value + 1
            }
        }
    }

    /** True when the KNOWLEDGE task's model file is present. */
    fun isKnowledgeModelAvailable(): Boolean =
        service?.modelPathForTask(DeenlyKnowledgeTasks.KNOWLEDGE) != null

    private val prayerSelectionMutex = Mutex()
    private val prayerSelections = linkedMapOf<String, String>()

    suspend fun selectPrayerHadith(context: String, candidates: List<PrayerHadithCandidate>): PrayerHadithCandidate? =
        prayerSelectionMutex.withLock {
            val engine = service ?: return@withLock null
            val path = engine.modelPathForTask(DeenlyKnowledgeTasks.KNOWLEDGE) ?: return@withLock null
            if (candidates.isEmpty()) return@withLock null
            val prompt = PrayerHadithSelection.prompt(context, candidates)
            val key = "$path|$prompt"
            prayerSelections[key]?.let { return@withLock PrayerHadithSelection.selected(it, candidates) }
            val raw = engine.runSuspending(path, DeenlyKnowledgeTasks.KNOWLEDGE, PrayerHadithSelection.SYSTEM_PROMPT, prompt)
            val selected = PrayerHadithSelection.selected(raw?.let(::parseKnowledgeTitle), candidates)
            if (selected != null) {
                if (prayerSelections.size >= 24) prayerSelections.remove(prayerSelections.keys.first())
                prayerSelections[key] = selected.sourceId
            }
            selected
        }

    /**
     * Runs the KNOWLEDGE task over one grounded source and returns the model's
     * title, or null when the service is missing, the model is not
     * downloaded, or the output fails the Android validation rules.
     */
    suspend fun generateKnowledgeTitle(
        collection: String,
        reference: String,
        topic: String,
        appSituation: String?,
        sourceText: String,
    ): String? {
        val service = service ?: return null
        val modelPath = service.modelPathForTask(DeenlyKnowledgeTasks.KNOWLEDGE) ?: return null
        val userPrompt = buildString {
            appendLine("Task: create a knowledge item.")
            appendLine("Collection: $collection")
            appendLine("Reference: $reference")
            appendLine("Topic: $topic")
            appSituation
                ?.takeIf(String::isNotBlank)
                ?.let { appendLine("App situation: ${it.take(MAX_APP_SITUATION_CHARS)}") }
            appendLine("Source text:")
            append(sourceText.take(MAX_SOURCE_CHARS))
        }
        val raw = service.runSuspending(
            modelPath = modelPath,
            task = DeenlyKnowledgeTasks.KNOWLEDGE,
            systemPrompt = SYSTEM_PROMPT,
            userPrompt = userPrompt,
        ) ?: return null
        return parseKnowledgeTitle(raw)
    }

    /**
     * Android's KNOWLEDGE validation, verbatim: exactly contentType and
     * title, contentType "knowledge", a non-empty title of at most 84 chars
     * with no control characters.
     */
    fun parseKnowledgeTitle(rawOutput: String): String? {
        val start = rawOutput.indexOf('{')
        val end = rawOutput.lastIndexOf('}')
        if (start < 0 || end < start) return null
        val output = runCatching {
            Json.parseToJsonElement(rawOutput.substring(start, end + 1)) as? JsonObject
        }.getOrNull() ?: return null
        if (output.keys != setOf("contentType", "title")) return null
        val contentType = (output["contentType"] as? JsonPrimitive)?.content
        if (contentType != DeenlyKnowledgeTasks.KNOWLEDGE) return null
        val title = (output["title"] as? JsonPrimitive)?.content?.trim().orEmpty()
        val meaningfulCharacters = title.filter { character -> character.isLetterOrDigit() }
        return title.takeIf {
            it.isNotEmpty() &&
                it.length <= MAX_TITLE_CHARS &&
                it.none(Char::isISOControl) &&
                meaningfulCharacters.length >= MIN_MEANINGFUL_TITLE_CHARS &&
                meaningfulCharacters.map { it.lowercaseChar() }.distinct().size >= 2
        }
    }

    private suspend fun DeenlyKnowledgeService.runSuspending(
        modelPath: String,
        task: String,
        systemPrompt: String,
        userPrompt: String,
    ): String? = suspendCancellableCoroutine { continuation ->
        run(modelPath, task, systemPrompt, userPrompt) { result ->
            if (continuation.isActive) continuation.resume(result)
        }
    }

    /**
     * Same KNOWLEDGE system prompt as Android's DeenlyKnowledgeModel: only
     * the supplied source, one minified JSON object, exact-span discipline.
     */
    internal const val SYSTEM_PROMPT =
        "You create one grounded Islamic learning item from the supplied source.\n" +
            "Use only the supplied source, never memory. Return one minified JSON object and nothing else.\n" +
            "A knowledge object has exactly contentType and title. For a question, create a natural,\n" +
            "self-contained global knowledge question that does not say \"this narration\", \"this hadith\",\n" +
            "\"this ayah\", \"this verse\", \"this passage\", \"according to\", or ask where the text came from.\n" +
            "A question object has exactly contentType, questionKind, question, answer, evidence, and options.\n" +
            "questionKind must be one of person, place, food, color, action, number, description, teaching,\n" +
            "outcome, object, or time. answer must be a short exact contiguous span copied from Source text.\n" +
            "evidence must be one exact contiguous span copied from Source text and must contain answer.\n" +
            "options must contain exactly four short unique strings including answer exactly once. Distractors\n" +
            "must not contradict anything stated in the source. Never output Arabic, IDs, citations, rulings,\n" +
            "or claims unsupported by the source. Prefer a simple explicit detail over interpretation."

    internal const val MAX_SOURCE_CHARS = 1_400
    internal const val MAX_APP_SITUATION_CHARS = 320
    internal const val MAX_TITLE_CHARS = 84
    internal const val MIN_MEANINGFUL_TITLE_CHARS = 3
}
