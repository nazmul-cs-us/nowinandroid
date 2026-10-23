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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import com.starception.submission.core.designsystem.component.contentSizedInsightExtent
import com.starception.submission.core.designsystem.component.InsightTextBlock
import com.starception.submission.core.designsystem.component.insightTextHeight
import com.starception.submission.core.designsystem.component.LocalInsightReaderChromeProgress
import com.starception.submission.core.designsystem.component.insightChromeVisibility
import com.starception.submission.core.designsystem.component.rememberInsightReaderChrome
import com.starception.submission.core.designsystem.component.insightReaderChromeProgress
import com.starception.submission.core.designsystem.component.InsightReaderControlsButton
import com.starception.submission.core.designsystem.component.InsightMotion
import com.starception.submission.core.designsystem.component.insightReadingEdges
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.starception.submission.core.designsystem.component.formatInsightHadithText
import com.starception.submission.core.designsystem.component.InsightContentLayout
import com.starception.submission.core.designsystem.component.InsightResizeCornerHandle
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
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
import com.starception.submission.core.images.resources.insight_quran_background
import com.starception.submission.core.images.resources.insight_quran_foreground_v2
import com.starception.submission.core.images.resources.insight_suggestion
import com.starception.submission.core.model.data.BukhariBooks
import com.starception.submission.core.model.data.ShamayelBooks
import com.starception.submission.feature.quran.dailyReading
import com.starception.submission.feature.quran.subtitle
import com.starception.submission.feature.quran.QuranData
import com.starception.submission.prayer.model.PrayerNotificationPreferences
import com.starception.submission.shared.SharedPrayerDay
import com.starception.submission.shared.SharedPrayerSlot
import com.starception.submission.shared.audio.QuranAudioPlayer
import com.starception.submission.shared.content.CONTEXTUAL_DUA_CHAPTER_IDS
import com.starception.submission.shared.content.ContextualRecommendation
import com.starception.submission.shared.content.SharedContentStore
import com.starception.submission.shared.content.contextualRecommendation
import com.starception.submission.shared.content.createSharedFortressRepository
import com.starception.submission.shared.hadith.createSharedHadithRepository
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

/** Content-sized automatic reading height; corner grips remain fully free-form. */
internal fun preferredInsightExpansionExtent(
    logicalPage: Int,
    restingHeight: Dp,
    maximumHeight: Dp,
    measuredHeight: Dp?,
): Float {
    return contentSizedInsightExtent(logicalPage, restingHeight, maximumHeight, measuredHeight)
}

