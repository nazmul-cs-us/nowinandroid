package com.starception.submission.feature.prayertimes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.home_cover_prayer_day_portrait
import com.starception.submission.core.images.resources.home_cover_prayer_night_portrait
import com.starception.submission.core.images.resources.home_cover_salah_portrait
import com.starception.submission.core.images.resources.home_cover_quran_portrait
import com.starception.submission.core.images.resources.home_cover_qibla_portrait
import com.starception.submission.core.images.resources.home_cover_suggestion_portrait
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** The feed and its transition render the same scene and carry the same page identity. */
internal data class HomeArtworkCard(
    val page: Int,
    val label: String,
    val subtitle: String,
    val background: DrawableResource,
    val foreground: DrawableResource? = null,
)

internal data class HomeFeedPage(
    val eyebrow: String,
    val prompt: String,
    val railPages: List<Int>,
    val featuredPage: Int,
)

internal fun homeFeedPage(page: Int): HomeFeedPage = when (page) {
    1 -> HomeFeedPage("TODAY'S SALAH", "What will you carry into your next prayer?", listOf(2, 3, 4), 0)
    2 -> HomeFeedPage("DAILY REFLECTION", "What will you take from today’s reading?", listOf(0, 1, 3), 4)
    3 -> HomeFeedPage("QIBLA", "Take a moment to turn toward Makkah.", listOf(2, 0, 4), 1)
    4 -> HomeFeedPage("FOR THIS MOMENT", "What would help you right now?", listOf(0, 1, 3), 2)
    else -> HomeFeedPage("PRAYER REFLECTION", "What intention will you carry into your next prayer?", listOf(2, 1, 3), 4)
}

private val homeArtworkCards by lazy {
    listOf(
        HomeArtworkCard(2, "Today's reading", "Quran for today",
            Res.drawable.home_cover_quran_portrait),
        HomeArtworkCard(1, "Today's salah", "Prayer & hadith",
            Res.drawable.home_cover_salah_portrait),
        HomeArtworkCard(3, "Qibla", "Direction to Makkah",
            Res.drawable.home_cover_qibla_portrait),
        HomeArtworkCard(4, "AI suggested", "For this moment",
            Res.drawable.home_cover_suggestion_portrait),
    )
}

