package com.starception.submission.feature.salah.visualization

import com.starception.submission.core.duadatabase.Dua
import com.starception.submission.ml.SalahPosture

/** One deliberately paced pose in the built-in two-rak'ah visualization sample. */
data class TwoRakahStep(
    val rakah: Int,
    val posture: SalahPosture,
    val label: String,
    val durationMillis: Long,
    /** Fortress of the Muslim chapter whose complete alternatives belong to this phase. */
    val fortressChapterId: Int? = null,
    /** Guidance for required phases that are not represented by a Fortress chapter. */
    val guidance: String? = null,
)

/** All Fortress invocations used by the two-rak'ah lesson, grouped in database order. */
data class TwoRakahDuaCatalog(
    val isLoading: Boolean = true,
    val chapters: Map<Int, List<Dua>> = emptyMap(),
    val errorMessage: String? = null,
)

/**
 * Fortress chapters that belong to an ordinary two-rak'ah salah.
 *
 * Chapter 21 is intentionally absent: it is for a separate prostration triggered by an
 * ayah of sajdah, not one of the two regular prostrations in every rak'ah.
 */
val twoRakahFortressChapterIds: Set<Int> = setOf(16, 17, 18, 19, 20, 22, 23, 24, 25)

/**
 * A canonical two-rak'ah pose sequence for demonstrating the 3D figure.
 *
 * This is presentation data, not captured sensor data. Transitional lowering and rising
 * poses are included so the articulated figure moves naturally between held positions.
 */
val twoRakahSample: List<TwoRakahStep> = listOf(
    TwoRakahStep(
        1, SalahPosture.QIYAM, "Opening takbir", 1_500,
        guidance = "Raise your hands and say Allahu Akbar to enter the prayer.",
    ),
    TwoRakahStep(
        1, SalahPosture.QIYAM, "Opening supplication", 5_000,
        fortressChapterId = 16,
    ),
    TwoRakahStep(
        1, SalahPosture.QIYAM, "Qur'an recitation", 5_000,
        guidance = "Recite Al-Fatihah, followed by another passage of the Qur'an.",
    ),
    TwoRakahStep(1, SalahPosture.RUKU, "Bowing", 4_000, fortressChapterId = 17),
    TwoRakahStep(
        1, SalahPosture.QIYAM_RISING, "Standing after ruku", 3_500,
        fortressChapterId = 18,
    ),
    TwoRakahStep(
        1, SalahPosture.GOING_TO_SUJUD, "Lowering to first sujud", 1_200,
        guidance = "Say Allahu Akbar while lowering into prostration.",
    ),
    TwoRakahStep(1, SalahPosture.SUJUD, "First sujud", 4_000, fortressChapterId = 19),
    TwoRakahStep(
        1, SalahPosture.JALSA, "Sitting between sujud", 3_500,
        fortressChapterId = 20,
    ),
    TwoRakahStep(
        1, SalahPosture.GOING_TO_SUJUD, "Lowering to second sujud", 1_200,
        guidance = "Say Allahu Akbar while lowering into the second prostration.",
    ),
    TwoRakahStep(1, SalahPosture.SUJUD, "Second sujud", 4_000, fortressChapterId = 19),
    TwoRakahStep(
        1, SalahPosture.RISING_TO_QIYAM, "Rise for second rak'ah", 1_500,
        guidance = "Say Allahu Akbar while rising for the second rak'ah.",
    ),
    TwoRakahStep(
        2, SalahPosture.QIYAM, "Qur'an recitation", 5_000,
        guidance = "Recite Al-Fatihah, followed by another passage of the Qur'an.",
    ),
    TwoRakahStep(2, SalahPosture.RUKU, "Bowing", 4_000, fortressChapterId = 17),
    TwoRakahStep(
        2, SalahPosture.QIYAM_RISING, "Standing after ruku", 3_500,
        fortressChapterId = 18,
    ),
    TwoRakahStep(
        2, SalahPosture.GOING_TO_SUJUD, "Lowering to first sujud", 1_200,
        guidance = "Say Allahu Akbar while lowering into prostration.",
    ),
    TwoRakahStep(2, SalahPosture.SUJUD, "First sujud", 4_000, fortressChapterId = 19),
    TwoRakahStep(
        2, SalahPosture.JALSA, "Sitting between sujud", 3_500,
        fortressChapterId = 20,
    ),
    TwoRakahStep(
        2, SalahPosture.GOING_TO_SUJUD, "Lowering to second sujud", 1_200,
        guidance = "Say Allahu Akbar while lowering into the second prostration.",
    ),
    TwoRakahStep(2, SalahPosture.SUJUD, "Second sujud", 4_000, fortressChapterId = 19),
    TwoRakahStep(
        2, SalahPosture.TASHAHHUD, "Tashahhud", 5_000,
        fortressChapterId = 22,
    ),
    TwoRakahStep(
        2, SalahPosture.TASHAHHUD, "Blessings upon the Prophet", 5_000,
        fortressChapterId = 23,
    ),
    TwoRakahStep(
        2, SalahPosture.TASHAHHUD, "Supplication before salam", 5_000,
        fortressChapterId = 24,
    ),
    TwoRakahStep(
        2, SalahPosture.TASHAHHUD, "End with salam", 2_500,
        guidance = "Turn to the right and then the left to end the prayer with salam.",
    ),
    TwoRakahStep(
        2, SalahPosture.TASHAHHUD, "Remembrance after salam", 5_000,
        fortressChapterId = 25,
    ),
)

fun VisualizationState.currentTwoRakahStep(): TwoRakahStep =
    twoRakahSample[twoRakahStepIndex.coerceIn(twoRakahSample.indices)]
