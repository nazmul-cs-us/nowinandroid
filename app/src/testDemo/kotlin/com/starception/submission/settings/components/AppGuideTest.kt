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
import org.robolectric.RuntimeEnvironment
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
                Button(onClick = { taps++ }) { Text("Use the page") }
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
        control.performTouchInput { click() }
        assertEquals(1, taps)
    }

    @Test fun replayPrioritizesUnvisitedSectionsWithoutInventingVisits() {
        val store = Store().apply { visits += GuideSection.HOME; visits += GuideSection.SETTINGS; tips = false }
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) { AppGuideSettings(store) } }
        compose.onNodeWithTag("appGuideReplay").performClick()
        compose.onNodeWithText("Make time for today's reading").assertIsDisplayed()
        compose.onNodeWithText("Not explored yet").assertIsDisplayed()
        compose.onNodeWithTag("appGuideBack").assertIsNotEnabled()
        compose.onNodeWithTag("appGuideTour").captureRoboImage("src/testDemo/screenshots/app_guide_tour_dark.png")
        repeat(GuideSection.entries.size) { compose.onNodeWithTag("appGuideNext").assertIsDisplayed().performClick() }
        compose.onNodeWithTag("appGuideTour").assertDoesNotExist()
        assertEquals(setOf(GuideSection.HOME, GuideSection.SETTINGS), store.visits)
        compose.onNodeWithTag("appGuideReplay").performClick()
        compose.onNodeWithTag("appGuideSkip").performClick()
        compose.onNodeWithTag("appGuideTour").assertDoesNotExist()
    }

    @Test @Config(qualifiers = "w640dp-h360dp-land-mdpi")
    fun largeTextLandscapeKeepsTourNavigationVisible() {
        RuntimeEnvironment.setFontScale(1.5f)
        compose.setContent { MaterialTheme { AppGuideSettings(Store()) } }
        compose.onNodeWithTag("appGuideReplay").assertIsDisplayed().performClick()
        compose.onNodeWithTag("appGuideNext").assertIsDisplayed()
        compose.onNodeWithTag("appGuideSkip").assertIsDisplayed()
        compose.onNodeWithTag("appGuideTour").captureRoboImage("src/testDemo/screenshots/app_guide_tour_landscape.png")
        compose.onNodeWithTag("appGuideNext").performClick()
        compose.onNodeWithTag("appGuideBack").assertIsEnabled().performClick()
        compose.onNodeWithTag("appGuideSkip").performClick()
    }

    @Test fun disabledTipsStillAllowReplayAndTrackRealVisits() {
        val store = Store().apply { tips = false }
        compose.setContent { MaterialTheme { Column { AppGuideHost(GuideSection.SEARCH, store = store); AppGuideSettings(store) } } }
        compose.waitForIdle()
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        compose.onNodeWithTag("appGuideTip").assertDoesNotExist()
        assertTrue(store.visited(GuideSection.SEARCH))
        compose.onNodeWithTag("appGuideTipsSwitch").performClick()
        assertTrue(store.tips)
        compose.onNodeWithTag("appGuideReplay").performClick()
        compose.onNodeWithTag("appGuideTour").assertIsDisplayed()
    }

    @Test fun aReplayedTipIsNotRepeatedOnTheFirstActualVisit() {
        val store = Store()
        var section by mutableStateOf<GuideSection?>(null)
        compose.setContent { MaterialTheme { Column {
            AppGuideHost(section, store = store)
            AppGuideSettings(store)
        } } }
        compose.onNodeWithTag("appGuideReplay").performClick()
        repeat(GuideSection.entries.size) { compose.onNodeWithTag("appGuideNext").performClick() }
        assertTrue(store.visits.isEmpty())
        assertEquals(GuideSection.entries.toSet(), store.seenTips)
        compose.runOnIdle { section = GuideSection.SEARCH }
        compose.waitForIdle()
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        compose.onNodeWithTag("appGuideTip").assertDoesNotExist()
        assertEquals(setOf(GuideSection.SEARCH), store.visits)
    }

    @Test fun inactiveScreensAreNotCountedAsVisits() {
        val store = Store()
        var enabled by mutableStateOf(false)
        compose.setContent { MaterialTheme { AppGuideHost(GuideSection.HOME, enabled, store) } }
        compose.waitForIdle()
        assertTrue(store.visits.isEmpty())
        compose.runOnIdle { enabled = true }
        waitForTip()
        assertEquals(setOf(GuideSection.HOME), store.visits)
    }

    @Test fun nestedSectionsNeverStackMultipleTips() {
        val store = Store()
        compose.setContent { MaterialTheme {
            AppGuideHost(GuideSection.SETTINGS, store = store)
            AppGuideHost(GuideSection.RECITERS, store = store)
        } }
        waitForTip()
        compose.onAllNodesWithTag("appGuideTip").assertCountEquals(1)
        assertEquals(setOf(GuideSection.SETTINGS, GuideSection.RECITERS), store.visits)
    }

    @Test fun persistedVisitPreventsAnotherTipAfterCompositionRecreation() {
        var generation by mutableIntStateOf(0)
        compose.setContent { MaterialTheme { key(generation) { AppGuideHost(GuideSection.FAMILY) } } }
        waitForTip()
        compose.runOnIdle { generation++ }
        compose.waitForIdle()
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        compose.onNodeWithTag("appGuideTip").assertDoesNotExist()
    }
}
