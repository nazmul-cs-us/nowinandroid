package com.starception.submission.core.quranicduas

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
         * Refresh database from assets using Room DAO operations.
         * This keeps the database connection alive so Flows continue to work.
         */
        suspend fun refreshFromAssets(context: Context): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    android.util.Log.d(TAG, "Starting Quranic Duas database refresh from assets...")

                    val db = getInstance(context)
                    val dao = db.quranicDuaDao()

                    // Copy asset database to temp file to read from
                    val tempFile = File(context.cacheDir, "temp_quranic_duas.db")
                    context.assets.open("databases/$DATABASE_NAME").use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Read from asset database
                    val assetDb = SQLiteDatabase.openDatabase(
                        tempFile.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY
                    )

                    val duas = mutableListOf<QuranicDuaEntity>()
                    val cursor = assetDb.rawQuery("SELECT * FROM quranic_duas ORDER BY dua_number ASC", null)

                    while (cursor.moveToNext()) {
                        duas.add(QuranicDuaEntity(
                            id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                            duaNumber = cursor.getInt(cursor.getColumnIndexOrThrow("dua_number")),
                            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                            surahReference = cursor.getString(cursor.getColumnIndexOrThrow("surah_reference")),
                            arabic = cursor.getString(cursor.getColumnIndexOrThrow("arabic")),
                            transliteration = cursor.getString(cursor.getColumnIndexOrThrow("transliteration")),
                            translation = cursor.getString(cursor.getColumnIndexOrThrow("translation")),
                            explanation = cursor.getString(cursor.getColumnIndexOrThrow("explanation")),
                            createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at")),
                            updatedAt = cursor.getString(cursor.getColumnIndexOrThrow("updated_at"))
                        ))
                    }
                    cursor.close()
                    assetDb.close()
                    tempFile.delete()

                    android.util.Log.d(TAG, "Read ${duas.size} Quranic Duas from assets")

                    // Clear and repopulate using DAO (keeps connection alive)
                    dao.deleteAll()
                    dao.insertAll(duas)

                    // Trigger invalidation so Flows update
                    db.invalidationTracker.refreshVersionsAsync()

                    android.util.Log.d(TAG, "Quranic Duas database refreshed successfully: ${duas.size} duas")
                    true
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to refresh Quranic Duas database", e)
                    false
                }
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
