package com.starception.submission.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import com.starception.submission.feature.prayertimes.HomeHeaderDetail
import com.starception.submission.feature.prayertimes.HomeHeaderDetailKind
import com.starception.submission.feature.prayertimes.HomeHeaderTypography
import com.starception.submission.feature.prayertimes.HomeRotatingHeaderText
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h240dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeHeaderDepthActionTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalResourceApi::class)
    @Test fun sculptedHomeActionsKeepSeparateAccessibleTargets() {
        var familyClicks = 0
        var searchClicks = 0
        var settingsClicks = 0
        val headerDetail = androidx.compose.runtime.mutableStateOf(
            HomeHeaderDetail("6 Jumada II 1448 AH", HomeHeaderDetailKind.Calendar))
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) { PreviewContextConfigurationEffect() }
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                    Color(0xFF6C8190), Color(0xFFB4B6B8),
                )))) {
                    Column(Modifier.align(Alignment.TopStart).padding(22.dp).width(240.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        com.starception.submission.feature.prayertimes.HomeFamilyRibbon { familyClicks++ }
                        HomeHeaderGreeting("Salam, evening", Color(0xFF101827), false)
                        HomeRotatingHeaderText(
                            listOf(headerDetail.value),
                            HomeHeaderTypography.detail, Color(0xFF101827).copy(alpha = .92f))
                    }
                    Box(Modifier.align(Alignment.TopEnd).padding(22.dp)) {
                        AppHeaderActions(
                            onDarkArtwork = false,
                            onSearchClick = { searchClicks++ },
                            onSettingsClick = { settingsClicks++ },
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/home_header_depth_actions.png")
        compose.onNodeWithContentDescription("Family account").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, familyClicks)
        compose.onNodeWithContentDescription("Search")
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("Settings")
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, searchClicks)
        assertEquals(1, settingsClicks)
        compose.runOnIdle { headerDetail.value = HomeHeaderDetail("Dubai, UAE", HomeHeaderDetailKind.Location) }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/home_header_location.png")
        compose.runOnIdle { headerDetail.value = HomeHeaderDetail("25°C · Partly cloudy", HomeHeaderDetailKind.Weather) }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/home_header_weather.png")
    }
}
