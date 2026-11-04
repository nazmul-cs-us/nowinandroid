package com.starception.submission.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
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

/** One fixed-height metadata line; the greeting above it stays stationary. */
@Composable
internal fun HomeRotatingHeaderText(
    texts: List<String>,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight = FontWeight.Medium,
) {
    val entries = remember(texts) { texts.filter(String::isNotBlank).distinct() }
    var displayedText by remember(entries) { mutableStateOf(entries.firstOrNull().orEmpty()) }
    val rotation = remember { Animatable(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(entries, lifecycleOwner) {
        var index = 0
        displayedText = entries.firstOrNull().orEmpty()
        rotation.snapTo(0f)
        if (entries.size < 2) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // A lifecycle pause can interrupt either half of the flip. Resume
            // with a readable face rather than leaving text edge-on.
            rotation.snapTo(0f)
            while (true) {
                // Give a long neighborhood/address time to scroll before flipping.
                delay(maxOf(6_000L, displayedText.length * 220L + 2_000L))
                rotation.animateTo(90f, tween(180, easing = FastOutLinearInEasing))
                index = (index + 1) % entries.size
                displayedText = entries[index]
                rotation.snapTo(-90f)
                rotation.animateTo(0f, tween(240, easing = LinearOutSlowInEasing))
            }
        }
    }
    Text(
        text = displayedText,
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.graphicsLayer {
            rotationX = rotation.value
            cameraDistance = 12f * density
        }.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1_000, velocity = 24.dp),
    )
}
