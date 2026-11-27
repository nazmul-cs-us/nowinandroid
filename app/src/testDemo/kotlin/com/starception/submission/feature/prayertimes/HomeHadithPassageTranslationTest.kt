package com.starception.submission.feature.prayertimes

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.starception.submission.core.hadithdatabase.HadithDatabase
import com.starception.submission.core.translation.TranslationService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class HomeHadithPassageTranslationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val target = NowNudgeKnowledgeTarget.Hadith("Sahih al-Bukhari", "sahih_bukhari.db", 615)
    private val english = "Narrated Malik: Two men came with the intention of a journey."
    private val bengali = "সফরের উদ্দেশ্যে দুজন ব্যক্তি এসেছিলেন।"

    @Before
    fun setUp() {
        resetTranslationService()
        TranslationService.getInstance(context).clearCache()
    }

    @After
    fun cleanUp() {
        HadithDatabase.closeAll()
        context.deleteDatabase("hadith_${target.databaseFile}")
        TranslationService.getInstance(context).clearCache()
        resetTranslationService()
    }

    @Test
    fun homeReusesReadersBengaliTranslationOfTheExactEnglishRow() = runBlocking {
        installCollection(english)
        cacheReaderTranslation(english, bengali)

        val reading = loadHomePassageTranslation(context, target, "bn")!!

        assertEquals(bengali, reading.text)
        assertTrue(reading.machineTranslated)
        assertTrue(reading.notice.contains("May contain errors"))
    }

    @Test
    fun storedBengaliIsPreferredOverMachineTranslation() = runBlocking {
        installCollection(bengali)
        cacheReaderTranslation(bengali, "Incorrect cached translation")

        val reading = loadHomePassageTranslation(context, target, "bn")!!

        assertEquals(bengali, reading.text)
        assertFalse(reading.machineTranslated)
    }

    @Test
    fun unchangedEnglishResponseIsNotPresentedAsBengali() = runBlocking {
        installCollection(english)
        cacheReaderTranslation(english, english)

        assertNull(loadHomePassageTranslation(context, target, "bn"))
    }

    @Test
    fun anotherRecordCannotSupplyAMissingRecordsTranslation() = runBlocking {
        installCollection(english)
        cacheReaderTranslation(english, bengali)

        assertNull(loadHomePassageTranslation(context, target.copy(hadithNumber = 630), "bn"))
    }

    @Test
    fun englishAndTransliterationDoNotRequestAMachineTranslation() = runBlocking {
        installCollection(english)

        assertNull(loadHomePassageTranslation(context, target, "en"))
        assertNull(loadHomePassageTranslation(context, target, "transliteration"))
    }

    private fun cacheReaderTranslation(source: String, translation: String) {
        // Seed the real persistent cache written by translateFromEnglish in the reader.
        context.getSharedPreferences("translation_cache", Context.MODE_PRIVATE).edit()
            .putString("${"en_$source".hashCode()}_bn", translation).commit()
    }

    private fun resetTranslationService() {
        // Robolectric recreates Application/preferences between tests, but this
        // process singleton otherwise retains a previous test's preferences.
        TranslationService::class.java.getDeclaredField("instance").apply {
            isAccessible = true
            set(null, null)
        }
    }

    private fun installCollection(text: String) {
        HadithDatabase.closeAll()
        val file = context.getDatabasePath("hadith_${target.databaseFile}")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE hadiths (id INTEGER NOT NULL PRIMARY KEY, text_arabic TEXT NOT NULL, text_plain TEXT, elaboration TEXT)")
            database.execSQL("INSERT INTO hadiths VALUES (615, 'نص عربي', ?, NULL)", arrayOf(text))
            database.version = 2
        }
    }
}
