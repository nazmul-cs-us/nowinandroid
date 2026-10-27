package com.starception.submission.feature.prayertimes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.insight_quran_background
import com.starception.submission.feature.quran.Surah
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/** A separate destination for the existing daily Quran media session. */
@Composable
internal fun HomeReadingPlayer(
    surah: Surah,
    playback: DailyReadingPlaybackState,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onRetry: () -> Unit,
    onSeek: (Int) -> Unit,
) {
    val isCurrent = playback.surahIndex == surah.number - 1
    val duration = if (isCurrent) playback.durationMs.coerceAtLeast(0) else 0
    val position = if (isCurrent) playback.positionMs.coerceIn(0, duration) else 0
    var dragging by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(position, duration, dragging) {
        if (!dragging) seekFraction = if (duration > 0) position.toFloat() / duration else 0f
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF06121D))) {
            Image(
                painter = painterResource(Res.drawable.insight_quran_background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to Color(0xFF081725).copy(alpha = .72f),
                .46f to Color(0xFF091724).copy(alpha = .78f),
                1f to Color(0xFF06121D),
            )))
            Column(
                Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 40.dp, bottom = 40.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("NOW PLAYING", color = Color.White.copy(alpha = .76f),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Clear, contentDescription = "Return to reading", tint = Color.White)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("TODAY'S READING", color = Color(0xFFD9B679),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(surah.nameEnglish, color = Color.White,
                    style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp))
                Text("Surah ${surah.number} · ${surah.nameArabic}",
                    color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 28.dp))
                if (playback.error != null && isCurrent) {
                    Text(playback.error, color = Color.White, style = MaterialTheme.typography.bodySmall)
                } else if ((playback.isLoading || playback.isDownloading) && isCurrent) {
                    Text(if (playback.isDownloading) "Preparing audio…" else "Starting playback…",
                        color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall)
                }
                Slider(
                    value = seekFraction.coerceIn(0f, 1f),
                    onValueChange = { dragging = true; seekFraction = it },
                    onValueChangeFinished = {
                        if (duration > 0) onSeek((seekFraction * duration).roundToInt())
                        dragging = false
                    },
                    enabled = duration > 0,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatPlaybackTime(position), color = Color.White.copy(alpha = .7f),
                        style = MaterialTheme.typography.labelSmall)
                    Text(formatPlaybackTime(duration), color = Color.White.copy(alpha = .7f),
                        style = MaterialTheme.typography.labelSmall)
                }
                Box(Modifier.fillMaxWidth().padding(top = 22.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        onClick = if (playback.error != null && isCurrent) onRetry else onPlayPause,
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(72.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if ((playback.isLoading || playback.isDownloading) && isCurrent) {
                                CircularProgressIndicator(color = Color(0xFF10283A),
                                    modifier = Modifier.size(28.dp))
                            } else {
                                Icon(
                                    imageVector = when {
                                        playback.error != null && isCurrent -> Icons.Default.Refresh
                                        playback.isPlaying && isCurrent -> Icons.Default.Pause
                                        else -> Icons.Default.PlayArrow
                                    },
                                    contentDescription = when {
                                        playback.error != null && isCurrent -> "Retry audio"
                                        playback.isPlaying && isCurrent -> "Pause"
                                        else -> "Play"
                                    },
                                    tint = Color(0xFF10283A),
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatPlaybackTime(milliseconds: Int): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
