package com.starception.submission.feature.surah

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.feature.quran.rememberAyahScrollGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        compose.setTafsirContent { MaterialTheme {
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
    @Test fun visibleTafsirCanBeHiddenFromTheCompactTopToolbar() {
        val visible = androidx.compose.runtime.mutableStateOf(true)
        compose.setTafsirContent { MaterialTheme {
            AlbumPlayerTopBar(1f, false, "Al-Fatihah", "الفاتحة", "en", false, "pdms_saleem", false,
                onBackClick = {}, onTafsirClick = { visible.value = !visible.value },
                isTafsirVisible = visible.value)
        } }
        compose.onNodeWithContentDescription("Hide tafsir").assertIsDisplayed()
            .assertIsOn().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("Hide tafsir").assertDoesNotExist()
        compose.onNodeWithContentDescription("Tafsir").assertIsDisplayed().assertIsOff().performClick()
        compose.onNodeWithContentDescription("Hide tafsir").assertIsDisplayed().assertIsOn()
        compose.onNodeWithText("Hide").assertDoesNotExist()
    }
    @Test fun tafsirIsDisabledUntilTheSurahCanBeOpened() {
        compose.setTafsirContent { MaterialTheme {
            AlbumPlayerTopBar(1f, false, "", "", "ar", false, "pdms_saleem", false, onBackClick = {})
        } }
        compose.onNodeWithContentDescription("Tafsir").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun collapsedTitleAndAllControlsHaveSeparateSpaceOnCompactScreens() =
        assertToolbarLayout(width = 320.dp, title = "At-Tin")

    @Test fun longTitleAndLargeTextDoNotOverlapControls() =
        assertToolbarLayout(width = 320.dp, title = "Al-Mu'minun", fontScale = 2f)

    @Test
    @Config(qualifiers = "w600dp-h240dp", sdk = [35])
    fun toolbarUsesItsParentWidthRatherThanTheScreenWidth() =
        assertToolbarLayout(width = 280.dp, title = "Al-Fatihah", fontScale = 1.3f)

    @Test
    @Config(qualifiers = "w320dp-h600dp", sdk = [35])
    fun narrationKeepsTheAyahBelowTheMeasuredToolbar() {
        val narration = mutableStateOf<IntRange?>(null)
        lateinit var list: LazyListState
        compose.setTafsirContent { MaterialTheme {
            val density = LocalDensity.current
            val toolbarHeight = remember { mutableStateOf(0.dp) }
            list = rememberLazyListState()
            rememberAyahScrollGuide(list, "95", true, narration.value, 0, topInset = toolbarHeight.value)
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                    items(10) { index ->
                        Box(Modifier.height(180.dp).testTag("ayah-$index")) { Text("Ayah ${index + 1}") }
                    }
                }
                AlbumPlayerTopBar(1f, true, "At-Tin", "التين", "en", false, "pdms_saleem", false,
                    onBackClick = {}, modifier = Modifier.testTag("toolbar").onSizeChanged {
                        toolbarHeight.value = with(density) { it.height.toDp() }
                    })
            }
        } }
        compose.runOnIdle { narration.value = 4..4 }
        compose.waitForIdle()
        val toolbarBottom = compose.onNodeWithTag("toolbar").fetchSemanticsNode().boundsInRoot.bottom
        val ayahTop = compose.onNodeWithTag("ayah-3").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        assertTrue("The followed ayah must clear the toolbar", ayahTop >= toolbarBottom - 1f)
    }

    private fun assertToolbarLayout(width: Dp, title: String, fontScale: Float = 1f) {
        val showLanguage = width >= 320.dp && fontScale <= 1.5f
        val clicks = mutableListOf<String>()
        compose.setTafsirContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box(Modifier.width(width).testTag("toolbar-host")) {
                        AlbumPlayerTopBar(1f, true, title, "التين", "en", false, "pdms_saleem", false,
                            onBackClick = { clicks += "Back" },
                            onBookmarkClick = { clicks += "Add bookmark" },
                            onMoreClick = { clicks += "More options" },
                            onTranslationClick = { clicks += "Change translation language, current: EN" },
                            onTafsirClick = { clicks += "Hide tafsir" },
                            isTafsirVisible = true)
                    }
                }
            }
        }
        val descriptions = buildList {
            add("Back")
            if (showLanguage) add("Change translation language, current: EN")
            add("Add bookmark")
            add("Hide tafsir")
            add("More options")
        }
        if (!showLanguage) compose.onNodeWithContentDescription("Change translation language, current: EN").assertDoesNotExist()
        val controls = descriptions.map { description ->
            compose.onNodeWithContentDescription(description).assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        val bounds = controls.map { it.fetchSemanticsNode().boundsInRoot }
        val hostBounds = compose.onNodeWithTag("toolbar-host").fetchSemanticsNode().boundsInRoot
        val titleBounds = compose.onNodeWithText(title).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        bounds.forEachIndexed { index, rect ->
            assertTrue("${descriptions[index]} must fit inside the parent", rect.left >= hostBounds.left &&
                rect.right <= hostBounds.right && rect.top >= hostBounds.top && rect.bottom <= hostBounds.bottom)
            assertFalse("Title overlaps ${descriptions[index]}", titleBounds.overlaps(rect))
            bounds.drop(index + 1).forEach { other -> assertFalse("Controls overlap", rect.overlaps(other)) }
            assertEquals("All controls must stay in a single row", bounds.first().center.y, rect.center.y, 0.5f)
        }
        assertTrue("Title must fit between Back and the actions", titleBounds.left >= bounds[0].right &&
            titleBounds.right <= bounds[1].left)
        if (fontScale == 1f) {
            compose.onNodeWithTag("toolbar-host").assertHeightIsEqualTo(64.dp)
            compose.onRoot().captureRoboImage("src/testDemo/screenshots/surah_tafsir_toolbar_dark_active.png")
        }
        controls.forEach { it.performClick() }
        assertEquals(descriptions, clicks)
    }
}
