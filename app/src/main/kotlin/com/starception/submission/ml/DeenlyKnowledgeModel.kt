/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.ml

import android.content.Context
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

enum class DeenlyKnowledgeTask(val wireName: String) {
    KNOWLEDGE("knowledge"),
    QUESTION("question"),
}

data class DeenlySourceEnvelope(
    val collection: String,
    val reference: String,
    val topic: String,
    /** Bounded, non-sensitive app state used to make the title timely. */
    val appSituation: String? = null,
    /** Exact database text. Canonical Arabic is intentionally not accepted by this API. */
    val sourceText: String,
    val sourceTextSha256: String,
)

sealed interface DeenlyKnowledgeDecision {
    data class Knowledge(val title: String) : DeenlyKnowledgeDecision
    data object SourceLocationQuestion : DeenlyKnowledgeDecision
}

/**
 * Runs the reviewed v1 GGUF fully on device.
 *
 * The model may only choose a bounded content shape and, for knowledge cards, a title. IDs,
 * citations, answers, source text, and Quran Arabic remain in the verified database envelope.
 * Invalid output returns null so callers keep using the deterministic content bank.
 */
class DeenlyKnowledgeModel(context: Context) {
    private val appContext = context.applicationContext

    suspend fun generate(
        task: DeenlyKnowledgeTask,
        source: DeenlySourceEnvelope,
    ): DeenlyKnowledgeDecision? = inferenceMutex.withLock {
        withContext(Dispatchers.IO) {
            if (sha256(source.sourceText.toByteArray()) != source.sourceTextSha256.lowercase()) {
                Log.w(TAG, "Rejected source with a mismatched checksum")
                return@withContext null
            }
            val modelFile = File(
                appContext.filesDir,
                "cdn_assets/models/now_nudge/$MODEL_FILE_NAME",
            )
            if (!modelFile.isFile || modelFile.length() != MODEL_SIZE_BYTES) {
                Log.w(TAG, "Knowledge model is missing or has an unexpected size")
                return@withContext null
            }
            if (sha256(modelFile) != MODEL_SHA256) {
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
                    SYSTEM_PROMPT,
                    "--prompt",
                    source.toPrompt(task),
                    "--single-turn",
                    "--predict",
                    "96",
                    "--temp",
                    "0",
                    "--no-display-prompt",
                    "--no-warmup",
                    "--simple-io",
                ).start()
                coroutineScope {
                    val stdout = async { process.inputStream.bufferedReader().readText() }
                    val stderr = async { process.errorStream.bufferedReader().readText() }
                    if (!process.waitFor(INFERENCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                        stdout.await()
                        stderr.await()
                        Log.w(TAG, "Knowledge model inference timed out")
                        return@coroutineScope null
                    }
                    val generated = stdout.await()
                    val errorOutput = stderr.await()
                    if (process.exitValue() != 0) {
                        Log.w(TAG, "Knowledge model runner failed: ${errorOutput.take(LOG_PREVIEW_CHARS)}")
                        null
                    } else {
                        parseDecision(generated, task).also { parsed ->
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

    companion object {
        const val MODEL_FILE_NAME = "deenly-knowledge-v1-q4_k_m.gguf"
        const val MODEL_SIZE_BYTES = 105_454_144L
        const val MODEL_SHA256 = "5682dd0dee0dcb9aa22d04c91d69914c0b3c89e62974f82b6f717f7bd9c6574d"

        private const val RUNNER_FILE_NAME = "libdeenly_completion.so"
        private const val INFERENCE_TIMEOUT_SECONDS = 30L
        private const val MAX_SOURCE_CHARS = 1_400
        private const val MAX_APP_SITUATION_CHARS = 320
        private const val MAX_TITLE_CHARS = 84
        private const val LOG_PREVIEW_CHARS = 240
        private const val TAG = "DeenlyKnowledgeModel"
        private const val SYSTEM_PROMPT = """You create one grounded Islamic learning item from the supplied source.
Use only the source and metadata. Return one minified JSON object and nothing else. A knowledge
object has exactly contentType and title. A question object has exactly contentType and
questionKind. Never output IDs, references, source text, options, answers, or Arabic. The app
builds the final item deterministically from its immutable database."""
        private val inferenceMutex = Mutex()

        internal fun parseDecision(
            rawOutput: String,
            expectedTask: DeenlyKnowledgeTask,
        ): DeenlyKnowledgeDecision? {
            val start = rawOutput.indexOf('{')
            val end = rawOutput.lastIndexOf('}')
            if (start < 0 || end < start) return null
            val output = runCatching {
                Json.parseToJsonElement(rawOutput.substring(start, end + 1)) as? JsonObject
            }.getOrNull() ?: return null
            val contentType = (output["contentType"] as? JsonPrimitive)?.content
            if (contentType != expectedTask.wireName) return null

            return when (expectedTask) {
                DeenlyKnowledgeTask.KNOWLEDGE -> {
                    if (output.keys != setOf("contentType", "title")) return null
                    val title = output["title"]?.jsonPrimitive?.content?.trim().orEmpty()
                    title.takeIf {
                        it.isNotEmpty() &&
                            it.length <= MAX_TITLE_CHARS &&
                            it.none(Char::isISOControl)
                    }?.let(DeenlyKnowledgeDecision::Knowledge)
                }

                DeenlyKnowledgeTask.QUESTION -> {
                    if (output.keys != setOf("contentType", "questionKind")) return null
                    if (output["questionKind"]?.jsonPrimitive?.content != "source_location") {
                        return null
                    }
                    DeenlyKnowledgeDecision.SourceLocationQuestion
                }
            }
        }

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
