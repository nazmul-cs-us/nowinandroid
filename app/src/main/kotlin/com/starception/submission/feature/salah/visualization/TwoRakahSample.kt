package com.starception.submission.feature.salah.visualization

import com.starception.submission.core.duadatabase.Dua
import com.starception.submission.ml.SalahPosture

/** One deliberately paced pose in a built-in prayer visualization sample. */
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

/** All Fortress invocations used by the prayer samples, grouped in database order. */
data class TwoRakahDuaCatalog(
    val isLoading: Boolean = true,
    val chapters: Map<Int, List<Dua>> = emptyMap(),
    val errorMessage: String? = null,
)

/**
 * Fortress chapters that belong to an ordinary salah.
 *
 * Chapter 21 is intentionally absent: it is for a separate prostration triggered by an
 * ayah of sajdah, not one of the two regular prostrations in every rak'ah.
 */
val twoRakahFortressChapterIds: Set<Int> = setOf(16, 17, 18, 19, 20, 22, 23, 24, 25)

/**
 * Builds a canonical 2-, 3-, or 4-rak'ah pose sequence for the 3D figure.
 *
 * This is presentation data, not captured sensor data. Transitional lowering and rising
 * poses are included so the articulated figure moves naturally between held positions.
 */
private fun buildPrayerSample(rakahCount: Int): List<TwoRakahStep> = buildList {
    require(rakahCount in 2..4) { "Only 2-, 3-, and 4-rak'ah samples are supported" }

    for (rakah in 1..rakahCount) {
        if (rakah == 1) {
            add(
                TwoRakahStep(
                    1, SalahPosture.QIYAM, "Opening takbir", 1_500,
                    guidance = "Raise your hands and say Allahu Akbar to enter the prayer.",
                ),
            )
            add(
                TwoRakahStep(
                    1, SalahPosture.QIYAM, "Opening supplication", 5_000,
                    fortressChapterId = 16,
                ),
            )
        }

        add(
            TwoRakahStep(
                rakah, SalahPosture.QIYAM, "Qur'an recitation", 5_000,
                guidance = if (rakah <= 2) {
                    "Recite Al-Fatihah, followed by another passage of the Qur'an."
                } else {
                    "Recite Al-Fatihah."
                },
            ),
        )
        add(TwoRakahStep(rakah, SalahPosture.RUKU, "Bowing", 4_000, fortressChapterId = 17))
        add(
            TwoRakahStep(
                rakah, SalahPosture.QIYAM_RISING, "Standing after ruku", 3_500,
                fortressChapterId = 18,
            ),
        )
        add(
            TwoRakahStep(
                rakah, SalahPosture.GOING_TO_SUJUD, "Lowering to first sujud", 1_200,
                guidance = "Say Allahu Akbar while lowering into prostration.",
            ),
        )
        add(TwoRakahStep(rakah, SalahPosture.SUJUD, "First sujud", 4_000, fortressChapterId = 19))
        add(
            TwoRakahStep(
                rakah, SalahPosture.JALSA, "Sitting between sujud", 3_500,
                fortressChapterId = 20,
            ),
        )
        add(
            TwoRakahStep(
                rakah, SalahPosture.GOING_TO_SUJUD, "Lowering to second sujud", 1_200,
                guidance = "Say Allahu Akbar while lowering into the second prostration.",
            ),
        )
        add(TwoRakahStep(rakah, SalahPosture.SUJUD, "Second sujud", 4_000, fortressChapterId = 19))

        if (rakah == rakahCount) {
            add(TwoRakahStep(rakah, SalahPosture.TASHAHHUD, "Tashahhud", 5_000, fortressChapterId = 22))
            add(
                TwoRakahStep(
                    rakah, SalahPosture.TASHAHHUD, "Blessings upon the Prophet", 5_000,
                    fortressChapterId = 23,
                ),
            )
            add(
                TwoRakahStep(
                    rakah, SalahPosture.TASHAHHUD, "Supplication before salam", 5_000,
                    fortressChapterId = 24,
                ),
            )
            add(
                TwoRakahStep(
                    rakah, SalahPosture.TASHAHHUD, "End with salam", 2_500,
                    guidance = "Turn to the right and then the left to end the prayer with salam.",
                ),
            )
            add(
                TwoRakahStep(
                    rakah, SalahPosture.TASHAHHUD, "Remembrance after salam", 5_000,
                    fortressChapterId = 25,
                ),
            )
        } else {
            // Three- and four-rak'ah prayers include the first tashahhud after rak'ah two.
            if (rakah == 2 && rakahCount > 2) {
                add(
                    TwoRakahStep(
                        rakah, SalahPosture.TASHAHHUD, "First tashahhud", 5_000,
                        fortressChapterId = 22,
                    ),
                )
            }
            add(
                TwoRakahStep(
                    rakah, SalahPosture.RISING_TO_QIYAM, "Rise for rak'ah ${rakah + 1}", 1_500,
                    guidance = "Say Allahu Akbar while rising for rak'ah ${rakah + 1}.",
                ),
            )
        }
    }
}

val twoRakahSample: List<TwoRakahStep> = buildPrayerSample(2)
val threeRakahSample: List<TwoRakahStep> = buildPrayerSample(3)
val fourRakahSample: List<TwoRakahStep> = buildPrayerSample(4)

fun prayerSample(rakahCount: Int): List<TwoRakahStep> = when (rakahCount) {
    3 -> threeRakahSample
    4 -> fourRakahSample
    else -> twoRakahSample
}

fun VisualizationState.currentPrayerSample(): List<TwoRakahStep> = prayerSample(sampleRakahCount)

fun VisualizationState.currentTwoRakahStep(): TwoRakahStep =
    currentPrayerSample().let { it[twoRakahStepIndex.coerceIn(it.indices)] }
