package com.starception.submission.feature.prayertimes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.starception.submission.R
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.floor

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

/** Ground each settled cover page in a relevant downloaded Quran or Hadith source. */
internal fun homeFeedKnowledgeQuery(page: Int): String = when (page) {
    0 -> "intention prayer"
    1 -> "salah prayer"
    2 -> "Quran reading reflection"
    3 -> "Makkah prayer direction"
    else -> "guidance faith today"
}

/** Retain one model turn per page/day across Home navigation within this process. */
internal class HomePageGenerationState {
    val generated = mutableStateMapOf<Int, NowNudgeKnowledgeCandidate>()
    val attempted = mutableStateMapOf<Int, Boolean>()
    var generatingPage by mutableIntStateOf(-1)
}

internal object HomePageGenerationCache {
    private var day: String? = null
    private var state = HomePageGenerationState()

    fun forDay(requestedDay: String): HomePageGenerationState {
        if (day != requestedDay) {
            day = requestedDay
            state = HomePageGenerationState()
        }
        return state
    }
}

@Composable
private fun homeArtworkCards(): List<HomeArtworkCard> {
    return listOf(
        HomeArtworkCard(2, "Today's reading", "Quran for today",
            homeCoverArtwork(2, false)),
        HomeArtworkCard(1, "Today's salah", "Prayer & hadith",
            homeCoverArtwork(1, false)),
        HomeArtworkCard(3, "Qibla", "Direction to Makkah",
            homeCoverArtwork(3, false)),
        HomeArtworkCard(4, "AI suggested", "For this moment",
            homeCoverArtwork(4, false)),
    )
}

@Composable
internal fun HomeArtworkScene(card: HomeArtworkCard, modifier: Modifier = Modifier, copyAlpha: Float = 1f) {
    val dark = LocalDarkTheme.current
    val bitmap = com.starception.submission.core.images.currentHomeArtworkBitmap(card.page)
    Box(modifier) {
        Image(
            painter = if (bitmap != null) remember(bitmap) { androidx.compose.ui.graphics.painter.BitmapPainter(bitmap) }
                else painterResource(card.background),
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
                Text(card.label, color = Color.White, style = HomeTypography.section,
                    fontWeight = FontWeight.SemiBold)
                Text(card.subtitle, color = Color.White.copy(alpha = .85f),
                    style = HomeTypography.caption)
            }
        }
    }
}

