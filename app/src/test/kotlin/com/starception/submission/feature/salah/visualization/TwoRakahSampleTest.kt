package com.starception.submission.feature.salah.visualization

import com.starception.submission.ml.SalahPosture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoRakahSampleTest {

    @Test
    fun sampleContainsACompleteTwoRakahSequence() {
        assertEquals(24, twoRakahSample.size)
        assertEquals(1, twoRakahSample.first().rakah)
        assertEquals(2, twoRakahSample.last().rakah)

        for (rakah in 1..2) {
            val rakahSteps = twoRakahSample.filter { it.rakah == rakah }
            assertEquals(1, rakahSteps.count { it.posture == SalahPosture.RUKU })
            assertEquals(2, rakahSteps.count { it.posture == SalahPosture.SUJUD })
            assertEquals(1, rakahSteps.count { it.posture == SalahPosture.JALSA })
        }
    }

    @Test
    fun everyFortressChapterIsUsedByAtLeastOnePhase() {
        val mappedChapters = twoRakahSample.mapNotNull { it.fortressChapterId }.toSet()

        assertEquals(twoRakahFortressChapterIds, mappedChapters)
        assertFalse(21 in mappedChapters)
    }

    @Test
    fun everyPhaseProvidesDuaContentOrExplicitGuidance() {
        assertTrue(
            twoRakahSample.all { step ->
                step.fortressChapterId != null || !step.guidance.isNullOrBlank()
            },
        )
    }

    @Test
    fun finalSittingUsesFortressChaptersInPrayerOrder() {
        val finalSteps = twoRakahSample.takeLast(5)

        assertEquals(listOf(22, 23, 24, null, 25), finalSteps.map { it.fortressChapterId })
    }
}
