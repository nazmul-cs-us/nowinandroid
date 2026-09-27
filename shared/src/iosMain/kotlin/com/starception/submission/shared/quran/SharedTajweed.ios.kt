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

package com.starception.submission.shared.quran

import com.starception.submission.shared.assets.iosCloudAssets
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile

actual fun createSharedTajweedRepository(): SharedTajweedRepository = IosSharedTajweedRepository()

private class IosSharedTajweedRepository : SharedTajweedRepository {
    private var cache: Map<Int, Map<Int, List<SharedTajweedAnnotation>>>? = null

    override suspend fun annotationsForSurah(
        surahNumber: Int,
    ): Map<Int, List<SharedTajweedAnnotation>>? = withContext(Dispatchers.Default) {
        val parsed = cache ?: run {
            // resolveAsset downloads + sha-verifies the 5.5 MB CDN asset on
            // first use, then serves the cached copy — the manifest pattern
            // the Android app uses for json/tajweed.json.
            val asset = iosCloudAssets.resolveAsset("json/tajweed.json")
                ?: return@withContext null
            val jsonText = readTextFile(asset.absolutePath) ?: return@withContext null
            parseTajweedJson(jsonText).also { cache = it }
        }
        parsed[surahNumber]
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun readTextFile(path: String): String? {
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        return NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
    }
}
