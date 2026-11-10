package com.starception.submission.family

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
class FamilySettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val me = "omar.hassan@xmpp.starception.com"
    private fun state() = FamilyUiState(me = me, connected = true, serviceReady = true,
        members = listOf(FamilyMember("amina.khan@xmpp.starception.com", "accepted"),
            FamilyMember("maryam.ali@xmpp.starception.com", "incoming")))

    private fun open(dark: Boolean = false, actions: FamilyActions = FamilyActions()) {
        compose.setContent { NiaTheme(darkTheme = dark) { FamilyScreenContent(state(), actions) } }
        compose.onNodeWithContentDescription("Family settings").performClick()
    }

    @Test fun settingsLightShowsTheActualAccountAndGroupedActions() {
        open()
        compose.onNodeWithText(me).assertIsDisplayed()
        compose.onNodeWithText("Connected").assertIsDisplayed()
        compose.onNodeWithText("STAY CONNECTED").assertIsDisplayed()
        compose.onRoot().captureFamilyPreview("settings-light")
        compose.onNodeWithContentDescription("Close settings").performClick()
        compose.onNodeWithText("Family settings").assertDoesNotExist()
    }

    @Test fun settingsDarkUsesTheSameLayout() {
        open(dark = true)
        compose.onNodeWithText(me).assertIsDisplayed()
        compose.onRoot().captureFamilyPreview("settings-dark")
    }

    @Test fun settingsActionsDismissAndDispatchTheirExistingCallbacks() {
        val events = mutableListOf<String>()
        open(actions = FamilyActions(webHelper = { events += "alerts" }, refresh = { events += "refresh" },
            exit = { events += "exit" }, signOut = { events += "signOut" }))
        for ((label, event) in listOf("Temporary call alerts" to "alerts", "Refresh family" to "refresh",
            "Back to app" to "exit", "Sign out" to "signOut")) {
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.onNodeWithText("Family settings").assertDoesNotExist()
            compose.runOnIdle { assertEquals(event, events.last()) }
            compose.onNodeWithContentDescription("Family settings").performClick()
        }
        compose.runOnIdle { assertEquals(listOf("alerts", "refresh", "exit", "signOut"), events) }
    }

    @Test fun invitationsOpenTheExistingInvitationList() {
        open()
        compose.onNodeWithText("Invitations").performScrollTo().performClick()
        compose.onNodeWithText("Family settings").assertDoesNotExist()
        compose.onNodeWithText("Invitations").assertIsDisplayed()
        compose.onNodeWithText("Maryam Ali").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun largeTextCanScrollToSignOutWhileCloseStaysVisible() {
        compose.setContent { NiaTheme {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) { FamilyScreenContent(state()) }
        } }
        compose.onNodeWithContentDescription("Family settings").performClick()
        compose.onNodeWithTag("familySettingsList").performScrollToNode(hasText("Sign out"))
        compose.onNodeWithText("Sign out").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close settings").assertIsDisplayed().performClick()
        compose.onNodeWithText("Family settings").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-mdpi")
    fun landscapeCanReachAllActions() {
        open()
        compose.onNodeWithTag("familySettingsList").performScrollToNode(hasText("Sign out"))
        compose.onNodeWithText("Sign out").assertIsDisplayed()
        compose.onNodeWithTag("familySettingsList").performScrollToNode(hasText("Invitations"))
        compose.onNodeWithText("Invitations").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close settings").assertIsDisplayed()
    }
}
