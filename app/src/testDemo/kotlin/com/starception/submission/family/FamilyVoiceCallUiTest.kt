package com.starception.submission.family

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.starception.submission.core.designsystem.theme.NiaTheme
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class FamilyVoiceCallUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val me = "omar@xmpp.starception.com"
    private val peer = "amina.khan@xmpp.starception.com"
    private fun state(stage: String = "active", incoming: Boolean = false) = FamilyUiState(
        me = me, connected = true, serviceReady = true, mediaConnected = stage == "active", connectionRoute = "direct",
        call = FamilyCall("voice-ui", if (incoming) peer else me, if (incoming) me else peer,
            "voice", stage, 1, Long.MAX_VALUE, true),
    )

    @Test fun outgoingVoiceHasTheNewCallerCardAndWorkingCancelOnly() {
        var cancelled = 0
        compose.setContent { NiaTheme(darkTheme = false) {
            FamilyCallContent(state("ringing"), actions = FamilyCallActions(hangup = { cancelled++ }))
        } }
        compose.onNodeWithText("Amina Khan").assertIsDisplayed()
        compose.onNodeWithText("Calling…").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mute").assertDoesNotExist()
        compose.onNodeWithContentDescription("Speaker").assertDoesNotExist()
        compose.onRoot().captureFamilyPreview("outgoing-voice-light")
        compose.onNodeWithContentDescription("Cancel").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, cancelled) }
    }

    @Test fun activeVoiceControlsKeepTheirRealActionsAndSelectedStates() {
        val ui = mutableStateOf(state())
        var ended = 0
        compose.setContent { NiaTheme(darkTheme = false) {
            FamilyCallContent(ui.value, elapsed = 94, actions = FamilyCallActions(
                mute = { ui.value = ui.value.copy(muted = !ui.value.muted) },
                speaker = { ui.value = ui.value.copy(speaker = !ui.value.speaker) }, hangup = { ended++ }))
        } }
        compose.onNodeWithText("01:34").assertIsDisplayed()
        compose.onRoot().captureFamilyPreview("active-voice-light")
        compose.onNodeWithContentDescription("Mute").performClick()
        compose.onNodeWithContentDescription("Unmute").assertIsSelected()
        compose.onNodeWithContentDescription("Speaker").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("End call").performClick()
        compose.runOnIdle { assertEquals(1, ended) }
    }

    @Test fun incomingVoiceAcceptAndDeclineRemainDistinct() {
        val events = mutableListOf<String>()
        compose.setContent { NiaTheme(darkTheme = true) {
            FamilyCallContent(state("ringing", incoming = true), actions = FamilyCallActions(
                answer = { events += "accept" }, hangup = { events += "decline" }))
        } }
        compose.onNodeWithText("Incoming voice call").assertIsDisplayed()
        compose.onNodeWithContentDescription("Accept").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Decline").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("accept", "decline"), events) }
    }

    @Test fun reconnectingDoesNotShowAFakeConnectedTimerAndKeepsTheErrorVisible() {
        compose.setContent { NiaTheme {
            FamilyCallContent(state().copy(mediaConnected = false, connectedAtElapsed = 1,
                callError = "Connection interrupted. Trying again…"), elapsed = 94)
        } }
        compose.onNodeWithText("Reconnecting…").assertIsDisplayed()
        compose.onNodeWithText("01:34").assertDoesNotExist()
        compose.onNodeWithText("Connection interrupted. Trying again…").assertIsDisplayed()
        compose.onNodeWithContentDescription("End call").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun narrowLargeTextControlsStayVisibleAndDoNotOverlap() {
        compose.setContent { NiaTheme {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) {
                FamilyCallContent(state(), elapsed = 94)
            }
        } }
        assertControlsDoNotOverlap()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-mdpi")
    fun landscapeUsesTwoPanelsWithoutOverlappingTheControls() {
        compose.setContent { NiaTheme { FamilyCallContent(state(), elapsed = 94) } }
        compose.onNodeWithText("Amina Khan").assertIsDisplayed()
        assertControlsDoNotOverlap()
    }

    private fun assertControlsDoNotOverlap() {
        val labels = listOf("Mute", "Speaker", "End call")
        val bounds = labels.map { compose.onNodeWithContentDescription(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        for (i in bounds.indices) for (j in i + 1 until bounds.size) {
            assertFalse("${labels[i]} overlaps ${labels[j]}", bounds[i].overlaps(bounds[j]))
        }
    }
}
