package com.starception.submission.feature.prayertimes.weather

import android.content.Context
import com.starception.submission.feature.prayertimes.data.PrayerTimeCalculatorEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.withTimeoutOrNull

/** Resolves the saved/GPS location and forecast used by background prayer notifications. */
suspend fun getPrayerWeatherInsightForNotification(
    context: Context,
    prayerName: String,
    prayerTimeText: String,
): PrayerWeatherInsight? {
    val prayerTime = parseNotificationPrayerTime(prayerTimeText) ?: return null
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        PrayerTimeCalculatorEntryPoint::class.java,
    )
    val savedLocation = entryPoint.prayerSettingsRepository()
        .getLocationPreferences()
        .location
    val coordinates = if (savedLocation != null) {
        savedLocation.latitude to savedLocation.longitude
    } else {
        withTimeoutOrNull(1_500L) {
            entryPoint.enhancedLocationService().getBestAvailableLocation().getOrNull()
        }?.let { it.latitude to it.longitude }
    } ?: return null

    val now = LocalDateTime.now()
    var prayerDate = LocalDate.now()
    val todayPrayer = LocalDateTime.of(prayerDate, prayerTime)
    // A reminder after midnight may refer to tomorrow's Fajr. A notification can
    // also arrive late, so only roll forward when the parsed time is far in the past.
    if (todayPrayer.isBefore(now.minusHours(6))) prayerDate = prayerDate.plusDays(1)

    return CurrentWeatherRepository.getPrayerInsight(
        latitude = coordinates.first,
        longitude = coordinates.second,
        prayerName = prayerName,
        prayerDate = prayerDate,
        prayerTime = prayerTime,
        thresholds = PrayerWeatherThresholdStore.load(context),
    )
}

private fun parseNotificationPrayerTime(value: String): LocalTime? {
    val normalized = value.trim().uppercase(Locale.US)
    val formats = listOf(
        DateTimeFormatter.ofPattern("h:mm a", Locale.US),
        DateTimeFormatter.ofPattern("h:mm:ss a", Locale.US),
        DateTimeFormatter.ISO_LOCAL_TIME,
    )
    return formats.firstNotNullOfOrNull { formatter ->
        runCatching { LocalTime.parse(normalized, formatter) }.getOrNull()
    }
}
