package com.starception.submission.services

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChapterRecitationStateTest {

    @AfterTest
    fun tearDown() {
        ChapterRecitationState.onGlobalStateChanged = null
        ChapterRecitationState.onGlobalProgressChanged = null
        ChapterRecitationState.onStateChanged = null
        ChapterRecitationState.onProgressChanged = null
        ChapterRecitationState.markStopped()
        ChapterRecitationState.clearServiceRequests()
    }

    @Test
    fun publishNotifiesIndependentGlobalMediaObservers() {
        var state: Triple<Boolean, String, String>? = null
        var progress: Pair<Int, Int>? = null
        ChapterRecitationState.onGlobalStateChanged = { playing, title, subtitle ->
            state = Triple(playing, title, subtitle)
        }
        ChapterRecitationState.onGlobalProgressChanged = { position, duration ->
            progress = position to duration
        }

        ChapterRecitationState.publish(true, "Hadith #8", "Sahih Bukhari")
        ChapterRecitationState.publishProgress(400, 1_000)

        assertEquals(Triple(true, "Hadith #8", "Sahih Bukhari"), state)
        assertEquals(400 to 1_000, progress)
        assertTrue(ChapterRecitationState.isActive)
    }

    @Test
    fun activeServiceReceivesNextSourceWithoutAnotherServiceStart() {
        var received: Triple<String, String, String>? = null
        ChapterRecitationState.onSourcePlaybackRequested = { source, title, subtitle ->
            received = Triple(source, title, subtitle)
        }

        assertTrue(
            ChapterRecitationState.requestSourcePlayback(
                source = "/audio/hadith-2.mp3",
                title = "Hadith #2",
                subtitle = "Sahih Bukhari",
            ),
        )
        assertEquals(
            Triple("/audio/hadith-2.mp3", "Hadith #2", "Sahih Bukhari"),
            received,
        )
    }

    @Test
    fun missingServiceRequestsNormalForegroundStartFallback() {
        ChapterRecitationState.clearServiceRequests()

        assertFalse(
            ChapterRecitationState.requestExternalPlayback(
                title = "Hadith #1",
                subtitle = "Sahih Bukhari",
            ),
        )
    }
}
