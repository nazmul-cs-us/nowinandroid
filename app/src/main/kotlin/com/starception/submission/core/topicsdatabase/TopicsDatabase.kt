package com.starception.submission.core.topicsdatabase

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Room Database for Topics
 * Pre-populated from assets/databases/topics.db
 *
 * Features:
 * - 19 system topics (pre-installed)
 * - Support for user-created topics
 * - Searchable content
 */
@Database(
    entities = [TopicEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TopicsDatabase : RoomDatabase() {

    abstract fun topicsDao(): TopicsDao

    companion object {
        private const val DATABASE_NAME = "topics.db"
        private const val TAG = "TopicsDatabase"

        @Volatile
        private var INSTANCE: TopicsDatabase? = null

        /**
         * Get the singleton instance of TopicsDatabase
         */
        fun getInstance(context: Context): TopicsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TopicsDatabase::class.java,
                    DATABASE_NAME
                )
                    .createFromAsset("databases/$DATABASE_NAME")
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            android.util.Log.d(TAG, "Topics database created successfully")
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            android.util.Log.d(TAG, "Topics database opened")
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
                val cursor = db.query("SELECT COUNT(*) FROM topics")
                if (cursor.moveToFirst()) {
                    val count = cursor.getInt(0)
                    android.util.Log.d(TAG, "Total Topics: $count")
                }
                cursor.close()

                // Count user topics
                val userCursor = db.query("SELECT COUNT(*) FROM topics WHERE is_user_created = 1")
                if (userCursor.moveToFirst()) {
                    val userCount = userCursor.getInt(0)
                    android.util.Log.d(TAG, "User Topics: $userCount")
                }
                userCursor.close()

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
            android.util.Log.d(TAG, "Topics database closed")
        }

        /**
         * Refresh database from assets using Room DAO operations.
         * This keeps the database connection alive so Flows continue to work.
         * @return true if refresh was successful, false otherwise
         */
        suspend fun refreshFromAssets(context: Context): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    android.util.Log.d(TAG, "Starting topics database refresh from assets...")

                    val db = getInstance(context)
                    val dao = db.topicsDao()

                    // Copy asset database to temp file to read from
                    val tempFile = File(context.cacheDir, "temp_topics.db")
                    context.assets.open("databases/$DATABASE_NAME").use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Read topics from asset database
                    val assetDb = SQLiteDatabase.openDatabase(
                        tempFile.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY
                    )

                    val topics = mutableListOf<TopicEntity>()
                    val cursor = assetDb.rawQuery("SELECT * FROM topics ORDER BY id ASC", null)

                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                        val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                        val shortDescription = cursor.getString(cursor.getColumnIndexOrThrow("short_description"))
                        val longDescription = cursor.getString(cursor.getColumnIndexOrThrow("long_description"))
                        val url = cursor.getString(cursor.getColumnIndexOrThrow("url"))
                        val imageUrl = cursor.getString(cursor.getColumnIndexOrThrow("image_url"))
                        val isSystem = cursor.getInt(cursor.getColumnIndexOrThrow("is_system"))
                        val isUserCreated = cursor.getInt(cursor.getColumnIndexOrThrow("is_user_created"))
                        // Optional columns that might not exist in asset database
                        val icon = cursor.getColumnIndex("icon").takeIf { it >= 0 }?.let { cursor.getString(it) }
                        val createdAt = cursor.getColumnIndex("created_at").takeIf { it >= 0 }?.let { cursor.getString(it) }
                        val updatedAt = cursor.getColumnIndex("updated_at").takeIf { it >= 0 }?.let { cursor.getString(it) }

                        topics.add(TopicEntity(
                            id = id,
                            name = name,
                            shortDescription = shortDescription,
                            longDescription = longDescription,
                            url = url,
                            imageUrl = imageUrl,
                            icon = icon,
                            isSystem = isSystem,
                            isUserCreated = isUserCreated,
                            createdAt = createdAt,
                            updatedAt = updatedAt
                        ))
                    }
                    cursor.close()
                    assetDb.close()
                    tempFile.delete()

                    android.util.Log.d(TAG, "Read ${topics.size} topics from assets")

                    // Clear and repopulate using DAO (keeps connection alive)
                    dao.deleteAllTopics()
                    dao.insertTopics(topics)

                    // Trigger invalidation so Flows update
                    db.invalidationTracker.refreshVersionsAsync()

                    android.util.Log.d(TAG, "Topics database refreshed successfully: ${topics.size} topics")
                    true
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error refreshing topics database", e)
                    false
                }
            }
        }

        /**
         * Get database info for developer display
         */
        fun getDatabaseInfo(context: Context): TopicsDatabaseInfo {
            return try {
                val db = getInstance(context)
                val count = kotlinx.coroutines.runBlocking {
                    db.topicsDao().getTopicCount()
                }
                val dbFile = context.getDatabasePath(DATABASE_NAME)
                TopicsDatabaseInfo(
                    name = "Topics",
                    itemCount = count,
                    lastModified = dbFile.lastModified(),
                    sizeBytes = dbFile.length()
                )
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error getting database info", e)
                TopicsDatabaseInfo(name = "Topics", itemCount = 0, lastModified = 0L, sizeBytes = 0L)
            }
        }
    }
}

/**
 * Database info for developer display
 */
data class TopicsDatabaseInfo(
    val name: String,
    val itemCount: Int,
    val lastModified: Long,
    val sizeBytes: Long
)
