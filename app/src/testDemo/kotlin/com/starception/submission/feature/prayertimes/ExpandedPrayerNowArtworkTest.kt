package com.starception.submission.feature.prayertimes

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.home_cover_prayer_night_portrait
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h852dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExpandedPrayerNowArtworkTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    @Test fun portraitArtworkCoversLightSurfaceAtTop() {
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            CompositionLocalProvider(LocalDarkTheme provides false) {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    Box(Modifier.fillMaxWidth().height(720.dp)) {
                        InsightPreviewCard(
                            label = "Prayer now",
                            title = "Make Time for Fajr",
                            supportingText = "1h 15m since Fajr",
                            backgroundPainterRes = Res.drawable.home_cover_prayer_night_portrait,
                            backgroundBaseScale = PRAYER_POSTER_ARTWORK_SCALE,
                            disableExpandedArtworkBlur = true,
                            compactProgress = 0f,
                            expansionProgress = 1f,
                            isExpanded = true,
                            fluidFullBleed = true,
                            lightHomeArtwork = true,
                            isFocused = false,
                            onClick = {},
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/expanded_prayer_now_top.png")
        val image = BitmapFactory.decodeFile(File("src/testDemo/screenshots/expanded_prayer_now_top.png").absolutePath)
        val topSky = image.getPixel(image.width / 2, 1)
        assertTrue("Animated artwork must cover the light tile surface", AndroidColor.red(topSky) < 128)
    }
}
