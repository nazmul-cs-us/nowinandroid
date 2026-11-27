package com.starception.submission.feature.hadith

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HadithPlaybackRequestTest {
    @Test
    fun delayedModelCheckCannotStartThePreviousRecordAfterSwipe() = runTest {
        var recordId = 615
        var generation = 1
        var started = false
        val available = CompletableDeferred<Boolean>()
        val request = HadithPlaybackRequest("sahih_bukhari.db", recordId, generation)
        val pending = launch {
            if (available.await() && request.isCurrent("sahih_bukhari.db", recordId, generation)) started = true
        }
        recordId = 616
        generation++
        available.complete(true)
        pending.join()
        assertFalse(started)
        assertFalse(request.isCurrent("sahih_muslim.db", 615, 1))
        assertTrue(request.isCurrent("sahih_bukhari.db", 615, 1))
    }
}
