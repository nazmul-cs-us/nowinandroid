package com.starception.submission.feature.prayertimes.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.util.Log
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.*
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.horizontalScroll
import com.starception.submission.prayer.service.EnhancedLocationService
import com.starception.submission.islamic.qibla.presentation.component.QiblaGlobeView
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

// UI Constants
private const val DRAG_THRESHOLD_DP = 60
private const val EXIT_ANIMATION_DELAY_MS = 250L
private const val COMPASS_SIZE_DP = 260
private const val HANDLE_WIDTH_DP = 60
private const val HANDLE_HEIGHT_DP = 6
private const val DRAG_AREA_WIDTH_DP = 80
private const val DRAG_AREA_HEIGHT_DP = 20

/**
 * Helper function to dismiss popup with smooth exit animation
 */
private fun dismissWithAnimation(
    setVisible: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    coroutineScope: CoroutineScope
) {
    setVisible(false) // Trigger exit animation
    coroutineScope.launch {
        delay(EXIT_ANIMATION_DELAY_MS)
        onDismiss()
    }
}

/**
 * Full-screen compass popup with calibration guidance
 *
 * Shows the compass in large size with Islamic theming and sensor calibration instructions.
 * Automatically appears when sensor accuracy is poor or when user taps the compass tile.
 *
 * ## Features:
 * - **Large Compass Display**: 280dp compass for better visibility and interaction
 * - **Calibration Guidance**: Step-by-step instructions for improving sensor accuracy
 * - **Islamic Theming**: Traditional green colors with Kaaba emoji
 * - **Real-time Feedback**: Visual indicators showing sensor status improvements
 * - **Prayer Integration**: Shows current prayer time countdown within the compass
 * - **Pull-to-Close Gesture**: Drag down 60dp to close the popup naturally
 *
 * @param progress Current prayer time progress (0-1)
 * @param locationService Enhanced location service for GPS and Qibla calculations
 * @param onDismiss Callback when user closes the popup
 */
