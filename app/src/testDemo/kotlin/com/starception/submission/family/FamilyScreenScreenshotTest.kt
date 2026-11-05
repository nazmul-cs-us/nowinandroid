package com.starception.submission.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.core.designsystem.theme.NiaTheme
import com.starception.submission.core.model.data.ThemeBrand
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

/** Real Compose renders for visual review of the family landing/contacts states. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w390dp-h844dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class FamilyScreenScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val members = listOf("Amina Khan", "Omar Hassan", "Maryam Ali", "Yusuf Ahmed", "Sara Malik")
        .map { FamilyMember(it.lowercase().replace(' ', '.') + "@xmpp.starception.com", "accepted") }
    private fun render(dark: Boolean, connected: Boolean, brand: ThemeBrand = ThemeBrand.COASTAL) {
        compose.setContent {
            NiaTheme(darkTheme = dark, themeBrand = brand, disableDynamicTheming = true) {
                FamilyScreenContent(FamilyUiState(connected = connected, serviceReady = connected,
                    members = if (connected) members else emptyList()))
            }
        }
    }
    private fun capture(name: String) {
        File("src/test/screenshots/Family").mkdirs()
        compose.onRoot().captureRoboImage("src/test/screenshots/Family/$name.png")
    }
    @Test fun landingLight() { render(false, false); capture("landing-light") }
    @Test fun landingDark() { render(true, false); capture("landing-dark") }
    @Test fun contactsLight() { render(false, true); capture("contacts-light") }
    @Test fun contactsDark() { render(true, true); capture("contacts-dark") }
    @Test fun contactsRoyalTheme() { render(false, true, ThemeBrand.ROYAL); capture("contacts-royal") }
    @Test fun incomingCall() {
        compose.setContent {
            NiaTheme(darkTheme = false) {
                FamilyCallContent(FamilyUiState(me = "omar@xmpp.starception.com",
                    call = FamilyCall("call-preview", "amina.khan@xmpp.starception.com", "omar@xmpp.starception.com", "voice", "ringing", 1, Long.MAX_VALUE, false)))
            }
        }
        compose.onNodeWithText("Accept").assertExists()
        compose.onNodeWithText("Decline").assertExists()
        capture("incoming-call")
    }
    @Test fun outgoingVideoShowsLocalPreviewBeforeAnswer() {
        compose.setContent {
            NiaTheme {
                FamilyCallContent(FamilyUiState(me = "omar@xmpp.starception.com",
                    call = FamilyCall("call-preview", "omar@xmpp.starception.com", "amina.khan@xmpp.starception.com", "video", "ringing", 1, Long.MAX_VALUE, true)),
                    localReady = true, localVideo = {
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer)) {
                            androidx.compose.material3.Text("Local preview fixture")
                        }
                    })
            }
        }
        compose.onNodeWithText("Local preview fixture").assertExists()
        compose.onNodeWithText("Calling…").assertExists()
        compose.onNodeWithText("Flip").assertExists()
        compose.onNodeWithText("Cancel").assertExists()
        compose.onNodeWithText("Mute").assertDoesNotExist()
    }
    @Test fun activeVoiceCallDark() {
        compose.setContent {
            NiaTheme(darkTheme = true) {
                FamilyCallContent(FamilyUiState(me = "omar@xmpp.starception.com", mediaConnected = true,
                    call = FamilyCall("call-preview", "amina.khan@xmpp.starception.com", "omar@xmpp.starception.com", "voice", "active", 2, Long.MAX_VALUE, true)), elapsed = 94)
            }
        }
        compose.onNodeWithText("01:34").assertExists()
        capture("active-voice-dark")
    }
    @Test fun contactActionsAreInSheet() {
        render(false, true)
        compose.onNodeWithText("Amina Khan").performClick()
        compose.onNodeWithText("Video call").assertExists()
        compose.onNodeWithText("Voice call").assertExists()
        compose.onNodeWithText("amina.khan@xmpp.starception.com").assertExists()
    }
    @Test fun searchCanBeOpenedAndClosed() {
        render(false, true)
        compose.onNodeWithContentDescription("Search family").performClick()
        compose.onNodeWithText("Search your family").assertExists()
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNodeWithText("Search your family").assertDoesNotExist()
    }
}
