package com.starception.submission.feature.prayertimes

import android.app.Application
import android.graphics.Bitmap
import com.starception.submission.core.images.HomeArtworkPlatform
import com.starception.submission.core.images.HomeArtworkRepository
import com.starception.submission.core.images.OnlineHomeArtwork
import com.starception.submission.core.images.ReviewedHomeArtwork
import com.starception.submission.core.images.ArtworkCrop
import com.starception.submission.core.images.ArtworkTheme
import com.starception.submission.core.images.HOME_ARTWORK_FEED_URL
import org.json.JSONArray
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
        var corruptImages = false
        var remoteCatalog: List<ReviewedHomeArtwork>? = null
        var pauseImages: CompletableDeferred<Unit>? = null
        private val imageBytes = ByteArrayOutputStream().use { output ->
            val image = Bitmap.createBitmap(128, 192, Bitmap.Config.ARGB_8888)
            image.eraseColor(android.graphics.Color.GREEN)
            image.compress(Bitmap.CompressFormat.PNG, 100, output)
            image.recycle()
            output.toByteArray()
        }
        val catalog by lazy {
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(imageBytes)
                .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            (1..40).map { i -> ReviewedHomeArtwork(OnlineHomeArtwork("test$i", "Quiet Valley $i", "Test artist",
                "https://www.metmuseum.org/art/collection/search/$i",
                "https://images.metmuseum.org/CRDImages/is/web-large/test$i.jpg"), hash) }
        }
        override suspend fun download(url: String, maximumBytes: Int): ByteArray {
            requests += url
            if (offline) throw IOException("Offline")
            if (url == HOME_ARTWORK_FEED_URL) {
                val rows = (remoteCatalog ?: catalog).map { entry ->
                    val a = entry.artwork
                    JSONObject().put("approved", true).put("id", a.id).put("title", a.title).put("credit", a.artist)
                        .put("sourceUrl", a.sourceUrl).put("imageUrl", a.imageUrl).put("sha256", entry.sha256)
                        .put("theme", entry.theme.name).put("licenseName", a.licenseName).put("licenseUrl", a.licenseUrl)
                }
                return JSONObject().put("schemaVersion", 1).put("artworks", JSONArray(rows)).toString().encodeToByteArray()
            }
            pauseImages?.await()
            if (++imageRequests > failAfterImages) throw IOException("Image download failed")
            return if (corruptImages) imageBytes + byteArrayOf(0) else imageBytes
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
        val repository = HomeArtworkRepository(platform, this, chooseOffset = { 0 }, ioDispatcher = StandardTestDispatcher(testScheduler), catalog = platform.catalog)
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

    @Test fun refreshPullsTheUpdatedRemoteFeedInsteadOfAnEmbeddedCatalog() = runTest {
        val platform = Platform()
        platform.remoteCatalog = platform.catalog.take(5)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = HomeArtworkRepository(platform, this, chooseOffset = { 0 }, ioDispatcher = dispatcher)
        repository.update(0, frozen = false)
        advanceUntilIdle()
        val first = repository.state.value.artworks.map { it.id }
        assertEquals(5, first.size)
        platform.remoteCatalog = platform.catalog.drop(5).take(5)
        repository.update(1, frozen = true, manualRevision = 1)
        advanceUntilIdle()
        assertEquals(5, repository.state.value.artworks.size)
        assertTrue(first.toSet().intersect(repository.state.value.artworks.map { it.id }.toSet()).isEmpty())
        assertEquals(2, platform.requests.count { it == HOME_ARTWORK_FEED_URL })
        platform.offline = true
        val requests = platform.requests.size
        val restored = HomeArtworkRepository(platform, this, ioDispatcher = dispatcher)
        restored.update(1, frozen = true)
        advanceUntilIdle()
        assertEquals(repository.state.value.artworks.map { it.id }, restored.state.value.artworks.map { it.id })
        assertEquals(requests, platform.requests.size)
    }

    @Test fun remoteSyncHonoursTheSelectedTheme() = runTest {
        val platform = Platform()
        platform.remoteCatalog = platform.catalog.take(5).map { it.copy(theme = ArtworkTheme.SPACE) }
        val repository = HomeArtworkRepository(platform, this, ioDispatcher = StandardTestDispatcher(testScheduler))
        repository.update(0, frozen = false, theme = ArtworkTheme.SPACE)
        advanceUntilIdle()
        assertEquals(5, repository.state.value.artworks.size)
    }

    @Test fun legacyCacheIsRevalidatedByTheFeedAndKeptWhenNoNewApprovedImagesExist() = runTest {
        val platform = Platform()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = HomeArtworkRepository(platform, this, chooseOffset = { 0 }, ioDispatcher = dispatcher, catalog = platform.catalog.take(5))
        first.update(0, frozen = false)
        advanceUntilIdle()
        val ids = first.state.value.artworks.map { it.id }
        val legacy = JSONObject(requireNotNull(platform.metadata)).apply { remove("reviewedCatalog") }
        platform.metadata = legacy.toString()
        platform.remoteCatalog = platform.catalog.take(5)
        val restored = HomeArtworkRepository(platform, this, ioDispatcher = dispatcher)
        restored.update(1, frozen = true, manualRevision = 1)
        advanceUntilIdle()
        assertEquals(ids, restored.state.value.artworks.map { it.id })
        assertNotNull(restored.state.value.error)
        assertEquals(5, platform.imageRequests)
    }

    @Test fun multipleHomeAndSettingsConsumersCannotStartDuplicateDownloads() = runTest {
        val platform = Platform()
        val repository = HomeArtworkRepository(platform, this, chooseOffset = { 0 }, ioDispatcher = StandardTestDispatcher(testScheduler), catalog = platform.catalog)
        repeat(10) { repository.update(0, frozen = false) }
        advanceUntilIdle()
        repeat(10) { repository.update(0, frozen = false) }
        advanceUntilIdle()
        assertEquals(5, platform.imageRequests)
    }

    @Test fun keepCurrentMakesNoNetworkRequestsAndRestoresCachedArtOffline() = runTest {
        val platform = Platform()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = HomeArtworkRepository(platform, this, chooseOffset = { 0 }, ioDispatcher = dispatcher, catalog = platform.catalog)
        first.update(0, frozen = false)
        advanceUntilIdle()
        val ids = first.state.value.artworks.map { it.id }
        platform.offline = true
        val requests = platform.requests.size
        val restored = HomeArtworkRepository(platform, this, chooseOffset = { 0 }, ioDispatcher = dispatcher, catalog = platform.catalog)
        restored.update(50, frozen = true)
        advanceUntilIdle()
        assertEquals(ids, restored.state.value.artworks.map { it.id })
        assertEquals(requests, platform.requests.size)
        assertFalse(restored.state.value.loading)
    }

    @Test fun scheduledChangeFetchesANewSetButAFailedDownloadPreservesTheWholeOldSet() = runTest {
        val platform = Platform()
        val repository = HomeArtworkRepository(platform, this, chooseOffset = { 0 }, ioDispatcher = StandardTestDispatcher(testScheduler), catalog = platform.catalog)
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
        val repository = HomeArtworkRepository(platform, this, chooseOffset = { 0 }, ioDispatcher = StandardTestDispatcher(testScheduler), catalog = platform.catalog)
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
        val repository = HomeArtworkRepository(platform, this, chooseOffset = { 0 }, ioDispatcher = StandardTestDispatcher(testScheduler), catalog = platform.catalog)
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

    @Test fun changedImageBytesCannotBypassTheVisualReviewOrReplaceTheCurrentSet() = runTest {
        val platform = Platform()
        val repository = HomeArtworkRepository(platform, this, chooseOffset = { 0 },
            ioDispatcher = StandardTestDispatcher(testScheduler), catalog = platform.catalog)
        repository.update(0, frozen = false)
        advanceUntilIdle()
        val old = repository.state.value.artworks.map { it.id }
        platform.corruptImages = true
        repository.update(1, frozen = false)
        advanceUntilIdle()
        assertEquals(old, repository.state.value.artworks.map { it.id })
        assertEquals(old.toSet(), platform.images.keys)
        assertNotNull(repository.state.value.error)
    }

    @Test fun exhaustingReviewedArtNeverRepeatsImagesOrFallsBackToLiveSearch() = runTest {
        val platform = Platform()
        val repository = HomeArtworkRepository(platform, this, chooseOffset = { 0 },
            ioDispatcher = StandardTestDispatcher(testScheduler), catalog = platform.catalog.take(5))
        repository.update(0, frozen = false)
        advanceUntilIdle()
        val old = repository.state.value.artworks.map { it.id }
        val requests = platform.requests.size
        repository.update(1, frozen = true, manualRevision = 1)
        advanceUntilIdle()
        assertEquals(old, repository.state.value.artworks.map { it.id })
        assertEquals(requests, platform.requests.size)
        assertTrue(repository.state.value.error.orEmpty().contains("all available reviewed Islamic artwork"))
        assertTrue(platform.requests.all { it.startsWith("https://images.metmuseum.org/") })
    }

    @Test fun legacyGettySearchCachesAreNotDisplayedEvenInKeepCurrentMode() = runTest {
        val platform = Platform()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = HomeArtworkRepository(platform, this, chooseOffset = { 0 }, ioDispatcher = dispatcher, catalog = platform.catalog)
        first.update(0, frozen = false)
        advanceUntilIdle()
        val legacy = JSONObject(requireNotNull(platform.metadata))
        legacy.getJSONArray("artworks").getJSONObject(0).put("id", "107JW5")
            .put("source", "https://www.getty.edu/art/collection/object/107JW5")
        platform.metadata = legacy.toString()
        val requests = platform.requests.size
        val restored = HomeArtworkRepository(platform, this, ioDispatcher = dispatcher, catalog = platform.catalog)
        restored.update(0, frozen = true)
        advanceUntilIdle()
        assertTrue(restored.state.value.artworks.isEmpty())
        assertEquals(requests, platform.requests.size)
    }

    @Test fun restoringAlsoVerifiesPinnedImageBytes() = runTest {
        val platform = Platform()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = HomeArtworkRepository(platform, this, chooseOffset = { 0 }, ioDispatcher = dispatcher, catalog = platform.catalog)
        first.update(0, frozen = false)
        advanceUntilIdle()
        val id = first.state.value.artworks.first().id
        platform.images[id] = requireNotNull(platform.images[id]) + byteArrayOf(0)
        val restored = HomeArtworkRepository(platform, this, ioDispatcher = dispatcher, catalog = platform.catalog)
        restored.update(0, frozen = true)
        advanceUntilIdle()
        assertTrue(restored.state.value.artworks.isEmpty())
    }

    @Test fun reviewedCropIsAppliedOnceForDownloadsAndRestoredCaches() = runTest {
        val platform = Platform()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val catalog = platform.catalog.map { it.copy(crop = ArtworkCrop(.25f, .25f, .75f, .75f)) }
        val first = HomeArtworkRepository(platform, this, chooseOffset = { 0 }, ioDispatcher = dispatcher, catalog = catalog)
        first.update(0, frozen = false)
        advanceUntilIdle()
        assertEquals(64, first.state.value.artworks.first().bitmap?.width)
        assertEquals(96, first.state.value.artworks.first().bitmap?.height)
        val restored = HomeArtworkRepository(platform, this, ioDispatcher = dispatcher, catalog = catalog)
        restored.update(0, frozen = true)
        advanceUntilIdle()
        assertEquals(64, restored.state.value.artworks.first().bitmap?.width)
        assertEquals(96, restored.state.value.artworks.first().bitmap?.height)
    }
}
