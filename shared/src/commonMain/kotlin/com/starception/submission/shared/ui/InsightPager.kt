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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.starception.submission.shared.SharedPrayerSlot
import com.starception.submission.shared.audio.QuranAudioPlayer
import com.starception.submission.shared.audio.quranAudioUrl
import com.starception.submission.shared.content.CONTEXTUAL_DUA_CHAPTER_IDS
import com.starception.submission.shared.content.ContextualRecommendation
import com.starception.submission.shared.content.SharedContentStore
import com.starception.submission.shared.content.contextualRecommendation
import com.starception.submission.shared.content.createSharedFortressRepository
import com.starception.submission.shared.ml.DeenlyKnowledgeRuntime
import com.starception.submission.shared.qibla.HeadingProvider
import com.starception.submission.shared.qibla.HeadingReading
import com.starception.submission.shared.qibla.qiblaBearing
import com.starception.submission.shared.quran.QuranArabicFonts
import com.starception.submission.shared.quran.QuranTranslationLanguage
import com.starception.submission.shared.quran.QuranVerse
import com.starception.submission.shared.quran.createQuranVerseRepository
import com.starception.submission.shared.salah.FARD_PRAYERS
import com.starception.submission.shared.salah.SalahProgress
import io.github.alexzhirkevich.cupertino.CupertinoSurface
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Clock

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
    initialExpandedPage: Int? = null,
    collapseExpanded: Boolean = false,
    onExpandedPageChange: (Int?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val primaryPageCount = 5
    val hadithDetailPage = 5
    val readingDetailPage = 6
    val expandedPages = listOf(0, 1, hadithDetailPage, 2, readingDetailPage, 3, 4)
    var expandedLogicalPage by rememberSaveable { mutableStateOf<Int?>(initialExpandedPage) }
    var lastExpandedLogicalPage by rememberSaveable {
        mutableIntStateOf(initialExpandedPage ?: 0)
    }
    val collapsedPages = remember { List(primaryPageCount) { it } }
    val collapsedPagerState = rememberPagerState(
        initialPage = primaryPageCount,
        pageCount = { primaryPageCount * 3 },
    )
    val expandedPagerState = rememberPagerState(
        initialPage = expandedPages.size + expandedPages.indexOf(initialExpandedPage).coerceAtLeast(0),
        pageCount = { expandedPages.size * 3 },
    )
    val isExpanded = expandedLogicalPage != null
    val visiblePages = if (isExpanded) expandedPages else collapsedPages
    val pageCount = visiblePages.size
    val middleLoopStart = pageCount
    // Each page topology owns a fixed physical pager. Reusing one state while
    // changing 5-page modulo mapping to 7 pages could reinterpret a stale page
    // as a different source insight during expansion.
    val pagerState = if (isExpanded) expandedPagerState else collapsedPagerState
    val pagerScope = rememberCoroutineScope()
    var expandedPagerReady by remember { mutableStateOf(false) }
    LaunchedEffect(collapseExpanded) {
        if (collapseExpanded) expandedLogicalPage = null
    }
    LaunchedEffect(expandedLogicalPage) {
        expandedLogicalPage?.let { lastExpandedLogicalPage = it }
        onExpandedPageChange(expandedLogicalPage)
    }
    LaunchedEffect(isExpanded) {
        expandedPagerReady = false
        val sourcePage = expandedLogicalPage ?: lastExpandedLogicalPage
        val targetIndex = if (isExpanded) expandedPages.indexOf(sourcePage) else sourcePage
        pagerState.scrollToPage(pageCount + targetIndex.coerceAtLeast(0))
        expandedPagerReady = isExpanded
    }
    val expansionProgress by animateFloatAsState(
        targetValue = if (expandedLogicalPage != null) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 250f,
        ),
        label = "insightExpansionProgress",
    )
    val effectiveTileHeight by animateDpAsState(
        targetValue = if (expandedLogicalPage != null) {
            // Android's expansion budget: the tile grows by exactly the space
            // the schedule frees (one 96dp row + 8dp gap) with the remaining
            // row staying at its normal 96dp height, so every control below
            // keeps its resting placement and the location card cannot drift
            // into the floating navigation pill.
            tileHeight + 104.dp
        } else {
            tileHeight
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "iosInsightTileHeight",
    )
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
    LaunchedEffect(today) {
        quranPlayer.stop()
        com.starception.submission.shared.media.QuranPlaybackBus.clear()
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
    LaunchedEffect(pagerState.settledPage, expandedLogicalPage, expandedPagerReady) {
        if (expandedLogicalPage != null && expandedPagerReady) {
            val visiblePage = visiblePages[pagerState.settledPage % pageCount]
            expandedLogicalPage = when (visiblePage) {
                hadithDetailPage -> 1
                readingDetailPage -> 2
                else -> visiblePage
            }
        }
    }
    val qiblaPageVisible = visiblePages[pagerState.settledPage % pageCount] == 3
    DisposableEffect(headingProvider, qiblaPageVisible) {
        if (qiblaPageVisible) headingProvider.start { heading = it }
        onDispose { headingProvider.stop() }
    }
    LaunchedEffect(
        pagerState.settledPage,
        pagerState.isScrollInProgress,
        expandedLogicalPage,
    ) {
        autoAdvanceProgress.snapTo(0f)
        if (pagerState.isScrollInProgress || expandedLogicalPage != null) return@LaunchedEffect
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
                repeat(primaryPageCount) { index ->
                    val currentVisiblePage = visiblePages[pagerState.currentPage % pageCount]
                    val selected = currentVisiblePage == index ||
                        (currentVisiblePage == hadithDetailPage && index == 1) ||
                        (currentVisiblePage == readingDetailPage && index == 2)
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
                                contentDescription = "Insight ${index + 1} of $primaryPageCount"
                                this.selected = selected
                            }
                            .clickable(role = Role.Tab) {
                                pagerScope.launch {
                                    val targetVisibleIndex = visiblePages.indexOf(index)
                                    val currentVisibleIndex = pagerState.currentPage % pageCount
                                    pagerState.animateScrollToPage(
                                        pagerState.currentPage + (targetVisibleIndex - currentVisibleIndex),
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
            // The spring may briefly overshoot, but pager spacing and padding
            // must remain non-negative.
            val layoutExpansionProgress = expansionProgress.coerceIn(0f, 1f)
            val boundedPageWidth = maxPageWidth?.let { minOf(maxWidth, it) } ?: maxWidth
            val restingPageWidth = if (fullWidthPage) {
                boundedPageWidth
            } else {
                (effectiveTileHeight * (250f / 288f))
                    .coerceAtMost(minOf(maxWidth * 0.64f, boundedPageWidth))
                    .coerceAtLeast(140.dp)
            }
            val pageWidth = restingPageWidth +
                ((boundedPageWidth - restingPageWidth) * layoutExpansionProgress)
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(pageWidth),
                pageSpacing = 14.dp * (1f - layoutExpansionProgress),
                contentPadding = PaddingValues(
                    end = if (fullWidthPage) 0.dp else 18.dp * (1f - layoutExpansionProgress),
                ),
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxWidth().height(effectiveTileHeight),
            ) { page ->
                val logicalPage = visiblePages[page % pageCount]
                val sourceLogicalPage = when (logicalPage) {
                    hadithDetailPage -> 1
                    readingDetailPage -> 2
                    else -> logicalPage
                }
                val isExpanded = expandedLogicalPage == sourceLogicalPage
                val onLongClick = {
                    expandedLogicalPage = if (isExpanded) null else sourceLogicalPage
                }
                // Keep the page offset observable at the pager level. The iOS
                // graphics layer does not consistently invalidate when the
                // pager state is read through a nested callback, so deriving
                // it here makes every artwork layer follow the live drag.
                val pageOffset by remember(pagerState, page) {
                    derivedStateOf {
                        ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            .coerceIn(-1f, 1f)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val focus = 1f - abs(pageOffset)
                            val restingScale = 0.97f + (0.03f * focus)
                            val restingAlpha = 0.9f + (0.1f * focus)
                            scaleX = restingScale + ((1f - restingScale) * expansionProgress)
                            scaleY = restingScale + ((1f - restingScale) * expansionProgress)
                            alpha = restingAlpha + ((1f - restingAlpha) * expansionProgress)
                        },
                ) {
                    when (logicalPage) {
                        0 -> PrayerNowTile(
                            backgroundPageOffset = { pageOffset },
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
                            tileHeight = effectiveTileHeight,
                            isExpanded = isExpanded,
                            onClick = onOpenQibla,
                            onLongClick = onLongClick,
                            expandedContent = if (isExpanded) {
                                {
                                    ExpandedPrayerLine(
                                        nextPrayerName = day.nextPrayer,
                                        slots = day.slots,
                                    )
                                }
                            } else {
                                null
                            },
                        )

                        1 -> ArtworkTile(
                            backgroundPageOffset = { pageOffset },
                            artwork = Res.drawable.insight_prayer_background,
                            foreground = Res.drawable.insight_prayer_foreground,
                            foregroundScale = 0.84f,
                            foregroundOffsetYFraction = 0.08f,
                            isExpanded = isExpanded,
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
                            tileHeight = effectiveTileHeight,
                            onLongClick = onLongClick,
                        ) {
                            SalahMarkers(
                                completed = salah.completed,
                                available = day.slots
                                    .filter { it.hasStarted && it.name in FARD_PRAYERS }
                                    .mapTo(mutableSetOf()) { it.name },
                                onToggle = onTogglePrayer,
                            )
                        }

                        hadithDetailPage -> ArtworkTile(
                            backgroundPageOffset = { pageOffset },
                            artwork = Res.drawable.insight_prayer_background,
                            foreground = Res.drawable.insight_prayer_foreground,
                            foregroundScale = 0.84f,
                            foregroundOffsetYFraction = 0.08f,
                            isExpanded = true,
                            label = "Today's salah",
                            title = "Prayer guidance",
                            subtitle = salah.nextUnprayed ?: "Salah",
                            tileHeight = effectiveTileHeight,
                            onLongClick = onLongClick,
                        ) {
                            ExpandedSalahHadith(
                                nextPrayer = salah.nextUnprayed,
                                modifier = Modifier.fillMaxSize(),
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
                                backgroundPageOffset = { pageOffset },
                                artwork = Res.drawable.insight_quran_background,
                                foreground = Res.drawable.insight_quran_foreground_v2,
                                foregroundScale = 0.74f,
                                foregroundOffsetYFraction = 0.09f,
                                isExpanded = isExpanded,
                                label = "Today's reading",
                                title = surah.nameEnglish,
                                subtitle = surah.nameArabic,
                                footerText = surah.subtitle(),
                                tileHeight = effectiveTileHeight,
                                onLongClick = onLongClick,
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
                                    com.starception.submission.shared.media.QuranPlaybackBus.clear()
                                    isReadingAudio = false
                                    onOpenQuran(surah.number)
                                },
                                content = {
                                    if (isReadingAudio && duration > 0f) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 10.dp, start = 12.dp, end = 12.dp),
                                        ) {
                                            Text(
                                                formatTileSeconds(position.toInt()),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                            )
                                            Slider(
                                                value = (position / duration).coerceIn(0f, 1f),
                                                onValueChange = { fraction ->
                                                    position = fraction * duration
                                                    quranPlayer.seekTo(position)
                                                },
                                                colors = SliderDefaults.colors(
                                                    thumbColor = Color.White,
                                                    activeTrackColor = Color.White,
                                                    inactiveTrackColor = Color.White.copy(alpha = 0.38f),
                                                ),
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
                                },
                            )
                        }

                        readingDetailPage -> {
                            val surah = dailyReading(today)
                            ArtworkTile(
                                backgroundPageOffset = { pageOffset },
                                artwork = Res.drawable.insight_quran_background,
                                foreground = Res.drawable.insight_quran_foreground_v2,
                                foregroundScale = 0.74f,
                                foregroundOffsetYFraction = 0.09f,
                                isExpanded = true,
                                label = "Today's reading",
                                title = surah.nameEnglish,
                                subtitle = surah.nameArabic,
                                tileHeight = effectiveTileHeight,
                                onLongClick = onLongClick,
                                onClick = {
                                    quranPlayer.stop()
                                    com.starception.submission.shared.media.QuranPlaybackBus.clear()
                                    isReadingAudio = false
                                    onOpenQuran(surah.number)
                                },
                            ) {
                                ExpandedReadingPreview(
                                    surahNumber = surah.number,
                                    surahName = surah.nameEnglish,
                                    isPlaying = isReadingAudio,
                                )
                            }
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
                                backgroundPageOffset = { pageOffset },
                                artwork = Res.drawable.insight_qibla_background,
                                foreground = if (isExpanded) {
                                    null
                                } else {
                                    Res.drawable.insight_qibla_foreground_v2
                                },
                                foregroundScale = 0.75f,
                                foregroundOffsetXFraction = 0.025f,
                                foregroundOffsetYFraction = 0.11f,
                                isExpanded = isExpanded,
                                label = "Qibla",
                                title = if (isExpanded) "" else "$qiblaBearing° toward Makkah",
                                subtitle = if (isExpanded) "" else qiblaCardinalDirection(qiblaBearing),
                                footerText = if (isExpanded) null else "Open the live compass and 3D globe",
                                tileHeight = effectiveTileHeight,
                                onClick = onOpenQibla,
                                onLongClick = onLongClick,
                                content = if (isExpanded) {
                                    {
                                        ExpandedQiblaRadar(
                                            bearing = qiblaBearing,
                                            headingDegrees = heading.headingDegrees?.toFloat(),
                                            needleRotation = arrowRotation,
                                        )
                                    }
                                } else {
                                    null
                                },
                                topEndContent = if (isExpanded) {
                                    null
                                } else {
                                    {
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
                                    }
                                },
                            )
                        }

                        else -> {
                            val surah = dailyReading(today)
                            // Android's RecommendationExpandedStage: the
                            // enlarged state shows the model-generated
                            // grounded suggestion over the day's reading,
                            // falling back to the deterministic card when
                            // the GGUF is missing or its output is rejected.
                            // The model download is owned by the runtime, so
                            // it survives leaving this tile.
                            var modelCard by remember(today) {
                                mutableStateOf<DeenlyKnowledgeCard?>(null)
                            }
                            var modelLoading by remember(today) { mutableStateOf(false) }
                            var modelMissing by remember(today) { mutableStateOf(false) }
                            val downloadProgress by DeenlyKnowledgeRuntime.downloadProgress.collectAsState()
                            val downloadCompleted by DeenlyKnowledgeRuntime.downloadCompleted.collectAsState()

                            LaunchedEffect(isExpanded, today, downloadCompleted) {
                                modelCard = null
                                modelLoading = false
                                modelMissing = false
                                if (!isExpanded) return@LaunchedEffect
                                val runtime = DeenlyKnowledgeRuntime
                                if (!runtime.isKnowledgeModelAvailable()) {
                                    modelMissing = true
                                    return@LaunchedEffect
                                }
                                modelLoading = true
                                modelCard = runCatching {
                                    val store = SharedContentStore()
                                    val translationLanguage = QuranTranslationLanguage.fromCode(
                                        store.quranTranslationLanguage(),
                                    )
                                    val verses = createQuranVerseRepository()
                                        .getVersesBySurah(surah.number, translationLanguage)
                                        .take(3)
                                    val sourceText = verses
                                        .mapNotNull { it.translation.takeIf(String::isNotBlank) }
                                        .joinToString(" ")
                                        .ifBlank { return@runCatching null }
                                    val title = runtime.generateKnowledgeTitle(
                                        collection = "Holy Quran",
                                        reference = "Surah ${surah.number} · ${surah.nameEnglish}",
                                        topic = "Daily reading",
                                        appSituation = null,
                                        sourceText = sourceText,
                                    ) ?: return@runCatching null
                                    DeenlyKnowledgeCard(title = title, sourceText = sourceText)
                                }.getOrNull()
                                modelLoading = false
                            }

                            ArtworkTile(
                                backgroundPageOffset = { pageOffset },
                                artwork = Res.drawable.insight_suggestion,
                                isExpanded = isExpanded,
                                label = "AI suggested",
                                title = if (isExpanded && modelCard != null) "" else recommendation.title,
                                subtitle = if (isExpanded) "" else recommendation.supportingText,
                                footerText = if (isExpanded) null else recommendation.footerText,
                                tileHeight = effectiveTileHeight,
                                onLongClick = onLongClick,
                                content = if (isExpanded) {
                                    {
                                        when {
                                            modelLoading -> ModelSuggestionLoading()
                                            modelCard != null -> ModelSuggestionStage(
                                                card = modelCard,
                                                onOpen = { onOpenQuran(surah.number) },
                                            )
                                            modelMissing -> MissingModelDownloadCard(
                                                downloading = downloadProgress != null,
                                                downloadFraction = downloadProgress ?: 0f,
                                                onDownload = {
                                                    DeenlyKnowledgeRuntime.startModelDownload()
                                                },
                                            )
                                            else -> ExpandedSuggestion(
                                                supportingText = recommendation.supportingText,
                                                footerText = recommendation.footerText,
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                                onClick = {
                                    if (isExpanded && modelCard != null) {
                                        onOpenQuran(surah.number)
                                    } else {
                                        when (val target = recommendation.target) {
                                            is ContextualRecommendation.Target.Surah -> onOpenQuran(target.number)
                                            is ContextualRecommendation.Target.FortressDua ->
                                                onOpenFortressChapter(target.chapterId)
                                            is ContextualRecommendation.Target.Bukhari ->
                                                onOpenBukhariBook(target.bookId)
                                            is ContextualRecommendation.Target.Shamayel ->
                                                onOpenShamayelBook(target.bookId)
                                        }
                                    }
                                },
                            )
                        }
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

private val ReadingPreviewGold = Color(0xFFD8AB59)

private fun Int.toArabicIndic(): String = toString().map { digit ->
    if (digit in '0'..'9') ('٠'.code + (digit - '0')).toChar() else digit
}.joinToString("")

/** Android-parity Quran rendering used only by an enlarged reading tile. */
@Composable
private fun ExpandedReadingPreview(
    surahNumber: Int,
    surahName: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val store = remember { SharedContentStore() }
    val mushafMode = remember { store.quranMushafMode() }
    val arabicFont = remember { store.quranArabicFont() }
    val arabicFontSize = remember { store.quranArabicFontSize() }
    val translationLanguage = remember {
        QuranTranslationLanguage.fromCode(store.quranTranslationLanguage())
    }
    val repository = remember { createQuranVerseRepository() }
    var verses by remember(surahNumber) { mutableStateOf<List<QuranVerse>>(emptyList()) }
    var activeIndex by remember(surahNumber) { mutableIntStateOf(0) }
    LaunchedEffect(surahNumber, translationLanguage) {
        verses = runCatching {
            repository.getVersesBySurah(surahNumber, translationLanguage).take(3)
        }.getOrDefault(emptyList())
        activeIndex = 0
    }
    LaunchedEffect(verses.size, mushafMode) {
        if (!mushafMode && verses.size > 1) {
            while (true) {
                kotlinx.coroutines.delay(5_200L)
                activeIndex = (activeIndex + 1) % verses.size
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.38f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SURAH $surahNumber · ${surahName.uppercase()}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        letterSpacing = 1.sp,
                    ),
                    color = Color.White.copy(alpha = 0.68f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = when {
                        isPlaying -> "PLAYING"
                        mushafMode -> verses.firstOrNull()?.page?.let { "MUSHAF · PAGE $it" } ?: "MUSHAF"
                        else -> "${activeIndex + 1}/${verses.size.coerceAtLeast(1)}"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        letterSpacing = 0.8.sp,
                    ),
                    color = ReadingPreviewGold,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (mushafMode) {
                AnimatedContent(
                    targetState = verses,
                    transitionSpec = {
                        (fadeIn(tween(380)) + scaleIn(initialScale = 0.985f, animationSpec = tween(420))) togetherWith
                            (fadeOut(tween(180)) + scaleOut(targetScale = 0.985f, animationSpec = tween(220)))
                    },
                    label = "expandedMushafPassage",
                ) { passage ->
                    Text(
                        text = passage.take(3).joinToString(" ") { verse ->
                            "${verse.arabicText.trim()} ﴿${verse.numberInSurah.toArabicIndic()}﴾"
                        }.ifBlank { "Preparing today's passage…" },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = QuranArabicFonts.fontFamily(arabicFont),
                            fontSize = (arabicFontSize * 0.46f).coerceIn(17f, 23f).sp,
                            lineHeight = (arabicFontSize * 0.70f).coerceIn(27f, 34f).sp,
                            letterSpacing = 0.sp,
                        ),
                        color = Color.White.copy(alpha = if (passage.isEmpty()) 0.58f else 0.96f),
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                AnimatedContent(
                    targetState = verses.getOrNull(activeIndex),
                    transitionSpec = {
                        (fadeIn(tween(380)) + scaleIn(initialScale = 0.985f, animationSpec = tween(420))) togetherWith
                            (fadeOut(tween(180)) + scaleOut(targetScale = 0.985f, animationSpec = tween(220)))
                    },
                    label = "expandedReadingAyah",
                ) { verse ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = verse?.arabicText?.trim().orEmpty()
                                .ifBlank { "Preparing today's passage…" },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = QuranArabicFonts.fontFamily(arabicFont),
                                fontSize = (arabicFontSize * 0.46f).coerceIn(17f, 23f).sp,
                                lineHeight = (arabicFontSize * 0.66f).coerceIn(26f, 32f).sp,
                                letterSpacing = 0.sp,
                            ),
                            color = Color.White.copy(alpha = if (verse == null) 0.58f else 0.96f),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        verse?.translation?.takeIf(String::isNotBlank)?.let { translation ->
                            Text(
                                text = translation,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                ),
                                color = Color.White.copy(alpha = 0.66f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Android's PrayerTimelineExpandedStage, lean: the day's five prayers with
 * the next one highlighted in the reference gold.
 */
@Composable
private fun ExpandedPrayerLine(
    nextPrayerName: String?,
    slots: List<SharedPrayerSlot>,
) {
    val prayers = slots.filter { it.name in FARD_PRAYERS }
    if (prayers.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.36f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = "TODAY'S PRAYER LINE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                letterSpacing = 1.2.sp,
            ),
            color = Color.White.copy(alpha = 0.68f),
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            prayers.forEach { slot ->
                val highlighted = slot.name.equals(nextPrayerName, ignoreCase = true)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.widthIn(min = 44.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (highlighted) 8.dp else 5.dp)
                            .background(
                                if (highlighted) ReadingPreviewGold else Color.White.copy(alpha = 0.30f),
                                CircleShape,
                            ),
                    )
                    Text(
                        text = slot.name,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                        ),
                        color = if (highlighted) ReadingPreviewGold else Color.White.copy(alpha = 0.72f),
                        fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                    Text(
                        text = formatTileClock(slot.hour, slot.minute),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                        ),
                        color = Color.White.copy(alpha = if (highlighted) 0.96f else 0.58f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Degrees to radians for the radar drawing, common to all platforms. */
private fun radians(degrees: Double): Double = degrees * PI / 180.0

/** 12-hour clock label, matching the Android dial's formatting. */
private fun formatTileClock(hour: Int, minute: Int): String {
    val suffix = if (hour < 12) "AM" else "PM"
    val h = ((hour + 11) % 12) + 1
    return "$h:${minute.toString().padStart(2, '0')} $suffix"
}

/**
 * Android's SalahHadithExpandedStage, lean: the staged fallback narration for
 * the next unprayed prayer with its source reference.
 */
@Composable
private fun ExpandedSalahHadith(
    nextPrayer: String?,
    modifier: Modifier = Modifier,
) {
    val displayName = nextPrayer?.replaceFirstChar { it.uppercase() } ?: "Salah"
    val (text, source) = when (nextPrayer?.lowercase()) {
        "fajr", "asr" ->
            "Whoever prays the two cool prayers will enter Paradise." to "Sahih al-Bukhari · 574"
        "isha" ->
            "If they knew the reward of the night prayer, they would come to it " +
                "even if they had to crawl." to "Sahih al-Bukhari · 615"
        else ->
            "Prayer in congregation is twenty-seven times superior to prayer " +
                "offered alone." to "Sahih al-Bukhari · 645"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.36f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "\u201C",
                style = MaterialTheme.typography.titleLarge,
                color = ReadingPreviewGold.copy(alpha = 0.55f),
                modifier = Modifier.padding(end = 6.dp),
            )
            Text(
                text = "HADITH FOR ${displayName.uppercase()}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp,
                    letterSpacing = 1.2.sp,
                ),
                color = ReadingPreviewGold,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = source,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 12.sp,
            ),
            color = Color.White.copy(alpha = 0.55f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * Lean port of Android's QiblaBearingExpandedStage: a perspective radar with
 * the rotating rose, a faceted needle holding the bearing, and turn guidance.
 */
@Composable
private fun ExpandedQiblaRadar(
    bearing: Int,
    headingDegrees: Float?,
    needleRotation: Float?,
    modifier: Modifier = Modifier,
) {
    val turn = headingDegrees?.let { shortestBearingDelta(it, bearing.toFloat()) }
    val instruction = when {
        turn == null -> "Open the live compass for turn guidance"
        abs(turn) < 6f -> "You are facing the Qibla"
        turn > 0f -> "Turn ${abs(turn).roundToInt()}° right"
        else -> "Turn ${abs(turn).roundToInt()}° left"
    }
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black.copy(alpha = 0.36f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(22.dp),
                ),
        ) {
            val density = LocalDensity.current
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val radiusX = (size.width / 2f) - 18.dp.toPx()
                val radiusY = (size.height / 2f) - 12.dp.toPx()

                // Perspective depth rings.
                listOf(1f, 0.62f, 0.30f).forEachIndexed { index, scale ->
                    drawOval(
                        color = Color.White.copy(alpha = if (index == 0) 0.22f else 0.10f),
                        topLeft = Offset(centerX - radiusX * scale, centerY - radiusY * scale),
                        size = Size(radiusX * 2f * scale, radiusY * 2f * scale),
                        style = Stroke(width = if (index == 0) 1.2.dp.toPx() else 0.8.dp.toPx()),
                    )
                }

                // The rose rotates against the live heading.
                val roseHeading = headingDegrees ?: 0f
                repeat(48) { index ->
                    val rotated = radians(
                        (index * 7.5f - roseHeading - 90f).toDouble(),
                    )
                    val rx = cos(rotated).toFloat()
                    val ry = sin(rotated).toFloat()
                    val major = index % 6 == 0
                    drawLine(
                        color = Color.White.copy(alpha = if (major) 0.55f else 0.22f),
                        start = Offset(
                            centerX + rx * radiusX * (if (major) 0.88f else 0.93f),
                            centerY + ry * radiusY * (if (major) 0.88f else 0.93f),
                        ),
                        end = Offset(
                            centerX + rx * radiusX,
                            centerY + ry * radiusY,
                        ),
                        strokeWidth = if (major) 1.3.dp.toPx() else 0.7.dp.toPx(),
                    )
                }

                // Faceted needle: gold point at the bearing, white
                // counterweight behind the center, pivoting with the heading.
                val needleAngle = radians(
                    ((needleRotation ?: bearing.toFloat()) - 90f).toDouble(),
                )
                val dirX = kotlin.math.cos(needleAngle).toFloat()
                val dirY = kotlin.math.sin(needleAngle).toFloat()
                val normalX = -dirY
                val normalY = dirX
                val tipX = centerX + dirX * radiusX * 0.72f
                val tipY = centerY + dirY * radiusY * 0.72f
                val tailX = centerX - dirX * radiusX * 0.40f
                val tailY = centerY - dirY * radiusY * 0.40f
                val pointHalf = 4.dp.toPx()
                val tailHalf = 3.dp.toPx()
                val goldPoint = Path().apply {
                    moveTo(centerX + normalX * pointHalf, centerY + normalY * pointHalf)
                    lineTo(tipX, tipY)
                    lineTo(centerX - normalX * pointHalf, centerY - normalY * pointHalf)
                    close()
                }
                drawPath(goldPoint, ReadingPreviewGold)
                val counterweight = Path().apply {
                    moveTo(centerX + normalX * tailHalf, centerY + normalY * tailHalf)
                    lineTo(tailX, tailY)
                    lineTo(centerX - normalX * tailHalf, centerY - normalY * tailHalf)
                    close()
                }
                drawPath(counterweight, Color.White.copy(alpha = 0.86f))
                drawCircle(Color.White.copy(alpha = 0.30f), radius = 1.8.dp.toPx(), center = Offset(centerX, centerY))
            }

            // Cardinal labels orbit the rose; keep them their own layers so
            // they stay crisp while the heading changes every frame.
            listOf("N", "E", "S", "W").forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                    ),
                    color = if (label == "N") {
                        ReadingPreviewGold
                    } else {
                        Color.White.copy(alpha = 0.62f)
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            val angle = radians(
                                (index * 90f - (headingDegrees ?: 0f) - 90f).toDouble(),
                            )
                            translationX = cos(angle).toFloat() *
                                (maxWidth.toPx() / 2f - 22.dp.toPx()) * 0.92f
                            translationY = sin(angle).toFloat() *
                                (maxHeight.toPx() / 2f - 16.dp.toPx()) * 0.92f
                        },
                )
            }
        }
        Text(
            text = instruction,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                lineHeight = 15.sp,
            ),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = headingDegrees
                    ?.let { "Heading ${it.roundToInt()}°" }
                    ?: "$bearing° to Makkah",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.60f),
                maxLines = 1,
            )
        }
    }
}

/**
 * Android's RecommendationExpandedStage, lean: the full grounded suggestion
 * with the reference header and the verified-source hint.
 */
@Composable
private fun ExpandedSuggestion(
    supportingText: String,
    footerText: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "✦",
                style = MaterialTheme.typography.titleLarge,
                color = ReadingPreviewGold,
            )
            Text(
                text = "GROUNDED SUGGESTION",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    lineHeight = 11.sp,
                    letterSpacing = 1.1.sp,
                ),
                color = Color.White.copy(alpha = 0.64f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Text(
            text = supportingText,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 15.sp,
                lineHeight = 20.sp,
            ),
            color = Color.White,
            fontWeight = FontWeight.Medium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        footerText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                ),
                color = Color.White.copy(alpha = 0.66f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "Tap to open the verified source",
            style = MaterialTheme.typography.labelSmall,
            color = ReadingPreviewGold,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** The model-generated grounded suggestion shown by the enlarged AI tile. */
private data class DeenlyKnowledgeCard(
    val title: String,
    val sourceText: String,
)

/** Android's RecommendationExpandedStage loading beat. */
@Composable
private fun ModelSuggestionLoading() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        CircularProgressIndicator(
            color = ReadingPreviewGold,
            strokeWidth = 2.dp,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = "Reading your downloaded sources…",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.72f),
        )
    }
}

/** The grounded card: the model's title over the exact source text. */
@Composable
private fun ModelSuggestionStage(
    card: DeenlyKnowledgeCard?,
    onOpen: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics {
            contentDescription = "Model-generated suggestion"
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "✦",
                style = MaterialTheme.typography.titleLarge,
                color = ReadingPreviewGold,
            )
            Text(
                text = "GROUNDED SUGGESTION",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    lineHeight = 11.sp,
                    letterSpacing = 1.1.sp,
                ),
                color = Color.White.copy(alpha = 0.64f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Text(
            text = card?.title.orEmpty(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 15.5.sp,
                lineHeight = 20.5.sp,
            ),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = card?.sourceText.orEmpty(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
            ),
            color = Color.White.copy(alpha = 0.84f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Tap to open the verified source",
            style = MaterialTheme.typography.labelSmall,
            color = ReadingPreviewGold,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.clickable(onClick = onOpen),
        )
    }
}

/**
 * The missing-model download prompt, the iOS counterpart of Android's
 * MissingContentCard for the model_now_nudge category: the shared CDN
 * downloader fetches the GGUF, then the tile regenerates.
 */
@Composable
private fun MissingModelDownloadCard(
    downloading: Boolean,
    downloadFraction: Float,
    onDownload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.36f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "On-device AI model",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Creates source-grounded suggestions locally without " +
                "sending content off-device.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.66f),
        )
        if (downloading) {
            LinearProgressIndicator(
                progress = { downloadFraction },
                color = ReadingPreviewGold,
                trackColor = Color.White.copy(alpha = 0.18f),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Downloading… ${(downloadFraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "940 MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
                CupertinoSurface(
                    onClick = onDownload,
                    shape = RoundedCornerShape(14.dp),
                    color = ReadingPreviewGold,
                    contentColor = Color.Black,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "Download",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Cupertino rendering of Android's artwork insight card. */
@Composable
private fun ArtworkTile(
    artwork: DrawableResource,
    foreground: DrawableResource? = null,
    foregroundScale: Float = 1f,
    foregroundOffsetXFraction: Float = 0f,
    foregroundOffsetYFraction: Float = 0f,
    backgroundPageOffset: () -> Float = { 0f },
    isExpanded: Boolean,
    label: String,
    title: String,
    subtitle: String,
    tileHeight: Dp,
    modifier: Modifier = Modifier,
    footerText: String? = null,
    topEndContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    // Android's backgroundBaseScale: headroom so the counter-scrolling
    // artwork never reveals the card's edge mid-swipe.
    val backgroundBaseScale = 1.065f
    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 32.dp else 26.dp,
        animationSpec = tween(360),
        label = "insightCornerRadius",
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isExpanded) 10.dp else 6.dp,
        animationSpec = tween(360),
        label = "insightShadowElevation",
    )
    var revealExpandedContent by remember(isExpanded) { mutableStateOf(!isExpanded) }
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            kotlinx.coroutines.delay(50L)
            revealExpandedContent = true
        }
    }
    val expandedContentEntrance by animateFloatAsState(
        targetValue = if (revealExpandedContent) 1f else 0f,
        animationSpec = tween(durationMillis = 360),
        label = "expandedContentEntrance",
    )
    val shape = RoundedCornerShape(cornerRadius)
    CupertinoSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(tileHeight),
        shape = shape,
        color = Color(0xFF635A56),
        shadowElevation = shadowElevation,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .then(
                    when {
                        onLongClick != null -> Modifier.combinedClickable(
                            onClick = onClick ?: {},
                            onLongClick = onLongClick,
                        )
                        onClick != null -> Modifier.clickable(onClick = onClick)
                        else -> Modifier
                    },
                ),
        ) {
            Image(
                painter = painterResource(artwork),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Android's InsightPreviewCard parallax: the artwork
                        // counter-scrolls 18dp per page-fraction, scaled up and
                        // clamped so the card's edge never shows mid-swipe.
                        val pageOffset = backgroundPageOffset().coerceIn(-1f, 1f)
                        val maxCoveredHorizontalShift = (
                            (size.width * (backgroundBaseScale - 1f) / 2f) - 1.dp.toPx()
                            ).coerceAtLeast(0f)
                        scaleX = backgroundBaseScale
                        scaleY = backgroundBaseScale
                        translationX = (-pageOffset * 18.dp.toPx()).coerceIn(
                            -maxCoveredHorizontalShift,
                            maxCoveredHorizontalShift,
                        )
                    },
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
                            // Match Android's slight foreground overscan so the
                            // subject remains covered while it counter-scrolls.
                            val renderedForegroundScale = 1.025f * foregroundScale
                            scaleX = renderedForegroundScale
                            scaleY = renderedForegroundScale
                            // The foreground counter-scrolls at a slower rate
                            // than the background — the rate difference is what
                            // reads as depth while swiping (Android: 6dp vs 18dp).
                            val pageOffset = backgroundPageOffset().coerceIn(-1f, 1f)
                            translationX = (size.width * foregroundOffsetXFraction) -
                                (pageOffset * 6.dp.toPx())
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
            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 18.dp, top = 62.dp, end = 18.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    content?.let { expandedContent ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .graphicsLayer {
                                    alpha = expandedContentEntrance
                                    translationY = (1f - expandedContentEntrance) * 10.dp.toPx()
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            1f to Color.Black,
                                            endY = 28.dp.toPx(),
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                },
                        ) {
                            expandedContent()
                        }
                    }
                    ArtworkTileMetadata(
                        title = title,
                        subtitle = subtitle,
                        footerText = footerText,
                        expanded = true,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 22.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    content?.invoke()
                    ArtworkTileMetadata(
                        title = title,
                        subtitle = subtitle,
                        footerText = footerText,
                        expanded = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtworkTileMetadata(
    title: String,
    subtitle: String,
    footerText: String?,
    expanded: Boolean,
) {
    if (title.isNotEmpty()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = (if (expanded) 18 else 17).sp,
                lineHeight = 22.sp,
                letterSpacing = (-0.2).sp,
            ),
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (subtitle.isNotEmpty()) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                lineHeight = if (expanded) 16.sp else 17.sp,
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
                fontSize = if (expanded) 11.sp else 12.sp,
                lineHeight = if (expanded) 14.sp else 16.sp,
                letterSpacing = 0.sp,
            ),
            color = Color.White.copy(alpha = 0.78f),
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
