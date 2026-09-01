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

package com.starception.submission.shared.assets

import com.starception.submission.core.assetcache.AssetPlatform
import com.starception.submission.core.assetcache.CloudAssetRepository
import platform.Foundation.NSUUID

private const val MANIFEST_URL =
    "https://pub-aeff8de563e549db8ec4ee32f72790e4.r2.dev/manifest.json"

internal class IosAssetPlatform : AssetPlatform {
    private val platform = com.starception.submission.core.assets.AssetPlatform

    override suspend fun fetchText(url: String): String? = platform.fetchText(url)

    override suspend fun readCachedText(relativePath: String): String? =
        platform.cachedText(relativePath)

    override suspend fun writeCachedText(relativePath: String, content: String) {
        check(platform.writeCachedText(relativePath, content))
    }

    override suspend fun readBundledText(relativePath: String): String? =
        platform.bundledText(relativePath)

    override suspend fun cachedAssetPath(cdnKey: String): String? = platform.cachedPath(cdnKey)

    override suspend fun bundledAssetPath(cdnKey: String): String? = platform.bundledPath(cdnKey)

    override suspend fun downloadToTemporaryFile(
        url: String,
        cdnKey: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): String? {
        val temporaryKey = ".temporary/${NSUUID.UUID().UUIDString}/${cdnKey.substringAfterLast('/')}"
        val path = platform.download(url, temporaryKey)
        if (path != null) onProgress(platform.fileSize(path), platform.fileSize(path))
        return path
    }

    override suspend fun fileSize(absolutePath: String): Long? = platform.fileSize(absolutePath)

    override suspend fun sha256(absolutePath: String): String? = platform.fileSha256(absolutePath)

    override suspend fun commitDownloadedAsset(temporaryPath: String, cdnKey: String): String =
        requireNotNull(platform.commit(temporaryPath, cdnKey))

    override suspend fun deleteFile(absolutePath: String): Boolean {
        val temporaryKey = absolutePath.substringAfter("/StarceptionAssets/")
        return platform.delete(temporaryKey)
    }

    override suspend fun deleteCachedAsset(cdnKey: String): Boolean = platform.delete(cdnKey)
}

internal val iosCloudAssets = CloudAssetRepository(
    platform = IosAssetPlatform(),
    remoteManifestUrl = MANIFEST_URL,
)
