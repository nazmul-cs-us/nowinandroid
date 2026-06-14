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
