package com.starception.submission.feature.quran

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/** Persistent last-good metadata; portrait bytes are bundled separately for offline use. */
class ReciterCatalogCache<T>(
    private val read: () -> String?,
    private val write: (String) -> Unit,
    private val readUpdatedAt: () -> Long?,
    private val writeUpdatedAt: (Long) -> Unit,
    private val parse: (String) -> List<T>,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutex = Mutex()

    suspend fun load(forceRefresh: Boolean = false, download: suspend () -> String): List<T> = mutex.withLock {
        val saved = runCatching { read()?.let(parse).orEmpty() }.getOrDefault(emptyList())
        val updated = runCatching(readUpdatedAt).getOrNull()
        val age = updated?.let { now() - it }
        if (!forceRefresh && saved.isNotEmpty() && age != null && age in 0 until DAY_MILLIS) return@withLock saved
        try {
            val body = download()
            val latest = parse(body)
            check(latest.isNotEmpty()) { "The reciter catalog was empty." }
            // Validate before replacing the last good copy or marking it fresh.
            write(body)
            writeUpdatedAt(now())
            latest
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (saved.isNotEmpty()) saved else throw failure
        }
    }

    private companion object { const val DAY_MILLIS = 24 * 60 * 60 * 1000L }
}
