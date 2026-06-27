package com.starception.submission.core.sync

import android.content.Context
import android.util.Log
import com.starception.submission.core.duadatabase.DuaDatabase
import com.starception.submission.core.contentdatabase.NewsDatabase
import com.starception.submission.core.contentdatabase.NewsResourceEntity
import com.starception.submission.core.contentdatabase.NewsTopicCrossRef
import com.starception.submission.core.quranicduas.QuranicDuaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper class to synchronize duas databases with news database
 *
 * This ensures that when new duas are added to fortress_of_the_muslim.db or quranic_duas.db,
 * they will appear in the app after refreshing databases.
 */
object DatabaseSyncHelper {
    private const val TAG = "DatabaseSyncHelper"

    // Starting ID for Fortress of the Muslim duas in news (to avoid conflicts)
    private const val FORTRESS_DUA_START_ID = 1000
    // Starting ID for Quranic Duas in news
    private const val QURANIC_DUA_START_ID = 100

    // Topic IDs for associating duas
    private const val TOPIC_QURANIC_DUAS = 11  // "Quranic Duas" topic

    /**
     * Sync all duas from duas databases to news database
     * @return Pair of (fortressCount, quranicCount) synced
     */
    suspend fun syncDuasToNews(context: Context): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting duas to news sync...")

                val newsDb = NewsDatabase.getInstance(context)
                val duaDb = DuaDatabase.getInstance(context)
                val quranicDb = QuranicDuaDatabase.getInstance(context)

                val newsDao = newsDb.newsDao()
                val duaDao = duaDb.duaDao()
                val quranicDao = quranicDb.quranicDuaDao()

                var fortressSynced = 0
                var quranicSynced = 0

                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                val now = dateFormat.format(Date())

                // Sync Quranic Duas (40 duas)
                val quranicDuas = quranicDao.getAllQuranicDuas()
                Log.d(TAG, "Found ${quranicDuas.size} Quranic Duas to sync")

                for (dua in quranicDuas) {
                    val newsId = QURANIC_DUA_START_ID + dua.duaNumber

                    // Build content with Arabic, transliteration, translation, and explanation
                    val content = buildString {
                        dua.arabic?.let { append("$it\n\n") }
                        dua.transliteration?.let { append("Transliteration: $it\n\n") }
                        dua.translation?.let { append("Translation: $it\n\n") }
                        dua.explanation?.let { append(it) }
                    }

                    val newsEntity = NewsResourceEntity(
                        id = newsId,
                        title = "Quranic Dua ${dua.duaNumber}: ${dua.title} (${dua.surahReference ?: ""})",
                        content = content.trim(),
                        url = "",
                        headerImageUrl = "https://via.placeholder.com/600x300/4CAF50/FFFFFF?text=Quranic+Dua+${dua.duaNumber}",
                        publishDate = now,
                        type = "Dua \uD83E\uDD32",  // Dua 🤲
                        isSystem = 1,
                        isUserCreated = 0,
                        source = "quranic_duas_db",
                        createdAt = now,
                        updatedAt = now
                    )

                    newsDao.insertNewsResource(newsEntity)

                    // Associate with Quranic Duas topic
                    newsDao.insertNewsTopicCrossRef(NewsTopicCrossRef(newsId, TOPIC_QURANIC_DUAS))

                    quranicSynced++
                }

                // Sync Fortress of the Muslim Duas. Clear the previously-synced fortress
                // items first so stale rows from the old DB (different ids/titles) don't
                // linger after switching to the v2 database.
                newsDao.deleteNewsTopicsBySource("fortress_db")
                newsDao.deleteNewsResourcesBySource("fortress_db")

                val chapters = duaDao.getAllChapters()
                Log.d(TAG, "Found ${chapters.size} Fortress chapters to sync")

