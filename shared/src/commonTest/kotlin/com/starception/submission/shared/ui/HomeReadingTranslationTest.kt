package com.starception.submission.shared.ui

import com.starception.submission.shared.hadith.SharedHadith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeReadingTranslationTest {
    @Test fun downloadedBengaliIsUsedDirectlyWithoutLabelingItAsEnglish() {
        val hadith = SharedHadith(1, "حديث", "সকল কাজ নিয়তের উপর নির্ভরশীল")
        assertEquals(HomeReadingText(hadith.english, "Local translation"), localHomeHadithReading(hadith, "bn"))
        assertNull(localHomeHadithReading(hadith, "en"))
        assertNull(homeHadithEnglishForTranslation(hadith))
    }

    @Test fun arabicSourceAndEnglishTranslationKeepTheirOwnLabels() {
        val hadith = SharedHadith(1, "حديث", "Actions are judged by intentions.")
        assertEquals(HomeReadingText(hadith.arabic, "Arabic source"), localHomeHadithReading(hadith, "ar"))
        assertEquals(HomeReadingText(hadith.english, "Local English translation"), localHomeHadithReading(hadith, "en"))
        assertNull(localHomeHadithReading(hadith, "fr"))
        assertEquals(hadith.english, homeHadithEnglishForTranslation(hadith))
    }

    @Test fun missingTranslationDoesNotSubstituteAnotherLocalLanguage() {
        assertNull(localHomeHadithReading(SharedHadith(1, "حديث", ""), "bn"))
        assertNull(homeHadithEnglishForTranslation(null))
    }
}
