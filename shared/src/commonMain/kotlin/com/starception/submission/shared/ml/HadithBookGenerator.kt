/*
 * Copyright 2026 The Android Open Source Project
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

import com.starception.submission.core.model.data.GeneratedHadithBooks
import com.starception.submission.core.model.data.HadithCollections
import com.starception.submission.shared.assets.ContentDownloadBus
import com.starception.submission.shared.assets.ContentDownloadStatus
import com.starception.submission.shared.hadith.createSharedHadithRepository
import com.starception.submission.shared.storage.platformKeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume

/**
 * One AI-generated book of a hadith collection, persisted per database.
 */
@kotlinx.serialization.Serializable
data class GeneratedBook(
    val id: Int,
    val name: String,
    val firstHadithId: Int,
    val lastHadithId: Int,
    val hadithCount: Int,
)

/**
 * The shared hadith-book generator — the iOS counterpart of Android's
 * HadithBookCategorizer, running the same downloadable Qwen2.5 categorizer
 * GGUF through the platform Deenly runner (ChatML, greedy, grammar-guided).
 *
 * Collections are ordered, so the generator samples evenly spread hadiths,
 * proposes a topical book taxonomy from those samples, classifies each
 * sample into it, and derives gapless contiguous ranges with the same
 * misread-absorption rules as Android.
 *
 * All inference goes through [DeenlyKnowledgeRuntime]; progress feeds the
 * pull-to-sync banner via [ContentDownloadBus]; results persist in the
 * platform key-value store and publish through [GeneratedHadithBooks] so
 * collection browsing falls back to them when the registry has no books.
 */
object HadithBookGenerator {

    private const val TASK_PROPOSE = "categorizer-propose"
    private const val TASK_CLASSIFY = "categorizer-classify"
    private const val STORE_PREFIX = "generated_hadith_books/"

    /** Correlated with Android's runner label; the banner renders it small. */
    private const val BANNER_LABEL = "Generating books"

    private const val DEFAULT_MAX_SAMPLES = 24
    private const val MIN_LABELED_SAMPLES = 6
    private const val MIN_BOOKS = 4
    private const val MAX_BOOKS = 40
    private const val MAX_HADITH_CHARS = 900
    private const val PROPOSAL_SAMPLE_CHARS = 280
    private const val MIN_BOOK_NAME_CHARS = 3
    private const val MAX_BOOK_NAME_CHARS = 48

    private val json = Json { ignoreUnknownKeys = true }

    /** Installs the store-backed provider once per process. */
    private val providerInstalled = run {
        GeneratedHadithBooks.provider = { databaseFile ->
            loadBooks(databaseFile).map { book ->
                com.starception.submission.core.model.data.HadithCollectionBookEntry(
                    id = book.id,
                    nameEnglish = book.name,
                    nameArabic = "",
                    firstHadithId = book.firstHadithId,
                    lastHadithId = book.lastHadithId,
                    hadithCount = book.hadithCount,
                )
            }
        }
        true
    }

    /**
     * Platform hook: downloads + assembles the categorizer GGUF, reporting
     * byte progress; true on success. iOS installs it from
     * installHadithCategorizerDownloadHook().
     */
    var downloadModel: (suspend (onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit) -> Boolean)? = null

    /**
     * UI entry point: downloads the model when missing, then generates.
     * Banner progress covers both phases; returns the generated books or null.
     */
    suspend fun runFromUi(databaseFile: String): List<GeneratedBook>? {
        if (!isModelReady()) {
            val hook = downloadModel ?: return null
            if (!hook { _, _ -> }) return null
        }
        return generate(databaseFile)
    }

    /** True when the categorizer GGUF is assembled on this device. */
    fun isModelReady(): Boolean {
        providerInstalled // ensure the bridge is installed
        val service = DeenlyKnowledgeRuntime.service ?: return false
        return service.modelPathForTask(TASK_PROPOSE) != null
    }

    /** The persisted books for a collection, or empty. */
    fun loadBooks(databaseFile: String): List<GeneratedBook> = runCatching {
        platformKeyValueStore().getString(STORE_PREFIX + databaseFile)
            ?.let { json.decodeFromString(ListSerializer(GeneratedBook.serializer()), it) }
    }.getOrNull() ?: emptyList()

