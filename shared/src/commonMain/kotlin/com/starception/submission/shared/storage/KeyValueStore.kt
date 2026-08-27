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

package com.starception.submission.shared.storage

/**
 * The smallest persistence the shared UI needs: strings under keys.
 *
 * An interface rather than an `expect class` so tests can supply their own. The
 * first version was an expect class, and its tests passed on JVM while failing on
 * iOS: the Android actual was in-memory and gave every instance a clean slate,
 * whereas iOS used real NSUserDefaults and leaked state between cases. The tests
 * were green because they were not touching real persistence.
 *
 * Not DataStore or SQLDelight for the same reason `httpGet` is not Ktor —
 * choosing the project's multiplatform storage stack belongs with the settings
 * and Quran work, not with a set of prayer checkmarks.
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/** The store backed by whatever the platform persists to. */
expect fun platformKeyValueStore(): KeyValueStore

/** For tests, and for platforms with nothing to persist to yet. */
class InMemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) {
        values[key] = value
    }
}
