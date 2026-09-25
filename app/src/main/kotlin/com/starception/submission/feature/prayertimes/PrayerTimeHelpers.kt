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

package com.starception.submission.feature.prayertimes

import com.starception.submission.prayer.model.DayPrayerTimes
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object PrayerTimeHelpers {

    // Get current date formatted for display
    fun getCurrentDate(): String {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
        return today.format(formatter)
    }

    // Get prayer time display
    fun getPrayerTimeDisplay(prayerName: String, prayerTimes: DayPrayerTimes?): String {
        val times = prayerTimes ?: return "00:00 AM"

        val time = when (prayerName) {
            "Fajr" -> times.fajr
            "Sunrise" -> times.sunrise
            "Dhuhr" -> times.dhuhr
            "Asr" -> times.asr
            "Maghrib" -> times.maghrib
            "Isha" -> times.isha
            else -> times.fajr
        }

        return time.format(DateTimeFormatter.ofPattern("hh:mm a"))
    }

    // Get prayer status (Current, Next, Upcoming)
    fun getPrayerStatus(prayerName: String, currentTime: LocalTime, prayerTimes: DayPrayerTimes?): String {
        val times = prayerTimes ?: return "Upcoming"

        val prayerTime = when (prayerName) {
            "Fajr" -> times.fajr
            "Sunrise" -> times.sunrise
            "Dhuhr" -> times.dhuhr
            "Asr" -> times.asr
            "Maghrib" -> times.maghrib
            "Isha" -> times.isha
            else -> times.fajr
        }

        val allPrayerTimes = listOf(
            "Fajr" to times.fajr,
            "Sunrise" to times.sunrise,
            "Dhuhr" to times.dhuhr,
            "Asr" to times.asr,
            "Maghrib" to times.maghrib,
            "Isha" to times.isha,
        )

        // Find current prayer (if we're in a prayer time window)
        val currentPrayer = allPrayerTimes.find { (name, time) ->
            val nextPrayerTime = allPrayerTimes.find { it.second.isAfter(time) }?.second
                ?: LocalTime.of(23, 59) // Default to end of day if no next prayer
            currentTime.isAfter(time) && currentTime.isBefore(nextPrayerTime)
        }

        // Find next prayer
        val nextPrayer = allPrayerTimes.find { it.second.isAfter(currentTime) }

        return when {
            currentPrayer?.first == prayerName -> "Current"
            nextPrayer?.first == prayerName -> "Next"
            else -> "Upcoming"
        }
    }

    // Get next prayer
    fun getNextPrayer(currentTime: LocalTime, prayerTimes: DayPrayerTimes?): Pair<String, LocalTime>? {
        val times = prayerTimes ?: return null

        val allPrayerTimes = listOf(
            "Fajr" to times.fajr,
            "Sunrise" to times.sunrise,
            "Dhuhr" to times.dhuhr,
            "Asr" to times.asr,
            "Maghrib" to times.maghrib,
            "Isha" to times.isha,
        )

        return allPrayerTimes.find { it.second.isAfter(currentTime) }
    }

    // Get current prayer (if we're in a prayer time window)
    fun getCurrentPrayer(currentTime: LocalTime, prayerTimes: DayPrayerTimes?): Pair<String, LocalTime>? {
        val times = prayerTimes ?: return null

        val allPrayerTimes = listOf(
            "Fajr" to times.fajr,
            "Sunrise" to times.sunrise,
            "Dhuhr" to times.dhuhr,
            "Asr" to times.asr,
            "Maghrib" to times.maghrib,
            "Isha" to times.isha,
        )

        // Find current prayer (if we're in a prayer time window - within 30 minutes after prayer time)
        return allPrayerTimes.find { (name, time) ->
            currentTime.isAfter(time) &&
                ChronoUnit.MINUTES.between(time, currentTime) <= 30
        }
    }

    // Get time until next prayer
    fun getTimeUntilNextPrayer(currentTime: LocalTime, prayerTimes: DayPrayerTimes?): String {
        val nextPrayer = getNextPrayer(currentTime, prayerTimes)
        if (nextPrayer == null) return "--:--"

        val duration = ChronoUnit.MINUTES.between(currentTime, nextPrayer.second)
        val hours = duration / 60
        val minutes = duration % 60

        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }

    // Get next 4 prayers dynamically (handles day transitions)
    fun getNext4Prayers(currentTime: LocalTime, prayerTimes: DayPrayerTimes?): List<Pair<String, LocalTime>> {
        val times = prayerTimes ?: return emptyList()

        val allPrayerTimes = listOf(
            "Fajr" to times.fajr,
            "Sunrise" to times.sunrise,
            "Dhuhr" to times.dhuhr,
            "Asr" to times.asr,
            "Maghrib" to times.maghrib,
            "Isha" to times.isha,
        )

        // Find upcoming prayers for today
        val upcomingToday = allPrayerTimes.filter { it.second.isAfter(currentTime) }

        // If we have 4 or more prayers left today, return the next 4
        if (upcomingToday.size >= 4) {
            return upcomingToday.take(4)
        }

        // If less than 4 prayers left today, include tomorrow's prayers
        val result = upcomingToday.toMutableList()

        // Add tomorrow's prayers starting from Fajr until we have 4 total
        val tomorrowPrayers = listOf(
            "Fajr" to times.fajr.plusHours(24), // Tomorrow's Fajr
            "Sunrise" to times.sunrise.plusHours(24), // Tomorrow's Sunrise
            "Dhuhr" to times.dhuhr.plusHours(24), // Tomorrow's Dhuhr
            "Asr" to times.asr.plusHours(24), // Tomorrow's Asr
            "Maghrib" to times.maghrib.plusHours(24), // Tomorrow's Maghrib
            "Isha" to times.isha.plusHours(24), // Tomorrow's Isha
        )

        for (prayer in tomorrowPrayers) {
            if (result.size < 4) {
                result.add(prayer)
            } else {
                break
            }
        }

        return result.take(4)
    }
}
