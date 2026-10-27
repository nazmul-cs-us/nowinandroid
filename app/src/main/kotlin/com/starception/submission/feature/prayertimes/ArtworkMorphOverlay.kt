package com.starception.submission.feature.prayertimes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

internal data class ArtworkMorphRequest(
    val page: Int,
    val artwork: DrawableResource,
    val from: Rect,
    val to: Rect,
    val token: Int,
)

private object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

/** Keeps the tapped artwork visible while the existing reader opens underneath. */
@Composable
internal fun ArtworkMorphOverlay(request: ArtworkMorphRequest, finishing: Boolean) {
    val density = LocalDensity.current
    val left = remember(request.token) { Animatable(request.from.left) }
    val top = remember(request.token) { Animatable(request.from.top) }
    val width = remember(request.token) { Animatable(request.from.width) }
    val height = remember(request.token) { Animatable(request.from.height) }
    val corner = remember(request.token) { Animatable(with(density) { 24.dp.toPx() }) }
    val opacity by animateFloatAsState(
        targetValue = if (finishing) 0f else 1f,
        animationSpec = tween(150),
        label = "artworkHandoffOpacity",
    )
    LaunchedEffect(request.token) {
        coroutineScope {
            launch { left.animateTo(request.to.left, tween(520, easing = FastOutSlowInEasing)) }
            launch { top.animateTo(request.to.top, tween(520, easing = FastOutSlowInEasing)) }
            launch { width.animateTo(request.to.width, tween(520, easing = FastOutSlowInEasing)) }
            launch { height.animateTo(request.to.height, tween(520, easing = FastOutSlowInEasing)) }
            launch { corner.animateTo(0f, tween(520, easing = FastOutSlowInEasing)) }
        }
    }
    val travel = ((width.value - request.from.width) /
        (request.to.width - request.from.width).coerceAtLeast(1f)).coerceIn(0f, 1f)
    Popup(popupPositionProvider = WindowOrigin,
        properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        Box(Modifier.fillMaxSize().testTag("artworkMorphOverlay")
            .background(Color.Black.copy(alpha = travel * .28f * opacity))) {
            Image(
                painter = painterResource(request.artwork),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset { IntOffset(left.value.roundToInt(), top.value.roundToInt()) }
                    .size(with(density) { width.value.toDp() }, with(density) { height.value.toDp() })
                    .clip(RoundedCornerShape(with(density) { corner.value.toDp() }))
                    .graphicsLayer { alpha = opacity },
            )
        }
    }
}