@Composable
fun CompassPopupScreen(
    progress: Float,
    locationService: EnhancedLocationService?,
    onDismiss: () -> Unit,
    userLatitude: Double = 0.0,
    userLongitude: Double = 0.0,
    showGlobe: Boolean = false
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var dragState by remember { mutableFloatStateOf(0f) }
    val dragThreshold = with(density) { DRAG_THRESHOLD_DP.dp.toPx() }
    
    // Material 3 expressive entrance animations
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0.95f else 0f,
        animationSpec = if (isVisible) {
            // Entrance: Smooth and bouncy
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        } else {
            // Exit: Fast and smooth
            tween(
                durationMillis = 250,
                easing = FastOutLinearInEasing
            )
        },
        label = "backgroundAlpha"
    )
    
    // Visual feedback for drag gesture
    val dragProgress = (dragState / dragThreshold).coerceIn(0f, 1f)
    
    val contentScale by animateFloatAsState(
        targetValue = if (isVisible) (1f - dragProgress * 0.1f) else 0.85f,
        animationSpec = if (isVisible) {
            // Entrance: Bouncy spring
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        } else {
            // Exit: Quick scale down with slight overshoot
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            )
        },
        label = "contentScale"
    )
    
    val contentAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = if (isVisible) {
            // Entrance: Smooth fade in
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
                visibilityThreshold = 0.01f
            )
        } else {
            // Exit: Quick fade out
            tween(
                durationMillis = 200,
                easing = FastOutLinearInEasing
            )
        },
        label = "contentAlpha"
    )
    
    val handleScale by animateFloatAsState(
        targetValue = 1f + (dragProgress * 0.2f), // Slightly grow handle during drag
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "handleScale"
    )
    
    Log.d("CompassPopup", "CompassPopupScreen created with onDismiss: $onDismiss")
    Dialog(
        onDismissRequest = { 
            Log.d("CompassPopup", "Dialog onDismissRequest triggered")
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha))
                .windowInsetsPadding(WindowInsets.systemBars) // Handle camera cutouts and system bars
                .graphicsLayer {
                    scaleX = contentScale
                    scaleY = contentScale
                    alpha = contentAlpha
                }
        ) {
            // Material 3 expressive close button with enhanced feedback
            var isCloseButtonPressed by remember { mutableStateOf(false) }
            
            val closeButtonScale by animateFloatAsState(
                targetValue = if (isCloseButtonPressed) 0.9f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                ),
                label = "closeButtonScale"
            )
            
            val closeButtonAlpha by animateFloatAsState(
                targetValue = if (isVisible) 1f else 0f,
                animationSpec = if (isVisible) {
                    // Entrance: Delayed smooth appearance
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                        visibilityThreshold = 0.01f
                    )
                } else {
                    // Exit: Immediate fade
                    tween(
                        durationMillis = 150,
                        easing = FastOutLinearInEasing
                    )
                },
                label = "closeButtonAlpha"
            )
            
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
                    .zIndex(100f)
                    .graphicsLayer {
                        scaleX = closeButtonScale
                        scaleY = closeButtonScale
                        alpha = closeButtonAlpha
                    }
            ) {
                IconButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        Log.d("CompassPopup", "Close button clicked!")
                        dismissWithAnimation(
                            setVisible = { isVisible = it },
                            onDismiss = onDismiss,
                            coroutineScope = coroutineScope
                        )
                        Log.d("CompassPopup", "Exit animation started")
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { 
                                    isCloseButtonPressed = true
                                    tryAwaitRelease()
                                    isCloseButtonPressed = false
                                }
                            )
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Detect orientation for adaptive layout
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            // Dynamic compass size based on orientation
            val compassSizeDp = if (isLandscape) 180.dp else COMPASS_SIZE_DP.dp

            if (isLandscape) {
                // LANDSCAPE LAYOUT: Side-by-side compass and calibration
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left side: Compass
                    Column(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Title
                        Text(
                            text = "🕋 Qibla Compass",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Compass with globe
                        CompassProgressIndicator(
                            progress = progress,
                            size = compassSizeDp,
                            locationService = locationService,
                            userLatitude = userLatitude,
                            userLongitude = userLongitude,
                            showGlobe = showGlobe,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Islamic context note
                        Text(
                            text = "Points toward Kaaba in Mecca",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Right side: Calibration guidance (scrollable)
                    Column(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        // Calibration guidance card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Animated calibration demonstration (smaller in landscape)
                                Figure8AnimationDemo(
                                    modifier = Modifier
                                        .size(70.dp)
                                        .padding(bottom = 4.dp)
                                )

                                Text(
                                    text = "For Better Accuracy",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                Text(
                                    text = "Move device in a figure-8 pattern for better accuracy.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                // Steps (compact)
                                CalibrationStep(step = "1", instruction = "Hold phone flat")
                                CalibrationStep(step = "2", instruction = "Move in figure-8 motions")
                                CalibrationStep(step = "3", instruction = "Watch border turn green")

                                Spacer(modifier = Modifier.height(8.dp))

                                // Color guide (compact)
                                Text(
                                    text = "Color Guide:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                ColorIndicatorRow(color = Color(0xFF10B981), text = "Green = Good")
                                ColorIndicatorRow(color = Color(0xFFFFA500), text = "Orange = Fair")
                                ColorIndicatorRow(color = Color(0xFFFF4444), text = "Red = Poor")
                            }
                        }
                    }
                }
            } else {
            // PORTRAIT LAYOUT: Original vertical layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp), // More bottom padding
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // Pull-down handle and title area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // More prominent pull-down handle indicator with drag gesture
                        Box(
                            modifier = Modifier
                                .width(DRAG_AREA_WIDTH_DP.dp)
                                .height(DRAG_AREA_HEIGHT_DP.dp)
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragEnd = {
                                            // If user dragged down far enough, close with animation
                                            if (dragState > dragThreshold) {
                                                Log.d("CompassPopup", "Pull-down gesture detected (${dragState}px > ${dragThreshold}px), closing popup")
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                dismissWithAnimation(
                                                    setVisible = { isVisible = it },
                                                    onDismiss = onDismiss,
                                                    coroutineScope = coroutineScope
                                                )
                                            }
                                            // Reset drag state
                                            dragState = 0f
                                        }
                                    ) { _, dragAmount ->
                                        // Track drags with gradual decay for upward movements
                                        if (dragAmount > 0) {
                                            dragState = maxOf(0f, dragState + dragAmount)
                                            Log.d("CompassPopup", "Drag down: ${dragAmount}px, total: ${dragState}px")
                                        } else {
                                            // Gradual decay for upward drags (feels more natural)
                                            dragState = maxOf(0f, dragState + dragAmount * 0.5f)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Visual handle bar with animation feedback
                            Box(
                                modifier = Modifier
                                    .width(HANDLE_WIDTH_DP.dp)
                                    .height(HANDLE_HEIGHT_DP.dp)
                                    .scale(handleScale) // Animated scale feedback
                                    .clip(RoundedCornerShape((HANDLE_HEIGHT_DP / 2).dp))
                                    .background(
                                        Color.White.copy(
                                            alpha = 0.8f + (dragProgress * 0.2f) // Brightens during drag
                                        )
                                    )
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Pull instruction text
                        Text(
                            text = "Pull down to close",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Title
                        Text(
                            text = "🕋 Qibla Compass",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }

                // Material 3 expressive large compass with enhanced entrance
                val compassRotation by animateFloatAsState(
                    targetValue = if (isVisible) 0f else -15f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "compassRotation"
                )
                
                val compassScale by animateFloatAsState(
                    targetValue = if (isVisible) 1f else 0.7f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "compassScale"
                )
                
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = compassScale
                            scaleY = compassScale
                            rotationZ = compassRotation
                        }
                ) {
                    CompassProgressIndicator(
                        progress = progress,
                        size = COMPASS_SIZE_DP.dp,
                        locationService = locationService,
                        userLatitude = userLatitude,
                        userLongitude = userLongitude,
                        showGlobe = showGlobe,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Calibration guidance card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Animated calibration demonstration
                        Figure8AnimationDemo(
                            modifier = Modifier
                                .size(100.dp)
                                .padding(bottom = 6.dp)
                        )

                        Text(
                            text = "For Better Accuracy",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Text(
                            text = "Move your device in a figure-8 pattern to improve compass accuracy. Keep the phone flat and make smooth, continuous motions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Steps
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CalibrationStep(
                                step = "1",
                                instruction = "Hold phone flat in your palm"
                            )
                            CalibrationStep(
                                step = "2", 
                                instruction = "Move in smooth figure-8 motions"
                            )
                            CalibrationStep(
                                step = "3",
                                instruction = "Watch the compass border turn green"
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Color meaning explanation
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Color Guide:",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // Green - Good accuracy
                            ColorIndicatorRow(
                                color = Color(0xFF10B981),
                                text = "Green = Good accuracy, reliable direction"
                            )
                            
                            // Orange - Medium accuracy
                            ColorIndicatorRow(
                                color = Color(0xFFFFA500),
                                text = "Orange = Fair accuracy, mostly reliable"
                            )
                            
                            // Red - Poor accuracy
                            ColorIndicatorRow(
                                color = Color(0xFFFF4444),
                                text = "Red = Poor accuracy, needs calibration"
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Status indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Compass will update automatically",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Islamic context note
                Text(
                    text = "The green arc points toward the Kaaba in Mecca, Saudi Arabia",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp) // Added bottom padding
                )
            }
            } // End of portrait layout else block
        }
    }
}

@Composable
private fun CalibrationStep(
    step: String,
    instruction: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Step number
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color(0xFF10B981)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = instruction,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
        )
    }
}

@Composable
private fun ColorIndicatorRow(
    color: Color,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color indicator circle
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            fontSize = 12.sp
        )
    }
}

/**
 * Simple and subtle figure-8 calibration animation
 * Clean demonstration showing phone movement in figure-8 pattern
 */
@Composable
private fun Figure8AnimationDemo(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "figure8Animation")
    
    // Simple smooth animation progress
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "figure8Progress"
    )
    
    Canvas(modifier = modifier) {
        val center = size.center
        val radius = size.minDimension / 2.5f
        
        // Simple figure-8 path
        val path = Path().apply {
            reset()
            for (i in 0..360 step 3) {
                val t = i * PI / 180.0
                val x = center.x + radius * sin(t)
                val y = center.y + radius * sin(t) * cos(t)
                if (i == 0) moveTo(x.toFloat(), y.toFloat())
                else lineTo(x.toFloat(), y.toFloat())
            }
        }
        
        // Simple dashed path
        drawPath(
            path = path,
            color = Color(0xFF10B981).copy(alpha = 0.4f),
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
        )
        
        // Calculate current position
        val t = progress * 2 * PI
        val currentX = center.x + radius * sin(t).toFloat()
        val currentY = center.y + radius * sin(t).toFloat() * cos(t).toFloat()
        
        // Calculate direction for phone alignment
        val nextT = ((progress + 0.01f) % 1f) * 2 * PI
        val nextX = center.x + radius * sin(nextT).toFloat()
        val nextY = center.y + radius * sin(nextT).toFloat() * cos(nextT).toFloat()
        
        val dx = nextX - currentX
        val dy = nextY - currentY
        val angle = atan2(dy, dx)
        
        // Phone aligned with movement direction
        val phoneWidth = 8.dp.toPx()
        val phoneHeight = 12.dp.toPx()
        
        rotate(
            degrees = Math.toDegrees(angle.toDouble()).toFloat(),
            pivot = androidx.compose.ui.geometry.Offset(currentX, currentY)
        ) {
            // Phone body
            drawRoundRect(
                color = Color(0xFF10B981),
                topLeft = androidx.compose.ui.geometry.Offset(
                    currentX - phoneWidth / 2,
                    currentY - phoneHeight / 2
                ),
                size = androidx.compose.ui.geometry.Size(phoneWidth, phoneHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
            
            // Phone screen
            drawRoundRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(
                    currentX - phoneWidth / 2 + 1.dp.toPx(),
                    currentY - phoneHeight / 2 + 1.5.dp.toPx()
                ),
                size = androidx.compose.ui.geometry.Size(
                    phoneWidth - 2.dp.toPx(),
                    phoneHeight - 3.dp.toPx()
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
            )
        }
        
        // Center circle removed for cleaner look
    }
}