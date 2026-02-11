package com.starception.submission.core.duadatabase

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
        DuaFootnoteEntity::class,
        HadithReferenceEntity::class
    ],
    version = 5,
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

                // Count hadith references
                val hadithCursor = db.query("SELECT COUNT(*) FROM hadith_references")
                if (hadithCursor.moveToFirst()) {
                    val count = hadithCursor.getInt(0)
                    android.util.Log.d(TAG, "📖 Total Hadith References: $count")
                }
                hadithCursor.close()

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
         * Refresh database from assets using Room DAO operations.
         * This keeps the database connection alive so Flows continue to work.
         * @return true if refresh was successful, false otherwise
         */
        suspend fun refreshFromAssets(context: Context): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    android.util.Log.d(TAG, "🔄 Starting dua database refresh from assets...")

                    val db = getInstance(context)
                    val dao = db.duaDao()

                    // Copy asset database to temp file to read from
                    val tempFile = File(context.cacheDir, "temp_fortress.db")
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

                    // Read metadata
                    val metadataCursor = assetDb.rawQuery("SELECT * FROM metadata WHERE id = 1", null)
                    var metadata: DuaMetadataEntity? = null
                    if (metadataCursor.moveToFirst()) {
                        metadata = DuaMetadataEntity(
                            id = metadataCursor.getInt(metadataCursor.getColumnIndexOrThrow("id")),
                            title = metadataCursor.getString(metadataCursor.getColumnIndexOrThrow("title")),
                            subtitle = metadataCursor.getString(metadataCursor.getColumnIndexOrThrow("subtitle")),
                            publisher = metadataCursor.getString(metadataCursor.getColumnIndexOrThrow("publisher")),
                            sourceIds = metadataCursor.getString(metadataCursor.getColumnIndexOrThrow("source_ids"))
                        )
                    }
                    metadataCursor.close()

                    // Read chapters
                    val chapters = mutableListOf<DuaChapterEntity>()
                    val chaptersCursor = assetDb.rawQuery("SELECT * FROM chapters ORDER BY id ASC", null)
                    while (chaptersCursor.moveToNext()) {
                        chapters.add(DuaChapterEntity(
                            id = chaptersCursor.getInt(chaptersCursor.getColumnIndexOrThrow("id")),
                            title = chaptersCursor.getString(chaptersCursor.getColumnIndexOrThrow("title"))
                        ))
                    }
                    chaptersCursor.close()

                    // Read invocations
                    val invocations = mutableListOf<DuaInvocationEntity>()
                    val invocationsCursor = assetDb.rawQuery("SELECT * FROM invocations ORDER BY id ASC", null)
                    while (invocationsCursor.moveToNext()) {
                        invocations.add(DuaInvocationEntity(
                            id = invocationsCursor.getInt(invocationsCursor.getColumnIndexOrThrow("id")),
                            chapterId = invocationsCursor.getInt(invocationsCursor.getColumnIndexOrThrow("chapter_id")),
                            position = invocationsCursor.getInt(invocationsCursor.getColumnIndexOrThrow("position")),
                            arabic = invocationsCursor.getString(invocationsCursor.getColumnIndexOrThrow("arabic")),
                            transliteration = invocationsCursor.getString(invocationsCursor.getColumnIndexOrThrow("transliteration")),
                            translation = invocationsCursor.getString(invocationsCursor.getColumnIndexOrThrow("translation")),
                            context = invocationsCursor.getString(invocationsCursor.getColumnIndexOrThrow("context")),
                            instruction = invocationsCursor.getString(invocationsCursor.getColumnIndexOrThrow("instruction")),
                            note = invocationsCursor.getString(invocationsCursor.getColumnIndexOrThrow("note")),
                            postContext = invocationsCursor.getString(invocationsCursor.getColumnIndexOrThrow("post_context")),
                            description = invocationsCursor.getString(invocationsCursor.getColumnIndexOrThrow("description")),
                            sourceIds = invocationsCursor.getString(invocationsCursor.getColumnIndexOrThrow("source_ids"))
                        ))
                    }
                    invocationsCursor.close()

                    // Read footnotes
                    val footnotes = mutableListOf<DuaFootnoteEntity>()
                    val footnotesCursor = assetDb.rawQuery("SELECT * FROM footnotes ORDER BY id ASC", null)
                    while (footnotesCursor.moveToNext()) {
                        footnotes.add(DuaFootnoteEntity(
                            id = footnotesCursor.getInt(footnotesCursor.getColumnIndexOrThrow("id")),
                            chapterId = footnotesCursor.getInt(footnotesCursor.getColumnIndexOrThrow("chapter_id")),
                            term = footnotesCursor.getString(footnotesCursor.getColumnIndexOrThrow("term")),
                            definition = footnotesCursor.getString(footnotesCursor.getColumnIndexOrThrow("definition")),
                            note = footnotesCursor.getString(footnotesCursor.getColumnIndexOrThrow("note")),
                            sourceIds = footnotesCursor.getString(footnotesCursor.getColumnIndexOrThrow("source_ids"))
                        ))
                    }
                    footnotesCursor.close()

                    // Read hadith references
                    val hadithRefs = mutableListOf<HadithReferenceEntity>()
                    val hadithCursor = assetDb.rawQuery("SELECT * FROM hadith_references ORDER BY id ASC", null)
                    while (hadithCursor.moveToNext()) {
                        hadithRefs.add(HadithReferenceEntity(
                            id = hadithCursor.getInt(hadithCursor.getColumnIndexOrThrow("id")),
                            invocationId = hadithCursor.getInt(hadithCursor.getColumnIndexOrThrow("invocation_id")),
                            collectionId = if (hadithCursor.isNull(hadithCursor.getColumnIndexOrThrow("collection_id"))) null
                                else hadithCursor.getInt(hadithCursor.getColumnIndexOrThrow("collection_id")),
                            collectionName = hadithCursor.getString(hadithCursor.getColumnIndexOrThrow("collection_name")),
                            hadithNumber = if (hadithCursor.isNull(hadithCursor.getColumnIndexOrThrow("hadith_number"))) null
                                else hadithCursor.getInt(hadithCursor.getColumnIndexOrThrow("hadith_number")),
                            referenceStr = hadithCursor.getString(hadithCursor.getColumnIndexOrThrow("reference_str")),
                            databaseFile = hadithCursor.getString(hadithCursor.getColumnIndexOrThrow("database_file"))
                        ))
                    }
                    hadithCursor.close()

                    assetDb.close()
                    tempFile.delete()

                    android.util.Log.d(TAG, "📚 Read from assets: ${chapters.size} chapters, ${invocations.size} invocations, ${footnotes.size} footnotes, ${hadithRefs.size} hadith refs")

                    // Delete in reverse order of dependencies (children first)
                    dao.deleteAllHadithReferences()
                    dao.deleteAllFootnotes()
                    dao.deleteAllInvocations()
                    dao.deleteAllChapters()
                    dao.deleteAllMetadata()

                    // Insert in order of dependencies (parents first)
                    metadata?.let { dao.insertMetadata(it) }
                    dao.insertChapters(chapters)
                    dao.insertInvocations(invocations)
                    dao.insertFootnotes(footnotes)
                    dao.insertHadithReferences(hadithRefs)

                    // Trigger invalidation so Flows update
                    db.invalidationTracker.refreshVersionsAsync()

                    android.util.Log.d(TAG, "✅ Dua database refreshed successfully")
                    true
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "❌ Failed to refresh dua database", e)
                    false
                }
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
