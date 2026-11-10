package com.starception.submission.feature.prayertimes

import android.app.Application
import android.graphics.Bitmap
import com.starception.submission.core.images.HomeArtworkPlatform
import com.starception.submission.core.images.HomeArtworkRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnlineHomeArtworkTest {
    internal class Platform : HomeArtworkPlatform {
        var metadata: String? = null
        val images = mutableMapOf<String, ByteArray>()
        val requests = mutableListOf<String>()
        var offline = false
        var failAfterImages = Int.MAX_VALUE
        var imageRequests = 0
        var commitFails = false
        var pauseImages: CompletableDeferred<Unit>? = null
        private val imageBytes = ByteArrayOutputStream().use { output ->
            val image = Bitmap.createBitmap(128, 192, Bitmap.Config.ARGB_8888)
            image.eraseColor(android.graphics.Color.GREEN)
            image.compress(Bitmap.CompressFormat.PNG, 100, output)
            image.recycle()
            output.toByteArray()
        }
        override suspend fun download(url: String, maximumBytes: Int): ByteArray {
            requests += url
            if (offline) throw IOException("Offline")
            if (url.contains("/api/search")) {
                val rows = JSONArray()
                for (i in 1..40) rows.put(JSONObject().put("id_manager_slug", "test$i")
                    .put("primary_name", "Quiet Valley $i").put("is_parent", false)
                    .put("manifest", JSONObject().put("license", "http://creativecommons.org/publicdomain/zero/1.0/")
                        .put("thumbUuid", "image-$i").put("altText", "Landscape"))
                    .put("producers", JSONArray().put(JSONObject().put("primary_name", "Test artist"))))
                return JSONObject().put("total", 40).put("data", rows).toString().encodeToByteArray()
            }
            pauseImages?.await()
            if (++imageRequests > failAfterImages) throw IOException("Image download failed")
            return imageBytes
        }
        override fun readMetadata() = metadata
        override fun writeMetadata(value: String) {
            if (commitFails) throw IOException("Disk full")
            metadata = value
        }
        override fun readImage(id: String) = images[id]
        override fun writeImage(id: String, bytes: ByteArray) { images[id] = bytes }
        override fun retainImages(ids: Set<String>) { images.keys.retainAll(ids) }
    }

    @Test fun refreshDownloadsFiveUnseenImagesInsteadOfShufflingTheBundledGallery() = runTest {
        val platform = Platform()
        val repository = HomeArtworkRepository(platform, this, ioDispatcher = StandardTestDispatcher(testScheduler))
        repository.update(0, frozen = false)
        advanceUntilIdle()
        val first = repository.state.value.artworks.map { it.id }
        assertEquals(5, first.size)
        assertEquals(5, platform.imageRequests)
        repository.update(1, frozen = true, manualRevision = 1)
        advanceUntilIdle()
        val second = repository.state.value.artworks.map { it.id }
        assertEquals(5, second.size)
        assertTrue(first.toSet().intersect(second.toSet()).isEmpty())
        assertEquals(10, platform.imageRequests)
        assertEquals(second.toSet(), platform.images.keys)
    }

    @Test fun multipleHomeAndSettingsConsumersCannotStartDuplicateDownloads() = runTest {
        val platform = Platform()
        val repository = HomeArtworkRepository(platform, this, ioDispatcher = StandardTestDispatcher(testScheduler))
        repeat(10) { repository.update(0, frozen = false) }
        advanceUntilIdle()
        repeat(10) { repository.update(0, frozen = false) }
        advanceUntilIdle()
        assertEquals(5, platform.imageRequests)
    }

    @Test fun keepCurrentMakesNoNetworkRequestsAndRestoresCachedArtOffline() = runTest {
        val platform = Platform()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = HomeArtworkRepository(platform, this, ioDispatcher = dispatcher)
        first.update(0, frozen = false)
        advanceUntilIdle()
        val ids = first.state.value.artworks.map { it.id }
        platform.offline = true
        val requests = platform.requests.size
        val restored = HomeArtworkRepository(platform, this, ioDispatcher = dispatcher)
        restored.update(50, frozen = true)
        advanceUntilIdle()
        assertEquals(ids, restored.state.value.artworks.map { it.id })
        assertEquals(requests, platform.requests.size)
        assertFalse(restored.state.value.loading)
    }

    @Test fun scheduledChangeFetchesANewSetButAFailedDownloadPreservesTheWholeOldSet() = runTest {
        val platform = Platform()
        val repository = HomeArtworkRepository(platform, this, ioDispatcher = StandardTestDispatcher(testScheduler))
        repository.update(0, frozen = false)
        advanceUntilIdle()
        val old = repository.state.value.artworks.map { it.id }
        val metadata = platform.metadata
        platform.failAfterImages = platform.imageRequests + 2
        repository.update(1, frozen = false)
        advanceUntilIdle()
        assertEquals(old, repository.state.value.artworks.map { it.id })
        assertEquals(metadata, platform.metadata)
        assertEquals(old.toSet(), platform.images.keys)
        assertNotNull(repository.state.value.error)
        assertFalse(repository.state.value.loading)
    }

    @Test fun freezingDuringAnAutomaticDownloadDoesNotSwitchTheArtworkOrRemainLoading() = runTest {
        val platform = Platform()
        val repository = HomeArtworkRepository(platform, this, ioDispatcher = StandardTestDispatcher(testScheduler))
        repository.update(0, frozen = false)
        advanceUntilIdle()
        val old = repository.state.value.artworks.map { it.id }
        platform.pauseImages = CompletableDeferred()
        repository.update(1, frozen = false)
        runCurrent()
        assertTrue(repository.state.value.loading)
        repository.update(1, frozen = true)
        advanceUntilIdle()
        assertEquals(old, repository.state.value.artworks.map { it.id })
        assertFalse(repository.state.value.loading)
    }

    @Test fun aFailedDiskCommitCannotPublishHalfACachedCollection() = runTest {
        val platform = Platform()
        val repository = HomeArtworkRepository(platform, this, ioDispatcher = StandardTestDispatcher(testScheduler))
        repository.update(0, frozen = false)
        advanceUntilIdle()
        val old = repository.state.value.artworks.map { it.id }
        platform.commitFails = true
        repository.update(1, frozen = false)
        advanceUntilIdle()
        assertEquals(old, repository.state.value.artworks.map { it.id })
        assertEquals(old.toSet(), platform.images.keys)
        assertNotNull(repository.state.value.error)
    }
}
