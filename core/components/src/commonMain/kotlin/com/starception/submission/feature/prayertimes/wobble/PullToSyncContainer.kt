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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay

/**
 * The height the bar row reserves. This was MiniBarRowHeight in the app module;
 * it is a plain dimension, so it comes along rather than dragging the mini-bar
 * shell across with it.
 */
private val MiniBarRowHeight = 30.dp

/**
 * A row the container can host inside its strip.
 *
 * The container reserves the space, animates the reveal and sweeps the progress
 * line. It does not know whether the row is media playback or a Mushaf page —
 * those features exist only on Android today, and this container is used on both
 * platforms, so the feature passes in what to draw and how far along it is.
 */
data class SyncBarRow(
    val isVisible: Boolean,
    /** 0..1 along the strip, or 0 for a row with no notion of progress. */
    val progress: Float = 0f,
    val content: @Composable (statusText: String?) -> Unit,
)


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
    val heldContentInsetTop: Dp = 0.dp,
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
    syncResultText: String? = null,
    onSyncResultClick: (() -> Unit)? = null,
    onSyncResultDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
    idleContainerColor: Color = Color.Unspecified,
    idleContainerBrush: Brush? = null,
    enabled: Boolean = true,
    downloadProgress: Float = 0f,
    downloadLabel: String = "",
    isTtsPreparing: Boolean = false,
    /** Fills the bar row when media is playing; supplied by the app. */
    mediaBar: SyncBarRow? = null,
    prayerAlertState: PrayerAlertState = PrayerAlertState(),
    forbiddenPrayerTimeState: ForbiddenPrayerTimeState = ForbiddenPrayerTimeState(),
    weatherWarningText: String? = null,
    onWeatherWarningDismiss: () -> Unit = {},
    silentModeState: SilentModeState = SilentModeState(),
    islamicEventState: IslamicEventState = IslamicEventState(),
    onIslamicEventClick: (IslamicEventState) -> Unit = {},
    /** Fills the bar row while a Mushaf page is open. */
    mushafBar: SyncBarRow? = null,
    content: @Composable (syncState: SyncContainerState) -> Unit
) {
    val resolvedIdleContainerColor = if (idleContainerColor == Color.Unspecified) {
        MaterialTheme.colorScheme.background
    } else {
        idleContainerColor
    }
    val idleBackgroundModifier = if (idleContainerBrush != null) {
        Modifier.background(idleContainerBrush)
    } else {
        Modifier.background(resolvedIdleContainerColor)
    }
    val isDownloading = downloadProgress > 0f
    var prayerAlertDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(prayerAlertState.displayText) {
        prayerAlertDismissed = false
    }
    var islamicEventDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(islamicEventState.eventKey) {
        islamicEventDismissed = false
    }
    val isPrayerAlert = prayerAlertState.isActive && !prayerAlertDismissed
    val isForbiddenPrayerTime = forbiddenPrayerTimeState.isActive &&
        forbiddenPrayerTimeState.displayText.isNotBlank()
    var weatherWarningDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(weatherWarningText) {
        weatherWarningDismissed = false
    }
    val isWeatherWarning = !weatherWarningText.isNullOrBlank() && !weatherWarningDismissed
    val isSilentMode = silentModeState.isActive
    val isIslamicEvent = islamicEventState.isActive && !islamicEventDismissed
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

    // ── One standard bar height, for every kind of information ──────────────
    // Every state the strip can be in — media, Mushaf, prayer alert, Islamic
    // event, silent mode, sync, download, TTS — renders inside the same single
    // [MiniBarRowHeight] row and therefore holds the page down by exactly the
    // same amount. Previously each state computed its own height (a sync banner
    // held 110dp while a prayer alert held ~60dp, and media grew by another row
    // when a status stacked on it), so the strip visibly jumped as state changed.
    val windowSize = LocalWindowInfo.current.containerSize
    val baseMaxRevealDp =
        // Window width against height rather than Configuration.orientation,
        // which is Android-only. The same question, asked portably.
        if (windowSize.width > windowSize.height) 130f else 220f
    val bannerDensity = LocalDensity.current
    val bannerTopInsetDp = with(bannerDensity) {
        WindowInsets.safeDrawing.getTop(this).toDp()
    }
    // Sit inside the otherwise unused lower portion of the cutout-safe area
    // while retaining clearance from the camera/status region.
    //
    // The 20dp reclaim assumes the top inset is plain status bar with room to
    // spare. Where the inset exists *because* of a camera cutout that assumption
    // is false and the row draws under the punch-hole, so never reclaim past the
    // cutout itself. Devices without a cutout report 0 here and are unaffected.
    val cutoutTopDp = with(bannerDensity) {
        WindowInsets.displayCutout.getTop(this).toDp()
    }
    val barTopInsetPadding = (bannerTopInsetDp - 20.dp)
        .coerceAtLeast(cutoutTopDp)
        .coerceAtLeast(0.dp)
    val barBottomPadding = 4.dp
    // Fixed row, but still allowed to grow with the user's font scale so long
    // labels never clip.
    val barRowDp = maxOf(MiniBarRowHeight, with(bannerDensity) { 17.sp.toDp() } + 13.dp)
    val standardBarHeightDp = barTopInsetPadding + barRowDp + barBottomPadding
    val isMushafActive = mushafBar?.isVisible == true

    // Nothing here branches on *which* information is live: the row is either up
    // at its standard height or fully closed.
    val hasSyncResult = !syncResultText.isNullOrBlank()
    val hasStatus = isPrayerAlert || isForbiddenPrayerTime || isWeatherWarning || isIslamicEvent ||
        isSilentMode || hasSyncResult ||
        isRefreshing || isDownloading || isTtsPreparing
    val targetHoldHeightDp = if (mediaBar?.isVisible == true || isMushafActive || hasStatus) {
        standardBarHeightDp
    } else {
        0.dp
    }
    // Normally the pull gesture can reveal more than the resting bar. At large
    // font scales or in landscape, let the bar expand past the cap rather than clip.
    val maxRevealDpForBanners = maxOf(baseMaxRevealDp, targetHoldHeightDp.value)
    val targetHoldFraction =
        (targetHoldHeightDp.value / maxRevealDpForBanners).coerceIn(0f, 1f)

    // Keep the page, banner, and responsive Insights sizing on one continuous
    // progress value. Opening used to snap for sync/download/TTS, while closing
    // used a spring; that discontinuity made the large cards visibly jump. Key
    // this only to the requested height as well, so swapping one status for
    // another at the same height does not cancel and restart the transition.
    val refreshingOffset = remember { Animatable(0f) }
    LaunchedEffect(targetHoldFraction) {
        refreshingOffset.animateTo(
            targetValue = targetHoldFraction,
            animationSpec = spring(
                // A nearly critical spring keeps its velocity when the banner
                // changes direction, but settles cleanly without a visible bounce.
                dampingRatio = 0.92f,
                stiffness = 260f,
            ),
        )
    }

    // Animated download progress for smooth horizontal fill
    val animatedDownloadProgress by animateFloatAsState(
        targetValue = downloadProgress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )
    val animatedMushafPageProgress by animateFloatAsState(
        targetValue = if (mushafBar != null) {
            mushafBar.progress.coerceIn(0f, 1f)
        } else {
            0f
        },
        animationSpec = tween(durationMillis = 420),
        label = "Mushaf page sweep",
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
    // PRIMARY: Vertical translation (content pushes down). How far a finger drag
    // can reveal — the resting bar is always [standardBarHeightDp] regardless.
    val maxRevealDp = maxRevealDpForBanners
    val contentOffsetY = (wobbleIntensity * maxRevealDp).dp

    // Fitbit-style rounded top corners on content card when pushed down
    val cornerRadius = (wobbleIntensity * 40f).dp.coerceAtMost(36.dp)
    // A spring can spend a few frames just above zero while a banner opens or
    // closes. Painting the full accent color during those frames produces a
    // stray colored line at the very top before any banner content is visible.
    // Blend the reveal in only as it becomes substantial, keeping idle frames
    // visually continuous with the destination canvas.
    val revealColorAlpha = ((wobbleIntensity - 0.03f) / 0.12f).coerceIn(0f, 1f)
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

    // Every status line that is live right now, most urgent first. They all
    // share the single row: when a mini bar owns the row they ride along as its
    // subtitle, otherwise the top one owns the row directly. With more than one
    // live, the row cycles between them — that is how the bar carries several
    // pieces of information without ever growing a second row.
    val statuses = buildList {
        if (isDownloading) {
            val pct = (animatedDownloadProgress * 100).toInt()
            val label = downloadLabel.ifEmpty { "Downloading" }
            add(SyncBarStatus("download", "$label  $pct%", SyncBarIcon.Spinner))
        }
        if (isRefreshing) {
            add(SyncBarStatus("sync", "Syncing your data", SyncBarIcon.Spinner))
        }
        if (hasSyncResult) {
            add(
                SyncBarStatus(
                    key = "sync-result:$syncResultText",
                    text = syncResultText.orEmpty(),
                    icon = if (onSyncResultClick != null) SyncBarIcon.Retry else SyncBarIcon.Sparkle,
                    onClick = onSyncResultClick,
                    onDismiss = onSyncResultDismiss,
                ),
            )
        }
        // The media bar renders this as its own subtitle, so it would be a
        // duplicate here.
        if (isTtsPreparing && mediaBar?.isVisible != true) {
            add(SyncBarStatus("tts", "Preparing audio…", SyncBarIcon.Spinner))
        }
        if (isForbiddenPrayerTime) {
            add(
                SyncBarStatus(
                    key = "forbidden-prayer:${forbiddenPrayerTimeState.periodKey}",
                    text = forbiddenPrayerTimeState.displayText,
                    icon = SyncBarIcon.WeatherWarning,
                ),
            )
        }
        if (isPrayerAlert) {
            add(
                SyncBarStatus(
                    key = "prayer",
                    text = prayerAlertState.displayText,
                    icon = SyncBarIcon.Spinner,
                    onDismiss = { prayerAlertDismissed = true },
                ),
            )
        }
        if (isWeatherWarning) {
            add(
                SyncBarStatus(
                    key = "weather:$weatherWarningText",
                    text = weatherWarningText.orEmpty(),
                    icon = SyncBarIcon.WeatherWarning,
                    onDismiss = {
                        weatherWarningDismissed = true
                        onWeatherWarningDismiss()
                    },
                ),
            )
        }
        if (isIslamicEvent) {
            add(
                SyncBarStatus(
                    key = "event:${islamicEventState.eventKey}",
                    text = islamicEventState.title,
                    icon = SyncBarIcon.Sparkle,
                    onClick = { onIslamicEventClick(islamicEventState) },
                    onDismiss = { islamicEventDismissed = true },
                ),
            )
        }
        if (isSilentMode) {
            add(SyncBarStatus("silent", silentModeState.displayText, SyncBarIcon.DoNotDisturb))
        }
    }
    val activeStatusIndex = rememberCyclingStatusIndex(statuses)
    val activeStatus = statuses.getOrNull(activeStatusIndex)

    val heldContentInsetTop = (refreshingOffset.value * maxRevealDpForBanners).dp

    // Create sync container state for content
    val syncState = SyncContainerState(
        dragDistance = dragDistanceAnimated,
        isWobbling = wobbleIntensity > 0.01f,
        maxDragDistance = maxDragDistance,
        wobbleIntensity = wobbleIntensity,
        heldContentInsetTop = heldContentInsetTop,
        pullModifier = Modifier.nestedScroll(nestedScrollConnection),
    )

    // Keep the container itself on the destination's normal canvas. The active
    // pull color belongs only to the strip exposed above the displaced content.
    // Painting the whole container with the pull color leaks through destinations
    // such as Home that intentionally use a transparent content surface, making
    // their entire background change while the horizontal sweep is visible.
    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .then(idleBackgroundModifier)
    ) {
        if (revealColorAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Extend beneath the clipped sheet just far enough for its
                    // rounded top corners to expose the pull color. The opaque
                    // destination canvas still prevents this from tinting the body.
                    .height((contentOffsetY + cornerRadius).coerceAtLeast(1.dp))
                    .graphicsLayer { alpha = revealColorAlpha }
                    .background(fitbitBgColorLight)
                    .align(Alignment.TopStart),
            )
        }

        // Horizontal progress fill: sweeps sage color left-to-right (background hidden until sweep covers it)
        // Mushaf page position uses this same full-width treatment as playback
        // progress; the thin bar remains as a precise secondary indicator.
        val showSweep = (
            isRefreshing ||
                isDownloading ||
                mediaBar?.isVisible == true ||
                isPrayerAlert ||
                isMushafActive
            ) && wobbleIntensity > 0.01f
        if (showSweep) {
            val fillProgress = when {
                isDownloading -> animatedDownloadProgress
                isRefreshing -> syncProgress.value
                mediaBar?.isVisible == true -> {
                    if (mediaBar.progress > 0f) {
                        mediaBar.progress
                            .coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
                isPrayerAlert -> prayerAlertState.fillProgress
                isMushafActive -> animatedMushafPageProgress
                else -> 0f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillProgress.coerceAtLeast(0.001f))
                    // Progress belongs to the revealed sync/banner strip. Keeping
                    // it out of the page body prevents a horizontal sweep from
                    // flashing behind the entire Home screen during refresh.
                    .height((contentOffsetY + cornerRadius).coerceAtLeast(1.dp))
                    .graphicsLayer { alpha = revealColorAlpha }
                    .background(fitbitBgColor)
                    .align(Alignment.TopStart)
            )
        }

        // Inner content: pushes down flat (like Fitbit). Two displacement modes on
        // purpose: persistent holds (media bar / alerts / syncing) use top PADDING so
        // the page resizes and its bottom stays scroll-reachable while the strip is
        // up; the transient finger-drag part TRANSLATES the sheet instead, so pulling
        // slides the page rigidly rather than squeezing weight-based layouts — and the
        // offset lambda runs in the placement phase, skipping per-frame relayout.
        val holdOffsetY = heldContentInsetTop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = holdOffsetY, start = horizontalMargin, end = horizontalMargin)
                // The sheet is now below the window top by holdOffsetY, so tell children
                // that much top inset is already spent — otherwise every screen's
                // statusBarsPadding/TopAppBar inset stacks onto the strip and leaves a
                // dead zone under the sheet's top edge.
                .consumeWindowInsets(PaddingValues(top = holdOffsetY))
                .offset {
                    val raw = (dragDistanceAnimated / maxDragDistance).coerceIn(0f, 1f)
                    val dragBeyondHold = (raw - refreshingOffset.value).coerceAtLeast(0f)
                    IntOffset(0, (dragBeyondHold * maxRevealDp).dp.roundToPx())
                }
                .clip(
                    RoundedCornerShape(
                        topStart = cornerRadius,
                        topEnd = cornerRadius,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp
                    )
                )
                .then(idleBackgroundModifier)
        ) {
            // Main screen content
            content(syncState)
        }

        // Indicator row: one fixed-height line above the content sheet carrying
        // whatever is live. A mini bar owns the row when playback or Mushaf
        // reading is active and shows any status as its subtitle; otherwise the
        // status owns the row itself. Either way the row — and so the height of
        // the whole strip — is the same.
        if (wobbleIntensity > 0.05f) {
            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .fillMaxWidth()
                    .height(contentOffsetY)
                    .padding(top = barTopInsetPadding, bottom = barBottomPadding),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    mediaBar?.isVisible == true -> {
                        // A slot rather than a direct call: the media and Mushaf
                        // bars belong to features that exist only on Android
                        // today. The container reserves and animates the row; it
                        // does not need to know what fills it.
                        mediaBar.content(activeStatus?.text)
                    }

                    mushafBar?.isVisible == true -> {
                        mushafBar.content(activeStatus?.text)
                    }

                    else -> {
                        // Pull, release, syncing, results and alerts all occupy one
                        // animated text slot. Keeping them in the same composition
                        // prevents the release -> syncing handoff from cutting.
                        val pullStatus = if (dragDistance > 0f && rawWobbleIntensity > 0.01f) {
                            val canRelease = wobbleIntensity > 0.4f
                            SyncBarStatus(
                                key = if (canRelease) "pull-release" else "pull-drag",
                                text = if (canRelease) "Release to sync" else "Pull to sync",
                                icon = SyncBarIcon.Spinner,
                            )
                        } else {
                            null
                        }
                        val displayedStatus = activeStatus ?: pullStatus
                        if (displayedStatus != null) {
                            AnimatedContent(
                                targetState = displayedStatus,
                                // Progress percentages update in place; only a real
                                // message change should start a transition.
                                contentKey = { it.key },
                                transitionSpec = {
                                    val isPullHandoff =
                                        initialState.key.startsWith("pull-") ||
                                            targetState.key.startsWith("pull-")
                                    if (isPullHandoff) {
                                        // The revealed strip already follows the
                                        // finger vertically. Sliding its label too
                                        // creates double motion at the release
                                        // threshold, so use a compact fade-through.
                                        fadeIn(tween(150, delayMillis = 35)) togetherWith
                                            fadeOut(tween(90))
                                    } else {
                                        (fadeIn(
                                            animationSpec = tween(220, delayMillis = 55),
                                        ) + slideInVertically(
                                            animationSpec = tween(
                                                300,
                                                easing = FastOutSlowInEasing,
                                            ),
                                            initialOffsetY = { height -> height / 2 },
                                        )) togetherWith
                                            (fadeOut(tween(150)) + slideOutVertically(
                                                animationSpec = tween(
                                                    230,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                                targetOffsetY = { height -> -height / 2 },
                                            ))
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = if (activeStatus == null) {
                                            (wobbleIntensity * 2.5f).coerceIn(0f, 1f)
                                        } else {
                                            1f
                                        }
                                    },
                                label = "syncBarMessageTransition",
                            ) { status ->
                                SyncBarStatusRow(
                                    status = status,
                                    spinAngle = if (status.key.startsWith("pull-")) {
                                        wobbleIntensity * 360f
                                    } else {
                                        spinAngle
                                    },
                                    contentColor = indicatorColor,
                                    onDismissed = {
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.LongPress,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Leading glyph for a [SyncBarStatus]. */
private enum class SyncBarIcon { Spinner, Sparkle, Retry, WeatherWarning, DoNotDisturb }

/**
 * One line of information the top strip can show. Several can be live at once;
 * the strip cycles between them inside its single row instead of stacking rows,
 * so its height never depends on how much there is to say.
 */
private data class SyncBarStatus(
    val key: String,
    val text: String,
    val icon: SyncBarIcon,
    val onClick: (() -> Unit)? = null,
    val onDismiss: (() -> Unit)? = null,
)

/** How long each status holds the row before the next one takes over. */
private const val SyncBarStatusCycleMillis = 3_500L

/**
 * Index of the status currently holding the row. Restarts from the top whenever
 * the live set changes, and stops advancing when there is only one — a lone
 * status must never flicker.
 */
@Composable
private fun rememberCyclingStatusIndex(statuses: List<SyncBarStatus>): Int {
    val keys = statuses.joinToString("|") { it.key }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(keys) {
        index = 0
        if (statuses.size > 1) {
            while (true) {
                delay(SyncBarStatusCycleMillis)
                index = (index + 1) % statuses.size
            }
        }
    }
    return if (statuses.isEmpty()) 0 else index % statuses.size
}

/** The shared spinning/steady indicator arc drawn beside a status. */
private fun DrawScope.drawIndicatorArc(color: Color, startAngle: Float) {
    val strokeWidth = 2.dp.toPx()
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = 270f,
        useCenter = false,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
    )
}

/**
 * A status occupying the strip's single row: glyph and message. Multiple live
 * statuses still cycle, but remain visually quiet without pagination dots.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SyncBarStatusRow(
    status: SyncBarStatus,
    spinAngle: Float,
    contentColor: Color,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (status.onDismiss != null) {
                    Modifier.pointerInput(status.key) {
                        var totalDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                            onDragEnd = {
                                if (totalDrag < -80f) {
                                    onDismissed()
                                    status.onDismiss.invoke()
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (status.onClick != null) {
                    Modifier.clickable(onClick = status.onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (status.icon) {
            SyncBarIcon.Spinner -> Canvas(modifier = Modifier.size(18.dp)) {
                drawIndicatorArc(contentColor, startAngle = spinAngle)
            }

            SyncBarIcon.Sparkle -> Text(
                text = "✦",
                style = MaterialTheme.typography.labelMedium,
                fontSize = 15.sp,
                color = contentColor,
            )

            SyncBarIcon.Retry -> Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )

            SyncBarIcon.WeatherWarning -> Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )

            SyncBarIcon.DoNotDisturb -> Icon(
                imageVector = Icons.Default.DoNotDisturbOn,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = status.text,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 15.sp,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
            modifier = Modifier
                .weight(1f, fill = false)
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 700,
                    repeatDelayMillis = 1_000,
                    velocity = 42.dp,
                ),
        )
    }
}
