package com.starception.submission.core.qurandatabase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Database for Enhanced Quran
 * Contains comprehensive Arabic Quran with Tafseer, grammar analysis, and line-by-line layout
 *
 * Database file: quran_enhanced.db (30MB)
 * Features:
 * - 3 Tafseer books (Saadi, Moysar, Baghawi)
 * - Arabic grammar analysis (I'rab)
 * - Revelation context (Asbab al-Nuzul)
 * - Word meanings in Arabic
 * - Line-by-line layout for Mushaf page display
 * - Multiple Arabic text variants
 */
@Database(
    entities = [QuranEnhancedEntity::class],
    version = 1,
    exportSchema = false
)
abstract class QuranEnhancedDatabase : RoomDatabase() {

    abstract fun quranEnhancedDao(): QuranEnhancedDao

    companion object {
        private const val DATABASE_NAME = "quran_enhanced.db"

        @Volatile
        private var INSTANCE: QuranEnhancedDatabase? = null

        /**
         * Get the singleton instance of QuranEnhancedDatabase
         */
        fun getInstance(context: Context): QuranEnhancedDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QuranEnhancedDatabase::class.java,
                    DATABASE_NAME
                )
                    .createFromAsset("databases/$DATABASE_NAME") // Load from assets
                    .fallbackToDestructiveMigration() // For development
                    .setJournalMode(JournalMode.TRUNCATE) // Simplify for pre-packaged DB
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            android.util.Log.d(
                                "QuranEnhancedDB",
                                "✅ Enhanced Quran database created successfully"
                            )
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            android.util.Log.d(
                                "QuranEnhancedDB",
                                "📖 Enhanced Quran database opened"
                            )
                            // Log database info
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
                // Count total Ayahs
                val cursor = db.query("SELECT COUNT(*) FROM quran")
                if (cursor.moveToFirst()) {
                    val count = cursor.getInt(0)
                    android.util.Log.d("QuranEnhancedDB", "📊 Total Ayahs: $count")
                }
                cursor.close()

                // Count Surahs
                val surahCursor = db.query("SELECT COUNT(DISTINCT sora) FROM quran")
                if (surahCursor.moveToFirst()) {
                    val count = surahCursor.getInt(0)
                    android.util.Log.d("QuranEnhancedDB", "📚 Total Surahs: $count")
                }
                surahCursor.close()

                // Count pages
                val pageCursor = db.query("SELECT MAX(page) FROM quran")
                if (pageCursor.moveToFirst()) {
                    val count = pageCursor.getInt(0)
                    android.util.Log.d("QuranEnhancedDB", "📄 Total Pages: $count")
                }
                pageCursor.close()

            } catch (e: Exception) {
                android.util.Log.e("QuranEnhancedDB", "❌ Error logging database info", e)
            }
        }

        /**
         * Close database instance (for testing or cleanup)
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
            android.util.Log.d("QuranEnhancedDB", "🔒 Enhanced Quran database closed")
        }
    }
}
