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

package com.starception.submission.core.hadithdatabase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.starception.submission.download.AssetRepository

/**
 * Room Database for Hadith collections
 * Dynamically opens different hadith databases (Bukhari, Muslim, etc.)
 *
 * Database files are in assets/databases/hadith/
 * Each collection has its own database file with the same schema
 */
@Database(
    entities = [HadithEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class HadithDatabase : RoomDatabase() {

    abstract fun hadithDao(): HadithDao

    companion object {
        private const val TAG = "HadithDatabase"
        private const val HADITH_DB_PATH = "databases/hadith/"

        // Cache of database instances by filename
        private val instances = mutableMapOf<String, HadithDatabase>()

        /**
         * Mapping from collection names in references to database files
         */
        private val collectionToFile = mapOf(
            "Bukhari" to "sahih_bukhari.db",
            "Muslim" to "sahih_muslim.db",
            "Tirmidhi" to "sunan_tirmidhi.db",
            "Shamai'l At-Tirmidhi" to "shamayele_tirmidhi_complete.db",
            "Abu Dawud" to "sunan_abu_dawud.db",
            "Nasa'i" to "sunan_nasai.db",
            "Ibn Majah" to "sunan_ibn_majah.db",
            "Malik" to "muwatta_malik.db",
            "Ahmad" to "musnad_ahmad.db",
            "Darimi" to "sunan_darimi.db",
        )

        // Stored AssetRepository reference for database creation
        private var assetRepo: AssetRepository? = null

        /**
         * Get database instance for a specific collection file
         * @param context Application context
         * @param databaseFile Database filename (e.g., "sahih_bukhari.db")
         * @param assetRepository Optional AssetRepository for CDN asset support
         */
        fun getInstance(context: Context, databaseFile: String, assetRepository: AssetRepository? = null): HadithDatabase {
            if (assetRepository != null) assetRepo = assetRepository
            instances[databaseFile]?.let { return it }
            return synchronized(this) {
                instances[databaseFile]?.let { return it }
                val db = createDatabase(context, databaseFile)
                instances[databaseFile] = db
                db
            }
        }

        /**
         * Get database instance by collection name
         * @param context Application context
         * @param collectionName Collection name (e.g., "Bukhari", "Muslim")
         * @param assetRepository Optional AssetRepository for CDN asset support
         */
        fun getInstanceByCollectionName(context: Context, collectionName: String, assetRepository: AssetRepository? = null): HadithDatabase? {
            val dbFile = collectionToFile[collectionName] ?: return null
            return getInstance(context, dbFile, assetRepository)
        }

        /**
         * Create database from CDN download or bundled assets
         */
        private fun createDatabase(context: Context, databaseFile: String): HadithDatabase {
            val assetPath = "$HADITH_DB_PATH$databaseFile"
            android.util.Log.d(TAG, "📖 Opening hadith database: $databaseFile")

            val builder = Room.databaseBuilder(
                context.applicationContext,
                HadithDatabase::class.java,
                "hadith_$databaseFile",
            )

            // Try CDN/extracted file first, fall back to bundled asset
            val dbFile = resolveDownloadedDatabaseFile(context, databaseFile)
            if (dbFile != null) {
                android.util.Log.d(TAG, "📂 Using file-based DB: ${dbFile.absolutePath}")
                builder.createFromFile(dbFile)
            } else if (hasBundledAsset(context, databaseFile)) {
                android.util.Log.d(TAG, "📦 Using bundled asset: $assetPath")
                builder.createFromAsset(assetPath)
            } else {
                throw IllegalStateException(
                    "Hadith database missing: $databaseFile. Download the required content assets and try again.",
                )
            }

            return builder
                // Version 2 adds source-specific auxiliary tables (such as
                // hadith_details) without changing Room's hadiths table.
                // A no-op migration keeps version-1 collections intact.
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .setJournalMode(JournalMode.TRUNCATE)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        android.util.Log.d(TAG, "📖 Hadith database opened: $databaseFile")
                    }
                })
                .build()
        }

        /**
         * Get collection metadata from database
         */
        suspend fun getCollectionMetadata(context: Context, databaseFile: String): HadithCollectionMetadata? {
            var temporaryFile: java.io.File? = null
            return try {
                val sourceFile = resolveDownloadedDatabaseFile(context, databaseFile) ?: run {
                    // Use a unique cache file because metadata for multiple collections can be
                    // requested concurrently. The previous shared filename could be deleted while
                    // another reader still had it open.
                    java.io.File.createTempFile("hadith_metadata_", ".db", context.cacheDir).also {
                        temporaryFile = it
                        context.assets.open("$HADITH_DB_PATH$databaseFile").use { input ->
                            java.io.FileOutputStream(it).use { output -> input.copyTo(output) }
                        }
                    }
                }

                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    sourceFile.absolutePath,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
                )

                val metaMap = mutableMapOf<String, String>()
                try {
                    db.rawQuery("SELECT key, value FROM metadata", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            val key = cursor.getString(0)
                            val value = cursor.getString(1)
                            metaMap[key] = value
                        }
                    }
                } finally {
                    db.close()
                }

                if (metaMap.isNotEmpty()) {
                    HadithCollectionMetadata(
                        collectionId = metaMap["collection_id"]?.toIntOrNull() ?: 0,
                        name = metaMap["name"] ?: "",
                        nameArabic = metaMap["name_arabic"] ?: "",
                        nameEnglish = metaMap["name_english"] ?: "",
                        author = metaMap["author"] ?: "",
                        authorArabic = metaMap["author_arabic"] ?: "",
                        hasElaboration = metaMap["has_elaboration"] == "1",
                        hadithCount = metaMap["hadith_count"]?.toIntOrNull() ?: 0,
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Error getting collection metadata", e)
                null
            } finally {
                temporaryFile?.delete()
            }
        }

        /**
         * Get list of available hadith collection files
         */
        fun getAvailableCollections(): List<String> {
            return collectionToFile.values.toList()
        }

        /**
         * Get collection name from database file
         */
        fun getCollectionNameFromFile(databaseFile: String): String {
            return collectionToFile.entries.find { it.value == databaseFile }?.key ?: databaseFile
        }

        /**
         * Clear a cached instance so it will be recreated on next access.
         * Call this after downloading a database from CDN to ensure
         * the new file is used instead of a previously failed instance.
         */
        fun clearInstance(context: Context, databaseFile: String) {
            synchronized(this) {
                val existing = instances.remove(databaseFile)
                if (existing != null) {
                    try {
                        existing.close()
                    } catch (_: Exception) {}
                }
                // Delete Room's cached DB so it recreates from the CDN-downloaded file
                val roomDbName = "hadith_$databaseFile"
                val dbPath = context.getDatabasePath(roomDbName)
                if (dbPath.exists()) {
                    dbPath.delete()
                    android.util.Log.d(TAG, "🗑️ Deleted cached Room DB: $roomDbName")
                }
                // Also delete WAL and SHM files
                val walFile = java.io.File(dbPath.absolutePath + "-wal")
                val shmFile = java.io.File(dbPath.absolutePath + "-shm")
                if (walFile.exists()) walFile.delete()
                if (shmFile.exists()) shmFile.delete()
                android.util.Log.d(TAG, "🔄 Cleared instance for: $databaseFile")
            }
        }

        fun isDatabaseAvailable(context: Context, databaseFile: String): Boolean {
            return resolveDownloadedDatabaseFile(context, databaseFile) != null ||
                hasBundledAsset(context, databaseFile)
        }

        /**
         * Close all database instances
         */
        fun closeAll() {
            instances.forEach { (name, db) ->
                try {
                    db.close()
                    android.util.Log.d(TAG, "🔒 Closed hadith database: $name")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "❌ Error closing database: $name", e)
                }
            }
            instances.clear()
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        private fun resolveDownloadedDatabaseFile(context: Context, databaseFile: String): java.io.File? {
            val cdnKey = "$HADITH_DB_PATH$databaseFile"
            return assetRepo?.getDatabaseFile(cdnKey)
                ?: run {
                    val cdnFile = java.io.File(context.applicationContext.filesDir, "cdn_assets/$cdnKey")
                    if (cdnFile.isFile && cdnFile.length() > 0L) {
                        android.util.Log.d(TAG, "📂 Found CDN-downloaded DB directly: ${cdnFile.absolutePath}")
                        cdnFile
                    } else {
                        null
                    }
                }
        }

        private fun hasBundledAsset(context: Context, databaseFile: String): Boolean {
            val assetPath = "$HADITH_DB_PATH$databaseFile"
            return try {
                context.assets.open(assetPath).use { true }
            } catch (_: Exception) {
                false
            }
        }
    }
}
