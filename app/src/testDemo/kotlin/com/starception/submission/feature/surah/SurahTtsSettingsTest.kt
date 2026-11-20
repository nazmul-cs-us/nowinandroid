package com.starception.submission.feature.surah

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.settings.components.TtsVoice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w320dp-h240dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SurahTtsSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun voiceSettingsRowShowsTheAppsSavedModelAndOpensTheAppsPicker() = checkRow()

    @Test fun voiceSettingsRemainUsableWithLargerTextInDarkTheme() = checkRow(dark = true, fontScale = 1.5f)

    private fun checkRow(dark: Boolean = false, fontScale: Float = 1f) {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("selected_voice", TtsVoice.VITS_VCTK.name).putInt("selected_speaker_id", 12).commit()
        var opened = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    Surface { SurahTtsSettingsEntry(onClick = { opened++ }) }
                }
            }
        }
        compose.onNodeWithText("VCTK British · Speaker 12").assertIsDisplayed()
        val row = compose.onNodeWithText("TTS voice settings").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        row.captureRoboImage(if (dark) "src/testDemo/screenshots/surah_tts_settings_dark.png"
            else "src/testDemo/screenshots/surah_tts_settings.png")
        row.performClick()
        assertEquals(1, opened)
        compose.runOnIdle { selectSurahTtsVoice(prefs, TtsVoice.KOKORO_EN) }
        compose.onNodeWithText("Kokoro · Speaker 0").assertIsDisplayed()
    }

    @Test fun choosingAnAppModelResetsTheSpeakerInTheSharedSettingsPreferences() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_voice", TtsVoice.VITS_VCTK.name).putInt("selected_speaker_id", 108).commit()
        selectSurahTtsVoice(prefs, TtsVoice.KOKORO_EN)
        assertEquals(SurahTtsSelection(TtsVoice.KOKORO_EN, 0), readSurahTtsSelection(prefs))
        assertEquals(TtsVoice.KOKORO_EN.name, prefs.getString("selected_voice", null))
    }

    @Test fun invalidModelsAndOutOfRangeSpeakersUseSafeAppDefaults() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_voice", "retired").putInt("selected_speaker_id", 999).commit()
        assertEquals(TtsVoice.KOKORO_EN, readSurahTtsSelection(prefs).voice)
        selectSurahTtsSpeaker(prefs, -1)
        assertEquals(0, readSurahTtsSelection(prefs).speakerId)
    }
}
