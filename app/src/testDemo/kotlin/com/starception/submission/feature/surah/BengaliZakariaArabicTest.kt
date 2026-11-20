package com.starception.submission.feature.surah

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    @Test fun actualPublishedBengaliAndArabicParagraphsSurviveDecodingAndInlineRendering() = renderReader()
    @Test
    @Config(qualifiers = "w320dp-h900dp", sdk = [35])
    fun compactDarkReaderKeepsEditionAndPlaybackUsableWithLargerText() = renderReader(dark = true, fontScale = 1.3f)

    private fun renderReader(dark: Boolean = false, fontScale: Float = 1f) {
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
        val narrating = mutableStateOf(false)
        compose.setTafsirContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(density.density, fontScale)) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    val scope = rememberCoroutineScope()
                    val queue = remember { SurahTafsirSpeechPlayer(scope, silent, 20, "bn", { "Commentary" }, {}) }
                    DisposableEffect(queue) { onDispose { queue.release() } }
                    Surface { LazyColumn(Modifier.fillMaxSize()) {
                        item { InlineTafsirHeader("zakaria_bn", {}, {}, playback = {
                            SurahTafsirPlaybackControls(queue, "Al-Muzzammil", compact = true) { Text("Download required") }
                        }) }
                        item { AyahNarrationGroup(4, false, studyMode = true) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("أَوْ زِدْ عَلَيْهِ وَرَتِّلِ الْقُرْآنَ تَرْتِيلًا", modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Right)
                                Text("Translation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text("অথবা তার চেয়েও একটু বাড়ান। আর কুরআন তিলাওয়াত করুন ধীরে ধীরে সুস্পষ্টভাবে।",
                                    style = MaterialTheme.typography.bodyLarge)
                            }
                            InlineTafsirCommentary(73, 4, "zakaria_bn", loadText = { repo.text(73, 4) }, embedded = true, narrating = narrating.value,
                                speechPlayerFactory = { silent }, missingAsset = { Text("Download required") })
                        } }
                    } }
                }
            }
        }
        compose.waitForIdle()
        compose.waitUntil(5000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tafsir-ayah-4").assertExists()
        compose.onNodeWithText("Translation").assertExists()
        compose.onNodeWithText(text).assertExists()
        compose.onNodeWithText("Play full surah tafsir").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hide tafsir").assertIsDisplayed()
        compose.onRoot().captureRoboImage(if (dark) "src/testDemo/screenshots/bengali_tafsir_dark_large.png"
            else "src/testDemo/screenshots/bengali_zakaria_arabic.png")
        compose.onNodeWithText("Read full tafsir").performScrollTo().performClick()
        compose.onNodeWithText("Show less").performScrollTo().performClick()
        compose.onNodeWithText("Read full tafsir").assertExists()
        compose.runOnIdle { narrating.value = true }
        compose.onNodeWithText("Read full tafsir").assertDoesNotExist()
        compose.onNodeWithText(text).assertExists()
        compose.runOnIdle { narrating.value = false }
        compose.onNodeWithText("Read full tafsir").assertExists()
    }
}
