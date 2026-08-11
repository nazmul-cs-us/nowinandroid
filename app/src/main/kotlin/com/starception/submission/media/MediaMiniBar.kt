package com.starception.submission.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.ui.MiniBarShell

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
    preparingAudio: Boolean = false,
    /**
     * Optional strip status (prayer alert, sync, Islamic event, …) shown in place
     * of the track subtitle. The strip has exactly one row, so a status that is
     * live at the same time as playback rides here instead of claiming a row of
     * its own.
     */
    statusText: String? = null,
    /** See [MiniBarShell]; off while reading Mushaf so the page stays uncluttered. */
    showProgressLine: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val playback = state.playback

    // Use sage-area colors so the mini-bar blends into PullToSyncContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val subtitleColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.70f)
    val progress = if (playback.duration > 0) {
        playback.currentPosition.toFloat() / playback.duration.toFloat()
    } else {
        0f
    }

    MiniBarShell(
        progress = progress,
        modifier = modifier,
        showProgressLine = showProgressLine,
    ) {
        // Title + subtitle share one line so playback and Mushaf navigation use
        // exactly the same compact geometry.
        // Tapping the title navigates to the playing surah; drag-to-dismiss is bound
        // to this area only so taps on the playback buttons are never intercepted.
        val subtitleText = when {
            statusText != null -> statusText
            preparingAudio -> "Preparing audio…"
            else -> playback.subtitle
        }
        val displayText = if (subtitleText.isBlank()) {
            playback.title
        } else {
            "${playback.title} · $subtitleText"
        }
        Text(
            text = displayText,
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
                .clickable(onClick = onTitleClick)
                .then(titleDragModifier),
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 17.sp,
            lineHeight = 19.sp,
            color = if (preparingAudio) subtitleColor else contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Controls: skip prev, play/pause, skip next
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onAction(MediaAction.SkipPrevious)
            },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                modifier = Modifier.size(20.dp),
                tint = contentColor.copy(alpha = 0.78f),
            )
        }

        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (playback.isPlaying) onAction(MediaAction.Pause)
                else onAction(MediaAction.Play)
            },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playback.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(22.dp),
                tint = contentColor,
            )
        }

        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onAction(MediaAction.SkipNext)
            },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                modifier = Modifier.size(20.dp),
                tint = contentColor.copy(alpha = 0.78f),
            )
        }
    }
}
