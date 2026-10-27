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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.insight_prayer_background
import com.starception.submission.core.images.resources.insight_qibla_background
import com.starception.submission.core.images.resources.insight_suggestion
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Secondary artwork cards select the existing insight reader and its real content. */
@Composable
internal fun HomeArtworkRail(onSelect: (Int, DrawableResource, Rect) -> Unit) {
    Column {
        Text("Explore your day", style = MaterialTheme.typography.titleLarge,
            color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            ArtworkCard("Today's salah", "Prayer & hadith", Res.drawable.insight_prayer_background,
                Modifier.weight(1f), onClick = { bounds -> onSelect(1, Res.drawable.insight_prayer_background, bounds) })
            Spacer(Modifier.weight(.06f))
            ArtworkCard("For this moment", "Suggested for you", Res.drawable.insight_suggestion,
                Modifier.weight(1f), onClick = { bounds -> onSelect(4, Res.drawable.insight_suggestion, bounds) })
        }
        Spacer(Modifier.height(12.dp))
        ArtworkCard("Direction to Makkah", "Qibla compass", Res.drawable.insight_qibla_background,
            Modifier.fillMaxWidth(), onClick = { bounds -> onSelect(3, Res.drawable.insight_qibla_background, bounds) })
    }
}

@Composable
private fun ArtworkCard(
    title: String,
    subtitle: String,
    artwork: DrawableResource,
    modifier: Modifier,
    onClick: (Rect) -> Unit,
) {
    val height = if (LocalDensity.current.fontScale > 1.3f) 240.dp else 168.dp
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(modifier.height(height)
        .onGloballyPositioned { bounds = it.boundsInWindow() }
        .clip(RoundedCornerShape(24.dp))
        .clickable { onClick(bounds) }) {
        Image(painterResource(artwork), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = .78f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.White.copy(alpha = .85f),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
