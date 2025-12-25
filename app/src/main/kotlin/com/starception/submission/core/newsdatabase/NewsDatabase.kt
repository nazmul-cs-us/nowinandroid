package com.starception.submission.core.newsdatabase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Database for News Resources
 * Pre-populated from assets/databases/news.db
 *
 * Features:
 * - 436 news resources (114 Quran, 322 Duas)
 * - Many-to-many relationship with topics
 * - Support for user-created content
 * - Searchable content
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
        private const val TAG = "NewsDatabase"

        @Volatile
        private var INSTANCE: NewsDatabase? = null

        /**
         * Get the singleton instance of NewsDatabase
         */
        fun getInstance(context: Context): NewsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NewsDatabase::class.java,
                    DATABASE_NAME
                )
                    .createFromAsset("databases/$DATABASE_NAME")
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            android.util.Log.d(TAG, "News database created successfully")
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            android.util.Log.d(TAG, "News database opened")
                            logDatabaseInfo(db)
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Log database information on open
         */
        private fun logDatabaseInfo(db: SupportSQLiteDatabase) {
            try {
                // Count news resources
                val newsCursor = db.query("SELECT COUNT(*) FROM news_resources")
                if (newsCursor.moveToFirst()) {
                    val count = newsCursor.getInt(0)
                    android.util.Log.d(TAG, "Total News Resources: $count")
                }
                newsCursor.close()

                // Count topic associations
                val topicsCursor = db.query("SELECT COUNT(*) FROM news_topics")
                if (topicsCursor.moveToFirst()) {
                    val count = topicsCursor.getInt(0)
                    android.util.Log.d(TAG, "Topic Associations: $count")
                }
                topicsCursor.close()

                // Count by type
                val typeCursor = db.query("SELECT type, COUNT(*) FROM news_resources GROUP BY type")
                while (typeCursor.moveToNext()) {
                    val type = typeCursor.getString(0)
                    val count = typeCursor.getInt(1)
                    android.util.Log.d(TAG, "Type '$type': $count")
                }
                typeCursor.close()

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error logging database info", e)
            }
        }

        /**
         * Close database instance
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
            android.util.Log.d(TAG, "News database closed")
        }

        /**
         * Refresh database from assets
         * Closes current database, deletes it, and re-creates from assets
         * @return true if refresh was successful, false otherwise
         */
        fun refreshFromAssets(context: Context): Boolean {
            return try {
                android.util.Log.d(TAG, "Starting news database refresh...")

                // Close existing database
                closeDatabase()

                // Delete the database file
                val deleted = context.deleteDatabase(DATABASE_NAME)
                android.util.Log.d(TAG, "Database file deleted: $deleted")

                // Re-initialize (Room will copy from assets)
                getInstance(context)

                android.util.Log.d(TAG, "News database refresh completed successfully")
                true
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to refresh news database", e)
                false
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
                android.util.Log.e(TAG, "Error getting database info", e)
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
