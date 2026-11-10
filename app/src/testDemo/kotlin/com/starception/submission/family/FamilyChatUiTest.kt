package com.starception.submission.family

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import com.starception.submission.core.designsystem.theme.NiaTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.rules.TemporaryFolder
import android.graphics.BitmapFactory
import com.starception.submission.core.model.data.ThemeBrand
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w390dp-h844dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class FamilyChatUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val images = TemporaryFolder()
    private val peer = "amina.khan@xmpp.starception.com"
    private fun state() = FamilyUiState(
        me = "omar@xmpp.starception.com", connected = true, serviceReady = true,
        chatPeer = peer, members = listOf(FamilyMember(peer, "accepted", "online")),
        chatDraft = "Assalamu alaikum",
    )

    @Test fun signedOutLandingHasAVisibleWorkingBackButton() {
        var exits = 0
        compose.setContent { NiaTheme { FamilyScreenContent(FamilyUiState(), FamilyActions(exit = { exits++ })) } }
        compose.onNodeWithContentDescription("Back to app").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun signedInFamilyHasAVisibleWorkingBackButton() {
        var exits = 0
        compose.setContent { NiaTheme { FamilyScreenContent(state().copy(chatPeer = null), FamilyActions(exit = { exits++ })) } }
        compose.onNodeWithContentDescription("Back to app").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun signedOutLandingKeepsBackAndSignUpReachableWithLargeText() {
        compose.setContent { NiaTheme {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) {
                FamilyScreenContent(FamilyUiState())
            }
        } }
        compose.onNodeWithContentDescription("Back to app").assertIsDisplayed()
        compose.onNodeWithText("New to Family? Create an account").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Confirm password").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to app").assertIsDisplayed()
    }

    @Test fun chatInfoOpensTheFullProfileAndBackPreservesTheDraft() {
        var closed = false
        compose.setContent { NiaTheme(themeBrand = ThemeBrand.COASTAL, disableDynamicTheming = true) {
            FamilyScreenContent(state(), FamilyActions(closeChat = { closed = true }))
        } }
        compose.onNodeWithContentDescription("Contact details").performClick()
        compose.onNodeWithText("FAMILY ACCOUNT").assertIsDisplayed()
        compose.onNodeWithText(peer).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to conversation").performClick()
        compose.onNodeWithText("Assalamu alaikum").assertIsDisplayed()
        compose.runOnIdle { assertEquals(false, closed) }
    }

    @Test fun profileMessageReturnsToTheExistingConversationAndCallsUseTheSamePeer() {
        var called: String? = null
        var openedAnotherChat = false
        compose.setContent { NiaTheme {
            FamilyScreenContent(state(), FamilyActions(voice = { called = it }, openChat = { openedAnotherChat = true }))
        } }
        compose.onNodeWithContentDescription("Open profile for Amina Khan").performClick()
        compose.onNodeWithText("Message").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Voice call").performClick()
        compose.runOnIdle { assertEquals(peer, called); assertEquals(false, openedAnotherChat) }
    }

    @Test fun sendButtonAndKeyboardRespectSendingAndConnectionState() {
        val uiState = mutableStateOf(state())
        var sends = 0
        compose.setContent { NiaTheme {
            FamilyScreenContent(uiState.value, FamilyActions(sendMessage = { sends++ }))
        } }
        compose.onNodeWithContentDescription("Message").performImeAction()
        compose.runOnIdle { assertEquals(1, sends); uiState.value = state().copy(chatSending = true) }
        compose.onNodeWithContentDescription("Send message").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Message").performImeAction()
        compose.runOnIdle { assertEquals(1, sends); uiState.value = state().copy(connected = false, serviceReady = false) }
        compose.onNodeWithContentDescription("Send message").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Voice call").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Message").performImeAction()
        compose.runOnIdle { assertEquals(1, sends) }
    }

    @Test fun darkChatKeepsTheSyncErrorAndDraftVisibleWithWorkingRetry() {
        var retries = 0
        compose.setContent { NiaTheme(darkTheme = true) {
            FamilyScreenContent(state().copy(chatError = "Could not sync this conversation. Your draft is still here. Please retry."),
                FamilyActions(retryMessages = { retries++ }))
        } }
        compose.onNodeWithText("Could not sync this conversation. Your draft is still here. Please retry.").assertIsDisplayed()
        compose.onNodeWithText("Assalamu alaikum").assertIsDisplayed()
        compose.onNodeWithText("Refresh").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun disconnectedProfileCannotShowStaleOnlinePresenceOrPlaceCalls() {
        compose.setContent { NiaTheme {
            FamilyScreenContent(state().copy(serviceReady = false))
        } }
        compose.onNodeWithContentDescription("Contact details").performClick()
        compose.onNodeWithText("Online").assertDoesNotExist()
        compose.onNodeWithText("Status unavailable").assertIsDisplayed()
        compose.onNodeWithContentDescription("Voice call").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Video call").assertIsNotEnabled()
    }

    @Test fun largerTextProfileCanScrollToActionsAndReturnToTheDraft() {
        compose.setContent { NiaTheme(darkTheme = true) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                FamilyScreenContent(state())
            }
        } }
        compose.onNodeWithContentDescription("Contact details").performClick()
        compose.onNodeWithText(peer).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Message").performScrollTo().performClick()
        compose.onNodeWithText("Assalamu alaikum").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun narrowVideoControlsDoNotOverlapAndKeepTheirRealActions() {
        val events = mutableListOf<String>()
        compose.setContent { NiaTheme {
            FamilyCallContent(state().copy(call = FamilyCall("layout-test", peer,
                "omar@xmpp.starception.com", "video", "active", 2, Long.MAX_VALUE, true)),
                actions = FamilyCallActions(mute = { events += "mute" }, speaker = { events += "speaker" },
                    camera = { events += "camera" }, flip = { events += "flip" }, hangup = { events += "end" }))
        } }
        val labels = listOf("Camera", "Mute", "Flip", "Speaker", "End call")
        val bounds = labels.map { label ->
            compose.onNodeWithContentDescription(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        for (i in bounds.indices) for (j in i + 1 until bounds.size) {
            assertFalse("${labels[i]} overlaps ${labels[j]}", bounds[i].overlaps(bounds[j]))
        }
        labels.forEach { compose.onNodeWithContentDescription(it).performClick() }
        compose.runOnIdle { assertEquals(listOf("camera", "mute", "flip", "speaker", "end"), events) }
    }

    @Test fun keyboardInsetKeepsTheComposerImmediatelyAboveTheKeyboard() {
        compose.setContent { NiaTheme { FamilyDesign {
            Box(Modifier.fillMaxWidth().height(640.dp)) {
                FamilyChatContent(state(), FamilyActions(), contentInsets = WindowInsets(top = 24.dp, bottom = 300.dp))
            }
        } } }
        val send = compose.onNodeWithContentDescription("Send message").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(330f, send.bottom, 1f) // 640 - 300 keyboard - 10 composer margin.
        compose.onNodeWithContentDescription("Message").assertIsDisplayed()
    }

    @Test fun activityRequestsResizeInsteadOfPanningTheWholeConversation() {
        compose.setContent { NiaTheme { FamilyScreenContent(state()) } }
        compose.runOnIdle {
            val info = compose.activity.packageManager.getActivityInfo(
                android.content.ComponentName(compose.activity, com.starception.submission.MainActivity::class.java), 0)
            assertTrue(info.softInputMode and android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST ==
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    @Test fun lightBottomBarDoesNotCastAShadowOverTheGestureArea() = assertGestureAreaMatchesSurface(false)

    @Test fun darkBottomBarDoesNotCastAShadowOverTheGestureArea() = assertGestureAreaMatchesSurface(true)

    private fun assertGestureAreaMatchesSurface(dark: Boolean) {
        compose.setContent { NiaTheme(darkTheme = dark) { FamilyDesign {
            // An opposite parent colour exposes any unpainted navigation inset.
            Box(Modifier.fillMaxSize().background(Color.Magenta).testTag("familyCanvas")) {
                FamilyBottomBar(false, 0, {}, {}, {}, {}, {}, Modifier.align(Alignment.BottomCenter),
                    navigationInsets = WindowInsets(bottom = 32.dp))
            }
        } } }
        val image = images.newFile("gesture-area.png")
        compose.onNodeWithTag("familyCanvas").captureRoboImage(image.absolutePath,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        val pixels = requireNotNull(BitmapFactory.decodeFile(image.absolutePath))
        val expected = FamilyVisuals.canvas(dark).toArgb()
        for (y in pixels.height - 30 until pixels.height) {
            assertEquals("Gesture area should match the bar at y=$y", expected, pixels.getPixel(pixels.width / 4, y))
        }
    }
}
