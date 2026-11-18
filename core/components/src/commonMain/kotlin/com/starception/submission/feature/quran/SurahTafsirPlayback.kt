package com.starception.submission.feature.quran

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** App-owned narration survives reader navigation. Individual-ayah speech remains page-owned. */
class SurahTafsirPlaybackOwner(private val scope: CoroutineScope) {
    class Entry(val surah: Int, val book: String, val title: String, val player: SurahTafsirSpeechPlayer) {
        internal var attachments = 1
        internal var beforeStart: () -> Unit = {}
    }
    data class Playback(val entry: Entry, val speech: TafsirSpeechState, val ayahs: IntRange?) {
        val playing get() = speech.status != TafsirSpeechStatus.PAUSED
        val subtitle get() = listOfNotNull("Tafsir", PublishedTafsirs.forBook(entry.book)?.englishLanguageName,
            ayahs?.let { if (it.first == it.last) "Ayah ${it.first}" else "Ayahs ${it.first}–${it.last}" }).joinToString(" · ")
    }
    private val current = MutableStateFlow<Playback?>(null)
    val state = current.asStateFlow()
    private var observer: Job? = null

    fun attach(surah: Int, book: String, title: String, engineFactory: () -> TafsirSpeechPlayer,
        loadAyah: suspend (Int) -> String, beforeStart: () -> Unit = {}, onActivate: () -> Unit = {}): Entry {
        current.value?.entry?.takeIf { it.surah == surah && it.book == book }?.let {
            it.attachments++
            it.beforeStart = beforeStart
            return it
        }
        lateinit var entry: Entry
        val player = SurahTafsirSpeechPlayer(scope, engineFactory(), BengaliIbnKathir.ayahCounts[surah - 1],
            PublishedTafsirs.forBook(book)?.languageCode ?: "ar", loadAyah, {
                entry.beforeStart()
                activate(entry)
                onActivate()
            })
        entry = Entry(surah, book, title, player).also { it.beforeStart = beforeStart }
        return entry
    }

    private fun activate(entry: Entry) {
        observer?.cancel()
        val old = current.value?.entry
        if (old !== entry) { old?.player?.stop(); if (old != null && old.attachments == 0) old.player.release() }
        current.value = Playback(entry, entry.player.state.value, entry.player.currentAyahs.value)
        observer = scope.launch {
            combine(entry.player.state, entry.player.currentAyahs) { speech, ayahs -> Playback(entry, speech, ayahs) }
                .collect { update ->
                    if (current.value?.entry !== entry) return@collect
                    if (update.speech.status in listOf(TafsirSpeechStatus.IDLE, TafsirSpeechStatus.ERROR)) {
                        current.value = null
                        if (entry.attachments == 0) entry.player.release()
                        cancel()
                    } else current.value = update
                }
        }
    }
    fun detach(entry: Entry) {
        entry.attachments = (entry.attachments - 1).coerceAtLeast(0)
        if (entry.attachments == 0 && current.value?.entry !== entry) entry.player.release()
    }
    fun stop() {
        observer?.cancel(); observer = null
        val entry = current.value?.entry
        current.value = null
        entry?.player?.stop()
        if (entry != null && entry.attachments == 0) entry.player.release()
    }
}

object SurahTafsirPlayback {
    val owner = SurahTafsirPlaybackOwner(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
}

