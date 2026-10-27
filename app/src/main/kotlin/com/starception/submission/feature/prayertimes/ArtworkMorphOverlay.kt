package com.starception.submission.feature.prayertimes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

internal data class ArtworkMorphRequest(
    val card: HomeArtworkCard,
    val from: Rect,
    val to: Rect,
    val token: Int,
    val sourceScroll: Int,
)

private object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

/** Carries the same artwork, foreground and label between the feed and the real reader. */
@Composable
internal fun ArtworkMorphOverlay(
    request: ArtworkMorphRequest,
    reversing: Boolean,
    onFinished: () -> Unit,
) {
    val density = LocalDensity.current
    val progress = remember(request.token, reversing) { Animatable(if (reversing) 1f else 0f) }
    LaunchedEffect(request.token, reversing) {
        progress.animateTo(
            targetValue = if (reversing) 0f else 1f,
            animationSpec = tween(520, easing = FastOutSlowInEasing),
        )
        onFinished()
    }
    val p = progress.value
    val left = request.from.left + (request.to.left - request.from.left) * p
    val top = request.from.top + (request.to.top - request.from.top) * p
    val width = request.from.width + (request.to.width - request.from.width) * p
    val height = request.from.height + (request.to.height - request.from.height) * p
    val corner = with(density) { (24f * (1f - p)).dp }
    // Fade the travelling scene only once the real destination is ready to take over.
    val sceneAlpha = ((1f - p) / .26f).coerceIn(0f, 1f)
    Popup(
        popupPositionProvider = WindowOrigin,
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        Box(Modifier.fillMaxSize().testTag("artworkMorphOverlay")
            .background(Color.Black.copy(alpha = p * .24f * sceneAlpha))) {
            HomeArtworkScene(
                card = request.card,
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(with(density) { width.toDp() }, with(density) { height.toDp() })
                    .clip(RoundedCornerShape(corner))
                    .graphicsLayer { alpha = sceneAlpha },
                copyAlpha = ((.64f - p) / .24f).coerceIn(0f, 1f),
            )
        }
    }
}
