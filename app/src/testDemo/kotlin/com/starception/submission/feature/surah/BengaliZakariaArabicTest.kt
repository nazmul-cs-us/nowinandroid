package com.starception.submission.feature.surah

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.feature.quran.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h900dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BengaliZakariaArabicTest {
    @get:Rule val compose = createComposeRule()
    private fun source() = javaClass.getResource("/tafsir/bn-zakaria-73.json")!!.readText()
    @Test fun actualPublishedBengaliAndArabicParagraphsSurviveDecodingAndInlineRendering() {
        val data = source()
        val json = Json.parseToJsonElement(data).jsonObject
        val index = json.getValue("ayahPassage").jsonArray[3].jsonPrimitive.int
        val original = json.getValue("passages").jsonArray[index].jsonPrimitive.content
        val repo = PublishedTafsirRepository { data }
        val text = runBlocking { repo.text(73, 4) }
        assertEquals(original, text)
        assertTrue(text.contains("ترتيل"))
        assertTrue(text.contains("\n\n"))
        assertFalse(text.contains("(আরবি)"))
        val silent = object : TafsirSpeechPlayer {
            override val state = MutableStateFlow(TafsirSpeechState())
            override fun play(text: String, language: String) = Unit
            override fun pause() = Unit
            override fun resume() = Unit
            override fun stop() = Unit
            override fun release() = Unit
        }
        compose.setContent { MaterialTheme { Surface { LazyColumn(Modifier.fillMaxSize()) {
            item { InlineTafsirHeader("zakaria_bn", {}, {}) }
            item { InlineTafsirCommentary(73, 4, "zakaria_bn", loadText = { repo.text(73, 4) },
                speechPlayerFactory = { silent }, missingAsset = { Text("Download required") }) }
        } } } }
        compose.waitForIdle()
        compose.waitUntil(5000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/bengali_zakaria_arabic.png")
    }
}
