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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import com.starception.submission.shared.SharedPrayerDay
import com.starception.submission.shared.salah.FARD_PRAYERS
import com.starception.submission.feature.quran.dailyReading
import com.starception.submission.feature.quran.subtitle
import com.starception.submission.shared.salah.SalahProgress
import kotlinx.datetime.LocalDate
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
    salah: SalahProgress,
    onTogglePrayer: (String) -> Unit,
    today: LocalDate,
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
                            .size(
                                width = if (selected) 18.dp else 6.dp,
                                height = 6.dp,
                            )
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

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Match the compact Android dashboard: two useful cards remain in
            // view while the next page still reads as horizontally swipeable.
            val pageWidth = (maxWidth - 12.dp) / 2
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(pageWidth),
                pageSpacing = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> PrayerNowTile(
                        phase = day.skyPhase,
                        weather = day.skyWeather,
                        headline = day.currentPrayer?.let { "Time for $it" }
                            ?: "Prayer Times",
                        subtitle = placeName,
                        nextPrayer = day.nextPrayer.orEmpty(),
                        countdown = day.countdown,
                    )

                    1 -> ArtworkTile(
                        artwork = Res.drawable.insight_prayer_background,
                        label = "Today's salah",
                        title = salah.headline,
                        subtitle = salah.detail,
                    ) {
                        SalahMarkers(
                            completed = salah.completed,
                            onToggle = onTogglePrayer,
                        )
                    }

                    else -> {
                        val surah = dailyReading(today)
                        ArtworkTile(
                            artwork = Res.drawable.insight_quran_background,
                            label = "Reading",
                            title = surah.nameEnglish,
                            subtitle = surah.subtitle(),
                            arabicTitle = surah.nameArabic,
                        )
                    }
                }
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
    arabicTitle: String? = null,
    content: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(20.dp)),
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
                .padding(10.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
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
                .padding(14.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            content?.invoke()
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            if (arabicTitle != null) {
                Text(
                    text = arabicTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
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

/**
 * One tappable marker per obligatory prayer, in order.
 *
 * Tapping toggles rather than only setting, so a mistake is undone the same way
 * it was made — there is no other affordance on the tile to correct one.
 */
@Composable
private fun SalahMarkers(
    completed: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FARD_PRAYERS.forEach { prayer ->
            val isDone = prayer in completed
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDone) Color.White else Color.White.copy(alpha = 0.15f),
                        )
                        .border(
                            width = 1.5.dp,
                            color = Color.White.copy(alpha = if (isDone) 1f else 0.6f),
                            shape = CircleShape,
                        )
                        .clickable { onToggle(prayer) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = prayer.take(1),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        // Dark on the filled state, light on the empty one, so
                        // the letter stays legible either way.
                        color = if (isDone) Color(0xFF1B3A2A) else Color.White,
                    )
                }
            }
        }
    }
}
