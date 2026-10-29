package com.starception.submission.feature.prayertimes

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.insight_prayer_background
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h852dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExpandedSalahLayoutTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    @Test fun hadithHeadingClearsTileControls() {
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            CompositionLocalProvider(LocalDarkTheme provides true) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box(Modifier.fillMaxWidth().height(720.dp).background(Color(0xFF15101A))) {
                        InsightPreviewCard(
                            label = "Today's salah",
                            title = "0 prayers complete",
                            supportingText = "5 remain · Fajr is next",
                            backgroundPainterRes = Res.drawable.insight_prayer_background,
                            compactProgress = 0f,
                            expansionProgress = 1f,
                            isExpanded = true,
                            fluidFullBleed = true,
                            useEditorialExpandedLayout = true,
                            prayerRecap = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha").map {
                                PrayerRecapIndicator(it, it.first().toString(), false, it == "Fajr")
                            },
                            expandedLeadingContent = {
                                Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
                                    Text("Hadith for Fajr", color = Color(0xFFE7C57B))
                                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                                        Text(
                                            "Narrated Qatada: " +
                                                "Anas bin Malik said that the Prophet stood up and prayed Fajr. ".repeat(6),
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.padding(top = 24.dp),
                                        )
                                        FlowingSourceDisclosure("Sahih al-Bukhari", "Search match")
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
        compose.onNodeWithText("Today's salah").fetchSemanticsNode()
        compose.onNodeWithText("Hadith for Fajr").fetchSemanticsNode()
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/expanded_salah_header.png")
    }
}
