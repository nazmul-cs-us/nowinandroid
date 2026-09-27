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

actual fun createSharedDuaRepository(): SharedDuaRepository = IosSharedDuaRepository()

private class IosSharedDuaRepository : SharedDuaRepository {
    override suspend fun getQuranicDuas(): List<SharedQuranicDua> =
        withContext(Dispatchers.Default) { readDuas() }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun readDuas(): List<SharedQuranicDua> {
        val databasePath = resolveDatabaseAsset(
            bundledPath = NSBundle.mainBundle.pathForResource("quranic_duas", ofType = "db"),
            remotePath = "databases/quran/quranic_duas.db",
            cacheName = "quranic_duas.db",
        )
        return memScoped {
            val database = alloc<CPointerVar<sqlite3>>()
            val openResult = sqlite3_open_v2(databasePath, database.ptr, SQLITE_OPEN_READONLY, null)
            if (openResult != SQLITE_OK) {
                val message = database.value.errorMessage()
                database.value?.let(::sqlite3_close)
                error("Unable to open Quranic duas database: $message")
            }
            try {
                val statement = alloc<CPointerVar<sqlite3_stmt>>()
                val sql = """
                SELECT id, dua_number, title, surah_reference, arabic,
                       transliteration, translation, explanation
                FROM quranic_duas
                ORDER BY dua_number ASC
                """.trimIndent()
                val prepareResult = sqlite3_prepare_v2(database.value, sql, -1, statement.ptr, null)
                if (prepareResult != SQLITE_OK) {
                    error("Unable to prepare duas query: ${database.value.errorMessage()}")
                }
                try {
                    buildList {
                        while (true) {
                            when (sqlite3_step(statement.value)) {
                                SQLITE_ROW -> add(
                                    SharedQuranicDua(
                                        id = sqlite3_column_int(statement.value, 0),
                                        duaNumber = sqlite3_column_int(statement.value, 1),
                                        title = columnText(statement.value, 2),
                                        surahReference = columnText(statement.value, 3),
                                        arabic = columnText(statement.value, 4),
                                        transliteration = columnText(statement.value, 5),
                                        translation = columnText(statement.value, 6),
                                        explanation = columnText(statement.value, 7),
                                    ),
                                )
                                SQLITE_DONE -> break
                                else -> error("Unable to step through duas")
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

    @OptIn(ExperimentalForeignApi::class)
    private fun columnText(statement: CPointer<sqlite3_stmt>?, index: Int): String =
        sqlite3_column_text(statement, index)?.reinterpret<ByteVar>()?.toKString().orEmpty()
}

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<sqlite3>?.errorMessage(): String =
    this?.let { sqlite3_errmsg(it)?.toKString() } ?: "Unknown SQLite error"
