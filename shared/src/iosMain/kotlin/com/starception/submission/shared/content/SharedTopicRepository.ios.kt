package com.starception.submission.shared.content

import cnames.structs.sqlite3
import cnames.structs.sqlite3_stmt
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
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import sqlite3.SQLITE_DONE
import sqlite3.SQLITE_OK
import sqlite3.SQLITE_OPEN_READONLY
import sqlite3.SQLITE_ROW
import sqlite3.sqlite3_close
import sqlite3.sqlite3_column_int
import sqlite3.sqlite3_column_text
import sqlite3.sqlite3_errmsg
import sqlite3.sqlite3_finalize
import sqlite3.sqlite3_open_v2
import sqlite3.sqlite3_prepare_v2
import sqlite3.sqlite3_step

actual fun createSharedTopicRepository(): SharedTopicRepository = IosSharedTopicRepository()

private class IosSharedTopicRepository : SharedTopicRepository {
    override suspend fun topics(): List<SharedTopic> = withContext(Dispatchers.Default) {
        runCatching { readTopics() }.getOrElse { SharedTopics }
    }

    override suspend fun articles(topicId: Int): List<SharedTopicArticle> =
        withContext(Dispatchers.Default) {
            when (topicId) {
                11 -> readQuranicDuas()
                in fortressChaptersByTopic.keys -> readFortressArticles(topicId)
                else -> emptyList()
            }
        }

    @OptIn(ExperimentalForeignApi::class)
    private fun readTopics(): List<SharedTopic> = query("topics", "db", """
        SELECT id, name, short_description, long_description
        FROM topics
        ORDER BY id ASC
    """.trimIndent()) { statement ->
        SharedTopic(
            id = sqlite3_column_int(statement, 0),
            name = statement.text(1),
            shortDescription = statement.text(2),
            longDescription = statement.text(3),
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun readQuranicDuas(): List<SharedTopicArticle> = query("quranic_duas", "db", """
        SELECT id, dua_number, title, arabic, translation, transliteration,
               surah_reference, explanation
        FROM quranic_duas
        ORDER BY dua_number ASC
    """.trimIndent()) { statement ->
        val duaNumber = sqlite3_column_int(statement, 1)
        val reference = statement.text(6)
        SharedTopicArticle(
            id = sqlite3_column_int(statement, 0),
            topicId = 11,
            title = buildString {
                append("Quranic Dua $duaNumber: ${statement.text(2)}")
                if (reference.isNotBlank()) append(" ($reference)")
            },
            arabic = statement.text(3),
            translation = statement.text(4),
            transliteration = statement.text(5),
            context = statement.text(7),
            reference = reference,
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun readFortressArticles(topicId: Int): List<SharedTopicArticle> {
        val chapters = fortressChaptersByTopic[topicId].orEmpty()
        if (chapters.isEmpty()) return emptyList()
        val chapterIds = chapters.joinToString(",")
        return query("fortress_of_the_muslim_v2", "db", """
            SELECT i.id, c.title, i.position, i.arabic, i.translation,
                   i.transliteration, i.context, i.instruction, i.note,
                   i.post_context,
                   (SELECT h.reference_str FROM hadith_references h
                    WHERE h.invocation_id = i.id LIMIT 1),
                   c.id
            FROM invocations i
            JOIN chapters c ON c.id = i.chapter_id
            WHERE c.id IN ($chapterIds)
            ORDER BY c.id ASC, i.position ASC
        """.trimIndent()) { statement ->
            val position = sqlite3_column_int(statement, 2)
            SharedTopicArticle(
                id = sqlite3_column_int(statement, 0),
                topicId = topicId,
                title = "${statement.text(1)}: Dua $position",
                arabic = statement.text(3),
                translation = statement.text(4),
                transliteration = statement.text(5),
                context = statement.text(6),
                instruction = listOf(statement.text(7), statement.text(8))
                    .filter(String::isNotBlank)
                    .joinToString("\n\n"),
                additionalContext = statement.text(9),
                reference = statement.text(10).ifBlank {
                    "Fortress of the Muslim · Chapter ${sqlite3_column_int(statement, 11)}"
                },
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private inline fun <T> query(
    resource: String,
    type: String,
    sql: String,
    row: (CPointer<sqlite3_stmt>) -> T,
): List<T> = memScoped {
    val databasePath = NSBundle.mainBundle.pathForResource(resource, ofType = type)
        ?: error("Bundled $resource.$type was not found")
    val database = alloc<CPointerVar<sqlite3>>()
    val openResult = sqlite3_open_v2(databasePath, database.ptr, SQLITE_OPEN_READONLY, null)
    if (openResult != SQLITE_OK) {
        val message = database.value.errorMessage()
        database.value?.let(::sqlite3_close)
        error("Unable to open $resource database: $message")
    }

    try {
        val statement = alloc<CPointerVar<sqlite3_stmt>>()
        val prepareResult = sqlite3_prepare_v2(database.value, sql, -1, statement.ptr, null)
        if (prepareResult != SQLITE_OK) {
            error("Unable to prepare $resource query: ${database.value.errorMessage()}")
        }
        try {
            buildList {
                while (true) {
                    when (sqlite3_step(statement.value)) {
                        SQLITE_ROW -> add(row(requireNotNull(statement.value)))
                        SQLITE_DONE -> break
                        else -> error("Unable to read $resource: ${database.value.errorMessage()}")
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

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<sqlite3_stmt>.text(index: Int): String =
    sqlite3_column_text(this, index)?.reinterpret<ByteVar>()?.toKString().orEmpty()

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<sqlite3>?.errorMessage(): String =
    this?.let { sqlite3_errmsg(it)?.toKString() } ?: "Unknown SQLite error"
