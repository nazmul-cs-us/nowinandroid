package com.starception.submission.ui

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class DeenlySparkSplashTest {
    @get:Rule val compose = createComposeRule()

    @Test fun splashFinishesOnce() {
        var finishes = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { DeenlySparkSplash { finishes++ } }
        compose.mainClock.advanceTimeBy(1200)
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/deenly_spark_splash_mid.png")
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
        val wordmark = compose.onNodeWithContentDescription("Deenly").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val continueButton = compose.onNodeWithText("Continue").fetchSemanticsNode().boundsInRoot
        assertFalse("The wordmark must not cover Continue", wordmark.overlaps(continueButton))
        compose.onNodeWithText("Continue").performClick()
        assertEquals(1, continues)
    }

    @Test fun sparksWriteTheWordmarkBeforeSettling() {
        val progress = mutableFloatStateOf(.32f)
        compose.setContent { MaterialTheme { SparkSplashArtwork({ progress.floatValue }, {}) } }
        val wordmark = compose.onNodeWithContentDescription("Deenly").assertIsDisplayed()
        fun brightPixels(stage: String): Int {
            val file = File("src/testDemo/screenshots/deenly_wordmark_$stage.png")
            wordmark.captureRoboImage(file.absolutePath)
            val pixels = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
            var count = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val color = pixels.getPixel(x, y)
                if (Color.red(color) > 204 && Color.green(color) > 178 && Color.blue(color) > 204) count++
            }
            pixels.recycle()
            return count
        }
        val firstLetter = brightPixels("first")
        compose.runOnIdle { progress.floatValue = .58f }
        val halfWritten = brightPixels("writing")
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/deenly_splash_writing.png")
        compose.runOnIdle { progress.floatValue = 1f }
        val complete = brightPixels("complete")
        assertTrue("The spark must leave visible strokes behind", firstLetter > 0 && halfWritten > firstLetter)
        assertTrue("The whole name must be drawn before exit", complete > halfWritten)
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/deenly_splash_wordmark.png")
    }

    @Test fun continueStillSkipsImmediatelyAndFinishesOnlyOnce() {
        var finishes = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { DeenlySparkSplash { finishes++ } }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Continue").performClick()
        assertEquals(1, finishes)
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        assertEquals(1, finishes)
    }
}
