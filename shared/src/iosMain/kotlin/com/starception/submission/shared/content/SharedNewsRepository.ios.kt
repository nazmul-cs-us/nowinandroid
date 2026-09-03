package com.starception.submission.shared.content

import cnames.structs.sqlite3
import cnames.structs.sqlite3_stmt
import com.starception.submission.shared.database.resolveDatabaseAsset
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import sqlite3.SQLITE_DONE
import sqlite3.SQLITE_OK
import sqlite3.SQLITE_OPEN_READONLY
import sqlite3.SQLITE_ROW
import sqlite3.sqlite3_bind_int
import sqlite3.sqlite3_close
import sqlite3.sqlite3_column_int
import sqlite3.sqlite3_column_text
import sqlite3.sqlite3_errmsg
import sqlite3.sqlite3_finalize
import sqlite3.sqlite3_open_v2
import sqlite3.sqlite3_prepare_v2
import sqlite3.sqlite3_step

actual fun createSharedNewsRepository(): SharedNewsRepository = IosSharedNewsRepository()

private class IosSharedNewsRepository : SharedNewsRepository {
    private val databasePathMutex = Mutex()
    private var cachedDatabasePath: String? = null

    override suspend fun newsForTopics(
        topicIds: Set<Int>,
        limit: Int,
        offset: Int,
    ): List<SharedNewsResource> = withContext(Dispatchers.Default) {
        if (topicIds.isEmpty() || limit <= 0) return@withContext emptyList()
        val orderedIds = topicIds.sorted()
        queryNews(
            path = databasePath(),
            sql = """
                $NEWS_SELECT
                FROM news_resources n
                WHERE EXISTS (
                    SELECT 1 FROM news_topics filtered
                    WHERE filtered.news_id = n.id
                      AND filtered.topic_id IN (${placeholders(orderedIds.size)})
                )
                ORDER BY n.id ASC
                LIMIT ? OFFSET ?
            """.trimIndent(),
            arguments = orderedIds + listOf(limit, offset),
        )
    }

    override suspend fun newsByIds(ids: Set<Int>): List<SharedNewsResource> =
        withContext(Dispatchers.Default) {
            if (ids.isEmpty()) return@withContext emptyList()
            val orderedIds = ids.sorted()
            queryNews(
                path = databasePath(),
                sql = """
                    $NEWS_SELECT
                    FROM news_resources n
                    WHERE n.id IN (${placeholders(orderedIds.size)})
                    ORDER BY n.id ASC
                """.trimIndent(),
                arguments = orderedIds,
            )
        }

    override suspend fun newsForTopic(
        topicId: Int,
        limit: Int,
        offset: Int,
    ): List<SharedNewsResource> = newsForTopics(setOf(topicId), limit, offset)

    override suspend fun newsById(id: Int): SharedNewsResource? =
        withContext(Dispatchers.Default) {
            queryNews(
                path = databasePath(),
                sql = """
                    $NEWS_SELECT
                    FROM news_resources n
                    WHERE n.id = ?
                    LIMIT 1
                """.trimIndent(),
                arguments = listOf(id),
            ).firstOrNull()
        }

    private suspend fun databasePath(): String {
        databasePathMutex.lock()
        return try {
            cachedDatabasePath ?: resolveDatabaseAsset(
                bundledPath = NSBundle.mainBundle.pathForResource("news", ofType = "db"),
                remotePath = "databases/news.db",
                cacheName = "news.db",
            ).also { cachedDatabasePath = it }
        } finally {
            databasePathMutex.unlock()
        }
    }
}

private const val NEWS_SELECT = """
    SELECT n.id, n.title, n.content, n.url, n.header_image_url,
           n.publish_date, n.type, n.source,
           (SELECT GROUP_CONCAT(topic_id)
            FROM news_topics all_topics
            WHERE all_topics.news_id = n.id) AS topic_ids
"""

private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(",")

@OptIn(ExperimentalForeignApi::class)
private suspend fun queryNews(
    path: String,
    sql: String,
    arguments: List<Int>,
): List<SharedNewsResource> {
    val coroutineContext = currentCoroutineContext()
    return memScoped {
        val database = alloc<CPointerVar<sqlite3>>()
        val openResult = sqlite3_open_v2(path, database.ptr, SQLITE_OPEN_READONLY, null)
        if (openResult != SQLITE_OK) {
            val message = database.value.errorMessage()
            database.value?.let(::sqlite3_close)
            error("Unable to open news database: $message")
        }

        try {
            val statement = alloc<CPointerVar<sqlite3_stmt>>()
            check(sqlite3_prepare_v2(database.value, sql, -1, statement.ptr, null) == SQLITE_OK) {
                "Unable to prepare news query: ${database.value.errorMessage()}"
            }
            try {
                arguments.forEachIndexed { index, value ->
                    check(sqlite3_bind_int(statement.value, index + 1, value) == SQLITE_OK) {
                        "Unable to bind news query argument ${index + 1}"
                    }
                }
                buildList {
                    while (true) {
                        coroutineContext.ensureActive()
                        when (sqlite3_step(statement.value)) {
                            SQLITE_ROW -> add(requireNotNull(statement.value).newsResource())
                            SQLITE_DONE -> break
                            else -> error(
                                "Unable to read news database: ${database.value.errorMessage()}",
                            )
                        }
                    }
                }
            } finally {
                statement.value?.let(::sqlite3_finalize)
            }
        } finally {
            database.value?.let(::sqlite3_close)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<sqlite3_stmt>.newsResource(): SharedNewsResource = SharedNewsResource(
    id = sqlite3_column_int(this, 0),
    title = text(1),
    content = text(2),
    url = text(3),
    headerImageUrl = text(4),
    publishDate = text(5),
    type = text(6),
    source = text(7),
    topicIds = text(8).split(',').mapNotNullTo(mutableSetOf(), String::toIntOrNull),
)

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<sqlite3_stmt>.text(index: Int): String =
    sqlite3_column_text(this, index)?.reinterpret<ByteVar>()?.toKString().orEmpty()

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<sqlite3>?.errorMessage(): String =
    this?.let { sqlite3_errmsg(it)?.toKString() } ?: "Unknown SQLite error"
