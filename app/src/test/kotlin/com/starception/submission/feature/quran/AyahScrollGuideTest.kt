package com.starception.submission.feature.quran

import org.junit.Assert.*
import org.junit.Test

class AyahScrollGuideTest {
    @Test fun longAyahHintAppearsOnManualScrollingAndDoesNotRepeatAfterDismissal() {
        val guide = AyahScrollGuide()
        assertFalse(guide.hintVisible)
        guide.manualScroll(narrating = false, longAyah = true)
        assertTrue(guide.hintVisible)
        assertTrue(guide.longAyahHint)
        guide.dismissHint()
        guide.manualScroll(narrating = false, longAyah = true)
        assertFalse(guide.hintVisible)
    }
    @Test fun manualReadingPausesOnlyFollowingAndTheFollowActionRestoresIt() {
        val guide = AyahScrollGuide()
        guide.manualScroll(narrating = true, longAyah = true)
        assertFalse(guide.following)
        guide.follow()
        assertTrue(guide.following)
    }
}
