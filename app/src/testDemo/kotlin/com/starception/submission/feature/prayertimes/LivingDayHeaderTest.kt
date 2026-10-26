package com.starception.submission.feature.prayertimes

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.Location
import java.time.LocalDateTime
import java.time.LocalTime
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
class LivingDayHeaderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun countdownWrapsAtMidnightAndRetainsHours() {
        assertEquals("01:09:00", livingDayCountdown(LocalTime.of(14, 32), LocalTime.of(15, 41)))
        assertEquals("05:06:01", livingDayCountdown(LocalTime.of(23, 59, 59), LocalTime.of(5, 6)))
        assertEquals("00:00:00", livingDayCountdown(LocalTime.NOON, LocalTime.NOON))
    }

    @Test fun actionsReachExistingFeatures() {
        var marked = ""
        var qibla = 0
        var listen = 0
        var schedule = 0
        showHeader(onMark = { marked = it }, onQibla = { qibla++ }, onListen = { listen++ }, onSchedule = { schedule++ })
        compose.onNodeWithText("01:09:00").assertExists()
        compose.onNodeWithText("Mark Dhuhr").performScrollTo().performClick()
        compose.onNodeWithText("Qibla").performClick()
        compose.onNodeWithText("Listen").performClick()
        compose.onNodeWithText("Schedule").performClick()
        assertEquals("Dhuhr", marked)
        assertEquals(1, qibla)
        assertEquals(1, listen)
        assertEquals(1, schedule)
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/living_day_header.png")
    }

    @Test fun largeTextKeepsActionsReachable() {
        var opened = false
        showHeader(fontScale = 2f, onSchedule = { opened = true })
        compose.onNodeWithText("Schedule").performScrollTo().performClick()
        assertEquals(true, opened)
        compose.onNodeWithText("Isha").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/living_day_large_text.png")
    }

    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    private fun showHeader(
        fontScale: Float = 1f,
        onMark: (String) -> Unit = {},
        onQibla: () -> Unit = {},
        onListen: () -> Unit = {},
        onSchedule: () -> Unit = {},
    ) {
        compose.setContent {
            // Robolectric does not start the Compose resource ContentProvider. Initialize
            // its context through the supported preview hook, then render normally.
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
            }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    Surface {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                            LivingDayHeader(
                                day = DayPrayerTimes(
                                    date = LocalDateTime.of(2026, 10, 26, 14, 32),
                                    fajr = LocalTime.of(5, 6), sunrise = LocalTime.of(6, 20),
                                    dhuhr = LocalTime.of(12, 15), asr = LocalTime.of(15, 41),
                                    maghrib = LocalTime.of(18, 20), isha = LocalTime.of(19, 49),
                                    location = Location(25.2, 55.3, 4.0, city = "Dubai"),
                                ),
                                now = LocalTime.of(14, 32), completed = setOf("Fajr"),
                                onTogglePrayer = onMark, onQibla = onQibla, onListen = onListen,
                                onSchedule = onSchedule, scheduleExpanded = false,
                            )
                        }
                    }
                }
            }
        }
    }
}
