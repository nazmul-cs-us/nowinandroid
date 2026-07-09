package com.starception.submission.feature.prayertimes.wobble

import com.starception.submission.feature.prayertimes.getPrayerDisplayName
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.PrayerNotificationPreferences
import com.starception.submission.prayer.model.PrayerTimeOffsets
import java.time.Duration
import java.time.LocalTime

/**
 * Computes the prayer-alert banner state (BEFORE_PRAYER countdown / GO_TO_MOSQUE window) from
 * the current time and the user's prayer times, per-prayer offsets, and notification prefs.
 *
 * Shared so both the Home screen (PrayerTimesScreen) and MainActivityViewModel's app-wide
 * ticker use the exact same logic — the VM ticker keeps the countdown live on every screen
 * (e.g. the Surah detail page) instead of freezing when Home leaves composition.
 */
fun calculatePrayerAlertState(
    currentTime: LocalTime,
    prayerTimes: DayPrayerTimes?,
    notificationPrefs: PrayerNotificationPreferences,
    timeOffsets: PrayerTimeOffsets,
): PrayerAlertState {
    if (prayerTimes == null) return PrayerAlertState()

    // Apply the user's per-prayer offset so the banner aligns with the time
    // shown on the prayer card and the Smart Prediction tile. Without this,
    // a +3m offset on Dhuhr would make "Xm left" off by 3 vs. what the user sees.
    val actualPrayers = prayerTimes.getActualPrayers().map { p ->
        val adjusted = p.time.plusMinutes(timeOffsets.getOffset(p.name).toLong())
        p.copy(time = adjusted)
    }
    val currentPrayer = actualPrayers.firstOrNull {
        currentTime.isAfter(it.time) && it.name != "Sunrise" &&
            (actualPrayers.getOrNull(actualPrayers.indexOf(it) + 1)?.let { next ->
                currentTime.isBefore(next.time)
            } ?: currentTime.isBefore(it.time.plusHours(2)))
    }

    if (currentPrayer != null) {
        val goToMosqueDuration = notificationPrefs.getGoToMosqueDurationForPrayer(currentPrayer.name).toLong()
        val minutesSince = Duration.between(currentPrayer.time, currentTime).toMinutes()
        val minutesLeft = goToMosqueDuration - minutesSince
        if (minutesLeft > 0) {
            return PrayerAlertState(
                isActive = true,
                prayerName = currentPrayer.name,
                phase = AlertPhase.GO_TO_MOSQUE,
                countdownMinutes = minutesLeft.toInt(),
                totalMinutes = goToMosqueDuration.toInt(),
                displayText = "${getPrayerDisplayName(currentPrayer.name)} · Go now to mosque, ${minutesLeft}m left",
            )
        }
    }

    val nextPrayer = actualPrayers.firstOrNull { it.time.isAfter(currentTime) && it.name != "Sunrise" }
    if (nextPrayer != null) {
        val priorMinutes = notificationPrefs.getPriorMinutesForPrayer(nextPrayer.name).toLong()
        val minutesUntil = Duration.between(currentTime, nextPrayer.time).toMinutes()
        if (minutesUntil in 1..priorMinutes) {
            return PrayerAlertState(
                isActive = true,
                prayerName = nextPrayer.name,
                phase = AlertPhase.BEFORE_PRAYER,
                countdownMinutes = minutesUntil.toInt(),
                totalMinutes = priorMinutes.toInt(),
                displayText = "${getPrayerDisplayName(nextPrayer.name)} in ${minutesUntil}m",
            )
        }
    }

    return PrayerAlertState()
}
