/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.ml

import com.starception.submission.core.model.deenly.PrayerHadithCandidate
import com.starception.submission.core.model.deenly.PrayerHadithSelection
import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

enum class DeenlyKnowledgeTask(val wireName: String) {
    KNOWLEDGE("knowledge"),
    QUESTION("question"),
    ANSWER("answer"),
}

data class DeenlySourceEnvelope(
    val collection: String,
    val reference: String,
    val topic: String,
    /** Bounded, non-sensitive app state used to make the title timely. */
    val appSituation: String? = null,
    /** The typed Now Nudge question, answered only from [sourceText]. */
    val userQuestion: String? = null,
    /** Exact database text. Canonical Arabic is intentionally not accepted by this API. */
    val sourceText: String,
    val sourceTextSha256: String,
)

sealed interface DeenlyKnowledgeDecision {
    data class Knowledge(val title: String) : DeenlyKnowledgeDecision

    /** A self-contained quiz item whose answer and evidence are exact source-text spans. */
    data class GroundedQuestion(
        val questionKind: String,
        val question: String,
        val answer: String,
        val evidence: String,
        val options: List<String>,
    ) : DeenlyKnowledgeDecision

    data object SourceLocationQuestion : DeenlyKnowledgeDecision

    /** A direct answer to the user's question, quoted verbatim from the source. */
    data class GroundedAnswer(
        val answer: String,
        val evidence: String,
    ) : DeenlyKnowledgeDecision

    /** The source does not address the user's question. */
    data object Unanswered : DeenlyKnowledgeDecision
}

/**
 * Runs the reviewed Qwen-based v2 GGUF fully on device.
 *
 * The model may only choose a bounded content shape and, for knowledge cards, a title. IDs,
 * citations, answers, source text, and Quran Arabic remain in the verified database envelope.
 * Invalid output returns null so callers keep using the deterministic content bank.
 */
class DeenlyKnowledgeModel(context: Context) {
    private val appContext = context.applicationContext

    fun isKnowledgeModelAvailable(): Boolean {
        val task = DeenlyKnowledgeTask.KNOWLEDGE
        val file = File(appContext.filesDir, "cdn_assets/models/now_nudge/${task.modelFileName}")
        return file.isFile && file.length() == task.modelSizeBytes
    }

    suspend fun generate(
        task: DeenlyKnowledgeTask,
        source: DeenlySourceEnvelope,
    ): DeenlyKnowledgeDecision? = generateCompletion(task, source, task.systemPrompt, source.toPrompt(task))

    suspend fun selectPrayerHadith(
        context: String,
        candidates: List<PrayerHadithCandidate>,
    ): PrayerHadithCandidate? = prayerSelectionMutex.withLock {
        if (candidates.isEmpty()) return@withLock null
        val prompt = PrayerHadithSelection.prompt(context, candidates)
        prayerSelections[prompt]?.let { return@withLock PrayerHadithSelection.selected(it, candidates) }
        val envelope = DeenlySourceEnvelope(
            collection = "Downloaded Hadith collections",
            reference = "Prayer-specific source selection",
            topic = context,
            sourceText = prompt,
            sourceTextSha256 = sha256(prompt.toByteArray()),
        )
        val decision = generateCompletion(
            DeenlyKnowledgeTask.KNOWLEDGE, envelope, PrayerHadithSelection.SYSTEM_PROMPT, prompt,
        ) as? DeenlyKnowledgeDecision.Knowledge
        val selected = PrayerHadithSelection.selected(decision?.title, candidates)
        if (selected != null) {
            if (prayerSelections.size >= 24) prayerSelections.remove(prayerSelections.keys.first())
            prayerSelections[prompt] = selected.sourceId
        }
        selected
    }