/** Secondary artwork cards open the existing insight reader and its real content. */
@Composable
internal fun HomeArtworkRail(
    focusedPage: Int = 0,
    useNightArtwork: Boolean = false,
    bottomClearance: Dp = 0.dp,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    onSelect: (HomeArtworkCard, Rect) -> Unit,
) {
    val prayerNow = HomeArtworkCard(
        0, "Prayer now", "Your next prayer",
        homeCoverArtwork(0, useNightArtwork),
    )
    val cards = homeArtworkCards() + prayerNow
    FluidHomeReveal(
        title = stringResource(R.string.home_explore_title),
        subtitle = stringResource(R.string.home_explore_subtitle),
        modifier = modifier,
        bottomClearance = bottomClearance,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) { enabled, collapse ->
        AnimatedContent(
            targetState = focusedPage.coerceIn(0, 4),
            transitionSpec = {
                (fadeIn(tween(260, delayMillis = 140)) togetherWith fadeOut(tween(140)))
                    .using(SizeTransform { _, _ -> tween(400, easing = FastOutSlowInEasing) })
            },
            label = "homeExploreDestinations",
        ) { page ->
            val feed = homeFeedPage(page)
            val destinations = (feed.railPages + feed.featuredPage).map { destination -> cards.first { it.page == destination } }
            BoxWithConstraints {
                val columns = if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.5f) 1 else 2
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    destinations.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { card ->
                                ArtworkCard(card, Modifier.weight(1f), 128.dp, enabled = enabled && page == focusedPage,
                                    onSelect = { selected, bounds ->
                                        collapse()
                                        onSelect(selected, bounds)
                                    })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeReflectionPrompt(
    focusedPage: Int = 0,
    scrollPosition: Float = focusedPage.toFloat(),
    onDarkCover: Boolean = false,
    exploreExpanded: Boolean = false,
    onExploreToggle: () -> Unit = {},
    exploreContent: @Composable () -> Unit = {},
    generatedInsights: Map<Int, NowNudgeKnowledgeCandidate> = emptyMap(),
    onClick: () -> Unit,
) {
    val ink = if (onDarkCover) Color.White else MaterialTheme.colorScheme.onBackground
    val haptics = LocalHapticFeedback.current
    val arrowRotation by animateFloatAsState(
        targetValue = if (exploreExpanded) 180f else 0f,
        label = "homeExploreDividerArrow",
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        // The arrow is tucked just below the cover's page marks. Give the
        // following reflection the same clear space below the arrow.
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().padding(top = 16.dp)) { HomeSectionDivider(ink) }
            // The panel starts at the divider's center, directly beneath its arrow.
            // As it grows it replaces the line rather than appearing below the reflection.
            Box(Modifier.fillMaxWidth().padding(top = 16.dp)) { exploreContent() }
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onExploreToggle()
                },
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-8).dp)
                    .testTag("homeExploreDividerToggle"),
            ) {
                Box(
                    Modifier.width(40.dp).height(40.dp)
                        .background(Color(0xFF1B1E2A), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(
                            if (exploreExpanded) R.string.home_options_collapse else R.string.home_options_expand,
                        ),
                        tint = Color.White,
                        modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
                    )
                }
            }
        }
        // Mirror the cover headline's swipe-driven fade and small drift. Both
        // text areas change at the same point, with no separate timed animation.
        val position = ((scrollPosition % 5f) + 5f) % 5f
        val current = floor(position).toInt()
        val visiblePages = if (position - current < .001f) {
            listOf(current)
        } else {
            listOf(current, (current + 1) % 5)
        }
        Box(Modifier.fillMaxWidth().clipToBounds()) {
            visiblePages.forEach { page ->
                key(page) {
                    val offset = homeSwipePageOffset(position, page)
                    val feed = homeFeedPage(page)
                    val generatedCandidate = generatedInsights[page]
                    Column(
                        Modifier.fillMaxWidth()
                            .graphicsLayer {
                                translationX = -offset * 16.dp.toPx()
                                translationY = abs(offset) * 8.dp.toPx()
                                alpha = homeCoverCopyOpacity(offset)
                            }
                            .clickable(onClick = onClick),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(feed.eyebrow, style = HomeTypography.eyebrow,
                            color = ink.copy(alpha = .72f))
                        Text(
                            feed.prompt,
                            style = HomeTypography.reflection,
                            color = ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        generatedCandidate?.nudge?.supportingText?.takeIf(String::isNotBlank)?.let { sourceText ->
                            val preview = remember(sourceText) { homeEvidencePreview(sourceText) }
                            Text(preview, style = HomeTypography.body,
                                color = ink.copy(alpha = .82f), maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            generatedCandidate.nudge.sourceLabel?.let { reference ->
                                Text(reference, style = HomeTypography.caption,
                                    color = ink.copy(alpha = .68f), maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A short verified passage belongs on Home; the full source opens on tap. */
internal fun homeEvidencePreview(sourceText: String): String =
    sourceText.replace(Regex("\\s+"), " ").trim().let { text ->
        val quotedTeaching = Regex("(?i)\\bsaid:\\s*").findAll(text).lastOrNull()
            ?.let { text.substring(it.range.last + 1) }
            ?.trim(' ', '\'', '"', '“', '”', '‘', '’')
            ?.takeIf(String::isNotBlank)
            ?: text
        if (quotedTeaching.length > 96) {
            val prefix = quotedTeaching.take(96)
            prefix.substringBeforeLast(' ', missingDelimiterValue = prefix).trimEnd() + "…"
        } else quotedTeaching
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
    enabled: Boolean,
) {
    val shape = RoundedCornerShape(18.dp)
    val ink = androidx.compose.material3.LocalContentColor.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Column(
        modifier = modifier
            .testTag("homeArtworkCard-${card.page}")
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button) { if (bounds.width > 0f) onSelect(card, bounds) }
            .semantics { contentDescription = "${card.label}. ${card.subtitle}" }
            .padding(8.dp),
    ) {
        HomeArtworkScene(card, copyAlpha = 0f,
            modifier = Modifier.fillMaxWidth().height(height)
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .clip(RoundedCornerShape(12.dp)))
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 4.dp)) {
            Text(card.label, color = ink, style = HomeTypography.cardTitle)
            Text(card.subtitle, color = ink.copy(alpha = .78f), style = HomeTypography.caption)
        }
    }
}
