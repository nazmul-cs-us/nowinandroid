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

package com.starception.submission.ml

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.starception.submission.core.hadithdatabase.HadithDatabase
import com.starception.submission.core.hadithdatabase.HadithRepository
import com.starception.submission.core.model.data.HadithCollectionBookEntry
import com.starception.submission.core.model.data.HadithCollections
import com.starception.submission.ui.AppTaskProgressBus
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One AI-generated book/chapter of a hadith collection. */
@Serializable
data class GeneratedHadithBook(
    val id: Int,
    val nameEnglish: String,
    val firstHadithId: Int,
    val lastHadithId: Int,
    val hadithCount: Int,
)

/** Progress for the generation run surfaced to UI/receivers. */
data class HadithBookGenerationProgress(
    val phase: String,
    val processed: Int,
    val total: Int,
)

/**
 * Generates a hadith collection's book/chapter structure fully on device with
 * the downloadable `hadith-categorizer-v1` GGUF (Qwen2.5-1.5B-Instruct), run
 * through the same llama.cpp completion binary as the Deenly knowledge models.
 *
 * Hadith collections are ordered — books are contiguous id ranges — so the
 * categorizer never classifies every hadith. It:
 *   1. proposes the canonical topical books from a spread of sample hadiths
 *      (JSON-schema-constrained), then
 *   2. classifies sampled hadiths into those books (enum-constrained), and
 *   3. derives contiguous ranges from the sampled labels with majority
 *      smoothing, so a single misread window cannot scramble a boundary.
 *
 * Collections whose [com.starception.submission.core.model.data.HadithCollections]
 * entry already carries verified books never need this; it exists for future
 * collections with no structured source.
 */
class HadithBookCategorizer(context: Context) {
    private val appContext = context.applicationContext

    /** Single model instance: one inference at a time, like the knowledge models. */
    private val inferenceMutex = Mutex()

    /**
     * Runs the full generation for a downloaded collection database.
     * Returns the generated books (also persisted by [GeneratedHadithBooksStore]),
     * or null when the model is missing/invalid or the collection cannot be read.
     */
    suspend fun generateBooks(
        databaseFile: String,
        maxSamples: Int = DEFAULT_MAX_SAMPLES,
        onProgress: (HadithBookGenerationProgress) -> Unit = {},
    ): List<GeneratedHadithBook>? = inferenceMutex.withLock {
        withContext(Dispatchers.IO) {
            val modelFile = File(appContext.filesDir, "cdn_assets/$MODEL_CDN_KEY")
            if (!modelFile.isFile || modelFile.length() != MODEL_SIZE_BYTES) {
                Log.w(TAG, "Categorizer model is missing or has an unexpected size")
                return@withContext null
            }
            if (sha256(modelFile) != MODEL_SHA256) {
                Log.w(TAG, "Categorizer model checksum verification failed")
                return@withContext null
            }
            val runner = File(appContext.applicationInfo.nativeLibraryDir, RUNNER_FILE_NAME)
            if (!runner.isFile || !runner.canExecute()) {
                Log.w(TAG, "Categorizer runner is unavailable")
                return@withContext null
            }
            if (!HadithDatabase.isDatabaseAvailable(appContext, databaseFile)) {
                Log.w(TAG, "Collection database is not available: $databaseFile")
                return@withContext null
            }

            val dao = HadithDatabase.getInstance(appContext, databaseFile).hadithDao()
            val metadata = HadithDatabase.getCollectionMetadata(appContext, databaseFile)
                ?: run {
                    Log.w(TAG, "Cannot read hadith count for $databaseFile")
                    return@withContext null
                }
            val hadithCount = metadata.hadithCount

            // Evenly spread sample ids over the ordered collection.
            val sampleIds = buildSampleIds(hadithCount, maxSamples)
            val samples = dao.getHadithsByIds(sampleIds)
            if (samples.isEmpty()) {
                Log.w(TAG, "No sample hadiths could be read")
                return@withContext null
            }

            // The pull-to-sync banner (both Home's inner container and the
            // app-level one) renders long non-download tasks from this bus, so
            // the generation stays visible on every tab while it runs. Real CDN
            // downloads outrank it while active. The finally guarantees the
            // banner never sticks on failure or cancellation.
            val totalSteps = samples.size + PROPOSAL_STEPS
            try {
                onProgress(HadithBookGenerationProgress("proposing books", 0, 1))
                // Compact label on purpose: the strip is one marquee line, and a
                // moving percentage says more than a long sentence.
                AppTaskProgressBus.update(GENERATION_LABEL, 0, totalSteps * TICKER_UNITS_PER_STEP)

                // Phase 1: propose the topical book taxonomy.
                val proposed = withStepTicker(stepIndex = 0, totalSteps = totalSteps) {
                    proposeBooks(databaseFile, samples)
                } ?: run {
                    Log.w(TAG, "Book proposal was rejected")
                    return@withContext null
                }
                if (proposed.isEmpty()) {
                    Log.w(TAG, "Book proposal returned no books")
                    return@withContext null
                }
                Log.i(TAG, "Proposed ${proposed.size} books for $databaseFile")

                // Phase 2: classify each sampled hadith into the proposed books.
                val labels = mutableListOf<Pair<Int, Int>>() // hadith id to book index
                samples.forEachIndexed { index, hadith ->
                    onProgress(
                        HadithBookGenerationProgress(
                            phase = "classifying",
                            processed = index + 1,
                            total = samples.size,
                        ),
                    )
                    val text = hadith.textPlain.orEmpty().take(MAX_HADITH_CHARS).ifBlank { return@forEachIndexed }
                    // Each inference reloads the ~1 GB model, so a single step
                    // runs for tens of seconds; the ticker keeps the percentage
                    // moving within the step instead of parking on one value.
                    val bookIndex = withStepTicker(
                        stepIndex = index + PROPOSAL_STEPS,
                        totalSteps = totalSteps,
                    ) {
                        classify(databaseFile, proposed, text)
                    } ?: return@forEachIndexed
                    labels += hadith.id to bookIndex
                }
                if (labels.size < MIN_LABELED_SAMPLES) {
                    Log.w(TAG, "Only ${labels.size} samples survived classification")
                    return@withContext null
                }

                // Phase 3: contiguous ranges from the sampled labels.
                val books = buildContiguousBooks(proposed, labels, hadithCount)
                Log.i(TAG, "Generated ${books.size} contiguous books for $databaseFile")
                GeneratedHadithBooksStore.save(appContext, databaseFile, books)
                books
            } finally {
                AppTaskProgressBus.clear()
            }
        }
    }

