package com.starception.submission.ui

import com.starception.submission.core.designsystem.component.deenlySplashReminder
import com.starception.submission.core.designsystem.component.nextDeenlySplashReminder
import com.starception.submission.core.designsystem.component.splashReminderCitation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeenlySplashReminderTest {
    @Test fun quranCitationUsesSurahNameAndAyahWithoutChangingOtherSources() {
        assertEquals("Surah Ta-Ha · Ayah 114", splashReminderCitation("Quran 20:114"))
        assertEquals("Surah Al-Ikhlas · Ayah 2", splashReminderCitation("Quran 112:2"))
        assertEquals("Sahih al-Bukhari 5027", splashReminderCitation("Sahih al-Bukhari 5027"))
        assertEquals("Quran 1:1", splashReminderCitation("Quran 1:1"))
    }
    @Test fun noModelAlwaysUsesQuranEvenWithHadithDownloaded() {
        for (day in -10L..10L) {
            assertTrue(deenlySplashReminder(day, false, true).reference.startsWith("Quran "))
            assertTrue(deenlySplashReminder(day, false, false).reference.startsWith("Quran "))
        }
    }

    @Test fun missingHadithContentKeepsTheReminderQuranOnly() {
        for (day in -10L..10L) assertTrue(deenlySplashReminder(day, true, false).reference.startsWith("Quran "))
    }

    @Test fun downloadedModelAndHadithAllowASourcedHadith() {
        assertEquals("Sahih al-Bukhari 5027", deenlySplashReminder(7, true, true).reference)
        assertEquals(deenlySplashReminder(3, true, true), deenlySplashReminder(3, true, true))
    }

    @Test fun eachStartupAdvancesWithoutRepeatingForEveryDownloadState() {
        for (model in listOf(false, true)) for (hadith in listOf(false, true)) {
            var previous: String? = null
            repeat(12) {
                val reminder = nextDeenlySplashReminder(previous, model, hadith)
                org.junit.Assert.assertNotEquals(previous, reminder.reference)
                if (!model || !hadith) assertTrue(reminder.reference.startsWith("Quran "))
                previous = reminder.reference
            }
        }
    }

    @Test fun removingAModelStillAvoidsRepeatingThePreviousQuranPassage() {
        val previous = deenlySplashReminder(0, true, true)
        org.junit.Assert.assertNotEquals(previous.reference,
            nextDeenlySplashReminder(previous.reference, false, false).reference)
    }
}
