/*
 * Copyright 2021 The Android Open Source Project
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

package com.starception.submission.shared.settings

import com.starception.submission.prayer.model.PrayerTimeOffsets
import com.starception.submission.shared.storage.KeyValueStore
import com.starception.submission.shared.storage.platformKeyValueStore
import kotlinx.serialization.json.Json

/**
 * The adjustments a user has made to their prayer times.
 *
 * Separate from the country's published offsets: those describe what the local
 * authority announces, these describe the user disagreeing with the calculation
 * — usually to match the mosque they actually pray at. Both apply, which is why
 * they are added rather than one overriding the other.
 *
 * Reuses [PrayerTimeOffsets] from the shared engine rather than defining another
 * offsets type, so a value written on one platform means the same on the other.
 */
class UserPrayerSettings(private val store: KeyValueStore = platformKeyValueStore()) {

    private val json = Json { ignoreUnknownKeys = true }

    fun offsets(): PrayerTimeOffsets {
        val stored = store.getString(KEY_OFFSETS) ?: return PrayerTimeOffsets()
        // A corrupt or outdated value should not stop the app showing times;
        // falling back to no adjustment is both safe and obvious to the user.
        return runCatching { json.decodeFromString<PrayerTimeOffsets>(stored) }
            .getOrElse { PrayerTimeOffsets() }
    }

    fun setOffsets(offsets: PrayerTimeOffsets) {
        store.putString(KEY_OFFSETS, json.encodeToString(offsets))
    }

    /** Adjusts one prayer by [delta] minutes, clamped to the supported range. */
    fun adjust(prayer: String, delta: Int): PrayerTimeOffsets {
        val current = offsets()
        val updated = current.withOffset(
            prayer = prayer,
            minutes = (current.getOffset(prayer) + delta).coerceIn(-MAX_OFFSET, MAX_OFFSET),
        )
        setOffsets(updated)
        return updated
    }

    private companion object {
        const val KEY_OFFSETS = "prayer_time_offsets"

        /**
         * The same range the Android dial allows: three hours either way. Wide
         * enough for any mosque's practice, bounded so a runaway drag cannot put
         * a prayer on the wrong day.
         */
        const val MAX_OFFSET = 180
    }
}

/** Returns a copy with [prayer]'s offset replaced. */
fun PrayerTimeOffsets.withOffset(prayer: String, minutes: Int): PrayerTimeOffsets =
    when (prayer.lowercase()) {
        "fajr" -> copy(fajr = minutes)
        "sunrise" -> copy(sunrise = minutes)
        "dhuhr" -> copy(dhuhr = minutes)
        "asr" -> copy(asr = minutes)
        "maghrib" -> copy(maghrib = minutes)
        "isha" -> copy(isha = minutes)
        else -> this
    }

/** Reads as `+5m`, `-3m`, or empty when unadjusted — matching the Android tiles. */
fun formatOffset(minutes: Int): String = when {
    minutes > 0 -> "+${minutes}m"
    minutes < 0 -> "${minutes}m"
    else -> ""
}
