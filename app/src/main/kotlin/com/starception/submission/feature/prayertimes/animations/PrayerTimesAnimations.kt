package com.starception.submission.feature.prayertimes.animations

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pull-to-refresh state management data class
 * 
 * This holds all the state needed to manage pull-to-refresh gestures and animations.
 * Use this state to track drag distance, animation progress, and trigger refresh actions.
 * 
 * @param pullOffset Current pull distance in dp (0f = no pull, 150f = max pull)
 * @param isPulling True when user is actively dragging downward
 * @param animatedPullOffset Smoothly animated version of pullOffset for fluid animations
 * @param onRefresh Callback function triggered when refresh threshold is reached (80dp)
 * @param setPullOffset Internal function to update pull distance - called by gesture detector
 * @param setIsPulling Internal function to update pulling state - called by gesture detector
 * 
 * DEBUG TIPS:
 * - Monitor pullOffset values: 0-80 = pulling, 80+ = ready to refresh
 * - Check isPulling state for gesture tracking issues
 * - animatedPullOffset should smoothly follow pullOffset changes
 */
@Stable
data class PullToRefreshState(
    val pullOffset: Float,
    val isPulling: Boolean,
    val animatedPullOffset: Float,
    val onRefresh: () -> Unit,
    val setPullOffset: (Float) -> Unit,
    val setIsPulling: (Boolean) -> Unit
)

/**
 * Creates and remembers pull-to-refresh state for gesture handling
 * 
 * This composable function manages the internal state of pull-to-refresh gestures
 * and provides smooth animations for visual feedback.
 * 
 * @param onRefresh Callback function invoked when refresh is triggered (pull > 80dp)
 * @return PullToRefreshState object containing all pull-to-refresh state and controls
 * 
 * ANIMATION BEHAVIOR:
 * - During pull: Fast tracking (50ms tween) for immediate visual feedback
 * - On release: Smooth spring animation (0.8 damping) for professional feel
 * 
 * DEBUG CHECKLIST:
 * - Verify onRefresh callback is called when pullOffset > 80dp
 * - Check animation smoothness during pull and release
 * - Monitor state recomposition frequency (should be minimal)
 * - Ensure state persists across recompositions using remember()
 */
@Composable
fun rememberPullToRefreshState(
    onRefresh: () -> Unit
): PullToRefreshState {
    // Core state values - persist across recompositions
    var pullOffset by remember { mutableStateOf(0f) }
    var isPulling by remember { mutableStateOf(false) }
    
    // Smooth animation for pull offset with context-aware animation specs
    val animatedPullOffset by animateFloatAsState(
        targetValue = pullOffset,
        animationSpec = if (pullOffset == 0f) {
            // Release animation: Smooth spring for professional feel
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
        } else {
            // Pull animation: Fast tracking for immediate response
            tween(durationMillis = 50, easing = LinearEasing)
        },
        label = "pullOffset"
    )
    
    // Return remembered state object to prevent unnecessary recreations
    return remember(onRefresh) {
        PullToRefreshState(
            pullOffset = pullOffset,
            isPulling = isPulling,
            animatedPullOffset = animatedPullOffset,
            onRefresh = onRefresh,
            setPullOffset = { pullOffset = it },
            setIsPulling = { isPulling = it }
        )
    }
}

/**
 * Professional flowing arrows animation for pull-to-refresh visual feedback
 * 
 * Creates a sophisticated "rain effect" animation with continuously flowing arrows
 * that guide users to pull down for refresh. Includes dynamic text feedback
 * based on pull progress and refreshing state.
 * 
 * @param isPulling True when user is actively dragging downward
 * @param pullOffset Current pull distance (0f to 150f) - affects text messaging
 * @param isRefreshing True when refresh operation is in progress
 * 
 * ANIMATION MECHANICS:
 * - Two arrows with 1-second phase offset create continuous flow
 * - 4-second master cycle prevents repetitive visual patterns
 * - Mathematical positioning using sine waves for smooth motion
 * - Arrows fade in/out at edges (15% fade zones) for seamless effect
 * 
 * TEXT STATES:
 * - Default: "Pull to Update Location" + description
 * - Pulling (0-70dp): "Keep Pulling..."
 * - Ready (70dp+): "Release to Refresh"
 * - Refreshing: Animation hidden, replaced by loading indicator
 * 
 * DEBUG POINTS:
 * - Monitor animationTime for smooth 0-4000ms cycling
 * - Check arrow positions: should range from -18dp to +18dp
 * - Verify fade calculations at position extremes
 * - Test text transitions at 70dp threshold
 */
