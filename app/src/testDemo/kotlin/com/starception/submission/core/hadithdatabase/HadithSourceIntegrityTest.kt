package com.starception.submission.core.hadithdatabase

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.starception.submission.core.duadatabase.HadithReference
import com.starception.submission.core.duadatabase.HadithReferenceEntity
import com.starception.submission.core.duadatabase.toHadithReference
import com.starception.submission.core.contentdatabase.NewsDatabase
import com.starception.submission.core.contentdatabase.NewsDbGenerator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HadithSourceIntegrityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val narration = "Narrated Malik bin Huwairith:\n\nTwo men came to the Prophet (ﷺ) with the intention of a journey."

    @After
    fun cleanUp() {
        HadithDatabase.closeAll()
        context.deleteDatabase("hadith_sahih_bukhari.db")
        context.filesDir.resolve("cdn_assets/json/sahih_bukhari.json").delete()
        context.filesDir.resolve("cdn_assets/databases/hadith/sahih_bukhari.db").delete()
        NewsDatabase.closeDatabase()
        context.deleteDatabase("news.db")
    }

    private fun installCollection() {
        val file = context.getDatabasePath("hadith_sahih_bukhari.db")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE hadiths (id INTEGER NOT NULL PRIMARY KEY, text_arabic TEXT NOT NULL, text_plain TEXT, elaboration TEXT)")
            db.execSQL("INSERT INTO hadiths VALUES (615, 'Arabic of the same record', ?, NULL)", arrayOf(narration))
            db.execSQL("INSERT INTO hadiths VALUES (630, 'Different Arabic', 'A different local narration', NULL)")
            db.version = 2
        }
    }

    @Test
    fun readerAndPlaybackUseTheSameRowNotAnAmbiguousLegacyNumber() = runBlocking {
        installCollection()
        val legacy = context.filesDir.resolve("cdn_assets/json/sahih_bukhari.json")
        legacy.parentFile!!.mkdirs()
        legacy.writeText("""[{"name":"Volume 1","books":[{"name":"1. Wrong book","hadiths":[{"info":"Volume 1, Book 1, Number 615","by":"Different narrator","text":"Wrong narration"}]}]}]""")
        val reader = HadithRepository(context).getHadith("sahih_bukhari.db", 615)!!
        val playback = BukhariLocalTranslationRepository.getInstance(context).getEnglishText(615)
        assertEquals(narration, reader.textPlain)
        assertEquals(reader.textPlain, playback)
        assertNull(BukhariLocalTranslationRepository.getInstance(context).getEnglishText(616))
    }

    @Test
    fun externalPublisher630CannotOpenLocalRow630() = runBlocking {
        installCollection()
        val reference = HadithReference(1, 1, 1, "Bukhari", 630, "Bukhari 630", "sahih_bukhari.db")
        assertNull(HadithRepository(context).getHadithFromReference(reference))
        val converted = HadithReferenceEntity(1, 1, 1, "Bukhari", 630, "Bukhari 630", "sahih_bukhari.db")
            .toHadithReference()
        assertEquals("Bukhari #630", converted.displayName)
        assertNull(converted.databaseFile)
    }

    @Test
    fun runtimeFeedAndReaderUseTheSameRecord() = runBlocking {
        installCollection()
        val database = NewsDatabase.getInstance(context)
        val result = NewsDbGenerator.regenerateWithRoom(context, database.newsDao())
        assertTrue(result.error, result.success)
        val feed = database.newsDao().getNewsResourceById(1_000_615)!!
        assertEquals(narration, feed.content)
        assertEquals("Sahih al-Bukhari · Local record 615", feed.title)
        assertEquals("hadith-local://sahih_bukhari/615", feed.url)
    }

    @Test
    fun oldFeedIsQuarantinedBeforeItCanShowOrNavigate() = runBlocking {
        val database = NewsDatabase.getInstance(context)
        database.openHelper.writableDatabase.execSQL("""
            INSERT INTO news_resources (id, title, content, url, is_system, is_user_created)
            VALUES (3001, 'Bukhari 615', 'Wrong legacy quote', 'hadith://sahih_bukhari/615', 1, 0)
        """.trimIndent())
        NewsDatabase.closeDatabase()
        val restored = NewsDatabase.getInstance(context).newsDao().getNewsResourceById(3001)!!
        assertNull(restored.content)
        assertNull(restored.url)
        assertTrue(restored.title.contains("requires verification"))
    }

    @Test
    fun regenerationPreservesUserContentEvenWhenGeneratedIdCollides() = runBlocking {
        installCollection()
        val database = NewsDatabase.getInstance(context)
        database.openHelper.writableDatabase.execSQL("""
            INSERT INTO news_resources (id, title, content, url, is_system, is_user_created)
            VALUES (1000615, 'My note', 'My saved personal content', NULL, 0, 1)
        """.trimIndent())
        database.openHelper.writableDatabase.execSQL("INSERT INTO news_topics VALUES (1000615, 77)")
        assertTrue(NewsDbGenerator.regenerateWithRoom(context, database.newsDao()).success)
        val personal = database.newsDao().getNewsResourceById(1000615)!!
        assertEquals("My note", personal.title)
        assertEquals("My saved personal content", personal.content)
        assertEquals("77", personal.topicIds)
    }

    @Test
    fun homeTranslationDoesNotMixInstalledAndNewlyDownloadedEditions() {
        installCollection()
        val download = context.filesDir.resolve("cdn_assets/databases/hadith/sahih_bukhari.db")
        download.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(download, null).use {
            it.execSQL("CREATE TABLE hadiths (id INTEGER PRIMARY KEY, text_arabic TEXT)")
            it.execSQL("INSERT INTO hadiths VALUES (615, 'Unrelated text from a replacement edition')")
        }
        val target = com.starception.submission.feature.prayertimes.NowNudgeKnowledgeTarget.Hadith(
            "Sahih al-Bukhari", "sahih_bukhari.db", 615)
        assertEquals("Arabic of the same record",
            com.starception.submission.feature.prayertimes.readHomePassageTranslation(context, target, "ar"))
    }
}
