package com.starception.submission.core.duadatabase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Database for Fortress of the Muslim (Hisnul Muslim)
 * Contains Islamic duas and supplications for various occasions
 *
 * Database file: fortress_of_the_muslim.db (128KB)
 * Features:
 * - 129 chapters covering various occasions
 * - 282 invocations (duas) with Arabic, transliteration, and translation
 * - 43 footnotes with term definitions
 * - Searchable content
 */
@Database(
    entities = [
        DuaMetadataEntity::class,
        DuaChapterEntity::class,
        DuaInvocationEntity::class,
        DuaFootnoteEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DuaDatabase : RoomDatabase() {

    abstract fun duaDao(): DuaDao

    companion object {
        private const val DATABASE_NAME = "fortress_of_the_muslim.db"
        private const val TAG = "DuaDatabase"

        @Volatile
        private var INSTANCE: DuaDatabase? = null

        /**
         * Get the singleton instance of DuaDatabase
         */
        fun getInstance(context: Context): DuaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DuaDatabase::class.java,
                    DATABASE_NAME
                )
                    .createFromAsset("databases/$DATABASE_NAME")
                    .fallbackToDestructiveMigration()
                    .setJournalMode(JournalMode.TRUNCATE)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            android.util.Log.d(TAG, "✅ Dua database created successfully")
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            android.util.Log.d(TAG, "📿 Dua database opened")
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
                // Count chapters
                val chapterCursor = db.query("SELECT COUNT(*) FROM chapters")
                if (chapterCursor.moveToFirst()) {
                    val count = chapterCursor.getInt(0)
                    android.util.Log.d(TAG, "📚 Total Chapters: $count")
                }
                chapterCursor.close()

                // Count invocations
                val duaCursor = db.query("SELECT COUNT(*) FROM invocations")
                if (duaCursor.moveToFirst()) {
                    val count = duaCursor.getInt(0)
                    android.util.Log.d(TAG, "🤲 Total Duas: $count")
                }
                duaCursor.close()

                // Count footnotes
                val footnoteCursor = db.query("SELECT COUNT(*) FROM footnotes")
                if (footnoteCursor.moveToFirst()) {
                    val count = footnoteCursor.getInt(0)
                    android.util.Log.d(TAG, "📝 Total Footnotes: $count")
                }
                footnoteCursor.close()

            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Error logging database info", e)
            }
        }

        /**
         * Close database instance (for testing or cleanup)
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
            android.util.Log.d(TAG, "🔒 Dua database closed")
        }

        /**
         * Refresh database from assets
         * Closes current database, deletes it, and re-creates from assets
         * @return true if refresh was successful, false otherwise
         */
        fun refreshFromAssets(context: Context): Boolean {
            return try {
                android.util.Log.d(TAG, "🔄 Starting dua database refresh...")

                // Close existing database
                closeDatabase()

                // Delete the database file
                val deleted = context.deleteDatabase(DATABASE_NAME)
                android.util.Log.d(TAG, "🗑️ Database file deleted: $deleted")

                // Re-initialize (Room will copy from assets)
                getInstance(context)

                android.util.Log.d(TAG, "✅ Dua database refresh completed successfully")
                true
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Failed to refresh dua database", e)
                false
            }
        }

        /**
         * Get database info for developer display
         */
        fun getDatabaseInfo(context: Context): DuaDatabaseInfo {
            return try {
                val db = getInstance(context)
                val (chapterCount, duaCount) = kotlinx.coroutines.runBlocking {
                    Pair(db.duaDao().getChapterCount(), db.duaDao().getInvocationCount())
                }
                val dbFile = context.getDatabasePath(DATABASE_NAME)
                DuaDatabaseInfo(
                    name = "Fortress of the Muslim",
                    chapterCount = chapterCount,
                    duaCount = duaCount,
                    lastModified = dbFile.lastModified(),
                    sizeBytes = dbFile.length()
                )
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Error getting database info", e)
                DuaDatabaseInfo(name = "Fortress of the Muslim", chapterCount = 0, duaCount = 0, lastModified = 0L, sizeBytes = 0L)
            }
        }
    }
}

/**
 * Database info for developer display
 */
data class DuaDatabaseInfo(
    val name: String,
    val chapterCount: Int,
    val duaCount: Int,
    val lastModified: Long,
    val sizeBytes: Long
)
