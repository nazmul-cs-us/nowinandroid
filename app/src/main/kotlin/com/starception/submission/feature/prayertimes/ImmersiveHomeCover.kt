package com.starception.submission.feature.prayertimes

import com.starception.submission.core.designsystem.theme.homeFeedPalette

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.images.resources.Res
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun homeCoverArtwork(page: Int, useNightArtwork: Boolean): DrawableResource =
    com.starception.submission.core.images.currentHomeArtwork(page, useNightArtwork)

internal data class HomeCoverCopy(val eyebrow: String, val title: String, val detail: String)

/** The cover copy fades out before the next page's copy comes into focus. */
internal fun homeCoverCopyOpacity(pageOffset: Float): Float {
    val visible = (1f - abs(pageOffset) * 2f).coerceIn(0f, 1f)
    return visible * visible * (3f - 2f * visible)
}

/** Signed distance from a page, including the wrap between the last and first covers. */
internal fun homeSwipePageOffset(position: Float, page: Int): Float {
    val distance = ((position - page + 5f) % 5f)
    return if (distance > 2.5f) distance - 5f else distance
}

internal fun homeGreetingForHour(hour: Int): String = when (hour) {
    in 5..11 -> "Salam, morning"
    in 12..17 -> "Salam, afternoon"
    else -> "Salam, evening"
}

/** The poster previews the destination opened by its tap, not a generic mood. */
internal fun homeCoverCopy(
    page: Int,
    currentPrayerName: String,
    nextPrayerName: String,
    nextPrayerCountdown: String,
    readingSurahName: String,
    readingSurahNumber: Int,
): HomeCoverCopy = when (page) {
    0 -> HomeCoverCopy(
        "PRAYER NOW",
        currentPrayerName.takeIf(String::isNotBlank)?.let { "Make time for $it" } ?: "Your prayer moment",
        if (nextPrayerName.isNotBlank()) {
            "Next · $nextPrayerName" + nextPrayerCountdown.takeIf(String::isNotBlank)?.let { " in $it" }.orEmpty()
        } else "Today's prayer times",
    )
    1 -> HomeCoverCopy(
        "TODAY'S SALAH",
        currentPrayerName.takeIf(String::isNotBlank)?.let { "Hadith for $it" } ?: "Hadith for this prayer",
        "Read a narration for this prayer",
    )
    2 -> HomeCoverCopy(
        "TODAY'S READING",
        readingSurahName.takeIf(String::isNotBlank)?.let { "Read $it" } ?: "Read today's Surah",
        if (readingSurahNumber > 0) "Surah $readingSurahNumber · today's ayahs" else "Today's Quran passage",
    )
    3 -> HomeCoverCopy("QIBLA DIRECTION", "Find the Qibla", "Live compass toward Makkah")
    else -> HomeCoverCopy("AI SUGGESTED", "A verified reading", "Quran, Hadith or dua for this moment")
}

/** All Getty covers have a dark upper scrim protecting the greeting and system icons. */
@Suppress("UNUSED_PARAMETER")
internal fun homeCoverHeaderUsesLightText(page: Int, useNightArtwork: Boolean): Boolean = true

/** Every portrait cover keeps white copy on the same deep, blurred lower surface. */
internal fun homeCoverCopyUsesLightText(
    page: Int,
    useNightArtwork: Boolean,
    isDarkTheme: Boolean,
): Boolean = true

/** Keep the cover title readable while the artwork fades into the reflection. */
internal fun homeCoverScrim(
    prayerVisibility: Float,
    feedBackground: Color,
    isExpanded: Boolean = false,
): Brush = Brush.verticalGradient(
    0f to feedBackground.copy(alpha = .12f),
    (.48f + .03f * prayerVisibility) to feedBackground.copy(alpha = .22f),
    (.72f + .03f * prayerVisibility) to feedBackground.copy(alpha = if (isExpanded) .18f else .55f),
    .91f to feedBackground.copy(alpha = if (isExpanded) .22f else .98f),
    1f to feedBackground.copy(alpha = if (isExpanded) .28f else 1f),
)

