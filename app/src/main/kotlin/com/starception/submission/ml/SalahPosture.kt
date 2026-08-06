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
    RISING_TO_QIYAM("Rise to Next Rak‘ah", "نهوض للقيام"),

    /**
     * Everything that is not prayer: walking, sitting, driving, stairs, phone on a desk.
     *
     * Without it the classifier cannot say "no posture applies", so ordinary movement is
     * forced onto whichever prayer posture it happens to resemble. Deliberately one broad
     * class — the app never needs to tell walking from driving, only either from prayer.
     *
     * Which activity a take contains is not recorded per sample. Keep one activity per
     * take so it stays recoverable from the session id if a specific negative later turns
     * out to be the one causing confusions.
     */
    NOT_PRAYING("Not Praying", "ليس صلاة");

    /** True for the eight postures that make up a prayer; false for [NOT_PRAYING]. */
    val isPrayerPosture: Boolean get() = this != NOT_PRAYING

    companion object {
        /**
         * The eight postures a prayer is made of. Drives the guided sequence, rak'ah
         * validation, and what counts as a complete ("full") prayer recording — none of
         * which should start demanding a NOT_PRAYING segment.
         */
        val prayerPostures = listOf(
            QIYAM, RUKU, GOING_TO_SUJUD, SUJUD, JALSA, TASHAHHUD,
            QIYAM_RISING, RISING_TO_QIYAM,
        )

        /**
         * Canonical label order — the model's output space. New labels are always
         * appended to preserve old model indices: [SalahDetectionEngine] requires a
         * model's labels to be a prefix of this list, so the shipped 7-class model keeps
         * loading unchanged after NOT_PRAYING is added at the end.
         */
        val classificationLabels = prayerPostures + NOT_PRAYING

        val recordingLabels = classificationLabels

        fun fromIndex(index: Int): SalahPosture {
            return classificationLabels.getOrElse(index) { QIYAM }
        }
    }
}
