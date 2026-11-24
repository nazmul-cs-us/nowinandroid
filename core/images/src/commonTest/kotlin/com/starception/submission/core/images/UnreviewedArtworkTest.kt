package com.starception.submission.core.images

import kotlin.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class UnreviewedArtworkTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun discoveryRejectionsAreNotMisreportedAsAnOfflinePhone() = runTest {
        for ((status, expected) in listOf(403 to "blocked this request", 429 to "is busy", 503 to "temporarily unavailable")) {
            val platform = ArtworkReviewPlatform { url, _ ->
                if (url == HOME_ARTWORK_FEED_URL) """{"schemaVersion":1,"artworks":[]}""".encodeToByteArray()
                else throw ArtworkReviewHttpException(status)
            }
            val repository = ArtworkReviewRepository(platform, this, dispatcher = StandardTestDispatcher(testScheduler))
            repository.fetch(ArtworkTheme.NATURE, allowUnreviewed = true)
            advanceUntilIdle()
            assertFalse(repository.state.value.loading)
            assertTrue(repository.state.value.candidates.isEmpty())
            assertTrue(repository.state.value.error.orEmpty().contains(expected))
        }
    }
    @Test fun reviewEndpointsAreSeparateAndCannotExtendHomesApprovedFeedAllowlist() {
        val metadata = "https://picsum.photos/v2/list?page=2&limit=20"
        assertTrue(artworkReviewMetadataUrl(metadata))
        assertTrue(allowedArtworkReviewDownload(metadata))
        assertFalse(allowedArtworkDownload(metadata))
        assertFalse(allowedArtworkReviewDownload("http://picsum.photos/v2/list?page=2"))
        assertFalse(allowedArtworkReviewDownload("https://picsum.photos.evil.example/v2/list?page=2"))
        assertFalse(allowedArtworkReviewDownload("https://example.com/image.jpg"))
    }

    @Test fun natureCandidatesKeepCreditsAndRightsButAreNeverMarkedApproved() {
        val rows = parseArtworkReviewCandidates(ArtworkTheme.NATURE,
            """[{"id":"88","author":"Photographer","url":"https://unsplash.com/photos/example"}]""")
        assertEquals("picsum-88", rows.single().id)
        assertTrue(rows.single().artist.contains("Photographer"))
        assertEquals("Unsplash License", rows.single().licenseName)
        assertEquals("https://picsum.photos/id/88/900/1600", rows.single().imageUrl)
        // The runtime feed parser still rejects a candidate without explicit approval.
        assertTrue(parseReviewedArtworkFeed("""{"schemaVersion":1,"artworks":[{"approved":false,"id":"picsum-88"}]}""").isEmpty())
    }

    @Test fun openverseNatureFallbackPreservesCreditAndFiltersOutOtherLicenses() {
        val text = """{"results":[{"id":"nature-1","title":"Quiet valley","creator":"Photographer","license":"cc0","width":1600,"height":3000,"url":"https://upload.wikimedia.org/wikipedia/commons/example.jpg","thumbnail":"https://api.openverse.org/v1/images/nature-1/thumb/"},{"id":"nature-2","title":"Other license","license":"by"}]}"""
        val image = parseArtworkReviewCandidates(ArtworkTheme.NATURE, text).single()
        assertEquals("openverse-nature-1", image.id)
        assertTrue(image.artist.contains("Photographer"))
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/example.jpg", image.imageUrl)
        assertEquals("CC0", image.licenseName)
        val metadata = "https://api.openverse.org/v1/images/?q=nature&license=cc0"
        assertTrue(artworkReviewMetadataUrl(metadata))
        assertTrue(allowedArtworkReviewDownload(metadata))
        assertFalse(allowedArtworkDownload(metadata))
    }

    @Test fun knownLandscapeMetadataIsExcludedBeforeDownloadingNatureImages() {
        val picsum = """[{"id":"88","author":"Photographer","url":"https://unsplash.com/photos/example","width":1600,"height":900}]"""
        assertTrue(parseArtworkReviewCandidates(ArtworkTheme.NATURE, picsum).isEmpty())
        val openverse = """{"results":[{"id":"photo","title":"Nature","creator":"Photographer","license":"cc0","width":3000,"height":1600,"url":"https://upload.wikimedia.org/wikipedia/commons/example.jpg"}]}"""
        assertTrue(parseArtworkReviewCandidates(ArtworkTheme.NATURE, openverse).isEmpty())
        assertEquals(1, parseArtworkReviewCandidates(ArtworkTheme.NATURE,
            openverse.replace("\"width\":3000,\"height\":1600", "\"width\":1600,\"height\":3000")).size)
    }

    @Test fun unsafeCandidateIdsAndSourceLinksAreRejected() {
        assertTrue(parseArtworkReviewCandidates(ArtworkTheme.NATURE,
            """[{"id":"../bad","author":"Photographer","url":"https://unsplash.com/photos/example"}]""").isEmpty())
        assertTrue(parseArtworkReviewCandidates(ArtworkTheme.NATURE,
            """[{"id":"88","author":"Photographer","url":"javascript:alert(1)"}]""").isEmpty())
    }

    @Test fun islamicCandidatesRequireTheReportedCc0LicenseAndCorrectDepartment() {
        val text = """{"data":[{"accession_number":"2004.70","title":"Floral tile","share_license_status":"CC0","department":"Islamic Art","creditline":"Museum collection","url":"https://clevelandart.org/art/2004.70","images":{"web":{"url":"https://openaccess-cdn.clevelandart.org/2004.70/2004.70_web.jpg"}}}]}"""
        assertEquals("cma-2004-70", parseArtworkReviewCandidates(ArtworkTheme.ISLAMIC, text).single().id)
        assertTrue(parseArtworkReviewCandidates(ArtworkTheme.ISLAMIC, text.replace("\"CC0\"", "\"Copyright\"")).isEmpty())
        assertTrue(parseArtworkReviewCandidates(ArtworkTheme.ISLAMIC, text.replace("\"Islamic Art\"", "\"Modern Art\"")).isEmpty())
    }

    @Test fun nasaPreviewsKeepTheirOwnLicenseAndRejectOversizedOrCopyrightFlaggedResults() {
        val text = """{"collection":{"items":[{"data":[{"nasa_id":"TEST","title":"Nebula","secondary_creator":"NASA/JPL"}],"links":[{"href":"https://images-assets.nasa.gov/image/TEST/TEST~small.jpg","render":"image","width":640,"height":480}]}]}}"""
        val image = parseArtworkReviewCandidates(ArtworkTheme.SPACE, text).single()
        assertEquals("NASA/JPL", image.artist)
        assertEquals("NASA media usage guidelines", image.licenseName)
        assertTrue(parseArtworkReviewCandidates(ArtworkTheme.SPACE, text.replace("\"width\":640", "\"width\":9000")).isEmpty())
        assertTrue(parseArtworkReviewCandidates(ArtworkTheme.SPACE,
            text.replace("\"nasa_id\":\"TEST\"", "\"copyright\":\"Third party\",\"nasa_id\":\"TEST\"")).isEmpty())
    }

    @Test fun nasaPreviewLinksWithoutOptionalDimensionsAreDiscoverable() {
        val text = """{"collection":{"items":[{"data":[{"nasa_id":"TEST","title":"Nebula"}],"links":[{"href":"https://images-assets.nasa.gov/image/TEST/TEST~thumb.jpg","rel":"preview","render":"image"}]}]}}"""
        assertEquals("nasa-TEST", parseArtworkReviewCandidates(ArtworkTheme.SPACE, text).single().id)
        assertTrue(parseArtworkReviewCandidates(ArtworkTheme.SPACE, text.replace("\"preview\"", "\"original\"")).isEmpty())
        assertTrue(parseArtworkReviewCandidates(ArtworkTheme.SPACE, text.replace("\"render\":\"image\"", "\"render\":\"image\",\"width\":9000")).isEmpty())
    }
}
