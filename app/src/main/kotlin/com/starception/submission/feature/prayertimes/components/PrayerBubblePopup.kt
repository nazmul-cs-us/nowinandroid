/**
 * PRAYER BUBBLE POPUP COMPONENT
 *
 * This file contains the 3D bubble popup component that appears when touching prayer indicators
 * in the Smart Tracking tile. It provides an immersive, glassmorphic popup with prayer details
 * and status information.
 *
 * WHAT IT DOES:
 * - Creates a 3D animated bubble with glassmorphic design
 * - Shows prayer name, time, and completion status
 * - Provides smooth entrance/exit animations
 * - Displays decorative elements and prayer information
 *
 * FEATURES:
 * - Glassmorphic bubble with blur and transparency effects
 * - 3D depth with shadow and elevation
 * - Smooth spring animations for entrance/exit
 * - Arabic and English prayer names
 * - Prayer completion status with visual indicators
 * - Touch outside to dismiss functionality
 * - Material 3 color scheme integration
 */
package com.starception.submission.feature.prayertimes.components

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size

/**
 * Prayer data class for bubble popup
 */
data class PrayerBubbleData(
    val name: String,
    val arabicName: String,
    val time: String,
    val isPrayed: Boolean,
    val initial: String
)

/**
 * Main 3D Bubble Popup Composable
 *
 * @param prayerData Prayer information to display
 * @param onDismiss Callback when popup is dismissed
 */
@Composable
fun PrayerBubblePopup(
    prayerData: PrayerBubbleData,
    onDismiss: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Animation states - start visible immediately for better UX
    var isVisible by remember { mutableStateOf(true) }

    // Trigger entrance animation and haptic feedback
    LaunchedEffect(Unit) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Dismiss handler with animation
    val handleDismiss: () -> Unit = {
        isVisible = false
        coroutineScope.launch {
            delay(300)
            onDismiss()
        }
    }

    // Animated values for entrance/exit
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bubbleScale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "bubbleAlpha"
    )

    // 3D rotation effect
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotationY by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotationY"
    )

    // Dialog with glassmorphic background
    Dialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        // Add logging
        LaunchedEffect(Unit) {
            android.util.Log.d("PrayerBubble", "Dialog is being displayed, scale=$scale, alpha=$alpha, isVisible=$isVisible")
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * alpha))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    android.util.Log.d("PrayerBubble", "Background clicked, dismissing")
                    handleDismiss()
                },
            contentAlignment = Alignment.Center
        ) {

            // 3D Bubble Card
            Bubble3DCard(
                prayerData = prayerData,
                scale = scale,
                alpha = alpha,
                rotationY = rotationY,
                onDismiss = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    handleDismiss()
                }
            )
        }
    }
}

/**
 * 3D Bubble Card with glassmorphic design
 */
