package com.starception.submission.download

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AndroidAssetPlatformTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val root: File
        get() = File(context.filesDir, "cdn_assets")

    @Test
    fun resumesPartialWithRangeAndIfRangeAndReportsWholeObjectProgress() = runBlocking {
        val content = "abcdefghij".toByteArray()
        val cdnKey = "test/resume.bin"
        val partial = writePartial(cdnKey, content.copyOfRange(0, 4), "\"v1\"")

        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 4-9/10")
                    .setHeader("ETag", "\"v1\"")
                    .setBody(content.copyOfRange(4, content.size).toString(Charsets.UTF_8)),
            )
            val progress = mutableListOf<Pair<Long, Long>>()

            val result = AndroidAssetPlatform(context, OkHttpClient()).downloadToTemporaryFile(
                server.url("/asset").toString(),
                cdnKey,
            ) { downloaded, total -> progress += downloaded to total }

            assertEquals(partial.absolutePath, result)
            assertTrue(content.contentEquals(partial.readBytes()))
            val request = server.takeRequest()
            assertEquals("bytes=4-", request.getHeader("Range"))
            assertEquals("\"v1\"", request.getHeader("If-Range"))
            assertEquals(4L to 10L, progress.first())
            assertEquals(10L to 10L, progress.last())
        }
        AndroidAssetPlatform.deletePartial(partial)
        Unit
    }

    @Test
    fun fullResponseReplacesPartialAndItsEtag() = runBlocking {
        val cdnKey = "test/replace.bin"
        val partial = writePartial(cdnKey, "stale".toByteArray(), "\"old\"")

        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("ETag", "\"new\"")
                    .setBody("fresh"),
            )

            AndroidAssetPlatform(context, OkHttpClient()).downloadToTemporaryFile(
                server.url("/asset").toString(),
                cdnKey,
            ) { _, _ -> }

            val request = server.takeRequest()
            assertEquals("bytes=5-", request.getHeader("Range"))
            assertEquals("\"old\"", request.getHeader("If-Range"))
            assertEquals("fresh", partial.readText())
            assertEquals("\"new\"", AndroidAssetPlatform.partialMetadataFile(partial).readText())
        }
        AndroidAssetPlatform.deletePartial(partial)
        Unit
    }

    @Test
    fun invalidContentRangeDeletesPartialAndRestartsWithoutRange() = runBlocking {
        val cdnKey = "test/invalid-range.bin"
        val partial = writePartial(cdnKey, "abcd".toByteArray(), "\"v1\"")

        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 3-9/10")
                    .setHeader("ETag", "\"v1\"")
                    .setBody("defghij"),
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody("abcdefghij"))

            val result = AndroidAssetPlatform(context, OkHttpClient()).downloadToTemporaryFile(
                server.url("/asset").toString(),
                cdnKey,
            ) { _, _ -> }

            assertNotNull(result)
            assertEquals("bytes=4-", server.takeRequest().getHeader("Range"))
            assertNull(server.takeRequest().getHeader("Range"))
            assertEquals("abcdefghij", partial.readText())
        }
        AndroidAssetPlatform.deletePartial(partial)
        Unit
    }

    @Test
    fun mismatchedRangeNotSatisfiableRestartsFromZero() = runBlocking {
        val cdnKey = "test/range-416.bin"
        val partial = writePartial(cdnKey, "abcd".toByteArray(), "\"v1\"")

        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(416)
                    .setHeader("Content-Range", "bytes */10"),
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody("abcdefghij"))

            AndroidAssetPlatform(context, OkHttpClient()).downloadToTemporaryFile(
                server.url("/asset").toString(),
                cdnKey,
            ) { _, _ -> }

            assertEquals("bytes=4-", server.takeRequest().getHeader("Range"))
            assertNull(server.takeRequest().getHeader("Range"))
            assertEquals("abcdefghij", partial.readText())
        }
        AndroidAssetPlatform.deletePartial(partial)
        Unit
    }

    @Test
    fun rangeNotSatisfiableAcceptsAlreadyCompletePartial() = runBlocking {
        val cdnKey = "test/complete-416.bin"
        val partial = writePartial(cdnKey, "abcdefghij".toByteArray(), "\"v1\"")

        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(416)
                    .setHeader("Content-Range", "bytes */10"),
            )
            val progress = mutableListOf<Pair<Long, Long>>()

            val result = AndroidAssetPlatform(context, OkHttpClient()).downloadToTemporaryFile(
                server.url("/asset").toString(),
                cdnKey,
            ) { downloaded, total -> progress += downloaded to total }

            assertEquals(partial.absolutePath, result)
            assertEquals(10L to 10L, progress.single())
            assertEquals(1, server.requestCount)
        }
        AndroidAssetPlatform.deletePartial(partial)
        Unit
    }

    @Test
    fun transientResponseKeepsPartialForNextRetry() = runBlocking {
        val cdnKey = "test/transient.bin"
        val partial = writePartial(cdnKey, "abcd".toByteArray(), "\"v1\"")

        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 4-9/10")
                    .setHeader("ETag", "\"v1\"")
                    .setBody("efghij"),
            )

            AndroidAssetPlatform(context, OkHttpClient()).downloadToTemporaryFile(
                server.url("/asset").toString(),
                cdnKey,
            ) { _, _ -> }

            assertEquals("bytes=4-", server.takeRequest().getHeader("Range"))
            assertEquals("bytes=4-", server.takeRequest().getHeader("Range"))
            assertEquals("abcdefghij", partial.readText())
        }
        AndroidAssetPlatform.deletePartial(partial)
        Unit
    }

    @Test
    fun cancellationKeepsPartialAndMetadata() = runBlocking {
        val cdnKey = "test/cancel.bin"
        val partial = writePartial(cdnKey, "abcd".toByteArray(), "\"v1\"")
        val cancellingClient = OkHttpClient.Builder()
            .addInterceptor { throw CancellationException("cancelled") }
            .build()

        assertFailsWith<CancellationException> {
            AndroidAssetPlatform(context, cancellingClient).downloadToTemporaryFile(
                "https://example.invalid/asset",
                cdnKey,
            ) { _, _ -> }
        }

        assertEquals("abcd", partial.readText())
        assertEquals("\"v1\"", AndroidAssetPlatform.partialMetadataFile(partial).readText())
        AndroidAssetPlatform.deletePartial(partial)
        Unit
    }

    @Test
    fun managerDeletionRemovesFinalPartialAndMetadataButTotalsExcludeOnlyMetadata() {
        val isolatedFiles = File(context.cacheDir, "asset-manager-${System.nanoTime()}").apply {
            mkdirs()
        }
        val isolatedContext = object : ContextWrapper(context) {
            override fun getFilesDir(): File = isolatedFiles
        }
        val isolatedRoot = File(isolatedFiles, "cdn_assets").apply { mkdirs() }
        val cdnKey = "test/delete.bin"
        val final = File(isolatedRoot, cdnKey).apply {
            parentFile?.mkdirs()
            writeText("final")
        }
        val partial = AndroidAssetPlatform.partialFile(isolatedRoot, cdnKey).apply {
            parentFile?.mkdirs()
            writeText("part")
        }
        AndroidAssetPlatform.partialMetadataFile(partial).writeText("metadata")
        val finalNamedLikeMetadata = File(isolatedRoot, "test/asset.etag").apply {
            writeText("asset")
        }
        val manager = AssetDownloadManager(isolatedContext, OkHttpClient())

        assertEquals(
            final.length() + partial.length() + finalNamedLikeMetadata.length(),
            manager.getTotalDownloadedSize(),
        )
        assertTrue(manager.deleteAsset(cdnKey))
        assertFalse(final.exists())
        assertFalse(partial.exists())
        assertFalse(AndroidAssetPlatform.partialMetadataFile(partial).exists())

        isolatedFiles.deleteRecursively()
    }

    private fun writePartial(cdnKey: String, content: ByteArray, etag: String): File {
        val partial = AndroidAssetPlatform.partialFile(root, cdnKey)
        partial.parentFile?.mkdirs()
        partial.writeBytes(content)
        AndroidAssetPlatform.partialMetadataFile(partial).writeText(etag)
        return partial
    }
}
