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

package com.starception.submission.shared.assets

import com.starception.submission.core.assets.AssetPlatform
import com.starception.submission.shared.ml.HadithBookGenerator
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.ferror
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite

private const val CATEGORIZER_CATEGORY = "model_hadith_categorizer"

// Android's AssetDownloadManager carries the same constants: the GGUF ships
// as four ~279 MB CDN parts that must be assembled, verified, and activated
// as one file before the runner can load it.
private const val CATEGORIZER_MODEL_KEY = "models/hadith/hadith-categorizer-v1-q4_k_m.gguf"
private const val CATEGORIZER_MODEL_SIZE = 1_117_320_736L
private const val CATEGORIZER_MODEL_SHA256 =
    "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
private val CATEGORIZER_PART_KEYS = (0..3).map { "$CATEGORIZER_MODEL_KEY.part-0$it" }

private const val ASSEMBLY_CHUNK_BYTES = 4L * 1024L * 1024L

/**
 * Installs the hadith-book generator's CDN download behind
 * [HadithBookGenerator.downloadModel] so the shared Recategorize UI can fetch
 * and assemble the categorizer GGUF. Called once from the iOS Compose root;
 * Android's app module owns its own AssetDownloadManager flow (including its
 * part assembly).
 *
 * CloudAssetRepository runs in its caller's context, and the category is a
 * ~1.1 GB download that also SHA-256-verifies every part — the work runs on
 * the default dispatcher while progress feeds the pull-to-sync banner.
 */
internal fun installHadithCategorizerDownloadHook() {
    HadithBookGenerator.downloadModel = { onProgress ->
        withContext(Dispatchers.Default) {
            val throttle = ContentDownloadProgressThrottle()
            val result = iosCloudAssets.downloadCategory(CATEGORIZER_CATEGORY) { progress ->
                onProgress(progress.bytesDownloaded, progress.totalBytes)
                if (throttle.shouldPublish(progress.fraction)) {
                    ContentDownloadBus.publish(
                        ContentDownloadStatus(
                            label = "Hadith Book Generator Model",
                            progress = progress.fraction,
                        ),
                    )
                }
            }
            val assembled = result.isComplete && assembleHadithCategorizerModel()
            ContentDownloadBus.publish(null)
            assembled
        }
    }
}

/**
 * Concatenate the four verified CDN parts, check the final size and SHA-256,
 * activate the file atomically, then delete the parts — the same contract as
 * the Now Nudge assembler.
 */
@OptIn(ExperimentalForeignApi::class)
private fun assembleHadithCategorizerModel(): Boolean {
    val existing = AssetPlatform.cachedPath(CATEGORIZER_MODEL_KEY)
    if (existing != null && AssetPlatform.fileSize(existing) == CATEGORIZER_MODEL_SIZE) return true

    val partPaths = CATEGORIZER_PART_KEYS.mapNotNull(AssetPlatform::cachedPath)
    if (partPaths.size != CATEGORIZER_PART_KEYS.size) return false

    val fileManager = NSFileManager.defaultManager
    val assemblingPath = "${partPaths.first()}.assembling"
    fileManager.removeItemAtPath(assemblingPath, error = null)

    val assembled = memScoped {
        val buffer = allocArray<ByteVar>(ASSEMBLY_CHUNK_BYTES.toInt())
        val output = fopen(assemblingPath, "wb") ?: return@memScoped false
        var success = true
        try {
            for (partPath in partPaths) {
                val input = fopen(partPath, "rb")
                if (input == null) {
                    success = false
                    break
                }
                try {
                    while (true) {
                        val count = fread(
                            buffer,
                            1.convert(),
                            ASSEMBLY_CHUNK_BYTES.convert(),
                            input,
                        )
                        if (count == 0UL) break
                        if (fwrite(buffer, 1.convert(), count, output) != count) {
                            success = false
                            break
                        }
                    }
                    if (ferror(input) != 0) success = false
                } finally {
                    fclose(input)
                }
                if (!success) break
            }
            if (fflush(output) != 0) success = false
            if (ferror(output) != 0) success = false
        } finally {
            fclose(output)
        }
        success
    }
    if (!assembled) {
        fileManager.removeItemAtPath(assemblingPath, error = null)
        return false
    }

    val validSize = AssetPlatform.fileSize(assemblingPath) == CATEGORIZER_MODEL_SIZE
    val validHash = AssetPlatform.fileSha256(assemblingPath)
        ?.equals(CATEGORIZER_MODEL_SHA256, ignoreCase = true) == true
    if (!validSize || !validHash) {
        fileManager.removeItemAtPath(assemblingPath, error = null)
        return false
    }
    val activated = AssetPlatform.commit(assemblingPath, CATEGORIZER_MODEL_KEY)
    if (activated == null) {
        fileManager.removeItemAtPath(assemblingPath, error = null)
        return false
    }
    CATEGORIZER_PART_KEYS.forEach(AssetPlatform::delete)
    return true
}
