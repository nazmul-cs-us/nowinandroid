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

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Writable user-settings database. The file [DATABASE_NAME] is the single artifact synced to
 * Cloudflare (Phase B/C) — keep all user-owned, syncable settings inside it.
 */
@Database(
    entities = [
        PrayerCountrySettingsEntity::class,
        SettingsMetaEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class UserSettingsDatabase : RoomDatabase() {

    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        const val DATABASE_NAME = "user_settings.db"
    }
}
