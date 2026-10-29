package com.starception.submission.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(22.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        HeaderButton("Search", { searchClicks++ }) {
                            Icon(Icons.Default.Search, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                        HeaderButton("Settings", { settingsClicks++ }) {
                            Icon(Icons.Default.Settings, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(19.dp))
                        }
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

@Composable
private fun HeaderButton(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    HomeHeaderDepthAction(label, isDarkTheme = false, onClick = onClick, content = icon)
}
