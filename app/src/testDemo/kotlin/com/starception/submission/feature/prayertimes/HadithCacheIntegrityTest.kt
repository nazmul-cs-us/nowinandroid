package com.starception.submission.feature.prayertimes

import com.starception.submission.core.model.deenly.packInsightFields
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HadithCacheIntegrityTest {
    @Test
    fun oldCachedQuoteAndTargetCannotBeReusedByRelabeling() {
        val legacy = packInsightFields(listOf(
            "old", "OPEN_CONTEXTUAL_RECOMMENDATION", "Heading", "", "action",
            "An unverified cached quote", "Sahih al-Bukhari 615", "sahih_bukhari-615",
            "sahih_al_bukhari", "Prayer", "1.0", "hadith", "Sahih al-Bukhari",
            "sahih_bukhari.db", "615", "model-generated-v1", "true",
        ))
        assertThrows(IllegalArgumentException::class.java) {
            PreparedInsightReadings.decodeAiCandidate(legacy)
        }
    }
}
