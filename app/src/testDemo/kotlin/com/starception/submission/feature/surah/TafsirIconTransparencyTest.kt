package com.starception.submission.feature.surah

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.core.designsystem.icon.TafsirIcon
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w64dp-h64dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TafsirIconTransparencyTest {
    @get:Rule val compose = createComposeRule()
    @Test fun transparentIconUsesDarkForegroundOnLightThemes() = checkIcon(false)
    @Test fun transparentIconUsesLightForegroundOnDarkThemes() = checkIcon(true)

    private fun checkIcon(dark: Boolean) {
        val background = 0xFF28616B.toInt()
        compose.setTafsirContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(color = Color(background), contentColor = if (dark) Color.White else Color.Black) {
                    TafsirIcon(Modifier.size(32.dp).testTag("tafsir-icon"))
                }
            }
        }
        val file = File("src/testDemo/screenshots/tafsir_icon_transparent_${if (dark) "dark" else "light"}.png")
        compose.onNodeWithTag("tafsir-icon").captureRoboImage(file.absolutePath)
        val image = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        try {
            listOf(0 to 0, image.width - 1 to 0, 0 to image.height - 1, image.width - 1 to image.height - 1)
                .forEach { (x, y) -> assertEquals("The icon must not paint a background", background, image.getPixel(x, y)) }
            val foreground = if (dark) AndroidColor.WHITE else AndroidColor.BLACK
            assertTrue("The original icon silhouette must follow the foreground color",
                (0 until image.height).any { y -> (0 until image.width).any { x -> image.getPixel(x, y) == foreground } })
        } finally { image.recycle() }
    }
}
