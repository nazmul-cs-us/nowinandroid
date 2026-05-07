package com.starception.submission.feature.prayertimes.wobble

/**
 * State for the prayer alert banner shown in PullToSyncContainer.
 * Displays countdown alerts in two phases:
 * 1. BEFORE_PRAYER: "Fajr in 15m" - countdown to prayer time
 * 2. GO_TO_MOSQUE: "Go now, 18m left" - window to leave for mosque
 */
data class PrayerAlertState(
    val isActive: Boolean = false,
    val prayerName: String = "",
    val phase: AlertPhase = AlertPhase.NONE,
    val countdownMinutes: Int = 0,
    val displayText: String = ""
)

enum class AlertPhase {
    NONE,
    BEFORE_PRAYER,
    GO_TO_MOSQUE
}
