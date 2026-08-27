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

package com.starception.submission.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.insight_prayer_background
import com.starception.submission.core.images.resources.insight_quran_background
import com.starception.submission.shared.SharedPrayerDay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * The swipeable insight tiles at the top of the home page.
 *
 * Mirrors the structure of the Android home page's pager rather than its full
 * contents: the prayer sky tile is real, and the salah and reading tiles carry
 * the right artwork with placeholder figures until their data moves to shared
 * code. The Qibla tile is absent entirely — it needs the 3D globe, which is
 * gated on a WorldWind version bump.
 *
 * Named honestly as a partial port. It is the shape of the home page, not parity.
 */
@Composable
fun InsightPager(
    day: SharedPrayerDay,
    placeName: String,
    modifier: Modifier = Modifier,
) {
    val pageCount = 3
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Insights",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(pageCount) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (selected) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            // A peek of the next tile is what signals the pager is swipeable at
            // all; without it the row reads as a single static card.
            contentPadding = PaddingValues(end = 48.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> PrayerNowTile(
                    phase = day.skyPhase,
                    weather = day.skyWeather,
                    headline = day.currentPrayer?.let { "Best Time to Pray $it" }
                        ?: "Prayer Times",
                    subtitle = placeName,
                    nextPrayer = day.nextPrayer.orEmpty(),
                    countdown = day.countdown,
                )

                1 -> ArtworkTile(
                    artwork = Res.drawable.insight_prayer_background,
                    label = "Today's salah",
                    title = "0 prayers complete",
                    subtitle = day.nextPrayer?.let { "5 remain · $it is next" }.orEmpty(),
                )

                else -> ArtworkTile(
                    artwork = Res.drawable.insight_quran_background,
                    label = "Today's reading",
                    title = "Al-Fatihah",
                    subtitle = "Surah 1 · Meccan",
                )
            }
        }
    }
}

/** A tile that is artwork with a label chip and a caption over it. */
@Composable
private fun ArtworkTile(
    artwork: DrawableResource,
    label: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        Image(
            painter = painterResource(artwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.60f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .padding(12.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}
