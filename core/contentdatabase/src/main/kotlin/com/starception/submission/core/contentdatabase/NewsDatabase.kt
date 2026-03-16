package com.starception.submission.core.contentdatabase

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/**
 * Room Database for News Resources
 * Created empty by Room and populated via regenerateFromSources()
 *
 * Features:
 * - News resources with topics
 * - Many-to-many relationship with topics
 * - Support for user-created content
 * - Refresh from assets capability
 */
@Database(
    entities = [
        NewsResourceEntity::class,
        NewsTopicCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NewsDatabase : RoomDatabase() {

    abstract fun newsDao(): NewsDao

    companion object {
        private const val DATABASE_NAME = "news.db"
        private const val CDN_KEY = "databases/news.db"
        private const val TAG = "NewsDatabase"

        @Volatile
        private var INSTANCE: NewsDatabase? = null

        // Store resolved DB file for internal re-creation (refreshFromAssets)
        private var resolvedDbFile: File? = null

        /**
         * Get the singleton instance of NewsDatabase
         * @param preResolvedDbFile Optional pre-resolved File from CDN/extracted assets
         */
        fun getInstance(context: Context, preResolvedDbFile: File? = null): NewsDatabase {
            if (preResolvedDbFile != null) resolvedDbFile = preResolvedDbFile
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    NewsDatabase::class.java,
                    DATABASE_NAME
                )

                // Try pre-resolved file first; if unavailable, let Room create empty DB
                // (news.db is regenerated from source databases at runtime)
                val dbFile = preResolvedDbFile ?: resolvedDbFile
                if (dbFile != null && dbFile.exists()) {
                    builder.createFromFile(dbFile)
                }
                // No bundled fallback needed - Room will create empty schema,
                // then regenerateFromSources() populates it from quran.db, fortress_of_the_muslim.db, etc.

                val instance = builder
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            Log.d(TAG, "News database created successfully")
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            Log.d(TAG, "News database opened")
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Close database instance
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
            Log.d(TAG, "News database closed")
        }

        /**
         * Refresh database from assets
         * Closes current database, deletes it, and re-creates from assets
         * @return true if refresh was successful, false otherwise
         */
        fun refreshFromAssets(context: Context): Boolean {
            return try {
                Log.d(TAG, "Starting news database refresh...")

                // Close existing database
                closeDatabase()

                // Delete the database file
                val deleted = context.deleteDatabase(DATABASE_NAME)
                Log.d(TAG, "Database file deleted: $deleted")

                // Re-initialize (Room will copy from assets)
                getInstance(context)

                Log.d(TAG, "News database refresh completed successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh news database", e)
                false
            }
        }

        /**
         * Regenerate news.db from source databases (quran.db, fortress_of_the_muslim.db, quranic_duas.db)
         * Uses Room DAO to update the existing database, which works with singleton injected instances.
         *
         * @param context Application context
         * @return RegenerationResult with success status and counts
         */
        suspend fun regenerateFromSources(context: Context): RegenerationResult {
            Log.d(TAG, "Starting news database regeneration from sources...")

            return try {
                val db = getInstance(context)
                val dao = db.newsDao()

                // Use the generator to create entities and insert via Room
                val result = NewsDbGenerator.regenerateWithRoom(context, dao)

                if (result.success) {
                    Log.d(TAG, "News database regenerated successfully: ${result.totalNewsResources} resources")
                    // Force invalidation of all tables to trigger Flow updates
                    // This ensures all active Flows re-emit with new data
                    db.invalidationTracker.refreshVersionsAsync()
                    Log.d(TAG, "Database invalidation tracker refreshed")
                } else {
                    Log.e(TAG, "News database regeneration failed: ${result.error}")
                }

                result
            } catch (e: Exception) {
                Log.e(TAG, "Error regenerating news database", e)
                RegenerationResult(success = false, error = e.message)
            }
        }

        /**
         * Get database info for developer display
         */
        fun getDatabaseInfo(context: Context): DatabaseInfo {
            return try {
                val db = getInstance(context)
                val count = kotlinx.coroutines.runBlocking {
                    db.newsDao().getNewsResourceCount()
                }
                val dbFile = context.getDatabasePath(DATABASE_NAME)
                DatabaseInfo(
                    name = "News",
                    itemCount = count,
                    lastModified = dbFile.lastModified(),
                    sizeBytes = dbFile.length()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting database info", e)
                DatabaseInfo(name = "News", itemCount = 0, lastModified = 0L, sizeBytes = 0L)
            }
        }
    }
}

/**
 * Database info for developer display
 */
data class DatabaseInfo(
    val name: String,
    val itemCount: Int,
    val lastModified: Long,
    val sizeBytes: Long
)
