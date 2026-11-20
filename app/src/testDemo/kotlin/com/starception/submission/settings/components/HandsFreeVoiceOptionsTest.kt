package com.starception.submission.settings.components

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.voice.handsfree.HandsFreePhase
import com.starception.submission.voice.handsfree.HandsFreeStatus
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h680dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HandsFreeVoiceOptionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun listeningAndDefaultAssistantAreSeparateUserChoices() {
        val status = mutableStateOf(HandsFreeStatus())
        var assistantRequests = 0
        compose.setContent { MaterialTheme { Surface {
            HandsFreeVoiceOptions(status.value, true, false,
                onToggle = { status.value = if (it) HandsFreeStatus(HandsFreePhase.WAKE_WORD, "Say Hey Deenly") else HandsFreeStatus() },
                onChooseAssistant = { assistantRequests++ })
        } } }
        compose.onNodeWithText("Hey Deenly").assertIsOff()
        compose.onNodeWithText("Choose Deenly as default assistant").performClick()
        assertEquals(1, assistantRequests)
        compose.onNodeWithText("Hey Deenly").assertIsOff()
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/hands_free_voice_options.png")
        compose.onNodeWithText("Hey Deenly").performClick().assertIsOn()
        compose.onNodeWithText("Hey Deenly").performClick().assertIsOff()
    }

    @Test fun missingModelsDisableListeningButStillAllowAssistantSelection() {
        compose.setContent { MaterialTheme { Surface {
            HandsFreeVoiceOptions(HandsFreeStatus(), false, false, {}, {})
        } } }
        compose.onNodeWithText("Hey Deenly").assertIsNotEnabled().assertIsOff()
        compose.onNodeWithText("Choose Deenly as default assistant").assertIsEnabled()
    }
}
