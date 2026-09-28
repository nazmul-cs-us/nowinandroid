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
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import sqlite3.SQLITE_OK
import sqlite3.SQLITE_OPEN_READONLY
import sqlite3.SQLITE_ROW
import sqlite3.sqlite3_close
import sqlite3.sqlite3_column_int
import sqlite3.sqlite3_finalize
import sqlite3.sqlite3_open_v2
import sqlite3.sqlite3_prepare_v2
import sqlite3.sqlite3_step

private data class DbDescriptor(
    val key: String,
    val label: String,
    val itemLabel: String,
    val countTable: String,
    val countColumn: String,
    val bundledName: String? = null,
    val remotePath: String? = null,
    val cacheName: String? = null,
)

private val DESCRIPTORS = listOf(
    DbDescriptor(
        key = "news",
        label = "News Database",
        itemLabel = "articles",
        countTable = "news_resources",
        countColumn = "id",
        bundledName = "news",
    ),
    DbDescriptor(
        key = "topics",
        label = "Topics Database",
        itemLabel = "topics",
        countTable = "topics",
        countColumn = "id",
        bundledName = "topics",
    ),
    DbDescriptor(
        key = "fortress",
        label = "Fortress of the Muslim",
        itemLabel = "invocations",
        countTable = "invocations",
        countColumn = "id",
        remotePath = "databases/quran/fortress_of_the_muslim_v2.db",
        cacheName = "fortress_of_the_muslim_v2.db",
    ),
    DbDescriptor(
        key = "quranic_duas",
        label = "Quranic Duas",
        itemLabel = "duas",
        countTable = "quranic_duas",
        countColumn = "id",
        remotePath = "databases/quran/quranic_duas.db",
        cacheName = "quranic_duas.db",
    ),
)

@OptIn(ExperimentalForeignApi::class)
actual class SharedDatabaseStats actual constructor() {
    actual suspend fun snapshot(): List<SharedDatabaseInfo> = withContext(Dispatchers.Default) {
        DESCRIPTORS.mapNotNull { descriptor -> infoFor(descriptor) }
    }

    actual fun refresh(key: String): Boolean {
        val descriptor = DESCRIPTORS.firstOrNull { it.key == key } ?: return false
        if (descriptor.bundledName != null) return false
        val path = cachedPathFor(descriptor) ?: return false
        platform.Foundation.NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        return true
    }

    private suspend fun infoFor(descriptor: DbDescriptor): SharedDatabaseInfo? {
        val path = resolvePath(descriptor) ?: return null
        val count = countRows(path, descriptor) ?: 0
        val size = fileSize(path)
        return SharedDatabaseInfo(
            key = descriptor.key,
            label = descriptor.label,
            itemCount = count,
            itemLabel = descriptor.itemLabel,
            sizeBytes = size,
            bundled = descriptor.bundledName != null,
        )
    }

    private suspend fun resolvePath(descriptor: DbDescriptor): String? {
        if (descriptor.bundledName != null) {
            return NSBundle.mainBundle.pathForResource(descriptor.bundledName, ofType = "db")
        }
        val remote = descriptor.remotePath ?: return null
        val cache = descriptor.cacheName ?: return null
        return runCatching {
            resolveDatabaseAsset(
                bundledPath = null,
                remotePath = remote,
                cacheName = cache,
            )
        }.getOrNull()
    }

    private fun cachedPathFor(descriptor: DbDescriptor): String? {
        val documents = (
            platform.Foundation.NSSearchPathForDirectoriesInDomains(
                9uL,
                platform.Foundation.NSUserDomainMask,
                true,
            ).firstOrNull() as? String
            ) ?: return null
        val entry = descriptor.remotePath?.substringAfterLast('/') ?: return null
        return "$documents/../Application Support/StarceptionAssets/$entry"
            .replace("Documents/../", "")
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun countRows(path: String, descriptor: DbDescriptor): Int? = memScoped {
        val database = alloc<CPointerVar<sqlite3>>()
        val openResult = sqlite3_open_v2(path, database.ptr, SQLITE_OPEN_READONLY, null)
        if (openResult != SQLITE_OK) {
            database.value?.let(::sqlite3_close)
            return@memScoped null
        }
        try {
            val statement = alloc<CPointerVar<sqlite3_stmt>>()
            val sql = "SELECT COUNT(${descriptor.countColumn}) FROM ${descriptor.countTable}"
            val prepareResult = sqlite3_prepare_v2(database.value, sql, -1, statement.ptr, null)
            if (prepareResult != SQLITE_OK) return@memScoped null
            try {
                when (sqlite3_step(statement.value)) {
                    SQLITE_ROW -> sqlite3_column_int(statement.value, 0)
                    else -> null
                }
            } finally {
                sqlite3_finalize(statement.value)
            }
        } finally {
            sqlite3_close(database.value)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun fileSize(path: String): Long {
        val attributes = platform.Foundation.NSFileManager.defaultManager
            .attributesOfItemAtPath(path, error = null) ?: return 0L
        return (attributes.get("NSFileSize") as? platform.Foundation.NSNumber)?.longValue ?: 0L
    }
}
