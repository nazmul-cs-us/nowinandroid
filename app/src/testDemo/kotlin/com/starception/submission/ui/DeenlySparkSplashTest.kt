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
        compose.setContent { DeenlySparkSplash(reminderLoader = {
            com.starception.submission.core.designsystem.component.deenlySplashReminder(0, false, false)
        }) { finishes++ } }
        compose.mainClock.advanceTimeBy(1200)
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/deenly_spark_splash_mid.png")
        assertEquals(0, finishes)
        compose.mainClock.advanceTimeBy(2200)
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

    @Test fun appVersionIsVisibleWithoutCoveringContinue() {
        compose.setContent { MaterialTheme { SparkSplashArtwork({ 1f }, {}) } }
        val version = compose.onNodeWithText("v${com.starception.submission.BuildConfig.VERSION_NAME}")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val continueButton = compose.onNodeWithText("Continue").fetchSemanticsNode().boundsInRoot
        assertFalse("Version must not cover Continue", version.overlaps(continueButton))
        assertTrue("Version belongs to the right of Continue", version.center.x > continueButton.center.x)
    }

    @Test fun wordmarkStartsBehindTheLowerEdgeThenCrescentReveals() {
        val progress = mutableFloatStateOf(0f)
        compose.setContent { MaterialTheme { SparkSplashArtwork({ progress.floatValue }, {}) } }
        val wordmark = compose.onNodeWithContentDescription("Deenly").assertIsDisplayed()
        val file = File("src/testDemo/screenshots/deenly_first_frame_wordmark.png")
        wordmark.captureRoboImage(file.absolutePath)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        var visible = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.red(pixel) > 140 && Color.green(pixel) > 130 && Color.blue(pixel) > 140) visible++
        }
        bitmap.recycle()
        assertEquals("Wordmark must start hidden behind the opening", 0, visible)
        compose.runOnIdle { progress.floatValue = .5f }
        val scene = File("src/testDemo/screenshots/deenly_crescent_reveal.png")
        compose.onRoot().captureRoboImage(scene.absolutePath)
        val frame = requireNotNull(BitmapFactory.decodeFile(scene.absolutePath))
        var crescentPixels = 0
        for (y in (frame.height * .34f).toInt() until (frame.height * .55f).toInt()) {
            for (x in (frame.width * .3f).toInt() until (frame.width * .7f).toInt()) {
                if (Color.red(frame.getPixel(x, y)) > 100) crescentPixels++
            }
        }
        frame.recycle()
        assertTrue("The restored crescent must reveal its purple-and-gold strokes", crescentPixels > 20)
    }

    @Test fun wordmarkRisesThroughTheMaskBeforeSettling() {
        val progress = mutableFloatStateOf(.12f)
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
        val early = brightPixels("first")
        compose.runOnIdle { progress.floatValue = .58f }
        val fadingIn = brightPixels("writing")
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/deenly_splash_writing.png")
        compose.runOnIdle { progress.floatValue = 1f }
        val complete = brightPixels("complete")
        assertTrue("More of the wordmark must rise above the edge", fadingIn > early)
        assertTrue("The full wordmark must be visible before exit", complete >= fadingIn && complete > 0)
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/deenly_splash_wordmark.png")
    }

    @Test fun continueStillSkipsImmediatelyAndFinishesOnlyOnce() {
        var finishes = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { DeenlySparkSplash(reminderLoader = {
            com.starception.submission.core.designsystem.component.deenlySplashReminder(0, false, false)
        }) { finishes++ } }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Continue").performClick()
        assertEquals(1, finishes)
        compose.mainClock.advanceTimeBy(4000)
        compose.waitForIdle()
        assertEquals(1, finishes)
    }
}
