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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.starception.submission.R

@Composable
internal fun AppHeaderActions(
    onDarkArtwork: Boolean,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Box(contentAlignment = Alignment.Center) {
        AppActionSurround(
            onDarkArtwork = onDarkArtwork,
            modifier = Modifier.matchParentSize(),
        )
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
    // Engrave the surround into the underlying surface using inset light/shadow.
    // Share the buttons' bounds so the circular lobes stay concentric with them.
    Box(
        modifier.drawWithCache {
            val path = if (paired) pairedActionPath(size.width, size.height) else Path().apply {
                addOval(Rect(0f, 0f, size.width, size.height))
            }
            val surface = Brush.linearGradient(
                colors = listOf(
                    Color.Black.copy(alpha = if (onDarkArtwork) .12f else .09f),
                    Color.Black.copy(alpha = .035f),
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
            // Shift the area outside the contour into the clipped recess. This
            // casts a shadow INSIDE its upper-left edge, rather than a raised
            // button's drop shadow outside the lower-right edge. Layered offsets
            // feather the lip without requiring Android 12 RenderEffect blur.
            val reach = 8.dp.toPx()
            val outside = Path.combine(
                PathOperation.Difference,
                Path().apply { addRect(Rect(-reach, -reach, size.width + reach, size.height + reach)) },
                path,
            )
            val offsets = (1..12).map { it / 12f * 2.5.dp.toPx() }
            val innerShade = Color.Black.copy(alpha = if (onDarkArtwork) .028f else .020f)
            val innerLight = Color.White.copy(alpha = if (onDarkArtwork) .025f else .035f)
            onDrawBehind {
                drawPath(path, surface)
                clipPath(path) {
                    offsets.forEach { offset ->
                        translate(left = offset, top = offset) {
                            drawPath(outside, innerShade)
                        }
                        translate(left = -offset, top = -offset) {
                            drawPath(outside, innerLight)
                        }
                    }
                }
            }
        },
    )
}

/** One continuous contour: no overlapping oval outlines or seam between the actions. */
private fun pairedActionPath(width: Float, height: Float): Path {
    val radius = height * (23f / 48f)
    val left = width * .25f
    val right = width * .75f
    val middle = width / 2f
    val y = height / 2f
    val shoulderX = radius * .5f
    val shoulderY = radius * .866025f
    // Join at 60 degrees with tangent-aligned handles for a smooth, shallow neck.
    val handle = (right - left - radius) / 2f
    val neckControlY = shoulderY - handle * .577350f
    return Path().apply {
        moveTo(left + shoulderX, y + shoulderY)
        arcTo(Rect(left - radius, y - radius, left + radius, y + radius), 60f, 240f, false)
        cubicTo(
            middle, y - neckControlY,
            middle, y - neckControlY,
            right - shoulderX, y - shoulderY,
        )
        arcTo(Rect(right - radius, y - radius, right + radius, y + radius), 240f, 240f, false)
        cubicTo(
            middle, y + neckControlY,
            middle, y + neckControlY,
            left + shoulderX, y + shoulderY,
        )
        close()
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
                    .background(Color(0xFF0B0A12), CircleShape),
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
