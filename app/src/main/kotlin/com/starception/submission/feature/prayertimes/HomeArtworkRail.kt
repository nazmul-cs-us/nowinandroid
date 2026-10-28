package com.starception.submission.feature.prayertimes

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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.insight_prayer_background
import com.starception.submission.core.images.resources.insight_prayer_foreground
import com.starception.submission.core.images.resources.insight_qibla_background
import com.starception.submission.core.images.resources.insight_qibla_foreground_v2
import com.starception.submission.core.images.resources.insight_suggestion
import com.starception.submission.core.images.resources.insight_quran_background
import com.starception.submission.core.images.resources.insight_quran_foreground_v2
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

private val homeArtworkCards by lazy {
    listOf(
        HomeArtworkCard(2, "Today's reading", "Quran for today",
            Res.drawable.insight_quran_background, Res.drawable.insight_quran_foreground_v2),
        HomeArtworkCard(1, "Today's salah", "Prayer & hadith",
            Res.drawable.insight_prayer_background, Res.drawable.insight_prayer_foreground),
        HomeArtworkCard(3, "Qibla", "Direction to Makkah",
            Res.drawable.insight_qibla_background, Res.drawable.insight_qibla_foreground_v2),
        HomeArtworkCard(4, "AI suggested", "For this moment",
            Res.drawable.insight_suggestion),
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
                modifier = Modifier.matchParentSize().padding(8.dp),
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
internal fun HomeArtworkRail(onSelect: (HomeArtworkCard, Rect) -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Column {
        HomeFeedSectionLabel("EXPLORE YOUR DAY")
        Spacer(Modifier.height(16.dp))
        LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            items(homeArtworkCards.take(3), key = HomeArtworkCard::page) { card ->
                Column(Modifier.width(142.dp)) {
                    ArtworkCard(card, Modifier.fillMaxWidth(), 148.dp, onSelect)
                    Spacer(Modifier.height(10.dp))
                    Text(card.label, color = ink, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text(card.subtitle, color = ink.copy(alpha = .62f),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        HomeFeedSectionLabel("SUGGESTED FOR YOU")
        Spacer(Modifier.height(16.dp))
        ArtworkCard(homeArtworkCards[3], Modifier.fillMaxWidth(), 224.dp, onSelect)
        Spacer(Modifier.height(10.dp))
        Text(homeArtworkCards[3].label, color = ink, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Text(homeArtworkCards[3].subtitle, color = ink.copy(alpha = .62f),
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun HomeReflectionPrompt(onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = .22f)))
        Text("DAILY REFLECTION", style = MaterialTheme.typography.labelSmall,
            color = ink.copy(alpha = .72f))
        Text("What will you take from today’s reading?", style = MaterialTheme.typography.titleLarge,
            color = ink)
    }
}

@Composable
private fun HomeFeedSectionLabel(text: String) {
    val ink = MaterialTheme.colorScheme.onBackground
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = .18f)))
        Spacer(Modifier.height(18.dp))
        Text(text, color = ink.copy(alpha = .82f), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ArtworkCard(
    card: HomeArtworkCard,
    modifier: Modifier,
    height: androidx.compose.ui.unit.Dp,
    onSelect: (HomeArtworkCard, Rect) -> Unit,
) {
    val measuredHeight = if (LocalDensity.current.fontScale > 1.3f) height + 24.dp else height
    val dark = LocalDarkTheme.current
    val shape = RoundedCornerShape(24.dp)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    HomeArtworkScene(
        card = card,
        copyAlpha = 0f,
        modifier = modifier.height(measuredHeight)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .then(if (dark) Modifier else Modifier.shadow(8.dp, shape))
            .clip(shape)
            .then(if (dark) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = .88f), shape))
            .clickable { if (bounds.width > 0f) onSelect(card, bounds) },
    )
}
