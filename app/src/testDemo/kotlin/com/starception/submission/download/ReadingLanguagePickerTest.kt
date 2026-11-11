package com.starception.submission.download

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.starception.submission.feature.prayertimes.rememberReadingLanguage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReadingLanguagePickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun choosingALanguageUpdatesTheReadingPreferenceCallback() {
        val language = mutableStateOf("en")
        compose.setContent {
            MaterialTheme { ReadingLanguagePicker(language.value, true) { language.value = it } }
        }
        compose.onNodeWithText("English").performClick()
        compose.onNodeWithText("বাংলা").performClick()
        compose.runOnIdle { assertEquals("bn", language.value) }
        compose.onNodeWithText("বাংলা").assertExists()
    }

    @Test fun homeReadingLanguageRefreshesWhenTheSharedPreferenceChanges() {
        lateinit var context: Context
        compose.setContent {
            context = LocalContext.current
            Text(rememberReadingLanguage())
        }
        compose.runOnIdle {
            context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE).edit()
                .putString("quran_translation", "bn").commit()
        }
        compose.onNodeWithText("bn").assertExists()
    }
}
