package com.starception.submission.settings.components

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.voice.handsfree.HandsFreeStatus
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h900dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SamsungSideButtonSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sideButtonSetupIsASeparateUserChoiceAndDoesNotEnableListening() {
        var opened = 0
        var listeningChanges = 0
        var assistantChanges = 0
        compose.setContent { MaterialTheme { Surface {
            HandsFreeVoiceOptions(HandsFreeStatus(), true, false,
                onToggle = { listeningChanges++ }, onChooseAssistant = { assistantChanges++ },
                showSideButtonSettings = true, onSideButtonSettings = { opened++ })
        } } }
        compose.onNodeWithText("Side button settings").assertIsDisplayed().performClick()
        assertEquals(1, opened)
        assertEquals(0, listeningChanges)
        assertEquals(0, assistantChanges)
        compose.onNodeWithText("Hey Deenly").assertIsOff()
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/samsung_side_button_settings.png")
    }

    @Test fun unsupportedOrProtectedOemPagesFallBackToPublicSettings() {
        val attempts = mutableListOf<Intent>()
        assertTrue(openSamsungSideButtonSettings {
            attempts += it
            if (attempts.size == 1) throw ActivityNotFoundException()
            if (attempts.size == 2) throw SecurityException()
        })
        assertEquals(3, attempts.size)
        assertEquals("com.android.settings", attempts.first().component?.packageName)
        assertEquals(Settings.ACTION_SETTINGS, attempts.last().action)
    }

    @Test fun unavailableSettingsAreReportedWithoutCrashing() {
        assertFalse(openSamsungSideButtonSettings { throw ActivityNotFoundException() })
    }

    @Test fun samsungShortcutIsNotShownOnOtherDevices() {
        compose.setContent { MaterialTheme { Surface {
            HandsFreeVoiceOptions(HandsFreeStatus(), true, false, {}, {})
        } } }
        compose.onNodeWithText("Side button settings").assertDoesNotExist()
    }
}
