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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.insight_prayer_background
import com.starception.submission.core.images.resources.insight_prayer_foreground
import com.starception.submission.core.images.resources.insight_quran_background
import com.starception.submission.core.images.resources.insight_quran_foreground_v2
import com.starception.submission.core.images.resources.insight_qibla_background
import com.starception.submission.core.images.resources.insight_qibla_foreground_v2
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import com.starception.submission.shared.SharedPrayerDay
import com.starception.submission.shared.salah.FARD_PRAYERS
import com.starception.submission.feature.quran.dailyReading
import com.starception.submission.feature.quran.subtitle
import com.starception.submission.shared.salah.SalahProgress
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The swipeable insight tiles at the top of the home page.
 *
 * Uses the same prayer, salah, reading and Qibla artwork as Android. The Qibla
 * card intentionally shows the useful portable part — the great-circle bearing
 * to Makkah — while Android's interactive WorldWind globe remains platform-only.
 */
@Composable
fun InsightPager(
    day: SharedPrayerDay,
    placeName: String,
    salah: SalahProgress,
    onTogglePrayer: (String) -> Unit,
    today: LocalDate,
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
) {
    val pageCount = 4
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val pagerScope = rememberCoroutineScope()
    val qiblaBearing = remember(latitude, longitude) {
        calculateQiblaBearing(latitude, longitude).roundToInt()
    }

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
                            )
                            .clickable {
                                pagerScope.launch { pagerState.animateScrollToPage(index) }
                            },
                    )
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Android leaves the next card visibly clipped so the horizontal
            // gesture is apparent without a separate swipe hint.
            val pageWidth = (maxWidth * 0.64f).coerceAtLeast(210.dp)
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
                        foreground = Res.drawable.insight_prayer_foreground,
                        foregroundScale = 0.88f,
                        foregroundOffsetYFraction = 0.08f,
                        label = "Today's salah",
                        title = salah.headline,
                        subtitle = salah.detail,
                    ) {
                        SalahMarkers(
                            completed = salah.completed,
                            onToggle = onTogglePrayer,
                        )
                    }

                    2 -> {
                        val surah = dailyReading(today)
                        ArtworkTile(
                            artwork = Res.drawable.insight_quran_background,
                            foreground = Res.drawable.insight_quran_foreground_v2,
                            foregroundScale = 0.80f,
                            foregroundOffsetYFraction = 0.10f,
                            label = "Reading",
                            title = surah.nameEnglish,
                            subtitle = surah.subtitle(),
                            arabicTitle = surah.nameArabic,
                        )
                    }

                    else -> ArtworkTile(
                        artwork = Res.drawable.insight_qibla_background,
                        foreground = Res.drawable.insight_qibla_foreground_v2,
                        foregroundScale = 0.75f,
                        foregroundOffsetYFraction = 0.10f,
                        label = "Qibla",
                        title = "$qiblaBearing° toward Makkah",
                        subtitle = qiblaCardinalDirection(qiblaBearing),
                    )
                }
            }
        }
    }
}

/** Great-circle initial bearing from the user to the Kaaba, clockwise from north. */
private fun calculateQiblaBearing(latitude: Double, longitude: Double): Double {
    val userLatitude = degreesToRadians(latitude)
    val kaabaLatitude = degreesToRadians(21.4225)
    val longitudeDelta = degreesToRadians(39.8262 - longitude)
    val y = sin(longitudeDelta) * cos(kaabaLatitude)
    val x = cos(userLatitude) * sin(kaabaLatitude) -
        sin(userLatitude) * cos(kaabaLatitude) * cos(longitudeDelta)
    return ((atan2(y, x) * 180.0 / kotlin.math.PI) + 360.0) % 360.0
}

private fun degreesToRadians(value: Double): Double = value * kotlin.math.PI / 180.0

private fun qiblaCardinalDirection(bearing: Int): String = when (bearing.mod(360)) {
    in 23..67 -> "North-east"
    in 68..112 -> "East"
    in 113..157 -> "South-east"
    in 158..202 -> "South"
    in 203..247 -> "South-west"
    in 248..292 -> "West"
    in 293..337 -> "North-west"
    else -> "North"
}

/** A tile that is artwork with a label chip and a caption over it. */
@Composable
private fun ArtworkTile(
    artwork: DrawableResource,
    foreground: DrawableResource? = null,
    foregroundScale: Float = 1f,
    foregroundOffsetYFraction: Float = 0f,
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
            .height(220.dp)
            .clip(RoundedCornerShape(26.dp)),
    ) {
        Image(
            painter = painterResource(artwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        foreground?.let { foregroundArtwork ->
            Image(
                painter = painterResource(foregroundArtwork),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = foregroundScale
                        scaleY = foregroundScale
                        translationY = size.height * foregroundOffsetYFraction
                    },
            )
        }
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
