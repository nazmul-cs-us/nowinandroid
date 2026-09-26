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
     * download fails or the surah number has no artwork).
     */
    suspend fun ensureArtworkDownloaded(surahNumber: Int): java.io.File? {
        resolveArtworkFile(surahNumber)?.let { return it }
        val cdnKey = SurahArtwork.cdnKey(surahNumber) ?: run {
            android.util.Log.w(TAG, "No CDN key for surah $surahNumber")
            return null
        }
        val manifest = downloadManager.loadManifest() ?: run {
            android.util.Log.w(TAG, "Manifest unavailable (offline or parse failure); cannot fetch $cdnKey")
            return null
        }
        android.util.Log.i(TAG, "Downloading artwork $cdnKey (in manifest: ${manifest.assets.containsKey(cdnKey)})")
        return when (val state = downloadAsset(cdnKey, manifest, allowManifestRefresh = true)) {
            is AssetDownloadManager.DownloadState.Completed -> resolveArtworkFile(surahNumber)
            else -> {
                android.util.Log.w(TAG, "Artwork download failed for $cdnKey: $state")
                null
            }
        }
    }

    /**
     * Downloads [cdnKey] via [manifest], healing a stale manifest when the key
     * is unexpectedly absent: the CDN may have gained this category after the
     * cached manifest was loaded, so force-refresh once and retry.
     */
    private suspend fun downloadAsset(
        cdnKey: String,
        manifest: com.starception.submission.core.assetcache.AssetManifest,
        allowManifestRefresh: Boolean,
    ): AssetDownloadManager.DownloadState {
        var state = if (manifest.assets.containsKey(cdnKey)) {
            downloadManager.downloadAsset(cdnKey, manifest)
        } else {
            AssetDownloadManager.DownloadState.Failed("Asset not in manifest: $cdnKey")
        }
        if (
            state is AssetDownloadManager.DownloadState.Failed &&
            state.error.startsWith("Asset not in manifest") &&
            allowManifestRefresh
        ) {
            android.util.Log.i(TAG, "Key $cdnKey missing from cached manifest — refreshing")
            val fresh = downloadManager.refreshManifest()
            if (fresh != null && fresh.assets.containsKey(cdnKey)) {
                state = downloadManager.downloadAsset(cdnKey, fresh)
            }
        }
        return state
    }

    companion object {
        private const val TAG = "SurahArtworkResolver"

        fun from(context: Context): SurahArtworkResolver =
            SurahArtworkResolver(
                dagger.hilt.android.EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.starception.submission.voice.SherpaOnnxTtsEntryPoint::class.java,
                ).assetDownloadManager(),
            )
    }
}
