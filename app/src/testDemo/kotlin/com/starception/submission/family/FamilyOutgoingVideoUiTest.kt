package com.starception.submission.family

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.starception.submission.core.designsystem.theme.NiaTheme
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
class FamilyOutgoingVideoUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun state() = FamilyUiState(me = "omar@xmpp.starception.com", camera = true,
        call = FamilyCall("outgoing-video", "omar@xmpp.starception.com", "amina.khan@xmpp.starception.com",
            "video", "ringing", 1, Long.MAX_VALUE, true))

    @Test fun outgoingPreviewUsesTheRoundedCardAndShelfBeforeTheCallIsAnswered() {
        val events = mutableListOf<String>()
        compose.setContent { NiaTheme(darkTheme = false) {
            FamilyCallContent(state(), localReady = true,
                actions = FamilyCallActions(flip = { events += "flip" }, hangup = { events += "cancel" },
                    mute = { events += "mute" }, camera = { events += "camera" }),
                localVideo = {
                    Box(Modifier.fillMaxSize().testTag("localVideoFixture").background(Brush.verticalGradient(
                        listOf(Color(0xFF527A72), Color(0xFFB8C8B4), Color(0xFF30473F)))),
                        contentAlignment = Alignment.Center) { Text("Local camera fixture", color = Color.White) }
                })
        } }
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("familyVideoMainCard").fetchSemanticsNode().boundsInRoot
        val preview = compose.onAllNodesWithTag("localVideoFixture")[0].fetchSemanticsNode().boundsInRoot
        compose.onAllNodesWithTag("localVideoFixture").assertCountEquals(1)
        assertTrue("Preview must not be full bleed", card.left > root.left && card.right < root.right)
        assertTrue(preview.left >= card.left && preview.right <= card.right && preview.bottom <= card.bottom)
        compose.onNodeWithTag("familyVideoShelf").assertIsDisplayed()
        val shelf = compose.onNodeWithTag("familyVideoShelf").fetchSemanticsNode().boundsInRoot
        assertTrue("Portrait controls must remain inside the video frame", shelf.bottom <= card.bottom)
        val cancel = compose.onNodeWithContentDescription("Cancel").fetchSemanticsNode().boundsInRoot
        assertTrue("Cancel must not protrude above the toolbar", cancel.top >= shelf.top)
        compose.onNodeWithText("Your camera preview").assertIsDisplayed()
        compose.onNodeWithText("Calling…").assertIsDisplayed()
        compose.onNodeWithContentDescription("Camera").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Mute").assertIsDisplayed().performClick()
        compose.onRoot().captureFamilyPreview("outgoing-video-preview-light")
        compose.onNodeWithContentDescription("Flip").performClick()
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.runOnIdle { assertEquals(listOf("camera", "mute", "flip", "cancel"), events) }
    }

    @Test fun startingCameraDoesNotFallBackToTheOldFullScreenLayout() {
        compose.setContent { NiaTheme(darkTheme = true) { FamilyCallContent(state(), localReady = false) } }
        compose.onNodeWithTag("familyVideoMainCard").assertIsDisplayed()
        compose.onNodeWithText("Starting your camera…").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancel").assertIsDisplayed()
        compose.onRoot().captureFamilyPreview("outgoing-video-starting-dark")
    }

    @Test fun cameraOffKeepsTheSameCardAndDisablesFlip() {
        compose.setContent { NiaTheme {
            FamilyCallContent(state().copy(camera = false), localReady = true,
                localVideo = { Text("Camera must not render") })
        } }
        compose.onNodeWithTag("familyVideoMainCard").assertIsDisplayed()
        compose.onNodeWithText("Your camera is off").assertIsDisplayed()
        compose.onNodeWithText("Camera must not render").assertDoesNotExist()
        compose.onNodeWithContentDescription("Flip").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Cancel").assertIsDisplayed()
    }

    @Test fun answeringSwitchesFromLocalPreviewToRemoteVideoWithoutChangingTheFrame() {
        val ui = mutableStateOf(state())
        compose.setContent { NiaTheme {
            FamilyCallContent(ui.value, elapsed = 94, localReady = true, remoteReady = true,
                localVideo = { Text("Local camera fixture") }, remoteVideo = { Text("Remote camera fixture") })
        } }
        compose.onAllNodesWithText("Local camera fixture").assertCountEquals(1)
        compose.onNodeWithText("Remote camera fixture").assertDoesNotExist()
        compose.runOnIdle { ui.value = ui.value.copy(call = ui.value.call?.copy(stage = "active"), mediaConnected = true) }
        compose.onNodeWithTag("familyVideoMainCard").assertIsDisplayed()
        compose.onNodeWithText("Remote camera fixture").assertExists()
        compose.onNodeWithText("01:34").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancel").assertDoesNotExist()
        compose.onNodeWithContentDescription("End call").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mute").assertIsDisplayed()
    }

    @Test fun cameraAndMicrophoneChoicesRemainSelectedWhenTheCallIsAnswered() {
        val ui = mutableStateOf(state())
        compose.setContent { NiaTheme {
            FamilyCallContent(ui.value, localReady = true, remoteReady = true,
                actions = FamilyCallActions(
                    camera = { ui.value = ui.value.copy(camera = !ui.value.camera) },
                    mute = { ui.value = ui.value.copy(muted = !ui.value.muted) }))
        } }
        compose.onNodeWithContentDescription("Camera").performClick().assertIsSelected()
        compose.onNodeWithText("Your camera is off").assertIsDisplayed()
        compose.onNodeWithContentDescription("Flip").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Mute").performClick()
        compose.onNodeWithContentDescription("Unmute").assertIsSelected()
        compose.runOnIdle { ui.value = ui.value.copy(call = ui.value.call?.copy(stage = "active"), mediaConnected = true) }
        compose.onNodeWithContentDescription("Camera").assertIsSelected()
        compose.onNodeWithContentDescription("Unmute").assertIsSelected()
        compose.onNodeWithContentDescription("Flip").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-mdpi")
    fun landscapeUsesFramedVideoAndKeepsTheShelfBesideIt() {
        compose.setContent { NiaTheme { FamilyCallContent(state(), localReady = false) } }
        val card = compose.onNodeWithTag("familyVideoMainCard").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val shelf = compose.onNodeWithTag("familyVideoShelf").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Landscape shelf must sit beside video", shelf.left >= card.right)
        compose.onNodeWithContentDescription("Cancel").assertIsDisplayed()
    }

    @Test fun incomingVideoDoesNotRenderCameraFramesBeforeAcceptance() {
        var answers = 0
        val incoming = state().copy(me = "amina.khan@xmpp.starception.com")
        compose.setContent { NiaTheme {
            FamilyCallContent(incoming, localReady = true, remoteReady = true,
                actions = FamilyCallActions(answer = { answers++ }),
                localVideo = { Text("Private local camera") }, remoteVideo = { Text("Private remote camera") })
        } }
        compose.onNodeWithText("Private local camera").assertDoesNotExist()
        compose.onNodeWithText("Private remote camera").assertDoesNotExist()
        compose.onNodeWithContentDescription("Camera").assertDoesNotExist()
        compose.onNodeWithText("Accept").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, answers) }
        compose.onNodeWithContentDescription("Decline").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun narrowLargeTextKeepsOutgoingControlsSeparate() {
        compose.setContent { NiaTheme {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) {
                FamilyCallContent(state(), localReady = false)
            }
        } }
        val flip = compose.onNodeWithContentDescription("Flip").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val cancel = compose.onNodeWithContentDescription("Cancel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertFalse("Flip and cancel must not overlap", flip.overlaps(cancel))
        val cameraMessage = compose.onNodeWithText("Starting your camera…").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val shelf = compose.onNodeWithTag("familyVideoShelf").fetchSemanticsNode().boundsInRoot
        assertTrue("Camera status must not hide underneath the shelf", cameraMessage.bottom <= shelf.top)
        compose.onRoot().captureFamilyPreview("outgoing-video-large-text")
    }
}
