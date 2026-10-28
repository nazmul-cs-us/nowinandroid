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
import com.starception.submission.core.images.resources.insight_prayer_background
import com.starception.submission.core.images.resources.insight_qibla_background
import com.starception.submission.core.images.resources.insight_quran_background
import com.starception.submission.core.images.resources.insight_suggestion
import com.starception.submission.core.images.resources.prayer_now_insight_day
import com.starception.submission.core.images.resources.prayer_now_insight_night
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.launch

internal fun homeCoverArtwork(page: Int, useNightArtwork: Boolean): DrawableResource = when (page) {
    0 -> if (useNightArtwork) Res.drawable.prayer_now_insight_night else Res.drawable.prayer_now_insight_day
    1 -> Res.drawable.insight_prayer_background
    2 -> Res.drawable.insight_quran_background
    3 -> Res.drawable.insight_qibla_background
    else -> Res.drawable.insight_suggestion
}

/** Keep the poster legible through the recommendation, then hand it into the feed. */
internal fun homeCoverScrim(isDarkTheme: Boolean): Brush = if (isDarkTheme) {
    Brush.verticalGradient(
        0f to Color(0xFF071C30).copy(alpha = .12f),
        .48f to Color(0xFF0B1D2C).copy(alpha = .22f),
        .72f to Color(0xFF071725).copy(alpha = .55f),
        .91f to Color(0xFF080B14).copy(alpha = .98f),
        1f to Color(0xFF080B14),
    )
} else {
    Brush.verticalGradient(
        0f to Color.White.copy(alpha = .08f),
        .48f to Color.White.copy(alpha = .10f),
        .60f to Color.White.copy(alpha = .48f),
        .73f to Color.White.copy(alpha = .62f),
        .90f to Color.White.copy(alpha = .92f),
        1f to Color.White,
    )
}

/** A blurred copy of the same scene gives the cover copy a soft, readable backdrop. */
@Composable
internal fun HomeCoverArtworkBlur(page: Int, useNightArtwork: Boolean) {
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
        Image(
            painter = painterResource(homeCoverArtwork(page, useNightArtwork)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = if (page == 0) Alignment.BottomEnd else Alignment.Center,
            modifier = Modifier.fillMaxSize()
                .then(if (page == 0) Modifier.graphicsLayer {
                    scaleX = 1.25f
                    scaleY = 1.25f
                    translationY = -size.height * .20f
                } else Modifier)
                .blur(20.dp),
        )
    }
}

/** A quiet first-screen composition; tapping continues into the corresponding real reader. */
@Composable
internal fun ImmersiveHomeCover(
    selectedPage: Int,
    isDarkTheme: Boolean,
    nextPrayerName: String,
    nextPrayerCountdown: String,
    onSelectionChanged: (Int) -> Unit,
    onOpen: (Int) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = selectedPage.coerceIn(0, 4), pageCount = { 5 })
    val scope = rememberCoroutineScope()
    LaunchedEffect(selectedPage) {
        if (pagerState.settledPage != selectedPage) pagerState.scrollToPage(selectedPage.coerceIn(0, 4))
    }
    LaunchedEffect(pagerState.settledPage) { onSelectionChanged(pagerState.settledPage) }
    val ink = if (isDarkTheme) Color.White else Color(0xFF211C2B)
    val secondaryInk = ink.copy(alpha = if (isDarkTheme) .80f else .72f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The reference leaves breathing room between the recommendation and
        // the lower feed; anchoring both to the tile bottom puts them too low.
        val copyBottom = maxHeight * .20f
        val indicatorBottom = maxHeight * .13f
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val (eyebrow, title, detail) = when (page) {
                0 -> Triple("YOUR DAY", "A moment to pause",
                    listOfNotNull(nextPrayerName.takeIf(String::isNotBlank),
                        nextPrayerCountdown.takeIf(String::isNotBlank)).joinToString(" · "))
                1 -> Triple("TODAY'S SALAH", "Make room for reflection", "A hadith for this prayer")
                2 -> Triple("TODAY'S READING", "Return to the Quran", "Your daily passage")
                3 -> Triple("QIBLA", "Turn toward Makkah", "Find your direction")
                else -> Triple("FOR THIS MOMENT", "A thought for today", "Explore a grounded suggestion")
            }
            Box(Modifier.fillMaxSize().clipToBounds().clickable { onOpen(page) }) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = copyBottom),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        eyebrow,
                        color = secondaryInk,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        title,
                        color = ink,
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
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
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(5) { index ->
                val active = pagerState.currentPage == index
                Box(
                    Modifier.size(width = 23.dp, height = 26.dp)
                        .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                        .semantics { contentDescription = "Show insight ${index + 1} of 5" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = if (active) 16.dp else 5.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(ink.copy(alpha = if (active) 1f else .48f)))
                }
            }
        }
    }
}
