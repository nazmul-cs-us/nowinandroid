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