@Composable
internal fun HomeArtworkScene(card: HomeArtworkCard, modifier: Modifier = Modifier, copyAlpha: Float = 1f) {
    val dark = LocalDarkTheme.current
    Box(modifier) {
        Image(
            painter = painterResource(card.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        card.foreground?.let { foreground ->
            Image(
                painter = painterResource(foreground),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize().padding(6.dp),
            )
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            listOf(
                if (dark) Color.Black.copy(alpha = .12f) else Color.White.copy(alpha = .16f),
                Color.Transparent,
                Color.Black.copy(alpha = if (dark) .78f else .70f),
            ),
        )))
        if (copyAlpha > 0f) {
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)
                .graphicsLayer { alpha = copyAlpha }) {
                Text(card.label, color = Color.White, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(card.subtitle, color = Color.White.copy(alpha = .85f),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Secondary artwork cards open the existing insight reader and its real content. */
@Composable
internal fun HomeArtworkRail(
    focusedPage: Int = 0,
    useNightArtwork: Boolean = false,
    onSelect: (HomeArtworkCard, Rect) -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val largeText = LocalDensity.current.fontScale > 1.3f
    val prayerNow = HomeArtworkCard(
        0, "Prayer now", "Your next prayer",
        if (useNightArtwork) Res.drawable.home_cover_prayer_night_portrait
        else Res.drawable.home_cover_prayer_day_portrait,
    )
    val cards = homeArtworkCards + prayerNow
    AnimatedContent(
        targetState = focusedPage.coerceIn(0, 4),
        transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
        label = "homeFeedSelectedInsight",
    ) { page ->
        val feed = homeFeedPage(page)
        val railCards = feed.railPages.mapNotNull { id -> cards.firstOrNull { it.page == id } }
        val featuredCard = cards.first { it.page == feed.featuredPage }
        val railState = rememberLazyListState()
        val railSurface = if (LocalDarkTheme.current) Color(0xFF080B14)
            else MaterialTheme.colorScheme.background
        Column {
            HomeFeedSectionLabel("EXPLORE YOUR DAY")
            Spacer(Modifier.height(14.dp))
            Box(Modifier.drawWithContent {
                drawContent()
                if (railState.canScrollForward) {
                    val fadeWidth = 24.dp.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, railSurface),
                            startX = size.width - fadeWidth,
                            endX = size.width,
                        ),
                        topLeft = Offset(size.width - fadeWidth, 0f),
                        size = Size(fadeWidth, size.height),
                    )
                }
            }) {
                LazyRow(
                    state = railState,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                ) {
                    items(railCards, key = HomeArtworkCard::page) { card ->
                        Column(Modifier.width(if (largeText) 142.dp else 124.dp)) {
                            ArtworkCard(card, Modifier.fillMaxWidth(), 126.dp, onSelect, compact = true)
                            Spacer(Modifier.height(8.dp))
                            Text(card.label, color = ink, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = if (largeText) 2 else 1,
                                overflow = TextOverflow.Ellipsis)
                            Text(card.subtitle, color = ink.copy(alpha = .62f),
                                style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            HomeFeedSectionLabel("SUGGESTED FOR YOU")
            Spacer(Modifier.height(16.dp))
            ArtworkCard(featuredCard, Modifier.fillMaxWidth(), 224.dp, onSelect)
            Spacer(Modifier.height(10.dp))
            Text(featuredCard.label, color = ink, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text(featuredCard.subtitle, color = ink.copy(alpha = .62f),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun HomeReflectionPrompt(
    focusedPage: Int = 0,
    onDarkCover: Boolean = false,
    onClick: () -> Unit,
) {
    val ink = if (onDarkCover) Color.White else MaterialTheme.colorScheme.onBackground
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HomeSectionDivider(ink)
        AnimatedContent(
            targetState = focusedPage.coerceIn(0, 4),
            transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
            label = "homeReflectionSelectedInsight",
        ) { page ->
            val feed = homeFeedPage(page)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(feed.eyebrow, style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = .72f))
                Text(
                    feed.prompt,
                    style = if (onDarkCover) MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal)
                        else MaterialTheme.typography.titleLarge,
                    color = ink,
                )
            }
        }
    }
}

@Composable
private fun HomeFeedSectionLabel(text: String) {
    val ink = MaterialTheme.colorScheme.onBackground
    Column {
        HomeSectionDivider(ink)
        Spacer(Modifier.height(18.dp))
        Text(text, color = ink.copy(alpha = .82f), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HomeSectionDivider(ink: Color) {
    Box(
        Modifier.fillMaxWidth().height(1.dp).background(
            Brush.horizontalGradient(
                0f to ink.copy(alpha = 0f),
                .08f to ink.copy(alpha = .12f),
                .5f to ink.copy(alpha = .28f),
                .92f to ink.copy(alpha = .12f),
                1f to ink.copy(alpha = 0f),
            ),
        ),
    )
}

@Composable
private fun ArtworkCard(
    card: HomeArtworkCard,
    modifier: Modifier,
    height: androidx.compose.ui.unit.Dp,
    onSelect: (HomeArtworkCard, Rect) -> Unit,
    compact: Boolean = false,
) {
    val measuredHeight = if (LocalDensity.current.fontScale > 1.3f) height + 24.dp else height
    val dark = LocalDarkTheme.current
    val shape = RoundedCornerShape(if (compact) 22.dp else 28.dp)
    val edge = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (dark) .28f else .52f),
        .45f to Color.White.copy(alpha = if (dark) .10f else .20f),
        1f to Color.Black.copy(alpha = if (dark) .26f else .14f),
    )
    var bounds by remember { mutableStateOf(Rect.Zero) }
    HomeArtworkScene(
        card = card,
        copyAlpha = 0f,
        modifier = modifier.height(measuredHeight)
            .testTag("homeArtworkCard-${card.page}")
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .then(if (dark) Modifier else Modifier.shadow(
                elevation = if (compact) 4.dp else 6.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = .08f),
                spotColor = Color.Black.copy(alpha = .12f),
            ))
            .clip(shape)
            .border(1.dp, edge, shape)
            .clickable { if (bounds.width > 0f) onSelect(card, bounds) },
    )
}
