package com.starception.submission.feature.prayertimes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
        HomeArtworkCard(1, "Today's salah", "Prayer & hadith",
            Res.drawable.insight_prayer_background, Res.drawable.insight_prayer_foreground),
        HomeArtworkCard(4, "AI suggested", "For this moment",
            Res.drawable.insight_suggestion),
        HomeArtworkCard(3, "Qibla", "Direction to Makkah",
            Res.drawable.insight_qibla_background, Res.drawable.insight_qibla_foreground_v2),
    )
}

@Composable
internal fun HomeArtworkScene(card: HomeArtworkCard, modifier: Modifier = Modifier, copyAlpha: Float = 1f) {
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
            listOf(Color.Black.copy(alpha = .12f), Color.Transparent, Color.Black.copy(alpha = .78f)),
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
    Column {
        Text("Explore your day", style = MaterialTheme.typography.titleLarge,
            color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            ArtworkCard(homeArtworkCards[0], Modifier.weight(1f), onSelect)
            Spacer(Modifier.weight(.06f))
            ArtworkCard(homeArtworkCards[1], Modifier.weight(1f), onSelect)
        }
        Spacer(Modifier.height(12.dp))
        ArtworkCard(homeArtworkCards[2], Modifier.fillMaxWidth(), onSelect)
    }
}

@Composable
private fun ArtworkCard(
    card: HomeArtworkCard,
    modifier: Modifier,
    onSelect: (HomeArtworkCard, Rect) -> Unit,
) {
    val height = if (LocalDensity.current.fontScale > 1.3f) 240.dp else 168.dp
    var bounds by remember { mutableStateOf(Rect.Zero) }
    HomeArtworkScene(
        card = card,
        modifier = modifier.height(height)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .clip(RoundedCornerShape(24.dp))
            .clickable { if (bounds.width > 0f) onSelect(card, bounds) },
    )
}
