package com.starception.dua.prayer.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.animation.DecelerateInterpolator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material.icons.rounded.ArrowDownward
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
import com.starception.dua.core.designsystem.theme.NiaTheme
import com.starception.dua.prayer.model.CalculationMethod
import com.starception.dua.prayer.model.DayPrayerTimes
import com.starception.dua.prayer.model.Location
import com.starception.dua.prayer.model.PrayerTime
import kotlinx.coroutines.delay
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
 * Prayer times display card component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesCard(
    prayerTimes: DayPrayerTimes,
    timeUntilNext: String? = null,
    calculationMethod: CalculationMethod? = null,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Pull-to-refresh state
    val context = LocalContext.current
    var dragOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    // Animation for returning to original position with smooth easing
    val animatedOffset = animateFloatAsState(
        targetValue = if (isDragging) dragOffset else 0f,
        animationSpec = if (isDragging) tween(0) else tween(
            durationMillis = ANIMATION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "drag_offset"
    )
    
    // Update drag offset during animation return
    LaunchedEffect(animatedOffset.value) {
        if (!isDragging) {
            dragOffset = animatedOffset.value
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
                    isDragging = false
                    // Trigger refresh if dragged past threshold
                    if (dragOffset >= REFRESH_THRESHOLD) {
                        onRefresh()
                    }
                },
                state = rememberDraggableState { delta ->
                    if (delta > 0) { // Only allow downward drag
                        // Apply deceleration resistance like ResistRoute
                        val newOffset = dragOffset + delta
                        val resistance = DecelerateInterpolator().getInterpolation(
                            (newOffset / MAX_DRAG_OFFSET).coerceIn(0f, 1f)
                        )
                        dragOffset = (dragOffset + delta * (1f - resistance * 0.8f)).coerceAtMost(MAX_DRAG_OFFSET)
                    }
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .offset(y = (animatedOffset.value * 0.5f).dp), // Move entire content together
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
            
            // Prayer content with proper spacing
            Column {
                // Header with location and next prayer info
                PrayerTimesHeader(
                    prayerTimes = prayerTimes,
                    location = prayerTimes.location,
                    nextPrayer = prayerTimes.getNextPrayer(),
                    timeUntilNext = timeUntilNext,
                    calculationMethod = calculationMethod
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Prayer times list
                PrayerTimesList(prayers = prayerTimes.getAllPrayers())
            }
        }
    }
}

@Composable
private fun PrayerTimesHeader(
    prayerTimes: DayPrayerTimes,
    location: Location,
    nextPrayer: PrayerTime?,
    timeUntilNext: String?,
    calculationMethod: CalculationMethod?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Location info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Location",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = location.getDisplayName(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Calculation method info
        calculationMethod?.let { method ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Using ${method.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Next prayer info or last prayer info
        if (timeUntilNext != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = if (nextPrayer != null) "Next Prayer" else "Last Prayer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    if (nextPrayer != null) {
                        // Show next prayer
                        val isNextPrayerToday = prayerTimes.getAllPrayers().any { it.time.isAfter(LocalTime.now()) }
                        val displayText = if (isNextPrayerToday) {
                            "Next: ${nextPrayer.name}"
                        } else {
                            "Next: ${nextPrayer.name} (tomorrow)"
                        }
                        
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "in $timeUntilNext",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // This case should not happen anymore as we always have a next prayer (including tomorrow's Fajr)
                        Text(
                            text = "Prayer times calculated",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (nextPrayer != null) {
                    Text(
                        text = nextPrayer.time.format(DateTimeFormatter.ofPattern("h:mm a")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PrayerTimesList(
    prayers: List<PrayerTime>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        prayers.forEach { prayer ->
            PrayerTimeItem(
                prayer = prayer,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun PrayerTimeItem(
    prayer: PrayerTime,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    prayer.isCurrently -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    prayer.isNext -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = prayer.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (prayer.isNext || prayer.isCurrently) FontWeight.Medium else FontWeight.Normal,
            color = when {
                prayer.isCurrently -> MaterialTheme.colorScheme.primary
                prayer.isNext -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
        
        if (prayer.isCurrently) {
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
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Text(
            text = prayer.time.format(DateTimeFormatter.ofPattern("h:mm a")),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (prayer.isNext || prayer.isCurrently) FontWeight.Medium else FontWeight.Normal,
            color = when {
                prayer.isCurrently -> MaterialTheme.colorScheme.primary
                prayer.isNext -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.End
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