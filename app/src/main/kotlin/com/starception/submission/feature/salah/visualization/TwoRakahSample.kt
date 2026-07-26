package com.starception.submission.feature.salah.visualization

import com.starception.submission.ml.SalahPosture

/** One deliberately paced pose in the built-in two-rak'ah visualization sample. */
data class TwoRakahStep(
    val rakah: Int,
    val posture: SalahPosture,
    val label: String,
    val durationMillis: Long,
)

/**
 * A canonical two-rak'ah pose sequence for demonstrating the 3D figure.
 *
 * This is presentation data, not captured sensor data. Transitional lowering and rising
 * poses are included so the articulated figure moves naturally between held positions.
 */
val twoRakahSample: List<TwoRakahStep> = listOf(
    TwoRakahStep(1, SalahPosture.QIYAM, "Standing", 2_200),
    TwoRakahStep(1, SalahPosture.RUKU, "Bowing", 1_600),
    TwoRakahStep(1, SalahPosture.QIYAM_RISING, "Standing after ruku", 1_200),
    TwoRakahStep(1, SalahPosture.GOING_TO_SUJUD, "Lowering to sujud", 700),
    TwoRakahStep(1, SalahPosture.SUJUD, "First sujud", 1_700),
    TwoRakahStep(1, SalahPosture.JALSA, "Sitting between sujud", 1_300),
    TwoRakahStep(1, SalahPosture.GOING_TO_SUJUD, "Lowering to sujud", 700),
    TwoRakahStep(1, SalahPosture.SUJUD, "Second sujud", 1_700),
    TwoRakahStep(2, SalahPosture.QIYAM, "Standing", 2_200),
    TwoRakahStep(2, SalahPosture.RUKU, "Bowing", 1_600),
    TwoRakahStep(2, SalahPosture.QIYAM_RISING, "Standing after ruku", 1_200),
    TwoRakahStep(2, SalahPosture.GOING_TO_SUJUD, "Lowering to sujud", 700),
    TwoRakahStep(2, SalahPosture.SUJUD, "First sujud", 1_700),
    TwoRakahStep(2, SalahPosture.JALSA, "Sitting between sujud", 1_300),
    TwoRakahStep(2, SalahPosture.GOING_TO_SUJUD, "Lowering to sujud", 700),
    TwoRakahStep(2, SalahPosture.SUJUD, "Second sujud", 1_700),
    TwoRakahStep(2, SalahPosture.TASHAHHUD, "Final sitting", 2_600),
)

fun VisualizationState.currentTwoRakahStep(): TwoRakahStep =
    twoRakahSample[twoRakahStepIndex.coerceIn(twoRakahSample.indices)]
