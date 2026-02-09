package com.starception.submission.core.contentdatabase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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

        /**
         * Refresh database from assets
         * Closes current database, deletes it, and re-creates from assets
         */
        fun refreshFromAssets(context: Context): Boolean {
            return try {
                closeDatabase()
                context.deleteDatabase(DATABASE_NAME)
                // Force re-creation by getting instance
                getInstance(context)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
