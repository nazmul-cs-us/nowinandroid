package com.starception.submission.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.core.designsystem.theme.NiaTheme
import com.starception.submission.core.model.data.ThemeBrand
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
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
    private val originalTimeZone = java.util.TimeZone.getDefault()
    @Before fun useStableScreenshotTimeZone() { java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC")) }
    @After fun restoreTimeZone() { java.util.TimeZone.setDefault(originalTimeZone) }
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
                FamilyCallContent(FamilyUiState(me = "omar@xmpp.starception.com", mediaConnected = true, connectionRoute = "direct",
                    call = FamilyCall("call-preview", "amina.khan@xmpp.starception.com", "omar@xmpp.starception.com", "voice", "active", 2, Long.MAX_VALUE, true)), elapsed = 94)
            }
        }
        compose.onNodeWithText("01:34").assertExists()
        capture("active-voice-dark")
    }
    @Test fun contactActionsAreAvailableFromTheAvatarStrip() {
        render(false, true)
        compose.onNodeWithContentDescription("Contact details for Amina Khan").performClick()
        compose.onNodeWithContentDescription("Video call").assertExists()
        compose.onNodeWithContentDescription("Voice call").assertExists()
        compose.onNodeWithText("amina.khan@xmpp.starception.com").assertExists()
        capture("contact-profile-light")
        compose.onNodeWithContentDescription("Back to family").performClick()
        compose.onNodeWithText("FAMILY ACCOUNT").assertDoesNotExist()
    }
    @Test fun acceptedContactRowOpensChat() {
        var opened: String? = null
        compose.setContent { NiaTheme { FamilyScreenContent(
            FamilyUiState(connected = true, serviceReady = true, members = members), FamilyActions(openChat = { opened = it })) } }
        compose.onNodeWithText("Amina Khan").performClick()
        compose.runOnIdle { kotlin.test.assertEquals(members.first().jid, opened) }
    }
    private fun chatPreviewState(): FamilyUiState {
        val me = "omar@xmpp.starception.com"; val peer = members.first().jid
        return FamilyUiState(me = me, connected = true, serviceReady = true, members = members, chatPeer = peer,
                messages = listOf(FamilyChatMessage("intro", me, peer, "Thanks, Amina.", 1, 1000000),
                    FamilyChatMessage("one", peer, me, "Assalamu alaikum! How is everyone at home?", 2, 1060000),
                    FamilyChatMessage("plan", peer, me, "We’re getting the family together tomorrow evening. Mum and Dad would love to see you. Can you come over after Maghrib?", 3, 1120000),
                    FamilyChatMessage("two", me, peer, "Wa alaikum assalam. All well, alhamdulillah 🌿", 4, 1180000),
                    FamilyChatMessage("three", me, peer, "We’d love to come. What time?", 5, 1190000),
                    FamilyChatMessage("four", peer, me, "After Maghrib, insha’Allah.", 6, 4000000),
                     FamilyChatMessage("five", me, peer, "I’ll call you when we leave.", 7, 4050000)), chatDraft = "")
    }
    @Test fun chatLight() {
        compose.setContent { NiaTheme(themeBrand = ThemeBrand.COASTAL, disableDynamicTheming = true) {
            FamilyScreenContent(chatPreviewState())
        } }
        compose.onNodeWithText("Assalamu alaikum! How is everyone at home?").assertExists()
        compose.onNodeWithContentDescription("Send message").assertExists()
        capture("chat-light")
    }
    @Test fun chatDark() {
        compose.setContent { NiaTheme(darkTheme = true, disableDynamicTheming = true) {
            FamilyScreenContent(chatPreviewState())
        } }
        compose.onNodeWithContentDescription("Send message").assertExists()
        capture("chat-dark")
    }
    @Test fun chatProfileDarkUsesTheFullContactScreen() {
        val peer = members.first().jid
        compose.setContent { NiaTheme(darkTheme = true) {
            FamilyScreenContent(FamilyUiState(me = "omar@xmpp.starception.com", connected = true,
                serviceReady = true, members = members, chatPeer = peer))
        } }
        compose.onNodeWithContentDescription("Contact details").performClick()
        compose.onNodeWithText("FAMILY ACCOUNT").assertExists()
        compose.onNodeWithText(peer).assertExists()
        capture("chat-contact-profile-dark")
    }
    @Test fun familyNavigationSwitchesBetweenChatsAndContactDetails() {
        render(false, true)
        compose.onNodeWithContentDescription("Family contacts").performClick()
        compose.onNodeWithText("Contacts").assertExists()
        compose.onNodeWithText("Amina Khan").performClick()
        compose.onNodeWithText("FAMILY ACCOUNT").assertExists()
        compose.onNodeWithContentDescription("Back to family").performClick()
        compose.onNodeWithContentDescription("Family chats").performClick()
        compose.onNodeWithText("Chats").assertExists()
    }
    @Test fun invitationIsACompactDismissibleSheet() {
        render(false, true)
        compose.onNodeWithContentDescription("Invite a member").performClick()
        compose.onNodeWithText("Invite family").assertExists()
        capture("invitation-light")
        compose.onNodeWithContentDescription("Close invitation").performClick()
        compose.onNodeWithText("Invite family").assertDoesNotExist()
    }
    @Test fun transientReconnectKeepsTheFamilyPageInsteadOfTheLoginForm() {
        compose.setContent { NiaTheme {
            FamilyScreenContent(FamilyUiState(reconnecting = true, busy = true, members = members,
                error = "Reconnecting to Family…"))
        } }
        compose.onNodeWithText("Connect with family").assertDoesNotExist()
        compose.onNodeWithText("Amina Khan").assertExists()
        compose.onNodeWithText("Online").assertDoesNotExist()
        compose.onNodeWithText("Connecting your family…").assertExists()
    }
    @Test fun conversationsDark() {
        val peer = members[0].jid
        compose.setContent { NiaTheme(darkTheme = true, themeBrand = ThemeBrand.COASTAL, disableDynamicTheming = true) {
            FamilyScreenContent(FamilyUiState(me = "omar@xmpp.starception.com", connected = true, serviceReady = true, members = members,
                unreadCount = 2, conversations = listOf(FamilyConversation(peer, 2,
                    FamilyChatMessage("one", peer, "omar@xmpp.starception.com", "Assalamu alaikum! Are you free?", 1, 1000000)))))
        } }
        compose.onNodeWithText("Assalamu alaikum! Are you free?").assertExists()
        capture("conversations-dark")
    }
    @Test fun notificationInboxIncludesAllThreeKinds() {
        compose.setContent { NiaTheme {
            FamilyScreenContent(FamilyUiState(connected = true, serviceReady = true, members = members, unreadCount = 3,
                notifications = listOf(FamilyNotice("invite", "invitation", members[0].jid, 1, true, 1000000),
                    FamilyNotice("chat", "message", members[1].jid, 1, true, 1000000), FamilyNotice("missed", "missed_call", members[2].jid, 1, true, 1000000))))
        } }
        compose.onNodeWithContentDescription("Family notifications").performClick()
        compose.onNodeWithText("Invited you to join their family").assertExists()
        compose.onNodeWithText("New message").assertExists()
        compose.onNodeWithText("Missed call").assertExists()
        capture("notifications-light")
    }
    @Test fun activeVideoShowsRouteAndBothFeeds() {
        compose.setContent { NiaTheme(darkTheme = true) {
            FamilyCallContent(FamilyUiState(me = "omar@xmpp.starception.com", mediaConnected = true, connectionRoute = "relay",
                call = FamilyCall("video-fixture", "amina.khan@xmpp.starception.com", "omar@xmpp.starception.com", "video", "active", 2, Long.MAX_VALUE, true)),
                remoteReady = true, localReady = true, remoteVideo = { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { androidx.compose.material3.Text("Remote video", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface) } },
                localVideo = { androidx.compose.material3.Text("Local video", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface) })
        } }
        compose.onNodeWithText("TURN relay").assertExists()
        compose.onNodeWithText("Remote video").assertExists()
        compose.onNodeWithText("Local video").assertExists()
        compose.onNodeWithContentDescription("End call").assertExists()
        capture("active-video-dark")
    }
    @Test fun videoControlsKeepTheirActionsAndDisableFlipWhenCameraIsOff() {
        var muted = 0; var ended = 0; var camera = 0
        compose.setContent { NiaTheme {
            FamilyCallContent(FamilyUiState(me = "omar@xmpp.starception.com", camera = false,
                call = FamilyCall("video-fixture", "amina@xmpp.starception.com", "omar@xmpp.starception.com", "video", "active", 2, Long.MAX_VALUE, true)),
                actions = FamilyCallActions(mute = { muted++ }, camera = { camera++ }, hangup = { ended++ }))
        } }
        compose.onNodeWithContentDescription("Flip").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Mute").performClick()
        compose.onNodeWithContentDescription("Camera").performClick()
        compose.runOnIdle { kotlin.test.assertEquals(1, muted); kotlin.test.assertEquals(1, camera); kotlin.test.assertEquals(0, ended) }
        compose.onNodeWithContentDescription("End call").performClick()
        compose.runOnIdle { kotlin.test.assertEquals(1, ended) }
    }
    @Test fun activeVideoLight() {
        compose.setContent { NiaTheme(darkTheme = false) {
            FamilyCallContent(FamilyUiState(me = "omar@xmpp.starception.com", mediaConnected = true, connectionRoute = "relay",
                call = FamilyCall("video-fixture", "amina.khan@xmpp.starception.com", "omar@xmpp.starception.com", "video", "active", 2, Long.MAX_VALUE, true)),
                remoteReady = true, localReady = true, remoteVideo = { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { androidx.compose.material3.Text("Remote video", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface) } },
                localVideo = { androidx.compose.material3.Text("Local video", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface) })
        } }
        compose.onNodeWithText("TURN relay").assertExists()
        compose.onNodeWithText("Remote video").assertExists()
        compose.onNodeWithText("Local video").assertExists()
        compose.onNodeWithContentDescription("End call").assertExists()
        capture("active-video-light")
    }
    @Test fun persistentSearchFiltersAndCanBeCleared() {
        render(false, true)
        compose.onNodeWithContentDescription("Search family").performTextInput("Amina")
        compose.onNodeWithText("Amina Khan").assertExists()
        compose.onNodeWithText("Omar Hassan").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNodeWithText("Omar Hassan").assertExists()
    }
}
