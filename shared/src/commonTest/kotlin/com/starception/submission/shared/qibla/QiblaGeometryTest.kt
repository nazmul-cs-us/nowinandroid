package com.starception.submission.shared.qibla

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QiblaGeometryTest {
    @Test
    fun knownCitiesProduceExpectedInitialBearings() {
        assertTrue(abs(qiblaBearing(43.6532, -79.3832) - 54.6) < 1.0)
        assertTrue(abs(qiblaBearing(51.5074, -0.1278) - 118.9) < 1.0)
    }

    @Test
    fun relativeTurnUsesShortestSignedDirection() {
        assertEquals(20.0, relativeQiblaTurn(10.0, 350.0), 0.001)
        assertEquals(-20.0, relativeQiblaTurn(350.0, 10.0), 0.001)
        assertEquals(0.0, relativeQiblaTurn(90.0, 90.0), 0.001)
    }
}
