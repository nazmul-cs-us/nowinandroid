package com.starception.submission.core.quranicduas

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Database for Quranic Duas
 * Pre-populated from assets/databases/quranic_duas.db
 *
 * Contains 40 duas directly from the Holy Quran
 */
@Database(
    entities = [QuranicDuaEntity::class],
    version = 1,
    exportSchema = false
)
abstract class QuranicDuaDatabase : RoomDatabase() {

    abstract fun quranicDuaDao(): QuranicDuaDao

    companion object {
        private const val DATABASE_NAME = "quranic_duas.db"
        private const val TAG = "QuranicDuaDatabase"

        @Volatile
        private var INSTANCE: QuranicDuaDatabase? = null

        /**
         * Get the singleton instance of QuranicDuaDatabase
         */
        fun getInstance(context: Context): QuranicDuaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QuranicDuaDatabase::class.java,
                    DATABASE_NAME
                )
                    .createFromAsset("databases/$DATABASE_NAME")
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            android.util.Log.d(TAG, "Quranic Duas database created successfully")
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            android.util.Log.d(TAG, "Quranic Duas database opened")
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
                val cursor = db.query("SELECT COUNT(*) FROM quranic_duas")
                if (cursor.moveToFirst()) {
                    val count = cursor.getInt(0)
                    android.util.Log.d(TAG, "Total Quranic Duas: $count")
                }
                cursor.close()
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
            android.util.Log.d(TAG, "Quranic Duas database closed")
        }

        /**
         * Refresh database from assets
         */
        fun refreshFromAssets(context: Context): Boolean {
            return try {
                android.util.Log.d(TAG, "Starting Quranic Duas database refresh...")

                closeDatabase()

                val deleted = context.deleteDatabase(DATABASE_NAME)
                android.util.Log.d(TAG, "Database file deleted: $deleted")

                getInstance(context)

                android.util.Log.d(TAG, "Quranic Duas database refresh completed successfully")
                true
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to refresh Quranic Duas database", e)
                false
            }
        }

        /**
         * Get database info for developer display
         */
        fun getDatabaseInfo(context: Context): QuranicDuaDatabaseInfo {
            return try {
                val db = getInstance(context)
                val count = kotlinx.coroutines.runBlocking {
                    db.quranicDuaDao().getQuranicDuaCount()
                }
                val dbFile = context.getDatabasePath(DATABASE_NAME)
                QuranicDuaDatabaseInfo(
                    name = "Quranic Duas",
                    itemCount = count,
                    lastModified = dbFile.lastModified(),
                    sizeBytes = dbFile.length()
                )
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error getting database info", e)
                QuranicDuaDatabaseInfo(name = "Quranic Duas", itemCount = 0, lastModified = 0L, sizeBytes = 0L)
            }
        }
    }
}

/**
 * Database info for developer display
 */
data class QuranicDuaDatabaseInfo(
    val name: String,
    val itemCount: Int,
    val lastModified: Long,
    val sizeBytes: Long
)
