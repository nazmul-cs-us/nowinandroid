package com.starception.submission.feature.quran

import androidx.compose.foundation.layout.*
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
fun SurahTafsirPlaybackControls(player: SurahTafsirSpeechPlayer, missingAsset: @Composable (() -> Unit) -> Unit) {
    val state by player.state.collectAsState()
    val ayahs by player.currentAyahs.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(enabled = state.status != TafsirSpeechStatus.PREPARING, onClick = {
                when (state.status) {
                    TafsirSpeechStatus.PLAYING -> player.pause()
                    TafsirSpeechStatus.PAUSED -> player.resume()
                    else -> player.start()
                }
            }) {
                Icon(if (state.status == TafsirSpeechStatus.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(when (state.status) {
                    TafsirSpeechStatus.PLAYING -> "Pause surah tafsir"
                    TafsirSpeechStatus.PAUSED -> "Resume surah tafsir"
                    TafsirSpeechStatus.PREPARING -> "Preparing surah…"
                    else -> "Play full surah tafsir"
                })
            }
            if (state.status in listOf(TafsirSpeechStatus.PREPARING, TafsirSpeechStatus.PLAYING, TafsirSpeechStatus.PAUSED))
                IconButton(onClick = player::stop) { Icon(Icons.Default.Stop, contentDescription = "Stop surah tafsir") }
        }
        if (state.finished) Text("Surah tafsir finished.", style = MaterialTheme.typography.bodySmall)
        else ayahs?.let {
            Text(if (it.first == it.last) "Ayah ${it.first} of ${player.totalAyahs}"
                else "Ayahs ${it.first}–${it.last} of ${player.totalAyahs}", style = MaterialTheme.typography.bodySmall)
        }
        if (state.assetMissing) missingAsset { player.stop() }
        else state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
