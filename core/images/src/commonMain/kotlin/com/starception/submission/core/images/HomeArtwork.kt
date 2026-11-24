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
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.home_artwork_neutral

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
    val licenseName: String = "CC0",
    val licenseUrl: String = "https://creativecommons.org/publicdomain/zero/1.0/",
)

fun homeArtworkIndex(page: Int, rotation: Int): Int =
    ((rotation.toLong() + page.coerceIn(0, 4)) % homeArtworkGallery.size).toInt()

@Composable
fun currentHomeArtwork(page: Int, useNightArtwork: Boolean = false): DrawableResource {
    // Bundled images are a stable fallback, not a pretend online refresh.
    val artwork = bundledHomeArtwork(page, rememberHomeArtworkSettings().theme)
        ?: return Res.drawable.home_artwork_neutral
    return if (useNightArtwork) artwork.night else artwork.day
}

internal fun bundledHomeArtwork(page: Int, theme: ArtworkTheme): HomeArtwork? {
    val candidates = homeArtworkGallery.filter { artwork ->
        when (theme) {
            ArtworkTheme.NATURE -> artwork.subject == HomeArtworkSubject.CALM_NATURE
            ArtworkTheme.ISLAMIC -> artwork.subject == HomeArtworkSubject.ISLAMIC_ARCHITECTURE ||
                artwork.subject == HomeArtworkSubject.ISLAMIC_PATTERN
            ArtworkTheme.SPACE -> false
        }
    }
    return candidates.takeIf { it.isNotEmpty() }?.let { it[page.coerceIn(0, 4) % it.size] }
}
