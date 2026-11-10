package com.starception.submission.core.images

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GettyArtworkSearchTest {
    private fun search(
        title: String = "Quiet Valley",
        license: String = "https://creativecommons.org/publicdomain/zero/1.0/",
        parent: Boolean = false,
        id: String = "test1",
        image: String = "image-1",
    ) = """{"total":1,"data":[{"id_manager_slug":"$id","primary_name":"$title","is_parent":$parent,"manifest":{"license":"$license","thumbUuid":"$image","altText":"Landscape"},"producers":[{"primary_name":"Test artist"}]}]}"""

    @Test fun acceptsCc0WithAttributionAndHttpsImageSource() {
        val (total, artworks) = parseGettyArtworkSearch(search(license = "http://creativecommons.org/publicdomain/zero/1.0/"))
        assertEquals(1, total)
        val artwork = artworks.single()
        assertEquals("Quiet Valley", artwork.title)
        assertEquals("Test artist", artwork.artist)
        assertEquals("https://www.getty.edu/art/collection/object/test1", artwork.sourceUrl)
        assertTrue(artwork.imageUrl.startsWith("https://media.getty.edu/iiif/image/"))
    }

    @Test fun rejectsUnlicensedAndParentObjects() {
        assertTrue(parseGettyArtworkSearch(search(license = "https://example.com/copyright")).second.isEmpty())
        assertTrue(parseGettyArtworkSearch(search(parent = true)).second.isEmpty())
    }

    @Test fun excludesKnownPeopleAndAnimalSubjects() {
        for (title in listOf("Portrait in a valley", "A horse in a landscape", "Children in a garden", "A man at sea")) {
            assertTrue(parseGettyArtworkSearch(search(title = title)).second.isEmpty(), title)
        }
    }

    @Test fun rejectsUnsafeObjectAndImageIdentifiers() {
        assertTrue(parseGettyArtworkSearch(search(id = "../test")).second.isEmpty())
        assertTrue(parseGettyArtworkSearch(search(image = "../image")).second.isEmpty())
    }
}
