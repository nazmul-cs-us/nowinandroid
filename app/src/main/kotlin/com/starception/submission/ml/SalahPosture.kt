package com.starception.submission.ml

/**
 * Salah prayer postures that can be detected via phone sensors.
 * Each posture produces distinct accelerometer/gyroscope signatures
 * when the phone is in the user's pocket.
 */
enum class SalahPosture(val displayName: String, val arabicName: String) {
    QIYAM("Standing", "قيام"),
    RUKU("Bowing", "ركوع"),
    SUJUD("Prostration", "سجود"),
    JALSA("Sitting", "جلسة"),
    TASHAHHUD("Final Sitting", "تشهد"),
    TRANSITION("Transition", "انتقال"),
    NOT_PRAYING("Not Praying", "");

    companion object {
        /** Postures used for ML classification (excludes NOT_PRAYING) */
        val classificationLabels = listOf(QIYAM, RUKU, SUJUD, JALSA, TASHAHHUD)

        fun fromIndex(index: Int): SalahPosture {
            return classificationLabels.getOrElse(index) { NOT_PRAYING }
        }
    }
}