    /**
     * Runs the full generation for a collection. Returns the generated books
     * (also persisted + published), or null when the model is missing, the
     * collection is unknown, or a phase fails validation.
     */
    suspend fun generate(
        databaseFile: String,
        maxSamples: Int = DEFAULT_MAX_SAMPLES,
    ): List<GeneratedBook>? = withContext(Dispatchers.Default) {
        providerInstalled
        val service = DeenlyKnowledgeRuntime.service ?: return@withContext null
        val modelPath = service.modelPathForTask(TASK_PROPOSE) ?: return@withContext null
        val collection = HadithCollections.byDatabaseFile(databaseFile) ?: return@withContext null
        val hadithCount = collection.hadithCount
        if (hadithCount <= 0) return@withContext null

        val sampleIds = buildSampleIds(hadithCount, maxSamples)
        if (sampleIds.isEmpty()) return@withContext null
        val samples = createSharedHadithRepository()
            .getCollectionHadithsByIds(databaseFile, sampleIds)
        if (samples.isEmpty()) return@withContext null

        val totalSteps = samples.size + 1
        publishProgress(0, totalSteps)

        // Phase 1: propose the topical taxonomy.
        val proposalPrompt = buildString {
            appendLine("Collection: ${collection.name}")
            appendLine("Sampled hadiths (in collection order):")
            samples.forEach { hadith ->
                appendLine("• ${hadith.english.ifBlank { hadith.arabic }.take(PROPOSAL_SAMPLE_CHARS).trim()}")
            }
        }
        val proposedRaw = service.runSuspending(
            modelPath = modelPath,
            task = TASK_PROPOSE,
            systemPrompt = PROPOSAL_SYSTEM_PROMPT,
            userPrompt = proposalPrompt,
        ) ?: run { ContentDownloadBus.publish(null); return@withContext null }
        val proposed = parseProposedBooks(proposedRaw)
        if (proposed == null || proposed.size !in MIN_BOOKS..MAX_BOOKS) {
            ContentDownloadBus.publish(null)
            return@withContext null
        }
        publishProgress(1, totalSteps)

        // Phase 2: classify each sampled hadith into the proposed books.
        val labels = mutableListOf<Pair<Int, Int>>() // hadith id to book index
        val bookList = proposed.mapIndexed { index, name -> "$index. $name" }.joinToString("\n")
        for ((sampleIndex, hadith) in samples.withIndex()) {
            val text = hadith.english.ifBlank { hadith.arabic }.take(MAX_HADITH_CHARS)
            if (text.isBlank()) continue
            val raw = service.runSuspending(
                modelPath = modelPath,
                task = TASK_CLASSIFY,
                systemPrompt = CLASSIFY_SYSTEM_PROMPT,
                userPrompt = buildString {
                    appendLine("Books:")
                    appendLine(bookList)
                    appendLine("Hadith:")
                    appendLine(text)
                    append("Book index: ")
                },
            ) ?: continue
            val bookIndex = parseClassification(raw) ?: continue
            if (bookIndex in proposed.indices) {
                labels += hadith.id to bookIndex
            }
            publishProgress(sampleIndex + 2, totalSteps)
        }
        if (labels.size < MIN_LABELED_SAMPLES) {
            ContentDownloadBus.publish(null)
            return@withContext null
        }

        // Phase 3: gapless contiguous ranges with misread absorption.
        val books = buildContiguousBooks(proposed, labels, hadithCount)
        if (books.isEmpty()) {
            ContentDownloadBus.publish(null)
            return@withContext null
        }
        platformKeyValueStore().putString(
            STORE_PREFIX + databaseFile,
            json.encodeToString(ListSerializer(GeneratedBook.serializer()), books),
        )
        ContentDownloadBus.publish(null)
        GeneratedHadithBooks.notifyChanged()
        books
    }

    // ---- prompts ----

    private const val PROPOSAL_SYSTEM_PROMPT =
        "You catalog hadith collections into their canonical topical books.\n" +
            "The hadiths arrive in collection order. Infer the traditional book (kitab) divisions\n" +
            "the collection uses, such as Purification, Prayer, Fasting, Charity, Pilgrimage,\n" +
            "Marriage, Trade, Knowledge, Virtues. Return a minified JSON array of book titles\n" +
            "in collection order, and nothing else. Between 4 and 40 short English book titles.\n" +
            "Never include hadith text or Arabic."

