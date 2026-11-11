package com.starception.submission.core.images

import kotlin.test.*

class UnreviewedArtworkTest {
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
        assertEquals("https://picsum.photos/id/88/900/1200", rows.single().imageUrl)
        // The runtime feed parser still rejects a candidate without explicit approval.
        assertTrue(parseReviewedArtworkFeed("""{"schemaVersion":1,"artworks":[{"approved":false,"id":"picsum-88"}]}""").isEmpty())
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
}
