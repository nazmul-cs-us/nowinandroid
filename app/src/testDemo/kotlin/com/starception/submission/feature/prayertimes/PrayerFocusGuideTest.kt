package com.starception.submission.feature.prayertimes

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.settings.components.PrayerFocusGuide
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h852dp-mdpi", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrayerFocusGuideTest {
    @get:Rule val compose = createComposeRule()

    @Test fun guideExplainsManualSchedulingAndOpensAppSettingsOnlyOnRequest() {
        var settingsOpened = 0
        compose.setContent { MaterialTheme { PrayerFocusGuide { settingsOpened++ } } }
        compose.onNodeWithTag("prayerFocusGuideOpen").performClick()
        compose.onNodeWithText("Prayer Focus on iPhone").assertIsDisplayed()
        compose.onNodeWithText("do not follow Deenly", substring = true).assertExists()
        compose.runOnIdle { assertEquals(0, settingsOpened) }
        compose.onNodeWithTag("prayerFocusNotificationSettings").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, settingsOpened) }
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Prayer Focus on iPhone").assertDoesNotExist()
    }

    @Test @Config(qualifiers = "w320dp-h852dp-mdpi")
    fun guideAndActionsRemainReadableAtLargeSystemTextSize() {
        RuntimeEnvironment.setFontScale(1.5f)
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) { PrayerFocusGuide {} } }
        compose.onNodeWithTag("prayerFocusGuideOpen").performClick()
        compose.onNodeWithTag("prayerFocusNotificationSettings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/prayer_focus_guide_large_text.png")
    }
}
