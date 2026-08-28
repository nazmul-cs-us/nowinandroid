package com.starception.submission.services

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChapterRecitationStateTest {

    @AfterTest
    fun tearDown() {
        ChapterRecitationState.clearServiceRequests()
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
