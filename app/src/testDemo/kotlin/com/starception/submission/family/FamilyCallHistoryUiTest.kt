package com.starception.submission.family

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.starception.submission.core.designsystem.theme.NiaTheme
import dagger.hilt.android.testing.HiltTestApplication
import org.json.JSONObject
import org.junit.Assert.*
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
class FamilyCallHistoryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val me = "omar@xmpp.starception.com"
    private val peer = "amina.khan@xmpp.starception.com"
    private val id = "11111111-2222-3333-4444-555555555555"
    private fun json(outcome: String, kind: String = "voice", outgoing: Boolean = true): JSONObject = JSONObject()
        .put("id", "call:$id").put("from", if (outgoing) me else peer).put("to", if (outgoing) peer else me)
        .put("text", "Fallback call text").put("sequence", 1)
        .put("createdAt", java.time.Instant.parse("2026-11-10T15:18:00Z").toEpochMilli())
        .put("type", "call").put("call", JSONObject().put("id", id).put("kind", kind).put("outcome", outcome))

    private fun render(message: FamilyChatMessage, dark: Boolean = false) {
        compose.setContent { NiaTheme(darkTheme = dark) {
            FamilyScreenContent(FamilyUiState(me = me, connected = true, serviceReady = true, chatPeer = peer,
                members = listOf(FamilyMember(peer, "accepted")), messages = listOf(message)))
        } }
    }

    @Test fun answeredVideoCallShowsDirectionAndTheAuthoritativeDuration() {
        val value = json("answered", "video")
        value.getJSONObject("call").put("durationSeconds", 94)
        render(FamilyChatMessage.parse(value))
        compose.onNodeWithText("Video call").assertIsDisplayed()
        compose.onNodeWithText("Outgoing · Answered").assertIsDisplayed()
        compose.onNodeWithText("1m 34s", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Saved to server").assertDoesNotExist()
        compose.onRoot().captureFamilyPreview("chat-answered-video-call")
    }

    @Test fun incomingCancelledCallIsShownAsMissedWithoutAFakeDuration() {
        render(FamilyChatMessage.parse(json("cancelled", outgoing = false)), dark = true)
        compose.onNodeWithText("Voice call").assertIsDisplayed()
        compose.onNodeWithText("Incoming · Missed").assertIsDisplayed()
        compose.onNodeWithText("0s", substring = true).assertDoesNotExist()
        compose.onRoot().captureFamilyPreview("chat-missed-voice-call")
    }

    @Test fun answeredAudioCallWithDurationNeverDisplaysAVideoLabel() {
        val value = json("answered", "voice")
        value.getJSONObject("call").put("durationSeconds", 94)
        val message = FamilyChatMessage.parse(value)
        assertEquals("voice", message.call?.kind)
        assertEquals("Voice call · Answered", message.preview(me))
        render(message)
        compose.onNodeWithText("Voice call").assertIsDisplayed()
        compose.onNodeWithText("Video call").assertDoesNotExist()
        compose.onNodeWithText("1m 34s", substring = true).assertIsDisplayed()
    }

    @Test fun sameRecordHasParticipantSpecificConversationPreviews() {
        val message = FamilyChatMessage.parse(json("cancelled"))
        assertEquals("Voice call · Cancelled", message.preview(me))
        assertEquals("Voice call · Missed", message.preview(peer))
    }

    @Test fun malformedOrUnrelatedCallMetadataFallsBackToCompatibleText() {
        val wrongId = json("answered").put("id", "ordinary-message-id")
        assertNull(FamilyChatMessage.parse(wrongId).call)
        val unknown = json("unsupported")
        assertNull(FamilyChatMessage.parse(unknown).call)
        val invalidDuration = json("answered")
        invalidDuration.getJSONObject("call").put("durationSeconds", -1)
        assertNull(FamilyChatMessage.parse(invalidDuration).call)
        val ordinary = json("answered").put("type", "text")
        assertEquals("Fallback call text", FamilyChatMessage.parse(ordinary).preview(me))
    }

    @Test fun legacyTextMessagesRemainCompatible() {
        val value = json("answered").apply { remove("type"); remove("call") }
        val message = FamilyChatMessage.parse(value)
        assertNull(message.call)
        assertEquals("Fallback call text", message.text)
    }

    @Test fun missedCallAlertOpensTheCorrespondingUsersConversation() {
        var opened: String? = null
        compose.setContent { NiaTheme {
            FamilyScreenContent(FamilyUiState(me = me, connected = true, serviceReady = true,
                members = listOf(FamilyMember(peer, "accepted")), unreadCount = 1,
                notifications = listOf(FamilyNotice("missed", "missed_call", peer, 1, true, 1000000L))),
                FamilyActions(openChat = { opened = it }))
        } }
        compose.onNodeWithContentDescription("Family notifications").performClick()
        compose.onNodeWithText("Missed call").performClick()
        compose.runOnIdle { assertEquals(peer, opened) }
    }
}
