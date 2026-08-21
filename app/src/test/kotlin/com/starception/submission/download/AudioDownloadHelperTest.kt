package com.starception.submission.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudioDownloadHelperTest {

    @Test
    fun bukhariCandidatesIncludeOggAndMp3() {
        assertEquals(
            listOf(
                "audio/bukhari/bn/bukhari_5063.ogg",
                "audio/bukhari/bn/bukhari_5063.mp3",
            ),
            bukhariAudioCdnKeyCandidates(5063),
        )
    }

    @Test
    fun missingOggRequestResolvesToManifestMp3() {
        val requested = "audio/bukhari/bn/bukhari_5063.ogg"
        val available = setOf("audio/bukhari/bn/bukhari_5063.mp3")

        assertEquals(
            "audio/bukhari/bn/bukhari_5063.mp3",
            resolveAudioManifestKey(requested, available),
        )
    }

    @Test
    fun existingOggRequestRemainsOgg() {
        val requested = "audio/bukhari/bn/bukhari_0001.ogg"
        val available = setOf(
            "audio/bukhari/bn/bukhari_0001.ogg",
            "audio/bukhari/bn/bukhari_0001.mp3",
        )

        assertEquals(requested, resolveAudioManifestKey(requested, available))
    }

    @Test
    fun unrelatedMissingAudioDoesNotChangeExtension() {
        assertNull(
            resolveAudioManifestKey(
                requestedKey = "audio/fortress/bn/001.ogg",
                availableKeys = setOf("audio/fortress/bn/001.mp3"),
            ),
        )
    }
}
