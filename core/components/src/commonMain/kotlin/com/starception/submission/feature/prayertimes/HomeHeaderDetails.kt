package com.starception.submission.feature.prayertimes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

enum class HomeHeaderDetailKind { Calendar, Location, Weather }

data class HomeHeaderDetail(val text: String, val kind: HomeHeaderDetailKind)

/** Clean, platform-native sans-serif for the compact artwork header. */
object HomeHeaderTypography {
    val greeting = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 22.sp,
        lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.35).sp)
    val detail = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp,
        lineHeight = 19.sp, fontWeight = FontWeight.Normal, letterSpacing = .1.sp)
}

/** One fixed-height metadata line; the greeting above it stays stationary. */
@Composable
fun HomeRotatingHeaderText(
    texts: List<HomeHeaderDetail>,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    val entries = remember(texts) { texts.filter { it.text.isNotBlank() }.distinct() }
    var displayedText by remember(entries) { mutableStateOf(entries.firstOrNull()) }
    val rotation = remember { Animatable(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(entries, lifecycleOwner) {
        var index = 0
        displayedText = entries.firstOrNull()
        rotation.snapTo(0f)
        if (entries.size < 2) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // A lifecycle pause can interrupt either half of the flip. Resume
            // with a readable face rather than leaving text edge-on.
            rotation.snapTo(0f)
            while (true) {
                // Give a long neighborhood/address time to scroll before flipping.
                delay(maxOf(6_000L, (displayedText?.text?.length ?: 0) * 220L + 2_000L))
                rotation.animateTo(90f, tween(180, easing = FastOutLinearInEasing))
                index = (index + 1) % entries.size
                displayedText = entries[index]
                rotation.snapTo(-90f)
                rotation.animateTo(0f, tween(240, easing = LinearOutSlowInEasing))
            }
        }
    }
    val detail = displayedText ?: return
    Row(
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            rotationX = rotation.value
            cameraDistance = 12f * density
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = when (detail.kind) {
                HomeHeaderDetailKind.Calendar -> Icons.Outlined.CalendarMonth
                HomeHeaderDetailKind.Location -> Icons.Outlined.LocationOn
                HomeHeaderDetailKind.Weather -> Icons.Outlined.Cloud
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = detail.text,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE,
                initialDelayMillis = 1_000, velocity = 24.dp),
        )
    }
}
