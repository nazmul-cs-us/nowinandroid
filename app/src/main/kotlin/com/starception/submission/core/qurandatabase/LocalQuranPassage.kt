package com.starception.submission.core.qurandatabase

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

internal fun existingQuranTranslationFile(context: Context, code: String): File? {
    if (!code.matches(Regex("[a-z]{2,3}"))) return null
    return listOf(File(context.filesDir, "cdn_assets/${QuranTranslationHelper.cdnKeyFor(code)}"),
        context.getDatabasePath("quran_${code}_instance"))
        .firstOrNull { it.isFile && it.length() > 1024 }
}

/** Exact source-authored text from an existing file. Never create a DB or translate scripture. */
internal fun readLocalQuranPassage(context: Context, code: String, surah: Int, ayah: Int): String? {
    if (surah !in 1..114 || ayah <= 0) return null
    val file = existingQuranTranslationFile(context, code) ?: return null
    return runCatching {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT a.text FROM ayahs a JOIN surahs s ON s.id = a.surah_id " +
                "WHERE s.number = ? AND a.number_in_surah = ? LIMIT 1", arrayOf(surah.toString(), ayah.toString())).use {
                if (it.moveToFirst()) it.getString(0)?.trim()?.takeIf(String::isNotBlank) else null
            }
        }
    }.getOrNull()
}
