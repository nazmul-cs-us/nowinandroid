package com.starception.submission.family

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FamilyVisibleReadReceiptsTest {
    private val incoming = FamilyChatMessage("visible", "sender", "recipient", "Fixture", 1, 1000)

    @Test fun unchangedVisibleMessagesRetryUntilServerSeenAcknowledgementArrives() = runTest {
        var rows = listOf(incoming)
        var attempts = 0
        val task = launch {
            acknowledgeVisibleChat(listOf("visible"), "recipient", { rows }) {
                attempts++
                if (attempts == 2) rows = listOf(incoming.copy(seenAt = 2000))
            }
        }
        advanceUntilIdle()
        assertEquals(2, attempts)
        assertTrue(task.isCompleted)
    }

    @Test fun leavingChatCancelsRetriesAndNeverMarksOffscreenMessages() = runTest {
        var attempts = 0
        val task = launch {
            acknowledgeVisibleChat(listOf("visible"), "recipient", { listOf(incoming, incoming.copy(id = "offscreen")) }) {
                assertEquals(listOf("visible"), it)
                attempts++
            }
        }
        runCurrent()
        task.cancel()
        advanceTimeBy(12_000)
        runCurrent()
        assertEquals(1, attempts)
    }
}
