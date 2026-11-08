/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.feature.prayertimes

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.core.images.ArtworkFrequency
import com.starception.submission.core.images.HomeArtworkSchedule
import com.starception.submission.core.images.HomeArtworkSettings
import com.starception.submission.settings.components.HomeArtworkSettingsSection
import org.junit.Assert.assertTrue
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
class HomeArtworkSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun frequencyControlPersistsAndCanFreezeTheCurrentSet() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = context.getSharedPreferences("home_artwork", 0)
        compose.setContent { ArtworkSettingsScene() }
        compose.onNodeWithText("Every week").assertDoesNotExist()
        compose.onNodeWithTag("homeArtworkFrequency").performClick()
        compose.onNodeWithText("Every week").performClick()
        compose.runOnIdle {
            assertEquals(ArtworkFrequency.WEEKLY,
                HomeArtworkSchedule.decode(prefs.getString("schedule", null)).frequency)
        }
        compose.onNodeWithTag("homeArtworkFrequency").performClick()
        compose.onNodeWithText("Keep current").performClick()
        compose.runOnIdle {
            val restored = HomeArtworkSettings(prefs.getString("schedule", null)) {}
            assertEquals(ArtworkFrequency.FIXED, restored.schedule.frequency)
        }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/home_artwork_settings.png")
        // Leave the shared singleton at the normal default for other component tests.
        compose.onNodeWithTag("homeArtworkFrequency").performClick()
        compose.onNodeWithText("Every day").performClick()
    }

    @Test fun lightArtworkSettingsStayCompactAndHideCreditsUntilRequested() {
        compose.setContent { ArtworkSettingsScene(dark = false) }
        compose.onNodeWithText("Today's collection").assertDoesNotExist()
        compose.onNodeWithTag("homeArtworkPreview").assertIsDisplayed()
        val bounds = compose.onNodeWithTag("homeArtworkSettings").getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top < 400.dp)
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/home_artwork_settings_light.png")
        compose.onNodeWithTag("homeArtworkDetails").performClick()
        compose.onNodeWithText("Today's collection").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/home_artwork_details.png")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Today's collection").assertDoesNotExist()
    }

    @Test @Config(qualifiers = "w320dp-h852dp")
    fun largeTextKeepsFrequencyAndDetailsReachable() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1.5f)) {
                ArtworkSettingsScene()
            }
        }
        compose.onNodeWithTag("homeArtworkFrequency").assertIsDisplayed().performClick()
        compose.onNodeWithText("Keep current").performClick()
        compose.onNodeWithTag("homeArtworkDetails").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/home_artwork_settings_large_text.png")
        compose.onNodeWithTag("homeArtworkFrequency").performClick()
        compose.onNodeWithText("Every day").performClick()
    }

}


@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
@Composable
private fun ArtworkSettingsScene(dark: Boolean = true) {
    CompositionLocalProvider(LocalInspectionMode provides true) {
        org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
    }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()).padding(16.dp)) {
            HomeArtworkSettingsSection()
        }
    }
}
