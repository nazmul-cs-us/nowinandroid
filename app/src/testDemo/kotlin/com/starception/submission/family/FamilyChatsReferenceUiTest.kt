package com.starception.submission.family

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
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
class FamilyChatsReferenceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val me = "omar@xmpp.starception.com"
    private val members = listOf("amina.khan", "yusuf.ali", "maryam.hassan", "khalid.ahmed", "sara.malik", "ibrahim.ali")
        .mapIndexed { i, name -> FamilyMember("$name@xmpp.starception.com", "accepted", if (i == 0) "online" else "offline") }
    private fun state() = FamilyUiState(me = me, connected = true, serviceReady = true, members = members,
        unreadCount = 2, conversations = members.mapIndexed { i, member ->
            FamilyConversation(member.jid, if (i == 0) 2 else 0, FamilyChatMessage("message-$i", member.jid, me,
                listOf("We’ll all be there, insha’Allah.", "See you after Maghrib.", "How is everyone at home?",
                    "I’ll call you this evening.", "JazakAllah khair!", "Made it home safely.")[i], 1, 1000000L + i * 60000))
        })

    @Test fun lightChatsPreviewKeepsUnreadBadgesAndRealChatActions() {
        var opened: String? = null
        compose.setContent { NiaTheme(darkTheme = false) {
            FamilyScreenContent(state(), FamilyActions(openChat = { opened = it }))
        } }
        compose.onNodeWithContentDescription("Family chats").assertIsSelected()
        compose.onNodeWithContentDescription("2 unread messages").assertIsDisplayed()
        compose.onRoot().captureFamilyPreview("chats-reference-light")
        compose.onNodeWithText("Amina Khan").performClick()
        compose.runOnIdle { assertEquals(members.first().jid, opened) }
    }

    @Test fun darkChatsPreviewUsesTheSameHeaderAndNavigationStructure() {
        compose.setContent { NiaTheme(darkTheme = true) { FamilyScreenContent(state()) } }
        compose.onNodeWithText("Chats").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to app").assertIsDisplayed()
        compose.onRoot().captureFamilyPreview("chats-reference-dark")
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun largeTextStillAllowsBothTabsToBeSelected() {
        compose.setContent { NiaTheme {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) { FamilyScreenContent(state()) }
        } }
        compose.onNodeWithContentDescription("Family contacts").assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Family chats").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Back to app").assertIsDisplayed()
    }
}
