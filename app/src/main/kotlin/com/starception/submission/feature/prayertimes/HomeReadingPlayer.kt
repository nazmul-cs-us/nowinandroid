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
import androidx.compose.material3.SliderDefaults
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
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
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
    val dark = LocalDarkTheme.current
    val ink = if (dark) Color.White else Color(0xFF17221B)
    val mutedInk = ink.copy(alpha = .72f)
    val playSurface = if (dark) Color.White else Color(0xFF17221B)
    val playInk = if (dark) Color(0xFF10283A) else Color.White
    val accent = if (dark) MaterialTheme.colorScheme.primary else Color(0xFF8B68B4)
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
        Box(Modifier.fillMaxSize().then(
            if (dark) Modifier.background(Color(0xFF06121D))
            else Modifier.background(Brush.verticalGradient(
                listOf(Color(0xFFF8F7FC), Color.White, Color(0xFFFAF9FD)),
            )),
        )) {
            if (dark) {
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
            } else {
                Box(
                    Modifier.align(Alignment.Center).size(320.dp).background(
                        Brush.radialGradient(listOf(
                            Color(0xFFE8DDF5).copy(alpha = .48f),
                            Color.Transparent,
                        )),
                        CircleShape,
                    ),
                )
            }
            Column(
                Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 40.dp, bottom = 40.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("NOW PLAYING", color = mutedInk,
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Clear, contentDescription = "Return to reading", tint = ink)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("TODAY'S READING", color = if (dark) Color(0xFFD9B679) else accent,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(surah.nameEnglish, color = ink,
                    style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp))
                Text("Surah ${surah.number} · ${surah.nameArabic}",
                    color = mutedInk, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 28.dp))
                if (playback.error != null && isCurrent) {
                    Text(playback.error, color = ink, style = MaterialTheme.typography.bodySmall)
                } else if ((playback.isLoading || playback.isDownloading) && isCurrent) {
                    Text(if (playback.isDownloading) "Preparing audio…" else "Starting playback…",
                        color = mutedInk, style = MaterialTheme.typography.bodySmall)
                }
                Slider(
                    value = seekFraction.coerceIn(0f, 1f),
                    onValueChange = { dragging = true; seekFraction = it },
                    onValueChangeFinished = {
                        if (duration > 0) onSeek((seekFraction * duration).roundToInt())
                        dragging = false
                    },
                    enabled = duration > 0,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = if (dark) Color.White.copy(alpha = .25f) else Color(0xFFE4DFEA),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatPlaybackTime(position), color = mutedInk,
                        style = MaterialTheme.typography.labelSmall)
                    Text(formatPlaybackTime(duration), color = mutedInk,
                        style = MaterialTheme.typography.labelSmall)
                }
                Box(Modifier.fillMaxWidth().padding(top = 22.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        onClick = if (playback.error != null && isCurrent) onRetry else onPlayPause,
                        shape = CircleShape,
                        color = playSurface,
                        modifier = Modifier.size(72.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if ((playback.isLoading || playback.isDownloading) && isCurrent) {
                                CircularProgressIndicator(color = playInk,
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
                                    tint = playInk,
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
