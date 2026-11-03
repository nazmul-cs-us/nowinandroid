package com.starception.submission.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationGlassAppearanceTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val images = TemporaryFolder()

    private fun drawFrame(name: String) {
        compose.onRoot().captureRoboImage(
            File(images.root, "$name.png").absolutePath,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
    }

    @Test
    fun appearanceSamplesThePageAndNotTheNavigationForeground() {
        val background = mutableStateOf(Color.Black)
        lateinit var appearance: NavigationGlassAppearance
        compose.setContent {
            val backdrop = rememberLayerBackdrop()
            appearance = rememberNavigationGlassAppearance(backdrop, initialDark = false)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(background.value))
                // Deliberately opposite to the page. Sampling the window or the
                // navigation itself would produce the wrong appearance.
                Box(
                    Modifier.size(220.dp, 56.dp)
                        .then(appearance.samplingModifier)
                        .background(if (background.value == Color.Black) Color.White else Color.Black),
                )
            }
        }
        drawFrame("dark")
        compose.waitUntil(5_000) { appearance.dark }
        compose.runOnIdle { assertTrue(appearance.dark) }
        compose.runOnIdle { background.value = Color.White }
        drawFrame("light")
        compose.waitUntil(5_000) { !appearance.dark }
        compose.runOnIdle { assertFalse(appearance.dark) }
        compose.runOnIdle { background.value = Color.Black }
        drawFrame("dark-again")
        compose.waitUntil(5_000) { appearance.dark }
    }
}
