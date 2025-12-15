package com.starception.submission.feature.prayertimes.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.prayer.model.PrayerTimeSuggestion
import kotlinx.coroutines.delay

/**
 * Displays prayer time offset with AI suggestion alternation.
 *
 * When AI suggestion differs from current offset:
 * - Alternates between showing current offset and ✨suggested offset
 * - Sparkle icon indicates AI suggestion
 * - User can tap to apply the suggestion
 *
 * When AI suggestion matches current offset (or no suggestion):
 * - Just shows the current offset without alternation
 *
 * @param currentOffset The user's current offset in minutes
 * @param suggestion Optional AI suggestion for this prayer
 * @param baseColor The text color to use
 * @param onApplySuggestion Callback when user taps to apply suggestion
 */
@Composable
fun AiSuggestionBadge(
    currentOffset: Int,
    suggestion: PrayerTimeSuggestion?,
    baseColor: Color,
    onApplySuggestion: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Determine if we should show alternating animation
    // Compare suggestion's suggestedOffset against the ACTUAL current offset (not the cached one in suggestion)
    // This ensures that once the user applies the suggestion, the animation stops
    val hasDifferentSuggestion = suggestion != null && suggestion.suggestedOffset != currentOffset

    if (!hasDifferentSuggestion) {
        // No alternation needed - just show current offset
        Text(
            text = formatOffset(currentOffset),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = if (currentOffset != 0) baseColor.copy(alpha = 0.85f) else baseColor.copy(alpha = 0.6f),
            fontWeight = FontWeight.Medium,
            modifier = modifier.padding(bottom = 2.dp)
        )
    } else {
        // Alternate between current offset and AI suggestion
        AlternatingOffsetDisplay(
            currentOffset = currentOffset,
            suggestion = suggestion!!,
            baseColor = baseColor,
            onApplySuggestion = onApplySuggestion,
            modifier = modifier
        )
    }
}

/**
 * Alternates between displaying current offset and AI suggestion.
 */
@Composable
private fun AlternatingOffsetDisplay(
    currentOffset: Int,
    suggestion: PrayerTimeSuggestion,
    baseColor: Color,
    onApplySuggestion: ((Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    // State to track which value to show
    var showSuggestion by remember { mutableStateOf(false) }

    // Alternate every 3 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            showSuggestion = !showSuggestion
        }
    }

    AnimatedContent(
        targetState = showSuggestion,
        transitionSpec = {
            // Slide up animation
            (slideInVertically { height -> height } + fadeIn())
                .togetherWith(slideOutVertically { height -> -height } + fadeOut())
        },
        label = "offset_alternation",
        modifier = modifier
            .then(
                if (onApplySuggestion != null && showSuggestion) {
                    Modifier.clickable {
                        onApplySuggestion(suggestion.suggestedOffset)
                    }
                } else {
                    Modifier
                }
            )
    ) { showingSuggestion ->
        if (showingSuggestion) {
            // Show AI suggestion with sparkle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Text(
                    text = suggestion.getFormattedSuggestion(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = Color(0xFF26C6DA), // Teal color for AI suggestion
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            // Show current offset
            Text(
                text = formatOffset(currentOffset),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = if (currentOffset != 0) baseColor.copy(alpha = 0.85f) else baseColor.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

/**
 * Formats offset value to display string.
 */
private fun formatOffset(offset: Int): String {
    return when {
        offset > 0 -> "+${offset}m"
        offset < 0 -> "${offset}m"
        else -> "±0m"
    }
}
