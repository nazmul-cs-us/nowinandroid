package com.starception.submission.feature.quran

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** A screen-owned queue; scrolling its controls off screen must not cancel narration. */
class SurahTafsirSpeechPlayer(
    private val scope: CoroutineScope,
    private val engine: TafsirSpeechPlayer,
    val totalAyahs: Int,
    private val language: String,
    private val loadAyah: suspend (Int) -> String,
    private val activate: (TafsirSpeechPlayer) -> Unit,
) : TafsirSpeechPlayer {
    private val mutableState = MutableStateFlow(TafsirSpeechState())
    override val state = mutableState.asStateFlow()
    private val range = MutableStateFlow<IntRange?>(null)
    val currentAyahs = range.asStateFlow()
    private var job: Job? = null
    private val paused = MutableStateFlow(false)

    fun start() = play("", language)
    override fun play(text: String, language: String) {
        stop()
        activate(this)
        mutableState.value = TafsirSpeechState(TafsirSpeechStatus.PREPARING)
        job = scope.launch {
            try {
                // Preserve source groupings without repeating an identical adjacent explanation.
                val passages = mutableListOf<Pair<IntRange, String>>()
                for (ayah in 1..totalAyahs) {
                    val body = loadAyah(ayah)
                    check(body.isNotBlank()) { "No commentary is available for ayah $ayah." }
                    val previous = passages.lastOrNull()
                    if (previous?.second == body) passages[passages.lastIndex] = (previous.first.first..ayah) to body
                    else passages += (ayah..ayah) to body
                }
                for ((ayahs, body) in passages) {
                    paused.first { !it }
                    range.value = ayahs
                    val result = coroutineScope {
                        val monitor = launch(start = CoroutineStart.UNDISPATCHED) {
                            engine.state.collect { update ->
                                if (update.status != TafsirSpeechStatus.IDLE) mutableState.value = update
                            }
                        }
                        engine.play(body, this@SurahTafsirSpeechPlayer.language)
                        val result = engine.state.first { it.finished || it.status == TafsirSpeechStatus.ERROR }
                        monitor.cancel()
                        result
                    }
                    if (result.status == TafsirSpeechStatus.ERROR) {
                        mutableState.value = result
                        engine.stop()
                        return@launch
                    }
                }
                mutableState.value = TafsirSpeechState(finished = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: PublishedTafsirAssetMissing) {
                mutableState.value = TafsirSpeechState(TafsirSpeechStatus.ERROR,
                    "Download this surah’s tafsir before playing.", assetMissing = true)
            } catch (error: Exception) {
                mutableState.value = TafsirSpeechState(TafsirSpeechStatus.ERROR,
                    error.message ?: "Could not play this surah’s tafsir. Try again.")
            }
        }
    }
    override fun pause() {
        if (state.value.status == TafsirSpeechStatus.PLAYING) {
            paused.value = true; engine.pause(); mutableState.value = TafsirSpeechState(TafsirSpeechStatus.PAUSED)
        }
    }
    override fun resume() {
        if (state.value.status == TafsirSpeechStatus.PAUSED) { activate(this); paused.value = false; engine.resume() }
    }
    override fun stop() {
        job?.cancel(); job = null; paused.value = false; engine.stop(); range.value = null; mutableState.value = TafsirSpeechState()
    }
    override fun release() { stop(); engine.release() }
}

@Composable
fun rememberSurahTafsirSpeechPlayer(surah: Int, book: String, session: TafsirSpeechSession,
    engineFactory: () -> TafsirSpeechPlayer, loadAyah: suspend (Int) -> String): SurahTafsirSpeechPlayer {
    val scope = rememberCoroutineScope()
    val read by rememberUpdatedState(loadAyah)
    val player = remember(surah, book) {
        SurahTafsirSpeechPlayer(scope, engineFactory(), BengaliIbnKathir.ayahCounts[surah - 1],
            PublishedTafsirs.forBook(book)?.languageCode ?: "ar", { read(it) }, session::activate)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.release() }
    }
    return player
}

@Composable
fun SurahTafsirPlaybackControls(player: SurahTafsirSpeechPlayer, surahTitle: String? = null,
    missingAsset: @Composable (() -> Unit) -> Unit) {
    val state by player.state.collectAsState()
    val ayahs by player.currentAyahs.collectAsState()
    val colors = MaterialTheme.colorScheme
    val active = state.status in listOf(TafsirSpeechStatus.PREPARING, TafsirSpeechStatus.PLAYING, TafsirSpeechStatus.PAUSED)
    val action = when (state.status) {
        TafsirSpeechStatus.PLAYING -> "Pause surah tafsir"
        TafsirSpeechStatus.PAUSED -> "Resume surah tafsir"
        TafsirSpeechStatus.PREPARING -> "Preparing surah…"
        else -> "Play full surah tafsir"
    }
    val progress by animateFloatAsState(
        if (state.finished) 1f else ((ayahs?.first ?: 1) - 1).toFloat() / player.totalAyahs,
        label = "Completed tafsir ayahs")
    Card(modifier = Modifier.fillMaxWidth().animateContentSize().testTag("full-surah-tafsir-player"),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f)),
        colors = CardDefaults.cardColors(containerColor = colors.surface)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(
            colors.primaryContainer.copy(alpha = 0.45f), colors.surfaceContainerLow)))
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Headphones, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("SURAH TAFSIR", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp, color = colors.onSurfaceVariant)
                Surface(shape = CircleShape, color = colors.surface.copy(alpha = 0.7f)) {
                    Text(when {
                        state.finished -> "Complete"
                        state.status == TafsirSpeechStatus.PLAYING -> "Listening"
                        state.status == TafsirSpeechStatus.PAUSED -> "Paused"
                        state.status == TafsirSpeechStatus.PREPARING -> "Preparing"
                        state.status == TafsirSpeechStatus.ERROR -> "Retry"
                        else -> "Ready"
                    }, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall, color = colors.primary)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(surahTitle ?: "Listen to tafsir", style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, color = colors.onSurface)
                Text("The full surah, read aloud", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(when {
                        state.finished -> "Surah tafsir finished."
                        else -> ayahs?.let {
                            if (it.first == it.last) "Ayah ${it.first} of ${player.totalAyahs}"
                            else "Ayahs ${it.first}–${it.last} of ${player.totalAyahs}"
                        } ?: "Begin with ayah 1"
                    }, style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
                    if (ayahs == null && !state.finished) Text("${player.totalAyahs} ayahs",
                        style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                if (state.status == TafsirSpeechStatus.PREPARING) LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp), color = colors.primary, trackColor = colors.outlineVariant.copy(alpha = 0.5f))
                else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = colors.primary, trackColor = colors.outlineVariant.copy(alpha = 0.5f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(enabled = state.status != TafsirSpeechStatus.PREPARING,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    onClick = {
                        when (state.status) {
                            TafsirSpeechStatus.PLAYING -> player.pause()
                            TafsirSpeechStatus.PAUSED -> player.resume()
                            else -> player.start()
                        }
                    }) {
                    Icon(if (state.status == TafsirSpeechStatus.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(action, style = MaterialTheme.typography.labelLarge)
                }
                if (active) IconButton(onClick = player::stop, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop surah tafsir", modifier = Modifier.size(20.dp))
                }
            }
            if (state.assetMissing) missingAsset { player.stop() }
            else state.error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
