package com.starception.submission.ui

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.starception.submission.core.designsystem.component.DeenlySplashReminder
import com.starception.submission.core.designsystem.component.nextDeenlySplashReminder
import com.starception.submission.core.qurandatabase.QuranTranslationHelper
import java.io.File

/** Read only existing content: no Room creation/copy, model inference, or network on startup. */
internal fun translatedSplashReminder(context: Context, code: String, previousReference: String?): DeenlySplashReminder? {
    if (!code.matches(Regex("[a-z]{2,3}")) || code == "en") return null
    val source = listOf(
        File(context.filesDir, "cdn_assets/${QuranTranslationHelper.cdnKeyFor(code)}"),
        context.getDatabasePath("quran_${code}_instance"),
    ).firstOrNull { it.isFile && it.length() > 1024 } ?: return null
    return runCatching {
        SQLiteDatabase.openDatabase(source.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            var candidate = nextDeenlySplashReminder(previousReference, false, false)
            val first = candidate.reference
            do {
                if (candidate.reference != previousReference) {
                    val parts = candidate.reference.removePrefix("Quran ").split(':')
                    database.rawQuery(
                        "SELECT a.text FROM ayahs a JOIN surahs s ON s.id = a.surah_id " +
                            "WHERE s.number = ? AND a.number_in_surah = ? LIMIT 1",
                        arrayOf(parts[0], parts[1]),
                    ).use { cursor ->
                        if (cursor.moveToFirst()) {
                            val text = cursor.getString(0)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                            // Select another short verse rather than clipping a long sacred passage.
                            if (text.isNotBlank() && text.length <= 100) {
                                return DeenlySplashReminder(text, candidate.reference)
                            }
                        }
                    }
                }
                candidate = nextDeenlySplashReminder(candidate.reference, false, false)
            } while (candidate.reference != first)
        }
        null
    }.getOrNull()
}
