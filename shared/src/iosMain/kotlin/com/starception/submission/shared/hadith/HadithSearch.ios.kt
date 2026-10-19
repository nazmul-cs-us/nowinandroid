/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

private val ENGLISH_HADITH_COLLECTIONS = listOf(
    "sahih_bukhari" to "Sahih al-Bukhari",
    "sahih_muslim" to "Sahih Muslim",
    "muwatta_malik" to "Muwatta Malik",
    // Bilingual since the tohed rebuild; Musnad Ahmad stays out because its
    // corpus rows are Arabic-only, so English search cannot rank them.
    "sunan_darimi" to "Sunan ad-Darimi",
    "sunan_abu_dawud" to "Sunan Abu Dawud",
    "sunan_nasai" to "Sunan an-Nasa'i",
    "sunan_ibn_majah" to "Sunan Ibn Majah",
    "sunan_tirmidhi" to "Jami' at-Tirmidhi",
)

actual suspend fun searchEnglishHadithCollections(
    tokens: List<String>,
    limit: Int,
): List<HadithSearchHit> {
    if (tokens.isEmpty()) return emptyList()
    // Android's search recipe: each token expands into a slot with its
    // corpus-synonym alternatives, plans run strictest first, and the strict
    // three-token plan merges the two most-specific pair plans so preference
    // narrations that lack one question term ("Rasulullah loved halwa and
    // honey" never says "food") still reach answer ranking.
    val slots = tokens.map(::hadithSearchSlotVariants)
    val plans = buildHadithSearchPlans(slots)
    for ((planIndex, plan) in plans.withIndex()) {
        val results = searchAllCollections(plan, limit)
        if (results.isNotEmpty()) {
            val isStrictThreeTokenPlan = planIndex == 0 && plan.size == 3 && slots.size == 3
            if (!isStrictThreeTokenPlan) return results
            val strictIds = results.map(::hitKey).toSet()
            val pairA = runCatching {
                searchAllCollections(listOf(slots[0], slots[1]), limit)
            }.getOrDefault(emptyList())
            val pairB = runCatching {
                searchAllCollections(listOf(slots[1], slots[2]), limit)
            }.getOrDefault(emptyList())
            val pairOnly = (pairA + pairB)
                .distinctBy(::hitKey)
                .filter { hitKey(it) !in strictIds }
            return (results + pairOnly).distinctBy(::hitKey)
        }
    }
    return emptyList()
}

private fun hitKey(hit: HadithSearchHit): String = "${hit.databaseFile}:${hit.hadithId}"

private suspend fun searchAllCollections(
    slots: List<List<String>>,
    limit: Int,
): List<HadithSearchHit> {
    val results = mutableListOf<HadithSearchHit>()
    for ((databaseFile, collectionName) in ENGLISH_HADITH_COLLECTIONS) {
        val hits = try {
            searchCollection(databaseFile, collectionName, slots, limit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A collection that is not downloaded (or fails to open) simply
            // contributes no candidates; the turn continues with the rest.
            emptyList()
        }
        results += hits
    }
    return results
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun searchCollection(
    databaseFile: String,
    collectionName: String,
    slots: List<List<String>>,
    limit: Int,
): List<HadithSearchHit> = withContext(Dispatchers.Default) {
    val path = resolveDatabaseAsset(
        bundledPath = NSBundle.mainBundle.pathForResource(databaseFile, ofType = "db"),
        remotePath = "databases/hadith/$databaseFile.db",
        cacheName = "$databaseFile.db",
    )
    // Each slot matches ANY of its variants (token + corpus synonyms); every
    // slot must match — Android's searchHadithsMultiTokenAny.
    val slotConditions = slots.joinToString(" AND ") { slot ->
        "(" + slot.joinToString(" OR ") { "text_plain LIKE ?" } + ")"
    }
    val sql = """
        SELECT id, text_plain FROM hadiths
        WHERE TRIM(text_plain) <> '' AND $slotConditions
        ORDER BY LENGTH(text_plain) ASC
        LIMIT ?
    """.trimIndent()
    memScoped {
        val database = alloc<CPointerVar<sqlite3>>()
        val openResult = sqlite3_open_v2(path, database.ptr, SQLITE_OPEN_READONLY, null)
        if (openResult != SQLITE_OK) {
            database.value?.let(::sqlite3_close)
            error("Unable to open $databaseFile: ${database.value.errorMessage()}")
        }
        try {
            val statement = alloc<CPointerVar<sqlite3_stmt>>()
            val prepareResult = sqlite3_prepare_v2(database.value, sql, -1, statement.ptr, null)
            if (prepareResult != SQLITE_OK) {
                error("Unable to prepare $databaseFile search: ${database.value.errorMessage()}")
            }
            try {
                var bindIndex = 1
                slots.forEach { slot ->
                    slot.forEach { variant ->
                        sqlite3_bind_text(
                            statement.value,
                            bindIndex,
                            "%$variant%",
                            -1,
                            SQLITE_TRANSIENT,
                        )
                        bindIndex += 1
                    }
                }
                sqlite3_bind_int(statement.value, bindIndex, limit)
                buildList {
                    while (true) {
                        when (sqlite3_step(statement.value)) {
                            SQLITE_ROW -> {
                                val id = sqlite3_column_int(statement.value, 0)
                                val text = statement.value.columnText(1)
                                if (text.isNotBlank()) {
                                    add(
                                        HadithSearchHit(
                                            databaseFile = databaseFile,
                                            hadithId = id,
                                            collectionName = collectionName,
                                            text = text,
                                        ),
                                    )
                                }
                            }
                            SQLITE_DONE -> break
                            else -> error("Unable to step through $databaseFile rows")
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
private fun CPointer<sqlite3>?.errorMessage(): String =
    this?.let { sqlite3_errmsg(it)?.toKString() } ?: "Unknown SQLite error"

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<sqlite3_stmt>?.columnText(index: Int): String =
    sqlite3_column_text(this, index)?.reinterpret<ByteVar>()?.toKString().orEmpty()

/** Tells SQLite to copy bound text before the Kotlin-managed bytes are freed. */
@OptIn(ExperimentalForeignApi::class)
private val SQLITE_TRANSIENT: CPointer<CFunction<(COpaquePointer?) -> Unit>>? =
    (-1L).toCPointer()
