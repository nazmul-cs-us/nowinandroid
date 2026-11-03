package com.starception.submission.feature.prayertimes

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h852dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FluidHomeRevealTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureStretchAndSeparationAcrossTheWholeTransition() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primaryContainer = Color(0xFFD4ECB5))) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    listOf(Color(0xFF182336), Color(0xFF565065), Color(0xFFF7F6F2)),
                )).padding(24.dp)) {
                    ExploreTestContent()
                }
            }
        }
        val directory = File("build/outputs/reference-motion/explore-frames").apply { mkdirs() }
        compose.onRoot().captureRoboImage(File(directory, "frame-000.png").path)
        compose.onNodeWithContentDescription("Expand home options")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        for (frame in 1..30) {
            compose.mainClock.advanceTimeBy(32)
            compose.onRoot().captureRoboImage(File(directory, "frame-%03d.png".format(frame)).path)
        }
        compose.onNodeWithTag("homeArtworkCard-4").assertIsDisplayed()
    }

    @Test fun hiddenOptionsAreNotExposedAndEachShortcutRoutesOnce() {
        val selections = mutableListOf<Int>()
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    ExploreTestContent(onSelect = selections::add)
                }
            }
        }
        compose.onNodeWithText("Today's reading").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/outputs/reference-motion/fluid-collapsed.png")
        for (page in listOf(2, 1, 3, 4)) {
            compose.onNodeWithContentDescription("Expand home options").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("homeArtworkCard-$page").assertIsDisplayed()
            if (page == 2) compose.onRoot().captureRoboImage("build/outputs/reference-motion/fluid-expanded.png")
            compose.onNodeWithTag("homeArtworkCard-$page").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Today's reading").assertDoesNotExist()
        }
        assertEquals(listOf(2, 1, 3, 4), selections)
    }

    @Test fun expansionCanReverseMidFlight() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().padding(24.dp)) { ExploreTestContent() }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Expand home options")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Collapse home options").assertExists()
        compose.mainClock.advanceTimeBy(400)
        compose.onRoot().captureRoboImage("build/outputs/reference-motion/fluid-stretch.png")
        compose.onNodeWithContentDescription("Collapse home options")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithText("Today's reading").assertDoesNotExist()
    }

    @Test fun expandedOptionsSurviveRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().padding(24.dp)) { ExploreTestContent() }
            }
        }
        compose.onNodeWithContentDescription("Expand home options").performClick()
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Collapse home options").assertIsDisplayed()
        compose.onNodeWithText("AI suggested").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w320dp-h800dp")
    fun largeTextOptionsRemainReachableOnNarrowScreens() {
        var selected = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1.8f)) {
                MaterialTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        ExploreTestContent(onSelect = { selected = true })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Expand home options").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("homeArtworkCard-4").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(true, selected)
    }

    @Test fun expansionScrollsLastOptionAboveFloatingNavigation() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.height(600.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Spacer(Modifier.height(500.dp))
                    ExploreTestContent(bottomClearance = 100.dp)
                    Spacer(Modifier.height(100.dp))
                }
            }
        }
        compose.onNodeWithContentDescription("Expand home options").performScrollTo().performClick()
        compose.waitForIdle()
        val bottom = compose.onNodeWithText("For this moment", useUnmergedTree = true)
            .getUnclippedBoundsInRoot().bottom
        assertTrue("Last shortcut must clear the bottom 100dp of the viewport: $bottom", bottom <= 500.dp)
    }
}

@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
@Composable
private fun ExploreTestContent(bottomClearance: androidx.compose.ui.unit.Dp = 0.dp, onSelect: (Int) -> Unit = {}) {
    CompositionLocalProvider(LocalInspectionMode provides true) {
        org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
    }
    HomeArtworkRail(bottomClearance = bottomClearance, onSelect = { card, _ -> onSelect(card.page) })
}
