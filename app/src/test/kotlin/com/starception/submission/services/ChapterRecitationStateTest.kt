/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
        data class PlaybackRequest(
            val source: String,
            val title: String,
            val subtitle: String,
            val continuousHandoff: Boolean,
        )

        var received: PlaybackRequest? = null
        ChapterRecitationState.onSourcePlaybackRequested =
            { source, title, subtitle, continuousHandoff ->
                received = PlaybackRequest(source, title, subtitle, continuousHandoff)
            }

        assertTrue(
            ChapterRecitationState.requestSourcePlayback(
                source = "/audio/hadith-2.mp3",
                title = "Hadith #2",
                subtitle = "Sahih Bukhari",
                continuousHandoff = true,
            ),
        )
        assertEquals(
            PlaybackRequest(
                source = "/audio/hadith-2.mp3",
                title = "Hadith #2",
                subtitle = "Sahih Bukhari",
                continuousHandoff = true,
            ),
            received,
        )
    }

    @Test
    fun singlePlaybackRequestDoesNotUseContinuousHandoff() {
        var continuousHandoff: Boolean? = null
        ChapterRecitationState.onSourcePlaybackRequested = { _, _, _, isContinuous ->
            continuousHandoff = isContinuous
        }

        assertTrue(
            ChapterRecitationState.requestSourcePlayback(
                source = "/audio/hadith-1.mp3",
                title = "Hadith #1",
                subtitle = "Sahih Bukhari",
                continuousHandoff = false,
            ),
        )
        assertFalse(continuousHandoff ?: true)
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
