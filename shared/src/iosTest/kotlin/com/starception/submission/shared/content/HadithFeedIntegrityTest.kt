@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.starception.submission.shared.content

import cnames.structs.sqlite3
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import sqlite3.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HadithFeedIntegrityTest {
    @Test
    fun legacyCachedFeedCannotLeakThroughSearchBookmarksOrTopicPages() = runBlocking {
        val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".db"
        try {
            memScoped {
                val database = alloc<CPointerVar<sqlite3>>()
                check(sqlite3_open(path, database.ptr) == SQLITE_OK)
                try {
                    val sql = """
                        CREATE TABLE news_resources (id INTEGER, title TEXT, content TEXT, url TEXT,
                            header_image_url TEXT, publish_date TEXT, type TEXT, source TEXT,
                            is_system INTEGER, is_user_created INTEGER);
                        CREATE TABLE news_topics (news_id INTEGER, topic_id INTEGER);
                        INSERT INTO news_resources VALUES (3001, 'Bukhari 615', 'Old journey quotation',
                            'hadith://sahih_bukhari/615', '', '', 'Hadith', '', 1, 0);
                        INSERT INTO news_resources VALUES (1000615, 'Local record 615', 'Verified local journey quotation',
                            'hadith-local://sahih_bukhari/615', '', '', 'Hadith', '', 1, 0);
                        INSERT INTO news_topics VALUES (3001, 8), (1000615, 8);
                    """.trimIndent()
                    check(sqlite3_exec(database.value, sql, null, null, null) == SQLITE_OK)
                } finally {
                    sqlite3_close(database.value)
                }
            }
            val repository = IosSharedNewsRepository(path)
            assertNull(repository.newsById(3001))
            assertEquals(listOf(1000615), repository.newsByIds(setOf(3001, 1000615)).map { it.id })
            assertEquals(listOf(1000615), repository.newsForTopics(setOf(8), 20, 0).map { it.id })
            assertEquals(listOf(1000615), repository.searchNews("journey", 20).map { it.id })
            assertEquals(listOf(1000615), repository.searchNewsByType("journey", "Hadith", 20).map { it.id })
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(path, null)
        }
    }
}
