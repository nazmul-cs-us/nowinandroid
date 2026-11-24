package com.starception.submission.ml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class InferenceRuntimeTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun unchangedVerifiedFileIsNotHashedAgainButChangedFileMustBeReverified() {
        val file = temporary.newFile("model.gguf").apply { writeText("original model") }
        fun digest() = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        val expected = digest()
        val cache = ModelVerificationCache()
        var checks = 0
        val hash: (java.io.File) -> String = { checks++; digest() }
        assertTrue(cache.verify(file, expected, hash))
        assertTrue(cache.verify(file, expected, hash))
        assertEquals(1, checks)
        val modified = file.lastModified()
        file.writeText("modified model")
        assertTrue(file.setLastModified(modified + 2000))
        assertFalse(cache.verify(file, expected, hash))
        assertEquals(2, checks)
        assertTrue(cache.verify(file, digest(), hash))
        assertEquals(3, checks)
    }

    @Test fun cancellingTheRequestStopsItsNativeProcessPromptly() = runTest {
        val process = FakeProcess()
        val job = launch {
            try { awaitInferenceExit(process, 120_000) }
            finally { stopInferenceProcess(process) }
        }
        runCurrent()
        job.cancel()
        runCurrent()
        assertTrue(job.isCancelled)
        assertTrue(process.destroyed)
        assertFalse(process.isAlive)
    }

    @Test fun timeoutStopsTheProcessWithoutBlockingTheCoroutineScheduler() = runTest {
        val process = FakeProcess()
        try { assertFalse(awaitInferenceExit(process, 100)) }
        finally { stopInferenceProcess(process) }
        assertTrue(process.destroyed)
    }

    @Test fun normallyCompletedProcessDoesNotNeedToBeKilled() = runTest {
        val process = FakeProcess()
        val result = async { awaitInferenceExit(process, 10_000) }
        runCurrent()
        process.alive = false
        advanceTimeBy(50)
        runCurrent()
        assertTrue(result.await())
        stopInferenceProcess(process)
        assertFalse(process.destroyed)
    }

    private class FakeProcess : Process() {
        var alive = true
        var destroyed = false
        private val input = ByteArrayInputStream(byteArrayOf())
        private val error = ByteArrayInputStream(byteArrayOf())
        private val output = ByteArrayOutputStream()
        override fun getInputStream() = input
        override fun getErrorStream() = error
        override fun getOutputStream() = output
        override fun isAlive() = alive
        override fun waitFor(): Int = exitValue()
        override fun exitValue(): Int = if (alive) throw IllegalThreadStateException() else 0
        override fun destroy() { destroyed = true; alive = false }
        override fun destroyForcibly(): Process { destroy(); return this }
    }
}
