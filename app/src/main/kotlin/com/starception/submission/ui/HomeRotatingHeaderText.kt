package com.starception.submission.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** A single, fixed-height line shared by the greeting/location and calendar slots. */
@Composable
internal fun HomeRotatingHeaderText(
    primary: String,
    alternate: String,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight = FontWeight.Medium,
) {
    var displayedText by remember(primary) { mutableStateOf(primary) }
    val rotation = remember { Animatable(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(primary, alternate, lifecycleOwner) {
        displayedText = primary
        rotation.snapTo(0f)
        if (alternate.isBlank() || alternate == primary) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // A lifecycle pause can interrupt either half of the flip. Resume
            // with a readable face rather than leaving text edge-on.
            rotation.snapTo(0f)
            while (true) {
                delay(6_000)
                rotation.animateTo(90f, tween(180, easing = FastOutLinearInEasing))
                displayedText = if (displayedText == primary) alternate else primary
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
        },
    )
}
