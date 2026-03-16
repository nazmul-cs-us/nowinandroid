package com.starception.submission.core.hadithdatabase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = false
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
            "Abu Dawud" to "sunan_abu_dawud.db",
            "Nasa'i" to "sunan_nasai.db",
            "Ibn Majah" to "sunan_ibn_majah.db",
            "Malik" to "muwatta_malik.db",
            "Ahmad" to "musnad_ahmad.db",
            "Darimi" to "sunan_darimi.db"
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
            val cdnKey = "$HADITH_DB_PATH$databaseFile"
            android.util.Log.d(TAG, "📖 Opening hadith database: $databaseFile")

            val builder = Room.databaseBuilder(
                context.applicationContext,
                HadithDatabase::class.java,
                "hadith_$databaseFile"
            )

            // Try CDN/extracted file first, fall back to bundled asset
            val dbFile = assetRepo?.getDatabaseFile(cdnKey)
                ?: run {
                    // Direct fallback: check cdn_assets directory even when assetRepo is null
                    // This handles the case where HadithRepository was created without AssetRepository
                    val cdnFile = java.io.File(context.applicationContext.filesDir, "cdn_assets/$cdnKey")
                    if (cdnFile.exists()) {
                        android.util.Log.d(TAG, "📂 Found CDN-downloaded DB directly: ${cdnFile.absolutePath}")
                        cdnFile
                    } else {
                        null
                    }
                }
            if (dbFile != null) {
                android.util.Log.d(TAG, "📂 Using file-based DB: ${dbFile.absolutePath}")
                builder.createFromFile(dbFile)
            } else {
                android.util.Log.d(TAG, "📦 Using bundled asset: $assetPath")
                builder.createFromAsset(assetPath)
            }

            return builder
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
            return try {
                val dbPath = context.getDatabasePath("hadith_temp_meta.db")
                if (dbPath.exists()) dbPath.delete()

                // Copy from assets to temp location
                context.assets.open("$HADITH_DB_PATH$databaseFile").use { input ->
                    dbPath.parentFile?.mkdirs()
                    java.io.FileOutputStream(dbPath).use { output ->
                        input.copyTo(output)
                    }
                }

                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbPath.absolutePath,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )

                var metadata: HadithCollectionMetadata? = null
                val cursor = db.rawQuery("SELECT key, value FROM metadata", null)

                val metaMap = mutableMapOf<String, String>()
                while (cursor.moveToNext()) {
                    val key = cursor.getString(0)
                    val value = cursor.getString(1)
                    metaMap[key] = value
                }
                cursor.close()
                db.close()
                dbPath.delete()

                if (metaMap.isNotEmpty()) {
                    metadata = HadithCollectionMetadata(
                        collectionId = metaMap["collection_id"]?.toIntOrNull() ?: 0,
                        name = metaMap["name"] ?: "",
                        nameArabic = metaMap["name_arabic"] ?: "",
                        nameEnglish = metaMap["name_english"] ?: "",
                        author = metaMap["author"] ?: "",
                        authorArabic = metaMap["author_arabic"] ?: "",
                        hasElaboration = metaMap["has_elaboration"] == "1",
                        hadithCount = metaMap["hadith_count"]?.toIntOrNull() ?: 0
                    )
                }

                metadata
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Error getting collection metadata", e)
                null
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
    }
}
