/*
 * Copyright 2022 The Android Open Source Project
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

package com.starception.submission.prayer.model

import kotlinx.datetime.LocalTime

private const val SECONDS_PER_DAY = 24 * 60 * 60

/**
 * Which prayer is current, which is next, and how long until it.
 *
 * Shared so that Android and iOS cannot disagree about what the schedule *means*
 * — the calculator already guarantees they agree about the times themselves.
 * Android's `DayPrayerTimes` delegates here rather than keeping a second copy;
 * the three bugs fixed in 9a77e487b are exactly what a second copy would have
 * reintroduced.
 */
object PrayerWindows {

    /**
     * Is [now] inside `[start, end)`?
     *
     * Inclusive of the moment a prayer begins, exclusive of the moment the next
     * one does, so exactly one prayer is current at any instant. Two details this
     * exists to get right:
     *
     * 1. **The start is inclusive.** With both bounds strict, the boundary instant
     *    belongs to no prayer and the UI briefly shows nothing.
     * 2. **The window may wrap past midnight.** Isha runs two hours from when it
     *    starts, so a 23:00 Isha ends at 01:00 — an end *before* its start. A
     *    plain `now < end` is then false all evening and Isha never reads as
     *    current at all.
     */
    fun isWithinWindow(now: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        if (end > start) {
            now >= start && now < end
        } else {
            now >= start || now < end
        }

    /**
     * Adds [hours] to [time], wrapping past midnight.
     *
     * kotlinx-datetime's `LocalTime` has no arithmetic, unlike `java.time`, so
     * this goes via seconds. The modulo is what makes a late Isha's window behave.
     */
    fun plusHours(time: LocalTime, hours: Int): LocalTime =
        LocalTime.fromSecondOfDay(
            (time.toSecondOfDay() + hours * 60 * 60).mod(SECONDS_PER_DAY),
        )

    /**
     * Whole minutes from [now] until [next], counting across midnight.
     *
     * Returns a full day when the two are equal, since "0" would read as "now"
     * for a prayer that is a day away.
     */
    fun minutesUntil(now: LocalTime, next: LocalTime): Int {
        val delta = (next.toSecondOfDay() - now.toSecondOfDay()).mod(SECONDS_PER_DAY)
        val seconds = if (delta == 0) SECONDS_PER_DAY else delta
        return seconds / 60
    }

    /** Formats a gap as `5h 37m`, `37m`, or `Now`. */
    fun formatCountdown(minutes: Int): String {
        val hours = minutes / 60
        val remaining = minutes % 60
        return when {
            hours > 0 -> "${hours}h ${remaining}m"
            remaining > 0 -> "${remaining}m"
            else -> "Now"
        }
    }

    /**
     * Marks which of [prayers] is current and which is next, given [now].
     *
     * [prayers] must be in chronological order. Every prayer runs until the next
     * one begins; the last runs for [lastPrayerHours] and may wrap past midnight.
     * When nothing remains today, the first prayer is next — tomorrow's.
     */
    fun annotate(
        now: LocalTime,
        prayers: List<PrayerInstant>,
        lastPrayerHours: Int = 2,
    ): List<PrayerInstant> {
        if (prayers.isEmpty()) return prayers

        val nextIndex = prayers.indexOfFirst { it.time > now }

        return prayers.mapIndexed { index, prayer ->
            val end = if (index < prayers.lastIndex) {
                prayers[index + 1].time
            } else {
                plusHours(prayer.time, lastPrayerHours)
            }
            prayer.copy(
                isCurrent = isWithinWindow(now, prayer.time, end),
                isNext = index == nextIndex || (nextIndex == -1 && index == 0),
            )
        }
    }
}

/** A prayer at a time, with its status relative to some instant. */
data class PrayerInstant(
    val name: String,
    val time: LocalTime,
    val isCurrent: Boolean = false,
    val isNext: Boolean = false,
)
