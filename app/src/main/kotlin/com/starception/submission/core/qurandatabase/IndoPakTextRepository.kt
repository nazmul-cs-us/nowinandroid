package com.starception.submission.core.qurandatabase

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Loads IndoPak-encoded Quran text from the bundled `quran_indopak.db` asset.
 *
 * The Uthmani text in the main Quran DB writes some long vowels as full alif `ا`,
 * which renders with an "extra alif" when shown in IndoPak Mushaf fonts. This
 * repo serves the parallel IndoPak edition (Tanzil / quran.com-compatible),
 * which uses small superscript marks instead — matching the IndoPak Mushaf look.
 */
class IndoPakTextRepository private constructor(context: Context) {

    private val db: SQLiteDatabase by lazy { openOrCopyDb(context.applicationContext) }
    private val cache = HashMap<Long, String>(6300)

    /**
     * Returns the IndoPak text for the given ayah, or null if not found.
     * Cached after first lookup per ayah.
     */
    fun getAyahText(surahNumber: Int, ayahNumber: Int): String? {
        val key = (surahNumber.toLong() shl 32) or ayahNumber.toLong()
        cache[key]?.let { return it }
        return try {
            db.rawQuery(
                "SELECT text FROM indopak WHERE surah = ? AND ayah = ? LIMIT 1",
                arrayOf(surahNumber.toString(), ayahNumber.toString()),
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).also { cache[key] = it } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "IndoPak lookup failed for $surahNumber:$ayahNumber", e)
            null
        }
    }

    /**
     * Bulk lookup for a whole surah, returning a map of ayahNumber -> text.
     */
    fun getSurahTexts(surahNumber: Int): Map<Int, String> {
        val out = HashMap<Int, String>()
        try {
            db.rawQuery(
                "SELECT ayah, text FROM indopak WHERE surah = ? ORDER BY ayah",
                arrayOf(surahNumber.toString()),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val ayah = cursor.getInt(0)
                    val text = cursor.getString(1)
                    out[ayah] = text
                    cache[(surahNumber.toLong() shl 32) or ayah.toLong()] = text
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "IndoPak bulk lookup failed for surah $surahNumber", e)
        }
        return out
    }

    private fun openOrCopyDb(context: Context): SQLiteDatabase {
        val dbFile = File(context.filesDir, DB_NAME)
        if (!dbFile.exists()) {
            context.assets.open("databases/$DB_NAME").use { input ->
                FileOutputStream(dbFile).use { output -> input.copyTo(output) }
            }
            Log.d(TAG, "Copied $DB_NAME to ${dbFile.absolutePath} (${dbFile.length()} bytes)")
        }
        return SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    }

    companion object {
        private const val TAG = "IndoPakTextRepository"
        private const val DB_NAME = "quran_indopak.db"

        @Volatile private var INSTANCE: IndoPakTextRepository? = null

        fun getInstance(context: Context): IndoPakTextRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: IndoPakTextRepository(context).also { INSTANCE = it }
            }
    }
}
