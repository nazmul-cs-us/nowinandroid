package com.starception.submission.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.starception.submission.R

/** The reference's soft paired surround, with two separate matte-black faces. */
private val PairedActionShape = GenericShape { size, _ ->
    val radius = size.height * (22f / 48f)
    val left = size.width * .25f
    val right = size.width * .75f
    val middle = size.width / 2f
    val y = size.height / 2f
    val diagonal = radius * .707107f
    moveTo(left + diagonal, y + diagonal)
    arcTo(Rect(left - radius, y - radius, left + radius, y + radius), 45f, 270f, false)
    cubicTo(
        middle - radius * .2f, y - radius * .16f,
        middle + radius * .2f, y - radius * .16f, right - diagonal, y - diagonal,
    )
    arcTo(Rect(right - radius, y - radius, right + radius, y + radius), 225f, 270f, false)
    cubicTo(
        middle + radius * .2f, y + radius * .16f,
        middle - radius * .2f, y + radius * .16f, left + diagonal, y + diagonal,
    )
    close()
}

@Composable
internal fun AppHeaderActions(
    onDarkArtwork: Boolean,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Box {
        AppActionSurround(onDarkArtwork, Modifier.matchParentSize())
        Row {
            AppActionButton(onSearchClick, showSurround = false) {
                HeaderActionIcon(
                    painter = painterResource(R.drawable.ic_home_search_fine),
                    contentDescription = stringResource(R.string.app_top_bar_search_hint),
                )
            }
            AppActionButton(onSettingsClick, showSurround = false) {
                HeaderActionIcon(
                    painter = painterResource(R.drawable.ic_home_settings_fine),
                    contentDescription = stringResource(R.string.app_top_bar_settings_icon_description),
                )
            }
        }
    }
}

@Composable
internal fun AppActionSurround(
    onDarkArtwork: Boolean,
    modifier: Modifier = Modifier,
    paired: Boolean = true,
) {
    Box(modifier.blur(1.6.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
        .sculptedActionSurround(onDarkArtwork, if (paired) PairedActionShape else CircleShape))
}

private fun Modifier.sculptedActionSurround(onDarkArtwork: Boolean, shape: Shape) = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val end = Offset(size.width, size.height)
    val upperLight = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (onDarkArtwork) .012f else .015f),
        .55f to Color.White.copy(alpha = .003f),
        1f to Color.Transparent,
        start = Offset.Zero,
        end = end,
    )
    val lowerShade = Brush.linearGradient(
        0f to Color.Transparent,
        .45f to Color.Black.copy(alpha = .003f),
        1f to Color.Black.copy(alpha = .016f),
        start = Offset.Zero,
        end = end,
    )
    val surface = Brush.verticalGradient(listOf(Color.White.copy(alpha = .035f), Color.Black.copy(alpha = .045f)))
    // Closely spaced translucent bands give a feathered edge on older Android
    // versions too. There is deliberately no hard outline around this surround.
    val featheredStrokes = (24 downTo 1).map { Stroke(it * .3.dp.toPx()) }
    val lightOffset = .7.dp.toPx()
    val shadeOffset = 1.4.dp.toPx()
    onDrawBehind {
        translate(left = shadeOffset, top = shadeOffset) {
            featheredStrokes.forEach { drawOutline(outline, lowerShade, style = it) }
        }
        translate(left = -lightOffset, top = -lightOffset) {
            featheredStrokes.forEach { drawOutline(outline, upperLight, style = it) }
        }
        drawOutline(outline, surface)
    }
}

@Composable
private fun HeaderActionIcon(painter: Painter, contentDescription: String) {
    Icon(painter, contentDescription, tint = Color(0xFFF2F2F7), modifier = Modifier.size(18.dp))
}

@Composable
internal fun AppActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showSurround: Boolean = true,
    onDarkArtwork: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth by animateFloatAsState(if (pressed) 1f else 0f, tween(120), label = "headerActionPress")
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (showSurround) {
            AppActionSurround(onDarkArtwork, Modifier.size(44.dp), paired = false)
        }
        IconButton(onClick = onClick, interactionSource = interaction, modifier = Modifier.size(48.dp)) {
            Box(
                Modifier.size(36.dp)
                    .graphicsLayer {
                        scaleX = 1f - depth * .035f
                        scaleY = scaleX
                        translationY = depth * .7.dp.toPx()
                    }
                    .shadow(2.dp, CircleShape)
                    .background(Color(0xFF101017), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.graphicsLayer { alpha = 1f - depth * .16f },
                    contentAlignment = Alignment.Center,
                ) { content() }
            }
        }
    }
}
