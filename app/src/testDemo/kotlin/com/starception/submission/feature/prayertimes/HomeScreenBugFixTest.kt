package com.starception.submission.feature.prayertimes

import android.app.Application
import android.graphics.BitmapFactory
import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import com.starception.submission.core.model.deenly.DeenlyNudge
import com.starception.submission.core.model.deenly.DeenlyNudgeAction
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w393dp-h852dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenBugFixTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    @Test fun sunsetOverridesTheFixedAfternoonHourRange() {
        assertEquals("Salam, evening", homeGreetingForHour(17, isDaylight = false))
        assertEquals("Salam, afternoon", homeGreetingForHour(17, isDaylight = true))
        assertEquals("Salam, morning", homeGreetingForHour(9, isDaylight = true))
    }

    @Test fun sceneLightingDoesNotStartANewAnimationEveryClockSecond() {
        val first = java.time.LocalTime.of(15, 5, 0)
        assertEquals(insightArtworkTime(first), insightArtworkTime(first.plusSeconds(59)))
        assertNotEquals(insightArtworkTime(first), insightArtworkTime(first.plusMinutes(1)))
        assertFalse(shouldAnimateInsightScene(focused = false, expanded = false, fullBleed = false))
        assertFalse(shouldAnimateInsightScene(focused = true, expanded = true, fullBleed = false))
        assertFalse(shouldAnimateInsightScene(focused = true, expanded = false, fullBleed = true))
        assertTrue(shouldAnimateInsightScene(focused = true, expanded = false, fullBleed = false))
    }

    @Test fun swipeNeverFadesBothCopiesToZeroAtTheMidpoint() {
        for (step in 0..100) {
            val position = step / 100f
            assertEquals("Crossfade must not flash an empty text frame", 1f,
                homeCoverCopyOpacity(position) + homeCoverCopyOpacity(position - 1f), .001f)
        }
    }

    @Test fun reflectionHoldsDuringTheSwipeThenFitsTheSettledBengaliPassage() {
        val position = mutableFloatStateOf(0f)
        val page = mutableIntStateOf(0)
        val short = "পরীক্ষার জন্য একটি ছোট বাংলা বাক্য।"
        val long = "পরীক্ষার জন্য বাংলা লেখা, যা ছোট অনুচ্ছেদের চেয়ে বেশি জায়গা নেয়। ".repeat(5).trim()
        fun candidate(id: Int, text: String) = NowNudgeKnowledgeCandidate(
            DeenlyNudge(id = "stable-$id", action = DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
                label = "Source", supportingText = text, sourceLabel = "Quran 94:5"),
            NowNudgeKnowledgeTarget.QuranAyah(94, 5), "source-$id", "quran", "Reading", 1f,
        )
        val candidates = mapOf(0 to candidate(0, short), 1 to candidate(1, long))
        compose.setContent {
            MaterialTheme { HomeReflectionPrompt(focusedPage = page.intValue,
                scrollPosition = position.floatValue, generatedInsights = candidates,
                modifier = Modifier.testTag("stable-reflection"), onClick = {}) }
        }
        val initialHeight = compose.onNodeWithTag("stable-reflection").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithText(long).assertDoesNotExist()
        for (next in listOf(.01f, .25f, .5f, .99f)) {
            compose.runOnIdle { position.floatValue = next }
            val height = compose.onNodeWithTag("stable-reflection").fetchSemanticsNode().boundsInRoot.height
            assertEquals("Swiping must not resize the cover through reflection height changes", initialHeight, height, .5f)
        }
        compose.runOnIdle { page.intValue = 1; position.floatValue = 1f }
        compose.waitForIdle()
        val longHeight = compose.onNodeWithTag("stable-reflection").fetchSemanticsNode().boundsInRoot.height
        assertTrue("The long passage must receive its own measured space", longHeight > initialHeight)
        for (next in listOf(.99f, .75f, .5f, .01f)) {
            compose.runOnIdle { position.floatValue = next }
            val height = compose.onNodeWithTag("stable-reflection").fetchSemanticsNode().boundsInRoot.height
            assertEquals("Do not collapse text while the finger is still moving", longHeight, height, .5f)
        }
        compose.runOnIdle { page.intValue = 0; position.floatValue = 0f }
        compose.waitForIdle()
        val fittedHeight = compose.onNodeWithTag("stable-reflection").fetchSemanticsNode().boundsInRoot.height
        assertEquals("Do not retain the long passage's empty space after returning", initialHeight, fittedHeight, .5f)
    }

    @Test fun foregroundContentBlendsIntoTheNavigationSurface() {
        val feed = Color(0xFF352C28)
        compose.setContent {
            Box(Modifier.size(width = 200.dp, height = 88.dp)
                .testTag("navigation-reveal").homeNavigationReveal(feed, 44.dp).background(Color.White))
        }
        val file = temporary.newFile("navigation-reveal.png")
        compose.onNodeWithTag("navigation-reveal").captureRoboImage(file.absolutePath,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        val x = bitmap.width / 2
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(x, 2))
        val nearBottom = bitmap.getPixel(x, bitmap.height - 2)
        assertTrue(android.graphics.Color.red(nearBottom) in 0x35..0x49)
        val blended = bitmap.getPixel(x, bitmap.height * 3 / 4)
        assertNotEquals(android.graphics.Color.WHITE, blended)
        assertNotEquals(0xff352c28.toInt(), blended)
        bitmap.recycle()
    }

    @Test fun coverShrinksToKeepReflectionAndWrappedCitationAboveNavigation() {
        assertEquals(428f, homeRestingCoverHeight(660f, 560f, 200f, 32f), .01f)
        for (reflection in listOf(148f, 180f, 240f, 300f)) {
            val height = homeRestingCoverHeight(660f, 560f, reflection, 32f)
            assertTrue("Reflection and its full source must fit in the available viewport",
                height + reflection + 32f <= 660f)
        }
        assertEquals(560f, homeRestingCoverHeight(0f, 560f, 200f, 32f), .01f)
    }

    @Test fun brightSkyIsDarkenedOnlyWhereHeaderNeedsReadableWhiteInk() {
        compose.setContent {
            Box(Modifier.size(224.dp).background(Color.White).testTag("header-contrast")) {
                Box(Modifier.fillMaxSize().background(homeHeaderArtworkScrim()))
            }
        }
        val file = temporary.newFile("header.png")
        compose.onNodeWithTag("header-contrast").captureRoboImage(file.absolutePath,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        val pixels = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        assertTrue(android.graphics.Color.red(pixels.getPixel(5, 5)) < 102)
        assertTrue(android.graphics.Color.red(pixels.getPixel(5, (pixels.height * .45f).toInt())) < 115)
        assertTrue(android.graphics.Color.red(pixels.getPixel(5, pixels.height - 2)) > 242)
        pixels.recycle()
    }

    @Test fun readingReflectionRejectsOtherSurahsAndUnrelatedDua() {
        assertTrue(homeReflectionMatchesReading(2, 99, NowNudgeKnowledgeTarget.QuranAyah(99, 4)))
        assertFalse(homeReflectionMatchesReading(2, 99, NowNudgeKnowledgeTarget.QuranAyah(18, 1)))
        assertFalse(homeReflectionMatchesReading(2, 99, NowNudgeKnowledgeTarget.Hadith("Bukhari", "bukhari.db", 1)))
    }

    @Test fun passageAndCitationAreNotCutToOneLineOrNinetySixCharacters() {
        val body = "A source-backed passage should remain fully readable rather than disappearing behind an ellipsis. " +
            "The complete passage stays available as the user scrolls the Home feed."
        val reference = "A complete source reference with its collection name, book title, narration number and citation details"
        val candidate = NowNudgeKnowledgeCandidate(
            DeenlyNudge(id = "long-source", action = DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
                label = "A grounded passage", supportingText = body, sourceLabel = reference),
            NowNudgeKnowledgeTarget.QuranAyah(99, 4), "quran-99-4", "quran", "Az-Zalzalah", 1f,
        )
        assertEquals(body, homeEvidencePreview(body))
        compose.setContent {
            MaterialTheme { HomeReflectionPrompt(focusedPage = 2, readingSurahNumber = 99,
                generatedInsights = mapOf(2 to candidate), onClick = {}) }
        }
        compose.onNodeWithText(body).assertExists()
        compose.onNodeWithText(reference).assertExists()
    }

    @Test fun readingProviderOnlyLoadsTheRequestedSurah() = runTest {
        val exact = GroundedKnowledgeSource("quran-99-4", "quran", "Quran 99:4", "Az-Zalzalah",
            "On that Day it will report its news.", NowNudgeKnowledgeTarget.QuranAyah(99, 4))
        val loader = object : GroundedKnowledgeSourceLoader {
            override suspend fun load(date: LocalDate, variation: Int): GroundedKnowledgeSource? =
                error("Daily reading must not draw from the unrelated general pool")
            override suspend fun quranSurah(surahNumber: Int): GroundedKnowledgeSource {
                assertEquals(99, surahNumber)
                return exact
            }
        }
        val provider = NowNudgeKnowledgeProvider(loader, KnowledgeDecisionGenerator { _, _ -> null })
        val candidate = provider.load(LocalDate.of(2026, 11, 23), requiredSurahNumber = 99)
        assertEquals(exact.target, candidate?.target)
        assertEquals(exact.sourceText, candidate?.nudge?.supportingText)
    }

    @Test fun bengaliSelectionReplacesCachedEnglishSourceTextOnHome() {
        val directory = temporary.newFolder("translated-home")
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = directory
        }
        val file = File(directory, "cdn_assets/databases/quran/quran_bn.db")
        file.parentFile!!.mkdirs()
        val bengali = "বাংলা উৎসের লেখা"
        SQLiteDatabase.openOrCreateDatabase(file, null).use {
            it.execSQL("CREATE TABLE surahs (id INTEGER, number INTEGER)")
            it.execSQL("CREATE TABLE ayahs (surah_id INTEGER, number_in_surah INTEGER, text TEXT)")
            it.execSQL("INSERT INTO surahs VALUES (1,99)")
            it.execSQL("INSERT INTO ayahs VALUES (1,4,?)", arrayOf(bengali))
        }
        val english = "On that Day it will report its news."
        val candidate = NowNudgeKnowledgeCandidate(
            DeenlyNudge(id = "translated-source", action = DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
                label = "A grounded passage", supportingText = english, sourceLabel = "Quran 99:4"),
            NowNudgeKnowledgeTarget.QuranAyah(99, 4), "quran-99-4", "quran", "Az-Zalzalah", 1f,
        )
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                MaterialTheme { HomeReflectionPrompt(focusedPage = 2, readingSurahNumber = 99,
                    translationLanguage = "bn", generatedInsights = mapOf(2 to candidate), onClick = {}) }
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText(bengali).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(english).assertDoesNotExist()
        compose.onNodeWithText("কুরআন ৯৯:৪").assertExists()
    }
}
