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

package com.starception.submission.core.model.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Read side of the on-device AI hadith-book generator
 * (app `ml.HadithBookCategorizer` + `GeneratedHadithBooksStore`).
 *
 * Feature modules cannot reach the app module's store, so the app installs a
 * lookup at process start and browsing code reads through this bridge. A
 * registry collection whose verified [HadithCollection.books] are absent
 * falls back to whatever the AI generator persisted for its database.
 */
object GeneratedHadithBooks {
    private val _version = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** Bumped by the app after a regeneration run so browsers recompose. */
    val version: kotlinx.coroutines.flow.StateFlow<Int> = _version.asStateFlow()

    /** Set by the app process at startup; plain var is fine — see [version] notes. */
    var provider: ((databaseFile: String) -> List<HadithCollectionBookEntry>)? = null

    /** The AI-generated books persisted for this collection, or empty. */
    fun booksFor(databaseFile: String): List<HadithCollectionBookEntry> =
        provider?.invoke(databaseFile) ?: emptyList()

    /** Signals every collector of [version] that the store changed. */
    fun notifyChanged() {
        _version.value += 1
    }
}
