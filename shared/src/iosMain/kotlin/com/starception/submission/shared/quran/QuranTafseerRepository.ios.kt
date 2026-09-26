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

package com.starception.submission.shared.quran

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
import sqlite3.SQLITE_OK
import sqlite3.SQLITE_OPEN_READONLY
import sqlite3.SQLITE_ROW
import sqlite3.sqlite3_bind_int
import sqlite3.sqlite3_close
import sqlite3.sqlite3_column_text
import sqlite3.sqlite3_errmsg
import sqlite3.sqlite3_finalize
import sqlite3.sqlite3_open_v2
import sqlite3.sqlite3_prepare_v2
import sqlite3.sqlite3_step

actual fun createQuranTafseerRepository(): QuranTafseerRepository = IosQuranTafseerRepository()

/**
 * Reads the enhanced Quran database (tafseer books + word meanings) with the
 * same raw-sqlite approach as [IosQuranVerseRepository]. The 30 MB database
 * is not bundled; it resolves from the CDN on first use.
 */
private class IosQuranTafseerRepository : QuranTafseerRepository {
    override suspend fun getTafseer(surahNumber: Int, ayahNumber: Int): AyahTafseer? =
        withContext(Dispatchers.Default) { readTafseer(surahNumber, ayahNumber) }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun readTafseer(surahNumber: Int, ayahNumber: Int): AyahTafseer? {
        val databasePath = resolveDatabaseAsset(
            bundledPath = null,
            remotePath = "databases/quran/quran_enhanced.db",
            cacheName = "quran_enhanced.db",
        )
        return memScoped {
            val database = alloc<CPointerVar<sqlite3>>()
            val openResult = sqlite3_open_v2(databasePath, database.ptr, SQLITE_OPEN_READONLY, null)
            if (openResult != SQLITE_OK) {
                val message = sqlite3_errmsg(database.value)?.toKString() ?: "unknown"
                sqlite3_close(database.value)
                error("Unable to open the enhanced Quran database: $message")
            }
            try {
                val statement = alloc<CPointerVar<sqlite3_stmt>>()
                // Column layout matches Android's QuranEnhancedDao.getTafseerForAyah.
                val sql = """
                SELECT aya_text, tafseer_saadi, tafseer_moyassar, tafseer_bughiu, maany_aya
                FROM quran
                WHERE sora = ? AND aya_no = ?
                """.trimIndent()
                val prepareResult = sqlite3_prepare_v2(database.value, sql, -1, statement.ptr, null)
                if (prepareResult != SQLITE_OK) {
                    error("Unable to prepare the tafseer query: ${sqlite3_errmsg(database.value)?.toKString()}")
                }
                try {
                    check(sqlite3_bind_int(statement.value, 1, surahNumber) == SQLITE_OK)
                    check(sqlite3_bind_int(statement.value, 2, ayahNumber) == SQLITE_OK)
                    when (sqlite3_step(statement.value)) {
                        SQLITE_ROW -> AyahTafseer(
                            surahNumber = surahNumber,
                            ayahNumber = ayahNumber,
                            ayahText = columnText(statement.value!!, 0),
                            tafseerSaadi = columnText(statement.value!!, 1),
                            tafseerMoysar = columnText(statement.value!!, 2),
                            tafseerBaghawi = columnText(statement.value!!, 3),
                            ayahMeanings = columnText(statement.value!!, 4),
                        )
                        else -> null
                    }
                } finally {
                    statement.value?.let(::sqlite3_finalize)
                }
            } finally {
                database.value?.let(::sqlite3_close)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun columnText(
    statement: CPointer<sqlite3_stmt>,
    index: Int,
): String =
    sqlite3_column_text(statement, index)
        ?.reinterpret<ByteVar>()
        ?.toKString()
        .orEmpty()
