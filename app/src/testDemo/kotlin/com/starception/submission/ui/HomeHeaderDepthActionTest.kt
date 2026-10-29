package com.starception.submission.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
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

    @Test fun sculptedHomeActionsKeepSeparateAccessibleTargets() {
        var searchClicks = 0
        var settingsClicks = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                    Color(0xFF6C8190), Color(0xFFB4B6B8),
                )))) {
                    Box(Modifier.align(Alignment.TopEnd).padding(22.dp)) {
                        HomeHeaderDepthActions(
                            isDarkTheme = false,
                            onSearchClick = { searchClicks++ },
                            onSettingsClick = { settingsClicks++ },
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/home_header_depth_actions.png")
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        assertEquals(1, searchClicks)
        assertEquals(1, settingsClicks)
    }
}
