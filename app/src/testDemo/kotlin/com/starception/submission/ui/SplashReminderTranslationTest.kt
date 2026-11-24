package com.starception.submission.ui

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.starception.submission.feature.prayertimes.NowNudgeKnowledgeTarget
import com.starception.submission.feature.prayertimes.readHomePassageTranslation
import com.starception.submission.feature.prayertimes.homeTranslationReference

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SplashReminderTranslationTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun context(): Context {
        val directory = temporary.newFolder()
        return object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir() = directory
            override fun getApplicationContext(): Context = this
        }
    }

    private fun addBengaliFixture(context: Context) {
        val file = File(context.filesDir, "cdn_assets/databases/quran/quran_bn.db")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE surahs (id INTEGER, number INTEGER)")
            database.execSQL("CREATE TABLE ayahs (surah_id INTEGER, number_in_surah INTEGER, text TEXT)")
            database.execSQL("INSERT INTO surahs VALUES (1,94),(2,112)")
            database.execSQL("INSERT INTO ayahs VALUES (1,5,?),(2,1,?)",
                arrayOf("বাংলা অনুবাদ এক", "বাংলা অনুবাদ দুই"))
        }
    }

    @Test fun usesExactDownloadedTranslationAndSkipsThePreviousVerse() {
        val context = context()
        addBengaliFixture(context)
        val first = translatedSplashReminder(context, "bn", null)!!
        assertEquals("বাংলা অনুবাদ এক", first.text)
        assertEquals("Quran 94:5", first.reference)
        val second = translatedSplashReminder(context, "bn", first.reference)!!
        assertEquals("বাংলা অনুবাদ দুই", second.text)
        assertEquals("Quran 112:1", second.reference)
    }

    @Test fun missingTranslationReturnsNoInventedOrMachineTranslatedText() {
        assertNull(translatedSplashReminder(context(), "bn", null))
        assertNull(translatedSplashReminder(context(), "../bn", null))
    }

    @Test fun homePassageReadsTheSameCanonicalVerseInBengaliWithoutUsingTheEnglishCache() {
        val context = context()
        addBengaliFixture(context)
        val target = NowNudgeKnowledgeTarget.QuranAyah(94, 5)
        assertEquals("বাংলা অনুবাদ এক", readHomePassageTranslation(context, target, "bn"))
        assertNull(readHomePassageTranslation(context, target, "fr"))
        assertEquals("কুরআন ৯৪:৫", homeTranslationReference(target, "Quran 94:5", "bn"))
    }

    @Test fun startupUsesSavedTranslationAndPersistsPreviousReference() {
        val context = context()
        addBengaliFixture(context)
        context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE).edit()
            .putString("quran_translation", "bn").commit()
        context.getSharedPreferences("splash_reminders", Context.MODE_PRIVATE).edit().clear().commit()
        val first = loadNextSplashReminder(context)
        val second = loadNextSplashReminder(context)
        assertEquals("বাংলা অনুবাদ এক", first.text)
        assertEquals("বাংলা অনুবাদ দুই", second.text)
        assertNotEquals(first.reference, second.reference)
    }
}