@Composable
private fun Bubble3DCard(
    prayerData: PrayerBubbleData,
    scale: Float,
    alpha: Float,
    rotationY: Float,
    onDismiss: () -> Unit
) {
    // Add logging
    LaunchedEffect(Unit) {
        android.util.Log.d("PrayerBubble", "Bubble3DCard rendered: ${prayerData.name}, scale=$scale, alpha=$alpha")
    }

    Box(
        modifier = Modifier
            .width(320.dp)
            .height(450.dp)
            .scale(scale)
            .alpha(alpha)
            .graphicsLayer {
                this.rotationY = rotationY
                shadowElevation = 24.dp.toPx()
            }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                // Prevent click propagation to background
            }
    ) {
        // Balloon-shaped surface with much larger, more visible tail
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .balloonShape(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tailWidth = 80f,
                    tailHeight = 60f
                ),
            color = Color.Transparent,
            shadowElevation = 0.dp
        ) {}
        Box {
            // Gradient background overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                Color.Transparent
                            ),
                            center = Offset(0.5f, 0.3f),
                            radius = 800f
                        )
                    )
            )

            // Content (account for balloon tail at bottom - larger tail needs more padding)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Prayer initial in large circle
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = if (prayerData.isPrayed) {
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primaryContainer
                                    )
                                } else {
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surface
                                    )
                                },
                                start = Offset(0f, 0f),
                                end = Offset(100f, 100f)
                            ),
                            shape = CircleShape
                        )
                        .graphicsLayer {
                            shadowElevation = 12.dp.toPx()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = prayerData.initial,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 64.sp,
                            color = if (prayerData.isPrayed) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            }
                        )

                        // Check mark if prayed
                        if (prayerData.isPrayed) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                // Prayer information
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    // Prayer name in English
                    Text(
                        text = prayerData.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Prayer name in Arabic
                    Text(
                        text = prayerData.arabicName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 28.sp
                    )

                    // Divider
                    HorizontalDivider(
                        modifier = Modifier
                            .width(120.dp)
                            .padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        thickness = 2.dp
                    )

                    // Prayer time
                    Text(
                        text = prayerData.time,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Status badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (prayerData.isPrayed) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = if (prayerData.isPrayed) "Completed ✓" else "Pending",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (prayerData.isPrayed) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Balloon shape modifier - creates a speech balloon with tail at bottom
 * Inspired by skydoves/Balloon library design
 */
fun Modifier.balloonShape(
    color: Color,
    tailWidth: Float = 50f,
    tailHeight: Float = 35f
) = this.drawBehind {
    val width = size.width
    val height = size.height - tailHeight
    val cornerRadius = 40.dp.toPx()

    // Create complete balloon path
    val balloonPath = Path().apply {
        // Start from left side, going clockwise

        // Top-left corner
        moveTo(cornerRadius, 0f)

        // Top edge
        lineTo(width - cornerRadius, 0f)

        // Top-right corner (arc)
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                width - cornerRadius * 2, 0f,
                width, cornerRadius * 2
            ),
            startAngleDegrees = -90f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )

        // Right edge
        lineTo(width, height - cornerRadius)

        // Bottom-right corner (arc)
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                width - cornerRadius * 2, height - cornerRadius * 2,
                width, height
            ),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )

        // Bottom edge to tail start
        lineTo(width / 2 + tailWidth / 2, height)

        // Balloon tail (smooth curve pointing down)
        quadraticBezierTo(
            width / 2 + tailWidth / 4, height + tailHeight / 2,
            width / 2, height + tailHeight
        )
        quadraticBezierTo(
            width / 2 - tailWidth / 4, height + tailHeight / 2,
            width / 2 - tailWidth / 2, height
        )

        // Continue bottom edge
        lineTo(cornerRadius, height)

        // Bottom-left corner (arc)
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                0f, height - cornerRadius * 2,
                cornerRadius * 2, height
            ),
            startAngleDegrees = 90f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )

        // Left edge
        lineTo(0f, cornerRadius)

        // Top-left corner (arc)
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                0f, 0f,
                cornerRadius * 2, cornerRadius * 2
            ),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )

        close()
    }

    // Draw shadow for depth
    drawPath(
        path = balloonPath,
        color = Color.Black.copy(alpha = 0.15f),
        style = androidx.compose.ui.graphics.drawscope.Fill,
        alpha = 1f
    )

    // Draw main balloon fill
    drawPath(
        path = balloonPath,
        color = color,
        style = androidx.compose.ui.graphics.drawscope.Fill
    )

    // Draw balloon border/stroke for classic speech balloon look
    drawPath(
        path = balloonPath,
        color = Color.Gray.copy(alpha = 0.3f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
    )
}

/**
 * Helper function to get Arabic prayer name
 */
fun getArabicPrayerName(prayerName: String): String {
    return when (prayerName) {
        "Fajr" -> "الفجر"
        "Dhuhr" -> "الظهر"
        "Asr" -> "العصر"
        "Maghrib" -> "المغرب"
        "Isha" -> "العشاء"
        else -> prayerName
    }
}
