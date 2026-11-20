package com.starception.submission.feature.surah

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
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
@Config(application = Application::class, qualifiers = "w360dp-h400dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactTafsirHeaderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bengaliHeaderIsCompactAndKeepsItsSourcePickerAndPlayback() = checkHeader()

    @Test
    @Config(qualifiers = "w320dp-h400dp", sdk = [35])
    fun compactDarkHeaderSupportsLargerTextWithoutWrappingTheBookName() =
        checkHeader(dark = true, fontScale = 1.3f)

    private fun checkHeader(dark: Boolean = false, fontScale: Float = 1f) {
        val edition = PublishedTafsirs.bengaliZakaria
        val book = mutableStateOf(edition.bookKey)
        var hidden = false
        var opened: String? = null
        compose.setTafsirContent {
            val density = LocalDensity.current
            val uri = remember { object : UriHandler {
                override fun openUri(uri: String) { opened = uri }
            } }
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalUriHandler provides uri) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    val scope = rememberCoroutineScope()
                    val player = remember { SurahTafsirSpeechPlayer(scope, SilentPlayer(), 8, "bn", { "Commentary" }, {}) }
                    DisposableEffect(player) { onDispose { player.release() } }
                    Surface {
                        InlineTafsirHeader(book.value, { book.value = it }, { hidden = true }, playback = {
                            SurahTafsirPlaybackControls(player, "At-Tin", compact = true) { Text("Download required") }
                        })
                    }
                }
            }
        }
        val header = compose.onNodeWithTag("inline-tafsir-header")
        val bounds = header.getUnclippedBoundsInRoot()
        assertTrue("The header should use only two compact control rows",
            bounds.bottom - bounds.top <= if (fontScale > 1f) 128.dp else 112.dp)
        compose.onNodeWithTag("inline-tafsir-icon").assertWidthIsEqualTo(20.dp).assertHeightIsEqualTo(20.dp)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(edition.title, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        header.captureRoboImage(if (dark) "src/testDemo/screenshots/compact_tafsir_header_dark.png"
            else "src/testDemo/screenshots/compact_tafsir_header.png")
        compose.onNodeWithContentDescription("Open tafsir source: ${edition.attribution}")
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(edition.sourceUrl, opened)
        compose.onNodeWithText("Play full surah tafsir").performClick()
        compose.onNodeWithContentDescription("Stop surah tafsir").assertIsDisplayed().performClick()
        compose.onNodeWithTag("published-tafsir-picker").performClick()
        compose.onNodeWithText("Ibn Kathir (abridged)").performClick()
        assertEquals("ibn_kathir_en", book.value)
        compose.onNodeWithContentDescription("Hide tafsir").assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(hidden)
    }

    private class SilentPlayer : TafsirSpeechPlayer {
        override val state = MutableStateFlow(TafsirSpeechState())
        override fun play(text: String, language: String) { state.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
        override fun pause() { state.value = TafsirSpeechState(TafsirSpeechStatus.PAUSED) }
        override fun resume() { state.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
        override fun stop() { state.value = TafsirSpeechState() }
        override fun release() = stop()
    }
}
