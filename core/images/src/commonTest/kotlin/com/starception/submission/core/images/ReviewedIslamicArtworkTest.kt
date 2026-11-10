package com.starception.submission.core.images

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewedIslamicArtworkTest {
    @Test fun approvedCatalogHasUniquePinnedImagesFromTheMetIslamicArtCollection() {
        assertEquals(20, reviewedIslamicArtwork.size)
        assertEquals(20, reviewedIslamicArtwork.map { it.artwork.id }.toSet().size)
        assertEquals(20, reviewedIslamicArtwork.map { it.sha256 }.toSet().size)
        for (entry in reviewedIslamicArtwork) {
            assertTrue(entry.artwork.id.startsWith("met-"))
            assertTrue(entry.artwork.sourceUrl.startsWith("https://www.metmuseum.org/art/collection/search/"))
            assertTrue(entry.artwork.imageUrl.startsWith("https://images.metmuseum.org/CRDImages/is/web-large/"))
            assertEquals(64, entry.sha256.length)
            assertTrue(entry.sha256.all { it in '0'..'9' || it in 'a'..'f' })
        }
    }

    @Test fun sha256MatchesTheStandardVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            artworkSha256("abc".encodeToByteArray()))
    }
}
