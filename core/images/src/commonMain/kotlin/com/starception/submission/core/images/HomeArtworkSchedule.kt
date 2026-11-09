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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
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

    fun nextUpdateAt(now: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): Instant? {
        val start = anchor.toLocalDateTime(zone).date
        val today = now.toLocalDateTime(zone).date
        return when (frequency) {
            ArtworkFrequency.HOURLY -> {
                val hours = ((now.epochSeconds - anchor.epochSeconds) / 3600).coerceAtLeast(0)
                Instant.fromEpochSeconds(anchor.epochSeconds + (hours + 1) * 3600)
            }
            ArtworkFrequency.DAILY -> today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
            ArtworkFrequency.WEEKLY -> {
                val weeks = ((today.toEpochDays() - start.toEpochDays()) / 7).coerceAtLeast(0)
                start.plus((weeks + 1) * 7, DateTimeUnit.DAY).atStartOfDayIn(zone)
            }
            ArtworkFrequency.MONTHLY -> LocalDate(today.year, today.monthNumber, 1)
                .plus(1, DateTimeUnit.MONTH).atStartOfDayIn(zone)
            ArtworkFrequency.FIXED -> null
        }
    }

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

    /** Advance immediately, including in Keep current mode, without moving the next scheduled change. */
    fun refreshNow() {
        schedule = schedule.copy(rotation = ((schedule.rotation.toLong() + 1) % Int.MAX_VALUE).toInt())
        save(schedule.encode())
    }
}

@Composable
expect fun rememberHomeArtworkSettings(): HomeArtworkSettings

data class HomeArtworkState(val rotation: Int, val nextUpdateAt: Instant?)

/** A fresh collection starts with wall-clock time, so resuming after missed periods catches up immediately. */
internal fun homeArtworkUpdates(
    schedule: HomeArtworkSchedule,
    clock: Clock = Clock.System,
    zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
): Flow<HomeArtworkState> = flow {
    while (true) {
        val now = clock.now()
        val currentZone = zone()
        val next = schedule.nextUpdateAt(now, currentZone)
        emit(HomeArtworkState(schedule.rotationAt(now, currentZone), next))
        if (next == null) break
        // Wake at the actual deadline, including an hourly anchor partway through a minute.
        // The one-minute cap also notices wall-clock or time-zone changes while visible.
        delay((next.toEpochMilliseconds() - now.toEpochMilliseconds()).coerceIn(1L, 60_000L))
    }
}

/** Restart from wall time whenever Android/iOS resumes; don't run a UI timer in the background. */
@Composable
fun rememberHomeArtworkState(
    settings: HomeArtworkSettings = rememberHomeArtworkSettings(),
    clock: Clock = Clock.System,
): HomeArtworkState {
    val schedule = settings.schedule
    val updates = remember(schedule, clock) { homeArtworkUpdates(schedule, clock) }
    val now = clock.now()
    val state by updates.collectAsStateWithLifecycle(
        initialValue = HomeArtworkState(schedule.rotationAt(now), schedule.nextUpdateAt(now)),
        minActiveState = Lifecycle.State.RESUMED,
    )
    return state
}

@Composable
fun rememberHomeArtworkRotation(): Int = rememberHomeArtworkState().rotation
