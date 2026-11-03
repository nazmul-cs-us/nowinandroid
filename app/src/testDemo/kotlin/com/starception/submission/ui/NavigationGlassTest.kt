package com.starception.submission.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w300dp-h180dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationGlassTest {
    @get:Rule val compose = createComposeRule()

    @Test fun materialFollowsLiveBackgroundInsteadOfKeepingAFixedFill() {
        val background = mutableStateOf(Color(0xFF080C14))
        compose.setContent {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(background.value))
                Box(
                    Modifier.size(220.dp, 56.dp)
                        .navigationGlass(backdrop, RoundedCornerShape(28.dp), darkAppearance = true),
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
