package com.starception.submission.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/** Keep a landscape photo visible in a portrait Home cover without stretching or zooming it. */
@Composable
fun ArtworkBackdropImage(
    painter: Painter,
    modifier: Modifier = Modifier,
    overscan: Float = 1f,
    horizontalTravel: Float = 0f,
) {
    BoxWithConstraints(modifier.clipToBounds().background(MaterialTheme.colorScheme.surface)) {
        val source = painter.intrinsicSize
        val fitLandscape = source.width.isFinite() && source.height.isFinite() &&
            source.width > source.height && maxHeight > maxWidth
        if (fitLandscape) {
            Image(painter, null, Modifier.fillMaxSize().blur(28.dp),
                contentScale = ContentScale.Crop, alpha = .4f)
            Image(painter, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, alignment = Alignment.Center)
        } else {
            Image(painter, null, Modifier.fillMaxSize().graphicsLayer {
                scaleX = overscan
                scaleY = overscan
                translationX = size.width * horizontalTravel
            }, contentScale = ContentScale.Crop, alignment = Alignment.Center)
        }
    }
}
