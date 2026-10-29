package com.starception.submission.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.core.designsystem.theme.LightDefaultColorScheme
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
import com.starception.submission.core.designsystem.theme.mainPageBackgroundBrush
import java.io.File
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h852dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LightPageGradientTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mainPageCanvasIsNotSolidWhite() {
        compose.setContent {
            CompositionLocalProvider(LocalDarkTheme provides false) {
                MaterialTheme(colorScheme = LightDefaultColorScheme) {
                    Box(Modifier.fillMaxSize().background(mainPageBackgroundBrush()))
                }
            }
        }
        compose.waitForIdle()
        val path = "src/testDemo/screenshots/light_page_gradient.png"
        compose.onRoot().captureRoboImage(path)
        val bitmap = BitmapFactory.decodeFile(File(path).absolutePath)
        assertNotEquals(bitmap.getPixel(bitmap.width / 2, 1), bitmap.getPixel(bitmap.width / 2, bitmap.height - 2))
    }
}