/** The swipeable insight tiles at the top of the home page. */
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
    onOpenBukhariHadith: (Int) -> Unit = {},
    onOpenShamayelBook: (Int) -> Unit = {},
    notifications: PrayerNotificationPreferences = PrayerNotificationPreferences(),
    tileHeight: Dp = 220.dp,
    maxTileHeight: Dp = tileHeight + 104.dp,
    fullWidthPage: Boolean = false,
    maxPageWidth: Dp? = null,
    initialExpandedPage: Int? = null,
    expansionExtent: Float = if (initialExpandedPage != null) 1f else 0f,
    onExpansionExtentChange: (Float) -> Unit = {},
    forceMoonForTesting: Boolean = false,
    forceSunForTesting: Boolean = false,
    collapseExpanded: Boolean = false,
    onExpandedPageChange: (Int?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val primaryPageCount = 5
    val visiblePages = remember { List(primaryPageCount) { it } }
    val pageCount = visiblePages.size
    val middleLoopStart = pageCount
    val pagerState = rememberPagerState(
        initialPage = primaryPageCount + (initialExpandedPage ?: 0).coerceIn(0, primaryPageCount - 1),
        pageCount = { primaryPageCount * 3 },
    )
    val pagerScope = rememberCoroutineScope()
    val normalizedExtent = expansionExtent.coerceIn(0f, 1f)
    val boundedMaxTileHeight = maxOf(tileHeight, maxTileHeight)
    val compactHeightHints = remember { mutableStateMapOf<Int, Dp>() }
    val expandedHeightHints = remember { mutableStateMapOf<Int, Dp>() }
    val stageHeightHints = remember { mutableStateMapOf<Int, Dp>() }
    val settledLogicalPage = visiblePages[pagerState.settledPage % pageCount]
    // Stable carousel baseline: content measurements belong to expanded mode only.
    val contentAwareRestingHeight = tileHeight
    val animatedRestingHeight by animateDpAsState(
        targetValue = contentAwareRestingHeight,
        animationSpec = spring(dampingRatio = 1f, stiffness = 350f),
        label = "insightContentAwareRestingHeight",
    )
    var isResizeDragging by remember { mutableStateOf(false) }
    var isAutomaticallySized by remember { mutableStateOf(true) }
    val animatedExpansionProgress by animateFloatAsState(
        targetValue = normalizedExtent,
        animationSpec = if (isResizeDragging) {
            snap()
        } else {
            tween(durationMillis = InsightMotion.ExpansionMillis, easing = FastOutSlowInEasing)
        },
        label = "insightExpansionProgress",
    )
    val layoutExpansionProgress = animatedExpansionProgress.coerceIn(0f, 1f)
    val isExpanded = normalizedExtent > 0.001f
    val expandedLogicalPage = if (isExpanded) {
        visiblePages[pagerState.currentPage % pageCount]
    } else {
        null
    }
    LaunchedEffect(collapseExpanded) {
        if (collapseExpanded) {
            isAutomaticallySized = true
            onExpansionExtentChange(0f)
        }
    }
    // Re-evaluate after content measurement and viewport changes, not just
    // swipes. Never replace a height chosen with the corner grips.
    val expandedHeightHint = expandedHeightHints[settledLogicalPage]
    LaunchedEffect(
        settledLogicalPage,
        isExpanded,
        expandedHeightHint,
        contentAwareRestingHeight,
        boundedMaxTileHeight,
        isAutomaticallySized,
        isResizeDragging,
        pagerState.isScrollInProgress,
    ) {
        if (isExpanded && isAutomaticallySized && !isResizeDragging && !pagerState.isScrollInProgress) {
            kotlinx.coroutines.delay(120L)
            val target = preferredInsightExpansionExtent(
                    settledLogicalPage,
                    contentAwareRestingHeight,
                    boundedMaxTileHeight,
                    expandedHeightHint,
                )
            if (kotlin.math.abs(target - normalizedExtent) *
                (boundedMaxTileHeight - contentAwareRestingHeight).value > 8f) {
                onExpansionExtentChange(target)
            }
        }
    }
    LaunchedEffect(expandedLogicalPage) {
        onExpandedPageChange(expandedLogicalPage)
    }
    val effectiveTileHeight = animatedRestingHeight +
        ((boundedMaxTileHeight - animatedRestingHeight) * layoutExpansionProgress)
    val density = LocalDensity.current
    val resizeRangePx = with(density) {
        (boundedMaxTileHeight - contentAwareRestingHeight).toPx()
    }
    var gestureExtent by remember { mutableFloatStateOf(normalizedExtent) }
    SideEffect { gestureExtent = normalizedExtent }
    val consumeResizeDelta: (Float) -> Float = remember(resizeRangePx, onExpansionExtentChange) {
        { deltaY ->
            if (resizeRangePx <= 0f) {
                0f
            } else {
                val previous = gestureExtent
                val next = (previous - (deltaY / resizeRangePx)).coerceIn(0f, 1f)
                gestureExtent = next
                if (next != previous) onExpansionExtentChange(next)
                -(next - previous) * resizeRangePx
            }
        }
    }
    val qiblaBearing = remember(latitude, longitude) {
        qiblaBearing(latitude, longitude).roundToInt()
    }
    val nextPrayerText = day.nextPrayer
        ?.let { "${day.displayPrayerName(it)} in ${day.countdown}" }
        .orEmpty()
    val readingSurah = remember(today) { dailyReading(today) }
    val readingPlayback = rememberReadingTilePlayback(quranPlayer, readingSurah.number) {
        onOpenQuran(readingSurah.number)
    }
    val isReadingAudio = readingPlayback.playing
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
    var modelCard by remember(today) { mutableStateOf<DeenlyKnowledgeCard?>(null) }
    var modelLoading by remember(today) { mutableStateOf(false) }
    var modelMissing by remember(today) { mutableStateOf(false) }
    var modelGenerationKey by remember(today) { mutableStateOf<String?>(null) }
    val downloadProgress by DeenlyKnowledgeRuntime.downloadProgress.collectAsState()
    val downloadCompleted by DeenlyKnowledgeRuntime.downloadCompleted.collectAsState()
    LaunchedEffect(expandedLogicalPage, today, recommendation, downloadCompleted) {
        if (expandedLogicalPage != 4) return@LaunchedEffect
        val generationKey = "$today|expanded-knowledge|$downloadCompleted"
        if (modelGenerationKey == generationKey) return@LaunchedEffect
        modelGenerationKey = generationKey
        modelCard = null
        modelMissing = false
        if (!DeenlyKnowledgeRuntime.isKnowledgeModelAvailable()) {
            modelLoading = false
            modelMissing = true
            return@LaunchedEffect
        }
        modelLoading = true
        // The pager scope survives swiping to another expanded insight. Android
        // retains the same in-flight generation instead of cancelling the model
        // when tile 4 leaves the viewport.
        pagerScope.launch {
            val generated = runCatching {
                generateInsightKnowledgeCard(
                    date = today,
                    appSituation = recommendation.supportingText,
                    fortressInvocations = fortressInvocations,
                )
            }.getOrNull()
            if (modelGenerationKey == generationKey) {
                modelCard = generated
                modelLoading = false
            }
        }
    }
    val openModelCard: (DeenlyKnowledgeCard) -> Unit = { card ->
        when (val target = card.target) {
            is DeenlyKnowledgeTarget.Surah -> onOpenQuran(target.number)
            is DeenlyKnowledgeTarget.FortressDua -> onOpenFortressChapter(target.chapterId)
            is DeenlyKnowledgeTarget.BukhariHadith -> onOpenBukhariHadith(target.hadithId)
            is DeenlyKnowledgeTarget.ShamayelBook -> onOpenShamayelBook(target.bookId)
        }
    }
    val autoAdvanceProgress = remember { Animatable(0f) }
    val headingProvider = remember { HeadingProvider() }
    var heading by remember { mutableStateOf(HeadingReading()) }
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
            if (isExpanded) {
                CupertinoSurface(
                    onClick = { onExpansionExtentChange(0f) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Collapse",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (maxPageWidth != null) 0.dp else 6.dp),
                ) {
                    repeat(primaryPageCount) { index ->
                        val currentVisiblePage = visiblePages[pagerState.currentPage % pageCount]
                        val selected = currentVisiblePage == index
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
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Keep Android's card aspect ratio. Partial-width pages leave a
            // neighboring card visible; a two-pane hero can consume its column.
            // The spring may briefly overshoot, but pager spacing and padding
            // must remain non-negative.
            val boundedPageWidth = maxPageWidth?.let { minOf(maxWidth, it) } ?: maxWidth
            val restingPageWidth = if (fullWidthPage) {
                boundedPageWidth
            } else {
                (tileHeight * (250f / 288f))
                    .coerceAtMost(minOf(maxWidth * 0.64f, boundedPageWidth))
                    .coerceAtLeast(140.dp)
            }
            // Keep text wrapping independent of content-driven height corrections.
            val widthExpansion by animateFloatAsState(
                targetValue = if (isExpanded) 1f else 0f,
                animationSpec = tween(InsightMotion.ExpansionMillis, easing = FastOutSlowInEasing),
                label = "insightReadingWidth",
            )
            val pageWidth = restingPageWidth +
                ((boundedPageWidth - restingPageWidth) * widthExpansion)
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(pageWidth),
                pageSpacing = 14.dp * (1f - widthExpansion),
                contentPadding = PaddingValues(
                    end = if (fullWidthPage) 0.dp else 18.dp * (1f - widthExpansion),
                ),
                beyondViewportPageCount = if (isExpanded) 0 else 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(effectiveTileHeight)
                    .semantics {
                        stateDescription = if (isExpanded) {
                            "Insight expanded ${(normalizedExtent * 100).roundToInt()} percent"
                        } else {
                            "Insight collapsed"
                        }
                        customActions = buildList {
                            if (normalizedExtent < 1f) {
                                add(
                                    CustomAccessibilityAction("Expand insight") {
                                        isAutomaticallySized = true
                                        onExpansionExtentChange(
                                            preferredInsightExpansionExtent(
                                                settledLogicalPage,
                                                contentAwareRestingHeight,
                                                boundedMaxTileHeight,
                                                expandedHeightHints[settledLogicalPage],
                                            ),
                                        )
                                        true
                                    },
                                )
                            }
                            if (normalizedExtent > 0f) {
                                add(
                                    CustomAccessibilityAction("Collapse insight") {
                                        isAutomaticallySized = true
                                        onExpansionExtentChange(0f)
                                        true
                                    },
                                )
                            }
                        }
                    },
            ) { page ->
                val logicalPage = visiblePages[page % pageCount]
                val sourceLogicalPage = logicalPage
                val isExpanded = expandedLogicalPage == sourceLogicalPage
                val onLongClick = {
                    isAutomaticallySized = true
                    onExpansionExtentChange(
                        if (isExpanded) {
                            0f
                        } else {
                            preferredInsightExpansionExtent(
                                logicalPage,
                                contentAwareRestingHeight,
                                boundedMaxTileHeight,
                                expandedHeightHints[logicalPage],
                            )
                        },
                    )
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
                val pageDensity = LocalDensity.current.density
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val focus = 1f - abs(pageOffset)
                            val restingScale = 0.94f + (0.06f * focus)
                            val restingAlpha = 0.76f + (0.24f * focus)
                            scaleX = restingScale + ((1f - restingScale) * layoutExpansionProgress)
                            scaleY = restingScale + ((1f - restingScale) * layoutExpansionProgress)
                            alpha = restingAlpha + ((1f - restingAlpha) * layoutExpansionProgress)
                            // A restrained cover-flow tilt gives the swipe a clear
                            // spatial relationship without making text feel tilted.
                            translationX = -pageOffset * 8.dp.toPx()
                            rotationY = pageOffset * -3.5f
                            cameraDistance = 24f * pageDensity
                        },
                ) {
                    AnimatedContent(
                        targetState = logicalPage,
                        transitionSpec = {
                            (
                                fadeIn(
                                    animationSpec = tween(
                                        durationMillis = 220,
                                        delayMillis = 24,
                                        easing = FastOutSlowInEasing,
                                    ),
                                ) + scaleIn(
                                    initialScale = 0.965f,
                                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                                )
                            ) togetherWith (
                                fadeOut(
                                    animationSpec = tween(120, easing = LinearEasing),
                                ) + scaleOut(
                                    targetScale = 0.985f,
                                    animationSpec = tween(160, easing = LinearEasing),
                                )
                            )
                        },
                        label = "insightTileContent",
                    ) { animatedLogicalPage ->
                    when (animatedLogicalPage) {
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
                            epochSeconds = Clock.System.now().epochSeconds.toDouble(),
                            latitude = latitude,
                            longitude = longitude,
                            tileHeight = effectiveTileHeight,
                            isExpanded = isExpanded,
                            forceMoonForTesting = forceMoonForTesting,
                            forceSunForTesting = forceSunForTesting,
                            onClick = onOpenQibla,
                            onLongClick = onLongClick,
                            onCompactContentHeightChange = { measured ->
                                compactHeightHints[0] = measured
                                expandedHeightHints[0] = measured + 76.dp
                            },
                             // Preserve the expanded layout's vertical balance after
                             // removing the prayer-line artwork/text. The headline
                             // and timeline remain in their established position.
                             expandedContent = if (isExpanded) {
                                 { Spacer(Modifier.fillMaxSize()) }
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
                            minimumExpandedContentHeight = stageHeightHints[1] ?: 180.dp,
                            onPreferredHeightChange = { measuredHeight: Dp ->
                                expandedHeightHints[1] = measuredHeight
                            },
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
                            onCompactContentHeightChange = { measured ->
                                compactHeightHints[1] = measured
                            },
                        ) {
                            if (isExpanded) {
                                ExpandedSalahHadith(
                                    prayerName = day.currentPrayer ?: day.nextPrayer ?: salah.nextUnprayed,
                                    date = today,
                                    onOpenHadith = onOpenBukhariHadith,
                                    onPreferredHeightChange = { measured ->
                                        stageHeightHints[1] = measured
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                SalahMarkers(
                                    completed = salah.completed,
                                    available = day.slots
                                        .filter { it.hasStarted && it.name in FARD_PRAYERS }
                                        .mapTo(mutableSetOf()) { it.name },
                                    onToggle = onTogglePrayer,
                                )
                            }
                        }

                        2 -> {
                            // Android's home reading tile: the day's fixed
                            // suggestion with a play control and a progress
                            // slider with time labels.
                            val surah = readingSurah
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
                                minimumExpandedContentHeight = stageHeightHints[2] ?: 240.dp,
                                onPreferredHeightChange = { measuredHeight: Dp ->
                                    expandedHeightHints[2] = measuredHeight
                                },
                                label = "Today's reading",
                                title = if (isExpanded) "" else surah.nameEnglish,
                                subtitle = readingPlayback.error
                                    ?: if (readingPlayback.preparing) "Preparing audio…"
                                    else if (isExpanded) "" else surah.nameArabic,
                                footerText = if (readingPlayback.error != null) {
                                    "Tap Play to try again"
                                } else if (isExpanded) {
                                    surah.nameArabic
                                } else {
                                    surah.subtitle()
                                },
                                tileHeight = effectiveTileHeight,
                                onLongClick = onLongClick,
                                onCompactContentHeightChange = { measured ->
                                    compactHeightHints[2] = measured
                                },
                                topEndContent = {
                                    CupertinoSurface(
                                        onClick = readingPlayback.toggle,
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.35f),
                                        contentColor = Color.White,
                                        modifier = Modifier.size(if (isExpanded) 44.dp else 40.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (readingPlayback.preparing) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp).semantics {
                                                        contentDescription = "Preparing recitation"
                                                    },
                                                    color = Color.White,
                                                    strokeWidth = 2.dp,
                                                )
                                            } else {
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
                                    }
                                },
                                onClick = readingPlayback.openSurah,
                                content = {
                                    if (isExpanded) {
                                        ExpandedReadingPreview(
                                            onPreferredHeightChange = { stageHeightHints[2] = it },
                                            surahNumber = surah.number,
                                            surahName = surah.nameEnglish,
                                            isPlaying = isReadingAudio,
                                        )
                                    } else if (isReadingAudio && duration > 0f) {
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

                        3 -> {
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
                                // The compact card already has a live heading
                                // arrow. Omitting the decorative compass avoids
                                // a static-compass-to-radar swap on expansion.
                                foreground = null,
                                isExpanded = isExpanded,
                                label = "Qibla",
                                title = if (isExpanded) "" else "$qiblaBearing° toward Makkah",
                                subtitle = if (isExpanded) "" else qiblaCardinalDirection(qiblaBearing),
                                footerText = if (isExpanded) null else "Open the live compass and 3D globe",
                                tileHeight = effectiveTileHeight,
                                minimumExpandedContentHeight = 320.dp,
                                onPreferredHeightChange = { measuredHeight: Dp ->
                                    expandedHeightHints[3] = measuredHeight
                                },
                                onClick = onOpenQibla,
                                onLongClick = onLongClick,
                                onCompactContentHeightChange = { measured ->
                                    compactHeightHints[3] = measured
                                },
                                content = if (isExpanded) {
                                    {
                                        ExpandedQiblaRadar(
                                            bearing = qiblaBearing,
                                            headingDegrees = heading.headingDegrees?.toFloat(),
                                            onOpenQibla = onOpenQibla,
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
                            // Android's RecommendationExpandedStage: the
                            // enlarged state shows the model-generated
                            // grounded suggestion over the day's reading,
                            // falling back to the deterministic card when
                            // the GGUF is missing or its output is rejected.
                            // The model download is owned by the runtime, so
                            // it survives leaving this tile.
                            ArtworkTile(
                                backgroundPageOffset = { pageOffset },
                                artwork = Res.drawable.insight_suggestion,
                                isExpanded = isExpanded,
                                minimumExpandedContentHeight = stageHeightHints[4] ?: 180.dp,
                                onPreferredHeightChange = { measuredHeight: Dp ->
                                    expandedHeightHints[4] = measuredHeight
                                },
                                label = "AI suggested",
                                title = if (isExpanded) "" else recommendation.title,
                                subtitle = if (isExpanded) "" else recommendation.supportingText,
                                footerText = if (isExpanded) null else recommendation.footerText,
                                tileHeight = effectiveTileHeight,
                                onLongClick = onLongClick,
                                onCompactContentHeightChange = { measured ->
                                    compactHeightHints[4] = measured
                                },
                                content = if (isExpanded) {
                                    {
                                        when {
                                            modelLoading -> ModelSuggestionLoading()
                                            modelCard != null -> ModelSuggestionStage(
                                                card = modelCard,
                                                onPreferredHeightChange = { stageHeightHints[4] = it },
                                                onOpen = { modelCard?.let(openModelCard) },
                                            )
                                            modelMissing -> MissingModelDownloadCard(
                                                downloading = downloadProgress != null,
                                                downloadFraction = downloadProgress ?: 0f,
                                                onDownload = {
                                                    DeenlyKnowledgeRuntime.startModelDownload()
                                                },
                                            )
                                            else -> ExpandedSuggestion(
                                                title = recommendation.title,
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
                                        modelCard?.let(openModelCard)
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
            if (!pagerState.isScrollInProgress) {
                val focusedCornerRadius by animateDpAsState(
                    targetValue = if (isExpanded) 32.dp else 26.dp,
                    animationSpec = tween(InsightMotion.ExpansionMillis, easing = FastOutSlowInEasing),
                    label = "insightGripCornerRadius",
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .width(pageWidth)
                        .height(effectiveTileHeight)
                        .clip(RoundedCornerShape(focusedCornerRadius)),
                ) {
                    listOf(Alignment.BottomStart to true, Alignment.BottomEnd to false).forEach { (alignment, start) ->
                        InsightResizeCornerHandle(
                            isStart = start,
                            expansionExtent = normalizedExtent,
                            isResizing = isResizeDragging,
                            onDragStart = {
                                isResizeDragging = true
                                isAutomaticallySized = false
                            },
                            // The grip controls the card's bottom edge: pulling
                            // outward and down increases height; inward and up
                            // decreases it.
                            onDrag = { deltaY -> consumeResizeDelta(-deltaY) },
                            onDragEnd = { isResizeDragging = false },
                            onExpand = {
                                isAutomaticallySized = true
                                onExpansionExtentChange(
                                    preferredInsightExpansionExtent(
                                        settledLogicalPage,
                                        contentAwareRestingHeight,
                                        boundedMaxTileHeight,
                                        expandedHeightHints[settledLogicalPage],
                                    ),
                                )
                            },
                            onCollapse = {
                                isAutomaticallySized = true
                                onExpansionExtentChange(0f)
                            },
                            modifier = Modifier
                                 .align(alignment)
                                 .offset(
                                     x = 0.dp,
                                     y = 0.dp,
                                 ),
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

private val ReadingPreviewGold = Color(0xFFE8C681)

@Composable
private fun InsightSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
        fontWeight = FontWeight.SemiBold,
        color = ReadingPreviewGold,
        modifier = modifier,
    )
}

@Composable
private fun InsightSourceAction(reference: String, onClick: () -> Unit) {
    CupertinoSurface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.08f),
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                InsightSectionLabel("VERIFIED SOURCE")
                Text(
                    text = reference,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.88f),
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Open source", modifier = Modifier.size(20.dp))
        }
    }
}

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
    onPreferredHeightChange: (Dp) -> Unit = {},
) {
    val store = remember { SharedContentStore() }
    // Read on recomposition so returning from Quran settings refreshes the
    // retained home carousel instead of keeping its first remembered values.
    val mushafMode = store.quranMushafMode()
    val arabicFont = store.quranArabicFont()
    val showTranslation = store.quranShowTranslation()
    val translationLanguage = if (showTranslation) {
        QuranTranslationLanguage.fromCode(store.quranTranslationLanguage())
    } else {
        QuranTranslationLanguage.None
    }
    val repository = remember { createQuranVerseRepository() }
    var verses by remember(surahNumber) { mutableStateOf<List<QuranVerse>>(emptyList()) }
    var loading by remember(surahNumber, translationLanguage) { mutableStateOf(true) }
    var loadFailed by remember(surahNumber, translationLanguage) { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(surahNumber, translationLanguage, loadAttempt) {
        loading = true
        loadFailed = false
        verses = emptyList()
        val result = runCatching {
            repository.getVersesBySurah(surahNumber, translationLanguage)
        }
        verses = result.getOrDefault(emptyList())
        loadFailed = result.isFailure || verses.isEmpty()
        loading = false
    }

    // The bismillah breathes with a slow glow instead of a hard shimmer, so
    // the stage stays calm under the card's own ambient motion (Android's
    // QuranReadingExpandedStage).
    val bismillahGlow by androidx.compose.animation.core.rememberInfiniteTransition(
        label = "quranStageGlow",
    ).animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(durationMillis = 2_600, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "quranStageGlowPhase",
    )

    // Scale Arabic from the actual expanded tile width. Fixed 17–23sp text
    // was too small on the iPad/large expanded card and hard to read.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val readingScrollState = rememberLazyListState()
        val hasTranslation = verses.any { it.translation.isNotBlank() }
        val readingArabicSize = (
            maxWidth.value * if (hasTranslation) 0.064f else 0.075f
            ).coerceIn(if (hasTranslation) 19f else 22f, if (hasTranslation) 29f else 34f).sp
        val readingArabicLineHeight = (readingArabicSize.value * 1.45f).sp
        val bismillahSize = (maxWidth.value * 0.052f).coerceIn(18f, 26f).sp
        val readingTranslationSize = (maxWidth.value * 0.038f).coerceIn(15f, 18f).sp
        val blocks = verses.flatMap { verse ->
            listOfNotNull(
                InsightTextBlock(verse.arabicText.trim(), MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = QuranArabicFonts.fontFamily(arabicFont),
                    fontSize = readingArabicSize, lineHeight = readingArabicLineHeight,
                    letterSpacing = 0.sp,
                ), 74.dp),
                verse.translation.takeIf(String::isNotBlank)?.let {
                    InsightTextBlock(it, MaterialTheme.typography.bodySmall.copy(
                        fontSize = readingTranslationSize,
                        lineHeight = (readingTranslationSize.value * 1.55f).sp,
                    ), 10.dp)
                },
            )
        }
        val naturalHeight = insightTextHeight(blocks, maxWidth) + 164.dp
        LaunchedEffect(naturalHeight, loading) {
            if (!loading && verses.isNotEmpty()) onPreferredHeightChange(naturalHeight)
        }


        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.insightChromeVisibility(LocalInsightReaderChromeProgress.current)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    InsightSectionLabel("SURAH $surahNumber")
                    Text(
                        text = surahName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            loading -> "Preparing passage…"
                            loadFailed -> "Passage unavailable"
                            isPlaying -> "PLAYING"
                            mushafMode -> "MUSHAF · ${verses.size} AYAT"
                            else -> "${verses.size} AYAT"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            letterSpacing = 0.8.sp,
                        ),
                        color = ReadingPreviewGold,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .insightReadingEdges(
                        canScrollBackward = { readingScrollState.canScrollBackward },
                        canScrollForward = { readingScrollState.canScrollForward },
                    ),
                state = readingScrollState,
                overscrollEffect = null,
                contentPadding = PaddingValues(vertical = InsightMotion.ReadingEdge),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (surahNumber != 1 && surahNumber != 9) {
                    item(key = "bismillah") {
                        Text(
                            text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = QuranArabicFonts.fontFamily(arabicFont),
                                fontSize = bismillahSize,
                                lineHeight = (bismillahSize.value * 1.35f).sp,
                                letterSpacing = 0.sp,
                            ),
                            color = Color.White.copy(alpha = 0.78f + (0.22f * bismillahGlow)),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (loading || loadFailed) {
                    item(key = "loading") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (loading) {
                                CircularProgressIndicator(color = ReadingPreviewGold, modifier = Modifier.size(24.dp))
                            }
                            Text(
                                text = if (loading) "Preparing today's passage…" else "Unable to load this passage.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.82f),
                                textAlign = TextAlign.Center,
                            )
                            if (loadFailed) {
                                CupertinoSurface(
                                    onClick = { loadAttempt++ },
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.12f),
                                    contentColor = Color.White,
                                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                                ) {
                                    Text("Try again", modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                                }
                            }
                        }
                    }
                }
                items(
                    items = verses,
                    key = { verse -> verse.numberInSurah },
                ) { verse ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = verse.numberInSurah.toArabicIndic(),
                            style = MaterialTheme.typography.labelSmall,
                            color = ReadingPreviewGold,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(ReadingPreviewGold.copy(alpha = 0.12f), CircleShape)
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                        Text(
                            text = verse.arabicText.trim(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = QuranArabicFonts.fontFamily(arabicFont),
                                fontSize = readingArabicSize,
                                lineHeight = readingArabicLineHeight,
                                letterSpacing = 0.sp,
                            ),
                            color = Color.White.copy(alpha = 0.96f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        verse.translation.takeIf(String::isNotBlank)?.let { translation ->
                                ReadingTranslationPanel(
                                    text = translation,
                                    fontSize = readingTranslationSize,
                                )
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingTranslationPanel(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = fontSize,
            lineHeight = (fontSize.value * 1.4f).sp,
        ),
        color = Color.White.copy(alpha = 0.88f),
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    )
}

/**
 * Android's PrayerTimelineExpandedStage, lean: the day's five prayers with
 * the next one highlighted in the reference gold.
 */
/** Degrees to radians for the radar drawing, common to all platforms. */
private fun radians(degrees: Double): Double = degrees * PI / 180.0

/** 12-hour clock label, matching the Android dial's formatting. */
private fun formatTileClock(hour: Int, minute: Int): String {
    val suffix = if (hour < 12) "AM" else "PM"
    val h = ((hour + 11) % 12) + 1
    return "$h:${minute.toString().padStart(2, '0')} $suffix"
}

/** Android's prayer-specific Bukhari query order. */
private fun prayerHadithQueries(prayerName: String?): List<String> = when (prayerName?.lowercase()) {
    "fajr" -> listOf("Fajr prayer", "morning prayer")
    "dhuhr" -> listOf("Dhuhr prayer", "midday prayer")
    "asr" -> listOf("Asr prayer", "afternoon prayer")
    "maghrib" -> listOf("Maghrib prayer", "sunset prayer")
    "isha" -> listOf("Isha prayer", "night prayer")
    else -> listOf("prayer", "congregational prayer")
}

/**
 * Android's SalahHadithExpandedStage: the staged fallback narration for the
 * next unprayed prayer — a full-bleed gradient overlay (not a floating
 * glass card), a giant quote glyph, the body at Android's 17.5sp reading
 * size in a manually controlled scrollable reading area.
 */
@Composable
private fun ExpandedSalahHadith(
    prayerName: String?,
    date: LocalDate,
    onOpenHadith: (Int) -> Unit,
    onPreferredHeightChange: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val displayName = prayerName?.replaceFirstChar { it.uppercase() } ?: "Salah"
    val (fallbackText, fallbackSource) = when (prayerName?.lowercase()) {
        "fajr", "asr" ->
            "Whoever prays the two cool prayers will enter Paradise." to "Sahih al-Bukhari · 574"
        "isha" ->
            "If they knew the reward of the night prayer, they would come to it " +
                "even if they had to crawl." to "Sahih al-Bukhari · 615"
        else ->
            "Prayer in congregation is twenty-seven times superior to prayer " +
                "offered alone." to "Sahih al-Bukhari · 645"
    }
    var resolvedHadith by remember(prayerName, date) {
        mutableStateOf<com.starception.submission.shared.hadith.SharedHadith?>(null)
    }
    LaunchedEffect(prayerName, date) {
        val repository = createSharedHadithRepository()
        val candidates = prayerHadithQueries(prayerName)
            .flatMap { query ->
                runCatching { repository.searchBukhari(query, limit = 24) }
                    .getOrDefault(emptyList())
            }
            .distinctBy { it.id }
            .filter { hadith ->
                hadith.english.isNotBlank() && hadith.english.length in 40..1_200
            }
            .sortedBy { it.english.length }
            .take(8)
        resolvedHadith = if (candidates.isEmpty()) {
            null
        } else {
            val seed = date.toEpochDays().toInt() * 31 + prayerName.orEmpty().lowercase().hashCode()
            candidates[((seed % candidates.size) + candidates.size) % candidates.size]
        }
    }
    val text = formatInsightHadithText(resolvedHadith?.english ?: fallbackText)
    val source = resolvedHadith?.let { "Sahih al-Bukhari · ${it.id}" } ?: fallbackSource
    // Kept outside AnimatedContent so touch scrolling never recreates the
    // scrollable subtree, matching Android's structure.
    val hadithScrollState = androidx.compose.foundation.rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Deliberately static subtree: only the text and scroll offset change.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 0.dp,
                    end = 0.dp,
                    top = 8.dp,
                    bottom = 7.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.insightChromeVisibility(LocalInsightReaderChromeProgress.current)) {
                InsightSectionLabel("HADITH FOR ${displayName.uppercase()}")
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .insightReadingEdges(
                            canScrollBackward = { hadithScrollState.canScrollBackward },
                            canScrollForward = { hadithScrollState.canScrollForward },
                        )
                        .verticalScroll(state = hadithScrollState, overscrollEffect = null)
                        .padding(vertical = InsightMotion.ReadingEdge),
                    verticalArrangement = Arrangement.Top,
                ) {
                    Text(
                        text = text,
                        textAlign = TextAlign.Justify,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 19.sp,
                            lineHeight = 29.sp,
                            letterSpacing = (-0.1).sp,
                        ),
                        color = Color.White.copy(alpha = 0.98f),
                        fontWeight = FontWeight.Medium,
                        onTextLayout = { result ->
                            val bodyHeight = with(density) { result.size.height.toDp() }
                            onPreferredHeightChange(bodyHeight + 148.dp)
                        },
                    )
                }
            }
            Box(Modifier.insightChromeVisibility(LocalInsightReaderChromeProgress.current)) {
                InsightSourceAction(reference = source) {
                    val fallbackId = when (prayerName?.lowercase()) {
                        "fajr", "asr" -> 574
                        "isha" -> 615
                        else -> 645
                    }
                    onOpenHadith(resolvedHadith?.id ?: fallbackId)
                }
            }
        }
    }
}

/** Android's complete expanded Qibla hierarchy and perspective instrument. */
@Composable
private fun ExpandedQiblaRadar(
    bearing: Int,
    headingDegrees: Float?,
    onOpenQibla: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val turn = headingDegrees?.let { shortestBearingDelta(it, bearing.toFloat()) }
    val isAligned = turn != null && abs(turn) < 6f
    val instruction = when {
        turn == null -> "Finding your direction"
        isAligned -> "You are facing the Qibla"
        turn > 0f -> "Turn ${abs(turn).roundToInt()}° right"
        else -> "Turn ${abs(turn).roundToInt()}° left"
    }
    val direction = qiblaCardinalDirection(bearing).substringBefore(" from your location")
    val pulse = 0.5f
    val radarSweep = 0f
    val foreground = Color.White
    val surface = Color(0xFF171816)
    val accent = ReadingPreviewGold

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Direction to Makkah",
                    style = MaterialTheme.typography.titleSmall,
                    color = foreground,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$bearing°  ·  $direction",
                    style = MaterialTheme.typography.labelSmall,
                    color = foreground.copy(alpha = 0.60f),
                )
            }
            if (headingDegrees != null) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(foreground.copy(alpha = 0.08f))
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .graphicsLayer {
                                scaleX = 1f + (0.35f * pulse)
                                scaleY = 1f + (0.35f * pulse)
                            }
                            .background(accent, CircleShape),
                    )
                    Text(
                        text = if (isAligned) "Aligned" else "Live",
                        style = MaterialTheme.typography.labelMedium,
                        color = foreground.copy(alpha = 0.88f),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .widthIn(max = 286.dp)
                    .fillMaxWidth()
                    .aspectRatio(286f / 174f)
                    .semantics {
                        contentDescription = when {
                            turn == null -> "Qibla compass. Waiting for your device heading."
                            isAligned -> "Aligned with Qibla"
                            else -> "Qibla compass, ${turn.roundToInt()} degrees to turn"
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radarCenter = Offset(center.x, center.y + 11.dp.toPx())
                    val radiusX = (size.width / 2f) - 21.dp.toPx()
                    val radiusY = (size.height / 2f) - 34.dp.toPx()
                    val radarTopLeft = Offset(radarCenter.x - radiusX, radarCenter.y - radiusY)
                    val radarSize = Size(radiusX * 2f, radiusY * 2f)

                    repeat(2) { index ->
                        val expansion = (8f + (index * 8f)).dp.toPx()
                        drawArc(
                            color = foreground.copy(alpha = if (index == 0) 0.08f else 0.04f),
                            startAngle = 198f,
                            sweepAngle = 144f,
                            useCenter = false,
                            topLeft = Offset(
                                radarCenter.x - radiusX - expansion,
                                radarCenter.y - radiusY - (expansion * 0.28f),
                            ),
                            size = Size(
                                (radiusX + expansion) * 2f,
                                (radiusY + (expansion * 0.28f)) * 2f,
                            ),
                            style = Stroke(width = 0.8.dp.toPx()),
                        )
                    }
                    drawOval(
                        color = Color.Black.copy(alpha = 0.28f),
                        topLeft = radarTopLeft + Offset(0f, 7.dp.toPx()),
                        size = radarSize,
                    )
                    drawOval(
                        brush = Brush.verticalGradient(
                            colors = listOf(surface.copy(alpha = 0.64f), surface.copy(alpha = 0.88f)),
                            startY = radarTopLeft.y,
                            endY = radarTopLeft.y + radarSize.height,
                        ),
                        topLeft = radarTopLeft,
                        size = radarSize,
                    )
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = if (isAligned) 0.15f else 0.06f),
                                Color.Transparent,
                            ),
                            center = radarCenter,
                            radius = radiusX,
                        ),
                        topLeft = radarTopLeft,
                        size = radarSize,
                    )
                    drawArc(
                        color = foreground.copy(alpha = 0.20f),
                        startAngle = 202f,
                        sweepAngle = 136f,
                        useCenter = false,
                        topLeft = radarTopLeft,
                        size = radarSize,
                        style = Stroke(width = 0.8.dp.toPx()),
                    )
                    listOf(1f, 0.62f, 0.30f).forEachIndexed { index, scale ->
                        drawOval(
                            color = foreground.copy(alpha = if (index == 0) 0.22f else 0.10f),
                            topLeft = Offset(
                                radarCenter.x - (radiusX * scale),
                                radarCenter.y - (radiusY * scale),
                            ),
                            size = Size(radiusX * 2f * scale, radiusY * 2f * scale),
                            style = Stroke(width = if (index == 0) 1.2.dp.toPx() else 0.8.dp.toPx()),
                        )
                    }
                    val headingCone = Path().apply {
                        moveTo(radarCenter.x, radarCenter.y - (radiusY * 0.72f))
                        lineTo(radarCenter.x - 13.dp.toPx(), radarCenter.y + 5.dp.toPx())
                        quadraticTo(
                            radarCenter.x,
                            radarCenter.y + 10.dp.toPx(),
                            radarCenter.x + 13.dp.toPx(),
                            radarCenter.y + 5.dp.toPx(),
                        )
                        close()
                    }
                    drawPath(
                        path = headingCone,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                foreground.copy(alpha = 0.13f),
                                foreground.copy(alpha = 0.01f),
                            ),
                            startY = radarCenter.y - radiusY,
                            endY = radarCenter.y + 10.dp.toPx(),
                        ),
                    )
                    val roseHeading = headingDegrees ?: 0f
                    repeat(4) { index ->
                        val angle = radians((index * 90f - roseHeading).toDouble())
                        drawLine(
                            color = foreground.copy(alpha = 0.07f),
                            start = radarCenter,
                            end = Offset(
                                radarCenter.x + cos(angle).toFloat() * radiusX,
                                radarCenter.y + sin(angle).toFloat() * radiusY,
                            ),
                            strokeWidth = 0.7.dp.toPx(),
                        )
                    }
                    drawArc(
                        color = foreground.copy(alpha = 0.035f),
                        startAngle = radarSweep,
                        sweepAngle = 42f,
                        useCenter = true,
                        topLeft = radarTopLeft,
                        size = radarSize,
                    )
                    if (turn != null) {
                        drawArc(
                            color = accent.copy(alpha = if (isAligned) 0.92f else 0.58f),
                            startAngle = -90f,
                            sweepAngle = if (isAligned) 360f else turn.coerceIn(-180f, 180f),
                            useCenter = false,
                            topLeft = radarTopLeft,
                            size = radarSize,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                    repeat(48) { index ->
                        val angle = radians(
                            (index * 7.5f - roseHeading - 90f).toDouble(),
                        )
                        val x = cos(angle).toFloat()
                        val y = sin(angle).toFloat()
                        val major = index % 6 == 0
                        drawLine(
                            color = foreground.copy(alpha = if (major) 0.68f else 0.28f),
                            start = Offset(
                                radarCenter.x + x * radiusX * (if (major) 0.90f else 0.95f),
                                radarCenter.y + y * radiusY * (if (major) 0.90f else 0.95f),
                            ),
                            end = Offset(
                                radarCenter.x + x * radiusX,
                                radarCenter.y + y * radiusY,
                            ),
                            strokeWidth = if (major) 1.4.dp.toPx() else 0.7.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                    repeat(30) { index ->
                        val angle = radians(
                            (index * 12f + (roseHeading * 0.42f) - 90f).toDouble(),
                        )
                        val x = cos(angle).toFloat()
                        val y = sin(angle).toFloat()
                        drawLine(
                            color = if (index % 5 == 0) {
                                accent.copy(alpha = 0.32f)
                            } else {
                                foreground.copy(alpha = 0.11f)
                            },
                            start = Offset(
                                radarCenter.x + (x * radiusX * 0.65f),
                                radarCenter.y + (y * radiusY * 0.62f),
                            ),
                            end = Offset(
                                radarCenter.x + (x * radiusX * 0.70f),
                                radarCenter.y + (y * radiusY * 0.67f),
                            ),
                            strokeWidth = if (index % 5 == 0) 1.dp.toPx() else 0.55.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }

                    val qiblaAngle = radians(((turn ?: 0f) - 90f).toDouble())
                    val qiblaPoint = Offset(
                        radarCenter.x + cos(qiblaAngle).toFloat() * radiusX * 0.64f,
                        radarCenter.y + sin(qiblaAngle).toFloat() * radiusY * 0.64f,
                    )
                    val routeDelta = qiblaPoint - radarCenter
                    val perspectiveScale = radiusY / radiusX
                    val routeNormal = Offset(
                        x = -sin(qiblaAngle).toFloat(),
                        y = cos(qiblaAngle).toFloat() * perspectiveScale,
                    )
                    val counterPoint = Offset(
                        radarCenter.x - (routeDelta.x * 0.62f),
                        radarCenter.y - (routeDelta.y * 0.62f),
                    )
                    val shadowOffset = 1.5.dp.toPx() * perspectiveScale
                    val handShadow = Path().apply {
                        moveTo(
                            radarCenter.x + (routeNormal.x * 7.dp.toPx()),
                            radarCenter.y + (routeNormal.y * 7.dp.toPx()) + shadowOffset,
                        )
                        lineTo(qiblaPoint.x, qiblaPoint.y + shadowOffset)
                        lineTo(
                            radarCenter.x - (routeNormal.x * 7.dp.toPx()),
                            radarCenter.y - (routeNormal.y * 7.dp.toPx()) + shadowOffset,
                        )
                        lineTo(counterPoint.x, counterPoint.y + shadowOffset)
                        close()
                    }
                    drawPath(handShadow, Color.Black.copy(alpha = 0.34f))
                    val qiblaLeft = Path().apply {
                        moveTo(radarCenter.x, radarCenter.y)
                        lineTo(
                            radarCenter.x + (routeNormal.x * 5.5.dp.toPx()),
                            radarCenter.y + (routeNormal.y * 5.5.dp.toPx()),
                        )
                        lineTo(qiblaPoint.x, qiblaPoint.y)
                        close()
                    }
                    drawPath(qiblaLeft, accent)
                    val qiblaRight = Path().apply {
                        moveTo(radarCenter.x, radarCenter.y)
                        lineTo(qiblaPoint.x, qiblaPoint.y)
                        lineTo(
                            radarCenter.x - (routeNormal.x * 5.5.dp.toPx()),
                            radarCenter.y - (routeNormal.y * 5.5.dp.toPx()),
                        )
                        close()
                    }
                    drawPath(qiblaRight, accent.copy(alpha = 0.68f))
                    val counterLeft = Path().apply {
                        moveTo(radarCenter.x, radarCenter.y)
                        lineTo(
                            radarCenter.x + (routeNormal.x * 5.5.dp.toPx()),
                            radarCenter.y + (routeNormal.y * 5.5.dp.toPx()),
                        )
                        lineTo(counterPoint.x, counterPoint.y)
                        close()
                    }
                    drawPath(counterLeft, foreground.copy(alpha = 0.92f))
                    val counterRight = Path().apply {
                        moveTo(radarCenter.x, radarCenter.y)
                        lineTo(counterPoint.x, counterPoint.y)
                        lineTo(
                            radarCenter.x - (routeNormal.x * 5.5.dp.toPx()),
                            radarCenter.y - (routeNormal.y * 5.5.dp.toPx()),
                        )
                        close()
                    }
                    drawPath(counterRight, foreground.copy(alpha = 0.56f))
                    drawCircle(
                        color = accent.copy(alpha = 0.12f + (0.08f * pulse)),
                        radius = 12.dp.toPx(),
                        center = qiblaPoint,
                    )
                    drawCircle(surface, radius = 2.2.dp.toPx(), center = radarCenter)

                    val headingMarker = Path().apply {
                        moveTo(radarCenter.x, radarTopLeft.y - 7.dp.toPx())
                        lineTo(radarCenter.x - 4.dp.toPx(), radarTopLeft.y - 13.dp.toPx())
                        lineTo(radarCenter.x + 4.dp.toPx(), radarTopLeft.y - 13.dp.toPx())
                        close()
                    }
                    drawPath(headingMarker, accent)
                }

                listOf("N", "E", "S", "W").forEachIndexed { index, label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                        ),
                        color = if (label == "N") {
                            accent
                        } else {
                            foreground.copy(alpha = 0.62f)
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .graphicsLayer {
                                val angle = radians(
                                    (index * 90f - (headingDegrees ?: 0f) - 90f).toDouble(),
                                )
                                translationX = cos(angle).toFloat() *
                                    (maxWidth.toPx() / 2f - 21.dp.toPx()) * 0.79f
                                translationY = 11.dp.toPx() + sin(angle).toFloat() *
                                    (maxHeight.toPx() / 2f - 34.dp.toPx()) * 0.76f
                            },
                    )
                }
                if (turn != null) {
                    Text(
                        text = "KAABA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                            letterSpacing = 1.1.sp,
                        ),
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .graphicsLayer {
                                val angle = radians((turn - 90f).toDouble())
                                translationX = cos(angle).toFloat() *
                                    (maxWidth.toPx() / 2f - 21.dp.toPx()) * 0.64f
                                translationY = 11.dp.toPx() + sin(angle).toFloat() *
                                    (maxHeight.toPx() / 2f - 34.dp.toPx()) * 0.64f -
                                    41.dp.toPx()
                            },
                    )
                }
            }

        }
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = instruction,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isAligned) accent else foreground,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = headingDegrees
                        ?.let { "Phone heading ${it.roundToInt()}°" }
                        ?: "Open the compass for live guidance",
                    style = MaterialTheme.typography.labelSmall,
                    color = foreground.copy(alpha = 0.56f),
                    maxLines = 2,
                )
            }
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(foreground.copy(alpha = 0.08f))
                    .clickable(role = Role.Button, onClick = onOpenQibla)
                    .defaultMinSize(minHeight = 44.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "3D globe",
                    style = MaterialTheme.typography.labelSmall,
                    color = foreground.copy(alpha = 0.84f),
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open live Qibla globe",
                    tint = foreground.copy(alpha = 0.76f),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/**
 * Android's RecommendationExpandedStage, lean: the full grounded suggestion
 * with the reference header and the verified-source hint.
 */
@Composable
private fun ExpandedSuggestion(
    title: String,
    supportingText: String,
    footerText: String?,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ReadingPreviewGold, modifier = Modifier.size(20.dp))
            InsightSectionLabel("GROUNDED SUGGESTION", Modifier.weight(1f))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = supportingText,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                lineHeight = 25.sp,
            ),
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
        footerText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                ),
                color = Color.White.copy(alpha = 0.66f),
            )
        }
        Text(
            text = "Tap to open the verified source",
            style = MaterialTheme.typography.labelSmall,
            color = ReadingPreviewGold,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private sealed interface DeenlyKnowledgeTarget {
    data class Surah(val number: Int) : DeenlyKnowledgeTarget
    data class FortressDua(val chapterId: Int) : DeenlyKnowledgeTarget
    data class BukhariHadith(val hadithId: Int) : DeenlyKnowledgeTarget
    data class ShamayelBook(val bookId: Int) : DeenlyKnowledgeTarget
}

private data class DeenlyInsightSource(
    val collection: String,
    val reference: String,
    val topic: String,
    val text: String,
    val target: DeenlyKnowledgeTarget,
)

internal fun expandedKnowledgeSourceIndex(date: LocalDate, sourceCount: Int): Int {
    require(sourceCount > 0)
    return date.dayOfYear % sourceCount
}

/** The model-generated grounded suggestion shown by the enlarged AI tile. */
private data class DeenlyKnowledgeCard(
    val title: String,
    val sourceText: String,
    val reference: String,
    val target: DeenlyKnowledgeTarget,
)

private suspend fun generateInsightKnowledgeCard(
    date: LocalDate,
    appSituation: String?,
    fortressInvocations: Map<Int, List<com.starception.submission.shared.content.FortressInvocation>>,
): DeenlyKnowledgeCard? {
    val seed = date.toEpochDays().toInt()
    val sources = mutableListOf<DeenlyInsightSource>()
    val quranStore = SharedContentStore()
    val quranLanguage = QuranTranslationLanguage.fromCode(quranStore.quranTranslationLanguage())
    val surah = QuranData.surahs[((seed % QuranData.surahs.size) + QuranData.surahs.size) % QuranData.surahs.size]
    runCatching {
        createQuranVerseRepository()
            .getVersesBySurah(surah.number, quranLanguage)
            .take(3)
            .mapNotNull { it.translation.takeIf(String::isNotBlank) }
            .joinToString(" ")
    }.getOrNull()?.takeIf(String::isNotBlank)?.let { text ->
        sources += DeenlyInsightSource(
            collection = "Holy Quran",
            reference = "Surah ${surah.number} · ${surah.nameEnglish}",
            topic = "Reflect on ${surah.nameEnglish}",
            text = text,
            target = DeenlyKnowledgeTarget.Surah(surah.number),
        )
    }

    fortressInvocations
        .entries
        .sortedBy { it.key }
        .flatMap { it.value }
        .filter { invocation ->
            listOf(
                invocation.translation,
                invocation.context,
                invocation.instruction,
                invocation.note,
                invocation.postContext,
                invocation.description,
            ).any(String::isNotBlank)
        }
        .let { invocations ->
            invocations.getOrNull(
                if (invocations.isEmpty()) 0 else ((seed + 17) % invocations.size + invocations.size) % invocations.size,
            )
        }
        ?.let { invocation ->
            sources += DeenlyInsightSource(
                collection = "Fortress of the Muslim",
                reference = "Chapter ${invocation.chapterId} · Dua ${invocation.position}",
                topic = "A dua for today",
                text = listOf(
                    invocation.translation,
                    invocation.context,
                    invocation.instruction,
                    invocation.note,
                    invocation.postContext,
                    invocation.description,
                ).filter(String::isNotBlank).joinToString(" "),
                target = DeenlyKnowledgeTarget.FortressDua(invocation.chapterId),
            )
        }

    val hadithRepository = createSharedHadithRepository()
    BukhariBooks.all.getOrNull(((seed + 31) % BukhariBooks.all.size + BukhariBooks.all.size) % BukhariBooks.all.size)
        ?.let { book ->
            runCatching { hadithRepository.getHadiths(book.firstHadithId, book.lastHadithId) }
                .getOrNull()
                ?.filter { it.english.isNotBlank() }
                ?.let { hadiths ->
                    hadiths.getOrNull(
                        if (hadiths.isEmpty()) 0 else ((seed + 7) % hadiths.size + hadiths.size) % hadiths.size,
                    )
                }
                ?.let { hadith ->
                    sources += DeenlyInsightSource(
                        collection = "Sahih al-Bukhari",
                        reference = "Hadith ${hadith.id} · ${book.nameEnglish}",
                        topic = "A teaching from ${book.nameEnglish}",
                        text = hadith.english,
                        target = DeenlyKnowledgeTarget.BukhariHadith(hadith.id),
                    )
                }
        }
    ShamayelBooks.all.getOrNull(((seed + 47) % ShamayelBooks.all.size + ShamayelBooks.all.size) % ShamayelBooks.all.size)
        ?.let { book ->
            runCatching { hadithRepository.getShamayelHadiths(book.firstHadithId, book.lastHadithId) }
                .getOrNull()
                ?.filter { it.english.isNotBlank() }
                ?.let { hadiths ->
                    hadiths.getOrNull(
                        if (hadiths.isEmpty()) 0 else ((seed + 11) % hadiths.size + hadiths.size) % hadiths.size,
                    )
                }
                ?.let { hadith ->
                    sources += DeenlyInsightSource(
                        collection = "Shama'il At-Tirmidhi",
                        reference = "Hadith ${hadith.id} · ${book.nameEnglish}",
                        topic = "Learn about ${book.nameEnglish}",
                        text = hadith.english,
                        target = DeenlyKnowledgeTarget.ShamayelBook(book.id),
                    )
                }
        }

    if (sources.isEmpty()) return null
    val source = sources[expandedKnowledgeSourceIndex(date, sources.size)]
    println("[DeenlyKnowledge] selected ${source.collection}: ${source.reference}")
    val generatedTitle = DeenlyKnowledgeRuntime.generateKnowledgeTitle(
        collection = source.collection,
        reference = source.reference,
        topic = source.topic,
        appSituation = appSituation,
        sourceText = source.text,
    )
    if (generatedTitle == null) {
        println("[DeenlyKnowledge] model title rejected; using grounded source title")
    }
    return DeenlyKnowledgeCard(
        title = generatedTitle ?: source.topic,
        sourceText = source.text,
        reference = source.reference,
        target = source.target,
    )
}

/** Android's RecommendationExpandedStage loading beat. */
@Composable
private fun ModelSuggestionLoading() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                color = ReadingPreviewGold,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = "Reading your downloaded sources…",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.90f),
                textAlign = TextAlign.Center,
            )
        }
        PreparingKnowledgeFooter()
    }
}

