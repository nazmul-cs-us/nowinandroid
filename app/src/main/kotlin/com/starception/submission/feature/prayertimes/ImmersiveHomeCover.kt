package com.starception.submission.feature.prayertimes

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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.home_cover_prayer_day_portrait
import com.starception.submission.core.images.resources.home_cover_prayer_night_portrait
import com.starception.submission.core.images.resources.home_cover_salah_portrait
import com.starception.submission.core.images.resources.home_cover_quran_portrait
import com.starception.submission.core.images.resources.home_cover_qibla_portrait
import com.starception.submission.core.images.resources.home_cover_suggestion_portrait
import com.starception.submission.feature.prayertimes.celestial.PrayerHomeCoverCelestialLayer
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.launch
import kotlin.math.abs

internal fun homeCoverArtwork(page: Int, useNightArtwork: Boolean): DrawableResource = when (page) {
    0 -> if (useNightArtwork) Res.drawable.home_cover_prayer_night_portrait else Res.drawable.home_cover_prayer_day_portrait
    1 -> Res.drawable.home_cover_salah_portrait
    2 -> Res.drawable.home_cover_quran_portrait
    3 -> Res.drawable.home_cover_qibla_portrait
    else -> Res.drawable.home_cover_suggestion_portrait
}

internal data class HomeCoverCopy(val eyebrow: String, val title: String, val detail: String)

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

/** Cover ink follows the photograph; the app theme alone cannot describe its contrast. */
internal fun homeCoverHeaderUsesLightText(page: Int, useNightArtwork: Boolean): Boolean = when (page) {
    0 -> true // The prayer poster needs the same white, quiet greeting as the reference.
    2, 3 -> false // Reading and Qibla have bright windows behind the greeting.
    else -> true
}

/** Every portrait cover keeps white copy on the same deep, blurred lower surface. */
internal fun homeCoverCopyUsesLightText(
    page: Int,
    useNightArtwork: Boolean,
    isDarkTheme: Boolean,
): Boolean = true

/** Keep the cover title readable while the artwork fades into the reflection. */
internal fun homeCoverScrim(): Brush = Brush.verticalGradient(
    0f to Color(0xFF071C30).copy(alpha = .12f),
    .48f to Color(0xFF0B1D2C).copy(alpha = .22f),
    .72f to Color(0xFF071725).copy(alpha = .55f),
    .91f to Color(0xFF080B14).copy(alpha = .98f),
    1f to Color(0xFF080B14),
)

/** A blurred copy of the same scene gives the cover copy a soft, readable backdrop. */
@Composable
internal fun HomeCoverArtworkBlur(lowerPage: Int, swipeProgress: Float, useNightArtwork: Boolean) {
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        .48f to Color.Transparent,
                        .55f to Color.White.copy(alpha = .88f),
                        .67f to Color.White.copy(alpha = .88f),
                        .76f to Color.Transparent,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Box(Modifier.fillMaxSize().blur(20.dp)) {
            HomeCoverArtworkImages(lowerPage, swipeProgress, useNightArtwork)
        }
    }
}

@Composable
private fun HomeCoverArtworkImages(lowerPage: Int, swipeProgress: Float, useNightArtwork: Boolean) {
    val nextPage = (lowerPage + 1) % 5
    for (layer in 0..1) {
        if (layer == 1 && swipeProgress <= .001f) continue
        val page = if (layer == 0) lowerPage else nextPage
        Image(
            painter = painterResource(homeCoverArtwork(page, useNightArtwork)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                translationX = (if (layer == 0) -swipeProgress else 1f - swipeProgress) * size.width * .18f
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
    epochSeconds: Double? = null,
    latitude: Double? = null,
    longitude: Double? = null,
    scrollOffsetPx: Int = 0,
    coverHeightPx: Int = 0,
) {
    val scrollPosition = position.value
    val lowerPage = scrollPosition.toInt().coerceIn(0, 4)
    val swipeProgress = (scrollPosition - lowerPage).coerceIn(0f, 1f)
    HomeCoverArtworkImages(lowerPage, swipeProgress, useNightArtwork)
    if (!useNightArtwork && lowerPage == 0) {
        // The day scene has a bright blue sky behind the greeting. A local
        // shadow over the upper sky keeps white type legible without darkening
        // the water and copy below it.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color(0xFF101C2B).copy(alpha = .36f),
            .30f to Color(0xFF101C2B).copy(alpha = .12f),
            .46f to Color.Transparent,
        )))
    }
    val prayerVisibility = when (lowerPage) {
        0 -> 1f - swipeProgress
        4 -> swipeProgress
        else -> 0f
    }
    if (!isExpanded && prayerVisibility > 0f && epochSeconds != null && latitude != null && longitude != null) {
        PrayerHomeCoverCelestialLayer(
            epochSeconds = epochSeconds,
            latitude = latitude,
            longitude = longitude,
            isNight = useNightArtwork,
            playing = false,
            modifier = Modifier.graphicsLayer { alpha = prayerVisibility },
        )
    }
    if (!isExpanded) HomeCoverArtworkBlur(lowerPage, swipeProgress, useNightArtwork)
    Box(Modifier.fillMaxSize().background(
        homeCoverScrim()
    ))
    // The cover scene is fixed behind the vertically scrolling feed. Once the
    // cover leaves the viewport, fully cover it so no stale artwork shows
    // through the transparent top of the next section.
    if (!isExpanded && coverHeightPx > 0) {
        val fadeStart = coverHeightPx * .35f
        val fadeDistance = coverHeightPx * .50f
        val feedOpacity = ((scrollOffsetPx - fadeStart) / fadeDistance).coerceIn(0f, 1f)
        if (feedOpacity > 0f) {
            Box(
                Modifier.fillMaxSize().background(
                    (if (isDarkTheme) Color(0xFF080B14) else Color.White).copy(alpha = feedOpacity)
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
            pagerState.scrollToPage(pagerState.settledPage + delta)
        }
    }
    LaunchedEffect(pagerState.settledPage) { onSelectionChanged(pagerState.settledPage % 5) }
    LaunchedEffect(pagerState) {
        snapshotFlow {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction) % 5f
        }.collect(onScrollPositionChanged)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Keep the copy on the water/floor, below the focal artwork. Move the
        // page dots with it so they retain a clear gap beneath the detail line.
        val copyBottom = maxHeight * .12f
        val indicatorBottom = maxHeight * .05f
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { absolutePage ->
            val page = absolutePage % 5
            val useLightText = homeCoverCopyUsesLightText(page, useNightArtwork, isDarkTheme)
            val ink = if (useLightText) Color.White else Color(0xFF211C2B)
            val secondaryInk = ink.copy(alpha = if (useLightText) .80f else .72f)
            val (eyebrow, title, detail) = homeCoverCopy(
                page, currentPrayerName, nextPrayerName, nextPrayerCountdown,
                readingSurahName, readingSurahNumber,
            )
            Box(Modifier.fillMaxSize().clipToBounds().clickable { onOpen(page) }) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = copyBottom),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        eyebrow,
                        color = secondaryInk,
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = .7.sp),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        title,
                        color = ink,
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            color = secondaryInk,
                            style = MaterialTheme.typography.bodyMedium,
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
                                pagerState.animateScrollToPage(pagerState.currentPage + delta)
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
