package com.starception.submission.shared.ui

import com.starception.submission.shared.audio.*
import com.starception.submission.shared.storage.InMemoryKeyValueStore
import kotlinx.coroutines.*
import kotlin.test.*

class ReciterPreviewPlayerTest {
    private val first = QuranReciterSource("1-1", "First", "https://example.org/first", setOf(1))
    private val second = QuranReciterSource("2-2", "Second", "https://example.org/second", setOf(1))

    @Test fun previewDoesNotSelectVoiceAndStopClearsPlayback() = runBlocking<Unit> {
        val preferences = QuranReciterPreferences(InMemoryKeyValueStore())
        preferences.selectSource(first)
        val library = TestLibrary()
        val preview = ReciterPreviewPlayer(this, library)
        try {
            preview.toggle(second)
            withTimeout(2000) { while (preview.loading) delay(10) }
            assertEquals(second.id, preview.activeId)
            assertEquals(first, preferences.selectedSource())
            assertEquals(listOf(second.urlForSurah(1)), library.player.urls)
            preview.stop()
            assertNull(preview.activeId)
            assertFalse(preview.loading)
            assertEquals(ReciterPreviewPlayback.Phase.ENDED, library.player.phase())
        } finally { preview.stop() }
    }

    @Test fun cancellingAnOldPreviewCannotClearTheNewPreview() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        val library = TestLibrary { source ->
            if (source?.id == first.id) gate.await()
            source?.urlForSurah(1)
        }
        val preview = ReciterPreviewPlayer(this, library)
        try {
            preview.toggle(first)
            delay(30)
            preview.toggle(second)
            withTimeout(2000) { while (preview.loading) delay(10) }
            gate.complete(Unit)
            delay(30)
            assertEquals(second.id, preview.activeId)
            assertEquals(listOf(second.urlForSurah(1)), library.player.urls)
        } finally { preview.stop() }
    }

    private class TestLibrary(
        private val resolve: suspend (QuranReciterSource?) -> String? = { it?.urlForSurah(1) },
    ) : ReciterLibrary {
        val player = TestPlayer()
        override suspend fun previewUrl(source: QuranReciterSource?) = resolve(source)
        override fun createPreviewPlayer(): ReciterPreviewPlayback = player
        override suspend fun catalog() = emptyList<QuranReciterSource>()
        override suspend fun download(source: QuranReciterSource, onCompleted: (Int, Int) -> Unit) = Unit
        override fun count(source: QuranReciterSource) = 0
        override fun bytes(source: QuranReciterSource) = 0L
        override fun delete(source: QuranReciterSource) = Unit
    }
    private class TestPlayer : ReciterPreviewPlayback {
        val urls = mutableListOf<String>()
        private var current = ReciterPreviewPlayback.Phase.ENDED
        override fun play(url: String): Boolean { urls += url; current = ReciterPreviewPlayback.Phase.PLAYING; return true }
        override fun phase() = current
        override fun stop() { current = ReciterPreviewPlayback.Phase.ENDED }
    }
}
