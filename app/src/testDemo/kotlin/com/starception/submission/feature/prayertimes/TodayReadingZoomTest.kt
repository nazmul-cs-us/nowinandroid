package com.starception.submission.feature.prayertimes

import android.app.Application
import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TodayReadingZoomTest {
    @get:Rule val compose = createComposeRule()

    @Test fun closingImmediatelyAfterZoomRestoresItWhenTheReaderReopens() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("zoom-test", 0)
        prefs.edit().putFloat("arabic_font_size", 60f).commit()
        val visible = mutableStateOf(true)
        var zoom: androidx.compose.runtime.MutableFloatState? = null
        compose.setContent {
            if (visible.value) {
                val current = rememberTodayReadingZoom(prefs)
                zoom = current
                Text("Zoom ${current.floatValue}")
            }
        }
        compose.runOnIdle { zoom!!.floatValue = 2.25f; visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(2.25f, prefs.getFloat(TODAY_READING_ZOOM_KEY, 1f), 0f)
            assertEquals(60f, prefs.getFloat("arabic_font_size", 0f), 0f)
            visible.value = true
        }
        compose.onNodeWithText("Zoom 2.25").assertExists()
    }

    @Test fun invalidSavedZoomFallsBackToNormalSize() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("invalid-zoom-test", 0)
        prefs.edit().putFloat(TODAY_READING_ZOOM_KEY, Float.NaN).commit()
        compose.setContent { Text("Zoom ${rememberTodayReadingZoom(prefs).floatValue}") }
        compose.onNodeWithText("Zoom 1.0").assertExists()
    }
}
