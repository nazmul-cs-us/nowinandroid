package com.starception.submission.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.starception.submission.shared.audio.QuranAudioPlayer
import com.starception.submission.shared.audio.resolveQuranAudioUrl
import com.starception.submission.shared.content.SharedContentStore
import com.starception.submission.shared.media.QuranPlaybackBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal suspend fun startReadingRecitation(
    surahNumber: Int,
    language: String,
    resolve: suspend (Int, String) -> String?,
    play: (String) -> Boolean,
    onStarted: (String) -> Unit,
) {
    val url = resolve(surahNumber, language)
        ?: error("Audio is unavailable. Check your connection and try again.")
    currentCoroutineContext().ensureActive()
    check(play(url)) { "Unable to play this audio. Please try again." }
    onStarted(url)
}

internal data class ReadingTilePlayback(
    val playing: Boolean,
    val preparing: Boolean,
    val error: String?,
    val toggle: () -> Unit,
    val openSurah: () -> Unit,
)

/** Resolve the selected-language recording before handing a local URL to iOS. */
@Composable
internal fun rememberReadingTilePlayback(
    player: QuranAudioPlayer,
    surahNumber: Int,
    onOpenSurah: () -> Unit,
): ReadingTilePlayback {
    val store = remember { SharedContentStore() }
    val scope = rememberCoroutineScope()
    val playback by QuranPlaybackBus.state.collectAsState()
    var preparing by remember(surahNumber) { mutableStateOf(false) }
    var error by remember(surahNumber) { mutableStateOf<String?>(null) }
    var request by remember(surahNumber) { mutableStateOf<Job?>(null) }

    DisposableEffect(player, surahNumber) {
        onDispose { request?.cancel() }
    }

    return ReadingTilePlayback(
        playing = playback?.let { it.surahNumber == surahNumber && it.playing } == true,
        preparing = preparing,
        error = error,
        toggle = {
            if (!preparing) {
                if (playback?.surahNumber == surahNumber && playback?.playing == true) {
                    QuranPlaybackBus.toggle()
                } else {
                    val language = store.quranTranslationLanguage()
                    player.pause()
                    QuranPlaybackBus.clear()
                    preparing = true
                    error = null
                    request = scope.launch {
                        try {
                            startReadingRecitation(
                                surahNumber = surahNumber,
                                language = language,
                                resolve = ::resolveQuranAudioUrl,
                                play = player::play,
                                onStarted = { url -> QuranPlaybackBus.startSurah(player, surahNumber, url) },
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.message ?: "Unable to prepare audio. Please try again."
                        } finally {
                            preparing = false
                        }
                    }
                }
            }
        },
        openSurah = {
            request?.cancel()
            preparing = false
            error = null
            player.stop()
            QuranPlaybackBus.clear()
            onOpenSurah()
        },
    )
}
