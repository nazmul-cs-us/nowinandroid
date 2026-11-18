package com.starception.submission.feature.surah

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.feature.quran.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h1100dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InlineTafsirTest {
    @get:Rule val compose = createComposeRule()
    private class SilentPlayer : TafsirSpeechPlayer {
        override val state = MutableStateFlow(TafsirSpeechState())
        override fun play(text: String, language: String) = Unit
        override fun pause() = Unit
        override fun resume() = Unit
        override fun stop() = Unit
        override fun release() = Unit
    }
    @Test fun eachAyahShowsItsOwnCommentaryInTheSameScrollingPage() {
        val repo = PublishedTafsirRepository {
            """{"surah":1,"ayahCount":7,"passages":["Commentary for the first ayah.","Commentary for the second ayah."],"ayahPassage":[0,1,1,1,1,1,1]}"""
        }
        compose.setContent { MaterialTheme { Surface { LazyColumn {
            item { InlineTafsirHeader("ibn_kathir_en", {}, {}) }
            items(2) { index -> Column {
                Text(if (index == 0) "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ" else "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
                    modifier = Modifier.fillMaxWidth().padding(24.dp), style = MaterialTheme.typography.headlineSmall)
                InlineTafsirCommentary(1, index + 1, "ibn_kathir_en",
                    loadText = { repo.text(1, index + 1) }, speechPlayerFactory = { SilentPlayer() },
                    missingAsset = { Text("Download required") })
            } }
        } } } }
        compose.waitUntil(5000) { compose.onAllNodes(hasText("Commentary for the second ayah.")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Tafsir · Ayah 1").assertIsDisplayed()
        compose.onNodeWithText("Commentary for the first ayah.").assertIsDisplayed()
        compose.onNodeWithText("Tafsir · Ayah 2").assertIsDisplayed()
        compose.onNodeWithText("Commentary for the second ayah.").assertIsDisplayed()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/inline_tafsir.png")
    }
    @Test fun commentaryResizesWithoutReloadingWhenReadingScaleChanges() {
        val scale = mutableFloatStateOf(1f)
        var reads = 0
        val commentary = "Commentary that follows the reader’s pinch zoom. ".repeat(8)
        compose.setContent { MaterialTheme {
            InlineTafsirCommentary(1, 1, "ibn_kathir_en",
                fontScale = scale.floatValue,
                loadText = { reads++; commentary }, speechPlayerFactory = { SilentPlayer() },
                missingAsset = { Text("Download required") })
        } }
        compose.waitUntil(5000) { compose.onAllNodes(hasText(commentary)).fetchSemanticsNodes().isNotEmpty() }
        fun layout(): TextLayoutResult {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(commentary)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single()
        }
        val original = layout()
        assertEquals(18.sp, original.layoutInput.style.fontSize)
        compose.runOnIdle { scale.floatValue = 1.5f }
        val enlarged = layout()
        assertEquals(27.sp, enlarged.layoutInput.style.fontSize)
        assertEquals(45.sp, enlarged.layoutInput.style.lineHeight)
        assertTrue(enlarged.size.height > original.size.height)
        compose.runOnIdle { scale.floatValue = 0.75f }
        assertEquals(13.5.sp, layout().layoutInput.style.fontSize)
        assertEquals(1, reads)
    }
    @Test fun changingAyahCancelsAnOldReadEvenWhenItFinishesLater() {
        val ayah = mutableIntStateOf(1)
        val started = CompletableDeferred<Unit>(); val gate = CompletableDeferred<Unit>()
        compose.setContent { MaterialTheme {
            InlineTafsirCommentary(1, ayah.intValue, "ibn_kathir_en", loadText = {
                if (ayah.intValue == 1) { started.complete(Unit); gate.await(); "Old commentary" }
                else "New commentary"
            }, speechPlayerFactory = { SilentPlayer() }, missingAsset = { Text("Download required") })
        } }
        compose.waitUntil(5000) { started.isCompleted }
        compose.runOnIdle { ayah.intValue = 2 }
        compose.waitUntil(5000) { compose.onAllNodes(hasText("New commentary")).fetchSemanticsNodes().isNotEmpty() }
        gate.complete(Unit)
        compose.onNodeWithText("Tafsir · Ayah 2").assertIsDisplayed()
        compose.onNodeWithText("Old commentary").assertDoesNotExist()
    }
    @Test fun missingAssetsStayInlineAndReloadAfterDownload() {
        var available = false
        compose.setContent { MaterialTheme {
            InlineTafsirCommentary(1, 1, "ibn_kathir_en", loadText = {
                if (!available) throw PublishedTafsirAssetMissing()
                "Saved commentary"
            }, speechPlayerFactory = { SilentPlayer() }, missingAsset = { reload ->
                Button(onClick = { available = true; reload() }) { Text("Download tafsir") }
            })
        } }
        compose.waitUntil(5000) { compose.onAllNodes(hasText("Download tafsir")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Download tafsir").performClick()
        compose.waitUntil(5000) { compose.onAllNodes(hasText("Saved commentary")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Download tafsir").assertDoesNotExist()
    }
}
