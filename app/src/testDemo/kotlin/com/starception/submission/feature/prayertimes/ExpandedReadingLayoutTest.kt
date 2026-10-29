package com.starception.submission.feature.prayertimes

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.insight_quran_background
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

    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    @Test fun surahHeadingClearsPlaybackControls() {
        var measuredActionTop = Float.NaN
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            CompositionLocalProvider(LocalDarkTheme provides true) {
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
                            expandedLeadingContent = {
                                Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
                                    Text("SURAH 74 · AL-MUDDATHIR", color = Color.White)
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
        val play = compose.onNodeWithContentDescription("Listen to daily Surah")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("The close anchor must use the action row's measured top", kotlin.math.abs(measuredActionTop - play.top) < 1f)
        val heading = compose.onNodeWithText("SURAH 74 · AL-MUDDATHIR", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Surah heading $heading must clear playback $play", heading.top > play.bottom)
    }
}
