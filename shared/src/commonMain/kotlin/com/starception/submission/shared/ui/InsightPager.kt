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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.starception.submission.core.images.resources.insight_suggestion
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import com.starception.submission.shared.SharedPrayerDay
import com.starception.submission.shared.salah.FARD_PRAYERS
import com.starception.submission.feature.quran.dailyReading
import com.starception.submission.feature.quran.subtitle
import com.starception.submission.shared.salah.SalahProgress
import com.starception.submission.shared.content.dailyRecommendation
import com.starception.submission.shared.audio.QuranAudioPlayer
import com.starception.submission.shared.audio.quranAudioUrl
import com.starception.submission.shared.qibla.cardinalDirection
import com.starception.submission.shared.qibla.qiblaBearing
import com.starception.submission.shared.qibla.relativeQiblaTurn
import com.starception.submission.shared.qibla.HeadingProvider
import com.starception.submission.shared.qibla.HeadingReading
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt
import kotlin.math.abs

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
    onOpenQuran: (Int) -> Unit = {},
    onOpenQibla: () -> Unit = {},
    onOpenRecommendation: () -> Unit = {},
    tileHeight: androidx.compose.ui.unit.Dp = 220.dp,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val pageCount = 5
    val middleLoopStart = pageCount
    val pagerState = rememberPagerState(
        initialPage = middleLoopStart,
        pageCount = { pageCount * 3 },
    )
    val pagerScope = rememberCoroutineScope()
    val qiblaBearing = remember(latitude, longitude) {
        qiblaBearing(latitude, longitude).roundToInt()
    }
    val nextPrayerSlot = day.nextPrayer?.let { prayerName ->
        day.slots.firstOrNull { it.name == prayerName }
    }
    val nextPrayerText = nextPrayerSlot?.let { slot ->
        "${slot.name} · ${formatPrayerTime(slot.hour, slot.minute)}"
    } ?: day.nextPrayer.orEmpty()
    val quranPlayer = remember { QuranAudioPlayer() }
    var isReadingAudio by remember { mutableStateOf(false) }
    var prayerSceneIndex by remember(today) { mutableStateOf(today.day % 3) }
    val autoAdvanceProgress = remember { Animatable(0f) }
    val headingProvider = remember { HeadingProvider() }
    var heading by remember { mutableStateOf(HeadingReading()) }
    DisposableEffect(quranPlayer) {
        onDispose { quranPlayer.stop() }
    }
    LaunchedEffect(today) {
        quranPlayer.stop()
        isReadingAudio = false
    }
    LaunchedEffect(pagerState.settledPage) {
        when {
            pagerState.settledPage < middleLoopStart -> {
                pagerState.scrollToPage(pagerState.settledPage + pageCount)
            }
            pagerState.settledPage >= middleLoopStart + pageCount -> {
                pagerState.scrollToPage(pagerState.settledPage - pageCount)
            }
        }
    }
    val qiblaPageVisible = pagerState.settledPage % pageCount == 3
    DisposableEffect(headingProvider, qiblaPageVisible) {
        if (qiblaPageVisible) headingProvider.start { heading = it }
        onDispose { headingProvider.stop() }
    }
    LaunchedEffect(pagerState.settledPage, pagerState.isScrollInProgress) {
        autoAdvanceProgress.snapTo(0f)
        if (pagerState.isScrollInProgress) return@LaunchedEffect
        autoAdvanceProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 30_000, easing = LinearEasing),
        )
        if (!pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(pagerState.settledPage + 1)
        }
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
                    val selected = pagerState.currentPage % pageCount == index
                    Box(
                        modifier = Modifier
                            .size(
                                width = if (selected) 26.dp else 6.dp,
                                height = 6.dp,
                            )
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            )
                            .clickable {
                                pagerScope.launch {
                                    val currentLogicalPage = pagerState.currentPage % pageCount
                                    pagerState.animateScrollToPage(
                                        pagerState.currentPage + (index - currentLogicalPage),
                                    )
                                }
                            },
                    ) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(autoAdvanceProgress.value)
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Keep Android's card aspect ratio. Portrait intentionally leaves a
            // neighboring card visible; landscape dedicates its left pane to it.
            val pageWidth = if (isLandscape) {
                maxWidth
            } else {
                (tileHeight * (250f / 288f))
                    .coerceAtMost(maxWidth * 0.64f)
                    .coerceAtLeast(140.dp)
            }
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(pageWidth),
                pageSpacing = 12.dp,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxWidth().height(tileHeight),
            ) { page ->
                val logicalPage = page % pageCount
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val pageOffset = abs(
                                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction,
                            ).coerceIn(0f, 1f)
                            val focus = 1f - pageOffset
                            scaleX = 0.97f + (0.03f * focus)
                            scaleY = 0.97f + (0.03f * focus)
                            alpha = 0.9f + (0.1f * focus)
                        },
                ) {
                when (logicalPage) {
                    0 -> PrayerNowTile(
                        phase = day.skyPhase,
                        weather = day.skyWeather,
                        headline = day.heroHeadline(),
                        subtitle = day.heroSubtitle(placeName),
                        nextPrayer = nextPrayerText,
                        countdown = day.countdown,
                        sceneIndex = prayerSceneIndex,
                        timelineProgress = day.prayerWindowProgress(),
                        tileHeight = tileHeight,
                        onClick = onOpenQibla,
                        onLongClick = { prayerSceneIndex = (prayerSceneIndex + 1).mod(3) },
                    )

                    1 -> ArtworkTile(
                        artwork = Res.drawable.insight_prayer_background,
                        foreground = Res.drawable.insight_prayer_foreground,
                        foregroundScale = 0.88f,
                        foregroundOffsetYFraction = 0.08f,
                        label = "Today's salah",
                        title = salah.headline,
                        subtitle = salah.detail,
                        tileHeight = tileHeight,
                    ) {
                        SalahMarkers(
                            completed = salah.completed,
                            available = day.slots
                                .filter { it.hasStarted && it.name in FARD_PRAYERS }
                                .mapTo(mutableSetOf()) { it.name },
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
                            label = "Today's reading",
                            title = surah.nameEnglish,
                            subtitle = surah.subtitle(),
                            arabicTitle = surah.nameArabic,
                            tileHeight = tileHeight,
                            onClick = {
                                quranPlayer.stop()
                                isReadingAudio = false
                                onOpenQuran(surah.number)
                            },
                            content = {
                                Surface(
                                    onClick = {
                                        if (isReadingAudio) {
                                            quranPlayer.pause()
                                            isReadingAudio = false
                                        } else {
                                            isReadingAudio = quranPlayer.play(quranAudioUrl(surah.number))
                                        }
                                    },
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.38f),
                                    contentColor = Color.White,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                                    modifier = Modifier.padding(bottom = 8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = if (isReadingAudio) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (isReadingAudio) "Pause recitation" else "Play recitation",
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = if (isReadingAudio) "Pause" else "Listen",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            },
                        )
                    }

                    3 -> ArtworkTile(
                        artwork = Res.drawable.insight_qibla_background,
                        foreground = Res.drawable.insight_qibla_foreground_v2,
                        foregroundScale = 0.75f,
                        foregroundOffsetYFraction = 0.10f,
                        label = "Qibla",
                        title = "$qiblaBearing° toward Makkah",
                        subtitle = qiblaGuidance(
                            qiblaBearing = qiblaBearing,
                            headingDegrees = heading.headingDegrees,
                        ),
                        tileHeight = tileHeight,
                        onClick = onOpenQibla,
                    )

                    else -> {
                        val recommendation = dailyRecommendation(today)
                        ArtworkTile(
                            artwork = Res.drawable.insight_suggestion,
                            label = "AI suggested · ${recommendation.category}",
                            title = recommendation.title,
                            subtitle = recommendation.summary,
                            tileHeight = tileHeight,
                            onClick = onOpenRecommendation,
                        )
                    }
                }
                }
            }
        }
    }
}

