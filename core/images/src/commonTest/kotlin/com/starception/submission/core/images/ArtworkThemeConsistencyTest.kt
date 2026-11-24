package com.starception.submission.core.images

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtworkThemeConsistencyTest {
    @Test fun offlineNatureNeverFallsBackToIslamicArtwork() {
        for (page in 0..4) {
            assertEquals(HomeArtworkSubject.CALM_NATURE, bundledHomeArtwork(page, ArtworkTheme.NATURE)?.subject)
            assertNotEquals(HomeArtworkSubject.CALM_NATURE, bundledHomeArtwork(page, ArtworkTheme.ISLAMIC)?.subject)
            assertNull(bundledHomeArtwork(page, ArtworkTheme.SPACE))
        }
    }

    @Test fun restoredCollectionOnlyExposesImagesMatchingTheSelectedTheme() {
        fun image(id: String, theme: ArtworkTheme?) = OnlineHomeArtwork(id, id, "Photographer",
            "https://example.com/source", "https://example.com/image", theme = theme)
        val state = HomeArtworkCollectionState(listOf(image("nature", ArtworkTheme.NATURE),
            image("islamic", ArtworkTheme.ISLAMIC), image("legacy", null)))
        assertEquals(listOf("nature"), state.forTheme(ArtworkTheme.NATURE).artworks.map { it.id })
        assertEquals(listOf("islamic"), state.forTheme(ArtworkTheme.ISLAMIC).artworks.map { it.id })
        assertTrue(state.forTheme(ArtworkTheme.SPACE).artworks.isEmpty())
        assertEquals(3, state.artworks.size) // Keep the cached originals intact for theme switches.
    }
}
