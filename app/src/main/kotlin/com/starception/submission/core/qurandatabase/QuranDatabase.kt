package com.starception.submission.core.qurandatabase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.starception.submission.download.AssetRepository
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
        HizbEntity::class,
        FavouriteAyahEntity::class,
        AyahNoteEntity::class
    ],
    version = 3,
    exportSchema = false,
    autoMigrations = []
)
abstract class QuranDatabase : RoomDatabase() {
    
    abstract fun quranDao(): QuranDao
    
    companion object {
        private const val DATABASE_NAME = "quran.db"
        private const val CDN_KEY = "databases/quran/quran.db"

        @Volatile
        private var INSTANCE: QuranDatabase? = null

        /**
         * Migration from version 1 to 2: Add favourite_ayahs table
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                android.util.Log.d("QuranDatabase", "🔄 Migrating database from version 1 to 2...")

                // Create favourite_ayahs table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS favourite_ayahs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        surah_number INTEGER NOT NULL,
                        ayah_number INTEGER NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())

                // Create unique index on surah_number and ayah_number
                database.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_favourite_unique
                    ON favourite_ayahs (surah_number, ayah_number)
                """.trimIndent())

                android.util.Log.d("QuranDatabase", "✅ Migration completed: favourite_ayahs table created")
            }
        }

        /**
         * Migration from version 2 to 3: Add ayah_notes table
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                android.util.Log.d("QuranDatabase", "🔄 Migrating database from version 2 to 3...")

                // Create ayah_notes table for user notes
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS ayah_notes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        surah_number INTEGER NOT NULL,
                        ayah_number INTEGER NOT NULL,
                        note_text TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())

                // Create index on surah_number and ayah_number for fast lookups
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS idx_note_surah_ayah
                    ON ayah_notes (surah_number, ayah_number)
                """.trimIndent())

                android.util.Log.d("QuranDatabase", "✅ Migration completed: ayah_notes table created")
            }
        }

        /**
         * Get the singleton instance of QuranDatabase
         */
        fun getInstance(context: Context, assetRepository: AssetRepository? = null): QuranDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    QuranDatabase::class.java,
                    DATABASE_NAME
                )

                // Try CDN/extracted file first, fall back to bundled asset
                val dbFile = assetRepository?.getDatabaseFile(CDN_KEY)
                if (dbFile != null) {
                    builder.createFromFile(dbFile)
                } else {
                    builder.createFromAsset("databases/$DATABASE_NAME")
                }

                val instance = builder
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .setJournalMode(JournalMode.TRUNCATE)
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

