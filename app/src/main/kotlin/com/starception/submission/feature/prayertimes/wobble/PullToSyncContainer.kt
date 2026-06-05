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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import com.starception.submission.media.MediaAction
import com.starception.submission.media.MediaControllerUiState
import com.starception.submission.media.MediaMiniBar

/**
 * Sync container state forwarded to the content lambda. `pullModifier` carries
 * the [NestedScrollConnection] so the scrollable child can opt in — needed
 * because the page content lives inside an AndroidView+ComposeView island that
 * doesn't propagate nested-scroll events across the View boundary.
 */
class SyncContainerState(
    val dragDistance: Float = 0f,
    val isWobbling: Boolean = false,
    val maxDragDistance: Float = 0f,
    val wobbleIntensity: Float = 0f,
    val pullModifier: Modifier = Modifier,
)

/**
 * Pull-to-sync container with download progress visualization.
 * Uses nestedScroll to properly integrate with scrollable content.
 * When the user pulls down (and content is at the top):
 * - Content translates DOWN with rounded top corners (Fitbit-style)
 * - A two-tone themed background is revealed behind
 * - "Release to sync" indicator appears above the content
 * - Smooth spring settle-back when released
 *
 * When syncing (isRefreshing = true):
 * - Content stays pushed down
 * - "Syncing your data" with spinning arc indicator
 * - Two-tone background sweep from left to right
 *
 * When downloading (downloadProgress > 0):
 * - Shows download progress with percentage
 * - Two-tone sweep visualizes download completion
 */
