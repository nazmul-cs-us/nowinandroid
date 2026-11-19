package com.starception.submission.download

import com.starception.submission.feature.quran.PublishedTafsirs
import com.starception.submission.feature.quran.QuranSetupContent
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class QuranSetupContentTest {
    @Test fun selectedLanguageAddsExactlyItsTafsirToFirstRunRequirements() {
        val base = setOf("quran_core", "news")
        for (edition in PublishedTafsirs.editions.map { it.languageCode }.distinct().mapNotNull(PublishedTafsirs::forLanguage)) {
            assertEquals(base + edition.category, QuranSetupContent.requiredCategories(base, edition.languageCode))
            assertFalse(QuranSetupContent.requiredCategories(base, edition.languageCode).any { it.startsWith("quran_audio_") })
        }
    }
    @Test fun changingLanguageReplacesTheTafsirRequirementInsteadOfKeepingThePreviousBook() {
        val base = setOf("quran_core")
        assertEquals(base + "quran_tafsir_bn_zakaria", QuranSetupContent.requiredCategories(base, "bn"))
        assertEquals(base + "quran_tafsir_ur", QuranSetupContent.requiredCategories(base, "ur"))
        assertFalse("quran_tafsir_bn_zakaria" in QuranSetupContent.requiredCategories(base, "ur"))
    }
    @Test fun cachedTranslationAloneDoesNotFinishSetupAndPartialTafsirCanBeRetried() {
        val files = mutableSetOf("databases/quran/quran_bn.db")
        assertFalse(QuranSetupContent.tafsirReady("bn", isAvailable = files::contains))
        files += QuranSetupContent.tafsirAssets("bn").dropLast(1)
        assertFalse(QuranSetupContent.tafsirReady("bn", isAvailable = files::contains))
        files += QuranSetupContent.tafsirAssets("bn")
        assertTrue(QuranSetupContent.tafsirReady("bn", isAvailable = files::contains))
        files -= "tafsir/bn-zakaria/v1/114.json"
        assertFalse(QuranSetupContent.tafsirReady("bn", isAvailable = files::contains))
    }
    @Test fun unsupportedTafsirLanguagesAndArabicRequireNoAdditionalBook() {
        for (code in listOf("sv", "ar", "", "transliteration")) {
            assertNull(QuranSetupContent.tafsirEdition(code))
            assertEquals(setOf("quran_core"), QuranSetupContent.requiredCategories(setOf("quran_core"), code))
            assertTrue(QuranSetupContent.tafsirReady(code) { false })
        }
    }
    @Test fun completedSetupDoesNotForceExistingUsersToDownloadOptionalBooks() {
        assertEquals(setOf("quran_core"), QuranSetupContent.requiredCategories(setOf("quran_core"), "bn", setupComplete = true))
        assertTrue(QuranSetupContent.tafsirReady("bn", setupComplete = true) { false })
    }
    @Test fun everySetupEditionHasAll114ChaptersAndMetadataInTheRealDownloadManifest() {
        val manifest = Json.parseToJsonElement(File("src/main/assets/manifest.json").readText()).jsonObject
        val assets = manifest.getValue("assets").jsonObject
        for (edition in PublishedTafsirs.editions.map { it.languageCode }.distinct().mapNotNull(PublishedTafsirs::forLanguage)) {
            val required = QuranSetupContent.tafsirAssets(edition.languageCode)
            assertEquals(116, required.size)
            assertTrue(required.contains(edition.chapterKey(1)))
            assertTrue(required.contains(edition.chapterKey(114)))
            for (key in required) assertEquals(edition.category, assets.getValue(key).jsonObject.getValue("category").jsonPrimitive.content)
            assertFalse(manifest.getValue("categories").jsonObject.getValue(edition.category).jsonObject.getValue("required").jsonPrimitive.boolean)
        }
    }
}
