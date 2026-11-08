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
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.home_cover_prayer_day_portrait
import com.starception.submission.core.images.resources.home_cover_prayer_night_portrait
import com.starception.submission.core.images.resources.home_cover_qibla_portrait
import com.starception.submission.core.images.resources.home_cover_quran_portrait
import com.starception.submission.core.images.resources.home_cover_salah_portrait
import com.starception.submission.core.images.resources.home_cover_suggestion_portrait
import com.starception.submission.core.images.resources.home_getty_flowers_day
import com.starception.submission.core.images.resources.home_getty_flowers_night
import com.starception.submission.core.images.resources.home_getty_grotto_day
import com.starception.submission.core.images.resources.home_getty_grotto_night
import com.starception.submission.core.images.resources.home_getty_irises_night
import com.starception.submission.core.images.resources.home_getty_lilies_day
import com.starception.submission.core.images.resources.home_getty_lilies_night
import com.starception.submission.core.images.resources.home_getty_seascape_night
import com.starception.submission.core.images.resources.home_getty_snowdon_night
import com.starception.submission.core.images.resources.home_getty_wheatstacks_night
import org.jetbrains.compose.resources.DrawableResource

data class HomeArtwork(
    val title: String,
    val artist: String,
    val sourceUrl: String,
    val day: DrawableResource,
    val night: DrawableResource,
)

/** Reviewed, human-free CC0 images; provenance and source digests live beside this module. */
val homeArtworkGallery: List<HomeArtwork> = listOf(
    HomeArtwork("Study of Clouds with a Sunset near Rome", "Simon Alexandre Clément Denis",
        "https://www.getty.edu/art/collection/object/10986B",
        Res.drawable.home_cover_prayer_day_portrait, Res.drawable.home_cover_prayer_night_portrait),
    HomeArtwork("Wheatstacks, Snow Effect, Morning", "Claude Monet",
        "https://www.getty.edu/art/collection/object/103RK8",
        Res.drawable.home_cover_salah_portrait, Res.drawable.home_getty_wheatstacks_night),
    HomeArtwork("Irises", "Vincent van Gogh",
        "https://www.getty.edu/art/collection/object/103JNH",
        Res.drawable.home_cover_quran_portrait, Res.drawable.home_getty_irises_night),
    HomeArtwork("Mount Snowdon through Clearing Clouds", "Alfred William Hunt",
        "https://www.getty.edu/art/collection/object/109KKN",
        Res.drawable.home_cover_qibla_portrait, Res.drawable.home_getty_snowdon_night),
    HomeArtwork("Seascape", "Paul Martin",
        "https://www.getty.edu/art/collection/object/10492H",
        Res.drawable.home_cover_suggestion_portrait, Res.drawable.home_getty_seascape_night),
    HomeArtwork("Grotto of Sarrazine near Nans-sous-Sainte-Anne", "Gustave Courbet",
        "https://www.getty.edu/art/collection/object/1096PR",
        Res.drawable.home_getty_grotto_day, Res.drawable.home_getty_grotto_night),
    HomeArtwork("Still Life with Flowers and Fruit", "Claude Monet",
        "https://www.getty.edu/art/collection/object/103RF8",
        Res.drawable.home_getty_flowers_day, Res.drawable.home_getty_flowers_night),
    HomeArtwork("Water Lilies", "Eugène Atget",
        "https://www.getty.edu/art/collection/object/104JKW",
        Res.drawable.home_getty_lilies_day, Res.drawable.home_getty_lilies_night),
)

fun homeArtworkIndex(page: Int, rotation: Int): Int =
    ((rotation.toLong() + page.coerceIn(0, 4)) % homeArtworkGallery.size).toInt()

@Composable
fun currentHomeArtwork(page: Int, useNightArtwork: Boolean = false): DrawableResource {
    val artwork = homeArtworkGallery[homeArtworkIndex(page, rememberHomeArtworkRotation())]
    return if (useNightArtwork) artwork.night else artwork.day
}
