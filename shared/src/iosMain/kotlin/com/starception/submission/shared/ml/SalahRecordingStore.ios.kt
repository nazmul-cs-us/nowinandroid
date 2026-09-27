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

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile

private const val NS_DOCUMENT_DIRECTORY = 9uL

private fun recordingsDirectory(): String {
    val documents = NSSearchPathForDirectoriesInDomains(
        directory = NS_DOCUMENT_DIRECTORY,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).firstOrNull() as? String ?: error("Documents directory unavailable")
    return "$documents/salah_recordings"
}

private fun readText(path: String): String? {
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    return NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
}

@OptIn(ExperimentalForeignApi::class)
actual class SalahRecordingStore actual constructor() {
    private val fileManager = NSFileManager.defaultManager
    private var activePath: String? = null
    private var buffer = StringBuilder()

    actual fun startSession(prefix: String, sessionId: String): String? {
        stopSession(trimLastMs = 0)
        val directory = recordingsDirectory()
        if (!fileManager.fileExistsAtPath(directory)) {
            fileManager.createDirectoryAtPath(
                directory,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
        val timestamp = (NSDate().timeIntervalSince1970).toLong()
        val name = "${prefix}${timestamp}_$sessionId.jsonl"
        val path = "$directory/$name"
        return if (fileManager.createFileAtPath(path, contents = null, attributes = null)) {
            activePath = path
            buffer = StringBuilder()
            name
        } else {
            null
        }
    }

    actual fun appendSample(sample: SalahDataSample): Boolean {
        val path = activePath ?: return false
        buffer.append(sample.toJson())
        buffer.append('\n')
        // Flush on a comfortable cadence: ~64KB ≈ 10 s of capture at 10 windows/s.
        if (buffer.length > 64_000) {
            if (!flushBuffer()) return false
        }
        return fileManager.fileExistsAtPath(path)
    }

    actual fun stopSession(trimLastMs: Long): String? {
        val path = activePath ?: return null
        activePath = null
        val content = buildString {
            append(readText(path).orEmpty())
            append(buffer)
        }
        buffer = StringBuilder()
        val finalContent = if (trimLastMs > 0) {
            val cutoff = (NSDate().timeIntervalSince1970 * 1000).toLong() - trimLastMs
            content.lineSequence()
                .filter { line -> line.isBlank() || line.timestampOf() <= cutoff }
                .joinToString("\n")
        } else {
            content
        }
        val payload = NSString.create(string = finalContent)
            .dataUsingEncoding(NSUTF8StringEncoding) ?: return null
        if (!payload.writeToFile(path, atomically = true)) return null
        val finalName = renameToDescriptive(path)
        return finalName
    }

    actual fun sessions(): List<SalahSessionInfo> {
        val directory = recordingsDirectory()
        val names = (fileManager.contentsOfDirectoryAtPath(directory, error = null) as? List<String>)
            ?.filter { it.endsWith(".jsonl") }
            ?: return emptyList()
        return names
            .mapNotNull { name -> sessionInfoFor("$directory/$name", name) }
            .sortedByDescending { it.lastModifiedMs }
    }

    actual fun deleteSession(fileName: String) {
        fileManager.removeItemAtPath("${recordingsDirectory()}/$fileName", error = null)
    }

    actual fun deleteAllSessions() {
        sessions().forEach { deleteSession(it.fileName) }
    }

    actual fun totalSizeKb(): Long = sessions().sumOf { it.sizeKb }

    private fun sessionInfoFor(path: String, name: String): SalahSessionInfo? {
        if (!fileManager.fileExistsAtPath(path)) return null
        val attributes = fileManager.attributesOfItemAtPath(path, error = null)
        val size = (attributes?.get("NSFileSize") as? NSNumber)?.longValue ?: 0L
        val modified = (
            (attributes?.get("NSFileModificationDate") as? NSDate)
                ?.timeIntervalSince1970?.toLong()
            ) ?: 0L
        val content = readText(path) ?: return SalahSessionInfo(
            fileName = name,
            sizeKb = size / 1024,
            sampleCount = 0,
            postureCounts = emptyMap(),
            lastModifiedMs = modified,
        )
        val counts = mutableMapOf<String, Int>()
        var total = 0
        for (line in content.lineSequence()) {
            if (line.isBlank()) continue
            val posture = line.postureOf()
            if (posture.isNotEmpty()) {
                counts[posture] = (counts[posture] ?: 0) + 1
                total++
            }
        }
        return SalahSessionInfo(
            fileName = name,
            sizeKb = size / 1024,
            sampleCount = total,
            postureCounts = counts,
            lastModifiedMs = modified,
        )
    }

    private fun flushBuffer(): Boolean {
        val path = activePath ?: return false
        val content = buildString {
            append(readText(path).orEmpty())
            append(buffer)
        }
        buffer = StringBuilder()
        val payload = NSString.create(string = content)
            .dataUsingEncoding(NSUTF8StringEncoding) ?: return false
        return payload.writeToFile(path, atomically = true)
    }

    /**
     * Android's SalahRecordingName semantics: the descriptor names what the
     * file actually holds — a single posture slug, `full` when every prayer
     * posture is present, otherwise `partial<N>`; live recordings keep their
     * names (their labels are model output, not ground truth).
     */
    private fun renameToDescriptive(path: String): String {
        val oldName = path.substringAfterLast('/')
        val prefix = FILE_PREFIXES.firstOrNull { oldName.startsWith(it) } ?: return oldName
        val content = readText(path) ?: return oldName
        val postures = mutableSetOf<String>()
        var rowCount = 0
        for (line in content.lineSequence()) {
            if (line.isBlank()) continue
            rowCount++
            line.postureOf().takeIf { it.isNotEmpty() }?.let(postures::add)
        }
        if (oldName.startsWith(LIVE_FILE_PREFIX)) return oldName
        val descriptor = when {
            rowCount == 0 -> "empty"
            postures.size == 1 -> postureSlug(
                SalahPosture.entries.firstOrNull { it.name == postures.first() }
                    ?: return oldName,
            )
            else -> {
                val prayerPostures = postures.count { postureName ->
                    SalahPosture.entries.firstOrNull { it.name == postureName }?.isPrayerPosture == true
                }
                if (prayerPostures == SalahPosture.prayerPostures.size) "full" else "partial$prayerPostures"
            }
        }
        // The tail sits after the descriptor slot: `<prefix><descriptor>_<rest>`.
        val rest = oldName.removePrefix(prefix)
        val tail = if (rest.take(8).all(Char::isDigit)) rest else rest.substringAfter('_', "")
        val newName = "$prefix${descriptor}_$tail"
        if (newName == oldName) return oldName
        val newPath = "${recordingsDirectory()}/$newName"
        if (fileManager.fileExistsAtPath(newPath)) return oldName
        return if (fileManager.moveItemAtPath(path, toPath = newPath, error = null)) newName else oldName
    }
}

private val FILE_PREFIXES = listOf(
    REVIEWED_FILE_PREFIX,
    GUIDED_FILE_PREFIX,
    LIVE_FILE_PREFIX,
    MANUAL_FILE_PREFIX,
)

internal const val LIVE_FILE_PREFIX = "salah_live_"
internal const val GUIDED_FILE_PREFIX = "salah_guided_"
internal const val REVIEWED_FILE_PREFIX = "salah_reviewed_"
internal const val MANUAL_FILE_PREFIX = "salah_data_"

private fun String.timestampOf(): Long =
    substringAfter("\"timestamp\":").substringBefore(",").toLongOrNull() ?: Long.MAX_VALUE

private fun String.postureOf(): String =
    substringAfter("\"posture\":\"").substringBefore("\",")
