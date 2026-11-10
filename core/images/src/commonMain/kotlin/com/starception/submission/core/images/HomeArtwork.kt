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

package com.starception.submission.core.images

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.DrawableResource

enum class HomeArtworkSubject {
    ISLAMIC_ARCHITECTURE,
    ISLAMIC_PATTERN,
    CALM_NATURE,
}

data class HomeArtwork(
    val title: String,
    val artist: String,
    val sourceUrl: String,
    val day: DrawableResource,
    val night: DrawableResource,
    val subject: HomeArtworkSubject,
)

fun homeArtworkIndex(page: Int, rotation: Int): Int =
    ((rotation.toLong() + page.coerceIn(0, 4)) % homeArtworkGallery.size).toInt()

@Composable
fun currentHomeArtwork(page: Int, useNightArtwork: Boolean = false): DrawableResource {
    // Bundled images are a stable fallback, not a pretend online refresh.
    val artwork = homeArtworkGallery[homeArtworkIndex(page, 0)]
    return if (useNightArtwork) artwork.night else artwork.day
}