    /**
     * Runs one inference step while keeping the banner percentage moving.
     * Each step reloads the ~1 GB model, so a single step runs for tens of
     * seconds; the ticker advances the fraction with elapsed time (capped
     * just below the step's end so it never completes early), and the finally
     * locks the exact boundary when the step really finishes.
     */
    private suspend fun <T> withStepTicker(
        stepIndex: Int,
        totalSteps: Int,
        block: suspend () -> T,
    ): T = coroutineScope {
        val ticker = launch {
            val start = android.os.SystemClock.elapsedRealtime()
            while (true) {
                delay(TICKER_INTERVAL_MILLIS)
                val withinStep = (
                    (android.os.SystemClock.elapsedRealtime() - start) * TICKER_UNITS_PER_STEP /
                        EXPECTED_STEP_MILLIS
                    ).toInt().coerceAtMost(TICKER_UNITS_PER_STEP - 1)
                AppTaskProgressBus.update(
                    GENERATION_LABEL,
                    stepIndex * TICKER_UNITS_PER_STEP + withinStep,
                    totalSteps * TICKER_UNITS_PER_STEP,
                )
            }
        }
        try {
            block()
        } finally {
            ticker.cancel()
            AppTaskProgressBus.update(
                GENERATION_LABEL,
                (stepIndex + 1) * TICKER_UNITS_PER_STEP,
                totalSteps * TICKER_UNITS_PER_STEP,
            )
        }
    }

