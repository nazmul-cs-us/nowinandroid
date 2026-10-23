package com.starception.submission.shared.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ReadingTilePlaybackTest {
    @Test
    fun playsResolvedRecordingAndPublishesTheSameLocalUrl() {
        val events = mutableListOf<String>()
        runImmediate {
            startReadingRecitation(
                surahNumber = 31,
                language = "bn",
                resolve = { number, language ->
                    assertEquals(31, number)
                    assertEquals("bn", language)
                    events += "resolve"
                    "file:///cache/031.ios.m4a"
                },
                play = { url -> events += "play:$url"; true },
                onStarted = { url -> events += "publish:$url" },
            )
        }
        assertEquals(
            listOf("resolve", "play:file:///cache/031.ios.m4a", "publish:file:///cache/031.ios.m4a"),
            events,
        )
    }

    @Test
    fun missingRecordingDoesNotTryTheOldStreamingUrlOrPublishPlayback() {
        var started = false
        assertFailsWith<IllegalStateException> {
            runImmediate {
                startReadingRecitation(1, "en", { _, _ -> null }, { started = true; true }, { started = true })
            }
        }
        assertFalse(started)
    }

    @Test
    fun failedPlaybackDoesNotPublishAPlayingSurah() {
        var published = false
        assertFailsWith<IllegalStateException> {
            runImmediate {
                startReadingRecitation(1, "ar", { _, _ -> "file:///cache/001.mp3" }, { false }, { published = true })
            }
        }
        assertFalse(published)
    }

    @Test
    fun leavingTheTileDuringPreparationPreventsPlayback() {
        val request = Job()
        var started = false
        assertFailsWith<CancellationException> {
            runImmediate(request) {
                startReadingRecitation(
                    1,
                    "en",
                    { _, _ -> request.cancel(); "file:///cache/001.ios.m4a" },
                    { started = true; true },
                    { started = true },
                )
            }
        }
        assertFalse(started)
    }

    /** These fake resolvers complete synchronously; no dispatcher or platform player is needed. */
    private fun runImmediate(context: CoroutineContext = EmptyCoroutineContext, action: suspend () -> Unit) {
        var outcome: Result<Unit>? = null
        action.startCoroutine(object : Continuation<Unit> {
            override val context = context
            override fun resumeWith(result: Result<Unit>) { outcome = result }
        })
        checkNotNull(outcome).getOrThrow()
    }
}