@Composable
private fun PreparingKnowledgeFooter(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = ReadingPreviewGold,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Grounded in the Quran and hadith",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.90f),
            )
            Text(
                text = "Your insight will appear here when it’s ready.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
            )
        }
    }
}

/** The grounded card: the model's title over the exact source text. */
@Composable
private fun ModelSuggestionStage(
    card: DeenlyKnowledgeCard?,
    onOpen: () -> Unit,
    onPreferredHeightChange: (Dp) -> Unit = {},
) {
    val density = LocalDensity.current
    val sourceScrollState = rememberScrollState()
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Model-generated suggestion" },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = ReadingPreviewGold,
                modifier = Modifier.size(20.dp),
            )
            InsightSectionLabel("GROUNDED SUGGESTION", Modifier.weight(1f))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .insightReadingEdges(
                    canScrollBackward = { sourceScrollState.canScrollBackward },
                    canScrollForward = { sourceScrollState.canScrollForward },
                )
                .verticalScroll(state = sourceScrollState, overscrollEffect = null)
                .padding(vertical = InsightMotion.ReadingEdge)
                .onSizeChanged { onPreferredHeightChange(with(density) { it.height.toDp() } + 120.dp) },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = card?.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                ),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = card?.sourceText.orEmpty(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 25.sp,
                ),
                color = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (sourceScrollState.maxValue > 0) {
            LinearProgressIndicator(
                progress = {
                    sourceScrollState.value.toFloat() / sourceScrollState.maxValue
                },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = ReadingPreviewGold,
                trackColor = Color.White.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round,
            )
        }
        InsightSourceAction(reference = card?.reference.orEmpty(), onClick = onOpen)
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
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp),
            color = Color.White.copy(alpha = 0.82f),
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
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp).padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = "Download",
                            style = MaterialTheme.typography.labelLarge,
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
    minimumExpandedContentHeight: Dp = 240.dp,
    onPreferredHeightChange: (Dp) -> Unit = {},
    modifier: Modifier = Modifier,
    footerText: String? = null,
    topEndContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onCompactContentHeightChange: (Dp) -> Unit = {},
    content: @Composable (() -> Unit)? = null,
) {
    val readerChrome = rememberInsightReaderChrome(isExpanded)
    val chromeProgress = insightReaderChromeProgress(readerChrome, isExpanded)
    val density = LocalDensity.current
    var expandedLabelHeight by remember(label, density.fontScale) { mutableStateOf(0.dp) }
    // Android's backgroundBaseScale: headroom so the counter-scrolling
    // artwork never reveals the card's edge mid-swipe.
    val backgroundBaseScale = 1.065f
    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 32.dp else 26.dp,
        animationSpec = tween(InsightMotion.ExpansionMillis, easing = FastOutSlowInEasing),
        label = "insightCornerRadius",
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isExpanded) 10.dp else 6.dp,
        animationSpec = tween(InsightMotion.ExpansionMillis, easing = FastOutSlowInEasing),
        label = "insightShadowElevation",
    )
    var revealExpandedContent by remember(isExpanded) { mutableStateOf(!isExpanded) }
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            kotlinx.coroutines.delay(InsightMotion.EntranceDelayMillis)
            revealExpandedContent = true
        }
    }
    val expandedContentEntrance by animateFloatAsState(
        targetValue = if (revealExpandedContent) 1f else 0f,
        animationSpec = tween(durationMillis = InsightMotion.EntranceMillis, easing = FastOutSlowInEasing),
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
                    .blur(if (isExpanded) 1.8.dp else 0.dp)
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
                        .blur(if (isExpanded) 1.8.dp else 0.dp)
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
                                0f to Color.Black.copy(alpha = if (isExpanded) 0.70f else 0f),
                                0.50f to Color.Black.copy(alpha = if (isExpanded) 0.70f else 0f),
                                0.72f to Color(0xFF241D19).copy(alpha = if (isExpanded) 0.76f else 0.12f),
                                1f to Color(0xFF241D19).copy(
                                    alpha = if (isExpanded) 0.86f else 0.66f,
                                ),
                            ),
                        ),
                    ),
            )
            if (isExpanded) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.86f),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = 1f - chromeProgress
                            translationY = -12.dp.toPx() * chromeProgress
                        }
                        .padding(start = 22.dp, end = 70.dp, top = 20.dp)
                        .onSizeChanged { expandedLabelHeight = with(density) { it.height.toDp() } }
                        .semantics { heading() },
                )
            } else {
                CupertinoSurface(
                    onClick = onClick ?: {},
                    shape = CircleShape,
                    color = if (isExpanded) Color.Transparent else Color.Black.copy(alpha = 0.34f),
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
                                fontSize = if (isExpanded) 12.sp else 11.sp,
                                letterSpacing = 0.sp,
                            ),
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
            topEndContent?.takeIf { chromeProgress < 0.999f }?.let { trailing ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .graphicsLayer { alpha = 1f - chromeProgress }
                        .padding(top = 11.dp, end = 11.dp),
                ) {
                    trailing()
                }
            }
            if (isExpanded) {
                InsightReaderControlsButton(readerChrome, Modifier.align(Alignment.TopEnd).padding(end = 8.dp))
                InsightContentLayout(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 22.dp, end = 22.dp,
                            top = maxOf(64.dp, expandedLabelHeight + 40.dp) * (1f - chromeProgress) +
                                48.dp * chromeProgress,
                            bottom = (24f - 16f * chromeProgress).dp,
                        ),
                    spacing = 12.dp,
                    minimumStageHeight = minimumExpandedContentHeight,
                    onPreferredHeightChange = { height ->
                        onPreferredHeightChange(height +
                            maxOf(64.dp, expandedLabelHeight + 40.dp) +
                            24.dp)
                    },
                    readerChrome = readerChrome,
                    chromeProgress = chromeProgress,
                    stage = {
                        content?.let { expandedContent ->
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 640.dp)
                                    .fillMaxSize()
                                    .then(
                                        if (expandedContentEntrance < 0.999f) {
                                            Modifier.graphicsLayer {
                                                alpha = expandedContentEntrance
                                                translationY = (1f - expandedContentEntrance) *
                                                    InsightMotion.EntranceOffset.toPx()
                                            }
                                        } else Modifier,
                                    ),
                                // Android centers the expanded stage inside its
                                // weighted zone instead of pinning it to the top.
                                contentAlignment = Alignment.Center,
                            ) {
                                expandedContent()
                            }
                        }
                    },
                    metadata = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ArtworkTileMetadata(
                                title = title,
                                subtitle = subtitle,
                                footerText = footerText,
                                expanded = true,
                            )
                        }
                    },
                )
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 22.dp, bottom = 24.dp)
                        .onSizeChanged { size ->
                            onCompactContentHeightChange(
                                with(density) { size.height.toDp() },
                            )
                        },
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
            maxLines = if (expanded) Int.MAX_VALUE else 3,
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
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (footerText != null) {
        Text(
            text = footerText,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = if (expanded) 14.sp else 12.sp,
                lineHeight = if (expanded) 20.sp else 16.sp,
                letterSpacing = 0.sp,
            ),
            color = Color.White.copy(alpha = 0.78f),
            fontWeight = FontWeight.Medium,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
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
