package com.starception.submission.feature.surah

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
@Config(application = Application::class, qualifiers = "w393dp-h600dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FullSurahTafsirControlsTest {
    @get:Rule val compose = createComposeRule()
    private class Engine : TafsirSpeechPlayer {
        override val state = MutableStateFlow(TafsirSpeechState())
        val spoken = mutableListOf<String>()
        var released = false
        override fun play(text: String, language: String) { spoken += text; state.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
        override fun pause() { state.value = TafsirSpeechState(TafsirSpeechStatus.PAUSED) }
        override fun resume() { state.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
        override fun stop() { state.value = TafsirSpeechState() }
        override fun release() { released = true; stop() }
    }
    @Test fun fullSurahPlaybackSurvivesScrollingControlsAwayAndCanPauseResumeAndStop() {
        val visible = mutableStateOf(true)
        val engine = Engine()
        compose.setContent { MaterialTheme { Surface { Column {
            val session = remember { TafsirSpeechSession() }
            val player = rememberSurahTafsirSpeechPlayer(1, "ibn_kathir_en", session,
                engineFactory = { engine }, loadAyah = { "Ayah $it commentary" })
            if (visible.value) InlineTafsirHeader("ibn_kathir_en", {}, {}, playback = {
                SurahTafsirPlaybackControls(player, surahTitle = "Al-Fatihah") { Text("Download required") }
            })
        } } } }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/full_surah_tafsir.png")
        assertTrue(engine.spoken.isEmpty())
        compose.onNodeWithText("Play full surah tafsir").performClick()
        compose.waitUntil(5000) { engine.spoken.size == 1 }
        compose.runOnIdle { visible.value = false; engine.state.value = TafsirSpeechState(finished = true) }
        compose.waitUntil(5000) { engine.spoken.size == 2 }
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithText("Ayah 2 of 7").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/full_surah_tafsir_playing.png")
        compose.onNodeWithText("Pause surah tafsir").performClick()
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/full_surah_tafsir_paused.png")
        compose.onNodeWithText("Resume surah tafsir").performClick()
        compose.onNodeWithContentDescription("Stop surah tafsir").performClick()
        compose.onNodeWithText("Play full surah tafsir").assertIsDisplayed()
        compose.runOnIdle { engine.state.value = TafsirSpeechState(finished = true) }
        assertEquals(2, engine.spoken.size)
    }
    @Test fun changingEditionReleasesTheQueueAndDoesNotAutomaticallyStartAnotherBook() {
        val book = mutableStateOf("ibn_kathir_en")
        val engines = mutableListOf<Engine>()
        compose.setContent { MaterialTheme {
            val session = remember { TafsirSpeechSession() }
            val player = rememberSurahTafsirSpeechPlayer(1, book.value, session,
                engineFactory = { Engine().also(engines::add) }, loadAyah = { "Ayah $it" })
            SurahTafsirPlaybackControls(player, surahTitle = "Al-Fatihah") { Text("Download required") }
        } }
        compose.onNodeWithText("Play full surah tafsir").performClick()
        compose.waitUntil(5000) { engines.first().spoken.isNotEmpty() }
        compose.runOnIdle { book.value = "mukhtasar_fr" }
        compose.waitForIdle()
        assertTrue(engines.first().released)
        assertEquals(2, engines.size)
        assertTrue(engines.last().spoken.isEmpty())
        compose.onNodeWithText("Play full surah tafsir").assertIsDisplayed()
    }
    @Test
    @Config(qualifiers = "w320dp-h780dp", sdk = [35])
    fun compactPlayerKeepsPlayAndStopUsableWithLargerTextInBothThemes() {
        val dark = mutableStateOf(false)
        val engine = Engine()
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(density.density, 1.3f)) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    Surface { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(16.dp)) {
                        val session = remember { TafsirSpeechSession() }
                        val player = rememberSurahTafsirSpeechPlayer(2, "ibn_kathir_en", session,
                            engineFactory = { engine }, loadAyah = { "Ayah $it" })
                        SurahTafsirPlaybackControls(player, "Al-Baqarah") { Text("Download required") }
                    } }
                }
            }
        }
        compose.onNodeWithText("Play full surah tafsir").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/full_surah_tafsir_compact.png")
        compose.onNodeWithText("Play full surah tafsir").performClick()
        compose.waitUntil(5000) { engine.spoken.isNotEmpty() }
        compose.runOnIdle { dark.value = true }
        compose.onNodeWithText("Pause surah tafsir").assertIsDisplayed()
        compose.onNodeWithContentDescription("Stop surah tafsir").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/full_surah_tafsir_dark.png")
        compose.onNodeWithContentDescription("Stop surah tafsir").performClick()
        compose.onNodeWithText("Play full surah tafsir").assertIsDisplayed()
    }

}
