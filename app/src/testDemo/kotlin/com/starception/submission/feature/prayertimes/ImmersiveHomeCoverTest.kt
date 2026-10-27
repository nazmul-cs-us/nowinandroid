package com.starception.submission.feature.prayertimes

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
@Config(application = Application::class, qualifiers = "w393dp-h852dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImmersiveHomeCoverTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    @Test fun coverOpensTheMatchingReader() {
        var openedPage = -1
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.fillMaxWidth().height(588.dp)) {
                    ImmersiveHomeCover(
                        selectedPage = 0,
                        isDarkTheme = true,
                        sceneHeight = 852.dp,
                        sceneTopInset = 80.dp,
                        nextPrayerName = "Asr",
                        nextPrayerCountdown = "1h 41m",
                        onSelectionChanged = {},
                        onOpen = { openedPage = it },
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_cover.png")
        compose.onNodeWithText("A moment to pause").performClick()
        assertEquals(0, openedPage)
    }
}
