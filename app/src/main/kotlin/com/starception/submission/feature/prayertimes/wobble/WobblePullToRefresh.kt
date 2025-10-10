/*
 * Wobble Pull-to-Refresh Implementation
 * Elegant elastic animation inspired by Android Platform Samples
 */
package com.starception.submission.feature.prayertimes.wobble

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Data class to hold wobble state and configuration
 */
data class WobbleState(
    val dragDistance: Float = 0f,
    val isWobbling: Boolean = false,
    val maxDragDistance: Float = 0f,
    val wobbleIntensity: Float = 0f
)

/**
 * Wobble pull-to-refresh container that applies elastic transformations to content
 */
@Composable
fun WobblePullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (wobbleState: WobbleState) -> Unit
) {
    // Wobble state management
    val maxDragDistance = with(LocalDensity.current) { 400.dp.toPx() }
    var dragDistance by remember { mutableStateOf(0f) }
    var isWobbling by remember { mutableStateOf(false) }
    
    // Animated drag distance with spring physics
    val dragDistanceAnimated by animateFloatAsState(
        targetValue = if (dragDistance > 0f) dragDistance else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        finishedListener = {
            if (isWobbling) {
                isWobbling = false
                // Trigger refresh when animation completes
                if (dragDistance > 150f) {
                    onRefresh()
                }
            }
        }
    )
    
    // Calculate wobble intensity
    val wobbleIntensity = (dragDistanceAnimated / maxDragDistance).coerceIn(0f, 1f)
    
    // Create wobble state for content
    val wobbleState = WobbleState(
        dragDistance = dragDistanceAnimated,
        isWobbling = isWobbling,
        maxDragDistance = maxDragDistance,
        wobbleIntensity = wobbleIntensity
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .draggable(
                onDragStopped = {
                    isWobbling = true
                    dragDistance = 0f
                },
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    isWobbling = false
                    dragDistance += delta
                    // Only allow drag down
                    if (dragDistance < 0f) {
                        dragDistance = 0f
                        return@rememberDraggableState
                    }
                    if (dragDistance >= maxDragDistance) {
                        dragDistance = maxDragDistance
                    }
                }
            )
    ) {
        content(wobbleState)
    }
}

/**
 * Apply wobble transformations to a composable
 */
fun Modifier.wobbleTransform(
    wobbleIntensity: Float,
    offsetMultiplier: Float = 1f,
    scaleMultiplier: Float = 1f
): Modifier = this
    .offset(y = (wobbleIntensity * 15f * offsetMultiplier).dp)
    .graphicsLayer {
        scaleY = 1f + (wobbleIntensity * 0.05f * scaleMultiplier)
        scaleX = 1f + (wobbleIntensity * 0.02f * scaleMultiplier)
    }