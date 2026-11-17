package com.starception.submission.feature.quran

import org.junit.Assert.*
import org.junit.Test

class AyahSnapDistanceTest {
    @Test fun nearestBoundaryRespectsTheToolbarInset() {
        assertEquals(16f, ayahSnapDistance(listOf(-120, 80, 400), 64, 96)!!, 0f)
    }
    @Test fun farAwayBoundariesDoNotPullTheReaderOutOfAPassage() {
        assertNull(ayahSnapDistance(listOf(-1000, 600), 64, 96))
        assertNull(ayahSnapDistance(emptyList(), 64, 96))
    }
}
