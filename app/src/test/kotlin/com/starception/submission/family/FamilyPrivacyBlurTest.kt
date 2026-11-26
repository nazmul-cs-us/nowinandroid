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

    @Test fun outOfFrameFacesAreClampedAndConstantChromaIsPreserved() {
        val plane = ByteArray(16 * 12) { 128.toByte() }
        val original = plane.copyOf()
        FamilyPrivacyBlur.apply(plane, 16, 12, PrivacyRegion(-20, -8, 100, 80), 12)
        assertArrayEquals(original, plane)
        FamilyPrivacyBlur.apply(plane, 16, 12, PrivacyRegion(30, 30, 40, 40), 12)
        assertArrayEquals(original, plane)
    }
}
