package com.starception.submission.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
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
class DeenlySparkSplashTest {
    @get:Rule val compose = createComposeRule()

    @Test fun splashFinishesOnce() {
        var finishes = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { DeenlySparkSplash { finishes++ } }
        compose.mainClock.advanceTimeBy(1200)
        compose.onRoot().captureRoboImage("build/outputs/reference-motion/spark-splash.png")
        assertEquals(0, finishes)
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(1, finishes)
        compose.mainClock.advanceTimeBy(2000)
        assertEquals(1, finishes)
    }

    @Test @Config(qualifiers = "w800dp-h360dp")
    fun continueIsReachableInLandscape() {
        var continues = 0
        compose.setContent { MaterialTheme { SparkSplashArtwork({ .8f }, { continues++ }) } }
        compose.onNodeWithText("Continue").performClick()
        assertEquals(1, continues)
    }
}
