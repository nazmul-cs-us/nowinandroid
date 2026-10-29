package com.starception.submission.feature.prayertimes

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.home_cover_salah_portrait
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h852dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeArtworkRailTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    @Test fun artworkCardsSelectTheirExistingContentPages() {
        var selected = -1
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(Modifier.fillMaxSize().background(Color(0xFF0C110F)).padding(24.dp)) {
                    HomeArtworkRail { card, bounds ->
                        selected = card.page
                        assertTrue(bounds.width > 0f && bounds.height > 0f)
                    }
                }
            }
        }
        compose.onNodeWithTag("homeArtworkCard-1").performClick()
        assertEquals(1, selected)
        compose.onNodeWithTag("homeArtworkCard-4").performClick()
        assertEquals(4, selected)
        compose.onNodeWithTag("homeArtworkCard-3").performClick()
        assertEquals(3, selected)
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/home_artwork_rail.png")
    }

    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    @Test fun tappedArtworkStaysPresentDuringMorphHandoff() {
        var finishing by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            ArtworkMorphOverlay(
                ArtworkMorphRequest(
                    HomeArtworkCard(1, "Today's salah", "Prayer & hadith",
                        Res.drawable.home_cover_salah_portrait),
                    Rect(24f, 320f, 190f, 488f), Rect(0f, 80f, 393f, 780f), 1, 0),
                reversing = false,
                onFinished = { finishing = true },
            )
        }
        compose.mainClock.advanceTimeBy(260)
        compose.onNodeWithTag("artworkMorphOverlay").assertExists()
        compose.onNodeWithTag("artworkMorphOverlay")
            .captureRoboImage("src/testDemo/screenshots/artwork_morph_mid.png")
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertTrue(finishing) }
        compose.onNodeWithTag("artworkMorphOverlay").assertExists()
    }
}