                for (chapter in chapters) {
                    val invocations = duaDao.getInvocationsByChapter(chapter.id)

                    for (invocation in invocations) {
                        val newsId = FORTRESS_DUA_START_ID + invocation.id

                        // v2 stores references in hadith_references (not `note`), so pull it.
                        val reference = duaDao.getHadithReferencesForInvocation(invocation.id)
                            .firstOrNull()?.referenceStr

                        // Build content
                        val content = buildString {
                            invocation.context?.let { append("$it\n\n") }
                            invocation.arabic?.let { append("$it\n\n") }
                            invocation.transliteration?.let { append("Transliteration: $it\n\n") }
                            invocation.translation?.let { append("Translation: $it\n\n") }
                            invocation.instruction?.let { append("Instruction: $it\n\n") }
                            invocation.note?.let { append("Note: $it\n\n") }
                            reference?.let { append("Reference: $it\n\n") }
                            invocation.postContext?.let { append(it) }
                        }

                        // Determine topic based on chapter title
                        val topicId = getTopicIdForChapter(chapter.title)

                        val newsEntity = NewsResourceEntity(
                            id = newsId,
                            title = "${chapter.title}: Dua ${invocation.position}",
                            content = content.trim(),
                            url = "",
                            headerImageUrl = "https://via.placeholder.com/600x300/9C27B0/FFFFFF?text=${chapter.title.take(20).replace(" ", "+")}",
                            publishDate = now,
                            type = "Dua \uD83E\uDD32",  // Dua 🤲
                            isSystem = 1,
                            isUserCreated = 0,
                            source = "fortress_db",
                            createdAt = now,
                            updatedAt = now
                        )

                        newsDao.insertNewsResource(newsEntity)

                        // Associate with appropriate topic
                        if (topicId != null) {
                            newsDao.insertNewsTopicCrossRef(NewsTopicCrossRef(newsId, topicId))
                        }

                        fortressSynced++
                    }
                }

                Log.d(TAG, "Sync completed: $quranicSynced Quranic, $fortressSynced Fortress duas")

                SyncResult(
                    success = true,
                    quranicDuasSynced = quranicSynced,
                    fortressDuasSynced = fortressSynced,
                    message = "Synced $quranicSynced Quranic + $fortressSynced Fortress duas"
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error syncing duas to news", e)
                SyncResult(
                    success = false,
                    quranicDuasSynced = 0,
                    fortressDuasSynced = 0,
                    message = "Sync failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Map chapter titles to topic IDs
     */
    private fun getTopicIdForChapter(chapterTitle: String): Int? {
        val title = chapterTitle.lowercase()
        return when {
            title.contains("morning") || title.contains("evening") || title.contains("waking") || title.contains("sleeping") -> 21  // Morning & Evening
            title.contains("prayer") || title.contains("salah") || title.contains("mosque") || title.contains("athan") -> 22  // Prayer
            title.contains("home") || title.contains("entering") || title.contains("leaving") || title.contains("toilet") -> 23  // Home & Daily
            title.contains("food") || title.contains("eating") || title.contains("drink") || title.contains("fasting") -> 24  // Food & Drink
            title.contains("travel") || title.contains("journey") -> 25  // Travel
            title.contains("protect") || title.contains("refuge") || title.contains("evil") || title.contains("fear") -> 26  // Protection
            title.contains("distress") || title.contains("anxiety") || title.contains("worry") || title.contains("debt") -> 27  // Distress & Anxiety
            title.contains("sick") || title.contains("health") || title.contains("pain") || title.contains("visit") -> 28  // Health & Sickness
            title.contains("guest") || title.contains("greeting") || title.contains("gift") || title.contains("thank") -> 29  // Social & Etiquette
            title.contains("death") || title.contains("funeral") || title.contains("grave") || title.contains("deceased") -> 30  // Death & Funeral
            title.contains("rain") || title.contains("wind") || title.contains("thunder") || title.contains("moon") -> 31  // Weather & Nature
            title.contains("hajj") || title.contains("umrah") || title.contains("tawaf") || title.contains("safa") -> 32  // Hajj & Umrah
            title.contains("forgive") || title.contains("repent") || title.contains("istighfar") -> 33  // Forgiveness & Repentance
            else -> null
        }
    }
}

/**
 * Result of database sync operation
 */
data class SyncResult(
    val success: Boolean,
    val quranicDuasSynced: Int,
    val fortressDuasSynced: Int,
    val message: String
)
