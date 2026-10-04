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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.insight_prayer_background
import com.starception.submission.core.images.resources.insight_prayer_foreground
import com.starception.submission.core.images.resources.insight_qibla_background
import com.starception.submission.core.images.resources.insight_qibla_foreground_v2
import com.starception.submission.core.images.resources.insight_quran_background
import com.starception.submission.core.images.resources.insight_quran_foreground_v2
import com.starception.submission.core.images.resources.insight_suggestion
import com.starception.submission.feature.quran.dailyReading
import com.starception.submission.feature.quran.subtitle
import com.starception.submission.prayer.model.PrayerNotificationPreferences
import com.starception.submission.shared.SharedPrayerDay
import com.starception.submission.shared.audio.QuranAudioPlayer
import com.starception.submission.shared.audio.quranAudioUrl
import com.starception.submission.shared.content.CONTEXTUAL_DUA_CHAPTER_IDS
import com.starception.submission.shared.content.ContextualRecommendation
import com.starception.submission.shared.content.createSharedFortressRepository
import com.starception.submission.shared.content.contextualRecommendation
import com.starception.submission.shared.qibla.HeadingProvider
import com.starception.submission.shared.qibla.HeadingReading
import com.starception.submission.shared.qibla.qiblaBearing
import com.starception.submission.shared.salah.FARD_PRAYERS
import com.starception.submission.shared.salah.SalahProgress
import io.github.alexzhirkevich.cupertino.CupertinoSliderNative
import io.github.alexzhirkevich.cupertino.CupertinoSurface
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

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
    quranPlayer: QuranAudioPlayer,
    onOpenQuran: (Int) -> Unit = {},
    onOpenQibla: () -> Unit = {},
    onOpenRecommendation: () -> Unit = {},
    onOpenFortressChapter: (Int) -> Unit = {},
    onOpenBukhariBook: (Int) -> Unit = {},
    onOpenShamayelBook: (Int) -> Unit = {},
    notifications: PrayerNotificationPreferences = PrayerNotificationPreferences(),
    tileHeight: Dp = 220.dp,
    fullWidthPage: Boolean = false,
    maxPageWidth: Dp? = null,
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
    val nextPrayerText = day.nextPrayer
        ?.let { "${day.displayPrayerName(it)} in ${day.countdown}" }
        .orEmpty()
    var isReadingAudio by remember { mutableStateOf(false) }
    var fortressInvocations by remember {
        mutableStateOf<Map<Int, List<com.starception.submission.shared.content.FortressInvocation>>>(emptyMap())
    }
    var fortressTitles by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    LaunchedEffect(today) {
        val repository = createSharedFortressRepository()
        val invocations = mutableMapOf<Int, List<com.starception.submission.shared.content.FortressInvocation>>()
        val titles = mutableMapOf<Int, String>()
        runCatching {
            repository.getChapters().forEach { chapter ->
                titles[chapter.id] = chapter.title
            }
            CONTEXTUAL_DUA_CHAPTER_IDS.forEach { chapterId ->
                runCatching {
                    invocations[chapterId] = repository.getChapterInvocations(chapterId)
                }
            }
        }
        fortressInvocations = invocations
        fortressTitles = titles
    }
    val currentHour = remember(today) {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    }
    val recommendation = remember(today, currentHour, fortressInvocations, fortressTitles) {
        contextualRecommendation(
            date = today,
            hour = currentHour,
            fortressInvocationsByChapter = fortressInvocations,
            chapterTitles = fortressTitles,
        )
    }
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
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    letterSpacing = (-0.25).sp,
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (maxPageWidth != null) 0.dp else 6.dp),
            ) {
                repeat(pageCount) { index ->
                    val selected = pagerState.currentPage % pageCount == index
                    val indicatorWidth = if (selected) 26.dp else 6.dp
                    Box(
                        modifier = Modifier
                            .then(
                                if (maxPageWidth != null) {
                                    Modifier.size(44.dp)
                                } else {
                                    Modifier.size(width = indicatorWidth, height = 6.dp)
                                },
                            )
                            .semantics {
                                contentDescription = "Insight ${index + 1} of $pageCount"
                                this.selected = selected
                            }
                            .clickable(role = Role.Tab) {
                                pagerScope.launch {
                                    val currentLogicalPage = pagerState.currentPage % pageCount
                                    pagerState.animateScrollToPage(
                                        pagerState.currentPage + (index - currentLogicalPage),
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = indicatorWidth, height = 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                ),
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
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Keep Android's card aspect ratio. Partial-width pages leave a
            // neighboring card visible; a two-pane hero can consume its column.
            val boundedPageWidth = maxPageWidth?.let { minOf(maxWidth, it) } ?: maxWidth
            val pageWidth = if (fullWidthPage) {
                boundedPageWidth
            } else {
                (tileHeight * (250f / 288f))
                    .coerceAtMost(minOf(maxWidth * 0.64f, boundedPageWidth))
                    .coerceAtLeast(140.dp)
            }
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(pageWidth),
                pageSpacing = 14.dp,
                contentPadding = PaddingValues(end = if (fullWidthPage) 0.dp else 18.dp),
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
                            headline = day.heroHeadline(notifications),
                            subtitle = day.heroSubtitle(placeName),
                            nextPrayer = nextPrayerText,
                            forecast = day.temperatureCelsius?.let { "${it.roundToInt()}°C" },
                            timelineProgress = day.prayerWindowProgress(),
                            nowMinute = day.nowMinute,
                            sunriseMinute = day.slots
                                .firstOrNull { it.name == "Sunrise" }
                                ?.let { it.hour * 60 + it.minute }
                                ?: 390,
                            maghribMinute = day.slots
                                .firstOrNull { it.name == "Maghrib" }
                                ?.let { it.hour * 60 + it.minute }
                                ?: 1_080,
                            tileHeight = tileHeight,
                            onClick = onOpenQibla,
                        )

                        1 -> ArtworkTile(
                            artwork = Res.drawable.insight_prayer_background,
                            foreground = Res.drawable.insight_prayer_foreground,
                            foregroundScale = 0.88f,
                            foregroundOffsetYFraction = 0.08f,
                            label = "Today's salah",
                            title = when (salah.completedCount) {
                                FARD_PRAYERS.size -> "All prayers complete"
                                1 -> "1 prayer complete"
                                else -> "${salah.completedCount} prayers complete"
                            },
                            subtitle = when {
                                salah.remainingCount == 0 -> "All five marked for today"
                                salah.remainingCount == 1 && salah.nextUnprayed != null ->
                                    "${salah.nextUnprayed} remains today"
                                salah.nextUnprayed != null ->
                                    "${salah.remainingCount} remain · ${salah.nextUnprayed} is next"
                                else -> "${salah.remainingCount} prayers remain today"
                            },
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
                            // Android's home reading tile: the day's fixed
                            // suggestion with a play control and a progress
                            // slider with time labels.
                            val surah = dailyReading(today)
                            var position by remember { mutableStateOf(0f) }
                            var duration by remember { mutableStateOf(0f) }
                            LaunchedEffect(isReadingAudio) {
                                while (isReadingAudio) {
                                    position = quranPlayer.positionSeconds()
                                    duration = quranPlayer.durationSeconds()
                                    kotlinx.coroutines.delay(500)
                                }
                            }
                            ArtworkTile(
                                artwork = Res.drawable.insight_quran_background,
                                foreground = Res.drawable.insight_quran_foreground_v2,
                                foregroundScale = 0.74f,
                                foregroundOffsetYFraction = 0.09f,
                                label = "Today's reading",
                                title = surah.nameEnglish,
                                subtitle = surah.nameArabic,
                                footerText = surah.subtitle(),
                                tileHeight = tileHeight,
                                topEndContent = {
                                    CupertinoSurface(
                                        onClick = {
                                            if (isReadingAudio) {
                                                quranPlayer.pause()
                                                isReadingAudio = false
                                            } else {
                                                isReadingAudio = quranPlayer.play(quranAudioUrl(surah.number))
                                            }
                                        },
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.35f),
                                        contentColor = Color.White,
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isReadingAudio) {
                                                    Icons.Filled.Pause
                                                } else {
                                                    Icons.Filled.PlayArrow
                                                },
                                                contentDescription = if (isReadingAudio) {
                                                    "Pause recitation"
                                                } else {
                                                    "Play recitation"
                                                },
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    quranPlayer.stop()
                                    isReadingAudio = false
                                    onOpenQuran(surah.number)
                                },
                                content = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 10.dp, start = 12.dp, end = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        if (isReadingAudio && duration > 0f) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text(
                                                    formatTileSeconds(position.toInt()),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                )
                                                CupertinoSliderNative(
                                                    value = (position / duration).coerceIn(0f, 1f),
                                                    onValueChange = { fraction ->
                                                        position = fraction * duration
                                                        quranPlayer.seekTo(position)
                                                    },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(horizontal = 8.dp),
                                                )
                                                Text(
                                                    formatTileSeconds(duration.toInt()),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                )
                                            }
                                        }
                                    }
                                },
                            )
                        }

                        3 -> {
                            // Android's live directional arrow: the needle holds
                            // the bearing while the device heading rotates the
                            // arrow, spring-eased through the shortest angle.
                            val rawRotation = normalizeBearing(
                                qiblaBearing - (heading.headingDegrees?.toFloat() ?: 0f),
                            )
                            var continuousRotation by remember(qiblaBearing) {
                                mutableFloatStateOf(rawRotation)
                            }
                            LaunchedEffect(rawRotation) {
                                continuousRotation += shortestBearingDelta(
                                    continuousRotation,
                                    rawRotation,
                                )
                            }
                            val arrowRotation by animateFloatAsState(
                                targetValue = continuousRotation,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                                label = "liveQiblaDirection",
                            )
                            ArtworkTile(
                                artwork = Res.drawable.insight_qibla_background,
                                foreground = Res.drawable.insight_qibla_foreground_v2,
                                foregroundScale = 0.75f,
                                foregroundOffsetXFraction = 0.025f,
                                foregroundOffsetYFraction = 0.11f,
                                label = "Qibla",
                                title = "$qiblaBearing° toward Makkah",
                                subtitle = qiblaCardinalDirection(qiblaBearing),
                                footerText = "Open the live compass and 3D globe",
                                tileHeight = tileHeight,
                                onClick = onOpenQibla,
                                topEndContent = {
                                    Box {
                                         CupertinoSurface(
                                             onClick = onOpenQibla,
                                             shape = CircleShape,
                                             color = Color.Black.copy(alpha = 0.35f),
                                             contentColor = Color.White,
                                             modifier = Modifier.size(40.dp),
                                         ) {
                                             Box(
                                                 modifier = Modifier.fillMaxSize(),
                                                 contentAlignment = Alignment.Center,
                                             ) {
                                                 // A navigation-style needle holding the
                                                 // bearing against the rotating heading.
                                                 androidx.compose.foundation.Canvas(
                                                     modifier = Modifier
                                                         .size(20.dp)
                                                         .rotate(arrowRotation),
                                                 ) {
                                                     val w = size.width
                                                     val h = size.height
                                                     val arrow = Path().apply {
                                                         moveTo(w / 2f, 0f)
                                                         lineTo(w * 0.92f, h)
                                                         lineTo(w / 2f, h * 0.78f)
                                                         lineTo(w * 0.08f, h)
                                                         close()
                                                     }
                                                     drawPath(arrow, Color.White)
                                                 }
                                             }
                                         }
                                    }
                                },
                            )
                        }

                        else -> ArtworkTile(
                            artwork = Res.drawable.insight_suggestion,
                            label = "AI suggested",
                            title = recommendation.title,
                            subtitle = recommendation.supportingText,
                            footerText = recommendation.footerText,
                            tileHeight = tileHeight,
                            onClick = {
                                when (val target = recommendation.target) {
                                    is ContextualRecommendation.Target.Surah -> onOpenQuran(target.number)
                                    is ContextualRecommendation.Target.FortressDua ->
                                        onOpenFortressChapter(target.chapterId)
                                    is ContextualRecommendation.Target.Bukhari ->
                                        onOpenBukhariBook(target.bookId)
                                    is ContextualRecommendation.Target.Shamayel ->
                                        onOpenShamayelBook(target.bookId)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Normalizes an angle into [0, 360). */
private fun normalizeBearing(value: Float): Float = ((value % 360f) + 360f) % 360f

/** The shortest signed rotation from one bearing to another. */
private fun shortestBearingDelta(from: Float, to: Float): Float =
    normalizeBearing(to - from + 180f) - 180f

/** Android's qiblaCardinalDirection: "Northwest from your location". */
private fun qiblaCardinalDirection(bearing: Int): String {
    val directions = listOf(
        "North",
        "Northeast",
        "East",
        "Southeast",
        "South",
        "Southwest",
        "West",
        "Northwest",
    )
    val normalized = ((bearing % 360) + 360) % 360
    val index = ((normalized + 22.5) / 45.0).toInt() % directions.size
    return "${directions[index]} from your location"
}

private fun formatTileSeconds(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:" + if (seconds < 10) "0$seconds" else seconds.toString()
}

/**
 * Mirrors Android's SmartContentUtils phase logic: the headline shifts as the
 * prayer window ages, so it always gives the user actionable context.
 *
 * When countdown == "Now" the next prayer has just started; even if [currentPrayer]
 * hasn't been updated yet, treat it as starting so the headline is actionable.
 */
internal fun SharedPrayerDay.heroHeadline(
    notifications: PrayerNotificationPreferences,
): String {
    val context = heroPrayerContext()
    if (context != null) {
        val (effectiveCurrent, elapsed) = context
        val displayName = displayPrayerName(effectiveCurrent)
        return when {
            elapsed <= notifications.getGoToMosqueDurationForPrayer(effectiveCurrent) ->
                "Go to Mosque for $displayName"
            elapsed <= 60 -> "Best Time to Pray $displayName"
            else -> "Make Time for $displayName"
        }
    }
    return nextPrayer?.let { "Your next prayer is ${displayPrayerName(it)}" } ?: "Prayer Times"
}

/**
 * When a prayer is in progress, shows how long ago it started ("7h 42m since
 * Fajr"), matching the Android hero subtitle. Between Isha and Fajr, use the
 * live countdown rather than spending the card's supporting line on a location
 * that is already visible below the prayer schedule.
 */
internal fun SharedPrayerDay.heroSubtitle(placeName: String): String {
    val context = heroPrayerContext() ?: return nextPrayer?.let { prayer ->
        if (countdown.isNotBlank()) "$countdown until ${displayPrayerName(prayer)}" else placeName
    } ?: placeName
    val (prayer, elapsed) = context
    val elapsedText = when {
        elapsed == 0 -> "Just started"
        elapsed == 1 -> "1 minute"
        elapsed < 60 -> "$elapsed minutes"
        elapsed % 60 == 0 -> "${elapsed / 60}h"
        else -> "${elapsed / 60}h ${elapsed % 60}m"
    }
    return "$elapsedText since ${displayPrayerName(prayer)}"
}

private fun SharedPrayerDay.heroPrayerContext(): Pair<String, Int>? {
    val effectiveCurrent = currentPrayer ?: if (countdown == "Now") nextPrayer else null
    if (effectiveCurrent != null) {
        val slot = slots.firstOrNull { it.isCurrent }
            ?: slots.firstOrNull { it.name == effectiveCurrent }
            ?: return effectiveCurrent to 0
        return effectiveCurrent to ((nowMinute - (slot.hour * 60 + slot.minute) + 1440) % 1440)
    }
    val fajr = slots.firstOrNull { it.name == "Fajr" } ?: return null
    val isha = slots.firstOrNull { it.name == "Isha" } ?: return null
    val fajrMinute = fajr.hour * 60 + fajr.minute
    if (nowMinute >= fajrMinute) return null
    val elapsedSinceIsha = nowMinute + 1440 - (isha.hour * 60 + isha.minute)
    return if (elapsedSinceIsha in 0..720) "Isha" to elapsedSinceIsha else null
}

private fun SharedPrayerDay.displayPrayerName(name: String): String =
    if (isFriday && name == "Dhuhr") "Jumu'ah" else name

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

/** Cupertino rendering of Android's artwork insight card. */
@Composable
private fun ArtworkTile(
    artwork: DrawableResource,
    foreground: DrawableResource? = null,
    foregroundScale: Float = 1f,
    foregroundOffsetXFraction: Float = 0f,
    foregroundOffsetYFraction: Float = 0f,
    label: String,
    title: String,
    subtitle: String,
    tileHeight: Dp,
    modifier: Modifier = Modifier,
    footerText: String? = null,
    topEndContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(26.dp)
    CupertinoSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(tileHeight),
        shape = shape,
        color = Color(0xFF635A56),
        shadowElevation = 6.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
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
                        translationX = size.width * foregroundOffsetXFraction
                        translationY = size.height * foregroundOffsetYFraction
                    },
            )
        }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.50f to Color.Transparent,
                                0.72f to Color(0xFF241D19).copy(alpha = 0.12f),
                                1f to Color(0xFF241D19).copy(alpha = 0.66f),
                            ),
                        ),
                    ),
            )
            CupertinoSurface(
                onClick = onClick ?: {},
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.34f),
                contentColor = Color.White,
                modifier = Modifier
                    .padding(11.dp)
                    .defaultMinSize(minWidth = 58.dp, minHeight = 40.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            letterSpacing = 0.sp,
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        topEndContent?.let { trailing ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 11.dp, end = 11.dp),
            ) {
                trailing()
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            content?.invoke()
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    letterSpacing = (-0.2).sp,
                ),
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        letterSpacing = 0.sp,
                    ),
                    color = Color.White.copy(alpha = 0.88f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (footerText != null) {
                Text(
                    text = footerText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        letterSpacing = 0.sp,
                    ),
                    color = Color.White.copy(alpha = 0.78f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
internal fun SalahMarkers(
    completed: Set<String>,
    available: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FARD_PRAYERS.forEach { prayer ->
            val isDone = prayer in completed
            val isAvailable = prayer in available
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(42.dp)
                    .semantics {
                        contentDescription = when {
                            isDone -> "$prayer completed. Tap to unmark"
                            isAvailable -> "$prayer not completed. Tap to mark"
                            else -> "$prayer prayer time has not arrived"
                        }
                        selected = isDone
                    }
                    .clickable(enabled = isAvailable || isDone) { onToggle(prayer) },
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier.wrapContentWidth(unbounded = true),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                color = when {
                                    isDone -> MaterialTheme.colorScheme.primary
                                    isAvailable -> Color.Black.copy(alpha = 0.28f)
                                    else -> Color.Black.copy(alpha = 0.14f)
                                },
                                shape = CircleShape,
                            )
                            .border(
                                width = 1.dp,
                                color = when {
                                    isDone -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                    isAvailable -> Color.White.copy(alpha = 0.48f)
                                    else -> Color.White.copy(alpha = 0.24f)
                                },
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isDone) "✓" else prayer.take(1),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 11.sp,
                            ),
                            color = if (isDone) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                Color.White.copy(alpha = if (isAvailable) 0.78f else 0.38f)
                            },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = prayer,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            lineHeight = 9.5.sp,
                            letterSpacing = 0.sp,
                        ),
                        color = Color.White.copy(
                            alpha = if (isAvailable || isDone) 0.72f else 0.40f,
                        ),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
