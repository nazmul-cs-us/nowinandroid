package com.starception.submission.download

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryDownloadJobsTest {
    @Test fun closingTheSheetDoesNotCancelTheAppOwnedTransfer() = runTest {
        val jobs = CategoryDownloadJobs(backgroundScope)
        val finish = CompletableDeferred<Unit>()
        var completed = false
        val sheet = launch {
            jobs.launch("model_now_nudge") { finish.await(); completed = true }.join()
        }
        runCurrent()
        sheet.cancel()
        finish.complete(Unit)
        runCurrent()
        assertTrue(completed)
    }

    @Test fun reopeningAndRequestingTheSameCategorySharesTheTransfer() = runTest {
        val jobs = CategoryDownloadJobs(backgroundScope)
        val finish = CompletableDeferred<Unit>()
        var starts = 0
        val first = jobs.launch("model_now_nudge") { starts++; finish.await() }
        val second = jobs.launch("model_now_nudge") { starts++ }
        runCurrent()
        assertSame(first, second)
        assertEquals(1, starts)
        finish.complete(Unit)
        runCurrent()
        val retry = jobs.launch("model_now_nudge") { starts++ }
        runCurrent()
        assertNotSame(first, retry)
        assertEquals(2, starts)
    }
}
