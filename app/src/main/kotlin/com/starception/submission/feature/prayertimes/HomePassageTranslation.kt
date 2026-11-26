package com.starception.submission.feature.prayertimes

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import androidx.compose.runtime.*
import com.starception.submission.core.qurandatabase.readLocalQuranPassage
import java.io.File

@Composable
internal fun rememberHomeTranslationLanguage(context: Context): String {
    val preferences = remember(context) { context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE) }
    var language by remember(preferences) { mutableStateOf(preferences.getString("quran_translation", "ar") ?: "ar") }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "quran_translation") language = prefs.getString(key, "ar") ?: "ar"
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return language
}

/** Localize the displayed source by its canonical target, not by translating model output. */
internal fun readHomePassageTranslation(context: Context, target: NowNudgeKnowledgeTarget, language: String): String? =
    when (target) {
        is NowNudgeKnowledgeTarget.QuranAyah -> readLocalQuranPassage(context, language, target.surahNumber, target.ayahNumber)
        is NowNudgeKnowledgeTarget.FortressDua -> when (language) {
            "ar" -> target.dua.arabic
            "bn" -> target.dua.translation?.takeIf { it.hasBengaliSourceText() }
            else -> null
        }
        is NowNudgeKnowledgeTarget.Hadith -> localHadithPassage(context, target, language)
    }?.trim()?.takeIf(String::isNotBlank)

private fun localHadithPassage(context: Context, target: NowNudgeKnowledgeTarget.Hadith, language: String): String? {
    if (!target.databaseFile.matches(Regex("[a-z0-9_]+\\.db")) || language !in setOf("ar", "bn")) return null
    val file = listOf(File(context.filesDir, "cdn_assets/databases/hadith/${target.databaseFile}"),
        context.getDatabasePath("hadith_${target.databaseFile}"))
        .firstOrNull { it.isFile && it.length() > 1024 } ?: return null
    return runCatching {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            fun query(sql: String): String? = runCatching {
                database.rawQuery(sql, arrayOf(target.hadithNumber.toString())).use {
                    if (it.moveToFirst()) it.getString(0)?.takeIf(String::isNotBlank) else null
                }
            }.getOrNull()
            when (language) {
                "ar" -> query("SELECT text_arabic FROM hadiths WHERE id = ? LIMIT 1")
                else -> query("SELECT bengali_text FROM hadith_details WHERE hadith_id = ? LIMIT 1")
                    ?.takeIf { text -> text.any { it in '\u0980'..'\u09ff' } }
                    ?: query("SELECT text_plain FROM hadiths WHERE id = ? LIMIT 1")?.takeIf { it.hasBengaliSourceText() }
            }
        }
    }.getOrNull()
}

private fun String.hasBengaliSourceText() = any { it in '\u0980'..'\u09ff' } && none { it in 'A'..'Z' || it in 'a'..'z' }

internal fun homeTranslationReference(target: NowNudgeKnowledgeTarget, reference: String, language: String): String {
    if (language != "bn" || target !is NowNudgeKnowledgeTarget.QuranAyah) return reference
    return "কুরআন ${target.surahNumber}:${target.ayahNumber}".map { char ->
        if (char in '0'..'9') ('\u09e6'.code + (char - '0')).toChar() else char
    }.joinToString("")
}
