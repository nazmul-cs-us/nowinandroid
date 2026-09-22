package com.starception.submission.core.contentdatabase

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
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
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            ensureShamayeleTopic(db)
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Close database instance and clear singleton
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }

        /** Adds the system topic to databases created by an older app version. */
        private fun ensureShamayeleTopic(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                INSERT OR REPLACE INTO topics (
                    id, name, short_description, long_description, image_url, url, icon,
                    is_system, is_user_created, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    9,
                    "Shamai'l At-Tirmidhi",
                    "الشمائل المحمدية",
                    "Explore 56 books and 322 hadiths describing the Prophet's appearance, " +
                        "character, manners, and daily life.",
                    "https://cdn-icons-png.flaticon.com/512/4556/4556746.png",
                    null,
                    null,
                    1,
                    0,
                    null,
                    null,
                ),
            )
        }

        /**
         * Refresh database from assets using Room DAO operations.
         * This keeps the database connection alive so Flows continue to work.
         */
        suspend fun refreshFromAssets(context: Context): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Starting topics database refresh from assets...")

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

                    Log.d(TAG, "Read ${topics.size} topics from assets")

                    // Clear and repopulate using DAO (keeps connection alive)
                    dao.deleteAllTopics()
                    dao.insertTopics(topics)

                    // Trigger invalidation so Flows update
                    db.invalidationTracker.refreshVersionsAsync()

                    Log.d(TAG, "Topics database refreshed successfully: ${topics.size} topics")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error refreshing topics database", e)
                    false
                }
            }
        }
    }
}
