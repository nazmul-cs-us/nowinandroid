package com.starception.submission.feature.surah

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.feature.quran.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h680dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollapsibleTafsirTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tafsirUsesTheSharedHomeRevealAndSelectedQuranIcon() = checkDisclosure()
    @Test
    @Config(qualifiers = "w320dp-h680dp", sdk = [35])
    fun disclosureRemainsUsableInDarkThemeWithLargerText() = checkDisclosure(dark = true, fontScale = 1.3f)

    private fun checkDisclosure(dark: Boolean = false, fontScale: Float = 1f) {
        val text = "Commentary from the selected published tafsir. ".repeat(16)
        val player = Player()
        var reads = 0
        compose.setTafsirContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    Surface { LazyColumn(Modifier.fillMaxSize()) {
                        item { InlineTafsirCommentary(95, 1, "ibn_kathir_en", embedded = true,
                            loadText = { reads++; text }, speechPlayerFactory = { player }, missingAsset = { Text("Download required") }) }
                    } }
                }
            }
        }
        compose.onNodeWithContentDescription("Play tafsir").assertIsDisplayed()
        val toggle = compose.onNodeWithTag("tafsir-toggle-95-1")
        toggle.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Tafsir collapsed"))
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("tafsir-disclosure-icon-95-1", useUnmergedTree = true).assertIsDisplayed().assertWidthIsEqualTo(20.dp)
        compose.onNodeWithText(text).assertDoesNotExist()
        compose.onNodeWithTag("tafsir-disclosure-95-1").captureRoboImage(
            "src/testDemo/screenshots/tafsir_disclosure_${if (dark) "dark" else "light"}_collapsed.png")
        toggle.performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText(text).assertIsDisplayed()
        compose.onNodeWithTag("tafsir-disclosure-95-1").captureRoboImage(
            "src/testDemo/screenshots/tafsir_disclosure_${if (dark) "dark" else "light"}_expanded.png")
        compose.onNodeWithContentDescription("Play tafsir").performClick()
        assertEquals(1, player.plays)
        toggle.performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText(text).assertDoesNotExist()
        compose.onNodeWithContentDescription("Pause tafsir").assertIsDisplayed()
        assertEquals("Collapsing must not dispose playback", 0, player.releases)
        toggle.performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText(text).assertIsDisplayed()
        assertEquals("Expanding must not reload commentary", 1, reads)
    }

    @Test fun activeNarrationOpensThePassageButTheUserCanStillCollapseIt() {
        val narrating = mutableStateOf(false)
        compose.setTafsirContent { MaterialTheme { Surface {
            InlineTafsirCommentary(95, 1, "ibn_kathir_en", embedded = true, narrating = narrating.value,
                loadText = { "Narrated commentary" }, speechPlayerFactory = { Player() }, missingAsset = { Text("Download required") })
        } } }
        compose.onNodeWithText("Narrated commentary").assertDoesNotExist()
        compose.runOnIdle { narrating.value = true }
        compose.onNodeWithText("Narrated commentary").assertIsDisplayed()
        compose.onNodeWithTag("tafsir-toggle-95-1").performClick()
        compose.onNodeWithText("Narrated commentary").assertDoesNotExist()
    }

    @Test fun downloadingMissingCommentaryKeepsTheDisclosureOpenAcrossRevisionChanges() {
        val revision = mutableIntStateOf(0)
        var available = false
        compose.setTafsirContent { MaterialTheme { Surface {
            InlineTafsirCommentary(95, 1, "ibn_kathir_en", revision.intValue, embedded = true,
                loadText = { if (available) "Downloaded commentary" else throw PublishedTafsirAssetMissing() },
                speechPlayerFactory = { Player() }, missingAsset = { reload ->
                    Button(onClick = { available = true; revision.intValue++; reload() }) { Text("Download tafsir") }
                })
        } } }
        compose.onNodeWithTag("tafsir-toggle-95-1").performClick()
        compose.onNodeWithText("Download tafsir").performClick()
        compose.onNodeWithText("Downloaded commentary").assertIsDisplayed()
        compose.onNodeWithTag("tafsir-toggle-95-1")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Tafsir expanded"))
    }

    private class Player : TafsirSpeechPlayer {
        override val state = MutableStateFlow(TafsirSpeechState())
        var plays = 0
        var releases = 0
        override fun play(text: String, language: String) { plays++; state.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
        override fun pause() { state.value = TafsirSpeechState(TafsirSpeechStatus.PAUSED) }
        override fun resume() { state.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
        override fun stop() { state.value = TafsirSpeechState() }
        override fun release() { releases++; stop() }
    }
}
