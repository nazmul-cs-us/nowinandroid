package com.starception.submission.feature.prayertimes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
import com.starception.submission.prayer.service.EnhancedLocationService
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@HiltAndroidTest
@Config(application = HiltTestApplication::class, qualifiers = "w393dp-h852dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImmersiveHomeReaderTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun coverRequestsOpenEachExpandedInsight() {
        hilt.inject()
        var expandedPage: Int? = null
        val requestedPage = mutableStateOf(0 to 1)
        val tilePull = mutableStateOf(0.dp)
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            CompositionLocalProvider(LocalDarkTheme provides true) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    val context = LocalContext.current
                    val locationService = remember(context) { EnhancedLocationService(context) }
                    Box(Modifier.fillMaxWidth().height(852.dp).background(Color(0xFF080B14))) {
                        Box(Modifier.fillMaxWidth().height(852.dp).offset(y = tilePull.value)) {
                            SwipeableBigTiles(
                                prayerTimes = null,
                                currentTime = LocalTime.of(17, 0),
                                locationService = locationService,
                                getNextPrayer = { null },
                                getCurrentPrayer = { null },
                                getPrayerStatus = { "" },
                                getPrayerTimeDisplay = { "" },
                                getTimeUntilNextPrayer = { "" },
                                getCurrentDate = { "" },
                                getSmartTitle = { "" },
                                getSmartContent = { "" },
                                getSmartFooter = { "" },
                                getTimeSinceCurrentPrayer = { "" },
                                getPrayerProgress = { 0 to 5 },
                                getDailyStatsTitle = { "" },
                                getDailyStatsMessage = { "" },
                                getCurrentActivity = { "UNKNOWN" },
                                onCompassClick = {},
                                portraitStripHeight = 550.dp,
                                portraitExpandedStripHeight = 852.dp,
                                immersiveHome = true,
                                edgeToEdgeArtwork = true,
                                pageRequest = requestedPage.value,
                                onExpandedPageChange = { expandedPage = it },
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(0, expandedPage)
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_prayer_reader.png")
        fun assertCloseStaysClearOfStatusBar() {
            compose.runOnIdle { tilePull.value = (-120).dp }
            compose.waitForIdle()
            val rootWidth = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
            val closeTop = compose.onNodeWithContentDescription("Collapse insight")
                .fetchSemanticsNode().boundsInRoot.top
            assertTrue("Close control scrolled into status bar: $closeTop", closeTop >= rootWidth * 24f / 393f)
            compose.runOnIdle { tilePull.value = 0.dp }
            compose.waitForIdle()
        }
        assertCloseStaysClearOfStatusBar()
        for ((page, image) in listOf(
            2 to "immersive_home_quran_reader.png",
            1 to "immersive_home_salah_reader.png",
            3 to "immersive_home_qibla_reader.png",
            4 to "immersive_home_ai_reader.png",
        )) {
            compose.runOnIdle { requestedPage.value = page to (requestedPage.value.second + 1) }
            compose.waitForIdle()
            if (page == 2) {
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithText("Quran text is unavailable")
                        .fetchSemanticsNodes().isNotEmpty()
                }
            } else if (page == 1) {
                // The local collection prompt arrives after the async Hadith
                // lookup; capture the settled reader rather than a partial frame.
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithText("Download more Hadith collections")
                        .fetchSemanticsNodes().isNotEmpty()
                }
            }
            assertEquals(page, expandedPage)
            compose.onRoot().captureRoboImage("src/testDemo/screenshots/$image")
            assertCloseStaysClearOfStatusBar()
            if (page == 2) {
                val grip = compose.onNodeWithContentDescription("Resize insight from bottom right")
                val before = grip.fetchSemanticsNode().boundsInRoot
                val rootWidth = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
                // Cross the old 55% presentation cutoff without fully collapsing.
                grip.performTouchInput {
                    swipe(center, center - Offset(0f, rootWidth * 240f / 393f), 900)
                }
                compose.waitForIdle()
                val after = grip.fetchSemanticsNode().boundsInRoot
                assertTrue("Resize handle did not reduce tile height", after.bottom < before.bottom)
                assertEquals(2, expandedPage)
                compose.onNodeWithText("Quran text is unavailable").assertIsDisplayed()
                compose.onNodeWithContentDescription("Collapse insight").assertIsDisplayed().performClick()
                compose.waitForIdle()
                assertEquals(null, expandedPage)
            }
            if (page == 4) {
                compose.onRoot().performTouchInput {
                    swipe(Offset(190f, 690f), Offset(190f, 230f), 350)
                }
                compose.waitForIdle()
                compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_ai_scrolled.png")
                val controlsTag = compose.onNodeWithText("Show controls").fetchSemanticsNode().boundsInRoot
                val rootWidth = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
                assertTrue("Reader tag grew beyond its compact height", controlsTag.height <= rootWidth * 48f / 393f)
                compose.onNodeWithText("Show controls").performClick()
                compose.waitForIdle()
                assertTrue(compose.onAllNodesWithText("Show controls").fetchSemanticsNodes().isEmpty())
            }
        }
    }
}
