package com.starception.submission.media

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starception.submission.feature.quran.AudioLanguage

/**
 * Full-featured expanded media controls composable.
 * Shown when the user pulls down or taps the mini-bar.
 *
 * Includes:
 * - Pull handle bar at top
 * - Track title + subtitle
 * - Play/pause, skip next/prev
 * - Seek slider with elapsed/remaining time
 * - Volume slider
 * - Language toggle chips (Quran only)
 * - Close button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedMediaController(
    state: MediaControllerUiState,
    onAction: (MediaAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val playback = state.playback
    val progress = if (playback.duration > 0) {
        playback.currentPosition.toFloat() / playback.duration.toFloat()
    } else {
        0f
    }

    // Use sage-area colors so the controller blends into PullToSyncContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val subtitleColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Pull handle bar
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(contentColor.copy(alpha = 0.4f)),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Title
        Text(
            text = playback.title,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // Subtitle
        if (playback.subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = playback.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Playback controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Skip Previous
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAction(MediaAction.SkipPrevious)
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(32.dp),
                    tint = contentColor,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Play / Pause
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (playback.isPlaying) onAction(MediaAction.Pause)
                    else onAction(MediaAction.Play)
                },
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playback.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Skip Next
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAction(MediaAction.SkipNext)
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(32.dp),
                    tint = contentColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Seek slider with time labels
        if (playback.duration > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatDuration(playback.currentPosition),
                    style = MaterialTheme.typography.labelSmall,
                    color = subtitleColor,
                )
                Slider(
                    value = progress,
                    onValueChange = { fraction ->
                        val position = (fraction * playback.duration).toInt()
                        onAction(MediaAction.SeekTo(position))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    ),
                )
                Text(
                    text = formatDuration(playback.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = subtitleColor,
                )
            }
        }

        // Volume slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Volume",
                modifier = Modifier.size(18.dp),
                tint = subtitleColor,
            )
            Slider(
                value = state.volume,
                onValueChange = { onAction(MediaAction.SetVolume(it)) },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                ),
            )
        }

        // Language toggle (Quran only)
        if (state.hasLanguageToggle) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                LanguageChip(
                    label = "AR",
                    selected = state.currentLanguage == AudioLanguage.ARABIC_ONLY,
                    onClick = {
                        if (state.currentLanguage != AudioLanguage.ARABIC_ONLY) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAction(MediaAction.ToggleLanguage)
                        }
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
                LanguageChip(
                    label = "\u09AC\u09BE\u0982",
                    selected = state.currentLanguage == AudioLanguage.BENGALI_TRANSLATION,
                    onClick = {
                        if (state.currentLanguage != AudioLanguage.BENGALI_TRANSLATION) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAction(MediaAction.ToggleLanguage)
                        }
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
                LanguageChip(
                    label = "EN",
                    selected = state.currentLanguage == AudioLanguage.ENGLISH_TRANSLATION,
                    onClick = {
                        if (state.currentLanguage != AudioLanguage.ENGLISH_TRANSLATION) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAction(MediaAction.ToggleLanguage)
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Close button
        TextButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onAction(MediaAction.Dismiss)
            },
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Close", color = contentColor)
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

private fun formatDuration(millis: Int): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
