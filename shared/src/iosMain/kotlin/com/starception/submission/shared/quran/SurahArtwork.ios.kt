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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.starception.submission.shared.assets.iosCloudAssets
import com.starception.submission.shared.ui.fileImageBitmap

@Composable
internal actual fun SurahArtworkHeader(
    surahNumber: Int,
    contentDescription: String,
    modifier: Modifier,
) {
    // CDN resolve + download happens once per surah; the resolved file path is
    // remembered for the composition and the (immutable) artwork file renders
    // as a plain Compose image decoded through Skia, so the album header's
    // graphicsLayer parallax applies to it.
    var imagePath by remember(surahNumber) { mutableStateOf<String?>(null) }
    LaunchedEffect(surahNumber) {
        imagePath = surahArtworkCdnKey(surahNumber)?.let { cdnKey ->
            runCatching {
                iosCloudAssets.resolveAsset(cdnKey)?.absolutePath
            }.getOrNull()
        }
    }

    Box(modifier = modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh)) {
        val path = imagePath
        if (path != null) {
            val imageBitmap = remember(path) { fileImageBitmap(path) }
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        // While resolving/downloading the header shows the neutral surface
        // background; artwork appears as soon as the file is available.
    }
}
