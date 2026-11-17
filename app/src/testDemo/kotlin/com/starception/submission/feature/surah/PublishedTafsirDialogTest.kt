package com.starception.submission.feature.surah

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.starception.submission.core.qurandatabase.QuranAyahTafseer
import com.starception.submission.feature.quran.PublishedTafsirs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.github.takahirom.roborazzi.captureRoboImage

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h900dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PublishedTafsirDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun choosingFrenchInTheFullScreenDialogDoesNotSnapBackToBengali() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        org.robolectric.Shadows.shadowOf(connectivity).setActiveNetworkInfo(null)
        val selected = mutableStateOf("saadi")
        val data = QuranAyahTafseer(0, 1, "الفاتحة", 1, "بسم الله", "Arabic Saadi", "Arabic Moyassar", "Arabic Baghawi", "", "", "")
        compose.setContent { MaterialTheme {
            TafseerDialog(data, selected.value, onTafseerBookSelected = { selected.value = it }, onDismiss = {},
                missingAssetContent = { _, _ -> androidx.compose.material3.Text("Download tafsir") })
        } }
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/tafsir_reader.png")
        compose.onNodeWithTag("published-tafsir-picker").assertIsDisplayed().performClick()
        compose.onNodeWithText("Français").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(PublishedTafsirs.forLanguage("fr")!!.bookKey, selected.value)
        compose.onNodeWithText("Français · QUL / Tarteel").assertIsDisplayed()
        compose.onNodeWithTag("published-tafsir-picker").performClick()
        compose.onNodeWithText("Al-Moyassar").performScrollTo().performClick()
        assertEquals("moysar", selected.value)
        compose.onNodeWithText("Arabic Moyassar").assertIsDisplayed()
    }
    @Test
    @Config(qualifiers = "w393dp-h640dp")
    fun longCommentaryHasOneScrollAreaWhileTheSelectorAndCloseStayVisible() {
        val data = QuranAyahTafseer(0, 1, "الفاتحة", 1, "بسم الله", "شرح الآية. ".repeat(500),
            "", "", "معنى الكلمة", "", "")
        compose.setContent { MaterialTheme {
            TafseerDialog(data, "saadi", onTafseerBookSelected = {}, onDismiss = {})
        } }
        compose.onAllNodes(hasScrollAction()).assertCountEquals(1)
        compose.onNodeWithText("Word meanings").performScrollTo()
        compose.onNodeWithText("Word meanings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
        compose.onNodeWithTag("published-tafsir-picker").assertIsDisplayed()
    }

}
