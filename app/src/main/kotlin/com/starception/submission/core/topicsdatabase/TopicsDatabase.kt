package com.starception.submission.core.topicsdatabase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

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
    }
}
