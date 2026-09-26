/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.shared.notifications

import com.starception.submission.prayer.model.CountryPrayerDefaults
import com.starception.submission.prayer.model.PrayerNotificationPreferences
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.shared.PrayerSchedule
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults
import kotlin.time.Clock

private const val PAYLOAD_KEY = "ios_prayer_schedule_payload"
private const val SCHEDULE_DAYS = 7

/** Publishes one prayer schedule used by the native notification and ActivityKit layers. */
internal object IosPrayerSchedulePublisher {
    private val json = Json { encodeDefaults = true }

    fun publish(
        locationName: String,
        startDate: LocalDate,
        latitude: Double,
        longitude: Double,
        timeZoneOffset: Double,
        countryCode: String,
        defaults: CountryPrayerDefaults?,
        settings: PrayerSettings,
        preferences: PrayerNotificationPreferences,
    ) {
        val days = (0 until SCHEDULE_DAYS).map { dayOffset ->
            val date = startDate.plus(DatePeriod(days = dayOffset))
            val schedule = PrayerSchedule.forDate(
                year = date.year,
                month = date.monthNumber,
                day = date.dayOfMonth,
                latitude = latitude,
                longitude = longitude,
                timeZoneOffset = timeZoneOffset,
                defaults = defaults,
                settings = settings,
                countryCode = countryCode,
                isFriday = date.dayOfWeek.name == "FRIDAY",
                nowHour = 0,
                nowMinute = 0,
            )
            IosPrayerDay(
                date = date.toString(),
                prayers = schedule.slots
                    .filter { it.name != "Sunrise" }
                    .map { slot ->
                        IosPrayerEntry(
                            name = slot.name,
                            hour = slot.hour,
                            minute = slot.minute,
                            enabled = preferences.isNotificationEnabledForPrayer(slot.name),
                            priorMinutes = preferences.getPriorMinutesForPrayer(slot.name),
                            activeMinutes = preferences.getGoToMosqueDurationForPrayer(slot.name),
                            adhanEnabled = preferences.isAdhanEnabledForPrayer(slot.name),
                        )
                    },
            )
        }

        val payload = IosPrayerPayload(
            generatedAtEpochMilliseconds = Clock.System.now().toEpochMilliseconds(),
            locationName = locationName,
            timeZoneOffset = timeZoneOffset,
            notificationsEnabled = preferences.notificationsEnabled,
            soundEnabled = preferences.notificationSound != "silent",
            days = days,
        )
        NSUserDefaults.standardUserDefaults.setObject(
            json.encodeToString(payload),
            forKey = PAYLOAD_KEY,
        )
    }
}

@Serializable
private data class IosPrayerPayload(
    val version: Int = 1,
    val generatedAtEpochMilliseconds: Long,
    val locationName: String,
    val timeZoneOffset: Double,
    val notificationsEnabled: Boolean,
    val soundEnabled: Boolean,
    val days: List<IosPrayerDay>,
)

@Serializable
private data class IosPrayerDay(
    val date: String,
    val prayers: List<IosPrayerEntry>,
)

@Serializable
private data class IosPrayerEntry(
    val name: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val priorMinutes: Int,
    val activeMinutes: Int,
    // Per-prayer adhan toggle: the prayer-time notification plays the bundled
    // adhan audio instead of the default sound.
    val adhanEnabled: Boolean = false,
)