@Composable
private fun HomeCoverArtworkImages(lowerPage: Int, swipeProgress: Float, useNightArtwork: Boolean) {
    val nextPage = (lowerPage + 1) % 5
    for (layer in 0..1) {
        if (layer == 1 && swipeProgress <= .001f) continue
        val page = if (layer == 0) lowerPage else nextPage
        Image(
            painter = com.starception.submission.core.images.currentHomeArtworkPainter(page, useNightArtwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                // Overscan covers the parallax travel so neither photo exposes
                // a hard vertical edge while it crossfades over the other.
                scaleX = 1.12f
                scaleY = 1.12f
                translationX = (if (layer == 0) -swipeProgress else 1f - swipeProgress) * size.width * .06f
                alpha = if (layer == 0) 1f else swipeProgress
            },
        )
    }
}

/** Recompose only the artwork while dragging, rather than the entire home feed. */
@Composable
internal fun HomeCoverArtworkBackdrop(
    position: State<Float>,
    useNightArtwork: Boolean,
    isDarkTheme: Boolean,
    isExpanded: Boolean,
    scrollOffsetPx: Int = 0,
    coverHeightPx: Int = 0,
) {
    val feedBackground = homeFeedPalette(MaterialTheme.colorScheme).background
    val scrollPosition = position.value
    val lowerPage = scrollPosition.toInt().coerceIn(0, 4)
    val swipeProgress = (scrollPosition - lowerPage).coerceIn(0f, 1f)
    val prayerVisibility = when (lowerPage) {
        0 -> 1f - swipeProgress
        4 -> swipeProgress
        else -> 0f
    }
    HomeCoverArtworkImages(lowerPage, swipeProgress, useNightArtwork)
    // Protect white header ink on every rotating artwork, in either app theme.
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        0f to Color.Black.copy(alpha = .66f),
        .20f to Color.Black.copy(alpha = .60f),
        .48f to Color.Transparent,
    )))
    // The lower scrim already protects the cover copy. A second full-screen
    // blurred scene kept Home rendering continuously even at rest (~50% of a
    // core on Samsung); the single image and scrim settle near idle instead.
    Box(Modifier.fillMaxSize().background(
        homeCoverScrim(prayerVisibility, feedBackground, isExpanded)
    ))
    // The cover scene is fixed behind the vertically scrolling feed. Once the
    // cover leaves the viewport, fully cover it so no stale artwork shows
    // through the transparent top of the next section.
    if (!isExpanded && coverHeightPx > 0) {
            val feedOpacity = homeBackdropFeedOpacity(scrollOffsetPx.toFloat(), coverHeightPx.toFloat())
        if (feedOpacity > 0f) {
            Box(
                Modifier.fillMaxSize().background(
                    feedBackground.copy(alpha = feedOpacity)
                ),
            )
        }
    }
}

