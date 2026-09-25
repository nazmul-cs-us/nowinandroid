/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.ml

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun risingFromSecondSujudCountsRakahButRukuRisingDoesNot() {
        val validator = SalahSequenceValidator()
        listOf(
            SalahPosture.QIYAM,
            SalahPosture.RUKU,
            SalahPosture.QIYAM_RISING,
            SalahPosture.GOING_TO_SUJUD,
            SalahPosture.SUJUD,
            SalahPosture.JALSA,
            SalahPosture.GOING_TO_SUJUD,
            SalahPosture.SUJUD,
        ).forEach { validator.stabilize(it) }

        assertEquals(0, validator.completedRakahs)
        val rising = validator.stabilize(SalahPosture.RISING_TO_QIYAM)

        assertTrue(rising.accepted)
        assertEquals(1, rising.rakahCount)
    }

    @Test
    fun twoRakahSequenceConfirmsPrayerAndCountsExactlyTwo() {
        val validator = SalahSequenceValidator()
        val firstRakah = listOf(
            SalahPosture.QIYAM,
            SalahPosture.RUKU,
            SalahPosture.QIYAM_RISING,
            SalahPosture.GOING_TO_SUJUD,
            SalahPosture.SUJUD,
            SalahPosture.JALSA,
            SalahPosture.GOING_TO_SUJUD,
            SalahPosture.SUJUD,
            SalahPosture.RISING_TO_QIYAM,
        )
        val secondRakah = listOf(
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

        (firstRakah + secondRakah).forEach { validator.stabilize(it) }

        assertTrue(validator.isPrayerConfirmed)
        assertEquals(2, validator.completedRakahs)
    }
}
