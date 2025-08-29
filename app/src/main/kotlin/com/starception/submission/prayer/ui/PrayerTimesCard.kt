package com.starception.submission.prayer.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.animation.DecelerateInterpolator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import com.starception.submission.core.designsystem.theme.NiaTheme
import com.starception.submission.prayer.model.CalculationMethod
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.Location
import com.starception.submission.prayer.model.PrayerTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Pull-to-refresh constants matching ResistRoute exactly
private const val REFRESH_THRESHOLD = 100f
private const val MAX_DRAG_OFFSET = 350f
private const val DRAG_BUFFER = 5f
private const val ANIMATION_DURATION_MS = 1000

// ResistRoute visual constants - exact same values
private val START_SIZE = 64.dp
private val START_STROKE_WIDTH = 8.dp
private const val START_ROTATION = 90f
private val START_Y_OFFSET = 0.dp

private val TARGET_SIZE = 128.dp
private val TARGET_Y_OFFSET = 150.dp
private val TARGET_STROKE_WIDTH = 16.dp
private const val TARGET_ROTATION = START_ROTATION + 360f

/**
 * Clean, elegant Prayer times display card component with Material 3 design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesCard(
    prayerTimes: DayPrayerTimes,
    timeUntilNext: String? = null,
    calculationMethod: CalculationMethod? = null,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onRefreshButton: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {}, // New parameter for permission request
    modifier: Modifier = Modifier
) {
    // Pull-to-refresh state exactly like ResistRoute
    val context = LocalContext.current
    var dragOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    // Animation to return the indicator to the original position where dragOffset is zero - exactly like ResistRoute
    val returnAnimation = animateFloatAsState(
        targetValue = if (isDragging) dragOffset else 0f,
        animationSpec = if (isDragging) tween(0) else tween(800) // Faster return animation
    )
    
    // Update drag offset based on return animation when not dragging - exactly like ResistRoute
    if (!isDragging) {
        LaunchedEffect(returnAnimation.value) {
            dragOffset = returnAnimation.value
        }
    }
    
    // Continuous haptic feedback during drag like ResistRoute
    if (isDragging && dragOffset > 10f) {
        val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
        val dragProgress = (dragOffset / REFRESH_THRESHOLD).coerceIn(0f, 1f)
        
        // Calculate vibration parameters based on drag progress like ResistRoute
        val interval = (60f - (30f * dragProgress)).toLong() // 60ms to 30ms
        val intensity = (0.2f + (0.6f * dragProgress)) // 0.2 to 0.8
        
        LaunchedEffect(Unit) {
            while (isDragging) {
                delay(interval)
                try {
                    vibrator?.let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val effectId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                            } else {
                                VibrationEffect.Composition.PRIMITIVE_TICK
                            }
                            it.vibrate(
                                VibrationEffect.startComposition()
                                    .addPrimitive(effectId, intensity)
                                    .compose()
                            )
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            it.vibrate(VibrationEffect.createOneShot(20, (intensity * 255).toInt()))
                        } else {
                            @Suppress("DEPRECATION")
                            it.vibrate(20)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore vibration errors
                }
            }
        }
    }
    
    Card(
        modifier = modifier
            .draggable(
                orientation = Orientation.Vertical,
                onDragStarted = {
                    isDragging = true
                },
                onDragStopped = {
                    val shouldRefresh = dragOffset >= REFRESH_THRESHOLD
                    
                    // Stop dragging - animation will start automatically
                    isDragging = false
                    
                    // Trigger refresh if threshold was reached
                    if (shouldRefresh) {
                        onRefresh()
                    }
                },
                state = rememberDraggableState { delta ->
                    if (delta > 0) { // Only allow downward drag
                        // Apply deceleration resistance like ResistRoute with smoother resistance
                        val newOffset = dragOffset + delta
                        val resistance = DecelerateInterpolator().getInterpolation(
                            (newOffset / MAX_DRAG_OFFSET).coerceIn(0f, 1f)
                        )
                        dragOffset = (dragOffset + delta * (1f - resistance * 0.9f)).coerceAtMost(MAX_DRAG_OFFSET)
                    }
                }
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .offset(y = (dragOffset * 0.3f).dp), // Less content movement for smoother feel
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Always show refresh indicator at top like ResistRoute
            ResistStyleIndicator(
                isRefreshing = isRefreshing,
                dragOffset = dragOffset
            )
            
            // Dynamic spacer that accounts for indicator movement to prevent overlap
            val progress = if (dragOffset > DRAG_BUFFER) {
                DecelerateInterpolator().getInterpolation((dragOffset / MAX_DRAG_OFFSET).coerceIn(0f, 1f))
            } else {
                0f
            }
            val dynamicSpacing = 24.dp + (progress * 60f).dp // Grows from 24dp to 84dp
            Spacer(modifier = Modifier.height(dynamicSpacing))
            
            // Clean, elegant prayer content
            Column {
                // Simple, clean header
                CleanPrayerHeader(
                    location = prayerTimes.location,
                    nextPrayer = prayerTimes.getNextPrayer(),
                    timeUntilNext = timeUntilNext,
                    calculationMethod = calculationMethod
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Clean prayer times list
                CleanPrayerTimesList(prayers = prayerTimes.getAllPrayers())
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Simple refresh button
                OutlinedButton(
                    onClick = onRefreshButton,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh")
                }
            }
        }
    }
}

@Composable
private fun CleanPrayerHeader(
    location: Location,
    nextPrayer: PrayerTime?,
    timeUntilNext: String?,
    calculationMethod: CalculationMethod?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Location info - simple and clean
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Location",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = location.getDisplayName(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        // Calculation method - subtle
        calculationMethod?.let { method ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Using ${method.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Next prayer info - clean and prominent
        if (timeUntilNext != null && nextPrayer != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Next Prayer",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        // Check if next prayer is today or tomorrow based on the prayer time
                        val now = LocalTime.now()
                        val isNextPrayerToday = nextPrayer.time.isAfter(now)
                        val displayText = if (isNextPrayerToday) {
                            "Next: ${nextPrayer.name}"
                        } else {
                            "Next: ${nextPrayer.name} (tomorrow)"
                        }
                        
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "in $timeUntilNext",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Text(
                        text = nextPrayer.time.format(DateTimeFormatter.ofPattern("h:mm a")),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else if (timeUntilNext == null) {
            // Show loading state when prayer times are being calculated
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = "Calculating prayer times...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CleanPrayerTimesList(
    prayers: List<PrayerTime>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Simple header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Mosque,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Prayer Schedule",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        // Clean prayer time items
        prayers.forEach { prayer ->
            CleanPrayerTimeItem(
                prayer = prayer,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun CleanPrayerTimeItem(
    prayer: PrayerTime,
    modifier: Modifier = Modifier
) {
    val isCurrent = prayer.isCurrently
    val isNext = prayer.isNext
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    isNext -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                    else -> Color.Transparent
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Smart prayer icon based on time of day
        Icon(
            imageVector = when {
                // Daytime prayers (6 AM to 6 PM)
                prayer.time.hour in 6..17 -> Icons.Default.WbSunny
                // Nighttime prayers (6 PM to 6 AM)
                else -> Icons.Default.Nightlight
            },
            contentDescription = null,
            tint = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                isNext -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Prayer name
        Text(
            text = prayer.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isCurrent || isNext) FontWeight.Medium else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        
        // Status indicator
        if (isCurrent) {
            Text(
                text = "NOW",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        } else if (isNext) {
            Text(
                text = "NEXT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.secondary,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        
        // Time
        Text(
            text = prayer.time.format(DateTimeFormatter.ofPattern("h:mm a")),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isCurrent || isNext) FontWeight.Medium else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview
@Composable
private fun PrayerTimesCardPreview() {
    NiaTheme {
        val sampleLocation = Location(
            latitude = 25.276987,
            longitude = 55.296249,
            timeZoneOffset = 4.0,
            city = "Dubai",
            country = "UAE"
        )
        
        val samplePrayerTimes = DayPrayerTimes(
            date = java.time.LocalDate.now().atStartOfDay(),
            fajr = LocalTime.of(5, 15),
            sunrise = LocalTime.of(6, 30),
            dhuhr = LocalTime.of(12, 15),
            asr = LocalTime.of(15, 45),
            maghrib = LocalTime.of(18, 20),
            isha = LocalTime.of(19, 50),
            location = sampleLocation
        )
        
        PrayerTimesCard(
            prayerTimes = samplePrayerTimes,
            timeUntilNext = "2h 45m",
            calculationMethod = CalculationMethod.UMM_AL_QURA,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun ResistStyleIndicator(
    isRefreshing: Boolean,
    dragOffset: Float,
    modifier: Modifier = Modifier
) {
    // Calculate drag progress and visual properties exactly like ResistRoute
    val progress = if (dragOffset > DRAG_BUFFER) {
        DecelerateInterpolator().getInterpolation((dragOffset / MAX_DRAG_OFFSET).coerceIn(0f, 1f))
    } else {
        0f
    }
    
    // Interpolate values like ResistRoute IndicatorData
    val size = Dp(lerp(START_SIZE.value, TARGET_SIZE.value, progress))
    val strokeWidth = Dp(lerp(START_STROKE_WIDTH.value, TARGET_STROKE_WIDTH.value, progress))
    val rotation = lerp(START_ROTATION, TARGET_ROTATION, progress)
    val offsetY = Dp(lerp(START_Y_OFFSET.value, TARGET_Y_OFFSET.value, progress))
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Main progress indicator exactly like ResistRoute - no text, just the indicator
        CircularProgressIndicator(
            progress = if (isRefreshing) 1f else 0.75f,
            modifier = Modifier
                .size(size)
                .offset(y = offsetY)
                .rotate(rotation),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = strokeWidth
        )
        
        // Down arrow icon when at rest - visible inside the indicator
        Box(
            modifier = Modifier.offset(y = offsetY)
        ) {
            AnimatedVisibility(
                visible = progress <= 0.05f,
                enter = fadeIn(animationSpec = tween(ANIMATION_DURATION_MS)),
                exit = fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        // Center completion dot like ResistRoute
        Box(
            modifier = Modifier.offset(y = offsetY)
        ) {
            AnimatedVisibility(
                visible = progress >= 0.95f,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                )
            }
        }
    }
}

// Utility function like ResistRoute
private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}