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

package com.starception.submission.core.assetcache

/**
 * Filesystem and network primitives required by [CloudAssetRepository].
 *
 * Paths returned by this interface are absolute, platform-readable paths. Implementations are
 * responsible for confining CDN keys to their cache root and for making [commitDownloadedAsset]
 * atomic where the platform supports it.
 */
interface AssetPlatform {
    suspend fun fetchText(url: String): String?

    suspend fun readCachedText(relativePath: String): String?

    suspend fun writeCachedText(relativePath: String, content: String)

    suspend fun readBundledText(relativePath: String): String?

    suspend fun cachedAssetPath(cdnKey: String): String?

    suspend fun bundledAssetPath(cdnKey: String): String?

    /** Downloads to a temporary file and returns its absolute path. */
    suspend fun downloadToTemporaryFile(
        url: String,
        cdnKey: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): String?

    suspend fun fileSize(absolutePath: String): Long?

    suspend fun sha256(absolutePath: String): String?

    /** Moves a validated temporary file into the asset cache and returns its absolute path. */
    suspend fun commitDownloadedAsset(temporaryPath: String, cdnKey: String): String

    suspend fun deleteFile(absolutePath: String): Boolean

    suspend fun deleteCachedAsset(cdnKey: String): Boolean
}
