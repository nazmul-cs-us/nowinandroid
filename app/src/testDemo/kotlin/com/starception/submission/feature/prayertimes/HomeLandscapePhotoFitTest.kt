package com.starception.submission.feature.prayertimes

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.core.designsystem.component.ArtworkBackdropImage
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w240dp-h480dp-mdpi", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeLandscapePhotoFitTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun portraitHomeRetainsBothPhotoEdgesEvenDuringParallax() {
        val source = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
            for (y in 0 until height) for (x in 0 until width) {
                if (x < 30) setPixel(x, y, Color.RED)
                if (x >= width - 30) setPixel(x, y, Color.BLUE)
            }
        }
        val painter = BitmapPainter(source.asImageBitmap())
        val travel = mutableFloatStateOf(0f)
        compose.setContent {
            MaterialTheme {
                ArtworkBackdropImage(painter, Modifier.width(240.dp).height(400.dp),
                    overscan = 1.12f, horizontalTravel = travel.floatValue)
            }
        }
        for ((index, offset) in listOf(-.06f, 0f, .06f).withIndex()) {
            compose.runOnIdle { travel.floatValue = offset }
            val path = File(System.getProperty("java.io.tmpdir"), "home-photo-fit-$index.png")
            compose.onRoot().captureRoboImage(path.absolutePath,
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
            val rendered = requireNotNull(BitmapFactory.decodeFile(path.absolutePath))
            val left = rendered.getPixel(3, 200)
            val right = rendered.getPixel(236, 200)
            assertTrue("Landscape photo's left edge was cropped at $offset", Color.red(left) > 200 && Color.blue(left) < 30)
            assertTrue("Landscape photo's right edge was cropped at $offset", Color.blue(right) > 200 && Color.red(right) < 30)
        }
    }
}
