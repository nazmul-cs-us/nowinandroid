package com.starception.submission.ml

/**
 * Salah prayer postures that can be detected via phone sensors.
 * Each posture produces distinct accelerometer/gyroscope signatures
 * when the phone is in the user's pocket.
 */
enum class SalahPosture(val displayName: String, val arabicName: String) {
    QIYAM("Standing", "قيام"),
    RUKU("Bowing", "ركوع"),
    GOING_TO_SUJUD("Going Down", "هوي"),
    SUJUD("Prostration", "سجود"),
    JALSA("Sitting", "جلسة"),
    TASHAHHUD("Final Sitting", "تشهد"),
    QIYAM_RISING("Rising Up", "اعتدال");

    companion object {
        val classificationLabels = listOf(QIYAM, RUKU, GOING_TO_SUJUD, SUJUD, JALSA, TASHAHHUD, QIYAM_RISING)

        fun fromIndex(index: Int): SalahPosture {
            return classificationLabels.getOrElse(index) { QIYAM }
        }
    }
}
