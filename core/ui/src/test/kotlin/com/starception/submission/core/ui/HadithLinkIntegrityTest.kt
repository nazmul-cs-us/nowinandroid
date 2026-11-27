package com.starception.submission.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HadithLinkIntegrityTest {
    @Test
    fun legacyAndPublisherNumbersAreNotLocalTargets() {
        assertNull(extractHadithInfo("hadith://sahih_bukhari/630"))
        assertNull(extractHadithInfo("https://sunnah.com/bukhari:630"))
        assertNull(extractHadithInfo("prefix-hadith-local://sahih_bukhari/615"))
        assertNull(extractHadithInfo("hadith-local://unknown/615"))
        assertNull(extractHadithInfo("hadith-local://sahih_bukhari/0"))
        assertEquals("sahih_bukhari.db" to 615, extractHadithInfo("hadith-local://sahih_bukhari/615"))
    }
}
