package com.starception.submission.feature.prayertimes

import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
import com.starception.submission.core.images.HomeArtworkRepository
import com.starception.submission.core.images.LocalHomeArtworkRepository
import com.starception.submission.prayer.service.EnhancedLocationService
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.File
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@HiltAndroidTest
@Config(application = HiltTestApplication::class, qualifiers = "w852dp-h393dp-mdpi", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeHomeArtworkTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    @OptIn(ExperimentalRoborazziApi::class, org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    @Test fun landscapePagesRenderTheDownloadedCollectionInsteadOfLegacyIllustrations() {
        hilt.inject()
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("insight_carousel_preferences", 0).edit()
            .putBoolean("auto_swipe_enabled", false).commit()
        lateinit var repository: HomeArtworkRepository
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            val scope = rememberCoroutineScope()
            repository = remember {
                val platform = OnlineHomeArtworkTest.Platform()
                HomeArtworkRepository(platform, scope, chooseOffset = { 0 },
                    ioDispatcher = Dispatchers.Main.immediate, catalog = platform.catalog)
            }
            LaunchedEffect(repository) { repository.update(0, frozen = true, manualRevision = 1) }
            CompositionLocalProvider(LocalHomeArtworkRepository provides repository, LocalDarkTheme provides true) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    val context = LocalContext.current
                    val locationService = remember { EnhancedLocationService(context) }
                    Box(Modifier.fillMaxSize()) {
                        SwipeableBigTiles(
                            prayerTimes = null, currentTime = LocalTime.of(12, 0), locationService = locationService,
                            getNextPrayer = { null }, getCurrentPrayer = { null }, getPrayerStatus = { "" },
                            getPrayerTimeDisplay = { "" }, getTimeUntilNextPrayer = { "" }, getCurrentDate = { "" },
                            getSmartTitle = { "" }, getSmartContent = { "" }, getSmartFooter = { "" },
                            getTimeSinceCurrentPrayer = { "" }, getPrayerProgress = { 0 to 5 },
                            getDailyStatsTitle = { "" }, getDailyStatsMessage = { "" },
                            getCurrentActivity = { "UNKNOWN" }, onCompassClick = {},
                            isLandscape = true, immersiveHome = false,
                        )
                    }
                }
            }
        }
        compose.waitUntil(15_000) { repository.state.value.artworks.size == 5 }
        repeat(5) { page ->
            compose.waitForIdle()
            val file = File(System.getProperty("java.io.tmpdir"), "opencode/landscape-downloaded-artwork-$page.png")
            file.parentFile.mkdirs()
            compose.onRoot().captureRoboImage(file.absolutePath,
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
            val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
            var downloadedPixels = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val color = bitmap.getPixel(x, y)
                if (AndroidColor.green(color) > 50 && AndroidColor.green(color) > AndroidColor.red(color) * 2 &&
                    AndroidColor.green(color) > AndroidColor.blue(color) * 2) downloadedPixels++
            }
            assertTrue("Landscape page $page did not show the downloaded green fixture",
                downloadedPixels > bitmap.width * bitmap.height * .15f)
            if (page < 4) compose.onRoot().performTouchInput { swipeLeft() }
        }
    }
}