    /** Runs the completion binary once with a JSON schema and returns stdout. */
    private suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        jsonSchema: String,
        maxPredictedTokens: Int,
        timeoutSeconds: Long = INFERENCE_TIMEOUT_SECONDS,
    ): String? = coroutineScope {
        val modelFile = File(appContext.filesDir, "cdn_assets/$MODEL_CDN_KEY")
        val runner = File(appContext.applicationInfo.nativeLibraryDir, RUNNER_FILE_NAME)
        try {
            val process = ProcessBuilder(
                runner.absolutePath,
                "--model",
                modelFile.absolutePath,
                "-sys",
                systemPrompt,
                "--prompt",
                userPrompt,
                "--single-turn",
                "--predict",
                maxPredictedTokens.toString(),
                "--json-schema",
                jsonSchema,
                "--temp",
                "0",
                "--no-display-prompt",
                "--no-warmup",
                "--simple-io",
                // Keep conversation mode ON: the runner is llama-cli, and
                // --no-conversation forces raw completion (no chat template).
                // The stock Qwen2.5-Instruct categorizer needs its embedded
                // ChatML template applied; without it the model degenerates
                // into schema-constrained garbage. The Deenly fine-tunes work
                // raw because they were trained on that format; this model
                // was not. --single-turn still exits after one response.
            ).start()
            val stdout = async { process.inputStream.bufferedReader().readText() }
            val stderr = async { process.errorStream.bufferedReader().readText() }
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                Log.w(TAG, "Categorizer inference timed out")
                process.destroyForcibly()
                process.waitFor(PROCESS_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                runCatching { process.inputStream.close() }
                runCatching { process.errorStream.close() }
                runCatching { stdout.await() }
                runCatching { stderr.await() }
                return@coroutineScope null
            }
            if (process.exitValue() != 0) {
                Log.w(TAG, "Categorizer runner failed: ${stderr.await().take(LOG_PREVIEW_CHARS)}")
                null
            } else {
                stdout.await()
            }
        } catch (error: Exception) {
            Log.w(TAG, "Categorizer inference failed", error)
            null
        }
    }

    /** Phase 1: the model proposes a bounded topical book list from spread samples. */
    private suspend fun proposeBooks(
        databaseFile: String,
        samples: List<com.starception.submission.core.hadithdatabase.HadithEntity>,
    ): List<String>? {
        val collectionName = databaseFile.removeSuffix(".db").replace('_', ' ')
        val digest = samples.map { it.id }.joinToString(",")
        val sampleText = samples
            .map { hadith -> "• ${hadith.textPlain.orEmpty().take(PROPOSAL_SAMPLE_CHARS).trim()}" }
            .joinToString("\n")
        val output = complete(
            systemPrompt = PROPOSAL_SYSTEM_PROMPT,
            userPrompt = buildString {
                appendLine("Collection: $collectionName")
                appendLine("Sampled hadiths (in collection order):")
                append(sampleText)
            },
            jsonSchema = PROPOSAL_JSON_SCHEMA,
            maxPredictedTokens = PROPOSAL_MAX_TOKENS,
            timeoutSeconds = PROPOSAL_TIMEOUT_SECONDS,
        ) ?: return null
        return parseProposedBooks(output).also { parsed ->
            if (parsed == null) {
                Log.w(TAG, "Rejected proposal output: ${output.take(LOG_PREVIEW_CHARS)} (digest $digest)")
            }
        }
    }

    /** Phase 2: the model labels one hadith with a proposed book index. */
    private suspend fun classify(
        databaseFile: String,
        proposed: List<String>,
        hadithText: String,
    ): Int? {
        val bookList = proposed.mapIndexed { index, name -> "$index. $name" }.joinToString("\n")
        val output = complete(
            systemPrompt = CLASSIFY_SYSTEM_PROMPT,
            userPrompt = buildString {
                appendLine("Books:")
                appendLine(bookList)
                appendLine("Hadith:")
                append(hadithText.take(MAX_HADITH_CHARS))
                appendLine()
                append("Book index: ")
            },
            jsonSchema = classificationJsonSchema(proposed.size),
            maxPredictedTokens = CLASSIFY_MAX_TOKENS,
        ) ?: return null
        val start = output.indexOf('{')
        val end = output.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val parsed = runCatching {
            Json.parseToJsonElement(output.substring(start, end + 1))
        }.getOrNull() ?: return null
        val book = (parsed as? kotlinx.serialization.json.JsonObject)?.get("book") as?
            kotlinx.serialization.json.JsonPrimitive ?: return null
        return book.content.toIntOrNull()?.takeIf { it in proposed.indices }
    }

    private fun parseProposedBooks(output: String): List<String>? {
        val start = output.indexOf('[')
        val end = output.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        val parsed = runCatching {
            Json.decodeFromString(ListSerializer(kotlinx.serialization.json.JsonPrimitive.serializer()), output.substring(start, end + 1))
        }.getOrNull() ?: return null
        val names = parsed.map { it.content.trim() }
            .filter { it.length in MIN_BOOK_NAME_CHARS..MAX_BOOK_NAME_CHARS && it.none(Char::isISOControl) }
            .distinct()
        if (names.size < MIN_BOOKS || names.size > MAX_BOOKS) return null
        return names
    }

    /**
     * Phase 3: turns (hadithId, bookIndex) samples into contiguous books. The
     * sampler walks an ordered collection, so each book's samples form a run;
     * the merge pass repairs isolated misreads and the books come out as
     * adjacent ranges covering 1..hadithCount.
     */
    internal fun buildContiguousBooks(
        proposed: List<String>,
        labels: List<Pair<Int, Int>>,
        hadithCount: Int,
    ): List<GeneratedHadithBook> {
        data class Segment(val bookIndex: Int, val fromId: Int, val toId: Int, val votes: Int)

        val sorted = labels.sortedBy { it.first }

        // Split into runs of the same label; the previous run ends halfway
        // toward the first sample of the next run.
        val runs = mutableListOf<Segment>()
        for ((hadithId, bookIndex) in sorted) {
            val last = runs.lastOrNull()
            if (last != null && last.bookIndex == bookIndex) {
                runs[runs.size - 1] = last.copy(toId = hadithId, votes = last.votes + 1)
            } else {
                if (last != null) {
                    runs[runs.size - 1] = last.copy(toId = (last.toId + hadithId) / 2)
                }
                runs += Segment(bookIndex, hadithId, hadithId, 1)
            }
        }
        if (runs.isEmpty()) return emptyList()

        // Absorb thin runs (a lone misread window) into the neighbor with more
        // votes, then coalesce adjacent runs of the surviving book.
        val cleaned = mutableListOf<Segment>()
        for ((index, run) in runs.withIndex()) {
            val previous = cleaned.lastOrNull()
            val nextRun = runs.getOrNull(index + 1)
            val isThin = run.votes == 1 &&
                (previous?.votes ?: 0) > 1 &&
                (nextRun?.votes ?: 0) > 1
            if (isThin) {
                // Drop the run; its range moves to whichever neighbor survives.
                continue
            }
            if (previous != null && previous.bookIndex == run.bookIndex) {
                cleaned[cleaned.size - 1] =
                    previous.copy(toId = run.toId, votes = previous.votes + run.votes)
            } else {
                cleaned += run
            }
        }

        // An ordered collection visits each book once: when the same book
        // survives in several separated runs, keep the strongest run only.
        val byBook = cleaned.groupBy { it.bookIndex }
        val kept = cleaned
            .filter { run -> byBook[run.bookIndex]!!.maxBy(Segment::votes) === run }
            .sortedBy { it.fromId }

        // Derive gapless contiguous ranges: an ordered collection's books are
        // adjacent by definition, so each book ends one id before the next
        // book's first sample; the first book starts at 1 and the last ends at
        // the collection's hadith count.
        val books = mutableListOf<GeneratedHadithBook>()
        kept.forEachIndexed { index, run ->
            val first = if (index == 0) 1 else run.fromId
            val last = if (index == kept.size - 1) {
                hadithCount
            } else {
                kept[index + 1].fromId - 1
            }
            if (last >= first) {
                books += GeneratedHadithBook(
                    id = books.size + 1,
                    nameEnglish = proposed.getOrNull(run.bookIndex) ?: "Book ${books.size + 1}",
                    firstHadithId = first,
                    lastHadithId = last,
                    hadithCount = last - first + 1,
                )
            }
        }
        return books
    }

    private fun buildSampleIds(hadithCount: Int, maxSamples: Int): List<Int> {
        if (hadithCount <= 0) return emptyList()
        val count = maxSamples.coerceAtMost(hadithCount)
        val step = hadithCount.toDouble() / count
        return List(count) { index -> ((index + 0.5) * step).toInt().coerceIn(1, hadithCount) }.distinct()
    }

    private fun classificationJsonSchema(bookCount: Int): String =
        """{"type":"object","properties":{"book":{"type":"integer","minimum":0,"maximum":${bookCount - 1}}},"required":["book"],"additionalProperties":false}"""

    companion object {
        const val MODEL_CDN_KEY = "models/hadith/hadith-categorizer-v1-q4_k_m.gguf"
        const val MODEL_FILE_NAME = "hadith-categorizer-v1-q4_k_m.gguf"
        const val MODEL_SIZE_BYTES = 1_117_320_736L
        const val MODEL_SHA256 =
            "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
        const val DOWNLOAD_CATEGORY = "model_hadith_categorizer"

        /** Whether the categorizer GGUF has been downloaded and is complete. */
        fun isModelDownloaded(context: Context): Boolean {
            // Called from composition on card-heavy screens; stat the 1.1 GB
            // file at most once per cache window instead of every recomposition.
            val now = SystemClock.elapsedRealtime()
            val cachedAt = cachedModelDownloadedAtMs
            val cached = cachedModelDownloaded
            if (cached != null && now - cachedAt < MODEL_DOWNLOAD_CACHE_TTL_MS) {
                return cached
            }
            val file = File(context.applicationContext.filesDir, "cdn_assets/$MODEL_CDN_KEY")
            val downloaded = file.isFile && file.length() == MODEL_SIZE_BYTES
            cachedModelDownloaded = downloaded
            cachedModelDownloadedAtMs = now
            return downloaded
        }

        @Volatile
        private var cachedModelDownloaded: Boolean? = null

        @Volatile
        private var cachedModelDownloadedAtMs: Long = 0L

        private const val MODEL_DOWNLOAD_CACHE_TTL_MS = 30_000L

        private const val RUNNER_FILE_NAME = "libdeenly_completion.so"
        private const val TAG = "HadithBookCategorizer"
        private const val INFERENCE_TIMEOUT_SECONDS = 120L
        private const val PROPOSAL_TIMEOUT_SECONDS = 240L
        private const val PROCESS_SHUTDOWN_TIMEOUT_SECONDS = 5L
        private const val DEFAULT_MAX_SAMPLES = 96
        /** The proposal phase's weight in the banner progress (one step of the total). */
        private const val PROPOSAL_STEPS = 1
        /** Compact banner label while the generation runs. */
        private const val GENERATION_LABEL = "Generating books"
        /** Sub-step resolution of the banner fraction; 100 keeps the % readable. */
        private const val TICKER_UNITS_PER_STEP = 100
        private const val TICKER_INTERVAL_MILLIS = 1_500L
        /** Measured on-device: one inference ≈ model load ≈ 90 s. */
        private const val EXPECTED_STEP_MILLIS = 90_000L
        private const val MIN_LABELED_SAMPLES = 12
        private const val MIN_BOOKS = 4
        private const val MAX_BOOKS = 97
        private const val MIN_BOOK_NAME_CHARS = 3
        private const val MAX_BOOK_NAME_CHARS = 48
        private const val MAX_HADITH_CHARS = 900
        private const val PROPOSAL_SAMPLE_CHARS = 280
        private const val PROPOSAL_MAX_TOKENS = 2_048
        private const val CLASSIFY_MAX_TOKENS = 32
        private const val MERGE_VOTE_THRESHOLD = 2
        private const val LOG_PREVIEW_CHARS = 240
        private val PROPOSAL_SYSTEM_PROMPT = """You catalog hadith collections into their canonical topical books.
The hadiths arrive in collection order. Infer the traditional book (kitab) divisions the collection
uses, such as Purification, Prayer, Fasting, Charity, Pilgrimage, Marriage, Trade, Knowledge,
Virtues. Return a minified JSON array of book titles in collection order, and nothing else.
Between 4 and 40 short English book titles. Never include hadith text or Arabic."""
        private val PROPOSAL_JSON_SCHEMA =
            """{"type":"array","minItems":4,"maxItems":40,"items":{"type":"string","minLength":3,"maxLength":48}}"""
        private val CLASSIFY_SYSTEM_PROMPT = """You assign one hadith to its canonical book.
Return one minified JSON object with exactly one integer field book: the index of the book the
hadith belongs to, counted from 0 in the given list. Judge only by the hadith's main topic.
Never output Arabic, explanations, or any other field."""

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

/** Persists and reads AI-generated book structures per collection database. */
object GeneratedHadithBooksStore {
    private val json = Json { ignoreUnknownKeys = true }
    private const val DIR_NAME = "generated_hadith_books"

    private fun fileFor(context: Context, databaseFile: String) =
        File(File(context.applicationContext.filesDir, DIR_NAME), "$databaseFile.json")

    fun save(context: Context, databaseFile: String, books: List<GeneratedHadithBook>) {
        val target = fileFor(context, databaseFile)
        target.parentFile?.mkdirs()
        target.writeText(json.encodeToString(ListSerializer(GeneratedHadithBook.serializer()), books))
    }

    fun load(context: Context, databaseFile: String): List<GeneratedHadithBook>? = runCatching {
        val source = fileFor(context, databaseFile)
        if (!source.isFile) return null
        json.decodeFromString(ListSerializer(GeneratedHadithBook.serializer()), source.readText())
    }.getOrNull()

    fun clear(context: Context, databaseFile: String) {
        fileFor(context, databaseFile).delete()
    }
}

/** Bridge so generated books browse exactly like registry books. */
fun List<GeneratedHadithBook>.asBookEntries(): List<HadithCollectionBookEntry> = map { book ->
    HadithCollectionBookEntry(
        id = book.id,
        nameEnglish = book.nameEnglish,
        nameArabic = "",
        firstHadithId = book.firstHadithId,
        lastHadithId = book.lastHadithId,
        hadithCount = book.hadithCount,
    )
}
