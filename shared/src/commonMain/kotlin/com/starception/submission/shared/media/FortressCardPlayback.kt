package com.starception.submission.shared.media

import com.starception.submission.shared.audio.QuranAudioPlayer
import com.starception.submission.shared.audio.resolveFortressAudioUrl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One preparation job/player across feed cards, including cards recycled by a lazy list. */
internal object FortressCardPlayback {
    data class Preparation(val url: String, val error: String? = null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val player by lazy { QuranAudioPlayer() }
    private val mutableState = MutableStateFlow<Preparation?>(null)
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0

    fun toggle(url: String, title: String) {
        if (mutableState.value?.let { it.url == url && it.error == null } == true) return
        if (QuranPlaybackBus.state.value?.sourceUrl == url) {
            QuranPlaybackBus.toggle()
            return
        }
        val ticket = ++generation
        job?.cancel()
        QuranPlaybackBus.stop()
        mutableState.value = Preparation(url)
        job = scope.launch {
            try {
                val localUrl = resolveFortressAudioUrl(url) ?: error("Unable to download dua audio. Please retry.")
                ensureActive()
                // Do not interrupt audio the user started elsewhere during preparation.
                if (QuranPlaybackBus.state.value != null) return@launch
                check(player.play(localUrl)) { "Unable to play dua audio. Please retry." }
                QuranPlaybackBus.startFortress(player, localUrl, title, sourceUrl = url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (ticket == generation) {
                    player.stop()
                    mutableState.value = Preparation(url, failure.message ?: "Unable to play audio.")
                }
            } finally {
                if (ticket == generation && mutableState.value?.error == null) mutableState.value = null
            }
        }
    }
}
