package com.starception.submission.media

import org.junit.Test
import kotlin.test.assertEquals

class MediaBannerTextTest {
    private val quran = MediaPlaybackState(
        title = "النبأ", subtitle = "An-Naba", reciterName = "Yasser Al-Dosari",
        isPlaying = true, source = MediaSource.Quran(surahIndex = 77),
    )

    @Test fun prayerReminderDoesNotReplaceTheReciterName() {
        assertEquals("An-Naba · النبأ", quran.bannerTitle)
        assertEquals("Yasser Al-Dosari · Fajr in 6m", quran.bannerSubtitle("Fajr in 6m", false))
    }

    @Test fun pauseAndPreparationKeepTheVoiceVisible() {
        assertEquals("Yasser Al-Dosari · Paused", quran.copy(isPlaying = false).bannerSubtitle(null, false))
        assertEquals("Yasser Al-Dosari · Preparing audio…", quran.bannerSubtitle(null, true))
    }

    @Test fun otherPlaybackKeepsItsOriginalTitleAndSubtitle() {
        val hadith = MediaPlaybackState(title = "Hadith 2354", subtitle = "Sahih al-Bukhari")
        assertEquals("Hadith 2354", hadith.bannerTitle)
        assertEquals("Sahih al-Bukhari", hadith.bannerSubtitle(null, false))
    }
}
