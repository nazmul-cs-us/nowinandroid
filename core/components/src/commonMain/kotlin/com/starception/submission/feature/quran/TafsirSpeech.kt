package com.starception.submission.feature.quran

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.StateFlow

enum class TafsirSpeechStatus { IDLE, PREPARING, PLAYING, PAUSED, ERROR }
data class TafsirSpeechState(val status: TafsirSpeechStatus = TafsirSpeechStatus.IDLE, val error: String? = null,
    val finished: Boolean = false, val assetMissing: Boolean = false)

interface TafsirSpeechPlayer {
    val state: StateFlow<TafsirSpeechState>
    fun play(text: String, language: String)
    fun pause()
    fun resume()
    fun stop()
    fun release()
}

/** Android engines limit individual utterances; never split a UTF-16 surrogate pair. */
fun tafsirSpeechChunks(text: String, maxCharacters: Int = 3000): List<String> {
    require(maxCharacters >= 2)
    val result = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = (start + maxCharacters).coerceAtMost(text.length)
        if (end < text.length) {
            val space = text.lastIndexOfAny(charArrayOf(' ', '\n', '\t'), end - 1)
            if (space > start + maxCharacters / 2) end = space + 1
            if (text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
        }
        text.substring(start, end).takeIf { it.isNotBlank() }?.let(result::add)
        start = end
    }
    return result
}

@Composable
fun TafsirPlaybackControls(text: String, language: String, playerFactory: () -> TafsirSpeechPlayer) {
    TafsirPlaybackControls(text, language, compact = false, playerFactory)
}

@Composable
fun TafsirPlaybackControls(text: String, language: String, compact: Boolean,
    playerFactory: () -> TafsirSpeechPlayer) {
    if (text.isBlank()) return
    key(text, language) {
        val player = remember { playerFactory() }
        val state by player.state.collectAsState()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(player, lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) player.stop()
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer); player.release() }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                val action = when (state.status) {
                    TafsirSpeechStatus.PLAYING -> "Pause tafsir"
                    TafsirSpeechStatus.PAUSED -> "Resume tafsir"
                    TafsirSpeechStatus.PREPARING -> "Preparing voice…"
                    else -> "Play tafsir"
                }
                val onPlay: () -> Unit = {
                    when (state.status) {
                        TafsirSpeechStatus.PLAYING -> player.pause()
                        TafsirSpeechStatus.PAUSED -> player.resume()
                        else -> player.play(text, language)
                    }
                }
                if (compact) {
                    IconButton(onClick = onPlay, enabled = state.status != TafsirSpeechStatus.PREPARING,
                        modifier = Modifier.size(48.dp)) {
                        if (state.status == TafsirSpeechStatus.PREPARING) CircularProgressIndicator(
                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(if (state.status == TafsirSpeechStatus.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = action, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                } else {
                    FilledTonalButton(enabled = state.status != TafsirSpeechStatus.PREPARING, onClick = onPlay) {
                        Icon(if (state.status == TafsirSpeechStatus.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(action)
                    }
                }
                if (state.status in listOf(TafsirSpeechStatus.PLAYING, TafsirSpeechStatus.PAUSED, TafsirSpeechStatus.PREPARING)) {
                    IconButton(onClick = player::stop) { Icon(Icons.Default.Stop, contentDescription = "Stop tafsir") }
                }
            }
            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}