@Composable
fun FlowingArrowsAnimation(
    isPulling: Boolean,
    pullOffset: Float,
    isRefreshing: Boolean
) {
    // Enhanced animation parameters for more professional feel
    val infiniteTransition = rememberInfiniteTransition(label = "arrowHint")
    
    // True continuous rain effect - no jumps or restarts
    val animationTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4000f, // 4 second full cycle
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainTime"
    )
    
    // Calculate arrow positions using continuous sine wave for perfect rain effect
    val arrow1 = ((animationTime % 2000f) / 2000f * 36f) - 18f // Full range every 2 seconds
    val arrow2 = (((animationTime + 1000f) % 2000f) / 2000f * 36f) - 18f // 1 second offset
    
    // Smooth fade animation for text
    val textAlpha by animateFloatAsState(
        targetValue = if (!isPulling && !isRefreshing) 1f else 0.6f,
        animationSpec = tween(durationMillis = 300),
        label = "textAlpha"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 24.dp), // Increased padding to prevent overlap
        contentAlignment = Alignment.Center
    ) {
        if (!isRefreshing) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .graphicsLayer { alpha = textAlpha }
            ) {
                if (!isPulling && pullOffset == 0f) {
                    // Professional flowing arrow stack on the left - height matches text
                    Box(
                        modifier = Modifier.width(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Smooth rain effect with continuous arrow flow
                        listOf(
                            Triple(arrow1, 1f, 20.dp),
                            Triple(arrow2, 0.8f, 18.dp)
                        ).forEach { (position, maxAlpha, size) ->
                            val normalizedPosition = position / 18f // Normalize to -1 to 1 based on new range
                            
                            // Smooth continuous visibility - no harsh cutoffs
                            val visibilityAlpha = when {
                                kotlin.math.abs(normalizedPosition) > 0.85f -> {
                                    // Gentle fade at edges (15% of range) 
                                    val fadeRange = (kotlin.math.abs(normalizedPosition) - 0.85f) / 0.15f
                                    (1f - fadeRange * 0.6f).coerceIn(0.4f, 1f)
                                }
                                else -> 1f // Full visibility in center 85%
                            }
                            
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Pull down hint",
                                tint = MaterialTheme.colorScheme.primary.copy(
                                    alpha = (visibilityAlpha * maxAlpha).coerceIn(0.4f, maxAlpha)
                                ),
                                modifier = Modifier
                                    .size(size)
                                    .offset(y = position.dp)
                                    .graphicsLayer {
                                        val centerScale = 0.95f + 0.05f * (1f - kotlin.math.abs(normalizedPosition))
                                        scaleX = centerScale
                                        scaleY = centerScale
                                    }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when {
                            isPulling && pullOffset > 70f -> "Release to update prayer times"
                            isPulling -> "Keep pulling..."
                            else -> "Pull to update prayer times"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.1.sp
                        ),
                        color = when {
                            isPulling && pullOffset > 70f -> MaterialTheme.colorScheme.primary
                            isPulling -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        },
                        textAlign = TextAlign.Center
                    )
                    
                    if (!isPulling) {
                        Text(
                            text = "Get updated prayer times for your current location",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Professional refresh indicator with enhanced animations and visual feedback
 * 
 * Displays a sophisticated Material 3 card-based indicator that responds to pull gestures
 * with smooth animations, dynamic content, and contextual visual feedback.
 * 
 * @param isRefreshing True when refresh operation is active (shows loading spinner)
 * @param pullOffset Current pull distance in dp (0-150) - controls visibility and positioning
 * @param modifier Optional Modifier for customization
 * 
 * VISIBILITY THRESHOLDS:
 * - pullOffset 0-20dp: Indicator hidden (alpha = 0f)
 * - pullOffset 20-60dp: Gradual fade in (alpha = 0.3f to 1f)
 * - pullOffset 60dp+: Full visibility (alpha = 1f)
 * - pullOffset 70dp+: "Almost There..." state
 * - pullOffset 80dp+: "Release to Refresh" state with icon rotation
 * 
 * ANIMATION STATES:
 * - Static: Shows refresh icon with "Pull to Refresh" text
 * - Pulling: Progress bar fills based on pullOffset/80dp ratio
 * - Near threshold: "Almost There..." with elevated card
 * - Ready: Rotated arrow icon + "Release to Refresh" + primary colors
 * - Refreshing: Spinning indicator + "Updating Prayer Times" + detailed status
 * 
 * VISUAL FEATURES:
 * - Dynamic card elevation (1-6dp based on state)
 * - Smooth icon rotation animation (0° to 180°)
 * - Progress bar tracking pull distance
 * - No shadow (elevation = 0dp) for clean appearance
 * 
 * DEBUG CHECKLIST:
 * - Verify visibility transitions at 20dp, 60dp thresholds
 * - Check progress bar fills correctly (pullOffset/80dp)
 * - Monitor icon rotation at 80dp threshold
 * - Test card elevation changes during different states
 * - Ensure text updates match pullOffset ranges
 */
@Composable
fun RefreshIndicator(
    isRefreshing: Boolean,
    pullOffset: Float = 0f,
    modifier: Modifier = Modifier
) {
    // Enhanced visibility thresholds for better UX
    val shouldShow = isRefreshing || pullOffset > 20f
    val isNearThreshold = pullOffset > 70f
    val canRelease = pullOffset > 80f
    
    // Smooth animations for professional feel with better transitions
    val indicatorAlpha by animateFloatAsState(
        targetValue = when {
            isRefreshing -> 1f
            pullOffset > 60f -> 1f
            pullOffset > 20f -> 0.3f + (pullOffset - 20f) / 40f * 0.7f
            else -> 0f
        },
        animationSpec = spring(
            dampingRatio = if (isRefreshing) 0.7f else 0.8f, 
            stiffness = if (isRefreshing) Spring.StiffnessLow else Spring.StiffnessMedium
        ),
        label = "indicatorAlpha"
    )
    
    val iconRotation by animateFloatAsState(
        targetValue = if (canRelease) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = EaseInOutQuart),
        label = "iconRotation"
    )
    
    val progressAlpha = (pullOffset / 80f).coerceIn(0f, 1f)
    val scale by animateFloatAsState(
        targetValue = when {
            isRefreshing -> 1f
            shouldShow -> 0.85f + 0.15f * progressAlpha
            else -> 0f
        },
        animationSpec = spring(
            dampingRatio = if (isRefreshing) 0.6f else 0.75f, 
            stiffness = if (isRefreshing) Spring.StiffnessLow else Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    
    if (indicatorAlpha > 0.01f || scale > 0.01f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .offset(y = (pullOffset * 0.4f - 32).dp)
                .graphicsLayer {
                    alpha = indicatorAlpha
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center
        ) {
            // Professional background with no shadow
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 0.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Dynamic icon with rotation and color changes
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                color = if (canRelease) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRefreshing) {
                            // Professional loading indicator
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (canRelease) Icons.Default.KeyboardArrowDown else Icons.Default.Refresh,
                                contentDescription = if (canRelease) "Release to update prayer times" else "Pull to update prayer times",
                                tint = if (canRelease) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer {
                                        rotationZ = if (canRelease) iconRotation else 0f
                                    }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Enhanced text with better typography and status indication
                    Column(
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = when {
                                isRefreshing -> "Updating Prayer Times"
                                canRelease -> "Release to update prayer times"
                                isNearThreshold -> "Almost There..."
                                else -> "Pull to update prayer times"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (canRelease) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        
                        if (isRefreshing) {
                            Text(
                                text = "Accessing your location & calculating fresh prayer times...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (canRelease) {
                            Text(
                                text = "Let go to update",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                
                // Professional progress bar
                if (!isRefreshing && shouldShow) {
                    LinearProgressIndicator(
                        progress = { progressAlpha },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = if (canRelease) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Loading state composable
 */
@Composable
fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
                strokeWidth = 4.dp
            )
            Text(
                text = "Live Updates Active",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Advanced pull-to-refresh gesture detector with resistance and threshold logic
 * 
 * This modifier function implements sophisticated touch gesture recognition
 * for pull-to-refresh functionality with resistance effects and threshold-based triggering.
 * 
 * @param pullToRefreshState State object containing gesture data and callbacks
 * @return Modified Modifier with gesture detection capabilities
 * 
 * GESTURE MECHANICS:
 * - Detects vertical drag gestures using detectDragGestures
 * - Applies 0.6f resistance factor (reduces perceived sensitivity)
 * - Maximum pull distance: 150dp (prevents excessive stretching)
 * - Refresh threshold: 80dp (triggers onRefresh callback)
 * - Only responds to downward drags (ignores upward gestures)
 * 
 * STATE MANAGEMENT:
 * - onDragStart: Resets totalDragDistance, sets isPulling = true
 * - onDrag: Updates pullOffset with resistance calculation
 * - onDragEnd: Triggers refresh if threshold met, resets all state
 * 
 * RESISTANCE CALCULATION:
 * totalDragDistance * resistance (0.6f) = visual pull distance
 * Example: 100dp actual drag = 60dp visual pull
 * 
 * THRESHOLD LOGIC:
 * - pullOffset > 80dp: Triggers refresh and calls onRefresh()
 * - pullOffset <= 80dp: Animation returns to rest position
 * 
 * DEBUG LOGGING:
 * - "PullToRefresh" tag tracks all gesture events
 * - Logs drag start, drag progress with distances, drag end with decisions
 * - Monitor totalDragDistance vs newOffset for resistance verification
 * 
 * COMMON DEBUG ISSUES:
 * - Gesture not detected: Check for conflicting scroll modifiers
 * - No refresh trigger: Verify pullOffset exceeds 80dp threshold
 * - Jerky animations: Check resistance calculation and state updates
 * - State not resetting: Ensure onDragEnd resets all values
 */
fun Modifier.pullToRefreshGesture(
    pullToRefreshState: PullToRefreshState
): Modifier = this.pointerInput(Unit) {
    var totalDragDistance = 0f
    
    detectDragGestures(
        onDragStart = { _ ->
            totalDragDistance = 0f
            pullToRefreshState.setIsPulling(true)
            android.util.Log.d("PullToRefresh", "Drag started")
        },
        onDragEnd = {
            val currentOffset = pullToRefreshState.pullOffset
            android.util.Log.d("PullToRefresh", "Drag ended - pullOffset: $currentOffset")
            
            // Trigger refresh if pulled enough (80dp threshold)
            if (currentOffset > 80f) {
                android.util.Log.d("PullToRefresh", "Triggering refresh!")
                pullToRefreshState.onRefresh()
            }
            
            // Reset state
            pullToRefreshState.setPullOffset(0f)
            pullToRefreshState.setIsPulling(false)
            totalDragDistance = 0f
        },
        onDrag = { change, _ ->
            val dragY = change.position.y
            totalDragDistance += dragY
            
            // Only respond to downward drags
            if (totalDragDistance > 0) {
                // Apply resistance effect: more resistance as you pull further
                val resistance = 0.6f
                val maxPull = 150f
                
                val adjustedDistance = totalDragDistance * resistance
                val newOffset = adjustedDistance.coerceIn(0f, maxPull)
                
                pullToRefreshState.setPullOffset(newOffset)
                android.util.Log.d("PullToRefresh", "Dragging: totalDistance=$totalDragDistance, newOffset=$newOffset")
            } else {
                // Reset if dragging upward
                pullToRefreshState.setPullOffset(0f)
            }
            
            // Consume the drag gesture
            change.consume()
        }
    )
}