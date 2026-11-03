package com.starception.submission.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w300dp-h180dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationGlassTest {
    @get:Rule val compose = createComposeRule()

    @Test fun appearanceTransitionsThroughIntermediateColorsAndReversesWithoutSnapping() {
        val dark = mutableStateOf(true)
        lateinit var current: NavigationGlassColors
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val colors = animatedNavigationGlassColors(dark.value)
            SideEffect { current = colors }
        }
        val darkColors = navigationGlassColors(1f)
        val lightColors = navigationGlassColors(0f)
        compose.runOnIdle {
            assertEquals(darkColors, current)
            dark.value = false
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(160)
        lateinit var middle: NavigationGlassColors
        compose.runOnIdle {
            middle = current
            // Material, border, labels, selected blue and capsule must all be in flight.
            fun NavigationGlassColors.channels() = listOf(surface, outline, content, active, selection)
            current.channels().forEachIndexed { index, color ->
                assertTrue("Channel $index must leave its dark endpoint: $color", color != darkColors.channels()[index])
                assertTrue("Channel $index must not snap to its light endpoint: $color", color != lightColors.channels()[index])
            }
            dark.value = true
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("Reversing must continue from the current tint", abs(current.surface.red - middle.surface.red) < .1f)
        }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle {
            assertEquals(darkColors, current)
            dark.value = false
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertEquals(lightColors, current) }
    }

    @Test fun lightMaterialDoesNotTurnLightContentIntoADarkPill() {
        compose.setContent {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color(0xFFF9F9FB)))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Match both production controls, including their transparent Surface.
                    for (width in listOf(220.dp, 56.dp)) {
                        Surface(
                            modifier = Modifier.size(width, 56.dp)
                                .navigationGlass(backdrop, RoundedCornerShape(28.dp), navigationGlassColors(0f)),
                            shape = RoundedCornerShape(28.dp),
                            color = Color.Transparent,
                            shadowElevation = 0.dp,
                        ) {}
                    }
                }
            }
        }
        val path = "src/testDemo/screenshots/navigation_glass_light.png"
        compose.onRoot().captureRoboImage(path)
        val bitmap = BitmapFactory.decodeFile(path)
        try {
            val scale = bitmap.width / 300f
            for (centerX in listOf(118f, 264f)) {
                val center = Color(bitmap.getPixel((centerX * scale).toInt(), bitmap.height / 2))
                val upperInterior = Color(bitmap.getPixel((centerX * scale).toInt(), (67 * scale).toInt()))
                assertTrue("Light glass must stay bright instead of acquiring a grey shadow fill", center.luminance() > .85f)
                assertTrue("The upper interior must not have a pale fading band", abs(upperInterior.luminance() - center.luminance()) < .04f)
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun mixedBackgroundsDoNotFlickerBetweenAppearances() {
        assertTrue(navigationUsesDarkGlass(.40f, currentlyDark = true))
        assertFalse(navigationUsesDarkGlass(.40f, currentlyDark = false))
        assertFalse(navigationUsesDarkGlass(.80f, currentlyDark = true))
        assertTrue(navigationUsesDarkGlass(.10f, currentlyDark = false))
    }

    @Test fun materialFollowsLiveBackgroundInsteadOfKeepingAFixedFill() {
        val background = mutableStateOf(Color(0xFF080C14))
        compose.setContent {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(background.value))
                Box(
                    Modifier.size(220.dp, 56.dp)
                        .navigationGlass(backdrop, RoundedCornerShape(28.dp), navigationGlassColors(1f)),
                )
            }
        }
        fun centerColor(name: String): Color {
            compose.waitForIdle()
            val path = "src/testDemo/screenshots/navigation_glass_$name.png"
            compose.onRoot().captureRoboImage(path)
            val bitmap = BitmapFactory.decodeFile(path)
            return Color(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)).also { bitmap.recycle() }
        }
        val dark = centerColor("dark")
        compose.runOnIdle { background.value = Color(0xFF565B65) }
        val grey = centerColor("grey")
        assertTrue("Glass should lighten as the page beneath it lightens", grey.red > dark.red + .08f)

        compose.runOnIdle { background.value = Color(0xFF82442C) }
        val warm = centerColor("warm")
        assertTrue("Glass should pick up the page's hue", warm.red > warm.blue + .08f)
    }
}
