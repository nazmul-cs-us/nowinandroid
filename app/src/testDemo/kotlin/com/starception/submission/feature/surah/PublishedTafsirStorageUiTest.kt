package com.starception.submission.feature.surah

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.starception.submission.download.CategoryDownloadState
import com.starception.submission.settings.components.ContentManagementSection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h900dp", sdk = [35])
class PublishedTafsirStorageUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun tafsirGroupCanDownloadAndRemoveIndividualLanguageEditions() {
        val english = CategoryDownloadState("quran_tafsir_en", "English tafsir", "All 114 surahs", 100, 0, 116, false, false)
        val bengali = CategoryDownloadState("quran_tafsir_bn_zakaria", "Bengali tafsir", "All 114 surahs", 100, 100, 116, false, true)
        val calls = mutableListOf<String>()
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ContentManagementSection(listOf(english, bengali), 100,
                onDownloadCategory = { calls += "download:$it" },
                onDeleteCategory = { calls += "delete:$it" })
        } } }
        compose.onNodeWithText("Tafsir").performClick()
        compose.onNodeWithContentDescription("Download English tafsir").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Remove Bengali tafsir").performScrollTo().performClick()
        assertEquals(listOf("download:quran_tafsir_en", "delete:quran_tafsir_bn_zakaria"), calls)
    }
    @Test fun aCachedRetiredCategoryCannotReappearInStorage() {
        val retired = CategoryDownloadState("quran_tafsir_bn", "Old Bengali Ibn Kathir", "Retired", 100, 100, 116, false, true)
        val active = CategoryDownloadState("quran_tafsir_bn_zakaria", "Bengali Zakaria", "All 114 surahs", 100, 0, 116, false, false)
        compose.setContent { MaterialTheme { ContentManagementSection(listOf(retired, active), 100, {}, {}) } }
        compose.onNodeWithText("Tafsir").performClick()
        compose.onNodeWithText("Old Bengali Ibn Kathir").assertDoesNotExist()
        compose.onNodeWithText("Bengali Zakaria").assertIsDisplayed()
    }

}
