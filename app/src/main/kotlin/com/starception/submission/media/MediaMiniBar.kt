package com.starception.submission.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Media mini-bar for the PullToSyncContainer sage area.
 * Single-row layout matching the same height as download/sync banners.
 *
 * Layout: [Title/Subtitle]  [◀◀] [▶❚❚] [▶▶]
 * Playback progress is shown via the PullToSyncContainer horizontal sweep.
 *
 * Renders with transparent background so it blends into the sage background,
 * using onPrimaryContainer colors to match the indicator style.
 *
 * Pull up on the sage area to dismiss.
 */
@Composable
fun MediaMiniBar(
    state: MediaControllerUiState,
    onAction: (MediaAction) -> Unit,
    modifier: Modifier = Modifier,
    titleDragModifier: Modifier = Modifier,
    onTitleClick: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val playback = state.playback

    // Use sage-area colors so the mini-bar blends into PullToSyncContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val subtitleColor = MaterialTheme.colorScheme.onPrimaryContainer

    // Single row: title area on left, controls on right
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Title + Subtitle, takes remaining space.
        // Tapping the title navigates to the playing surah; drag-to-dismiss is bound
        // to this area only so taps on the playback buttons are never intercepted.
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .clickable(onClick = onTitleClick)
                .then(titleDragModifier),
        ) {
            Text(
                text = playback.title,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 15.sp,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (playback.subtitle.isNotEmpty()) {
                Text(
                    text = playback.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Controls: skip prev, play/pause, skip next
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onAction(MediaAction.SkipPrevious)
            },
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                modifier = Modifier.size(28.dp),
                tint = contentColor,
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (playback.isPlaying) onAction(MediaAction.Pause)
                else onAction(MediaAction.Play)
            },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playback.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(36.dp),
                tint = contentColor,
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onAction(MediaAction.SkipNext)
            },
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                modifier = Modifier.size(28.dp),
                tint = contentColor,
            )
        }
    }
}