    private suspend fun generateCompletion(
        task: DeenlyKnowledgeTask,
        source: DeenlySourceEnvelope,
        systemPrompt: String,
        userPrompt: String,
    ): DeenlyKnowledgeDecision? = inferenceMutex.withLock {
        withContext(Dispatchers.IO) {
            if (sha256(source.sourceText.toByteArray()) != source.sourceTextSha256.lowercase()) {
                Log.w(TAG, "Rejected source with a mismatched checksum")
                return@withContext null
            }
            val modelFile = File(
                appContext.filesDir,
                "cdn_assets/models/now_nudge/${task.modelFileName}",
            )
            if (!modelFile.isFile || modelFile.length() != task.modelSizeBytes) {
                Log.w(TAG, "Knowledge model is missing or has an unexpected size")
                return@withContext null
            }
            if (sha256(modelFile) != task.modelSha256) {
                Log.w(TAG, "Knowledge model checksum verification failed")
                return@withContext null
            }
            val runner = File(appContext.applicationInfo.nativeLibraryDir, RUNNER_FILE_NAME)
            if (!runner.isFile || !runner.canExecute()) {
                Log.w(TAG, "Knowledge model runner is unavailable")
                return@withContext null
            }

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
                    "256",
                    "--json-schema",
                    task.outputSchema,
                    "--temp",
                    "0",
                    "--no-display-prompt",
                    "--no-warmup",
                    "--simple-io",
                    "--no-conversation",
                ).start()
                coroutineScope {
                    val stdout = async { process.inputStream.bufferedReader().readText() }
                    val stderr = async { process.errorStream.bufferedReader().readText() }
                    if (!process.waitFor(INFERENCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        Log.w(TAG, "Knowledge model inference timed out")
                        process.destroyForcibly()
                        process.waitFor(PROCESS_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        runCatching { process.inputStream.close() }
                        runCatching { process.errorStream.close() }
                        runCatching { stdout.await() }
                        runCatching { stderr.await() }
                        return@coroutineScope null
                    }
                    val generated = stdout.await()
                    val errorOutput = stderr.await()
                    if (process.exitValue() != 0) {
                        Log.w(TAG, "Knowledge model runner failed: ${errorOutput.take(LOG_PREVIEW_CHARS)}")
                        null
                    } else {
                        parseDecision(generated, task, source.sourceText).also { parsed ->
                            if (parsed == null) {
                                Log.w(
                                    TAG,
                                    "Rejected model output: ${generated.take(LOG_PREVIEW_CHARS)}",
                                )
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                Log.w(TAG, "Knowledge model inference failed", error)
                null
            }
        }
    }

    private fun DeenlySourceEnvelope.toPrompt(task: DeenlyKnowledgeTask): String = buildString {
        if (task == DeenlyKnowledgeTask.ANSWER) {
            appendLine("Task: answer the user's question.")
            appendLine("Collection: $collection")
            appendLine("Reference: $reference")
            appendLine("Topic: $topic")
            userQuestion
                ?.takeIf(String::isNotBlank)
                ?.let { appendLine("User question: ${it.take(MAX_USER_QUESTION_CHARS)}") }
            appendLine("Source text:")
            // The answering model was trained on short passages: a 0.5B copies
            // exact spans from ~600 chars reliably but degenerates on longer
            // ones. Retrieval still selects the full source; the prompt keeps
            // only the opening the model can quote from.
            append(sourceText.take(ANSWER_MAX_SOURCE_CHARS))
        } else {
            appendLine("Task: create a ${task.wireName} item.")
            appendLine("Collection: $collection")
            appendLine("Reference: $reference")
            appendLine("Topic: $topic")
            appSituation
                ?.takeIf(String::isNotBlank)
                ?.let { appendLine("App situation: ${it.take(MAX_APP_SITUATION_CHARS)}") }
            appendLine("Source text:")
            append(sourceText.take(MAX_SOURCE_CHARS))
        }
    }

    private val DeenlyKnowledgeTask.outputSchema: String
        get() = when (this) {
            DeenlyKnowledgeTask.KNOWLEDGE -> KNOWLEDGE_JSON_SCHEMA
            DeenlyKnowledgeTask.QUESTION -> QUESTION_JSON_SCHEMA
            DeenlyKnowledgeTask.ANSWER -> ANSWER_JSON_SCHEMA
        }

    companion object {
        // deenly-question-v5: trained on Qwen2.5-1.5B with the translated
        // Dorar bank and grounded corpus. Generates richer questions with
        // contextual evidence instead of bare answer copies.
        const val MODEL_FILE_NAME = "deenly-question-v5-q4_k_m.gguf"
        const val MODEL_SIZE_BYTES = 986_048_224L
        const val MODEL_SHA256 =
            "80535d590f1a690730530905fc83667fd2a4db72b7ba683afadc437107024455"

        // The answering model (deenly-knowledge-v3, Qwen2.5-1.5B). Every generated answer and
        // evidence quote is validated as an exact span of the verified source
        // before display; failures fall back to the verbatim source card.
        const val ANSWER_MODEL_FILE_NAME = "deenly-knowledge-v3-q4_k_m.gguf"
        const val ANSWER_MODEL_SIZE_BYTES = 986_048_224L
        const val ANSWER_MODEL_SHA256 =
            "d6e49d4aadca50b815a2a054c6facd22dccad9871561f146cb73dd6b5204aeee"

        /**
         * Whether the GGUF required by [task] has been downloaded and is complete.
         * UI uses this to offer the CDN download instead of silently falling back
         * to the deterministic content bank; inference still re-verifies the SHA.
         */
        fun isModelDownloaded(context: Context, task: DeenlyKnowledgeTask): Boolean {
            // Called from composition on card-heavy screens; stat the ~1 GB
            // file at most once per cache window instead of every recomposition.
            val now = SystemClock.elapsedRealtime()
            val cached = cachedModelDownloaded[task]
            if (cached != null && now - cached.first < MODEL_DOWNLOAD_CACHE_TTL_MS) {
                return cached.second
            }
            val file = File(
                context.applicationContext.filesDir,
                "cdn_assets/models/now_nudge/${task.modelFileName}",
            )
            val downloaded = file.isFile && file.length() == task.modelSizeBytes
            cachedModelDownloaded[task] = now to downloaded
            return downloaded
        }

        private val cachedModelDownloaded = mutableMapOf<DeenlyKnowledgeTask, Pair<Long, Boolean>>()

        private const val MODEL_DOWNLOAD_CACHE_TTL_MS = 30_000L

        private val DeenlyKnowledgeTask.modelFileName: String
            get() = when (this) {
                DeenlyKnowledgeTask.ANSWER -> ANSWER_MODEL_FILE_NAME
                else -> MODEL_FILE_NAME
            }

        private val DeenlyKnowledgeTask.modelSizeBytes: Long
            get() = when (this) {
                DeenlyKnowledgeTask.ANSWER -> ANSWER_MODEL_SIZE_BYTES
                else -> MODEL_SIZE_BYTES
            }

        private val DeenlyKnowledgeTask.modelSha256: String
            get() = when (this) {
                DeenlyKnowledgeTask.ANSWER -> ANSWER_MODEL_SHA256
                else -> MODEL_SHA256
            }

        private val DeenlyKnowledgeTask.systemPrompt: String
            get() = when (this) {
                DeenlyKnowledgeTask.ANSWER -> ANSWER_SYSTEM_PROMPT
                else -> SYSTEM_PROMPT
            }

        private const val RUNNER_FILE_NAME = "libdeenly_completion.so"
        private const val INFERENCE_TIMEOUT_SECONDS = 120L
        private const val PROCESS_SHUTDOWN_TIMEOUT_SECONDS = 5L
        private const val MAX_SOURCE_CHARS = 1_400
        private const val ANSWER_MAX_SOURCE_CHARS = 600
        private const val MAX_APP_SITUATION_CHARS = 320
        private const val MAX_USER_QUESTION_CHARS = 240
        private const val MAX_TITLE_CHARS = 84
        private const val MAX_QUESTION_CHARS = 180
        private const val MAX_ANSWER_CHARS = 96
        private const val MAX_EVIDENCE_CHARS = 420
        private const val ANSWER_EVIDENCE_MIN_CHARS = 110
        private val ANSWER_SENTENCE_REGEX =
            Regex("[^.!?]+[.!?]+(?=\\s|[\"'\\u201d]|$)|[^.!?]+\$")
        private const val MAX_OPTION_CHARS = 96
        private const val LOG_PREVIEW_CHARS = 240
        private const val TAG = "DeenlyKnowledgeModel"
        private const val SYSTEM_PROMPT = """You create one grounded Islamic learning item from the supplied source.
Use only the supplied source, never memory. Return one minified JSON object and nothing else.
A knowledge object has exactly contentType and title. For a question, create a natural,
self-contained global knowledge question that does not say "this narration", "this hadith",
"this ayah", "this verse", "this passage", "according to", or ask where the text came from.
A question object has exactly contentType, questionKind, question, answer, evidence, and options.
questionKind must be one of person, place, food, color, action, number, description, teaching,
outcome, object, or time. answer must be a short exact contiguous span copied from Source text.
evidence must be one exact contiguous span copied from Source text and must contain answer.
options must contain exactly four short unique strings including answer exactly once. Distractors
must not contradict anything stated in the source. Never output Arabic, IDs, citations, rulings,
or claims unsupported by the source. Prefer a simple explicit detail over interpretation."""
        private const val ANSWER_SYSTEM_PROMPT = """You answer the user's question from the supplied source.
Use only the supplied source, never memory. Return one minified JSON object and nothing else.
When the supplied source answers the question, contentType is answer, answer is a short exact
contiguous span copied from Source text, and evidence is one exact contiguous span copied from
Source text that contains answer. When the supplied source does not answer the question,
contentType is unanswered and answer and evidence are empty strings. Never output Arabic, IDs,
citations, rulings, or claims unsupported by the source."""
        private const val KNOWLEDGE_JSON_SCHEMA = """{"type":"object","properties":{"contentType":{"const":"knowledge"},"title":{"type":"string","minLength":1,"maxLength":84}},"required":["contentType","title"],"additionalProperties":false}"""
        private const val QUESTION_JSON_SCHEMA = """{"type":"object","properties":{"contentType":{"const":"question"},"questionKind":{"enum":["person","place","food","color","action","number","description","teaching","outcome","object","time"]},"question":{"type":"string","minLength":8,"maxLength":180},"answer":{"type":"string","minLength":1,"maxLength":96},"evidence":{"type":"string","minLength":1,"maxLength":420},"options":{"type":"array","minItems":4,"maxItems":4,"items":{"type":"string","minLength":1,"maxLength":96}}},"required":["contentType","questionKind","question","answer","evidence","options"],"additionalProperties":false}"""
        private const val ANSWER_JSON_SCHEMA = """{"type":"object","properties":{"contentType":{"enum":["answer","unanswered"]},"answer":{"type":"string","maxLength":96},"evidence":{"type":"string","maxLength":420}},"required":["contentType","answer","evidence"],"additionalProperties":false}"""
        private val inferenceMutex = Mutex()
        private val prayerSelectionMutex = Mutex()
        private val prayerSelections = linkedMapOf<String, String>()

        internal fun parseDecision(
            rawOutput: String,
            expectedTask: DeenlyKnowledgeTask,
            sourceText: String? = null,
        ): DeenlyKnowledgeDecision? {
            val start = rawOutput.indexOf('{')
            val end = rawOutput.lastIndexOf('}')
            if (start < 0 || end < start) return null
            val output = runCatching {
                Json.parseToJsonElement(rawOutput.substring(start, end + 1)) as? JsonObject
            }.getOrNull() ?: return null
            val contentType = (output["contentType"] as? JsonPrimitive)?.content
            if (contentType != expectedTask.wireName &&
                !(expectedTask == DeenlyKnowledgeTask.ANSWER && contentType == "unanswered")
            ) return null

            return when (expectedTask) {
                DeenlyKnowledgeTask.KNOWLEDGE -> {
                    if (output.keys != setOf("contentType", "title")) return null
                    val title = output["title"]?.jsonPrimitive?.content?.trim().orEmpty()
                    val meaningfulCharacters = title.filter { character -> character.isLetterOrDigit() }
                    title.takeIf {
                        it.isNotEmpty() &&
                            it.length <= MAX_TITLE_CHARS &&
                            it.none(Char::isISOControl) &&
                            meaningfulCharacters.length >= 3 &&
                            meaningfulCharacters.map { it.lowercaseChar() }.distinct().size >= 2
                    }?.let(DeenlyKnowledgeDecision::Knowledge)
                }

                DeenlyKnowledgeTask.QUESTION -> {
                    val questionKind = output["questionKind"]?.jsonPrimitive?.content ?: return null
                    if (questionKind == "source_location") {
                        if (output.keys != setOf("contentType", "questionKind")) return null
                        return DeenlyKnowledgeDecision.SourceLocationQuestion
                    }
                    if (questionKind !in ALLOWED_QUESTION_KINDS) return null
                    if (
                        output.keys != setOf(
                            "contentType",
                            "questionKind",
                            "question",
                            "answer",
                            "evidence",
                            "options",
                        )
                    ) return null
                    val verifiedSource = sourceText ?: return null
                    val question = output["question"]?.jsonPrimitive?.content?.trim().orEmpty()
                    val answer = output["answer"]?.jsonPrimitive?.content?.trim().orEmpty()
                    val evidence = output["evidence"]?.jsonPrimitive?.content?.trim().orEmpty()
                    val options = (output["options"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
                        ?: return null
                    val normalizedOptions = normalizeOptions(
                        question = question,
                        answer = answer,
                        options = options,
                    ) ?: return null
                    val generatedText = listOf(question, answer, evidence) + options
                    if (
                        question.isEmpty() || question.length > MAX_QUESTION_CHARS ||
                        !question.endsWith('?') || question.containsForbiddenScopePhrase() ||
                        answer.isEmpty() || answer.length > MAX_ANSWER_CHARS ||
                        evidence.isEmpty() || evidence.length > MAX_EVIDENCE_CHARS ||
                        answer !in verifiedSource || evidence !in verifiedSource || answer !in evidence ||
                        generatedText.any { it.containsArabic() } ||
                        generatedText.any { it.containsPlaceholderTag() } ||
                        generatedText.any { text -> text.any(Char::isISOControl) }
                    ) return null
                    DeenlyKnowledgeDecision.GroundedQuestion(
                        questionKind = questionKind,
                        question = question,
                        answer = answer,
                        evidence = evidence,
                        options = normalizedOptions,
                    )
                }

                DeenlyKnowledgeTask.ANSWER -> {
                    if (output.keys != setOf("contentType", "answer", "evidence")) return null
                    val modelAnswer = output["answer"]?.jsonPrimitive?.content?.trim().orEmpty()
                    val modelEvidence = output["evidence"]?.jsonPrimitive?.content?.trim().orEmpty()
                    if (contentType == "unanswered") {
                        return if (modelAnswer.isEmpty() && modelEvidence.isEmpty()) {
                            DeenlyKnowledgeDecision.Unanswered
                        } else {
                            null
                        }
                    }
                    val verifiedSource = sourceText ?: return null
                    // Bounded repair, ANSWER task only: the model's quoting
                    // slips — a lowercase "thareed" for the source's
                    // "Thareed", or a stray leading quote — must not discard
                    // an otherwise exact span. Every repaired value adopts
                    // the source's own characters, so grounding is preserved.
                    val answer = if (
                        modelAnswer.isNotEmpty() &&
                        modelAnswer.length <= MAX_ANSWER_CHARS &&
                        modelAnswer !in verifiedSource
                    ) {
                        verifiedSource.locateCaseInsensitiveSpan(modelAnswer) ?: return null
                    } else {
                        modelAnswer
                    }
                    if (answer.isEmpty() || answer.length > MAX_ANSWER_CHARS) return null
                    val evidence = when {
                        modelEvidence.isNotEmpty() &&
                            modelEvidence.length <= MAX_EVIDENCE_CHARS &&
                            modelEvidence in verifiedSource &&
                            answer in modelEvidence -> modelEvidence

                        else -> verifiedSource.buildEvidenceAround(answer) ?: return null
                    }
                    if (
                        evidence.isEmpty() || evidence.length > MAX_EVIDENCE_CHARS ||
                        evidence !in verifiedSource || answer !in evidence ||
                        listOf(answer, evidence).any { it.containsArabic() } ||
                        listOf(answer, evidence).any { text -> text.any(Char::isISOControl) }
                    ) return null
                    DeenlyKnowledgeDecision.GroundedAnswer(
                        answer = answer,
                        evidence = evidence,
                    )
                }
            }
        }

        /**
         * Repairs only the bounded multiple-choice shape. The correct answer has already been
         * verified as an exact source span; generated evidence and question wording are never
         * repaired. If three unique distractors do not survive validation, the whole output is
         * rejected and the caller uses its deterministic fallback.
         */
        private fun normalizeOptions(
            question: String,
            answer: String,
            options: List<String>,
        ): List<String>? {
            if (
                options.size != 4 ||
                options.any { it.isEmpty() || it.length > MAX_OPTION_CHARS }
            ) return null
            if (
                options.distinctBy(String::lowercase).size == 4 &&
                options.count { it == answer } == 1
            ) return options

            val distractors = options
                .filterNot { it.equals(answer, ignoreCase = true) }
                .distinctBy(String::lowercase)
            if (distractors.size < 3) return null
            return distractors.take(3).toMutableList().apply {
                add(Math.floorMod(question.hashCode(), 4), answer)
            }
        }

        private val ALLOWED_QUESTION_KINDS = setOf(
            "person",
            "place",
            "food",
            "color",
            "action",
            "number",
            "description",
            "teaching",
            "outcome",
            "object",
            "time",
        )

        private val FORBIDDEN_SCOPE_PHRASES = listOf(
            "this narration",
            "this hadith",
            "this ayah",
            "this verse",
            "this passage",
            "this text",
            "according to",
            "which source",
            "which collection",
            "which surah contains",
        )

        private fun String.containsForbiddenScopePhrase(): Boolean {
            val normalized = lowercase()
            return FORBIDDEN_SCOPE_PHRASES.any(normalized::contains)
        }

        private fun String.containsArabic(): Boolean = any { character ->
            character.code in 0x0600..0x06FF ||
                character.code in 0x0750..0x077F ||
                character.code in 0x08A0..0x08FF
        }

        /** The source's exact characters for a case-slipped model quote, or null. */
        private fun String.locateCaseInsensitiveSpan(modelQuote: String): String? {
            if (modelQuote.isEmpty()) return null
            val start = lowercase().indexOf(modelQuote.lowercase())
            if (start < 0) return null
            return substring(start, (start + modelQuote.length).coerceAtMost(length))
        }

        /**
         * Deterministic evidence fallback: the shortest sentence containing
         * the answer, cut at a word boundary when it runs past the answer —
         * the same construction the answer-contract dataset trains on. Always
         * an exact contiguous span of the source.
         */
        private fun String.buildEvidenceAround(answer: String): String? {
            if (answer.isEmpty() || answer !in this) return null
            var best: String? = null
            for (match in ANSWER_SENTENCE_REGEX.findAll(this)) {
                val sentence = match.value.trim()
                if (answer !in sentence) continue
                if (best == null || sentence.length < best.length) best = sentence
            }
            val sentence = best ?: return null
            val answerEnd = sentence.indexOf(answer) + answer.length
            val cut = minOf(sentence.length, maxOf(answerEnd, ANSWER_EVIDENCE_MIN_CHARS))
            var evidence = sentence.take(cut)
            if (cut < sentence.length) {
                evidence = evidence.trimEnd(' ', ',', ';', ':')
                val boundary = evidence.lastIndexOf(' ')
                if (boundary >= answerEnd) evidence = evidence.take(boundary)
            }
            if (answer !in evidence) return null
            return evidence.takeIf { it.length <= MAX_EVIDENCE_CHARS }
        }

        // The v4 topic mode occasionally leaks training placeholders such as
        // "<opt1>" or "<English>" into otherwise valid JSON; those strings must
        // never reach a quiz card, so treat them like any other bad output.
        private fun String.containsPlaceholderTag(): Boolean =
            contains("<opt", ignoreCase = true) || contains("<english", ignoreCase = true)

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

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
            return digest.digest().toHex()
        }

        private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }
    }
}
