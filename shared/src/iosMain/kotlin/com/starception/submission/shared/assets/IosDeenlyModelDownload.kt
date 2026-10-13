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

import com.starception.submission.core.assets.AssetPlatform
import com.starception.submission.shared.ml.DeenlyKnowledgeRuntime
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
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

private const val NOW_NUDGE_CATEGORY = "model_now_nudge"

// Android's AssetDownloadManager carries the same constants: the GGUF ships
// as four ~246 MB CDN parts that must be assembled, verified, and activated
// as one file before the runner can load it.
private const val NOW_NUDGE_MODEL_KEY = "models/now_nudge/deenly-question-v5-q4_k_m.gguf"
private const val NOW_NUDGE_MODEL_SIZE = 986_048_224L
private const val NOW_NUDGE_MODEL_SHA256 =
    "80535d590f1a690730530905fc83667fd2a4db72b7ba683afadc437107024455"
private val NOW_NUDGE_PART_KEYS = (0..3).map { "$NOW_NUDGE_MODEL_KEY.part-0$it" }

private const val ASSEMBLY_CHUNK_BYTES = 4L * 1024L * 1024L

/**
 * Installs the shared CDN download behind DeenlyKnowledgeRuntime so the
 * missing-model card in shared Compose can fetch the GGUF category. Called
 * once from the iOS Compose root; Android's app module owns its own
 * AssetDownloadManager flow (including its part assembly).
 *
 * CloudAssetRepository runs in its caller's context, and the category is a
 * ~986 MB download that also SHA-256-verifies every part — doing that on
 * the main dispatcher froze the whole UI. The hook is main-safe: the work
 * runs on the default dispatcher and only the coroutine suspension stays
 * on main. Progress also feeds the app-level pull-to-sync banner, matching
 * the other content downloads.
 */
internal fun installDeenlyModelDownloadHook() {
    DeenlyKnowledgeRuntime.downloadModels = { onProgress ->
        withContext(Dispatchers.Default) {
            val result = iosCloudAssets.downloadCategory(NOW_NUDGE_CATEGORY) { progress ->
                // Compose snapshot state is safe to write from any thread;
                // recomposition is scheduled on the main dispatcher.
                onProgress(progress.bytesDownloaded, progress.totalBytes)
                ContentDownloadBus.publish(
                    ContentDownloadStatus(
                        label = "On-device AI Question Model",
                        progress = progress.fraction,
                    ),
                )
            }
            val assembled = result.isComplete && assembleNowNudgeQuestionModel()
            // The banner belongs to the app shell: clear it whether the
            // category finished, failed, or was assembled.
            ContentDownloadBus.publish(null)
            assembled
        }
    }
}

/**
 * Android's assemblePartedModel, ported: concatenate the four verified CDN
 * parts, check the final size and SHA-256, activate the file atomically,
 * then delete the parts. Returns false when a part is missing or the
 * integrity check fails; the parts stay cached so a retry is cheap.
 */
@OptIn(ExperimentalForeignApi::class)
private fun assembleNowNudgeQuestionModel(): Boolean {
    val existing = AssetPlatform.cachedPath(NOW_NUDGE_MODEL_KEY)
    if (existing != null && AssetPlatform.fileSize(existing) == NOW_NUDGE_MODEL_SIZE) return true

    val partPaths = NOW_NUDGE_PART_KEYS.mapNotNull(AssetPlatform::cachedPath)
    if (partPaths.size != NOW_NUDGE_PART_KEYS.size) return false

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

    val validSize = AssetPlatform.fileSize(assemblingPath) == NOW_NUDGE_MODEL_SIZE
    val validHash = AssetPlatform.fileSha256(assemblingPath)
        ?.equals(NOW_NUDGE_MODEL_SHA256, ignoreCase = true) == true
    if (!validSize || !validHash) {
        fileManager.removeItemAtPath(assemblingPath, error = null)
        return false
    }
    val activated = AssetPlatform.commit(assemblingPath, NOW_NUDGE_MODEL_KEY)
    if (activated == null) {
        fileManager.removeItemAtPath(assemblingPath, error = null)
        return false
    }
    NOW_NUDGE_PART_KEYS.forEach(AssetPlatform::delete)
    return true
}
