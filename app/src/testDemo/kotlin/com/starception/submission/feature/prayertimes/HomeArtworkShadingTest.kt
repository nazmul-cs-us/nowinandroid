package com.starception.submission.feature.prayertimes

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h844dp-mdpi", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeArtworkShadingTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun shadingProtectsTextWithoutDimmingTheCentralArtwork() {
        val expanded = mutableStateOf(false)
        val prayerVisibility = mutableStateOf(0f)
        val artwork = Color(0xFFE8C078)
        compose.setContent {
            Box(Modifier.width(240.dp).height(600.dp).background(artwork)) {
                Box(Modifier.fillMaxSize().background(homeCoverScrim(prayerVisibility.value, Color.Black, expanded.value)))
            }
        }
        // Include both endpoints of the cover crossfade and the expanded backdrop.
        for ((index, configuration) in listOf(false to 0f, false to 1f, false to .5f, true to 0f).withIndex()) {
            compose.runOnIdle {
                expanded.value = configuration.first
                prayerVisibility.value = configuration.second
            }
            val output = File(System.getProperty("java.io.tmpdir"), "opencode/home-artwork-shading-$index.png")
            output.parentFile.mkdirs()
            compose.onRoot().captureRoboImage(output.absolutePath,
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
            val bitmap = requireNotNull(BitmapFactory.decodeFile(output.absolutePath))
            fun pixel(position: Float) = bitmap.getPixel(bitmap.width / 2, (bitmap.height * position).toInt())
            for (position in listOf(.01f, .10f, .25f, .35f, .5f, .6f)) {
                val rendered = pixel(position)
                assertTrue("Artwork at $position was dimmed in state $configuration",
                    abs(AndroidColor.red(rendered) - 232) <= 2 &&
                        abs(AndroidColor.green(rendered) - 192) <= 2 &&
                        abs(AndroidColor.blue(rendered) - 120) <= 2)
            }
            if (!configuration.first) {
                assertTrue("Lower copy must retain contrast", AndroidColor.red(pixel(.94f)) < 40)
            }
        }
    }

    @OptIn(ExperimentalRoborazziApi::class, org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    @Test fun artworkAndCopyPreview() {
        val showRawArtwork = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.width(390.dp).height(820.dp)) {
                    if (showRawArtwork.value) {
                        Image(
                            painter = com.starception.submission.core.images.currentHomeArtworkPainter(0, false),
                            contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.12f; scaleY = 1.12f },
                        )
                    } else {
                    val position = remember { mutableFloatStateOf(0f) }
                    HomeCoverArtworkBackdrop(position, useNightArtwork = false, isDarkTheme = true, isExpanded = false)
                    Text("Salam, morning", style = HomeTypography.greeting, color = Color.White,
                        modifier = Modifier.padding(start = 24.dp, top = 36.dp))
                    Column(Modifier.fillMaxSize().padding(top = 80.dp)) {
                        Box(Modifier.height(588.dp)) {
                            ImmersiveHomeCover(
                                selectedPage = 0, isDarkTheme = true, useNightArtwork = false,
                                nextPrayerName = "Asr", nextPrayerCountdown = "1h 41m", currentPrayerName = "Dhuhr",
                                onSelectionChanged = {}, onOpen = {},
                            )
                        }
                        HomeReflectionPrompt(onDarkCover = true, onClick = {})
                    }
                    }
                }
            }
        }
        val rawOutput = File(System.getProperty("java.io.tmpdir"), "opencode/home-artwork-unfiltered.png")
        rawOutput.parentFile.mkdirs()
        compose.onRoot().captureRoboImage(rawOutput.absolutePath,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        compose.runOnIdle { showRawArtwork.value = false }
        compose.onNodeWithText("Make time for Dhuhr").assertExists()
        val output = File(System.getProperty("java.io.tmpdir"), "opencode/home-artwork-shading-preview.png")
        output.parentFile.mkdirs()
        compose.onRoot().captureRoboImage(output.absolutePath,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        val original = requireNotNull(BitmapFactory.decodeFile(rawOutput.absolutePath))
        val rendered = requireNotNull(BitmapFactory.decodeFile(output.absolutePath))
        for (position in listOf(.01f, .10f, .25f, .35f, .5f, .6f)) {
            val x = original.width / 2
            val y = (original.height * position).toInt()
            val before = original.getPixel(x, y)
            val after = rendered.getPixel(x, y)
            assertTrue("Production backdrop dims artwork at $position",
                abs(AndroidColor.red(before) - AndroidColor.red(after)) <= 2 &&
                    abs(AndroidColor.green(before) - AndroidColor.green(after)) <= 2 &&
                    abs(AndroidColor.blue(before) - AndroidColor.blue(after)) <= 2)
        }
    }
}