private fun qiblaGuidance(qiblaBearing: Int, headingDegrees: Double?): String {
    if (headingDegrees == null) return cardinalDirection(qiblaBearing.toDouble())
    val turn = relativeQiblaTurn(qiblaBearing.toDouble(), headingDegrees)
    return when {
        abs(turn) <= 5.0 -> "Aligned with Qibla"
        turn > 0 -> "Turn right ${turn.roundToInt()}°"
        else -> "Turn left ${abs(turn).roundToInt()}°"
    }
}

private fun formatPrayerTime(hour: Int, minute: Int): String {
    val hour12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$hour12:${minute.toString().padStart(2, '0')} ${if (hour < 12) "AM" else "PM"}"
}

/**
 * Mirrors Android's SmartContentUtils phase logic: the headline shifts as the
 * prayer window ages, so it always gives the user actionable context.
 *
 * When countdown == "Now" the next prayer has just started; even if [currentPrayer]
 * hasn't been updated yet, treat it as starting so the headline is actionable.
 */
private fun SharedPrayerDay.heroHeadline(): String {
    // Treat countdown=="Now" as the prayer just starting, matching Android's behaviour.
    val effectiveCurrent = currentPrayer
        ?: if (countdown == "Now") nextPrayer else null
    if (effectiveCurrent != null) {
        val slot = slots.firstOrNull { it.isCurrent }
            ?: slots.firstOrNull { it.name == effectiveCurrent }
        val elapsed = if (slot != null) {
            val startMin = slot.hour * 60 + slot.minute
            (nowMinute - startMin + 1440) % 1440
        } else 0
        return when {
            elapsed <= 20 -> "Go to Mosque for $effectiveCurrent"
            elapsed <= 60 -> "Best Time to Pray $effectiveCurrent"
            else -> "Make Time for $effectiveCurrent"
        }
    }
    return nextPrayer?.let { "Your next prayer is $it" } ?: "Prayer Times"
}

