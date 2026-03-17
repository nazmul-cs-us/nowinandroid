/*
 * Wobble Pull-to-Refresh Implementation
 * Fitbit-inspired elastic pull-down: content pushes down revealing a flat background
 */
package com.starception.submission.feature.prayertimes.wobble

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.abs

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
 * Fitbit-inspired pull-to-refresh container.
 * When the user pulls down:
 * - Content translates DOWN significantly (primary effect)
 * - A flat muted sage/green-gray background is revealed behind
 * - Content gets rounded corners and becomes a floating card
 * - Horizontal margins appear progressively
 * - "Release to sync" indicator appears above the card
 * - Elastic spring bounce-back when released
 *
 * When syncing (isRefreshing = true):
 * - Content stays pushed down
 * - "Syncing your data" with spinning arc indicator
 * - Sage background remains visible
 */
@Composable
fun WobblePullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    downloadProgress: Float = 0f,
    downloadLabel: String = "",
    content: @Composable (wobbleState: WobbleState) -> Unit
) {
    val isDownloading = downloadProgress > 0f
    val hapticFeedback = LocalHapticFeedback.current

    // Wobble state management
    val maxDragDistance = with(LocalDensity.current) { 600.dp.toPx() }
    var dragDistance by remember { mutableStateOf(0f) }
    var lastHapticDistance by remember { mutableStateOf(0f) }
    var isVerticalDrag by remember { mutableStateOf(false) }

    // Animated drag distance with smooth settle (no bounce, like Fitbit)
    val dragDistanceAnimated by animateFloatAsState(
        targetValue = if (dragDistance > 0f) dragDistance else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    // Refreshing/downloading state: Animatable for instant snap-to when refreshing starts
    // This eliminates the gap where wobbleIntensity would drop between drag release and refresh hold
    val refreshingOffset = remember { Animatable(0f) }
    LaunchedEffect(isRefreshing, isDownloading) {
        if (isRefreshing || isDownloading) {
            // SNAP instantly to held position — no gap, like Fitbit
            refreshingOffset.snapTo(0.35f)
        } else {
            // Smooth settle back when sync completes
            refreshingOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    // Animated download progress for smooth horizontal fill
    val animatedDownloadProgress by animateFloatAsState(
        targetValue = downloadProgress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    // Calculate wobble intensity (0 to 1)
    // When refreshing/downloading, use the refreshing offset; when dragging, use drag distance
    val rawWobbleIntensity = (dragDistanceAnimated / maxDragDistance).coerceIn(0f, 1f)
    val wobbleIntensity = maxOf(rawWobbleIntensity, refreshingOffset.value)

    // --- Fitbit-style visual parameters ---
    // PRIMARY: Large vertical translation (content pushes down)
    val contentOffsetY = (wobbleIntensity * 220f).dp

    // Progressive rounded top corners: 0dp at rest -> 28dp fully pulled
    val cornerRadius = (wobbleIntensity * 28f).dp

    // Fitbit flat muted sage/gray-green background
    val fitbitBgColor = Color(0xFFD2D6CC)

    // Slightly darker sage for the progress fill (horizontal sweep)
    val progressFillColor = Color(0xFFC5CAB9)

    // Indicator text color
    val indicatorColor = Color(0xFF4A5042)

    // Spinning animation for syncing state
    val infiniteTransition = rememberInfiniteTransition(label = "sync_spinner")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )

    // Create wobble state for content
    val wobbleState = WobbleState(
        dragDistance = dragDistanceAnimated,
        isWobbling = wobbleIntensity > 0.01f,
        maxDragDistance = maxDragDistance,
        wobbleIntensity = wobbleIntensity
    )

    // Outer Box: shows the flat sage background when content pushes down
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (wobbleIntensity > 0.01f) fitbitBgColor
                else MaterialTheme.colorScheme.background
            )
            .pointerInput(isRefreshing) {
                detectDragGestures(
                    onDragStart = {
                        isVerticalDrag = false
                        dragDistance = 0f
                        lastHapticDistance = 0f
                    },
                    onDragEnd = {
                        if (isVerticalDrag && dragDistance > 150f && !isRefreshing) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRefresh()
                        }

                        dragDistance = 0f
                        lastHapticDistance = 0f
                        isVerticalDrag = false
                    },
                    onDragCancel = {
                        dragDistance = 0f
                        lastHapticDistance = 0f
                        isVerticalDrag = false
                    }
                ) { _, dragAmount ->
                    if (isRefreshing) {
                        return@detectDragGestures
                    }

                    val deltaX = dragAmount.x
                    val deltaY = dragAmount.y

                    if (!isVerticalDrag && dragDistance < 10f) {
                        val absX = abs(deltaX)
                        val absY = abs(deltaY)

                        if (absY > absX * 2f && absY > 5f) {
                            isVerticalDrag = true
                        } else if (absX > absY && absX > 5f) {
                            return@detectDragGestures
                        }
                    }

                    if (isVerticalDrag) {
                        // Apply resistance: drag gets progressively harder
                        val resistance = 1f - (dragDistance / maxDragDistance * 0.5f).coerceIn(0f, 0.5f)
                        dragDistance += deltaY * resistance

                        if (dragDistance < 0f) {
                            dragDistance = 0f
                            lastHapticDistance = 0f
                            return@detectDragGestures
                        }
                        if (dragDistance >= maxDragDistance) {
                            dragDistance = maxDragDistance
                        }

                        // Progressive haptic feedback every 50 pixels
                        val hapticInterval = 50f
                        if (dragDistance - lastHapticDistance >= hapticInterval) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastHapticDistance = dragDistance - (dragDistance % hapticInterval)
                        }
                    }
                }
            }
    ) {
        // Horizontal progress fill: sweeps left-to-right across the sage background
        if (isDownloading && wobbleIntensity > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedDownloadProgress)
                    .fillMaxHeight()
                    .background(progressFillColor)
                    .align(Alignment.CenterStart)
            )
        }

        // Indicator area: shows pull/release/syncing/downloading status above content card
        if (wobbleIntensity > 0.05f) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(top = (wobbleIntensity * 20f).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isDownloading) {
                    // Download state: spinning arc + label with percentage
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(
                            modifier = Modifier.size(18.dp)
                        ) {
                            val strokeWidth = 2.dp.toPx()
                            drawArc(
                                color = Color(0xFF4A5042),
                                startAngle = spinAngle,
                                sweepAngle = 270f,
                                useCenter = false,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round
                                ),
                                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                                size = androidx.compose.ui.geometry.Size(
                                    size.width - strokeWidth,
                                    size.height - strokeWidth
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        val pct = (animatedDownloadProgress * 100).toInt()
                        val label = if (downloadLabel.isNotEmpty()) downloadLabel else "Downloading"
                        Text(
                            text = "$label $pct%",
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 14.sp,
                            color = indicatorColor
                        )
                    }
                } else if (isRefreshing) {
                    // Syncing state: spinning arc + "Syncing your data"
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(
                            modifier = Modifier.size(18.dp)
                        ) {
                            val strokeWidth = 2.dp.toPx()
                            drawArc(
                                color = Color(0xFF4A5042),
                                startAngle = spinAngle,
                                sweepAngle = 270f,
                                useCenter = false,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round
                                ),
                                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                                size = androidx.compose.ui.geometry.Size(
                                    size.width - strokeWidth,
                                    size.height - strokeWidth
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Syncing your data",
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 14.sp,
                            color = indicatorColor
                        )
                    }
                } else {
                    // Pull/release state: symmetric Row layout matching Fitbit style
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer {
                            alpha = (wobbleIntensity * 2.5f).coerceIn(0f, 1f)
                        }
                    ) {
                        // Rotating refresh arc (same style as syncing spinner)
                        Canvas(
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    rotationZ = wobbleIntensity * 360f
                                }
                        ) {
                            val strokeWidth = 2.dp.toPx()
                            drawArc(
                                color = Color(0xFF4A5042),
                                startAngle = -90f,
                                sweepAngle = 270f,
                                useCenter = false,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round
                                ),
                                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                                size = androidx.compose.ui.geometry.Size(
                                    size.width - strokeWidth,
                                    size.height - strokeWidth
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (wobbleIntensity > 0.4f) "Release to sync" else "Pull to sync",
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 14.sp,
                            color = indicatorColor
                        )
                    }
                }
            }
        }

        // Inner content: pushes down, full width, rounded top corners
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentOffsetY)
                .clip(
                    RoundedCornerShape(
                        topStart = cornerRadius,
                        topEnd = cornerRadius,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp
                    )
                )
                .background(MaterialTheme.colorScheme.background)
        ) {
            content(wobbleState)
        }
    }
}

