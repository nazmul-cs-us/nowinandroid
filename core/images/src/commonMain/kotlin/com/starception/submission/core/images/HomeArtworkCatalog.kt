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

import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.home_cover_prayer_day_portrait
import com.starception.submission.core.images.resources.home_cover_prayer_night_portrait
import com.starception.submission.core.images.resources.home_cover_qibla_portrait
import com.starception.submission.core.images.resources.home_cover_quran_portrait
import com.starception.submission.core.images.resources.home_cover_salah_portrait
import com.starception.submission.core.images.resources.home_cover_suggestion_portrait
import com.starception.submission.core.images.resources.home_getty_arches_night
import com.starception.submission.core.images.resources.home_getty_carving_day
import com.starception.submission.core.images.resources.home_getty_carving_night
import com.starception.submission.core.images.resources.home_getty_clouds_day
import com.starception.submission.core.images.resources.home_getty_clouds_night
import com.starception.submission.core.images.resources.home_getty_coast_day
import com.starception.submission.core.images.resources.home_getty_coast_night
import com.starception.submission.core.images.resources.home_getty_columns_night
import com.starception.submission.core.images.resources.home_getty_courtyard_night
import com.starception.submission.core.images.resources.home_getty_geometry_night
import com.starception.submission.core.images.resources.home_getty_lilies_day
import com.starception.submission.core.images.resources.home_getty_lilies_night
import com.starception.submission.core.images.resources.home_getty_mountains_day
import com.starception.submission.core.images.resources.home_getty_mountains_night
import com.starception.submission.core.images.resources.home_getty_panel_day
import com.starception.submission.core.images.resources.home_getty_panel_night
import com.starception.submission.core.images.resources.home_getty_tiles_day
import com.starception.submission.core.images.resources.home_getty_tiles_night

// Generated from artwork/getty-home-artwork.json. Add only reviewed, approved subjects.
val homeArtworkGallery: List<HomeArtwork> = listOf(
    HomeArtwork(
        title = "Archways, the Alhambra",
        artist = "Unknown",
        sourceUrl = "https://www.getty.edu/art/collection/object/107JW5",
        day = Res.drawable.home_cover_prayer_day_portrait,
        night = Res.drawable.home_cover_prayer_night_portrait,
        subject = HomeArtworkSubject.ISLAMIC_ARCHITECTURE,
    ),
    HomeArtwork(
        title = "Archway, the Alhambra",
        artist = "Unknown",
        sourceUrl = "https://www.getty.edu/art/collection/object/107JW6",
        day = Res.drawable.home_cover_salah_portrait,
        night = Res.drawable.home_getty_columns_night,
        subject = HomeArtworkSubject.ISLAMIC_ARCHITECTURE,
    ),
    HomeArtwork(
        title = "Interior of Alhambra",
        artist = "Unknown",
        sourceUrl = "https://www.getty.edu/art/collection/object/104KMH",
        day = Res.drawable.home_cover_quran_portrait,
        night = Res.drawable.home_getty_courtyard_night,
        subject = HomeArtworkSubject.ISLAMIC_ARCHITECTURE,
    ),
    HomeArtwork(
        title = "Alhambra",
        artist = "Unknown",
        sourceUrl = "https://www.getty.edu/art/collection/object/104G6V",
        day = Res.drawable.home_cover_qibla_portrait,
        night = Res.drawable.home_getty_arches_night,
        subject = HomeArtworkSubject.ISLAMIC_ARCHITECTURE,
    ),
    HomeArtwork(
        title = "Archway in the Alhambra",
        artist = "Unknown",
        sourceUrl = "https://www.getty.edu/art/collection/object/107JSE",
        day = Res.drawable.home_cover_suggestion_portrait,
        night = Res.drawable.home_getty_geometry_night,
        subject = HomeArtworkSubject.ISLAMIC_PATTERN,
    ),
    HomeArtwork(
        title = "Archway in the Alhambra",
        artist = "Unknown",
        sourceUrl = "https://www.getty.edu/art/collection/object/107JVV",
        day = Res.drawable.home_getty_carving_day,
        night = Res.drawable.home_getty_carving_night,
        subject = HomeArtworkSubject.ISLAMIC_PATTERN,
    ),
    HomeArtwork(
        title = "Panel from the Alhambra",
        artist = "Hugh Owen",
        sourceUrl = "https://www.getty.edu/art/collection/object/107VD6",
        day = Res.drawable.home_getty_panel_day,
        night = Res.drawable.home_getty_panel_night,
        subject = HomeArtworkSubject.ISLAMIC_PATTERN,
    ),
    HomeArtwork(
        title = "Three tiles from The Alhambra",
        artist = "William Chaffers",
        sourceUrl = "https://www.getty.edu/art/collection/object/108VZD",
        day = Res.drawable.home_getty_tiles_day,
        night = Res.drawable.home_getty_tiles_night,
        subject = HomeArtworkSubject.ISLAMIC_PATTERN,
    ),
    HomeArtwork(
        title = "Study of Clouds with a Sunset near Rome",
        artist = "Simon Alexandre Clément Denis",
        sourceUrl = "https://www.getty.edu/art/collection/object/10986B",
        day = Res.drawable.home_getty_clouds_day,
        night = Res.drawable.home_getty_clouds_night,
        subject = HomeArtworkSubject.CALM_NATURE,
    ),
    HomeArtwork(
        title = "Mount Snowdon through Clearing Clouds",
        artist = "Alfred William Hunt",
        sourceUrl = "https://www.getty.edu/art/collection/object/109KKN",
        day = Res.drawable.home_getty_mountains_day,
        night = Res.drawable.home_getty_mountains_night,
        subject = HomeArtworkSubject.CALM_NATURE,
    ),
    HomeArtwork(
        title = "Seascape",
        artist = "Paul Martin",
        sourceUrl = "https://www.getty.edu/art/collection/object/10492H",
        day = Res.drawable.home_getty_coast_day,
        night = Res.drawable.home_getty_coast_night,
        subject = HomeArtworkSubject.CALM_NATURE,
    ),
    HomeArtwork(
        title = "Water Lilies",
        artist = "Eugène Atget",
        sourceUrl = "https://www.getty.edu/art/collection/object/104JKW",
        day = Res.drawable.home_getty_lilies_day,
        night = Res.drawable.home_getty_lilies_night,
        subject = HomeArtworkSubject.CALM_NATURE,
    ),
)
