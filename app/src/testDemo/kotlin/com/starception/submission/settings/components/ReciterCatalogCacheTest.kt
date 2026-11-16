package com.starception.submission.settings.components

import com.starception.submission.feature.quran.ReciterCatalogCache
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ReciterCatalogCacheTest {
    private class Disk {
        var body: String? = null
        var updated: Long? = null
        var now = 1_000_000L
        fun cache() = ReciterCatalogCache(
            read = { body }, write = { body = it }, readUpdatedAt = { updated },
            writeUpdatedAt = { updated = it }, now = { now },
            parse = { text: String -> when (text) {
                "malformed" -> error("Malformed catalog")
                "empty" -> emptyList()
                else -> text.split(',')
            } },
        )
    }

    @Test fun catalogSurvivesRecreationWithoutAnotherNetworkRequest() = runTest {
        val disk = Disk()
        assertEquals(listOf("Alafasy", "Sudais"), disk.cache().load { "Alafasy,Sudais" })
        disk.now += 1_000
        assertEquals(listOf("Alafasy", "Sudais"), disk.cache().load { error("Fresh cache must avoid network") })
    }
    @Test fun staleCatalogRefreshesAndForcedRefreshBypassesTheFreshnessWindow() = runTest {
        val disk = Disk()
        val cache = disk.cache()
        cache.load { "old" }
        disk.now += 24 * 60 * 60 * 1000L
        assertEquals(listOf("new"), cache.load { "new" })
        assertEquals(listOf("forced"), cache.load(forceRefresh = true) { "forced" })
    }
    @Test fun offlineEmptyAndMalformedResponsesKeepTheLastGoodCatalog() = runTest {
        val disk = Disk()
        val cache = disk.cache()
        cache.load { "saved" }
        val timestamp = disk.updated
        disk.now += 24 * 60 * 60 * 1000L
        assertEquals(listOf("saved"), cache.load { throw IOException("Offline") })
        assertEquals(listOf("saved"), cache.load { "empty" })
        assertEquals(listOf("saved"), cache.load { "malformed" })
        assertEquals("saved", disk.body)
        assertEquals(timestamp, disk.updated)
    }
    @Test fun corruptCacheAndFutureTimestampDoNotPreventAValidRefresh() = runTest {
        val disk = Disk().apply { body = "malformed"; updated = now }
        assertEquals(listOf("recovered"), disk.cache().load { "recovered" })
        disk.updated = disk.now + 1_000
        assertEquals(listOf("clock-recovered"), disk.cache().load { "clock-recovered" })
    }
    @Test fun concurrentPickersShareOneRefresh() = runTest {
        val disk = Disk()
        val cache = disk.cache()
        var requests = 0
        suspend fun fetch(): String { requests++; delay(10); return "saved" }
        val first = async { cache.load(download = ::fetch) }
        val second = async { cache.load(download = ::fetch) }
        assertEquals(first.await(), second.await())
        assertEquals(1, requests)
    }
    @Test fun cancellationIsPropagatedWithoutReplacingTheCache() = runTest {
        val disk = Disk().apply { body = "saved" }
        try {
            disk.cache().load { throw CancellationException("Closed picker") }
            fail("Cancellation should propagate")
        } catch (_: CancellationException) { }
        assertEquals("saved", disk.body)
        assertNull(disk.updated)
    }
}
