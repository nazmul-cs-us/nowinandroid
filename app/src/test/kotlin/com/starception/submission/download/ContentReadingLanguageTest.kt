package com.starception.submission.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.starception.submission.core.translation.LocationBasedTranslationDefaults
import com.starception.submission.core.translation.TranslationService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContentReadingLanguageTest {
    @Test fun onlySelectedTranslationIsDownloaded() = runBlocking {
        val files = mutableSetOf<String>()
        val requests = mutableListOf<String>()
        assertTrue(ContentReadingLanguage.ensureAvailable("bn", files::contains) { key ->
            requests += key
            files += key
            true
        })
        assertEquals(listOf("databases/quran/quran_bn.db"), requests)
    }

    @Test fun failedDownloadIsNotReadyAndCanBeRetried() = runBlocking {
        val files = mutableSetOf<String>()
        assertFalse(ContentReadingLanguage.ensureAvailable("ur", files::contains) { false })
        // A success response without a usable file must also not finish setup.
        assertFalse(ContentReadingLanguage.ensureAvailable("ur", files::contains) { true })
        assertTrue(ContentReadingLanguage.ensureAvailable("ur", files::contains) { files += it; true })
        assertTrue(ContentReadingLanguage.ensureAvailable("ur", files::contains) { error("Already cached") })
    }

    @Test fun arabicNeedsNoAdditionalTranslation() = runBlocking {
        assertTrue(ContentReadingLanguage.ensureAvailable("ar", { false }) { error("No translation needed") })
    }

    @Test fun setupLanguagesAllHaveAManifestAsset() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manifest = AssetManifest.fromJson(context.assets.open("manifest.json").bufferedReader().use { it.readText() })
        ContentReadingLanguage.languages.forEach { (code, _) ->
            ContentReadingLanguage.assetKey(code)?.let { assertTrue("Missing $it", manifest.assets.containsKey(it)) }
        }
    }

    @Test fun explicitSelectionIsSharedWithReadersAndSurvivesCountryDetection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        LocationBasedTranslationDefaults.prepare(prefs, isFreshInstall = true)
        TranslationService.getInstance(context).setSelectedLanguage("bn")
        assertEquals("bn", prefs.getString("quran_translation", null))
        assertNull(LocationBasedTranslationDefaults.applyDetectedCountry(context, "TR"))
        assertEquals("bn", TranslationService.getInstance(context).getSelectedLanguage())
        prefs.edit().clear().commit()
    }
}
