package com.starception.submission.feature.prayertimes.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.starception.submission.R
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

// Progress converter from catalog app
private fun convertProgress(progress: Float): Float {
    return (1f - exp(-abs(progress))) * progress.sign
}

/**
 * Control Center style popup - iOS Control Center style with glass tiles
 * Uses live app content as backdrop for glass blur effect
 */
@Composable
fun ControlCenterPrayerPopup(
    prayerName: String,
    prayerTime: String,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val containerColor = Color.Black.copy(0.05f)
    val dimColor = Color.Black.copy(0.4f)

    val itemSpacing = 16f.dp
    val itemSize = 68f.dp
    val itemTwoSpanSize = itemSize * 2 + itemSpacing
    val itemShape = ContinuousRoundedRectangle(itemSize / 2f)

    val innerItemSize = 56f.dp
    val innerItemShape = ContinuousCapsule
    val innerItemIconScale = 0.8f

    val inactiveItemColor = Color.White.copy(0.2f)
    val activeItemColor = accentColor

    val airplaneModeIcon = painterResource(R.drawable.flight_40px)
    val iconColorFilter = ColorFilter.tint(Color.White)

    val animationScope = rememberCoroutineScope()
    val enterProgressAnimation = remember { Animatable(1f) }
    val safeEnterProgressAnimation = remember { Animatable(1f) }
    val progress by remember {
        derivedStateOf {
            val p = enterProgressAnimation.value
            when {
                p < 0f -> convertProgress(p)
                p <= 1f -> p
                else -> 1f + convertProgress(p - 1f)
            }
        }
    }
    val maxDragHeight = 1000f

    val uiSensor = rememberUISensor()
    val glassShape = { itemShape }
    val glassHighlight = {
        Highlight(
            style = HighlightStyle.Default(
                angle = uiSensor.gravityAngle,
                falloff = 2f
            )
        )
    }
    val glassLayer: GraphicsLayerScope.() -> Unit = {
        val p = progress
        val safeProgress = safeEnterProgressAnimation.value
        translationY = -48f.dp.toPx() * (1f - p)
        alpha = EaseIn.transform(safeProgress)
        scaleX /= 1f + 0.1f * (p - 1f).fastCoerceAtLeast(0f)
        scaleY *= 1f + 0.1f * (p - 1f).fastCoerceAtLeast(0f)
    }
    val glassSurface: DrawScope.() -> Unit = { drawRect(containerColor) }
    val glassEffects: BackdropEffectScope.() -> Unit = {
        val p = safeEnterProgressAnimation.value
        vibrancy()
        lens(
            24f.dp.toPx() * p,
            48f.dp.toPx() * p,
            depthEffect = true
        )
    }

    val spacerLayoutModifier = Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val p = progress
        val height = itemSpacing.roundToPx() +
                (32f.dp.toPx() * (p - 1f).fastCoerceAtLeast(0f)).fastRoundToInt()
        layout(constraints.minWidth, height) {
            placeable.place(0, 0)
        }
    }
    val smallSpacerLayoutModifier = Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val p = progress
        val height = itemSpacing.roundToPx() +
                (16f.dp.toPx() * (p - 1f).fastCoerceAtLeast(0f)).fastRoundToInt()
        layout(constraints.minWidth, height) {
            placeable.place(0, 0)
        }
    }

    // Main layout - uses backdrop passed from parent (live app content)
    Box(
        modifier
            .fillMaxSize()
            .draggable(
                rememberDraggableState { delta ->
                    val targetProgress = enterProgressAnimation.value + delta / maxDragHeight
                    animationScope.launch {
                        launch {
                            enterProgressAnimation.snapTo(targetProgress)
                        }
                        launch {
                            safeEnterProgressAnimation.snapTo(targetProgress.fastCoerceIn(0f, 1f))
                        }
                    }
                },
                Orientation.Vertical,
                onDragStopped = { velocity ->
                    val targetProgress = when {
                        velocity < 0f -> 0f  // Any upward swipe dismisses
                        velocity > 0f -> 1f
                        else -> if (enterProgressAnimation.value < 0.5f) 0f else 1f
                    }

                    // Dismiss when target is 0
                    if (targetProgress == 0f) {
                        onDismiss()
                    }

                    animationScope.launch {
                        launch {
                            enterProgressAnimation.animateTo(
                                targetProgress,
                                if (targetProgress > 0.5f) {
                                    spring(0.5f, 300f, 0.5f / maxDragHeight)
                                } else {
                                    spring(1f, 300f, 0.01f)
                                },
                                velocity / maxDragHeight
                            )
                        }
                        launch {
                            safeEnterProgressAnimation.animateTo(
                                targetProgress,
                                spring(1f, 300f, 0.01f)
                            )
                        }
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Control Center glass tiles (using backdrop from live app content)
        // Note: Blur/dim is applied to the background content in PrayerTimesScreen
        Column(
            Modifier
                .padding(top = 80f.dp)
                .systemBarsPadding()
                .displayCutoutPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = glassShape,
                            effects = glassEffects,
                            highlight = glassHighlight,
                            shadow = null,
                            layerBlock = glassLayer,
                            onDrawSurface = glassSurface
                        )
                        .size(itemTwoSpanSize)
                        .padding(itemSpacing)
                ) {
                    Box(
                        Modifier
                            .clip(innerItemShape)
                            .background(inactiveItemColor)
                            .scale(innerItemIconScale)
                            .paint(airplaneModeIcon, colorFilter = iconColorFilter)
                            .size(innerItemSize)
                            .align(Alignment.TopStart)
                    )
                    Box(
                        Modifier
                            .clip(innerItemShape)
                            .background(activeItemColor)
                            .scale(innerItemIconScale)
                            .paint(airplaneModeIcon, colorFilter = iconColorFilter)
                            .size(innerItemSize)
                            .align(Alignment.TopEnd)
                    )
                    Box(
                        Modifier
                            .clip(innerItemShape)
                            .background(activeItemColor)
                            .scale(innerItemIconScale)
                            .paint(airplaneModeIcon, colorFilter = iconColorFilter)
                            .size(innerItemSize)
                            .align(Alignment.BottomStart)
                    )
                }
                Box(
                    Modifier
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = glassShape,
                            effects = glassEffects,
                            highlight = glassHighlight,
                            shadow = null,
                            layerBlock = glassLayer,
                            onDrawSurface = glassSurface
                        )
                        .size(itemTwoSpanSize)
                )
            }

            Spacer(spacerLayoutModifier)

            Row(
                horizontalArrangement = Arrangement.spacedBy(itemSpacing, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = glassShape,
                                    effects = glassEffects,
                                    highlight = glassHighlight,
                                    shadow = null,
                                    layerBlock = glassLayer,
                                    onDrawSurface = glassSurface
                                )
                                .paint(airplaneModeIcon, colorFilter = iconColorFilter)
                                .size(itemSize)
                        )
                        Box(
                            Modifier
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = glassShape,
                                    effects = glassEffects,
                                    highlight = glassHighlight,
                                    shadow = null,
                                    layerBlock = glassLayer,
                                    onDrawSurface = glassSurface
                                )
                                .paint(airplaneModeIcon, colorFilter = iconColorFilter)
                                .size(itemSize)
                        )
                    }

                    Spacer(smallSpacerLayoutModifier)

                    Box(
                        Modifier
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = glassShape,
                                effects = glassEffects,
                                highlight = glassHighlight,
                                shadow = null,
                                layerBlock = glassLayer
                            )
                            .size(itemTwoSpanSize, itemSize)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = glassShape,
                                effects = glassEffects,
                                highlight = glassHighlight,
                                shadow = null,
                                layerBlock = glassLayer,
                                onDrawSurface = glassSurface
                            )
                            .size(itemSize, itemTwoSpanSize)
                    )
                    Box(
                        Modifier
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = glassShape,
                                effects = glassEffects,
                                highlight = glassHighlight,
                                shadow = null,
                                layerBlock = glassLayer,
                                onDrawSurface = glassSurface
                            )
                            .size(itemSize, itemTwoSpanSize)
                    )
                }
            }

            Spacer(spacerLayoutModifier)

            Row(
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = glassShape,
                            effects = glassEffects,
                            highlight = glassHighlight,
                            shadow = null,
                            layerBlock = glassLayer,
                            onDrawSurface = glassSurface
                        )
                        .size(itemTwoSpanSize)
                )

                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = glassShape,
                                    effects = glassEffects,
                                    highlight = glassHighlight,
                                    shadow = null,
                                    layerBlock = glassLayer,
                                    onDrawSurface = glassSurface
                                )
                                .paint(airplaneModeIcon, colorFilter = iconColorFilter)
                                .size(itemSize)
                        )
                        Box(
                            Modifier
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = glassShape,
                                    effects = glassEffects,
                                    highlight = glassHighlight,
                                    shadow = null,
                                    layerBlock = glassLayer,
                                    onDrawSurface = glassSurface
                                )
                                .paint(airplaneModeIcon, colorFilter = iconColorFilter)
                                .size(itemSize)
                        )
                    }

                    Spacer(smallSpacerLayoutModifier)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = glassShape,
                                    effects = glassEffects,
                                    highlight = glassHighlight,
                                    shadow = null,
                                    layerBlock = glassLayer,
                                    onDrawSurface = glassSurface
                                )
                                .paint(airplaneModeIcon, colorFilter = iconColorFilter)
                                .size(itemSize)
                        )
                    }
                }
            }
        }
    }
}
