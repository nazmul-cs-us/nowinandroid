package com.starception.submission.core.model.data

import com.starception.submission.core.model.deenly.PrayerHadithCandidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotEquals

class HadithSourceIdentityTest {
    @Test
    fun internalRecord615IsNeverPresentedAsPublishedBukhari615() {
        // AhmedBaset/hadith-json v1.2.0 row 615 is the narration published at
        // https://sunnah.com/bukhari:630, NOT https://sunnah.com/bukhari:615.
        val narration = "Narrated Malik bin Huwairith: Two men came to the Prophet"
        val candidate = PrayerHadithCandidate("sahih_bukhari.db", "Sahih al-Bukhari", 615, narration)
        assertEquals("Sahih al-Bukhari · Local record 615", candidate.reference)
        assertNotEquals("Sahih al-Bukhari 615", candidate.reference)
        assertEquals(narration, candidate.text)
        assertEquals("sahih_bukhari.db#615", candidate.sourceId)
    }

    @Test
    fun allCollectionRecordIdsAreExplicitlyLocal() {
        HadithCollections.all.forEach { collection ->
            assertEquals("${collection.name} · Local record 630",
                HadithSourceIdentity.reference(collection.name, 630))
        }
    }

    @Test
    fun numberAloneNeverAuthorizesARecording() {
        listOf(1, 615, 630, 5063, 7277).forEach {
            assertNull(HadithSourceIdentity.verifiedBukhariRecording(it))
        }
    }
}
