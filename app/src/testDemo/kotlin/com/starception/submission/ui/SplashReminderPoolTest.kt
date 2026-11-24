package com.starception.submission.ui

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.starception.submission.core.designsystem.component.DeenlySplashReminder
import java.io.File
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SplashReminderPoolTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun preferences() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("shuffle_pool_test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }

    @Test fun fullPoolIsUsedWithoutRepeatsAcrossFreshBagInstancesAndProcessStyleRestarts() {
        val prefs = preferences()
        val pool = (1..40).map { DeenlySplashReminder("Complete source fixture $it", "Quran 2:$it") }
        val selected = (0..39).map { seed -> SplashReminderShuffleBag(prefs, Random(seed)).next("bn", pool) }
        assertEquals(40, selected.map { it.reference }.toSet().size)
        assertNotEquals(pool.map { it.reference }, selected.map { it.reference })
        val nextCycle = SplashReminderShuffleBag(prefs, Random(50)).next("bn", pool)
        assertNotEquals(selected.last().reference, nextCycle.reference)
    }

    @Test fun repeatedVerseTextIsDeduplicatedEvenUnderDifferentReferences() {
        val prefs = preferences()
        val pool = listOf(DeenlySplashReminder("Repeated complete fixture.", "Quran 2:1"),
            DeenlySplashReminder("Repeated complete fixture!", "Quran 3:1"),
            DeenlySplashReminder("Another complete fixture.", "Quran 4:1"))
        val bag = SplashReminderShuffleBag(prefs, Random(8))
        val first = bag.next("bn", pool)
        val second = bag.next("bn", pool)
        assertNotEquals(first.text, second.text)
        assertEquals("[]", prefs.getString("remaining_bn", null))
    }

    @Test fun selectionVariesSurahsWhenOtherChaptersAreAvailable() {
        val prefs = preferences()
        val pool = (2..5).flatMap { surah -> (1..5).map { ayah ->
            DeenlySplashReminder("Complete fixture for surah $surah verse $ayah", "Quran $surah:$ayah")
        } }
        val bag = SplashReminderShuffleBag(prefs, Random(3))
        var previous: String? = null
        repeat(12) {
            val current = bag.next("bn", pool).reference.substringBefore(':')
            assertNotEquals(previous, current)
            previous = current
        }
    }

    @Test fun readsCompleteShortAyahsBeyondTheOldListWithoutTruncatingLongVerses() {
        val directory = temporary.newFolder()
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = directory
        }
        val file = File(directory, "cdn_assets/databases/quran/quran_bn.db")
        file.parentFile!!.mkdirs()
        val short = "পরীক্ষার জন্য সম্পূর্ণ ছোট বাংলা লেখা।"
        SQLiteDatabase.openOrCreateDatabase(file, null).use {
            it.execSQL("CREATE TABLE surahs (id INTEGER, number INTEGER)")
            it.execSQL("CREATE TABLE ayahs (surah_id INTEGER, number_in_surah INTEGER, text TEXT)")
            it.execSQL("INSERT INTO surahs VALUES (1,100),(2,94)")
            it.execSQL("INSERT INTO ayahs VALUES (1,1,?),(2,5,?),(1,2,?)",
                arrayOf(short, short.repeat(20), short))
        }
        val pool = shortQuranReminderPool(context, "bn")
        assertEquals(1, pool.size)
        assertEquals(short, pool.single().text)
        assertEquals("Quran 100:1", pool.single().reference)
        assertTrue(shortQuranReminderPool(context, "../bn").isEmpty())
    }
}
