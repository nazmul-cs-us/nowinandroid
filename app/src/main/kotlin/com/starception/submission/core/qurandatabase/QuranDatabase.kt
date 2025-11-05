package com.starception.submission.core.qurandatabase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.io.FileOutputStream

/**
 * Room Database for Quran
 * Contains Surahs, Ayahs, Juzs, and Hizbs
 */
@Database(
    entities = [
        SurahEntity::class,
        AyahEntity::class,
        JuzEntity::class,
        HizbEntity::class
    ],
    version = 1,
    exportSchema = false,
    autoMigrations = []
)
abstract class QuranDatabase : RoomDatabase() {
    
    abstract fun quranDao(): QuranDao
    
    companion object {
        private const val DATABASE_NAME = "quran.db"
        
        @Volatile
        private var INSTANCE: QuranDatabase? = null
        
        /**
         * Get the singleton instance of QuranDatabase
         */
        fun getInstance(context: Context): QuranDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QuranDatabase::class.java,
                    DATABASE_NAME
                )
                    .createFromAsset("databases/$DATABASE_NAME") // Load from assets
                    .fallbackToDestructiveMigration() // For development
                    .setJournalMode(JournalMode.TRUNCATE) // Simplify for pre-packaged DB
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            android.util.Log.d("QuranDatabase", "✅ Quran database created successfully")
                        }
                        
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            android.util.Log.d("QuranDatabase", "📖 Quran database opened")
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Convert SQL file to SQLite database
         * This function should be called to prepare the database file from quran.sql
         */
        fun convertSqlToDatabase(context: Context, sqlFilePath: String): Boolean {
            return try {
                android.util.Log.d("QuranDatabase", "📥 Converting SQL file to database...")
                
                // This is a placeholder - actual conversion would be done offline
                // and the resulting .db file would be placed in assets/databases/
                
                android.util.Log.w("QuranDatabase", "⚠️  SQL conversion should be done offline")
                android.util.Log.i("QuranDatabase", "💡 Place the converted quran.db file in app/src/main/assets/databases/")
                
                false
            } catch (e: Exception) {
                android.util.Log.e("QuranDatabase", "❌ Error converting SQL file", e)
                false
            }
        }
    }
}

