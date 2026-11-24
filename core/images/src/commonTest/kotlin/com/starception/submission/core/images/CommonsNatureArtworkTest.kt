package com.starception.submission.core.images

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommonsNatureArtworkTest {
    private val fixture = """{"query":{"pages":{"42":{"pageid":42,"title":"File:Bamboo forest.jpg","imageinfo":[{"width":2400,"height":3600,"thumbwidth":2160,"thumbheight":3240,"mime":"image/jpeg","thumburl":"https://thumb.wikimedia.org/wikipedia/commons/1/12/forest/2160px-forest.jpg","descriptionurl":"https://commons.wikimedia.org/wiki/File:Bamboo_forest.jpg","extmetadata":{"Artist":{"value":"<a href='https://example.com'>Jane &amp; John</a>"},"LicenseShortName":{"value":"CC BY 4.0"},"LicenseUrl":{"value":"https://creativecommons.org/licenses/by/4.0/"},"Restrictions":{"value":""}}}]}}}}"""

    @Test fun commonsPreservesPhotographerLicenseAndSourceWithoutAutomaticallyApprovingImages() {
        val candidate = parseArtworkReviewCandidates(ArtworkTheme.NATURE, fixture).single()
        assertEquals("commons-42", candidate.id)
        assertEquals("Jane & John · Wikimedia Commons", candidate.artist)
        assertEquals("CC BY 4.0", candidate.licenseName)
        assertEquals(ArtworkTheme.NATURE, candidate.theme)
        assertEquals("https://commons.wikimedia.org/wiki/File:Bamboo_forest.jpg", candidate.sourceUrl)
        assertTrue(allowedArtworkReviewDownload(candidate.imageUrl))
    }

    @Test fun rejectsLandscapeLowResolutionRestrictedAndUnlicensedFiles() {
        for (invalid in listOf(
            fixture.replace("\"height\":3600", "\"height\":800"),
            fixture.replace("\"width\":2400", "\"width\":600"),
            fixture.replace("\"thumbwidth\":2160", "\"thumbwidth\":960"),
            fixture.replace("image/jpeg", "image/svg+xml"),
            fixture.replace("\"Restrictions\":{\"value\":\"\"}", "\"Restrictions\":{\"value\":\"personality rights\"}"),
            fixture.replace("CC BY 4.0", "All rights reserved"),
            fixture.replace("/licenses/by/4.0/", "/licenses/by-nc/4.0/"),
            fixture.replace("https://thumb.wikimedia.org/", "https://thumb.wikimedia.org.evil.example/"),
        )) assertTrue(parseArtworkReviewCandidates(ArtworkTheme.NATURE, invalid).isEmpty())
    }

    @Test fun rejectsSmallImagesEvenWhenTheyAreTechnicallyPortrait() {
        assertFalse(highResolutionNatureWallpaper(900, 1600))
        assertFalse(highResolutionNatureWallpaper(1000, 1400))
        assertFalse(highResolutionNatureWallpaper(1200, 3600))
        assertTrue(highResolutionNatureWallpaper(1440, 2560))
        assertTrue(highResolutionNatureWallpaper(2160, 3240))
        assertTrue(natureCommonsSearchUrl(1).contains("iiurlheight=3840"))
    }

    @Test fun natureSearchUsesVariedCommonsTopicsInsteadOfRandomPhotoFeeds() {
        assertTrue(natureCommonsSearchUrl(1).contains("commons.wikimedia.org/w/api.php?"))
        assertTrue(natureCommonsSearchUrl(1).contains("forest%20mist"))
        assertTrue(natureCommonsSearchUrl(6).contains("bamboo%20forest"))
        assertTrue(natureCommonsSearchUrl(7).contains("gsroffset=20"))
        assertTrue(artworkReviewMetadataUrl(natureCommonsSearchUrl(1)))
        assertFalse(allowedArtworkDownload(natureCommonsSearchUrl(1)))
        assertFalse(artworkReviewMetadataUrl("https://commons.wikimedia.org.evil.example/w/api.php?x=1"))
    }
}