/** A quiet first-screen composition; tapping continues into the corresponding real reader. */
@Composable
internal fun ImmersiveHomeCover(
    selectedPage: Int,
    isDarkTheme: Boolean,
    useNightArtwork: Boolean = isDarkTheme,
    nextPrayerName: String,
    nextPrayerCountdown: String,
    currentPrayerName: String = "",
    readingSurahName: String = "",
    readingSurahNumber: Int = 0,
    onSelectionChanged: (Int) -> Unit,
    onScrollPositionChanged: (Float) -> Unit = {},
    onOpen: (Int) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = 5_000 + selectedPage.coerceIn(0, 4), pageCount = { Int.MAX_VALUE })
    val scope = rememberCoroutineScope()
    LaunchedEffect(selectedPage) {
        val current = pagerState.settledPage % 5
        if (current != selectedPage) {
            val forward = (selectedPage - current + 5) % 5
            val delta = if (forward <= 2) forward else forward - 5
            pagerState.animateScrollToPage(
                pagerState.settledPage + delta,
                animationSpec = tween(460, easing = FastOutSlowInEasing),
            )
        }
    }
    LaunchedEffect(pagerState.settledPage) { onSelectionChanged(pagerState.settledPage % 5) }
    LaunchedEffect(pagerState) {
        snapshotFlow {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction) % 5f
        }.collect(onScrollPositionChanged)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Keep the copy on the water/floor, below the focal artwork. The page
        // dots share Prayer Now's baseline on every page so neither the copy
        // nor the dots jump while swiping between cover pages.
        val indicatorBottom = maxHeight * .012f
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { absolutePage ->
            val page = absolutePage % 5
            // Every cover keeps its copy on the same water/floor baseline as
            // Prayer Now so the eyebrow does not jump while swiping pages.
            val copyBottom = (maxHeight * .055f).coerceAtLeast(32.dp)
            val useLightText = homeCoverCopyUsesLightText(page, useNightArtwork, isDarkTheme)
            val ink = if (useLightText) Color.White else Color(0xFF211C2B)
            val secondaryInk = ink.copy(alpha = if (useLightText) .80f else .72f)
            val (eyebrow, title, detail) = homeCoverCopy(
                page, currentPrayerName, nextPrayerName, nextPrayerCountdown,
                readingSurahName, readingSurahNumber,
            )
            val pageOffset = pagerState.currentPage - absolutePage + pagerState.currentPageOffsetFraction
            Box(Modifier.fillMaxSize().clickable { onOpen(page) }) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        // Counter the pager's full-width travel. Copy drifts
                        // only a few pixels and fades before the next arrives.
                        .graphicsLayer {
                            val offset = pagerState.currentPage - absolutePage + pagerState.currentPageOffsetFraction
                            translationX = offset * (size.width - 16.dp.toPx())
                            translationY = abs(offset) * 8.dp.toPx()
                            alpha = homeCoverCopyOpacity(offset)
                        }
                        .then(if (abs(pageOffset) >= .5f) Modifier.clearAndSetSemantics { } else Modifier)
                        .padding(start = 32.dp, end = 32.dp, bottom = copyBottom),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        eyebrow,
                        color = secondaryInk,
                        style = HomeTypography.eyebrow,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        title,
                        color = ink,
                        style = HomeTypography.hero,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            color = secondaryInk,
                            style = HomeTypography.body,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = indicatorBottom),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val indicatorPage = pagerState.currentPage % 5
            val indicatorInk = if (homeCoverCopyUsesLightText(indicatorPage, useNightArtwork, isDarkTheme)) {
                Color.White
            } else {
                Color(0xFF211C2B)
            }
            val indicatorPosition = (pagerState.currentPage + pagerState.currentPageOffsetFraction) % 5f
            // Show the current insight and all four swipe destinations. Narrow
            // cells keep the full set as quiet as the reference's page marks.
            repeat(5) { index ->
                val directDistance = abs(indicatorPosition - index)
                val emphasis = (1f - minOf(directDistance, 5f - directDistance)).coerceIn(0f, 1f)
                Box(
                    Modifier.size(width = 10.dp, height = 26.dp)
                        .clickable {
                            scope.launch {
                                val current = pagerState.currentPage % 5
                                val forward = (index - current + 5) % 5
                                val delta = if (forward <= 2) forward else forward - 5
                            pagerState.animateScrollToPage(
                                pagerState.currentPage + delta,
                                animationSpec = tween(460, easing = FastOutSlowInEasing),
                            )
                            }
                        }
                        .semantics { contentDescription = "Show insight ${index + 1} of 5" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = (3f + 9f * emphasis).dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(indicatorInk.copy(alpha = .48f + .52f * emphasis)))
                }
            }
        }
    }
}
