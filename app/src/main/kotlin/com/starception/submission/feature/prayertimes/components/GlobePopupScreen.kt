package com.starception.submission.feature.prayertimes.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.starception.submission.islamic.qibla.presentation.component.QiblaGlobeView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// UI Constants
private const val DRAG_THRESHOLD_DP = 80
private const val EXIT_ANIMATION_DELAY_MS = 250L

/**
 * Helper function to dismiss popup with smooth exit animation
 */
private fun dismissWithAnimation(
    setVisible: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    coroutineScope: CoroutineScope
) {
    setVisible(false)
    coroutineScope.launch {
        delay(EXIT_ANIMATION_DELAY_MS)
        onDismiss()
    }
}

/**
 * Full-screen globe popup for interactive Qibla direction finding
 *
 * Shows the 3D globe in large size allowing user to interact and find Qibla direction.
 * Features pull-down to close gesture and smooth animations.
 *
 * @param userLatitude User's current latitude
 * @param userLongitude User's current longitude
 * @param onDismiss Callback when user closes the popup
 */
@Composable
fun GlobePopupScreen(
    userLatitude: Double,
    userLongitude: Double,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var dragState by remember { mutableFloatStateOf(0f) }
    val dragThreshold = with(density) { DRAG_THRESHOLD_DP.dp.toPx() }

    // Animation states
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0.97f else 0f,
        animationSpec = if (isVisible) {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        } else {
            tween(durationMillis = 250, easing = FastOutLinearInEasing)
        },
        label = "backgroundAlpha"
    )

    val dragProgress = (dragState / dragThreshold).coerceIn(0f, 1f)

    val contentScale by animateFloatAsState(
        targetValue = if (isVisible) (1f - dragProgress * 0.1f) else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "contentScale"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isVisible) (1f - dragProgress * 0.3f) else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "contentAlpha"
    )

    // Wrap in Dialog for proper lifecycle management of OpenGL surface
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha))
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // Globe container - NO graphicsLayer to ensure touch events work properly
            // AndroidView (WorldWind) requires unmodified touch coordinates
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 72.dp, bottom = 140.dp)  // Leave space for header and instructions
                    .padding(horizontal = 8.dp)  // Match tile content padding
            ) {
                // Background layer with animations (separate from touch-receiving globe)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = contentScale
                            scaleY = contentScale
                            alpha = contentAlpha
                        }
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(32.dp)
                        )
                )

                // Globe view - NO transforms, receives raw touch events
                QiblaGlobeView(
                    userLatitude = userLatitude,
                    userLongitude = userLongitude,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(32.dp))
                )
            }

            // Header overlay with drag handle and close button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        scaleX = contentScale
                        scaleY = contentScale
                        alpha = contentAlpha
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { dragState = 0f },
                            onDragEnd = {
                                if (dragState > dragThreshold) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dismissWithAnimation({ isVisible = it }, onDismiss, coroutineScope)
                                }
                                dragState = 0f
                            },
                            onDragCancel = { dragState = 0f },
                            onVerticalDrag = { _, dragAmount ->
                                if (dragAmount > 0) {
                                    dragState += dragAmount
                                }
                            }
                        )
                    }
                    .padding(16.dp)
            ) {
                // Drag handle indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(60.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.3f + dragProgress * 0.3f
                            )
                        )
                )

                // Close button
                IconButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        dismissWithAnimation({ isVisible = it }, onDismiss, coroutineScope)
                    },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Title
                Text(
                    text = "Qibla Direction",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 16.dp)
                )
            }

            // Instructions overlay at bottom
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 4.dp)
                    .padding(bottom = 24.dp)
                    .graphicsLayer {
                        scaleX = contentScale
                        scaleY = contentScale
                        alpha = contentAlpha
                    },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Interact with the globe",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pan, zoom, and rotate the globe.\nTap the location button to reset view.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
