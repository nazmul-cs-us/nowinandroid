package com.starception.submission.feature.prayertimes

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
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
import org.jetbrains.compose.resources.painterResource

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h852dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImmersiveHomeCoverTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    @Test fun coverOpensTheMatchingReader() {
        var openedPage = -1
        compose.setContent { CoverTestScene(dark = true, onOpen = { openedPage = it }) }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_cover.png")
        compose.onNodeWithText("A moment to pause").performClick()
        assertEquals(0, openedPage)
    }

    @Test fun lightCoverFillsThePaddedHomeWidth() {
        compose.setContent { CoverTestScene(dark = false, onOpen = {}) }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/immersive_home_cover_light.png")
    }
}

@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
@Composable
private fun CoverTestScene(dark: Boolean, onOpen: (Int) -> Unit) {
    CompositionLocalProvider(LocalInspectionMode provides true) {
        org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
    }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Box(Modifier.fillMaxWidth().height(852.dp)) {
            Image(
                painter = painterResource(homeCoverArtwork(0, dark)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.BottomEnd,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = 1.25f
                    scaleY = 1.25f
                    translationY = -size.height * .20f
                },
            )
            Box(Modifier.fillMaxSize().background(if (dark) Brush.verticalGradient(
                0f to Color(0xFF071C30).copy(alpha = .12f),
                .50f to Color(0xFF0B1D2C).copy(alpha = .50f),
                .78f to Color(0xFF0C110F).copy(alpha = .97f),
                1f to Color(0xFF0C110F).copy(alpha = .97f),
            ) else Brush.verticalGradient(
                0f to Color.White.copy(alpha = .14f),
                .48f to Color.White.copy(alpha = .58f),
                .78f to Color.White,
                1f to Color.White,
            )))
            Column(Modifier.fillMaxSize().padding(top = 80.dp, start = 24.dp, end = 24.dp)) {
                Box(Modifier.requiredWidth(393.dp).height(588.dp)) {
                    ImmersiveHomeCover(
                        selectedPage = 0,
                        isDarkTheme = dark,
                        nextPrayerName = "Asr",
                        nextPrayerCountdown = "1h 41m",
                        onSelectionChanged = {},
                        onOpen = onOpen,
                    )
                }
            }
        }
    }
}
