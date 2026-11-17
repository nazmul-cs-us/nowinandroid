package com.starception.submission.feature.surah

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.starception.submission.feature.quran.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TafsirPlaybackControlsTest {
    @get:Rule val compose = createComposeRule()
    private class Player : TafsirSpeechPlayer {
        override val state = MutableStateFlow(TafsirSpeechState())
        val calls = mutableListOf<String>()
        override fun play(text: String, language: String) { calls += "$language:$text"; state.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
        override fun pause() { calls += "pause"; state.value = TafsirSpeechState(TafsirSpeechStatus.PAUSED) }
        override fun resume() { calls += "resume"; state.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
        override fun stop() { calls += "stop"; state.value = TafsirSpeechState() }
        override fun release() { calls += "release"; stop() }
    }
    @Test fun playsOnlyOnTapAndSupportsPauseResumeAndStop() {
        val player = Player()
        compose.setContent { MaterialTheme { TafsirPlaybackControls("বাংলা তাফসীর", "bn") { player } } }
        assertTrue(player.calls.isEmpty())
        compose.onNodeWithText("Play tafsir").performClick()
        compose.onNodeWithText("Pause tafsir").performClick()
        compose.onNodeWithText("Resume tafsir").performClick()
        compose.onNodeWithContentDescription("Stop tafsir").performClick()
        compose.onNodeWithText("Play tafsir").assertIsDisplayed()
        assertEquals(listOf("bn:বাংলা তাফসীর", "pause", "resume", "stop"), player.calls)
    }
    @Test fun changingLanguageOrClosingTheReaderReleasesOldSpeech() {
        val language = mutableStateOf("en")
        val visible = mutableStateOf(true)
        val players = mutableListOf<Player>()
        compose.setContent { MaterialTheme { if (visible.value) TafsirPlaybackControls("Commentary", language.value) {
            Player().also(players::add)
        } } }
        compose.onNodeWithText("Play tafsir").performClick()
        compose.runOnIdle { language.value = "ar" }
        compose.waitForIdle()
        assertTrue(players[0].calls.contains("release"))
        assertEquals(2, players.size)
        assertTrue(players[1].calls.isEmpty())
        compose.onNodeWithText("Play tafsir").performClick()
        assertEquals("ar:Commentary", players[1].calls.first())
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        assertTrue(players[1].calls.contains("release"))
    }
    @Test fun missingVoiceMessageLeavesAWorkingRetryButton() {
        val player = Player()
        compose.setContent { MaterialTheme { TafsirPlaybackControls("Commentary", "bn") { player } } }
        compose.runOnIdle { player.state.value = TafsirSpeechState(TafsirSpeechStatus.ERROR, "Install a Bengali voice") }
        compose.onNodeWithText("Install a Bengali voice").assertIsDisplayed()
        compose.onNodeWithText("Play tafsir").assertIsEnabled().performClick()
        compose.onNodeWithText("Install a Bengali voice").assertDoesNotExist()
    }
    @Test fun backgroundingTheReaderStopsNarration() {
        val player = Player()
        val owner = object : androidx.lifecycle.LifecycleOwner {
            val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this)
            override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
        }
        compose.runOnIdle { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        compose.setContent { androidx.compose.runtime.CompositionLocalProvider(
            androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
            MaterialTheme { TafsirPlaybackControls("Commentary", "en") { player } }
        } }
        compose.onNodeWithText("Play tafsir").performClick()
        compose.runOnIdle { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.CREATED }
        compose.waitForIdle()
        assertTrue(player.calls.contains("stop"))
        compose.onNodeWithText("Play tafsir").assertIsDisplayed()
    }

}
