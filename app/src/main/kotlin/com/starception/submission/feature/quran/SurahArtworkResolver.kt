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

package com.starception.submission.feature.quran

import android.content.Context
import com.starception.submission.download.AssetDownloadManager
import java.io.File

/**
 * Resolves chapter artwork from the CDN download cache
 * (cdn_assets/surah_artwork/), downloading a chapter's art on demand.
 *
 * The artwork is intentionally NOT bundled in the APK (it was ~60 MB alone).
 * Callers fall back to [SurahArtwork.PLACEHOLDER] until a chapter's art has
 * been downloaded.
 */
class SurahArtworkResolver(private val downloadManager: AssetDownloadManager) {

    /** Cached artwork file for [surahNumber], or null when not downloaded yet. */
    fun resolveArtworkFile(surahNumber: Int): File? {
        val cdnKey = SurahArtwork.cdnKey(surahNumber) ?: return null
        return downloadManager.getLocallyAvailableAssetFile(cdnKey)
    }

    /**
     * Ensures the artwork for [surahNumber] exists locally, downloading it from
     * the CDN when needed. Returns the file once available (or null when the
     * download fails or the prayer number has no artwork).
     */
    suspend fun ensureArtworkDownloaded(surahNumber: Int): File? {
        resolveArtworkFile(surahNumber)?.let { return it }
        val cdnKey = SurahArtwork.cdnKey(surahNumber) ?: return null
        val manifest = downloadManager.loadManifest() ?: return null
        // A manifest entry can be absent right after an app update ships the
        // new category but the CDN manifest hasn't been replaced yet.
        if (!manifest.assets.containsKey(cdnKey)) return null
        return when (downloadManager.downloadAsset(cdnKey, manifest)) {
            is AssetDownloadManager.DownloadState.Completed -> resolveArtworkFile(surahNumber)
            else -> null
        }
    }

    companion object {
        fun from(context: Context): SurahArtworkResolver =
            SurahArtworkResolver(
                dagger.hilt.android.EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.starception.submission.voice.SherpaOnnxTtsEntryPoint::class.java,
                ).assetDownloadManager(),
            )
    }
}
