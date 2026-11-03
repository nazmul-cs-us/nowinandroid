package com.starception.submission.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.starception.submission.R

/** A thin circular rim around each disc, joined by a narrow recessed neck. */
private val PairedActionShape = GenericShape { size, _ ->
    val radius = size.height * .4375f
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
internal fun HomeHeaderDepthActions(
    onDarkArtwork: Boolean,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        Modifier
            .background(
                Brush.verticalGradient(listOf(
                    if (onDarkArtwork) Color(0xFF85818F) else Color(0xFFD9D7E0),
                    if (onDarkArtwork) Color(0xFF555361) else Color(0xFFAAA7B3),
                )),
                PairedActionShape,
            )
            .border(.5.dp, Color.White.copy(alpha = .12f), PairedActionShape),
    ) {
        HomeHeaderDepthAction(onSearchClick) {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(R.string.app_top_bar_search_hint),
                tint = Color.White,
                modifier = Modifier.size(19.dp),
            )
        }
        HomeHeaderDepthAction(onSettingsClick) {
            Icon(
                painterResource(R.drawable.ic_app_top_bar_settings_24),
                contentDescription = stringResource(R.string.app_top_bar_settings_icon_description),
                tint = Color.White,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun HomeHeaderDepthAction(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth by animateFloatAsState(if (pressed) 1f else 0f, tween(120), label = "homeActionPress")
    IconButton(onClick = onClick, interactionSource = interaction, modifier = Modifier.size(48.dp)) {
        Box(
            Modifier.size(36.dp)
                .graphicsLayer {
                    scaleX = 1f - depth * .07f
                    scaleY = scaleX
                    translationY = depth * 1.dp.toPx()
                }
                .shadow(2.dp, CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF292735), Color(0xFF101019))), CircleShape)
                .border(.5.dp, Color.White.copy(alpha = .16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}
