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
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
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
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState()).padding(16.dp)) {
                    HomeArtworkSettingsSection()
                }
            }
        }
        compose.onNodeWithText("Every week").performClick()
        compose.runOnIdle {
            assertEquals(ArtworkFrequency.WEEKLY,
                HomeArtworkSchedule.decode(prefs.getString("schedule", null)).frequency)
        }
        compose.onNodeWithText("Keep current").performClick()
        compose.runOnIdle {
            val restored = HomeArtworkSettings(prefs.getString("schedule", null)) {}
            assertEquals(ArtworkFrequency.FIXED, restored.schedule.frequency)
        }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/home_artwork_settings.png")
        // Leave the shared singleton at the normal default for other component tests.
        compose.onNodeWithText("Every day").performClick()
    }
}
