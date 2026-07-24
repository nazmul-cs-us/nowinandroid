package com.starception.submission.ml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SalahSequenceValidatorTest {

    private var timestamp = 0L

    private fun SalahSequenceValidator.stabilize(
        posture: SalahPosture,
        confidence: Float = 0.9f,
    ): SalahSequenceValidator.ValidationResult {
        var result: SalahSequenceValidator.ValidationResult? = null
        repeat(3) {
            timestamp += 100L
            result = processDetection(posture, confidence, timestamp)
        }
        return requireNotNull(result)
    }

    @Test
    fun singlePredictionCannotChangeConfirmedPosture() {
        val validator = SalahSequenceValidator()
        validator.stabilize(SalahPosture.QIYAM)

        timestamp += 300L
        val spike = validator.processDetection(SalahPosture.RUKU, 0.99f, timestamp)

        assertFalse(spike.accepted)
        assertEquals(SalahPosture.QIYAM, spike.confirmedPosture)
        assertEquals(SalahSequenceValidator.PrayerState.DETECTING, spike.prayerState)
    }

    @Test
    fun standingAndBowingAloneDoNotConfirmPrayer() {
        val validator = SalahSequenceValidator()
        validator.stabilize(SalahPosture.QIYAM)
        val ruku = validator.stabilize(SalahPosture.RUKU)

        assertTrue(ruku.accepted)
        assertEquals(SalahSequenceValidator.PrayerState.DETECTING, ruku.prayerState)
        assertFalse(validator.isPrayerConfirmed)
    }

    @Test
    fun completeGuidedRakahConfirmsPrayerAndCountsOnce() {
        val validator = SalahSequenceValidator()
        val sequence = listOf(
            SalahPosture.QIYAM,
            SalahPosture.RUKU,
            SalahPosture.QIYAM_RISING,
            SalahPosture.GOING_TO_SUJUD,
            SalahPosture.SUJUD,
            SalahPosture.JALSA,
            SalahPosture.GOING_TO_SUJUD,
            SalahPosture.SUJUD,
            SalahPosture.TASHAHHUD,
        )

        var result: SalahSequenceValidator.ValidationResult? = null
        for (posture in sequence) result = validator.stabilize(posture)

        assertTrue(requireNotNull(result).accepted)
        assertEquals(SalahSequenceValidator.PrayerState.CONFIRMED, result?.prayerState)
        assertEquals(1, result?.rakahCount)
        assertEquals(1, validator.completedRakahs)
    }
}
