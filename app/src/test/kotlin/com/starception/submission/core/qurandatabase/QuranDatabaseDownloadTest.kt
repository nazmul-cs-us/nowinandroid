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

package com.starception.submission.core.qurandatabase

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.starception.submission.download.AssetDownloadManager
import com.starception.submission.download.AssetRepository
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuranDatabaseDownloadTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = AssetRepository(context, AssetDownloadManager(context, OkHttpClient()))

    @After
    fun tearDown() {
        QuranDatabase.clearInstance(context)
        QuranEnhancedDatabase.closeDatabase()
        context.deleteDatabase("quran_enhanced.db")
        context.deleteDatabase("download_source.db")
        File(context.filesDir, "cdn_assets").deleteRecursively()
    }

    @Test
    fun standardDatabaseUsesSourceDownloadedAfterSingletonConstruction() {
        assertFalse(downloadFile("quran.db").exists())
        val database = QuranDatabase.getInstance(context, repository)

        downloadSource(QuranDatabase::class.java, "quran.db")

        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM surahs").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    @Test
    fun enhancedDatabaseUsesSourceDownloadedAfterSingletonConstruction() {
        assertFalse(downloadFile("quran_enhanced.db").exists())
        val database = QuranEnhancedDatabase.getInstance(context, repository)

        downloadSource(QuranEnhancedDatabase::class.java, "quran_enhanced.db")

        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM quran").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    private fun downloadFile(name: String) = File(context.filesDir, "cdn_assets/databases/quran/$name")

    private fun <T : RoomDatabase> downloadSource(type: Class<T>, name: String) {
        // Build a valid prepackaged database without bundling a large fixture or using the network.
        val source = Room.databaseBuilder(context, type, "download_source.db")
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        source.openHelper.writableDatabase
        source.close()
        val target = downloadFile(name)
        target.parentFile!!.mkdirs()
        context.getDatabasePath("download_source.db").copyTo(target)
    }
}
