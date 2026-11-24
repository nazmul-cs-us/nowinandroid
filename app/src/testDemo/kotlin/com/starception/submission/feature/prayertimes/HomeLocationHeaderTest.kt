package com.starception.submission.feature.prayertimes

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeLocationHeaderTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    @OptIn(ExperimentalResourceApi::class)
    @Test fun longLocationStaysStillRatherThanScrollingHorizontally() {
        val address = "Al Safouh Second, Dubai, United Arab Emirates — a deliberately long location"
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) { PreviewContextConfigurationEffect() }
            MaterialTheme {
                Box(Modifier.width(220.dp).background(Color.Black).testTag("location-line")) {
                    HomeRotatingHeaderText(listOf(HomeHeaderDetail(address, HomeHeaderDetailKind.Location)),
                        HomeHeaderTypography.detail, Color.White)
                }
            }
        }
        compose.mainClock.advanceTimeBy(32)
        val before = temporary.newFile("before.png")
        compose.onNodeWithTag("location-line").captureRoboImage(before.absolutePath,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        compose.mainClock.advanceTimeBy(3000)
        val after = temporary.newFile("after.png")
        compose.onNodeWithTag("location-line").captureRoboImage(after.absolutePath,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        val first = requireNotNull(BitmapFactory.decodeFile(before.absolutePath))
        val second = requireNotNull(BitmapFactory.decodeFile(after.absolutePath))
        assertTrue("The location line must not move while it is displayed", first.sameAs(second))
        first.recycle()
        second.recycle()
        compose.onNodeWithText(address).assertExists() // Full address remains available to accessibility services.
    }
}
