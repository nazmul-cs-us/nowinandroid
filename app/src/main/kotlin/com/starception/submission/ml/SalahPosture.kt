package com.starception.submission.ml

/**
 * Salah prayer postures that can be detected via phone sensors.
 * Each posture produces distinct accelerometer/gyroscope signatures
 * when the phone is in the user's pocket.
 */
enum class SalahPosture(val displayName: String, val arabicName: String) {
    QIYAM("Standing", "قيام"),
    RUKU("Bowing", "ركوع"),
    GOING_TO_SUJUD("Lowering to Sujud", "هوي"),
    SUJUD("Prostration", "سجود"),
    JALSA("Sitting", "جلسة"),
    TASHAHHUD("Final Sitting", "تشهد"),
    QIYAM_RISING("Ruku to Standing", "اعتدال"),
    RISING_TO_QIYAM("Rise to Next Rak‘ah", "نهوض للقيام");

    companion object {
        /** Canonical label order. New labels are always appended to preserve old model indices. */
        val classificationLabels = listOf(
            QIYAM, RUKU, GOING_TO_SUJUD, SUJUD, JALSA, TASHAHHUD,
            QIYAM_RISING, RISING_TO_QIYAM,
        )

        val recordingLabels = classificationLabels

        fun fromIndex(index: Int): SalahPosture {
            return classificationLabels.getOrElse(index) { QIYAM }
        }
    }
}
