package com.starception.submission.feature.surah

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.feature.quran.BengaliIbnKathir
import com.starception.submission.feature.quran.BengaliIbnKathirContent
import com.starception.submission.feature.quran.BengaliIbnKathirRepository
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h720dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BengaliIbnKathirContentTest {
    @get:Rule val compose = createComposeRule()
    private fun chapter(surah: Int, body: String) =
        """{"surah":$surah,"ayahCount":${BengaliIbnKathir.ayahCounts[surah - 1]},"passages":["$body"],"ayahPassage":[${List(BengaliIbnKathir.ayahCounts[surah - 1]) { 0 }.joinToString(",")}]}"""

    @Test fun publishedTextShowsItsEditionAndAttributionAndCanSaveTheWholeBook() {
        var reads = 0
        val body = "এই সূরাটির নাম ‘সূরা-ই-আল ফাতিহাহ্। কোন কিছু আরম্ভ করার নাম ‘ফাতিহাহ’ বা উদ্ঘাটিকা।"
        val repository = BengaliIbnKathirRepository { reads++; chapter(it, body) }
        compose.setContent { MaterialTheme { Surface { Column(Modifier.padding(20.dp)) {
            BengaliIbnKathirContent(1, 1, repository)
        } } } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(body)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(BengaliIbnKathir.TITLE).assertIsDisplayed()
        compose.onNodeWithText("বাংলা · Tawheed Publication").assertIsDisplayed()
        compose.onNodeWithText(body).assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDemo/screenshots/bengali_ibn_kathir_reader.png")
        compose.onNodeWithText("Download full tafsir for offline reading").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Full tafsir saved offline")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(114, reads)
    }
    @Test fun aFailedReadCanRetryWithoutShowingAnotherBooksText() {
        var attempts = 0
        val repository = BengaliIbnKathirRepository { if (++attempts == 1) error("offline") else chapter(it, "বাংলা তাফসীর") }
        compose.setContent { MaterialTheme { BengaliIbnKathirContent(1, 1, repository) } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Try again")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Try again").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("বাংলা তাফসীর")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("বাংলা তাফসীর").assertIsDisplayed()
        assertEquals(2, attempts)
    }
    @Test fun changingAyahCancelsTheOldReadAndDisplaysOnlyTheNewAddress() {
        val surah = mutableStateOf(1)
        val started = CompletableDeferred<Unit>(); val gate = CompletableDeferred<Unit>()
        val repository = BengaliIbnKathirRepository { number ->
            if (number == 1) { started.complete(Unit); gate.await() }
            chapter(number, if (number == 1) "পুরোনো তাফসীর" else "নতুন তাফসীর")
        }
        compose.setContent { MaterialTheme { BengaliIbnKathirContent(surah.value, 1, repository) } }
        compose.waitUntil(5_000) { started.isCompleted }
        compose.runOnIdle { surah.value = 2 }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("নতুন তাফসীর")).fetchSemanticsNodes().isNotEmpty() }
        gate.complete(Unit)
        compose.onNodeWithText("নতুন তাফসীর").assertIsDisplayed()
        compose.onNodeWithText("পুরোনো তাফসীর").assertDoesNotExist()
    }
}
