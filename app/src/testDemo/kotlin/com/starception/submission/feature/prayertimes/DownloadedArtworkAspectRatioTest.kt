package com.starception.submission.feature.prayertimes

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
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
class DownloadedArtworkAspectRatioTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun openingAPortraitTileDoesNotStretchALandscapeBitmap() {
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        Canvas(bitmap).drawCircle(100f, 50f, 10f, Paint().apply { color = Color.RED })
        compose.setContent {
            CompositionLocalProvider(LocalDarkTheme provides false) {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    Box(Modifier.width(240.dp).height(400.dp)) {
                        InsightPreviewCard(label = "", title = "", backgroundBitmap = bitmap.asImageBitmap(),
                            backgroundBaseScale = 1f, wallpaperTintAlpha = 0f, disableExpandedArtworkBlur = true,
                            compactProgress = 0f, expansionProgress = 1f, isExpanded = true,
                            fluidFullBleed = true, isFocused = false)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(3000)
        val out = File(System.getProperty("java.io.tmpdir"), "opencode/artwork-aspect-ratio.png")
        out.parentFile.mkdirs()
        compose.onRoot().captureRoboImage(out.absolutePath, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        val rendered = requireNotNull(BitmapFactory.decodeFile(out.absolutePath))
        var left = rendered.width; var right = -1; var top = rendered.height; var bottom = -1
        for (y in 0 until rendered.height) for (x in 0 until rendered.width) {
            val pixel = rendered.getPixel(x, y)
            if (Color.red(pixel) > 80 && Color.red(pixel) > Color.green(pixel) * 1.6 && Color.red(pixel) > Color.blue(pixel) * 1.6) {
                left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        val width = right - left + 1; val height = bottom - top + 1
        assertTrue("Circle marker should be visible", width > 20 && height > 20)
        assertTrue("Downloaded artwork was stretched: marker ${width}x$height", abs(width - height) <= 4)
    }
}
