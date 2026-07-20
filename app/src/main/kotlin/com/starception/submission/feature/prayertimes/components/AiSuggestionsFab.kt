package com.starception.submission.feature.prayertimes.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import com.starception.submission.prayer.model.DayPrayerSuggestions
import com.starception.submission.prayer.model.PrayerTimeSuggestion
import kotlinx.coroutines.launch

/**
 * Floating AI Assistant Bubble for Prayer Time Suggestions
 *
 * Displays a floating action button when there are AI suggestions available.
 * Tapping opens a bottom sheet with all suggestions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSuggestionsFab(
    suggestions: DayPrayerSuggestions,
    currentOffsets: Map<String, Int>,
    onApplySuggestion: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Filter to only suggestions that differ from current offsets
    // Note: DayPrayerSuggestions doesn't include Sunrise as it's not typically adjusted
    val pendingSuggestions = remember(suggestions, currentOffsets) {
        listOfNotNull<PrayerTimeSuggestion>(
            suggestions.fajr?.takeIf { it.suggestedOffset != currentOffsets["Fajr"] },
            suggestions.dhuhr?.takeIf { it.suggestedOffset != currentOffsets["Dhuhr"] },
            suggestions.asr?.takeIf { it.suggestedOffset != currentOffsets["Asr"] },
            suggestions.maghrib?.takeIf { it.suggestedOffset != currentOffsets["Maghrib"] },
            suggestions.isha?.takeIf { it.suggestedOffset != currentOffsets["Isha"] }
        )
    }

    val hasPendingSuggestions = pendingSuggestions.isNotEmpty()

    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // Subtle pulse animation for the FAB
    val infiniteTransition = rememberInfiniteTransition(label = "fabPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Show FAB only when there are pending suggestions
    AnimatedVisibility(
        visible = hasPendingSuggestions,
        enter = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) + fadeIn(tween(200)),
        exit = scaleOut(tween(150)) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Text(
                        text = "${pendingSuggestions.size}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        ) {
            FloatingActionButton(
                onClick = { showBottomSheet = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.scale(pulseScale)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Suggestions",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    // Bottom Sheet
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            AiSuggestionsContent(
                suggestions = pendingSuggestions,
                currentOffsets = currentOffsets,
                onApplySuggestion = { prayerName, offset ->
                    onApplySuggestion(prayerName, offset)
                },
                onApplyAll = {
                    pendingSuggestions.forEach { suggestion ->
                        onApplySuggestion(suggestion.prayerName, suggestion.suggestedOffset)
                    }
                    scope.launch {
                        sheetState.hide()
                        showBottomSheet = false
                    }
                },
                onDismiss = {
                    scope.launch {
                        sheetState.hide()
                        showBottomSheet = false
                    }
                }
            )
        }
    }
}

@Composable
private fun AiSuggestionsContent(
    suggestions: List<PrayerTimeSuggestion>,
    currentOffsets: Map<String, Int>,
    onApplySuggestion: (String, Int) -> Unit,
    onApplyAll: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "AI Suggestions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${suggestions.size} optimization${if (suggestions.size > 1) "s" else ""} available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Suggestions list
        suggestions.forEach { suggestion ->
            SuggestionCard(
                suggestion = suggestion,
                currentOffset = currentOffsets[suggestion.prayerName] ?: 0,
                onApply = { onApplySuggestion(suggestion.prayerName, suggestion.suggestedOffset) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Apply All button
        if (suggestions.size > 1) {
            NiaOutlinedButton(
                onClick = onApplyAll,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Apply All Suggestions",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: PrayerTimeSuggestion,
    currentOffset: Int,
    onApply: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.prayerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Current offset
                    Text(
                        text = formatOffset(currentOffset),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Arrow
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Suggested offset with highlight
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = formatOffset(suggestion.suggestedOffset),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Apply button
            Surface(
                modifier = Modifier.clickable { onApply() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Apply",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(20.dp)
                )
            }
        }
    }
}

private fun formatOffset(offset: Int): String {
    return when {
        offset > 0 -> "+${offset}m"
        offset < 0 -> "${offset}m"
        else -> "±0m"
    }
}
