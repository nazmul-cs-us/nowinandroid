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

package com.starception.submission.shared.salah

import com.starception.submission.shared.storage.KeyValueStore
import com.starception.submission.shared.storage.platformKeyValueStore
import kotlinx.datetime.LocalDate

/** The five obligatory prayers, in order. Sunrise is not one of them. */
val FARD_PRAYERS = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

/**
 * Which of today's prayers the user has marked as prayed.
 *
 * Stored per date so that a new day starts empty without anything having to
 * clear it — the absence of a record for today *is* the empty state, which
 * removes any need for a midnight reset that could fail to run.
 */
class SalahTracker(private val store: KeyValueStore = platformKeyValueStore()) {

    fun completed(date: LocalDate): Set<String> =
        store.getString(keyFor(date))
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()

    /** Marks [prayer] prayed, or unmarks it if it already was. */
    fun toggle(date: LocalDate, prayer: String): Set<String> {
        val current = completed(date)
        val updated = if (prayer in current) current - prayer else current + prayer
        // Written in the canonical prayer order rather than set order, so the
        // stored value is stable and readable when inspected by hand.
        store.putString(
            keyFor(date),
            FARD_PRAYERS.filter { it in updated }.joinToString(SEPARATOR),
        )
        return updated
    }

    private fun keyFor(date: LocalDate) = "$KEY_PREFIX$date"

    private companion object {
        const val KEY_PREFIX = "salah_completed_"
        const val SEPARATOR = ","
    }
}

/**
 * How the tracker reads on the home page tile.
 *
 * [nextUnprayed] is the first *unmarked* prayer in order, which is not the same
 * as the next prayer by time: someone who has not marked Fajr should still be
 * prompted about Fajr in the afternoon, rather than the tile skipping ahead.
 */
data class SalahProgress(
    val completed: Set<String>,
    val nextUnprayed: String?,
) {
    val completedCount: Int get() = completed.size
    val remainingCount: Int get() = FARD_PRAYERS.size - completed.size

    val headline: String
        get() = when (completedCount) {
            0 -> "No prayers marked"
            1 -> "1 prayer complete"
            FARD_PRAYERS.size -> "All prayers complete"
            else -> "$completedCount prayers complete"
        }

    val detail: String
        get() = when {
            remainingCount == 0 -> "Nothing remaining today"
            nextUnprayed != null -> "$remainingCount remain · $nextUnprayed is next"
            else -> "$remainingCount remain"
        }

    companion object {
        fun from(completed: Set<String>) = SalahProgress(
            completed = completed,
            nextUnprayed = FARD_PRAYERS.firstOrNull { it !in completed },
        )
    }
}
