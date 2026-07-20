/**
 * PRAYER POPUP COMPONENT - iOS-Inspired Design
 *
 * Clean, minimal popup with blur effects and simple animations.
 * Inspired by iOS modal sheets and alert dialogs.
 *
 * WHAT IT DOES:
 * - Displays prayer information in a clean card design
 * - Shows countdown to prayer time or elapsed time
 * - Allows marking/unmarking prayers as prayed
 * - Provides smooth iOS-style animations
 *
 * FEATURES:
 * - iOS-inspired design with blur effects
 * - Clean, minimal layout with generous spacing
 * - Real-time countdown/elapsed time display
 * - Simple toggle button for prayer status
 * - Smooth spring-based animations
 * - Touch outside to dismiss
 */
package com.starception.submission.feature.prayertimes.components

import com.starception.submission.feature.prayertimes.getPrayerDisplayName
import com.starception.submission.feature.prayertimes.isJumuahDay
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.starception.submission.core.designsystem.animation.NiaMotion
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import com.starception.submission.core.designsystem.animation.NiaTransitions
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import java.time.LocalTime
import java.time.Duration

/**
 * Prayer data class for popup
 */
data class PrayerBubbleData(
    val name: String,
    val arabicName: String,
    val time: String,
    val isPrayed: Boolean,
    val initial: String,
    val prayerTime: LocalTime? = null  // Actual prayer time for countdown
)

/**
 * Main iOS-Inspired Popup Composable
 *
 * @param prayerData Prayer information to display
 * @param onDismiss Callback when popup is dismissed
 * @param onTogglePrayer Callback when prayer status is toggled
 */
@Composable
fun PrayerBubblePopup(
    prayerData: PrayerBubbleData,
    onDismiss: () -> Unit,
    onTogglePrayer: ((String, Boolean) -> Unit)? = null
) {
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Animation states
    var isVisible by remember { mutableStateOf(true) }

    // Local prayer status state for reactive UI updates
    var isPrayed by remember { mutableStateOf(prayerData.isPrayed) }

    // Live countdown state - updates every second
    var countdownText by remember { mutableStateOf("") }
    var isUpcoming by remember { mutableStateOf(true) }

    // Update countdown every second
    LaunchedEffect(prayerData.prayerTime) {
        while (true) {
            prayerData.prayerTime?.let { prayerTime ->
                val now = LocalTime.now()
                val duration = Duration.between(now, prayerTime)

                if (duration.isNegative) {
                    // Prayer time has passed
                    val elapsed = duration.abs()
                    val hours = elapsed.toHours()
                    val minutes = elapsed.toMinutes() % 60

                    isUpcoming = false
                    countdownText = when {
                        hours > 0 -> "${hours}h ${minutes}m ago"
                        minutes > 0 -> "${minutes}m ago"
                        else -> "Just now"
                    }
                } else {
                    // Prayer time is upcoming
                    val hours = duration.toHours()
                    val minutes = duration.toMinutes() % 60

                    isUpcoming = true
                    countdownText = when {
                        hours > 0 -> "in ${hours}h ${minutes}m"
                        minutes > 0 -> "in ${minutes}m"
                        else -> "Now"
                    }
                }
            }
            delay(1000) // Update every second
        }
    }

    // Trigger entrance animation and haptic feedback
    LaunchedEffect(Unit) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Dismiss handler with animation
    val handleDismiss: () -> Unit = {
        isVisible = false
        coroutineScope.launch {
            delay(200)
            onDismiss()
        }
    }

    // iOS-style entrance/exit animations
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "popupScale"
    )

    // Fade tracks the scale spring on the way in (~400ms settle) and finishes within
    // the 200ms dismiss delay on the way out, so both movements read as one.
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = if (isVisible) {
            NiaMotion.enterTween()
        } else {
            NiaMotion.exitTween(NiaMotion.Duration.SHORT_4)
        },
        label = "popupAlpha"
    )

    // iOS-style Dialog
    Dialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * alpha))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    handleDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            // iOS-style Card with Blur Effect
            IOSPrayerCard(
                prayerData = prayerData,
                isPrayed = isPrayed,
                countdownText = countdownText,
                isUpcoming = isUpcoming,
                scale = scale,
                alpha = alpha,
                onDismiss = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    handleDismiss()
                },
                onTogglePrayer = { newStatus ->
                    isPrayed = newStatus
                    onTogglePrayer?.invoke(prayerData.name, newStatus)
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
        }
    }
}

/**
 * iOS-style Prayer Card with clean, minimal design
 */
@Composable
private fun IOSPrayerCard(
    prayerData: PrayerBubbleData,
    isPrayed: Boolean,
    countdownText: String,
    isUpcoming: Boolean,
    scale: Float,
    alpha: Float,
    onDismiss: () -> Unit,
    onTogglePrayer: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(340.dp)
            .wrapContentHeight()
            .scale(scale)
            .alpha(alpha)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                // Prevent click propagation to background
            },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Close button - iOS style (top right X)
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-8).dp)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Prayer initial circle - simple and clean
            Box(
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = if (isPrayed) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = prayerData.initial,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isPrayed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Simple checkmark badge if prayed
                androidx.compose.animation.AnimatedVisibility(
                    visible = isPrayed,
                    modifier = Modifier.align(Alignment.BottomEnd),
                    enter = NiaTransitions.fabEnter(),
                    exit = NiaTransitions.fabExit(),
                ) {
                    Surface(
                        modifier = Modifier.size(24.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Prayed",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Prayer name - clean typography
            Text(
                text = getPrayerDisplayName(prayerData.name),
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Arabic name - subtle
            Text(
                text = prayerData.arabicName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Prayer time - simple container
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = prayerData.time,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (countdownText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = countdownText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Simple divider
            Divider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action buttons - iOS style
            if (isPrayed) {
                // Text button for unmark action
                TextButton(
                    onClick = { onTogglePrayer(false) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Mark as Not Prayed",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                // Outlined button for mark action - disabled if prayer time hasn't arrived
                NiaOutlinedButton(
                    onClick = { onTogglePrayer(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !isUpcoming // Disable if prayer time is still upcoming
                ) {
                    Text(
                        text = if (isUpcoming) "Prayer Time Not Yet" else "Mark as Prayed",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status message - subtle
            Text(
                text = when {
                    isPrayed -> "Prayer marked as complete"
                    isUpcoming -> "Can mark after prayer time"
                    else -> "Track your daily prayers"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Helper function to get Arabic prayer name
 */
fun getArabicPrayerName(prayerName: String): String {
    if (prayerName == "Dhuhr" && isJumuahDay()) return "ٱلْجُمُعَة"
    return when (prayerName) {
        "Fajr" -> "ٱلْفَجْر"
        "Dhuhr" -> "ٱلظُّهْر"
        "Asr" -> "ٱلْعَصْر"
        "Maghrib" -> "ٱلْمَغْرِب"
        "Isha" -> "ٱلْعِشَاء"
        else -> prayerName
    }
}
