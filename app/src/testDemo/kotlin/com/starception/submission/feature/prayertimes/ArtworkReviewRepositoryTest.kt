package com.starception.submission.feature.prayertimes

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.starception.submission.core.images.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.SocketTimeoutException
import kotlin.time.Clock
import kotlin.time.Instant
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

internal class ReviewPlatformFixture : ArtworkReviewPlatform {
    val requests = mutableListOf<String>()
    var offline = false
    var pauseImages: CompletableDeferred<Unit>? = null
    var corruptImages = false
    var oversizedImages = false
    var landscapeImages = false
    var natureMetadataFailure: Exception? = null
    var pauseNatureMetadata: CompletableDeferred<Unit>? = null
    var unavailablePreviews = 0
    override suspend fun download(url: String, maximumBytes: Int): ByteArray {
        requests += url
        if (offline) throw IOException("Offline fixture")
        if (url == HOME_ARTWORK_FEED_URL) return """{"schemaVersion":1,"artworks":[]}""".encodeToByteArray()
        if (url.startsWith("https://picsum.photos/v2/list?")) {
            pauseNatureMetadata?.await()
            natureMetadataFailure?.let { throw it }
            val page = URI(url).query.split('&').first { it.startsWith("page=") }.substringAfter('=').toInt()
            return JSONArray((1..20).map { n -> JSONObject().put("id", (page * 20 + n).toString())
                .put("author", "Fixture photographer").put("url", "https://unsplash.com/photos/fixture-$n") }).toString().encodeToByteArray()
        }
        if (url.startsWith("https://api.openverse.org/v1/images/?")) {
            val page = URI(url).query.split('&').first { it.startsWith("page=") }.substringAfter('=').toInt()
            require(page in 1..12) { "Openverse anonymous pagination limit exceeded" }
            return JSONObject().put("results", JSONArray((1..20).map { n ->
                JSONObject().put("id", "$page-$n").put("title", "Landscape $n").put("license", "cc0")
                    .put("creator", "Fixture photographer")
                    .put("thumbnail", "https://live.staticflickr.com/fixture/$page-$n.jpg")
            })).toString().encodeToByteArray()
        }
        pauseImages?.await()
        if (unavailablePreviews > 0) {
            unavailablePreviews--
            throw IOException("Unavailable provider thumbnail")
        }
        if (corruptImages) return byteArrayOf(1, 2, 3)
        return ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(if (oversizedImages) 2000 else if (landscapeImages) 1600 else 1000, if (landscapeImages) 1000 else 1600, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(40 + (url.hashCode() and 127), 60 + ((url.hashCode() ushr 8) and 127), 80))
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkReviewRepositoryTest {
    @Test fun unavailableOpenversePreviewsDoNotPreventLoadingOtherPortraits() = runTest {
        val platform = ReviewPlatformFixture().apply {
            natureMetadataFailure = SocketTimeoutException("Synthetic timeout")
            unavailablePreviews = 3
        }
        val repository = ArtworkReviewRepository(platform, this,
            dispatcher = StandardTestDispatcher(testScheduler), initialPageOffset = 0)
        repository.fetch(ArtworkTheme.NATURE, allowUnreviewed = true)
        advanceUntilIdle()
        assertEquals(5, repository.state.value.candidates.size)
        assertNull(repository.state.value.error)
        assertTrue(repository.state.value.notice.orEmpty().contains("3 candidate preview(s) could not be loaded"))
    }

    @Test fun deepDiscoveryPageStaysWithinOpenverseAnonymousPaginationLimits() = runTest {
        val platform = ReviewPlatformFixture().apply { natureMetadataFailure = SocketTimeoutException("Synthetic timeout") }
        val clock = object : Clock { override fun now() = Instant.fromEpochSeconds(19 * 86400L) }
        val repository = ArtworkReviewRepository(platform, this, clock = clock,
            dispatcher = StandardTestDispatcher(testScheduler), initialPageOffset = 0)
        repository.fetch(ArtworkTheme.NATURE, allowUnreviewed = true)
        advanceUntilIdle()
        assertEquals(5, repository.state.value.candidates.size)
        assertNull(repository.state.value.error)
        val request = platform.requests.single { it.startsWith("https://api.openverse.org/v1/images/?") }
        assertTrue(request.contains("page=8"))
    }

    @Test fun landscapeResultsAreRejectedEvenWhenTheProviderIgnoresThePortraitRequest() = runTest {
        val platform = ReviewPlatformFixture().apply { landscapeImages = true }
        val repository = ArtworkReviewRepository(platform, this, dispatcher = StandardTestDispatcher(testScheduler))
        repository.fetch(ArtworkTheme.NATURE, allowUnreviewed = true)
        advanceUntilIdle()
        assertTrue(repository.state.value.candidates.isEmpty())
        assertTrue(repository.state.value.error.orEmpty().contains("No portrait images"))
    }

    @Test fun picsumMetadataTimeoutFallsBackToAnIndependentNatureSource() = runTest {
        val platform = ReviewPlatformFixture().apply { natureMetadataFailure = SocketTimeoutException("Synthetic timeout") }
        val repository = ArtworkReviewRepository(platform, this, dispatcher = StandardTestDispatcher(testScheduler))
        repository.fetch(ArtworkTheme.NATURE, allowUnreviewed = true)
        advanceUntilIdle()
        assertEquals(5, repository.state.value.candidates.size)
        assertNull(repository.state.value.error)
        assertTrue(repository.state.value.notice.orEmpty().contains("Openverse"))
        assertTrue(repository.state.value.candidates.all { it.artwork.id.startsWith("openverse-") && it.artwork.bitmap != null })
        assertTrue(platform.requests.any { it.startsWith("https://api.openverse.org/v1/images/?") && it.contains("aspect_ratio=tall") })
        assertTrue(platform.requests.any { it.startsWith("https://live.staticflickr.com/") })
        assertFalse(platform.requests.any { it.startsWith("https://picsum.photos/id/") })
    }

    @Test fun closingDuringNatureDiscoveryDoesNotStartTheFallback() = runTest {
        val platform = ReviewPlatformFixture().apply { pauseNatureMetadata = CompletableDeferred() }
        val repository = ArtworkReviewRepository(platform, this, dispatcher = StandardTestDispatcher(testScheduler))
        repository.fetch(ArtworkTheme.NATURE, allowUnreviewed = true)
        runCurrent()
        assertTrue(repository.state.value.loading)
        repository.clear()
        advanceUntilIdle()
        assertFalse(repository.state.value.loading)
        assertTrue(repository.state.value.candidates.isEmpty())
        assertFalse(platform.requests.any { it.startsWith("https://api.openverse.org/") })
    }
    @Test fun explicitlyApprovedImageIsSavedPrivatelyAndRestoredOfflineWithoutPublishing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val homePlatform = OnlineHomeArtworkTest.Platform()
        val home = HomeArtworkRepository(homePlatform, this, ioDispatcher = dispatcher)
        val platform = ReviewPlatformFixture()
        val review = ArtworkReviewRepository(platform, this, dispatcher = dispatcher)
        review.fetch(ArtworkTheme.NATURE, allowUnreviewed = true)
        advanceUntilIdle()
        val candidate = review.state.value.candidates.first()
        review.approveForDevice(candidate, ArtworkTheme.NATURE, home)
        assertEquals("personal-" + candidate.artwork.id, home.state.value.artworks.single().id)
        assertNotNull(homePlatform.personalMetadata)
        assertTrue(homePlatform.images.containsKey("personal-" + candidate.artwork.id))
        assertEquals(candidate.artwork.artist, home.state.value.artworks.single().artist)
        homePlatform.offline = true
        val restored = HomeArtworkRepository(homePlatform, this, ioDispatcher = dispatcher)
        restored.update(0, frozen = true)
        advanceUntilIdle()
        assertEquals(home.state.value.artworks.map { it.id }, restored.state.value.artworks.map { it.id })
        assertTrue(homePlatform.requests.isEmpty())
    }
    @Test fun restrictionIsSafeByDefaultAndMakesNoPreviewRequests() = runTest {
        val platform = ReviewPlatformFixture()
        val repository = ArtworkReviewRepository(platform, this, dispatcher = StandardTestDispatcher(testScheduler))
        repository.fetch(ArtworkTheme.NATURE)
        advanceUntilIdle()
        assertTrue(platform.requests.isEmpty())
        assertTrue(repository.state.value.candidates.isEmpty())
    }

    @Test fun explicitUnrestrictedReviewFetchesInternetCandidatesAndDoesNotModifyHome() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val homePlatform = OnlineHomeArtworkTest.Platform()
        val home = HomeArtworkRepository(homePlatform, this, chooseOffset = { 0 }, ioDispatcher = dispatcher, catalog = homePlatform.catalog)
        home.update(0, frozen = false)
        advanceUntilIdle()
        val saved = homePlatform.metadata
        val collection = home.state.value
        val platform = ReviewPlatformFixture()
        val repository = ArtworkReviewRepository(platform, this, dispatcher = dispatcher, initialPageOffset = 0)
        repository.fetch(ArtworkTheme.NATURE, allowUnreviewed = true)
        advanceUntilIdle()
        assertEquals(5, repository.state.value.candidates.size)
        assertTrue(platform.requests.any { it.startsWith("https://picsum.photos/v2/list?") })
        repository.state.value.candidates.forEach {
            assertNotNull(it.artwork.bitmap)
            assertTrue(it.artwork.bitmap!!.width <= 720 && it.artwork.bitmap!!.height <= 720)
            assertEquals(64, it.sha256.length)
        }
        assertSame(collection, home.state.value)
        assertEquals(saved, homePlatform.metadata)
        val first = repository.state.value.candidates.map { it.artwork.id }.toSet()
        repository.fetch(ArtworkTheme.NATURE, allowUnreviewed = true)
        advanceUntilIdle()
        assertTrue(first.intersect(repository.state.value.candidates.map { it.artwork.id }.toSet()).isEmpty())
    }

