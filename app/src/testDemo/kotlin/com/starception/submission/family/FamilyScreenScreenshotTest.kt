package com.starception.submission.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
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
        .mapIndexed { index, name -> FamilyMember(name.lowercase().replace(' ', '.') + "@xmpp.starception.com", "accepted",
            when (index) { 0 -> "online"; 1 -> "offline"; else -> "unknown" }) }
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
    @Test fun signupCollectsANewUsernameAndConfirmedPassword() {
        var submitted: List<String>? = null
        compose.setContent { NiaTheme {
            FamilyScreenContent(FamilyUiState(), FamilyActions(signUp = { name, secret, confirmation ->
                submitted = listOf(name, secret, confirmation)
            }), initialAccount = "android@xmpp.starception.com", initialPassword = "old-pilot-value")
        } }
        compose.onNodeWithText("New to Family? Create an account").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Choose a username").performScrollTo().performTextInput("amina")
        compose.onNodeWithContentDescription("Password").performScrollTo().performTextInput("a secure password")
        compose.onNodeWithContentDescription("Confirm password").performScrollTo().performTextInput("a secure password")
        compose.onNodeWithText("Create account").performScrollTo().performClick()
        compose.runOnIdle { kotlin.test.assertEquals(listOf("amina", "a secure password", "a secure password"), submitted) }
        capture("signup-light")
    }
    @Test fun signupToggleDoesNotKeepPasswordsWhenReturningToSignIn() {
        compose.setContent { NiaTheme { FamilyScreenContent(FamilyUiState()) } }
        compose.onNodeWithText("New to Family? Create an account").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Password").performScrollTo().performTextInput("a secure password")
        compose.onNodeWithText("Already have an account? Sign in").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Confirm password").assertDoesNotExist()
        compose.onNodeWithText("Connect with family").assertExists()
    }
    @Test fun landingLight() { render(false, false); capture("landing-light") }
    @Test fun landingDark() { render(true, false); capture("landing-dark") }
    @Test fun contactsLight() {
        render(false, true)
        compose.onNodeWithText("Online").assertExists()
        compose.onNodeWithText("Offline").assertExists()
        capture("contacts-light")
    }
    @Test fun disconnectedPresenceCannotShowAStaleOnlineIndicator() {
        compose.setContent {
            NiaTheme {
                FamilyScreenContent(FamilyUiState(connected = true, serviceReady = false,
                    members = listOf(FamilyMember("amina@xmpp.starception.com", "accepted", "online"))))
            }
        }
        compose.onNodeWithText("Online").assertDoesNotExist()
        compose.onNodeWithText("Status unavailable").assertExists()
    }
    @Test fun temporaryAlertsRequireAnExplicitFamilyMenuAction() {
        var requested = 0
        compose.setContent { NiaTheme {
            FamilyScreenContent(FamilyUiState(connected = true, serviceReady = true),
                FamilyActions(webHelper = { requested++ }))
        } }
        compose.onNodeWithContentDescription("Family settings").performClick()
        compose.onNodeWithText("Temporary call alerts").performClick()
        compose.runOnIdle { kotlin.test.assertEquals(1, requested) }
    }
    @Test fun missingFirebaseConfigurationCannotThrowIntoSignIn() {
        compose.setContent { androidx.compose.material3.Text("Optional notification setup") }
        compose.runOnIdle {
            val context = compose.activity.applicationContext
            com.google.firebase.FirebaseApp.getApps(context).forEach { it.delete() }
            kotlin.test.assertFailsWith<IllegalStateException> { com.google.firebase.messaging.FirebaseMessaging.getInstance() }
            val client = FamilyClient(context)
            client.registerPushToken()
            kotlin.test.assertNull(client.state.value.error)
        }
    }
    @Test fun contactsDark() { render(true, true); capture("contacts-dark") }
    @Test fun previewStartsWhenAlreadyResumedAndRestartsAfterPause() {
        var starts = 0
        var stops = 0
        compose.setContent {
            FamilyCameraPreviewEffect("call-preview", compose.activity.lifecycle,
                onStart = { starts++ }, onStop = { stops++ })
            androidx.compose.material3.Text("Preview lifecycle fixture")
        }
        compose.runOnIdle { kotlin.test.assertEquals(1, starts) }
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        compose.runOnIdle { kotlin.test.assertEquals(1, stops) }
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.runOnIdle { kotlin.test.assertEquals(2, starts) }
    }
    @Test fun stoppingUntrackedSplashDoesNotHideCurrentActivity() {
        compose.setContent { androidx.compose.material3.Text("Visibility fixture") }
        compose.runOnIdle {
            val splash = org.robolectric.Robolectric.buildActivity(ComponentActivity::class.java).setup()
            val client = FamilyClient(compose.activity.applicationContext)
            val main = org.robolectric.Robolectric.buildActivity(ComponentActivity::class.java).setup()
            try {
                splash.pause().stop().destroy()
                kotlin.test.assertTrue(client.hasForegroundActivity)
            } finally { main.pause().stop().destroy() }
            kotlin.test.assertFalse(client.hasForegroundActivity)
        }
    }
    @Test fun callScreenShowsOnlyCallErrors() {
        compose.setContent {
            NiaTheme {
                FamilyCallContent(FamilyUiState(me = "omar@xmpp.starception.com",
                    error = "General family action failed", pushRegistrationError = "Background registration failed",
                    callError = "Camera access required",
                    call = FamilyCall("call-preview", "omar@xmpp.starception.com", "amina@xmpp.starception.com", "video", "ringing", 1, Long.MAX_VALUE, true)))
            }
        }
        compose.onNodeWithText("General family action failed").assertDoesNotExist()
        compose.onNodeWithText("Background registration failed").assertDoesNotExist()
        compose.onNodeWithText("Camera access required").assertExists()
    }
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
