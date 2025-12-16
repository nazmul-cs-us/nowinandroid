package com.starception.submission.feature.prayertimes.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.prayer.model.PrayerTimeSuggestion

/**
 * Displays prayer time offset with optional AI suggestion indicator.
 *
 * Shows the current offset (e.g., "-8m", "-2m", "±0m").
 * If an AI suggestion differs from current, shows a small ✨ sparkle.
 * Tapping the sparkle applies the suggestion.
 *
 * @param currentOffset The user's current offset in minutes
 * @param suggestion Optional AI suggestion for this prayer
 * @param baseColor The text color to use
 * @param onApplySuggestion Callback when user taps sparkle to apply suggestion
 */
@Composable
fun AiSuggestionBadge(
    currentOffset: Int,
    suggestion: PrayerTimeSuggestion?,
    baseColor: Color,
    onApplySuggestion: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hasDifferentSuggestion = suggestion != null && suggestion.suggestedOffset != currentOffset

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(bottom = 2.dp)
    ) {
        Text(
            text = formatOffset(currentOffset),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = if (currentOffset != 0) baseColor.copy(alpha = 0.85f) else baseColor.copy(alpha = 0.6f),
            fontWeight = FontWeight.Medium
        )

        // Show sparkle if AI suggestion differs - tap to apply
        if (hasDifferentSuggestion && onApplySuggestion != null) {
            Text(
                text = " ✨",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                modifier = Modifier.clickable { onApplySuggestion(suggestion!!.suggestedOffset) }
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
