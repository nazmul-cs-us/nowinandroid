package com.starception.submission.ui

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.home_artwork_neutral
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NeutralArtworkFallbackTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalResourceApi::class)
    @Test fun neutralFallbackCanBeDecodedAndDisplayedOnAndroid() {
        // An SVG compiles for shared resources but crashes Android's painterResource.
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) { PreviewContextConfigurationEffect() }
            Image(painterResource(Res.drawable.home_artwork_neutral), "Artwork fallback", Modifier.size(200.dp))
        }
        compose.onNodeWithContentDescription("Artwork fallback").assertIsDisplayed()
    }
}
