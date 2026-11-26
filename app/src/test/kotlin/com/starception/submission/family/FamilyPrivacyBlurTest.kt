package com.starception.submission.family

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyPrivacyBlurTest {
    @Test fun fullFrameFallbackRemovesHighFrequencyDetail() {
        val plane = ByteArray(64 * 48) { if ((it + it / 64) % 2 == 0) 0 else 255.toByte() }
        FamilyPrivacyBlur.apply(plane, 64, 48, PrivacyRegion(0, 0, 64, 48), 12)
        assertTrue(plane.all { (it.toInt() and 255) in 110..145 })
    }

    @Test fun faceRegionIsBlurredWithoutChangingTheBackground() {
        val plane = ByteArray(32 * 24) { (it % 251).toByte() }
        val original = plane.copyOf()
        FamilyPrivacyBlur.apply(plane, 32, 24, PrivacyRegion(8, 6, 24, 18), 5)
        var changed = 0
        for (y in 0 until 24) for (x in 0 until 32) {
            if (x !in 8 until 24 || y !in 6 until 18) assertEquals(original[y * 32 + x], plane[y * 32 + x])
            else if (original[y * 32 + x] != plane[y * 32 + x]) changed++
        }
        assertTrue(changed > 100)
    }

    @Test fun dottedFaceMaskLeavesCornersClearAndObscuresFaceDetail() {
        val width = 64
        val plane = ByteArray(width * width) { if ((it + it / width) % 2 == 0) 0 else 255.toByte() }
        val original = plane.copyOf()
        FamilyPrivacyBlur.apply(plane, width, width, PrivacyRegion(8, 8, 56, 56), 12, oval = true, dots = true)

        assertEquals(original[8 * width + 8], plane[8 * width + 8])
        assertEquals(original[55 * width + 55], plane[55 * width + 55])
        val center = (24 until 40).flatMap { y -> (24 until 40).map { x -> plane[y * width + x].toInt() and 255 } }
        assertTrue(center.all { it in 110..180 })
        assertTrue(center.max() - center.min() > 15)
    }

    @Test fun dotPatternMovesOnIdenticalFramesAndLoops() {
        val width = 64
        val face = PrivacyRegion(8, 8, 56, 56)
        fun render(timestampNs: Long, dots: Boolean = true): ByteArray {
            val plane = ByteArray(width * width) { 100 }
            FamilyPrivacyBlur.apply(plane, width, width, face, 12, oval = true, dots = dots, timestampNs = timestampNs)
            return plane
        }

        val start = render(0)
        val quarter = render(700_000_000)
        assertTrue(!start.contentEquals(quarter))
        assertArrayEquals(start, render(2_800_000_000))
        assertArrayEquals(render(0, dots = false), render(700_000_000, dots = false))
        assertEquals(start[8 * width + 8], quarter[8 * width + 8])
    }

    @Test fun dotsShrinkAndNarrowTowardTheSideOfTheFace() {
        val width = 90
        val plane = ByteArray(width * width) { 100 }
        FamilyPrivacyBlur.apply(plane, width, width, PrivacyRegion(0, 0, width, width), 12, oval = true, dots = true)
        fun luminance(x: Int, y: Int) = plane[y * width + x].toInt() and 255

        assertTrue(luminance(43, 42) > luminance(73, 42))
        assertTrue(luminance(72, 43) > luminance(73, 42))
    }

    @Test fun faceMaskTapersAtTheJaw() {
        val width = 100
        val plane = ByteArray(width * width) { if (it % 2 == 0) 0 else 255.toByte() }
        FamilyPrivacyBlur.apply(plane, width, width, PrivacyRegion(0, 0, width, width), 12, oval = true)

        assertTrue((plane[20 * width + 90].toInt() and 255) > 100)
        assertEquals(0, plane[79 * width + 90].toInt())
    }

    @Test fun smoothingFollowsMovementWithoutShrinkingCurrentCoverage() {
        val current = PrivacyRegion(100, 100, 300, 300)
        assertEquals(PrivacyRegion(96, 96, 300, 300), current.stabilizedFrom(PrivacyRegion(96, 96, 296, 296)))
        assertEquals(PrivacyRegion(90, 90, 300, 300), current.stabilizedFrom(PrivacyRegion(0, 0, 200, 200)))
    }

    @Test fun outOfFrameFacesAreClampedAndConstantChromaIsPreserved() {
        val plane = ByteArray(16 * 12) { 128.toByte() }
        val original = plane.copyOf()
        FamilyPrivacyBlur.apply(plane, 16, 12, PrivacyRegion(-20, -8, 100, 80), 12)
        assertArrayEquals(original, plane)
        FamilyPrivacyBlur.apply(plane, 16, 12, PrivacyRegion(30, 30, 40, 40), 12)
        assertArrayEquals(original, plane)
    }
}
