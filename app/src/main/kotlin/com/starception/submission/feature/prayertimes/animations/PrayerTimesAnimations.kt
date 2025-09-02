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
 * Pull-to-refresh state management
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
 * Remember pull-to-refresh state
 */
@Composable
fun rememberPullToRefreshState(
    onRefresh: () -> Unit
): PullToRefreshState {
    var pullOffset by remember { mutableStateOf(0f) }
    var isPulling by remember { mutableStateOf(false) }
    
    // Smooth animation for pull offset with better release animation
    val animatedPullOffset by animateFloatAsState(
        targetValue = pullOffset,
        animationSpec = if (pullOffset == 0f) {
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
        } else {
            tween(durationMillis = 50, easing = LinearEasing)
        },
        label = "pullOffset"
    )
    
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
 * PROFESSIONAL Flowing arrows animation with enhanced visual feedback
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
            .padding(top = 8.dp, bottom = 16.dp), // Increased bottom padding to match tile spacing
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
                            isPulling && pullOffset > 70f -> "Release to Refresh"
                            isPulling -> "Keep Pulling..."
                            else -> "Pull to Update Location"
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
                            text = "Get fresh prayer times for your current GPS location",
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
 * PROFESSIONAL Refresh indicator with enhanced animations and visual feedback
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
                .offset(y = (pullOffset * 0.3f - 16).dp)
                .graphicsLayer {
                    alpha = indicatorAlpha
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center
        ) {
            // Professional background with consistent shadow effect
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = when {
                        isRefreshing -> 12.dp
                        canRelease -> 8.dp
                        isNearThreshold -> 6.dp
                        else -> 4.dp
                    }
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
                                contentDescription = if (canRelease) "Release to refresh" else "Pull to refresh",
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
                                canRelease -> "Release to Refresh"
                                isNearThreshold -> "Almost There..."
                                else -> "Pull to Refresh"
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
 * IMPROVED Pull-to-refresh gesture detector
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