/**
 * When a prayer is in progress, shows how long ago it started ("7h 42m since
 * Fajr"), matching the Android hero subtitle. Falls back to the place name when
 * no prayer is currently active — so it always reads naturally.
 */
private fun SharedPrayerDay.heroSubtitle(placeName: String): String {
    val effectiveCurrent = currentPrayer
        ?: if (countdown == "Now") nextPrayer else null
        ?: return placeName
    val slot = slots.firstOrNull { it.isCurrent }
        ?: slots.firstOrNull { it.name == effectiveCurrent }
        ?: return placeName
    val startMin = slot.hour * 60 + slot.minute
    val elapsed = (nowMinute - startMin + 1440) % 1440
    if (elapsed <= 0) return placeName
    val h = elapsed / 60
    val m = elapsed % 60
    return if (h > 0) "${h}h ${m}m since $effectiveCurrent" else "${m}m since $effectiveCurrent"
}

private fun SharedPrayerDay.prayerWindowProgress(): Float? {
    val current = currentPrayer?.let { name -> slots.firstOrNull { it.name == name } }
        ?: return null
    val next = nextPrayer?.let { name -> slots.firstOrNull { it.name == name } }
        ?: return null
    val startMinute = current.hour * 60 + current.minute
    var endMinute = next.hour * 60 + next.minute
    if (endMinute <= startMinute) endMinute += 24 * 60
    var currentMinute = nowMinute
    if (currentMinute < startMinute) currentMinute += 24 * 60
    return ((currentMinute - startMinute).toFloat() / (endMinute - startMinute))
        .coerceIn(0f, 1f)
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
    tileHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    arabicTitle: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(tileHeight)
            .clip(RoundedCornerShape(26.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
    available: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FARD_PRAYERS.forEach { prayer ->
            val isDone = prayer in completed
            val isAvailable = prayer in available
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isDone -> Color.White
                                isAvailable -> Color.White.copy(alpha = 0.15f)
                                else -> Color.White.copy(alpha = 0.07f)
                            },
                        )
                        .border(
                            width = 1.5.dp,
                            color = Color.White.copy(
                                alpha = when {
                                    isDone -> 1f
                                    isAvailable -> 0.6f
                                    else -> 0.28f
                                },
                            ),
                            shape = CircleShape,
                        )
                        .clickable(enabled = isAvailable) { onToggle(prayer) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = prayer.take(1),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        // Dark on the filled state, light on the empty one, so
                        // the letter stays legible either way.
                        color = if (isDone) {
                            Color(0xFF1B3A2A)
                        } else {
                            Color.White.copy(alpha = if (isAvailable) 1f else 0.42f)
                        },
                    )
                }
            }
        }
    }
}
