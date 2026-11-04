package com.starception.submission.feature.quran

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ReciterSelectionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("quran_reciter", Context.MODE_PRIVATE)
    private val quranPreferences = context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)
    private val yasser = ReciterCatalog.Reciter("1-2", "Yasser Al-Dosari", "https://example.org/", setOf(1, 78), "Hafs - Murattal")

    @Before fun reset() {
        preferences.edit().clear().commit()
        quranPreferences.edit().clear().commit()
    }

    @Test fun anOpenReaderReceivesSelectionAndResetFromAnotherPicker() = runTest {
        val reader = ReciterSelectionStore(context)
        val settings = ReciterSelectionStore(context)
        val observed = mutableListOf<ReciterSelection>()
        val observer = launch(UnconfinedTestDispatcher(testScheduler)) {
            reader.selections.collect { observed += it }
        }
        assertEquals(ReciterSelection(), observed.last())
        settings.select(yasser)
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        assertEquals("Yasser Al-Dosari", observed.last().name)
        assertEquals("Hafs - Murattal", observed.last().riwayah)
        settings.select(null)
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        assertEquals(ReciterSelection(), observed.last())
        observer.cancel()
    }

    @Test fun legacyCombinedLabelsAreReadAsSeparateNameAndRiwayah() {
        preferences.edit().putString("selected_id", yasser.id)
            .putString("selected_name", "Yasser Al-Dosari · Rewayat Hafs - Murattal").commit()
        val selected = ReciterSelectionStore(context).current()
        assertEquals("Yasser Al-Dosari", selected.name)
        assertEquals("Rewayat Hafs - Murattal", selected.riwayah)
    }

    @Test fun selectionPreservesDownloadSourceAndResetRemovesIt() {
        val store = ReciterSelectionStore(context)
        store.select(yasser)
        assertEquals(yasser.name, preferences.getString("selected_name", null))
        assertEquals(yasser.server, preferences.getString("selected_server", null))
        assertEquals("1,78", preferences.getString("selected_surahs", null))
        store.select(null)
        assertNull(preferences.getString("selected_server", null))
        assertNull(preferences.getString("selected_riwayah", null))
        assertEquals(DEFAULT_RECITER_NAME, store.current().name)
    }

    @Test fun selectingArabicReciterReplacesBengaliTranslationAudioPreference() {
        quranPreferences.edit()
            .putString("quran_translation", "bn")
            .putString("audio_language", AudioLanguage.BENGALI_TRANSLATION.name)
            .commit()

        val store = ReciterSelectionStore(context)
        assertEquals(true, store.hasTranslationAudioSelection())
        store.select(yasser)

        assertEquals("ar", quranPreferences.getString("quran_translation", null))
        assertEquals(AudioLanguage.ARABIC_ONLY.name, quranPreferences.getString("audio_language", null))
        assertEquals(yasser.id, ReciterSelectionStore(context).current().id)
        assertEquals(false, store.hasTranslationAudioSelection())
    }
}
