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

package com.starception.submission.download

import android.content.Context
import com.starception.submission.core.assetcache.AssetPlatform
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class AndroidAssetPlatform(
    private val context: Context,
    private val client: OkHttpClient,
) : AssetPlatform {
    private val root = File(context.filesDir, "cdn_assets").apply { mkdirs() }

    override suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                response.body?.string().takeIf { response.isSuccessful }
            }
        }.getOrNull()
    }

    override suspend fun readCachedText(relativePath: String): String? =
        file(relativePath).takeIf(File::isFile)?.readText()

    override suspend fun writeCachedText(relativePath: String, content: String) {
        val target = file(relativePath)
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(content)
        check(temporary.renameTo(target))
    }

    override suspend fun readBundledText(relativePath: String): String? = runCatching {
        context.assets.open(relativePath).bufferedReader().use { it.readText() }
    }.getOrNull()

    override suspend fun cachedAssetPath(cdnKey: String): String? =
        file(cdnKey).takeIf { it.isFile && it.length() > 0L }?.absolutePath

    override suspend fun bundledAssetPath(cdnKey: String): String? {
        val bundledPath = bundledPath(cdnKey)
        val destination = File(context.filesDir, "bundled_asset_fallback/$cdnKey")
        if (destination.isFile && destination.length() > 0L) return destination.absolutePath
        return runCatching {
            destination.parentFile?.mkdirs()
            context.assets.open(bundledPath).use { input ->
                destination.outputStream().use(input::copyTo)
            }
            destination.absolutePath.takeIf { destination.length() > 0L }
        }.getOrNull()
    }

    override suspend fun downloadToTemporaryFile(
        url: String,
        cdnKey: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): String? = withContext(Dispatchers.IO) {
        val temporary = partialFile(root, cdnKey)
        val metadata = partialMetadataFile(temporary)
        temporary.parentFile?.mkdirs()
        repeat(3) { attempt ->
            var restarted = false
            while (true) {
                val result = try {
                    downloadOnce(url, temporary, metadata, onProgress)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    DownloadResult.RETRY
                }

                if (result == DownloadResult.COMPLETE) {
                    return@withContext temporary.absolutePath
                }
                if (result != DownloadResult.RESTART || restarted) break

                deletePartial(temporary)
                restarted = true
            }
            if (attempt < 2) delay(1_000L * (attempt + 1))
        }
        null
    }

    private fun downloadOnce(
        url: String,
        temporary: File,
        metadata: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): DownloadResult {
        val offset = temporary.length().takeIf { temporary.isFile } ?: 0L
        val savedEtag = runCatching { metadata.takeIf(File::isFile)?.readText() }
            .getOrNull()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val request = Request.Builder().url(url).apply {
            if (offset > 0L) {
                header("Range", "bytes=$offset-")
                savedEtag
                    ?.takeUnless { it.startsWith("W/", ignoreCase = true) }
                    ?.let { header("If-Range", it) }
            }
        }.build()

        return client.newCall(request).execute().use { response ->
            when (response.code) {
                200 -> writeResponse(
                    response = response,
                    temporary = temporary,
                    metadata = metadata,
                    offset = 0L,
                    append = false,
                    totalBytes = response.body?.contentLength() ?: -1L,
                    expectedResponseBytes = response.body?.contentLength() ?: -1L,
                    onProgress = onProgress,
                )
                206 -> resumeResponse(
                    response = response,
                    temporary = temporary,
                    metadata = metadata,
                    offset = offset,
                    savedEtag = savedEtag,
                    onProgress = onProgress,
                )
                416 -> handleRangeNotSatisfiable(response, temporary, offset, onProgress)
                else -> DownloadResult.RETRY
            }
        }
    }

    private fun resumeResponse(
        response: Response,
        temporary: File,
        metadata: File,
        offset: Long,
        savedEtag: String?,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): DownloadResult {
        val range = parseContentRange(response.header("Content-Range"))
            ?: return DownloadResult.RESTART
        val responseLength = response.body?.contentLength() ?: -1L
        val responseEtag = response.header("ETag")
        val validBounds = range.lastByte >= range.firstByte &&
            (range.totalBytes == null || range.lastByte < range.totalBytes) &&
            range.lastByte - range.firstByte < Long.MAX_VALUE
        val rangeLength = if (validBounds) range.lastByte - range.firstByte + 1L else -1L
        val validRange = range.firstByte == offset &&
            validBounds &&
            (responseLength < 0L || responseLength == rangeLength) &&
            (savedEtag == null || responseEtag == null || savedEtag == responseEtag)
        if (!validRange) return DownloadResult.RESTART

        val totalBytes = range.totalBytes ?: -1L
        return writeResponse(
            response = response,
            temporary = temporary,
            metadata = metadata,
            offset = offset,
            append = offset > 0L,
            totalBytes = totalBytes,
            expectedResponseBytes = rangeLength,
            onProgress = onProgress,
        ).let { result ->
            if (
                result == DownloadResult.COMPLETE &&
                range.totalBytes != null &&
                temporary.length() < range.totalBytes
            ) {
                DownloadResult.RETRY
            } else {
                result
            }
        }
    }

    private fun writeResponse(
        response: Response,
        temporary: File,
        metadata: File,
        offset: Long,
        append: Boolean,
        totalBytes: Long,
        expectedResponseBytes: Long,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): DownloadResult {
        val body = response.body ?: return DownloadResult.RETRY
        if (offset > 0L) onProgress(offset, totalBytes)

        var responseBytes = 0L
        FileOutputStream(temporary, append).use { output ->
            val responseEtag = response.header("ETag")
            if (responseEtag != null) {
                metadata.writeText(responseEtag)
            } else if (!append) {
                metadata.delete()
            }
            body.byteStream().use { input ->
                val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    responseBytes += count
                    onProgress(offset + responseBytes, totalBytes)
                }
            }
        }
        return if (expectedResponseBytes < 0L || responseBytes == expectedResponseBytes) {
            DownloadResult.COMPLETE
        } else {
            DownloadResult.RETRY
        }
    }

    private fun handleRangeNotSatisfiable(
        response: Response,
        temporary: File,
        offset: Long,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): DownloadResult {
        val totalBytes = parseUnsatisfiedContentRange(response.header("Content-Range"))
        return if (
            offset > 0L &&
            totalBytes != null &&
            totalBytes == offset &&
            temporary.length() == offset
        ) {
            onProgress(offset, totalBytes)
            DownloadResult.COMPLETE
        } else if (offset > 0L) {
            DownloadResult.RESTART
        } else {
            DownloadResult.RETRY
        }
    }

    override suspend fun fileSize(absolutePath: String): Long? =
        File(absolutePath).takeIf(File::isFile)?.length()

    override suspend fun sha256(absolutePath: String): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        File(absolutePath).inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    override suspend fun commitDownloadedAsset(temporaryPath: String, cdnKey: String): String {
        val temporary = File(temporaryPath)
        val target = file(cdnKey)
        target.parentFile?.mkdirs()
        target.delete()
        check(temporary.renameTo(target))
        partialMetadataFile(temporary).delete()
        return target.absolutePath
    }

    override suspend fun deleteFile(absolutePath: String): Boolean = deletePartial(File(absolutePath))

    override suspend fun deleteCachedAsset(cdnKey: String): Boolean {
        val asset = file(cdnKey)
        val deletedAsset = asset.delete()
        val deletedPartial = deletePartial(partialFile(root, cdnKey))
        return (deletedAsset || deletedPartial) && !asset.exists()
    }

    private fun file(cdnKey: String): File {
        return File(root, normalizeCdnKey(cdnKey))
    }

    private fun bundledPath(cdnKey: String): String = when {
        cdnKey.startsWith("databases/quran/") -> "databases/${cdnKey.substringAfterLast('/')}"
        cdnKey.startsWith("json/") -> cdnKey.substringAfter("json/")
        cdnKey.startsWith("models/") -> cdnKey.substringAfter("models/")
        else -> cdnKey
    }

    private data class ContentRange(
        val firstByte: Long,
        val lastByte: Long,
        val totalBytes: Long?,
    )

    private enum class DownloadResult {
        COMPLETE,
        RETRY,
        RESTART,
    }

    companion object {
        internal const val PARTIAL_METADATA_SUFFIX = ".etag"
        private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
        private val CONTENT_RANGE = Regex(
            """bytes (\d+)-(\d+)/(\d+|\*)""",
            RegexOption.IGNORE_CASE,
        )
        private val UNSATISFIED_CONTENT_RANGE = Regex(
            """bytes \*/(\d+)""",
            RegexOption.IGNORE_CASE,
        )

        internal fun partialFile(root: File, cdnKey: String): File =
            File(root, ".temporary/${normalizeCdnKey(cdnKey)}.tmp")

        internal fun partialMetadataFile(partial: File): File =
            File(partial.parentFile, partial.name + PARTIAL_METADATA_SUFFIX)

        internal fun deletePartial(partial: File): Boolean {
            val deletedPartial = partial.delete()
            val deletedMetadata = partialMetadataFile(partial).delete()
            return deletedPartial || deletedMetadata
        }

        private fun normalizeCdnKey(cdnKey: String): String {
            val normalized = cdnKey.trimStart('/')
            require(
                normalized.isNotEmpty() && normalized.split('/').none { it == "." || it == ".." },
            )
            return normalized
        }

        private fun parseContentRange(value: String?): ContentRange? {
            val match = value?.trim()?.let(CONTENT_RANGE::matchEntire) ?: return null
            return runCatching {
                ContentRange(
                    firstByte = match.groupValues[1].toLong(),
                    lastByte = match.groupValues[2].toLong(),
                    totalBytes = match.groupValues[3].takeUnless { it == "*" }?.toLong(),
                )
            }.getOrNull()
        }

        private fun parseUnsatisfiedContentRange(value: String?): Long? {
            val match = value?.trim()?.let(UNSATISFIED_CONTENT_RANGE::matchEntire) ?: return null
            return match.groupValues[1].toLongOrNull()
        }
    }
}
