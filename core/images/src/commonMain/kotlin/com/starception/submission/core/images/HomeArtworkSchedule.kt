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

package com.starception.submission.core.images

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class ArtworkFrequency(val label: String) {
    HOURLY("Every hour"),
    DAILY("Every day"),
    WEEKLY("Every week"),
    MONTHLY("Every month"),
    FIXED("Keep current"),
}

/** Persist the anchor as well as the cadence so relaunching never resets the artwork. */
data class HomeArtworkSchedule(
    val frequency: ArtworkFrequency = ArtworkFrequency.DAILY,
    val anchor: Instant = Clock.System.now(),
    val rotation: Int = 0,
) {
    fun rotationAt(now: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): Int {
        val start = anchor.toLocalDateTime(zone).date
        val end = now.toLocalDateTime(zone).date
        val periods = when (frequency) {
            ArtworkFrequency.HOURLY -> (now.epochSeconds - anchor.epochSeconds) / 3600
            ArtworkFrequency.DAILY -> (end.toEpochDays() - start.toEpochDays()).toLong()
            ArtworkFrequency.WEEKLY -> (end.toEpochDays() - start.toEpochDays()).toLong() / 7
            ArtworkFrequency.MONTHLY -> ((end.year - start.year) * 12 + end.monthNumber - start.monthNumber).toLong()
            ArtworkFrequency.FIXED -> 0L
        }
        return ((rotation.toLong() + periods.coerceAtLeast(0)) % Int.MAX_VALUE).toInt()
    }

    fun withFrequency(frequency: ArtworkFrequency, now: Instant, zone: TimeZone = TimeZone.currentSystemDefault()) =
        HomeArtworkSchedule(frequency, now, rotationAt(now, zone))

    fun encode(): String = "${frequency.name}|${anchor.epochSeconds}|$rotation"

    companion object {
        fun decode(value: String?, now: Instant = Clock.System.now()): HomeArtworkSchedule = runCatching {
            val parts = requireNotNull(value).split('|')
            require(parts.size == 3)
            val rotation = parts[2].toInt()
            require(rotation >= 0)
            HomeArtworkSchedule(ArtworkFrequency.valueOf(parts[0]), Instant.fromEpochSeconds(parts[1].toLong()), rotation)
        }.getOrElse { HomeArtworkSchedule(anchor = now) }
    }
}

/** One observable settings instance per app, backed by native preferences on each platform. */
class HomeArtworkSettings(saved: String?, private val save: (String) -> Unit) {
    var schedule by mutableStateOf(HomeArtworkSchedule.decode(saved))
        private set

    init {
        if (saved != schedule.encode()) save(schedule.encode())
    }

    fun setFrequency(frequency: ArtworkFrequency) {
        if (frequency == schedule.frequency) return
        schedule = schedule.withFrequency(frequency, Clock.System.now())
        save(schedule.encode())
    }
}

@Composable
expect fun rememberHomeArtworkSettings(): HomeArtworkSettings

/** Calendar cadences use the device's local date, including DST and time-zone changes. */
@Composable
fun rememberHomeArtworkRotation(): Int {
    val schedule = rememberHomeArtworkSettings().schedule
    val now by produceState(Clock.System.now(), schedule) {
        while (true) {
            val current = Clock.System.now()
            value = current
            if (schedule.frequency == ArtworkFrequency.FIXED) break
            delay(60_000L - current.toEpochMilliseconds() % 60_000L)
        }
    }
    return schedule.rotationAt(now)
}
