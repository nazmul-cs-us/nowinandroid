/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.starception.submission.shared.hadith

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
import sqlite3.sqlite3_bind_int
import sqlite3.sqlite3_close
import sqlite3.sqlite3_column_int
import sqlite3.sqlite3_column_text
import sqlite3.sqlite3_errmsg
import sqlite3.sqlite3_finalize
import sqlite3.sqlite3_open_v2
import sqlite3.sqlite3_prepare_v2
import sqlite3.sqlite3_step

actual fun createSharedHadithRepository(): SharedHadithRepository = IosSharedHadithRepository()

private class IosSharedHadithRepository : SharedHadithRepository {
    override suspend fun getHadith(id: Int): SharedHadith? =
        getHadiths(id, id).firstOrNull()

    override suspend fun getHadiths(firstId: Int, lastId: Int): List<SharedHadith> =
        withContext(Dispatchers.Default) {
            require(firstId > 0 && lastId >= firstId)
            readHadiths(firstId, lastId)
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
                check(sqlite3_bind_int(statement.value, 1, firstId) == SQLITE_OK)
                check(sqlite3_bind_int(statement.value, 2, lastId) == SQLITE_OK)
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
}

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<sqlite3_stmt>?.text(index: Int): String =
    this?.let { sqlite3_column_text(it, index)?.reinterpret<ByteVar>()?.toKString() }.orEmpty()

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<sqlite3>?.errorMessage(): String =
    this?.let { sqlite3_errmsg(it)?.toKString() } ?: "Unknown SQLite error"
