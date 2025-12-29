package com.starception.submission.core.hadithdatabase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

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

        /**
         * Get database instance for a specific collection file
         * @param context Application context
         * @param databaseFile Database filename (e.g., "sahih_bukhari.db")
         */
        fun getInstance(context: Context, databaseFile: String): HadithDatabase {
            return instances.getOrPut(databaseFile) {
                synchronized(this) {
                    createDatabase(context, databaseFile)
                }
            }
        }

        /**
         * Get database instance by collection name
         * @param context Application context
         * @param collectionName Collection name (e.g., "Bukhari", "Muslim")
         */
        fun getInstanceByCollectionName(context: Context, collectionName: String): HadithDatabase? {
            val dbFile = collectionToFile[collectionName] ?: return null
            return getInstance(context, dbFile)
        }

        /**
         * Create database from assets
         */
        private fun createDatabase(context: Context, databaseFile: String): HadithDatabase {
            val assetPath = "$HADITH_DB_PATH$databaseFile"
            android.util.Log.d(TAG, "📖 Opening hadith database: $databaseFile")

            return Room.databaseBuilder(
                context.applicationContext,
                HadithDatabase::class.java,
                "hadith_$databaseFile" // Unique database name
            )
                .createFromAsset(assetPath)
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
