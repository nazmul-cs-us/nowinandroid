package com.starception.submission.feature.surah

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w320dp-h112dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SurahTafsirEntryPointTest {
    @get:Rule val compose = createComposeRule()
    @Test fun tafsirIsVisibleAndTappableOnACompactSurahPageWithoutOpeningAnAyahMenu() {
        var opened = 0
        compose.setContent { MaterialTheme {
            AlbumPlayerTopBar(1f, false, "Al-Fatihah", "الفاتحة", "en", false, "pdms_saleem", false,
                onBackClick = {}, onTafsirClick = { opened++ })
        } }
        compose.onNodeWithContentDescription("Tafsir").assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, opened)
        compose.onNodeWithContentDescription("More options").assertIsDisplayed()
        compose.onNodeWithContentDescription("Change translation language, current: EN").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/surah_tafsir_toolbar_compact.png")
    }
    @Test fun tafsirIsDisabledUntilTheSurahCanBeOpened() {
        compose.setContent { MaterialTheme {
            AlbumPlayerTopBar(1f, false, "", "", "ar", false, "pdms_saleem", false, onBackClick = {})
        } }
        compose.onNodeWithContentDescription("Tafsir").assertIsDisplayed().assertIsNotEnabled()
    }
}
