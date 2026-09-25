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

package com.starception.submission.usersettings

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Simple key/value bag for store-level metadata (last known country, sync bookkeeping, etc.).
 * Kept in the same DB file so it travels with the per-country rows during cloud sync.
 */
@Entity(tableName = "settings_meta")
data class SettingsMetaEntity(
    @PrimaryKey val key: String,
    val value: String?,
)
