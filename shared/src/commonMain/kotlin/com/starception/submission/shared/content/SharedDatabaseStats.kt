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

package com.starception.submission.shared.content

/** One database's developer info — Android's DatabaseInfo. */
data class SharedDatabaseInfo(
    val key: String,
    val label: String,
    val itemCount: Int,
    val itemLabel: String,
    val sizeBytes: Long,
    /** True when the database ships inside the app bundle (not deletable). */
    val bundled: Boolean,
)

/**
 * Developer options' database stats — reads the four content databases
 * (news, topics, Fortress duas, Quranic duas) the way the repositories do,
 * and refreshes the CDN-resolved copies by dropping their cached files.
 */
expect class SharedDatabaseStats() {
    suspend fun snapshot(): List<SharedDatabaseInfo>

    /**
     * Drops the cached CDN copy of [key] ("fortress" / "quranic_duas");
     * bundled databases return false. The next read re-downloads it.
     */
    fun refresh(key: String): Boolean
}
