package com.starception.submission.feature.prayertimes

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h852dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlowingSourceDisclosureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sourceExpandsAndCollapsesInLightTheme() = checkDisclosure(dark = false)

    @Test fun sourceExpandsAndCollapsesInDarkTheme() = checkDisclosure(dark = true)

    private fun checkDisclosure(dark: Boolean) {
        val reference = "Sahih al-Bukhari · Book of Friday Prayer · Hadith 910"
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.width(240.dp)) {
                    FlowingSourceDisclosure(reference, "Search match")
                }
            }
        }
        compose.onNodeWithText(reference).assertDoesNotExist()
        compose.onNodeWithContentDescription("Hadith source").performClick()
        compose.onNodeWithText(reference).assertIsDisplayed()
        compose.onNodeWithText("Search match").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hadith source").performClick()
        compose.onNodeWithText(reference).assertDoesNotExist()
    }
}
