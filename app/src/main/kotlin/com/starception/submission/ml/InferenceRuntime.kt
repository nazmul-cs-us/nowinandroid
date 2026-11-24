package com.starception.submission.ml

import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Reverify changed app-private artifacts, not the same gigabyte on every model turn. */
internal class ModelVerificationCache {
    private data class Stamp(val bytes: Long, val modified: Long, val expected: String)
    private val verified = mutableMapOf<String, Stamp>()

    @Synchronized
    fun verify(file: File, expected: String, hash: (File) -> String): Boolean {
        if (!file.isFile) return false
        val stamp = Stamp(file.length(), file.lastModified(), expected)
        if (stamp.modified > 0 && verified[file.absolutePath] == stamp) return true
        if (hash(file) != expected) {
            verified.remove(file.absolutePath)
            return false
        }
        verified[file.absolutePath] = stamp
        return true
    }
}

/** A cancellable wait instead of blocking Process.waitFor for up to two minutes. */
internal suspend fun awaitInferenceExit(process: Process, timeoutMillis: Long): Boolean =
    withTimeoutOrNull(timeoutMillis) {
        while (process.isAlive) delay(50)
        true
    } ?: false

internal fun stopInferenceProcess(process: Process) {
    if (process.isAlive) runCatching { process.destroyForcibly() }
    runCatching { process.inputStream.close() }
    runCatching { process.errorStream.close() }
    runCatching { process.outputStream.close() }
}
