package com.starception.submission.feature.prayertimes.components

import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * iOS-style swipe-to-reveal action card for prayer time adjustment.
 *
 * Swipe left to reveal +/- stepper buttons for time adjustment.
 * Swipe right to reveal Reset button to restore default offset.
 * Auto-collapses after adjustment for convenience.
 *
 * @param prayerName The name of the prayer (e.g., "Fajr", "Dhuhr")
 * @param currentOffset The current time offset in minutes
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
    isRevealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onOffsetChange: (Int) -> Unit,
    onResetOffset: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Auto-collapse delay after button tap (milliseconds)
    val autoCollapseDelayMs = 800L

    // Width of the revealed action buttons areas
    val adjustRevealedWidth = 120.dp  // Right side: +/- buttons
    val resetRevealedWidth = 80.dp    // Left side: Reset button
    val adjustRevealedWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { adjustRevealedWidth.toPx() }
    val resetRevealedWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { resetRevealedWidth.toPx() }

    // Swipe offset state (negative = left swipe, positive = right swipe)
    var swipeOffset by remember { mutableFloatStateOf(0f) }

    // Track which side is revealed: "adjust" (left swipe), "reset" (right swipe), or null
    var revealedSide by remember { mutableStateOf<String?>(null) }

    // Animate the offset for smooth transitions
    val animatedOffset by animateFloatAsState(
        targetValue = when {
            isRevealed && revealedSide == "adjust" -> -adjustRevealedWidthPx
            isRevealed && revealedSide == "reset" -> resetRevealedWidthPx
            else -> swipeOffset
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "swipeOffset"
    )

    // Collapse when another card is revealed
    LaunchedEffect(isRevealed) {
        if (!isRevealed) {
            swipeOffset = 0f
            revealedSide = null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
    ) {
        // LEFT SIDE: Reset button (only show when swiping right or revealed on reset side)
        if (swipeOffset > 0 || (isRevealed && revealedSide == "reset")) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(resetRevealedWidth)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .background(color = MaterialTheme.colorScheme.tertiaryContainer)
                    .clickable {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onResetOffset()
                        Log.d("SwipeToReveal", "🔄 Reset $prayerName to default")
                        // Auto-collapse after reset
                        coroutineScope.launch {
                            delay(autoCollapseDelayMs)
                            onRevealChange(false)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Reset to default",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Reset",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        // RIGHT SIDE: +/- adjustment buttons (only show when swiping left or revealed on adjust side)
        if (swipeOffset < 0 || (isRevealed && revealedSide == "adjust")) {
            // Tap on background area (not buttons) to collapse immediately
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(adjustRevealedWidth)
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                    .background(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable {
                        // Tap on background to collapse
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRevealChange(false)
                        Log.d("SwipeToReveal", "👆 Tapped background to collapse $prayerName")
                    }
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
            // Minus button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val newOffset = currentOffset - 1
                        onOffsetChange(newOffset)
                        Log.d("SwipeToReveal", "➖ $prayerName: $currentOffset → $newOffset")
                        // Auto-collapse after delay
                        coroutineScope.launch {
                            delay(autoCollapseDelayMs)
                            onRevealChange(false)
                            Log.d("SwipeToReveal", "🔄 Auto-collapsed $prayerName after adjustment")
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "Decrease time",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Current offset display
            Text(
                text = when {
                    currentOffset > 0 -> "+$currentOffset"
                    currentOffset < 0 -> "$currentOffset"
                    else -> "0"
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Plus button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val newOffset = currentOffset + 1
                        onOffsetChange(newOffset)
                        Log.d("SwipeToReveal", "➕ $prayerName: $currentOffset → $newOffset")
                        // Auto-collapse after delay
                        coroutineScope.launch {
                            delay(autoCollapseDelayMs)
                            onRevealChange(false)
                            Log.d("SwipeToReveal", "🔄 Auto-collapsed $prayerName after adjustment")
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Increase time",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            }
        }

        // Main content (slides when revealing actions)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .pointerInput(prayerName) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            Log.d("SwipeToReveal", "🖐️ Drag started on $prayerName")
                        },
                        onDragEnd = {
                            val adjustThreshold = adjustRevealedWidthPx * 0.4f
                            val resetThreshold = resetRevealedWidthPx * 0.4f

                            when {
                                // Swipe left past threshold → reveal adjust buttons
                                swipeOffset < -adjustThreshold && !isRevealed -> {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    revealedSide = "adjust"
                                    onRevealChange(true)
                                    Log.d("SwipeToReveal", "📖 Revealed adjust buttons for $prayerName")
                                }
                                // Swipe right past threshold → reveal reset button
                                swipeOffset > resetThreshold && !isRevealed -> {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    revealedSide = "reset"
                                    onRevealChange(true)
                                    Log.d("SwipeToReveal", "🔄 Revealed reset button for $prayerName")
                                }
                                // When revealed, any opposite swipe collapses
                                isRevealed && revealedSide == "adjust" && swipeOffset > -adjustThreshold -> {
                                    onRevealChange(false)
                                    Log.d("SwipeToReveal", "📕 Collapsed adjust for $prayerName")
                                }
                                isRevealed && revealedSide == "reset" && swipeOffset < resetThreshold -> {
                                    onRevealChange(false)
                                    Log.d("SwipeToReveal", "📕 Collapsed reset for $prayerName")
                                }
                            }
                            swipeOffset = 0f
                        },
                        onDragCancel = {
                            swipeOffset = 0f
                        }
                    ) { _, dragAmount ->
                        if (isRevealed) {
                            // When revealed, allow opposite direction swipe to collapse
                            if (revealedSide == "adjust" && dragAmount > 0) {
                                swipeOffset = (swipeOffset + dragAmount).coerceIn(0f, adjustRevealedWidthPx)
                            } else if (revealedSide == "reset" && dragAmount < 0) {
                                swipeOffset = (swipeOffset + dragAmount).coerceIn(-resetRevealedWidthPx, 0f)
                            }
                        } else {
                            // When collapsed, allow both directions
                            swipeOffset = (swipeOffset + dragAmount).coerceIn(-adjustRevealedWidthPx, resetRevealedWidthPx)
                        }
                    }
                }
                .clickable(enabled = isRevealed) {
                    // Tap on content to collapse when revealed
                    onRevealChange(false)
                    Log.d("SwipeToReveal", "👆 Tapped to collapse $prayerName")
                }
        ) {
            content()
        }
    }
}
