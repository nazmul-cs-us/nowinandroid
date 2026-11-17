package com.starception.submission.feature.surah

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.feature.quran.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h720dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PublishedTafsirContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun choosingALanguageChangesBothEditionAndTextAndDoesNotCarryOverThePreviousLanguage() {
        val selected = mutableStateOf(PublishedTafsirs.forLanguage("en")!!)
        val samples = mapOf("en" to "English commentary sample.", "hi" to "हिन्दी तफ़सीर का पाठ।",
            "ur" to "اردو تفسیر کا متن۔", "zh" to "中文经文注释。")
        compose.setContent { MaterialTheme { Surface { Column(Modifier.padding(20.dp)) {
            PublishedTafsirPicker(selected.value.bookKey) { selected.value = it }
            val edition = selected.value
            val repository = remember(edition.bookKey) { PublishedTafsirRepository {
                """{"surah":1,"ayahCount":7,"passages":["${samples[edition.languageCode]}"],"ayahPassage":[0,0,0,0,0,0,0]}"""
            } }
            PublishedTafsirContent(1, 1, edition, repository)
        } } } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(samples.getValue("en"))).fetchSemanticsNodes().isNotEmpty() }
        var previous = "en"
        for (code in listOf("ur", "hi", "zh")) {
            val edition = PublishedTafsirs.forLanguage(code)!!
            compose.onNodeWithTag("published-tafsir-picker").performClick()
            compose.onNodeWithText(edition.languageName).performScrollTo().performClick()
            compose.waitUntil(5_000) { compose.onAllNodes(hasText(samples.getValue(code))).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(samples.getValue(code)).assertIsDisplayed()
            compose.onNodeWithText(samples.getValue(previous)).assertDoesNotExist()
            compose.onNodeWithText("${edition.languageName} · ${edition.attribution}").assertIsDisplayed()
            compose.onRoot().captureRoboImage("src/testDemo/screenshots/published_tafsir_$code.png")
            previous = code
        }
    }
    @Test fun swedishIsExplicitlyUnavailableInsteadOfPresentingAnAutomaticTranslationAsPublished() {
        compose.setContent { MaterialTheme { PublishedTafsirPicker(null) {} } }
        compose.onNodeWithTag("published-tafsir-picker").performClick()
        compose.onNodeWithText("Svenska").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Published tafsir source unavailable").assertExists()
    }
}
