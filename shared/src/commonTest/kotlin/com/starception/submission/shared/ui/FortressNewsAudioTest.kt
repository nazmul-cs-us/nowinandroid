package com.starception.submission.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FortressNewsAudioTest {
    @Test fun fortressRecordingIsExtractedWithoutFollowingSection() {
        assertEquals("https://example.org/dua.mp3", sharedNewsAudioUrl(
            "**Arabic:** text\n\n**Audio:** https://example.org/dua.mp3\n\n**Source:** Fortress", "Fortress Dua"))
    }

    @Test fun audioSectionNameIsCaseInsensitive() {
        assertEquals("https://example.org/dua.mp3", sharedNewsAudioUrl(
            "**audio:**\nhttps://example.org/dua.mp3", "dua"))
    }

    @Test fun cardsWithoutValidDuaRecordingHaveNoPlayControl() {
        assertNull(sharedNewsAudioUrl("No recording", "dua"))
        assertNull(sharedNewsAudioUrl("**Audio:** javascript:alert(1)", "dua"))
        assertNull(sharedNewsAudioUrl("**Audio:** https://example.org/file.mp3", "hadith"))
        assertNull(sharedNewsAudioUrl("**Audio:** https://example.org/a.mp3 extra text", "dua"))
    }
}
