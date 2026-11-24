package com.starception.submission.settings.components

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h844dp-mdpi", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppGuideTest {
    @get:Rule val compose = createComposeRule()
    private class Store : AppGuideStore {
        val visits = mutableSetOf<GuideSection>()
        val seenTips = mutableSetOf<GuideSection>()
        var tips = true
        override fun visited(section: GuideSection) = section in visits
        override fun visit(section: GuideSection) { visits += section }
        override fun tipSeen(section: GuideSection) = section in seenTips
        override fun markTipSeen(section: GuideSection) { seenTips += section }
        override fun tipsEnabled() = tips
        override fun setTipsEnabled(enabled: Boolean) { tips = enabled }
    }
    @Before fun clearPersistedVisits() {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("deenly_app_guide", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun waitForTip() {
        compose.waitForIdle()
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        compose.mainClock.advanceTimeBy(1000)
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("appGuideTip").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun firstVisitTipDoesNotBlockThePageOrRepeatOnReturning() {
        val store = Store()
        var section by mutableStateOf(GuideSection.HOME)
        var taps = 0
        compose.setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp)) {
                Text("Home", style = MaterialTheme.typography.headlineLarge)
                Button(modifier = Modifier.appGuideAnchor(section), onClick = { taps++ }) { Text("Use the page") }
            }
            AppGuideHost(section, store = store)
        } } }
        waitForTip()
        compose.onAllNodes(isPopup()).assertCountEquals(0)
        compose.onNodeWithTag("appGuideTip").assertIsDisplayed()
        compose.onNodeWithText("Use the page").performClick()
        assertEquals(1, taps)
        compose.onNodeWithTag("appGuideTip").captureRoboImage("src/testDemo/screenshots/app_guide_tip_light.png")
        compose.onNodeWithTag("appGuideTipDismiss").performClick()
        compose.runOnIdle { section = GuideSection.SAVED }
        waitForTip()
        compose.runOnIdle { section = GuideSection.HOME }
        compose.waitForIdle()
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        compose.onNodeWithTag("appGuideTip").assertDoesNotExist()
        assertEquals(setOf(GuideSection.HOME, GuideSection.SAVED), store.visits)
    }

    @Test fun tipSitsAboveItsControlAndLeavesTheControlTappable() {
        val store = Store()
        var taps = 0
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) {
            Button(modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                .appGuideAnchor(GuideSection.SAVED), onClick = { taps++ }) { Text("Open Saved") }
            AppGuideHost(GuideSection.SAVED, store = store)
        } } }
        waitForTip()
        val control = compose.onNodeWithText("Open Saved")
        val tip = compose.onNodeWithTag("appGuideTip").fetchSemanticsNode().boundsInRoot
        assertTrue(tip.bottom <= control.fetchSemanticsNode().boundsInRoot.top)
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/app_guide_spotlight.png")
        control.performTouchInput { click() }
        assertEquals(1, taps)
    }

    @Test fun waitsForTheControlBeforeShowingTheTip() {
        val store = Store()
        var showControl by mutableStateOf(false)
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) {
            if (showControl) Button(
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter).appGuideAnchor(GuideSection.SEARCH),
                onClick = {},
            ) { Text("Search") }
            AppGuideHost(GuideSection.SEARCH, store = store)
        } } }
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        compose.onNodeWithTag("appGuideTip").assertDoesNotExist()
        assertFalse(store.tipSeen(GuideSection.SEARCH))
        compose.runOnIdle { showControl = true }
        waitForTip()
        compose.onNodeWithTag("appGuideTip").assertIsDisplayed()
        compose.onNodeWithText("Show steps").assertDoesNotExist()
        compose.runOnIdle { showControl = false }
        compose.onNodeWithTag("appGuideTip").assertDoesNotExist()
    }

    @Test fun existingOptOutStillPreventsTips() {
        val store = Store().apply { tips = false }
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) {
            Box(Modifier.size(48.dp).appGuideAnchor(GuideSection.SEARCH))
            AppGuideHost(GuideSection.SEARCH, store = store)
        } } }
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        compose.onNodeWithTag("appGuideTip").assertDoesNotExist()
        assertTrue(store.visited(GuideSection.SEARCH))
    }

    @Test fun inactiveScreensAreNotCountedAsVisits() {
        val store = Store()
        var enabled by mutableStateOf(false)
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) {
            Box(Modifier.size(48.dp).appGuideAnchor(GuideSection.HOME))
            AppGuideHost(GuideSection.HOME, enabled, store)
        } } }
        compose.waitForIdle()
        assertTrue(store.visits.isEmpty())
        compose.runOnIdle { enabled = true }
        waitForTip()
        assertEquals(setOf(GuideSection.HOME), store.visits)
    }

    @Test fun nestedSectionsNeverStackMultipleTips() {
        val store = Store()
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) {
            Box(Modifier.size(48.dp).appGuideAnchor(GuideSection.SETTINGS))
            Box(Modifier.size(48.dp).appGuideAnchor(GuideSection.RECITERS))
            AppGuideHost(GuideSection.SETTINGS, store = store)
            AppGuideHost(GuideSection.RECITERS, store = store)
        } } }
        waitForTip()
        compose.onAllNodesWithTag("appGuideTip").assertCountEquals(1)
        assertEquals(setOf(GuideSection.SETTINGS, GuideSection.RECITERS), store.visits)
    }

    @Test fun existingHomeUsersSeeTheNewTuneScheduleTip() {
        val preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("deenly_app_guide", Context.MODE_PRIVATE)
        preferences.edit().putBoolean("tip_seen_HOME", true).commit()
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) {
            Button(onClick = {}, modifier = Modifier.appGuideAnchor(GuideSection.HOME)) {
                Text("Tune schedule")
            }
            AppGuideHost(GuideSection.HOME)
        } } }
        waitForTip()
        compose.onNodeWithText(GuideSection.HOME.tip).assertIsDisplayed()
        assertTrue(preferences.getBoolean("tip_seen_HOME_TUNE_SCHEDULE", false))
    }

    @Test fun persistedVisitPreventsAnotherTipAfterCompositionRecreation() {
        var generation by mutableIntStateOf(0)
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) {
            Box(Modifier.size(48.dp).appGuideAnchor(GuideSection.FAMILY))
            key(generation) { AppGuideHost(GuideSection.FAMILY) }
        } } }
        waitForTip()
        compose.runOnIdle { generation++ }
        compose.waitForIdle()
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        compose.onNodeWithTag("appGuideTip").assertDoesNotExist()
    }
}
