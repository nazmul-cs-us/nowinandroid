package com.starception.submission.feature.prayertimes.components

import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.res.painterResource
import com.starception.submission.R
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.absoluteValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow

/**
 * iOS-style swipe-to-reveal action card for prayer time adjustment.
 *
 * Swipe left to reveal +/- stepper buttons for time adjustment.
 * Swipe right to reveal Reset button to restore default offset.
 * Auto-collapses after adjustment for convenience.
 *
 * @param prayerName The name of the prayer (e.g., "Fajr", "Dhuhr")
 * @param currentOffset The current time offset in minutes
 * @param gesturesEnabled Whether adjustment gestures are currently unlocked
 * @param isRevealed Whether this card's actions are currently revealed
 * @param onRevealChange Callback when the reveal state changes
 * @param onOffsetChange Callback when the offset is changed via +/- buttons
 * @param onResetOffset Callback to reset the offset to default
 * @param content The main card content to display
 */
@Composable
fun SwipeToRevealCard(
    prayerName: String,
    currentOffset: Int,
    gesturesEnabled: Boolean = true,
    isRevealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onOffsetChange: (Int) -> Unit,
    onResetOffset: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Use rememberUpdatedState to ensure gesture callbacks always see current values
    val currentIsRevealed by rememberUpdatedState(isRevealed)

    // Track which side is revealed: "adjust" (left swipe), "reset" (right swipe), or null
    var revealedSide by remember { mutableStateOf<String?>(null) }
    val currentRevealedSide by rememberUpdatedState(revealedSide)

    // Width of the revealed action buttons areas
    val adjustRevealedWidth = 120.dp  // Right side: +/- buttons
    val resetRevealedWidth = 80.dp    // Left side: Reset button
    val adjustRevealedWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { adjustRevealedWidth.toPx() }
    val resetRevealedWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { resetRevealedWidth.toPx() }

    // Swipe offset state (negative = left swipe, positive = right swipe)
    var swipeOffset by remember { mutableFloatStateOf(0f) }

    // Peek animation state - shows a brief hint that the card can be swiped
    var hasShownPeek by remember { mutableStateOf(false) }
    val peekOffset = remember { Animatable(0f) }

    // Trigger peek animation once on first render (only for first card to avoid overwhelming)
    LaunchedEffect(prayerName, gesturesEnabled) {
        if (gesturesEnabled && !hasShownPeek && prayerName == "Fajr") {
            delay(1000) // Wait for UI to settle
            hasShownPeek = true
            // Peek left to show +/- buttons hint
            peekOffset.animateTo(
                targetValue = -30f,
                animationSpec = tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            )
            delay(200)
            peekOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    // Animate the offset for smooth transitions
    // When revealed, we need to add swipeOffset to show the drag feedback
    val animatedOffset by animateFloatAsState(
        targetValue = when {
            isRevealed && revealedSide == "adjust" -> -adjustRevealedWidthPx + swipeOffset
            isRevealed && revealedSide == "reset" -> resetRevealedWidthPx + swipeOffset
            else -> swipeOffset + peekOffset.value
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "swipeOffset"
    )

    // Calculate edge hint visibility based on offset (shows chevrons more when swiping)
    val leftHintAlpha = ((animatedOffset / 50f).coerceIn(0f, 1f)) * 0.7f
    val rightHintAlpha = ((-animatedOffset / 50f).coerceIn(0f, 1f)) * 0.7f

    // Collapse when another card is revealed
    LaunchedEffect(isRevealed) {
        if (!isRevealed) {
            swipeOffset = 0f
            revealedSide = null
        }
    }

    LaunchedEffect(gesturesEnabled) {
        if (!gesturesEnabled) {
            swipeOffset = 0f
            revealedSide = null
            if (isRevealed) onRevealChange(false)
        }
    }

    val cardShape = RoundedCornerShape(24.dp)

    // Animated elevation - increases when revealed/active
    val targetElevation = when {
        isRevealed -> 12.dp  // Higher elevation when revealed
        else -> 6.dp         // Default elevation
    }
    val animatedElevation by animateFloatAsState(
        targetValue = targetElevation.value,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "elevationAnimation"
    )

    // Outer container with minimal padding for shadow space
    Box(
        modifier = modifier.padding(2.dp)
    ) {
        // Shadow layer rendered outside the clip with animated elevation
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    this.shadowElevation = animatedElevation.dp.toPx()
                    this.shape = cardShape
                    this.clip = false
                    this.ambientShadowColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.15f)
                    this.spotShadowColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f)
                }
        )
        // Content with clip for swipe-to-reveal
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(cardShape)
        ) {
        // LEFT SIDE: Reset button (only show when swiping right FROM COLLAPSED state, or revealed on reset side)
        // Matching the new vertical style of +/- side
        val showResetSide = (swipeOffset > 0 && !isRevealed) || (isRevealed && revealedSide == "reset")
        if (showResetSide) {
            // Track swipe on the Reset area for easy collapse
            var resetAreaSwipeOffset by remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(resetRevealedWidth)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .background(color = MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                    )
                    .pointerInput(isRevealed, revealedSide) {
                        // Enable swipe left on Reset area to collapse
                        if (isRevealed && revealedSide == "reset") {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    resetAreaSwipeOffset = 0f
                                    Log.d("SwipeToReveal", "🖐️ Drag started on Reset area")
                                },
                                onDragEnd = {
                                    if (resetAreaSwipeOffset < -30f) {
                                        // Swipe left detected on Reset area - collapse
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onRevealChange(false)
                                        Log.d("SwipeToReveal", "📕 Swipe left on Reset area to collapse $prayerName")
                                    }
                                    resetAreaSwipeOffset = 0f
                                },
                                onDragCancel = {
                                    resetAreaSwipeOffset = 0f
                                }
                            ) { _, dragAmount ->
                                if (dragAmount < 0) {
                                    resetAreaSwipeOffset += dragAmount
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Vertical layout matching the +/- side style
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Reset label on top
                    Text(
                        text = "Reset",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "to default",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Reset button in a pill shape
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .clickable {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onResetOffset()
                                Log.d("SwipeToReveal", "🔄 Reset $prayerName to default")
                                // Brief pause so the reset is seen, then spring the panel closed
                                coroutineScope.launch {
                                    delay(400L)
                                    onRevealChange(false)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_reset_settings),
                            contentDescription = "Reset to default",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // RIGHT SIDE: +/- adjustment buttons (only show when swiping left FROM COLLAPSED state, or revealed on adjust side)
        // iOS-style unified stepper control design
        val showAdjustSide = (swipeOffset < 0 && !isRevealed) || (isRevealed && revealedSide == "adjust")
        if (showAdjustSide) {
            // Track swipe on the ENTIRE +/- area for easy collapse
            var adjustAreaSwipeOffset by remember { mutableFloatStateOf(0f) }
            var isDraggingOnAdjustArea by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(adjustRevealedWidth)
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                    .background(color = MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                    )
                    // ENTIRE area is swipeable to collapse - drag anywhere on the +/- panel
                    .pointerInput(isRevealed, revealedSide) {
                        if (isRevealed && revealedSide == "adjust") {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    adjustAreaSwipeOffset = 0f
                                    isDraggingOnAdjustArea = true
                                    Log.d("SwipeToReveal", "🖐️ Drag started on +/- area")
                                },
                                onDragEnd = {
                                    if (adjustAreaSwipeOffset > 40f) {
                                        // Swipe right detected - collapse
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onRevealChange(false)
                                        Log.d("SwipeToReveal", "📕 Swipe right on +/- area to collapse $prayerName (offset: $adjustAreaSwipeOffset)")
                                    }
                                    adjustAreaSwipeOffset = 0f
                                    isDraggingOnAdjustArea = false
                                },
                                onDragCancel = {
                                    adjustAreaSwipeOffset = 0f
                                    isDraggingOnAdjustArea = false
                                }
                            ) { _, dragAmount ->
                                // Only track rightward drag (positive)
                                if (dragAmount > 0) {
                                    adjustAreaSwipeOffset += dragAmount
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Vertical layout: Value on top, buttons below
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Current offset value - prominent display
                    Text(
                        text = when {
                            currentOffset > 0 -> "+$currentOffset"
                            currentOffset < 0 -> "$currentOffset"
                            else -> "0"
                        },
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = when {
                            currentOffset > 0 -> MaterialTheme.colorScheme.primary
                            currentOffset < 0 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )

                    Text(
                        text = "minutes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Stepper buttons in a unified pill shape
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Minus button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable {
                                    if (!isDraggingOnAdjustArea) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val newOffset = currentOffset - 1
                                        onOffsetChange(newOffset)
                                        Log.d("SwipeToReveal", "➖ $prayerName: $currentOffset → $newOffset")
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Remove,
                                contentDescription = "Decrease time",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Vertical divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        // Plus button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable {
                                    if (!isDraggingOnAdjustArea) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val newOffset = currentOffset + 1
                                        onOffsetChange(newOffset)
                                        Log.d("SwipeToReveal", "➕ $prayerName: $currentOffset → $newOffset")
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Increase time",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            } // End of outer Box
        }

        // Main content (slides when revealing actions)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
        ) {
            // The actual draggable content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(prayerName, isRevealed, gesturesEnabled) {
                    if (!gesturesEnabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            Log.d("SwipeToReveal", "🖐️ Drag started on $prayerName | isRevealed=$currentIsRevealed | revealedSide=$currentRevealedSide")
                        },
                        onDragEnd = {
                            Log.d("SwipeToReveal", "🛑 Drag ended on $prayerName | currentIsRevealed=$currentIsRevealed | revealedSide=$currentRevealedSide | swipeOffset=$swipeOffset")
                            // Lower threshold (25%) for easier swipe activation
                            val adjustThreshold = adjustRevealedWidthPx * 0.25f
                            val resetThreshold = resetRevealedWidthPx * 0.25f
                            // Collapse threshold - any swipe > 15px toward center
                            val collapseThreshold = 15f

                            Log.d("SwipeToReveal", "📊 STATE: currentIsRevealed=$currentIsRevealed, currentRevealedSide=$currentRevealedSide, swipeOffset=$swipeOffset")
                            Log.d("SwipeToReveal", "📊 THRESHOLDS: adjustThreshold=$adjustThreshold, resetThreshold=$resetThreshold, collapseThreshold=$collapseThreshold")
                            Log.d("SwipeToReveal", "📊 CONDITIONS CHECK:")
                            Log.d("SwipeToReveal", "   - Collapse +/-: ${currentIsRevealed && currentRevealedSide == "adjust" && swipeOffset > collapseThreshold}")
                            Log.d("SwipeToReveal", "   - Collapse Reset: ${currentIsRevealed && currentRevealedSide == "reset" && swipeOffset < -collapseThreshold}")
                            Log.d("SwipeToReveal", "   - Reveal +/-: ${!currentIsRevealed && swipeOffset < -adjustThreshold}")
                            Log.d("SwipeToReveal", "   - Reveal Reset: ${!currentIsRevealed && swipeOffset > resetThreshold}")

                            when {
                                // When +/- is revealed, swipe right collapses
                                currentIsRevealed && currentRevealedSide == "adjust" && swipeOffset > collapseThreshold -> {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    // Reset offset FIRST, then collapse to prevent animation glitch
                                    swipeOffset = 0f
                                    onRevealChange(false)
                                    Log.d("SwipeToReveal", "📕 Swipe right to collapse +/- for $prayerName")
                                }
                                // When Reset is revealed, swipe left collapses
                                currentIsRevealed && currentRevealedSide == "reset" && swipeOffset < -collapseThreshold -> {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    // Reset offset FIRST, then collapse to prevent animation glitch
                                    swipeOffset = 0f
                                    onRevealChange(false)
                                    Log.d("SwipeToReveal", "📕 Swipe left to collapse Reset for $prayerName")
                                }
                                // Swipe left past threshold → reveal adjust buttons (only when collapsed)
                                !currentIsRevealed && swipeOffset < -adjustThreshold -> {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    swipeOffset = 0f
                                    revealedSide = "adjust"
                                    onRevealChange(true)
                                    Log.d("SwipeToReveal", "📖 Revealed adjust buttons for $prayerName")
                                }
                                // Swipe right past threshold → reveal reset button (only when collapsed)
                                !currentIsRevealed && swipeOffset > resetThreshold -> {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    swipeOffset = 0f
                                    revealedSide = "reset"
                                    onRevealChange(true)
                                    Log.d("SwipeToReveal", "🔄 Revealed reset button for $prayerName")
                                }
                                else -> {
                                    // No action taken, reset offset
                                    swipeOffset = 0f
                                }
                            }
                        },
                        onDragCancel = {
                            swipeOffset = 0f
                        }
                    ) { _, dragAmount ->
                        if (currentIsRevealed) {
                            // When revealed, only allow swiping to COLLAPSE (toward center)
                            // Don't allow revealing the other side
                            if (currentRevealedSide == "adjust") {
                                // +/- is shown on right, only allow swiping right (positive) to collapse
                                if (dragAmount > 0) {
                                    swipeOffset = (swipeOffset + dragAmount).coerceIn(0f, adjustRevealedWidthPx)
                                }
                            } else if (currentRevealedSide == "reset") {
                                // Reset is shown on left, only allow swiping left (negative) to collapse
                                if (dragAmount < 0) {
                                    swipeOffset = (swipeOffset + dragAmount).coerceIn(-resetRevealedWidthPx, 0f)
                                }
                            }
                        } else {
                            // When collapsed, allow both directions
                            swipeOffset = (swipeOffset + dragAmount).coerceIn(-adjustRevealedWidthPx, resetRevealedWidthPx)
                        }
                    }
                }
                    .pointerInput(currentIsRevealed) {
                        // Use detectTapGestures for tap-to-collapse, separate from drag
                        if (currentIsRevealed) {
                            detectTapGestures(
                                onTap = {
                                    onRevealChange(false)
                                    Log.d("SwipeToReveal", "👆 Tapped to collapse $prayerName")
                                }
                            )
                        }
                    }
            ) {
                content()
            }

            // DYNAMIC ROTATING CHEVRON - rotates based on swipe progress
            // Right edge chevron: starts as < (swipe left), rotates to > (swipe right to close) as user swipes
            if (gesturesEnabled) run {
                // Calculate rotation based on swipe progress (0 to 180 degrees)
                // animatedOffset goes from 0 to -adjustRevealedWidthPx when swiping left
                val swipeProgress = (-animatedOffset / adjustRevealedWidthPx).coerceIn(0f, 1f)
                val chevronRotation by animateFloatAsState(
                    targetValue = swipeProgress * 180f, // 0° = <, 180° = >
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "chevronRotation"
                )

                // Dynamic alpha: more visible as user swipes
                val chevronAlpha = 0.4f + (swipeProgress * 0.4f) // 0.4 to 0.8

                // Dynamic size: grows slightly as user swipes
                val chevronSize = 16.dp + (swipeProgress * 6f).dp // 16dp to 22dp

                // Dynamic color: transitions from onSurfaceVariant to onSecondaryContainer
                val chevronColor = if (swipeProgress > 0.5f) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                // Only show right edge chevron (for +/- reveal/collapse)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp + (swipeProgress * 4f).dp) // 4dp to 8dp
                        .alpha(chevronAlpha)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = if (swipeProgress < 0.5f) "Swipe left to adjust" else "Swipe right to close",
                        tint = chevronColor,
                        modifier = Modifier
                            .size(chevronSize)
                            .graphicsLayer {
                                rotationZ = chevronRotation
                            }
                    )
                }
            }

            // Left edge chevron for Reset (only when swiping right or revealed)
            if (gesturesEnabled && ((swipeOffset > 0 && !isRevealed) || (isRevealed && revealedSide == "reset"))) {
                val resetSwipeProgress = if (isRevealed && revealedSide == "reset") {
                    1f
                } else {
                    (animatedOffset / resetRevealedWidthPx).coerceIn(0f, 1f)
                }
                val resetChevronRotation by animateFloatAsState(
                    targetValue = resetSwipeProgress * 180f, // 0° = >, 180° = <
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "resetChevronRotation"
                )
                val resetChevronAlpha = 0.3f + (resetSwipeProgress * 0.4f)
                val resetChevronSize = 14.dp + (resetSwipeProgress * 6f).dp

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp + (resetSwipeProgress * 4f).dp)
                        .alpha(resetChevronAlpha)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = if (resetSwipeProgress < 0.5f) "Swipe right to reset" else "Swipe left to close",
                        tint = if (resetSwipeProgress > 0.5f)
                            MaterialTheme.colorScheme.onTertiaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(resetChevronSize)
                            .graphicsLayer {
                                rotationZ = resetChevronRotation
                            }
                    )
                }
            }
        }
        }
    }
}
