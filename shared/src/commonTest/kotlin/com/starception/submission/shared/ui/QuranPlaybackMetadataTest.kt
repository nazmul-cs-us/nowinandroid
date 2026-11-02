package com.starception.submission.shared.ui

import com.starception.submission.shared.media.QuranPlaybackBus
import kotlin.test.Test
import kotlin.test.assertEquals

class QuranPlaybackMetadataTest {
    private fun state(reciter: String?) = QuranPlaybackBus.PlaybackState(
        surahNumber = 78, title = "An-Naba", playing = true,
        positionSeconds = 7f, durationSeconds = 120f, reciterName = reciter,
    )

    @Test fun bannerIncludesThePlayingReciter() {
        assertEquals("An-Naba · Mishary Rashid Al-Alafasy",
            state("Mishary Rashid Al-Alafasy").bannerTitle)
    }

    @Test fun pauseAndProgressRetainReciterMetadata() {
        assertEquals("An-Naba · Example reciter",
            state("Example reciter").copy(playing = false, positionSeconds = 30f).bannerTitle)
    }

    @Test fun UnknownReciterDoesNotProduceAnEmptySeparator() {
        assertEquals("An-Naba", state(null).bannerTitle)
        assertEquals("An-Naba", state(" ").bannerTitle)
    }
}
