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

package com.starception.submission.feature.prayertimes

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.starception.submission.core.hadithdatabase.HadithDatabase
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrayerHadithRecommendationRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseFile = "sahih_bukhari.db"

    @After
    fun cleanUp() {
        HadithDatabase.closeAll()
        context.deleteDatabase("hadith_$databaseFile")
    }

    @Test
    fun fridaySearchUsesInstalledCollectionAfterDownloadCacheIsRemoved() = runBlocking {
        val narration = "A stored Friday prayer narration for the recommendation fixture."
        installCollection(narration)
        assertTrue(HadithDatabase.isDatabaseAvailable(context, databaseFile))
        val result = PrayerHadithRecommendationRepository.load(context, "Dhuhr", LocalDate.of(2026, 10, 23))
        assertNotNull(result)
        assertEquals(narration, result!!.source.text)
        assertEquals(databaseFile, result.source.databaseFile)
    }

    @Test
    fun longFridayNarrationIsPreservedInsteadOfDiscardedAsMissing() = runBlocking {
        val narration = "Friday prayer source text. ".repeat(160)
        installCollection(narration)
        val result = PrayerHadithRecommendationRepository.load(context, "Dhuhr", LocalDate.of(2026, 10, 23))
        assertNotNull(result)
        assertEquals(narration, result!!.source.text)
    }

    @Test
    fun progressReportsTheInstalledCollectionAndCompletedRead() = runBlocking {
        installCollection("Friday prayer narration with enough source text for selection.")
        val updates = mutableListOf<com.starception.submission.core.model.deenly.InsightSearchProgress>()
        PrayerHadithRecommendationRepository.load(context, "Dhuhr", LocalDate.of(2026, 10, 23)) {
            updates += it
        }
        val reads = updates.filter { it.determinate }
        assertTrue(reads.isNotEmpty())
        assertEquals(0, reads.first().completed)
        assertEquals(1, reads.last().completed)
        assertEquals(1, reads.last().total)
        assertTrue(reads.first().detail.contains("Bukhari", ignoreCase = true))
        assertTrue(reads.last().sources.single().contains("Bukhari", ignoreCase = true))
    }

    private fun installCollection(text: String) {
        HadithDatabase.closeAll()
        val file = context.getDatabasePath("hadith_$databaseFile")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE hadiths (id INTEGER NOT NULL PRIMARY KEY, text_arabic TEXT NOT NULL, text_plain TEXT, elaboration TEXT)")
            db.execSQL("INSERT INTO hadiths VALUES (1, '', ?, NULL)", arrayOf(text))
            db.version = 2
        }
    }
}
