package com.starception.submission.voice

import org.junit.Assert.*
import org.junit.Test

class HadithTtsCacheIdentityTest {
    @Test
    fun javaHashCollisionCannotReuseADifferentNarration() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(ttsCacheIdentity("Aa", "model", 0, 1f), ttsCacheIdentity("BB", "model", 0, 1f))
    }

    @Test
    fun synthesisParametersAndTextArePartOfIdentity() {
        val key = ttsCacheIdentity("Narration", "model-v1", 0, 1f)
        assertNotEquals(key, ttsCacheIdentity("Narration", "model-v2", 0, 1f))
        assertNotEquals(key, ttsCacheIdentity("Narration", "model-v1", 1, 1f))
        assertNotEquals(key, ttsCacheIdentity("Narration", "model-v1", 0, 2f))
        assertTrue(isTtsCacheIdentity(key))
        assertFalse(isTtsCacheIdentity("2112"))
    }
}