    private const val CLASSIFY_SYSTEM_PROMPT =
        "You assign one hadith to its canonical book.\n" +
            "Return one minified JSON object with exactly one integer field book: the index\n" +
            "of the book the hadith belongs to, counted from 0 in the given list. Judge only\n" +
            "by the hadith's main topic. Never output Arabic, explanations, or any other field."

    // ---- parsing ----

    private fun parseProposedBooks(raw: String): List<String>? {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        val parsed = runCatching {
            Json.parseToJsonElement(raw.substring(start, end + 1)) as? JsonArray
        }.getOrNull() ?: return null
        val names = parsed.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
            .filter { it.length in MIN_BOOK_NAME_CHARS..MAX_BOOK_NAME_CHARS && it.none(Char::isISOControl) }
            .distinct()
        return names.takeIf { it.size in MIN_BOOKS..MAX_BOOKS }
    }

    private fun parseClassification(raw: String): Int? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val parsed = runCatching {
            Json.parseToJsonElement(raw.substring(start, end + 1)) as? JsonObject
        }.getOrNull() ?: return null
        return (parsed["book"] as? JsonPrimitive)?.content?.toIntOrNull()
    }

    // ---- range assembly (Android's buildContiguousBooks, same rules) ----

    internal fun buildContiguousBooks(
        proposed: List<String>,
        labels: List<Pair<Int, Int>>,
        hadithCount: Int,
    ): List<GeneratedBook> {
        data class Segment(val bookIndex: Int, val fromId: Int, val toId: Int, val votes: Int)

        val sorted = labels.sortedBy { it.first }
        val runs = mutableListOf<Segment>()
        for ((hadithId, bookIndex) in sorted) {
            val last = runs.lastOrNull()
            if (last != null && last.bookIndex == bookIndex) {
                runs[runs.size - 1] = last.copy(toId = hadithId, votes = last.votes + 1)
            } else {
                runs += Segment(bookIndex, hadithId, hadithId, 1)
            }
        }
        if (runs.isEmpty()) return emptyList()

        val cleaned = mutableListOf<Segment>()
        for ((index, run) in runs.withIndex()) {
            val previous = cleaned.lastOrNull()
            val nextRun = runs.getOrNull(index + 1)
            val isThin = run.votes == 1 &&
                (previous?.votes ?: 0) > 1 &&
                (nextRun?.votes ?: 0) > 1
            if (isThin) continue
            if (previous != null && previous.bookIndex == run.bookIndex) {
                cleaned[cleaned.size - 1] =
                    previous.copy(toId = run.toId, votes = previous.votes + run.votes)
            } else {
                cleaned += run
            }
        }

        val byBook = cleaned.groupBy { it.bookIndex }
        val kept = cleaned
            .filter { run -> byBook[run.bookIndex]!!.maxBy(Segment::votes) === run }
            .sortedBy { it.fromId }

        val books = mutableListOf<GeneratedBook>()
        kept.forEachIndexed { index, run ->
            val first = if (index == 0) 1 else run.fromId
            val last = if (index == kept.size - 1) hadithCount else kept[index + 1].fromId - 1
            if (last >= first) {
                books += GeneratedBook(
                    id = books.size + 1,
                    name = proposed.getOrNull(run.bookIndex) ?: "Book ${books.size + 1}",
                    firstHadithId = first,
                    lastHadithId = last,
                    hadithCount = last - first + 1,
                )
            }
        }
        return books
    }

    private fun buildSampleIds(hadithCount: Int, maxSamples: Int): List<Int> {
        val count = maxSamples.coerceAtMost(hadithCount)
        val step = hadithCount.toDouble() / count
        return List(count) { index -> ((index + 0.5) * step).toInt().coerceIn(1, hadithCount) }.distinct()
    }

    private fun publishProgress(done: Int, total: Int) {
        ContentDownloadBus.publish(
            ContentDownloadStatus(
                label = BANNER_LABEL,
                progress = if (total > 0) done.toFloat() / total else 0f,
            ),
        )
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
}
