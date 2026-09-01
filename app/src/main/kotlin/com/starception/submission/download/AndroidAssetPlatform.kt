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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
        val temporary = File(root, ".temporary/$cdnKey.tmp")
        temporary.parentFile?.mkdirs()
        repeat(3) { attempt ->
            val completed = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use false
                    val body = response.body ?: return@use false
                    val total = body.contentLength()
                    var downloaded = 0L
                    FileOutputStream(temporary).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                downloaded += count
                                onProgress(downloaded, total)
                            }
                        }
                    }
                    true
                }
            }.getOrDefault(false)
            if (completed) return@withContext temporary.absolutePath
            temporary.delete()
            if (attempt < 2) delay(1_000L * (attempt + 1))
        }
        null
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
        val target = file(cdnKey)
        target.parentFile?.mkdirs()
        target.delete()
        check(File(temporaryPath).renameTo(target))
        return target.absolutePath
    }

    override suspend fun deleteFile(absolutePath: String): Boolean = File(absolutePath).delete()

    override suspend fun deleteCachedAsset(cdnKey: String): Boolean = file(cdnKey).delete()

    private fun file(cdnKey: String): File {
        val normalized = cdnKey.trimStart('/')
        require(normalized.isNotEmpty() && normalized.split('/').none { it == "." || it == ".." })
        return File(root, normalized)
    }

    private fun bundledPath(cdnKey: String): String = when {
        cdnKey.startsWith("databases/quran/") -> "databases/${cdnKey.substringAfterLast('/')}"
        cdnKey.startsWith("json/") -> cdnKey.substringAfter("json/")
        cdnKey.startsWith("models/") -> cdnKey.substringAfter("models/")
        else -> cdnKey
    }
}
