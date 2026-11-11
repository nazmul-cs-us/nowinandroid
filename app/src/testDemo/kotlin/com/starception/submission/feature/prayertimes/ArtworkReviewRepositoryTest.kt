package com.starception.submission.feature.prayertimes

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.starception.submission.core.images.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
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
    override suspend fun download(url: String, maximumBytes: Int): ByteArray {
        requests += url
        if (offline) throw IOException("Offline fixture")
        if (url == HOME_ARTWORK_FEED_URL) return """{"schemaVersion":1,"artworks":[]}""".encodeToByteArray()
        if (url.startsWith("https://picsum.photos/v2/list?")) {
            val page = URI(url).query.split('&').first { it.startsWith("page=") }.substringAfter('=').toInt()
            return JSONArray((1..20).map { n -> JSONObject().put("id", (page * 20 + n).toString())
                .put("author", "Fixture photographer").put("url", "https://unsplash.com/photos/fixture-$n") }).toString().encodeToByteArray()
        }
        pauseImages?.await()
        if (corruptImages) return byteArrayOf(1, 2, 3)
        return ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(if (oversizedImages) 2000 else 1000, 1000, Bitmap.Config.ARGB_8888)
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
