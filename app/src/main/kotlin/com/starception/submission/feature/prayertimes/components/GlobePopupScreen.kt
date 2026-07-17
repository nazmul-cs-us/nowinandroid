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
import com.starception.submission.core.designsystem.animation.NiaMotion
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
        targetValue = if (isVisible) 0.55f else 0f,
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

    // The whole card slides up into place. The GL globe is a SurfaceView, which can't be
    // alpha-composited — position transforms move its punched hole, alpha would not — so
    // the card animates position while the inner chrome fades via contentAlpha.
    val cardSlide by animateFloatAsState(
        targetValue = if (isVisible) 0f else 1f,
        animationSpec = if (isVisible) {
            NiaMotion.spatialDefault()
        } else {
            NiaMotion.exitTween(NiaMotion.Duration.SHORT_4)
        },
        label = "cardSlide"
    )

    // Distance to Kaaba (great-circle) for the bottom status pill
    val distanceKm = remember(userLatitude, userLongitude) {
        val lat1 = Math.toRadians(userLatitude)
        val lat2 = Math.toRadians(MAKKAH_LAT)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(MAKKAH_LON - userLongitude)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
                kotlin.math.sin(dLon / 2).let { it * it }
        val c = 2.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        (EARTH_RADIUS_KM * c).toInt()
    }

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
            // Floating modal card — vertically centered with equal dim above and
            // below, horizontal padding so it doesn't touch the screen edges, and
            // all four corners rounded so it reads as a card hovering over the
            // dimmed background. Pull-down-to-dismiss still works.
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .graphicsLayer { translationY = cardSlide * 48.dp.toPx() },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
            // Globe container — fills the sheet area minus insets for the drag
            // handle + close button (top) and status pill (bottom). Top inset is
            // generous enough that the globe's own "LEFT / RIGHT N°" angle badge
            // sits clear of the close button at the sheet's top-right corner.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 60.dp, bottom = 84.dp)
                    .padding(horizontal = 12.dp)
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
                            shape = RoundedCornerShape(28.dp)
                        )
                )

                // Globe view - NO transforms, receives raw touch events
                QiblaGlobeView(
                    userLatitude = userLatitude,
                    userLongitude = userLongitude,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                )
            }

            // Slim top chrome: drag handle (centered) + glass close button (top-right).
            // No "Qibla Direction" title — the globe IS the title.
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
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Slim drag handle indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.25f + dragProgress * 0.35f
                            )
                        )
                )

                // Glass-style close button floating in top-right
                Surface(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        dismissWithAnimation({ isVisible = it }, onDismiss, coroutineScope)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    tonalElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Compact bottom status pill — distance + swipe-down hint.
            // No more "Pan, zoom, and rotate the globe" lecture; users discover gestures.
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .graphicsLayer {
                        scaleX = contentScale
                        scaleY = contentScale
                        alpha = contentAlpha
                    },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = "Makkah",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                    )
                    Text(
                        text = "${formatDistance(distanceKm)} km",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
                }  // close inner sheet Box
            }  // close Surface sheet container
        }
    }
}

private const val MAKKAH_LAT = 21.4225
private const val MAKKAH_LON = 39.8262
private const val EARTH_RADIUS_KM = 6371.0

private fun formatDistance(km: Int): String =
    if (km >= 1000) "%,d".format(km) else km.toString()
