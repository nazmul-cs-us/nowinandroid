package com.starception.submission.ui

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import com.starception.submission.core.designsystem.component.DeenlySplashReminder
import com.starception.submission.core.qurandatabase.existingQuranTranslationFile
import org.json.JSONArray
import kotlin.random.Random

/** Complete short ayahs only; do not truncate a longer verse to fit the splash. */
internal fun shortQuranReminderPool(context: Context, language: String): List<DeenlySplashReminder> {
    val file = existingQuranTranslationFile(context, language) ?: return emptyList()
    return runCatching {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT s.number, a.number_in_surah, a.text FROM ayahs a " +
                "JOIN surahs s ON s.id = a.surah_id WHERE s.number BETWEEN 1 AND 114 " +
                "AND a.number_in_surah > 0 AND length(trim(a.text)) BETWEEN 12 AND 180 " +
                "ORDER BY s.number, a.number_in_surah", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val text = cursor.getString(2)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                        if (text.length in 12..100) add(DeenlySplashReminder(text,
                            "Quran ${cursor.getInt(0)}:${cursor.getInt(1)}"))
                    }
                }.distinctBy { reminderTextIdentity(it.text) }
            }
        }
    }.getOrDefault(emptyList())
}

private fun reminderTextIdentity(text: String) = text.lowercase()
    .replace(Regex("[\\p{P}\\s]+"), " ").trim()

/** Persistent per-language shuffle bags survive process restarts and do not run a model. */
internal class SplashReminderShuffleBag(private val preferences: SharedPreferences, private val random: Random = Random.Default) {
    fun next(poolKey: String, pool: List<DeenlySplashReminder>): DeenlySplashReminder = synchronized(lock) {
        val choices = pool.distinctBy { reminderTextIdentity(it.text) }.associateBy { it.reference }
        require(choices.isNotEmpty())
        val key = "remaining_$poolKey"
        var remaining = readList(preferences.getString(key, null)).distinct().filter { it in choices }
        if (remaining.isEmpty()) remaining = choices.keys.shuffled(random)
        val previous = preferences.getString("previous_reference", null)
        val recent = (readList(preferences.getString("recent_references", null)) + listOfNotNull(previous)).takeLast(12)
        val recentSurahs = recent.takeLast(3).map { it.substringBefore(':') }.toSet()
        val reference = remaining.firstOrNull { it !in recent && it.substringBefore(':') !in recentSurahs }
            ?: remaining.firstOrNull { it != previous && it.substringBefore(':') != previous?.substringBefore(':') }
            ?: remaining.firstOrNull { it != previous } ?: remaining.first()
        preferences.edit()
            .putString(key, JSONArray(remaining.filterNot { it == reference }).toString())
            .putString("recent_references", JSONArray((recent + reference).takeLast(12)).toString())
            .putString("previous_reference", reference)
            .commit()
        choices.getValue(reference)
    }

    private fun readList(value: String?): List<String> = runCatching {
        val array = JSONArray(value ?: "[]")
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())

    private companion object { val lock = Any() }
}
