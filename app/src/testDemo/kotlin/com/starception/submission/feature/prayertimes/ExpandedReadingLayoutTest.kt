package com.starception.submission.feature.prayertimes

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.core.designsystem.component.InsightReadingHeader
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.insight_quran_background
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h852dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExpandedReadingLayoutTest {
    @get:Rule val compose = createComposeRule()
    private var measuredActionTop = Float.NaN

    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    private fun render(fontScale: Float = 1f, onChromeHidden: (Boolean) -> Unit = {}) {
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDarkTheme provides true,
                LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box(Modifier.fillMaxWidth().height(720.dp).background(Color.Black)) {
                        InsightPreviewCard(
                            label = "Today's reading",
                            title = "",
                            footerText = null,
                            backgroundPainterRes = Res.drawable.insight_quran_background,
                            compactProgress = 0f,
                            expansionProgress = 1f,
                            isExpanded = true,
                            fluidFullBleed = true,
                            useEditorialExpandedLayout = true,
                            readingSurahIndex = 73,
                            onReadingPlayPause = {},
                            onHeaderActionTopInWindowPx = { measuredActionTop = it },
                            onReaderChromeHidden = onChromeHidden,
                            expandedLeadingContent = {
                                Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
                                    InsightReadingHeader("SURAH 74 · AL-MUDDATHIR", "56 AYAT", Color(0xFFD8AB59))
                                    LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("reading-passage")) {
                                        items(40) { index ->
                                            Text("Passage ${index + 1}", color = Color.White,
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
                                        }
                                    }
                                }
                            },
                            onClick = {},
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_200)
        compose.waitForIdle()
    }

    @Test fun surahHeadingClearsPlaybackControlsWithoutAnOversizedGap() {
        render()
        val play = compose.onNodeWithContentDescription("Listen to daily Surah").fetchSemanticsNode().boundsInRoot
        assertTrue("The close anchor must use the action row's measured top", kotlin.math.abs(measuredActionTop - play.top) < 1f)
        val heading = compose.onNodeWithText("SURAH 74 · AL-MUDDATHIR", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val scale = compose.onRoot().fetchSemanticsNode().boundsInRoot.width / 393f
        assertTrue("Surah heading must clear playback", heading.top > play.bottom)
        assertTrue("Reading header must sit close to playback controls", heading.top - play.bottom <= 40f * scale)
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/expanded_reading_header.png")
    }

    @Test fun largerTextStillClearsTheMeasuredHeader() {
        render(fontScale = 1.6f)
        val play = compose.onNodeWithContentDescription("Listen to daily Surah").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("Today's reading").fetchSemanticsNode().boundsInRoot
        val heading = compose.onNodeWithText("SURAH 74 · AL-MUDDATHIR", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Larger reader text must clear both the title and playback", heading.top > maxOf(play.bottom, title.bottom))
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/expanded_reading_header_large_text.png")
    }

    @Test fun scrollingThePassageHidesChromeAndShowingControlsRestoresIt() {
        var hidden = false
        render(onChromeHidden = { hidden = it })
        assertFalse(hidden)
        compose.onNodeWithTag("reading-passage", useUnmergedTree = true).performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue("The passage's own scroll must hide navigation", shouldHideHomeNavigationForInsight(true, false, 0, 24f, hidden))
        compose.onNodeWithText("Show controls").performClick()
        compose.waitForIdle()
        assertFalse("Showing reader controls must restore navigation", shouldHideHomeNavigationForInsight(true, false, 0, 24f, hidden))
    }
}
