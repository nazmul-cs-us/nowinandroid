package com.starception.submission.core.images

import kotlin.test.*

class ReviewedArtworkFeedTest {
    private val feed = """{"schemaVersion":1,"artworks":[{"approved":true,"id":"test1","title":"Test art","credit":"Test artist","sourceUrl":"https://www.metmuseum.org/art/collection/search/1","imageUrl":"https://images.metmuseum.org/CRDImages/is/web-large/test.jpg","sha256":"${"0".repeat(64)}","theme":"ISLAMIC","licenseName":"CC0","licenseUrl":"https://creativecommons.org/publicdomain/zero/1.0/"}]}"""

    @Test fun readsApprovedRemoteDataWithoutAnEmbeddedImageCatalog() {
        assertEquals("test1", parseReviewedArtworkFeed(feed).single().artwork.id)
        assertTrue(parseReviewedArtworkFeed(feed.replace("\"approved\":true", "\"approved\":false")).isEmpty())
    }
    @Test fun rejectsUnsafeImagesInvalidPinsAndUnknownThemes() {
        assertFails { parseReviewedArtworkFeed(feed.replace("\"test1\"", "\"../test1\"")) }
        assertFails { parseReviewedArtworkFeed(feed.replace("https://images.metmuseum.org", "http://images.metmuseum.org")) }
        assertFails { parseReviewedArtworkFeed(feed.replace("0".repeat(64), "invalid")) }
        assertFails { parseReviewedArtworkFeed(feed.replace("\"ISLAMIC\"", "\"OTHER\"")) }
    }
    @Test fun sha256MatchesTheStandardVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            artworkSha256("abc".encodeToByteArray()))
    }
    @Test fun clevelandOpenAccessImagesStillRequireExplicitApprovalAndAnExactHost() {
        val cleveland = feed.replace("https://images.metmuseum.org/CRDImages/is/web-large/test.jpg",
            "https://openaccess-cdn.clevelandart.org/2004.70/2004.70_web.jpg")
        assertEquals(1, parseReviewedArtworkFeed(cleveland).size)
        assertTrue(parseReviewedArtworkFeed(cleveland.replace("\"approved\":true", "\"approved\":false")).isEmpty())
        assertFails { parseReviewedArtworkFeed(cleveland.replace("openaccess-cdn.clevelandart.org/", "openaccess-cdn.clevelandart.org.evil.example/")) }
    }
}