@Composable
fun PullToSyncContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    downloadProgress: Float = 0f,
    downloadLabel: String = "",
    refreshingHoldFraction: Float = 0.50f,
    downloadingHoldFraction: Float = 0.50f,
    mediaState: MediaControllerUiState = MediaControllerUiState(),
    onMediaAction: (MediaAction) -> Unit = {},
    prayerAlertState: PrayerAlertState = PrayerAlertState(),
    silentModeState: SilentModeState = SilentModeState(),
    content: @Composable (syncState: SyncContainerState) -> Unit
) {
    val isDownloading = downloadProgress > 0f
    var prayerAlertDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(prayerAlertState.displayText) {
        prayerAlertDismissed = false
    }
    val isPrayerAlert = prayerAlertState.isActive && !prayerAlertDismissed
    val isSilentMode = silentModeState.isActive
    val hapticFeedback = LocalHapticFeedback.current

    // Wobble state management
    val maxDragDistance = with(LocalDensity.current) { 600.dp.toPx() }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var lastHapticDistance by remember { mutableFloatStateOf(0f) }

    // NestedScroll connection: properly integrates with scrollable content
    // Only activates pull-to-refresh when content is already scrolled to the top.
    // When enabled=false all gestures pass through untouched (download-only mode).
    val nestedScrollConnection = remember(isRefreshing, enabled) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enabled) return Offset.Zero
                // When user scrolls UP while pull-to-refresh is partially pulled,
                // consume the scroll to reduce drag distance first
                if (dragDistance > 0f && available.y < 0f) {
                    val consumed = available.y
                    dragDistance = (dragDistance + consumed).coerceAtLeast(0f)
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!enabled) return Offset.Zero
                // When content can't scroll up anymore (at top) and user pulls DOWN
                if (available.y > 0f && !isRefreshing && source == NestedScrollSource.UserInput) {
                    val resistance = 1f - (dragDistance / maxDragDistance * 0.5f).coerceIn(0f, 0.5f)
                    dragDistance += available.y * resistance
                    if (dragDistance >= maxDragDistance) {
                        dragDistance = maxDragDistance
                    }

                    // Progressive haptic feedback every 50 pixels
                    val hapticInterval = 50f
                    if (dragDistance - lastHapticDistance >= hapticInterval) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        lastHapticDistance = dragDistance - (dragDistance % hapticInterval)
                    }

                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!enabled) return Velocity.Zero
                // When user lifts finger, check if drag was enough to trigger refresh
                if (dragDistance > 150f && !isRefreshing) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRefresh()
                }
                dragDistance = 0f
                lastHapticDistance = 0f
                return Velocity.Zero
            }
        }
    }

    // Animated drag distance with smooth settle (no bounce, like Fitbit)
    val dragDistanceAnimated by animateFloatAsState(
        targetValue = if (dragDistance > 0f) dragDistance else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    // Refreshing/downloading/media state: Animatable for instant snap-to
    // This eliminates the gap where wobbleIntensity would drop between drag release and hold
    val mediaHoldFraction = refreshingHoldFraction
    val prayerAlertHoldFraction = refreshingHoldFraction
    val refreshingOffset = remember { Animatable(0f) }
    LaunchedEffect(isRefreshing, isDownloading, mediaState.isVisible, isPrayerAlert, isSilentMode) {
        if (isRefreshing || isDownloading) {
            refreshingOffset.snapTo(
                if (isDownloading) downloadingHoldFraction else refreshingHoldFraction,
            )
        } else if (mediaState.isVisible) {
            refreshingOffset.animateTo(
                targetValue = mediaHoldFraction,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        } else if (isPrayerAlert || isSilentMode) {
            refreshingOffset.animateTo(
                targetValue = prayerAlertHoldFraction,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        } else {
            refreshingOffset.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 600)
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

    // Animated sync progress: fills from 0 to 1 over 3 seconds during syncing
    val syncProgress = remember { Animatable(0f) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            syncProgress.snapTo(0f)
            syncProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
            )
        } else {
            syncProgress.snapTo(0f)
        }
    }

    // Calculate wobble intensity (0 to 1)
    // When refreshing/downloading, use the refreshing offset; when dragging, use drag distance
    val rawWobbleIntensity = (dragDistanceAnimated / maxDragDistance).coerceIn(0f, 1f)
    val wobbleIntensity = maxOf(rawWobbleIntensity, refreshingOffset.value)

    // --- Fitbit-style visual parameters ---
    // PRIMARY: Vertical translation (content pushes down)
    // Download uses the same sheet geometry as sync so the banner-to-title spacing
    // matches the pull-to-sync state exactly.
    val contentOffsetY = (wobbleIntensity * 220f).dp

    // Fitbit-style rounded top corners on content card when pushed down
    val cornerRadius = (wobbleIntensity * 28f).dp.coerceAtMost(28.dp)
    val horizontalMargin = 0.dp

    // Two-tone pull-to-refresh background using theme colors (Fitbit-style)
    val fitbitBgColor = MaterialTheme.colorScheme.primaryContainer
    val fitbitBgColorLight = MaterialTheme.colorScheme.tertiaryContainer

    // Indicator text color from theme
    val indicatorColor = MaterialTheme.colorScheme.onPrimaryContainer

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

    // Create sync container state for content
    val syncState = SyncContainerState(
        dragDistance = dragDistanceAnimated,
        isWobbling = wobbleIntensity > 0.01f,
        maxDragDistance = maxDragDistance,
        wobbleIntensity = wobbleIntensity,
        pullModifier = Modifier.nestedScroll(nestedScrollConnection),
    )

    // Outer Box: sage only during active pull; during sync the sweep reveals it progressively
    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .background(
                when {
                    rawWobbleIntensity > 0.01f -> fitbitBgColorLight
                    isRefreshing || isDownloading -> fitbitBgColorLight
                    mediaState.isVisible -> fitbitBgColorLight
                    isPrayerAlert -> fitbitBgColorLight
                    isSilentMode -> fitbitBgColorLight
                    else -> MaterialTheme.colorScheme.background
                }
            )
    ) {
        // Horizontal progress fill: sweeps sage color left-to-right (background hidden until sweep covers it)
        // Shows during syncing, downloading, or media playback
        val showSweep = (isRefreshing || isDownloading || mediaState.isVisible || isPrayerAlert) && wobbleIntensity > 0.01f
        if (showSweep) {
            val fillProgress = when {
                isDownloading -> animatedDownloadProgress
                isRefreshing -> syncProgress.value
                mediaState.isVisible && mediaState.playback.duration > 0 -> {
                    (mediaState.playback.currentPosition.toFloat() / mediaState.playback.duration.toFloat())
                        .coerceIn(0f, 1f)
                }
                isPrayerAlert -> prayerAlertState.fillProgress
                else -> 0f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillProgress.coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(fitbitBgColor)
                    .align(Alignment.CenterStart)
            )
        }

        // Inner content: pushes down flat (like Fitbit)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentOffsetY, start = horizontalMargin, end = horizontalMargin)
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
            // Main screen content
            content(syncState)
        }

        // Indicator / media area: render above the content sheet in the revealed sage background.
        // Media controls, download text, and sync indicators all render here.
        // For media, fill the full height of the revealed area and center content vertically.
        if (wobbleIntensity > 0.05f) {
            if (mediaState.isVisible) {
                // --- Media Mini-Bar fills the sage area ---
                // Pull-up-to-dismiss is scoped to the title column inside MediaMiniBar
                // (via titleDragModifier) so playback button taps are not swallowed.
                Box(
                    modifier = Modifier
                        .zIndex(1f)
                        .fillMaxWidth()
                        .height(contentOffsetY)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
                    contentAlignment = Alignment.Center,
                ) {
                    MediaMiniBar(
                        state = mediaState,
                        onAction = onMediaAction,
                        modifier = Modifier.padding(bottom = 6.dp),
                        titleDragModifier = Modifier.pointerInput(Unit) {
                            var totalDrag = 0f
                            detectVerticalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    totalDrag += dragAmount
                                },
                                onDragEnd = {
                                    if (totalDrag < -80f) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onMediaAction(MediaAction.Dismiss)
                                    }
                                },
                            )
                        },
                    )
                }
            } else if (isPrayerAlert) {
                Box(
                    modifier = Modifier
                        .zIndex(1f)
                        .fillMaxWidth()
                        .height(contentOffsetY)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        .pointerInput(Unit) {
                            var totalDrag = 0f
                            detectVerticalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    totalDrag += dragAmount
                                },
                                onDragEnd = {
                                    if (totalDrag < -80f) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        prayerAlertDismissed = true
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(
                            modifier = Modifier.size(18.dp)
                        ) {
                            val strokeWidth = 2.dp.toPx()
                            drawArc(
                                color = indicatorColor,
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
                            text = prayerAlertState.displayText,
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 14.sp,
                            color = indicatorColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else if (isSilentMode) {
                Box(
                    modifier = Modifier
                        .zIndex(1f)
                        .fillMaxWidth()
                        .height(contentOffsetY)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DoNotDisturbOn,
                            contentDescription = null,
                            tint = indicatorColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = silentModeState.displayText,
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 14.sp,
                            color = indicatorColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else if (isRefreshing || isDownloading || rawWobbleIntensity > 0.01f) {
            // Only show sync/download indicators when actively dragging or syncing/downloading.
            // Skip during settle-back animation after media dismiss (rawWobbleIntensity == 0).
            Column(
                modifier = Modifier
                    .zIndex(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(top = (wobbleIntensity * 8f).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isDownloading) {
                    val pct = (animatedDownloadProgress * 100).toInt()
                    val label = if (downloadLabel.isNotEmpty()) downloadLabel else "Downloading"
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(
                            modifier = Modifier.size(18.dp)
                        ) {
                            val strokeWidth = 2.dp.toPx()
                            drawArc(
                                color = indicatorColor,
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
                            text = "$label  $pct%",
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 14.sp,
                            color = indicatorColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else if (isRefreshing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(
                            modifier = Modifier.size(18.dp)
                        ) {
                            val strokeWidth = 2.dp.toPx()
                            drawArc(
                                color = indicatorColor,
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer {
                            alpha = (wobbleIntensity * 2.5f).coerceIn(0f, 1f)
                        }
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    rotationZ = wobbleIntensity * 360f
                                }
                        ) {
                            val strokeWidth = 2.dp.toPx()
                            drawArc(
                                color = indicatorColor,
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
            } // close else (non-media indicator Column)
        } // close if (wobbleIntensity > 0.05f)
    }
}