    @Test fun restrictingDuringDownloadCancelsAndClearsTheGallery() = runTest {
        val platform = ReviewPlatformFixture().apply { pauseImages = CompletableDeferred() }
        val repository = ArtworkReviewRepository(platform, this, dispatcher = StandardTestDispatcher(testScheduler))
        repository.fetch(ArtworkTheme.NATURE, allowUnreviewed = true)
        runCurrent()
        assertTrue(repository.state.value.loading)
        repository.clear()
        advanceUntilIdle()
        assertFalse(repository.state.value.loading)
        assertTrue(repository.state.value.candidates.isEmpty())
    }

    @Test fun offlineFailureDoesNotPretendToShowCachedHomeImagesAndCanRetry() = runTest {
        val platform = ReviewPlatformFixture().apply { offline = true }
        val repository = ArtworkReviewRepository(platform, this, dispatcher = StandardTestDispatcher(testScheduler))
        repository.fetch(ArtworkTheme.NATURE, allowUnreviewed = true)
        advanceUntilIdle()
        assertNotNull(repository.state.value.error)
        assertTrue(repository.state.value.candidates.isEmpty())
        platform.offline = false
        repository.fetch(ArtworkTheme.NATURE, allowUnreviewed = true)
        advanceUntilIdle()
        assertEquals(5, repository.state.value.candidates.size)
        assertNull(repository.state.value.error)
    }

    @Test fun malformedOrOversizedImagesAreNotDecodedIntoTheReviewGallery() = runTest {
        val platform = ReviewPlatformFixture().apply { corruptImages = true }
        val repository = ArtworkReviewRepository(platform, this, dispatcher = StandardTestDispatcher(testScheduler))
        repository.fetch(ArtworkTheme.NATURE, allowUnreviewed = true)
        advanceUntilIdle()
        assertTrue(repository.state.value.candidates.isEmpty())
        assertNotNull(repository.state.value.error)
        platform.corruptImages = false
        platform.oversizedImages = true
        repository.fetch(ArtworkTheme.NATURE, allowUnreviewed = true)
        advanceUntilIdle()
        assertTrue(repository.state.value.candidates.isEmpty())
        assertNotNull(repository.state.value.error)
    }
}
