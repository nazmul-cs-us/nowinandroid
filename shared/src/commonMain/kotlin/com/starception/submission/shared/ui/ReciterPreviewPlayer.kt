package com.starception.submission.shared.ui

import androidx.compose.runtime.*
import com.starception.submission.shared.audio.*
import kotlinx.coroutines.*

/** Previews are cancelled on dismissal and do not select or bookmark the voice. */
@Stable
internal class ReciterPreviewPlayer(private val scope: CoroutineScope, private val library: ReciterLibrary) {
    var activeId by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private val player = library.createPreviewPlayer()
    private var job: Job? = null
    private var generation = 0

    fun toggle(source: QuranReciterSource?, surahNumber: Int? = null) {
        val id = source?.id ?: "default-cdn"
        if (activeId == id) { stop(); return }
        stop()
        val token = ++generation
        activeId = id
        loading = true
        error = null
        job = scope.launch {
            try {
                val playback = player ?: kotlin.error("Preview unavailable")
                val url = withTimeout(15_000) { withContext(Dispatchers.Default) { library.previewUrl(source, surahNumber) } }
                    ?: error("Preview unavailable")
                com.starception.submission.shared.media.QuranPlaybackBus.stop()
                check(playback.play(url)) { "Preview unavailable" }
                withTimeout(15_000) {
                    while (playback.phase() == ReciterPreviewPlayback.Phase.BUFFERING) delay(100)
                }
                check(playback.phase() != ReciterPreviewPlayback.Phase.FAILED) { "Preview unavailable" }
                loading = false
                repeat(150) {
                    if (playback.phase() == ReciterPreviewPlayback.Phase.ENDED) return@launch
                    if (playback.phase() == ReciterPreviewPlayback.Phase.FAILED) error("Preview unavailable")
                    delay(200)
                }
            } catch (timeout: TimeoutCancellationException) {
                if (generation == token) error = "Preview unavailable. Check your connection and try again."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generation == token) error = "Preview unavailable. Check your connection and try again."
            } finally {
                if (generation == token) { player?.stop(); activeId = null; loading = false }
            }
        }
    }
    fun stop() {
        generation++
        job?.cancel()
        job = null
        player?.stop()
        activeId = null
        loading = false
    }
}

@Composable
internal fun rememberReciterPreview(library: ReciterLibrary): ReciterPreviewPlayer {
    val scope = rememberCoroutineScope()
    val preview = remember(library, scope) { ReciterPreviewPlayer(scope, library) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(preview, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) preview.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            preview.stop()
        }
    }
    return preview
}
