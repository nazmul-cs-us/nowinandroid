/*
 * Copyright 2021 The Android Open Source Project
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

package com.starception.submission.shared.hadith

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

private val iosSharedHadithRepository: SharedHadithRepository by lazy { IosSharedHadithRepository() }

actual fun createSharedHadithRepository(): SharedHadithRepository = iosSharedHadithRepository

private class IosSharedHadithRepository : SharedHadithRepository {
    private val cacheMutex = Mutex()
    private val bukhariRows = mutableMapOf<Int, SharedHadith>()
    private val bukhariRanges = mutableSetOf<Pair<Int, Int>>()
    private val shamayelRows = mutableMapOf<Int, SharedHadith>()
    private val shamayelRanges = mutableSetOf<Pair<Int, Int>>()
    private val collectionRows = mutableMapOf<String, MutableMap<Int, SharedHadith>>()
    private val collectionRanges = mutableSetOf<CollectionRange>()

    override suspend fun getHadith(id: Int): SharedHadith? {
        cacheMutex.withLock { bukhariRows[id] }?.let { return it }
        return getHadiths(id, id).firstOrNull()
    }

    override suspend fun getHadiths(firstId: Int, lastId: Int): List<SharedHadith> {
        require(firstId > 0 && lastId >= firstId)
        val range = firstId to lastId
        cacheMutex.withLock {
            if (range in bukhariRanges) {
                return bukhariRows.values.inRange(firstId, lastId)
            }
        }
        val rows = withContext(Dispatchers.Default) { readHadiths(firstId, lastId) }
        cacheMutex.withLock {
            rows.associateByTo(bukhariRows, SharedHadith::id)
            bukhariRanges += range
        }
        return rows
    }

    override suspend fun getShamayelHadiths(firstId: Int, lastId: Int): List<SharedHadith> {
        require(firstId > 0 && lastId >= firstId)
        val range = firstId to lastId
        cacheMutex.withLock {
            if (range in shamayelRanges) {
                return shamayelRows.values.inRange(firstId, lastId)
            }
        }
        val rows = withContext(Dispatchers.Default) { readShamayelHadiths(firstId, lastId) }
        cacheMutex.withLock {
            rows.associateByTo(shamayelRows, SharedHadith::id)
            shamayelRanges += range
        }
        return rows
    }

    override suspend fun availableCollectionDatabaseFiles(): List<String> =
        com.starception.submission.core.model.data.HadithCollections.all.filter { collection ->
            val key = "databases/hadith/${collection.databaseFile}"
            com.starception.submission.core.assets.AssetPlatform.cachedPath(key) != null ||
                com.starception.submission.core.assets.AssetPlatform.bundledPath(key) != null
        }.map { it.databaseFile }

    override suspend fun searchCollectionHadiths(databaseFile: String, queries: List<String>, limit: Int): List<SharedHadith> =
        withContext(Dispatchers.Default) {
            require(com.starception.submission.core.model.data.HadithCollections.byDatabaseFile(databaseFile) != null)
            val terms = queries.map(String::trim).filter(String::isNotEmpty).take(8)
            if (databaseFile !in availableCollectionDatabaseFiles() || terms.isEmpty()) emptyList()
            else {
                var arabicSql = "COALESCE(text_arabic, '')"
                ((0x064B..0x0652).map(Int::toChar) + listOf('\u0670', '\u0640')).forEach { mark ->
                    arabicSql = "replace($arabicSql, '$mark', '')"
                }
                readCollectionRows(
                    databaseFile, terms.joinToString(" OR ") { "(text_plain LIKE ? OR $arabicSql LIKE ?)" },
                    terms.flatMap { listOf("%$it%", "%$it%") }, limit.coerceIn(1, 48),
                )
            }
        }

    override suspend fun getCollectionHadiths(
        databaseFile: String,
        firstId: Int,
        lastId: Int,
    ): List<SharedHadith> {
        require(firstId > 0 && lastId >= firstId)
        val range = CollectionRange(databaseFile, firstId, lastId)
        cacheMutex.withLock {
            if (range in collectionRanges) {
                return collectionRows[databaseFile].orEmpty().values.inRange(firstId, lastId)
            }
        }
        val rows = withContext(Dispatchers.Default) {
            readCollectionHadiths(databaseFile, firstId, lastId)
        }
        cacheMutex.withLock {
            val cache = collectionRows.getOrPut(databaseFile) { mutableMapOf() }
            rows.associateByTo(cache, SharedHadith::id)
            collectionRanges += range
        }
        return rows
    }

    override suspend fun getCollectionHadithsByIds(
        databaseFile: String,
        ids: List<Int>,
    ): List<SharedHadith> {
        if (ids.isEmpty()) return emptyList()
        val cached = cacheMutex.withLock { collectionRows[databaseFile].orEmpty().toMap() }
        val missingIds = ids.filterNot(cached::containsKey)
        if (missingIds.isNotEmpty()) {
            val loaded = withContext(Dispatchers.Default) {
                readCollectionHadithsByIds(databaseFile, missingIds)
            }
            cacheMutex.withLock {
                loaded.associateByTo(
                    collectionRows.getOrPut(databaseFile) { mutableMapOf() },
                    SharedHadith::id,
                )
            }
        }
        return cacheMutex.withLock {
            val rows = collectionRows[databaseFile].orEmpty()
            ids.mapNotNull(rows::get)
        }
    }

    /**
     * Any registry collection. The shared schema (id, text_arabic, text_plain,
     * elaboration) covers every hadith database; Arabic-only editions ship a
     * null text_plain, which surfaces as an empty English string.
     */
    @OptIn(ExperimentalForeignApi::class)
    private suspend fun readCollectionHadithsByIds(
        databaseFile: String,
        ids: List<Int>,
    ): List<SharedHadith> = readCollectionRows(
        databaseFile = databaseFile,
        whereSql = "id IN (${ids.joinToString(",")})",
    )

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun readCollectionHadiths(
        databaseFile: String,
        firstId: Int,
        lastId: Int,
    ): List<SharedHadith> = readCollectionRows(
        databaseFile = databaseFile,
        whereSql = "id BETWEEN $firstId AND $lastId",
    )

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun readCollectionRows(
        databaseFile: String,
        whereSql: String,
        whereArgs: List<String> = emptyList(),
        limit: Int? = null,
    ): List<SharedHadith> {
        val path = resolveDatabaseAsset(
            bundledPath = null,
            remotePath = "databases/hadith/$databaseFile",
            cacheName = databaseFile,
        )
        return memScoped {
            val database = alloc<CPointerVar<sqlite3>>()
            check(sqlite3_open_v2(path, database.ptr, SQLITE_OPEN_READONLY, null) == SQLITE_OK) {
                "Unable to open $databaseFile: ${database.value.errorMessage()}"
            }
            try {
                val statement = alloc<CPointerVar<sqlite3_stmt>>()
                val sql = """
                SELECT id, text_arabic, COALESCE(text_plain, ''), COALESCE(elaboration, '')
                FROM hadiths
                WHERE $whereSql
                ORDER BY id ASC
                ${limit?.let { "LIMIT $it" }.orEmpty()}
                """.trimIndent()
                check(sqlite3_prepare_v2(database.value, sql, -1, statement.ptr, null) == SQLITE_OK) {
                    "Unable to prepare the $databaseFile query: ${database.value.errorMessage()}"
                }
                try {
                    whereArgs.forEachIndexed { index, argument ->
                        sqlite3_bind_text(statement.value, index + 1, argument, -1, SQLITE_TRANSIENT)
                    }
                    buildList {
                        while (true) {
                            when (sqlite3_step(statement.value)) {
                                SQLITE_ROW -> add(
                                    SharedHadith(
                                        id = sqlite3_column_int(statement.value, 0),
                                        arabic = statement.value.text(1),
                                        english = statement.value.text(2),
                                        explanation = statement.value.text(3),
                                    ),
                                )
                                SQLITE_DONE -> break
                                else -> error("Unable to read $databaseFile: ${database.value.errorMessage()}")
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

    /**
     * Shama'il At-Tirmidhi: the hadiths table carries the Arabic text; the
     * curated hadith_details rows supply the English translation (falling back
     * to the legacy text_plain elaboration column like the Android app).
     */
    @OptIn(ExperimentalForeignApi::class)
    private suspend fun readShamayelHadiths(firstId: Int, lastId: Int): List<SharedHadith> {
        val path = resolveDatabaseAsset(
            bundledPath = null,
            remotePath = "databases/hadith/shamayele_tirmidhi_complete.db",
            cacheName = "shamayele_tirmidhi_complete.db",
        )
        return memScoped {
            val database = alloc<CPointerVar<sqlite3>>()
            check(sqlite3_open_v2(path, database.ptr, SQLITE_OPEN_READONLY, null) == SQLITE_OK) {
                "Unable to open the Shama'il At-Tirmidhi database: ${database.value.errorMessage()}"
            }
            try {
                val statement = alloc<CPointerVar<sqlite3_stmt>>()
                val sql = """
                SELECT h.id, h.text_arabic,
                       COALESCE(NULLIF(TRIM(d.english_text), ''), h.text_plain),
                       COALESCE(NULLIF(TRIM(d.bengali_explanation), ''), h.elaboration)
                FROM hadiths h
                LEFT JOIN hadith_details d ON d.hadith_id = h.id
                WHERE h.id BETWEEN ? AND ?
                ORDER BY h.id ASC
                """.trimIndent()
                check(sqlite3_prepare_v2(database.value, sql, -1, statement.ptr, null) == SQLITE_OK) {
                    "Unable to prepare the Shama'il query: ${database.value.errorMessage()}"
                }
                try {
                    buildList {
                        while (true) {
                            when (sqlite3_step(statement.value)) {
                                SQLITE_ROW -> add(
                                    SharedHadith(
                                        id = sqlite3_column_int(statement.value, 0),
                                        arabic = statement.value.text(1),
                                        english = statement.value.text(2),
                                        explanation = statement.value.text(3),
                                    ),
                                )
                                SQLITE_DONE -> break
                                else -> error("Unable to read Shama'il: ${database.value.errorMessage()}")
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

    override suspend fun searchBukhari(query: String, limit: Int): List<SharedHadith> =
        withContext(Dispatchers.Default) {
            val term = query.trim()
            if (term.isEmpty()) {
                emptyList()
            } else {
                readBukhariSearch(term, limit)
            }
        }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun readBukhariSearch(term: String, limit: Int): List<SharedHadith> {
        val path = resolveDatabaseAsset(
            bundledPath = NSBundle.mainBundle.pathForResource("sahih_bukhari", ofType = "db"),
            remotePath = "databases/hadith/sahih_bukhari.db",
            cacheName = "sahih_bukhari.db",
        )
        return memScoped {
            val database = alloc<CPointerVar<sqlite3>>()
            check(sqlite3_open_v2(path, database.ptr, SQLITE_OPEN_READONLY, null) == SQLITE_OK) {
                "Unable to open Sahih al-Bukhari database: ${database.value.errorMessage()}"
            }
            try {
                val statement = alloc<CPointerVar<sqlite3_stmt>>()
                val sql = """
                SELECT id, text_arabic, text_plain, elaboration
                FROM hadiths
                WHERE text_plain LIKE ? OR elaboration LIKE ?
                ORDER BY id ASC
                LIMIT ?
                """.trimIndent()
                check(sqlite3_prepare_v2(database.value, sql, -1, statement.ptr, null) == SQLITE_OK) {
                    "Unable to prepare the Bukhari search: ${database.value.errorMessage()}"
                }
                try {
                    sqlite3_bind_text(statement.value, 1, "%$term%", -1, SQLITE_TRANSIENT)
                    sqlite3_bind_text(statement.value, 2, "%$term%", -1, SQLITE_TRANSIENT)
                    sqlite3_bind_int(statement.value, 3, limit)
                    buildList {
                        while (true) {
                            when (sqlite3_step(statement.value)) {
                                SQLITE_ROW -> add(
                                    SharedHadith(
                                        id = sqlite3_column_int(statement.value, 0),
                                        arabic = statement.value.text(1),
                                        english = statement.value.text(2),
                                        explanation = statement.value.text(3),
                                    ),
                                )
                                SQLITE_DONE -> break
                                else -> error("Unable to search Bukhari: ${database.value.errorMessage()}")
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
    private suspend fun readHadiths(firstId: Int, lastId: Int): List<SharedHadith> {
        val path = resolveDatabaseAsset(
            bundledPath = NSBundle.mainBundle.pathForResource("sahih_bukhari", ofType = "db"),
            remotePath = "databases/hadith/sahih_bukhari.db",
            cacheName = "sahih_bukhari.db",
        )
        return memScoped {
            val database = alloc<CPointerVar<sqlite3>>()
            check(sqlite3_open_v2(path, database.ptr, SQLITE_OPEN_READONLY, null) == SQLITE_OK) {
                "Unable to open Sahih al-Bukhari database: ${database.value.errorMessage()}"
            }
            try {
                val statement = alloc<CPointerVar<sqlite3_stmt>>()
                val sql = """
                SELECT id, text_arabic, text_plain, elaboration
                FROM hadiths
                WHERE id BETWEEN ? AND ?
                ORDER BY id ASC
                """.trimIndent()
                check(sqlite3_prepare_v2(database.value, sql, -1, statement.ptr, null) == SQLITE_OK) {
                    "Unable to prepare the Bukhari query: ${database.value.errorMessage()}"
                }
                try {
                    buildList {
                        while (true) {
                            when (sqlite3_step(statement.value)) {
                                SQLITE_ROW -> add(
                                    SharedHadith(
                                        id = sqlite3_column_int(statement.value, 0),
                                        arabic = statement.value.text(1),
                                        english = statement.value.text(2),
                                        explanation = statement.value.text(3),
                                    ),
                                )
                                SQLITE_DONE -> break
                                else -> error("Unable to read Bukhari: ${database.value.errorMessage()}")
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

    private data class CollectionRange(
        val databaseFile: String,
        val firstId: Int,
        val lastId: Int,
    )
}

private fun Collection<SharedHadith>.inRange(firstId: Int, lastId: Int): List<SharedHadith> =
    asSequence()
        .filter { it.id in firstId..lastId }
        .sortedBy(SharedHadith::id)
        .toList()

/** Tells SQLite to copy bound text before the Kotlin-managed bytes are freed. */
@OptIn(ExperimentalForeignApi::class)
private val SQLITE_TRANSIENT: CPointer<CFunction<(COpaquePointer?) -> Unit>>? =
    (-1L).toCPointer()

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<sqlite3_stmt>?.text(index: Int): String =
    this?.let { sqlite3_column_text(it, index)?.reinterpret<ByteVar>()?.toKString() }.orEmpty()

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<sqlite3>?.errorMessage(): String =
    this?.let { sqlite3_errmsg(it)?.toKString() } ?: "Unknown SQLite error"
