/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.shared.content

import cnames.structs.sqlite3
import cnames.structs.sqlite3_stmt
import com.starception.submission.shared.database.resolveDatabaseAsset
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import sqlite3.SQLITE_DONE
import sqlite3.SQLITE_OK
import sqlite3.SQLITE_OPEN_READONLY
import sqlite3.SQLITE_ROW
import sqlite3.sqlite3_bind_int
import sqlite3.sqlite3_bind_text
import sqlite3.sqlite3_close
import sqlite3.sqlite3_column_int
import sqlite3.sqlite3_column_text
import sqlite3.sqlite3_errmsg
import sqlite3.sqlite3_finalize
import sqlite3.sqlite3_open_v2
import sqlite3.sqlite3_prepare_v2
import sqlite3.sqlite3_step

actual fun createSharedFortressRepository(): SharedFortressRepository = IosSharedFortressRepository()

@OptIn(ExperimentalForeignApi::class)
private class IosSharedFortressRepository : SharedFortressRepository {
    override suspend fun getChapters(): List<FortressChapter> = withContext(Dispatchers.Default) {
        readRows(sql = "SELECT id, title FROM chapters ORDER BY id ASC") { statement ->
            FortressChapter(
                id = sqlite3_column_int(statement, 0),
                title = columnText(statement, 1),
            )
        }
    }

    override suspend fun getChapterReferences(chapterId: Int): Map<Int, String> =
        withContext(Dispatchers.Default) {
            readRows(
                sql = """
                SELECT invocation_id, reference_str FROM hadith_references
                WHERE invocation_id IN (SELECT id FROM invocations WHERE chapter_id = ?)
                """.trimIndent(),
                bind = { statement -> sqlite3_bind_int(statement, 1, chapterId) },
            ) { statement ->
                sqlite3_column_int(statement, 0) to columnText(statement, 1)
            }.toMap()
        }

    override suspend fun searchFortressInvocations(query: String, limit: Int): List<FortressInvocation> =
        withContext(Dispatchers.Default) {
            val term = query.trim()
            if (term.isEmpty()) {
                emptyList()
            } else {
                readRows(
                    sql = """
                    SELECT id, chapter_id, position, arabic, transliteration, translation,
                           context, instruction, note, post_context, description, audio_url
                    FROM invocations
                    WHERE translation LIKE ? OR description LIKE ?
                    ORDER BY chapter_id ASC, position ASC
                    LIMIT ?
                    """.trimIndent(),
                    bind = { statement ->
                        sqlite3_bind_text(statement, 1, "%$term%", -1, SQLITE_TRANSIENT)
                        sqlite3_bind_text(statement, 2, "%$term%", -1, SQLITE_TRANSIENT)
                        sqlite3_bind_int(statement, 3, limit)
                    },
                ) { statement ->
                    FortressInvocation(
                        id = sqlite3_column_int(statement, 0),
                        chapterId = sqlite3_column_int(statement, 1),
                        position = sqlite3_column_int(statement, 2),
                        arabic = columnText(statement, 3),
                        transliteration = columnText(statement, 4),
                        translation = columnText(statement, 5),
                        context = columnText(statement, 6),
                        instruction = columnText(statement, 7),
                        note = columnText(statement, 8),
                        postContext = columnText(statement, 9),
                        description = columnText(statement, 10),
                        audioUrl = columnText(statement, 11),
                    )
                }
            }
        }

    override suspend fun getChapterInvocations(chapterId: Int): List<FortressInvocation> =
        withContext(Dispatchers.Default) {
            readRows(
                sql = """
                SELECT id, chapter_id, position, arabic, transliteration, translation,
                       context, instruction, note, post_context, description, audio_url
                FROM invocations
                WHERE chapter_id = ?
                ORDER BY position ASC
                """.trimIndent(),
                bind = { statement -> sqlite3_bind_int(statement, 1, chapterId) },
            ) { statement ->
                FortressInvocation(
                    id = sqlite3_column_int(statement, 0),
                    chapterId = sqlite3_column_int(statement, 1),
                    position = sqlite3_column_int(statement, 2),
                    arabic = columnText(statement, 3),
                    transliteration = columnText(statement, 4),
                    translation = columnText(statement, 5),
                    context = columnText(statement, 6),
                    instruction = columnText(statement, 7),
                    note = columnText(statement, 8),
                    postContext = columnText(statement, 9),
                    description = columnText(statement, 10),
                    audioUrl = columnText(statement, 11),
                )
            }
        }

    private suspend fun <T> readRows(
        sql: String,
        bind: (CPointer<sqlite3_stmt>?) -> Unit = { },
        map: (CPointer<sqlite3_stmt>?) -> T,
    ): List<T> {
        val databasePath = resolveDatabaseAsset(
            bundledPath = NSBundle.mainBundle.pathForResource(
                "fortress_of_the_muslim_v2",
                ofType = "db",
            ),
            remotePath = "databases/quran/fortress_of_the_muslim_v2.db",
            cacheName = "fortress_of_the_muslim_v2.db",
        )
        return memScoped {
            val database = alloc<CPointerVar<sqlite3>>()
            val openResult = sqlite3_open_v2(databasePath, database.ptr, SQLITE_OPEN_READONLY, null)
            if (openResult != SQLITE_OK) {
                val message = database.value.errorMessage()
                database.value?.let(::sqlite3_close)
                error("Unable to open Fortress database: $message")
            }
            try {
                val statement = alloc<CPointerVar<sqlite3_stmt>>()
                val prepareResult = sqlite3_prepare_v2(database.value, sql, -1, statement.ptr, null)
                if (prepareResult != SQLITE_OK) {
                    error("Unable to prepare Fortress query: ${database.value.errorMessage()}")
                }
                try {
                    bind(statement.value)
                    buildList {
                        while (true) {
                            when (sqlite3_step(statement.value)) {
                                SQLITE_ROW -> add(map(statement.value))
                                SQLITE_DONE -> break
                                else -> error("Unable to step through Fortress rows")
                            }
                        }
                    }
                } finally {
                    sqlite3_finalize(statement.value)
                }
            } finally {
                sqlite3_close(database.value)
            }
        }
    }

    private fun columnText(statement: CPointer<sqlite3_stmt>?, index: Int): String =
        sqlite3_column_text(statement, index)?.reinterpret<ByteVar>()?.toKString().orEmpty()
}

/** Tells SQLite to copy bound text before the Kotlin-managed bytes are freed. */
@OptIn(ExperimentalForeignApi::class)
private val SQLITE_TRANSIENT: CPointer<CFunction<(COpaquePointer?) -> Unit>>? =
    (-1L).toCPointer()

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<sqlite3>?.errorMessage(): String =
    this?.let { sqlite3_errmsg(it)?.toKString() } ?: "Unknown SQLite error"
