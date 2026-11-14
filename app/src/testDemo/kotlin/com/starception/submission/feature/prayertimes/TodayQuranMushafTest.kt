package com.starception.submission.feature.prayertimes

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.core.qurandatabase.Ayah
import com.starception.submission.feature.surah.MushafKeyBus
import com.starception.submission.feature.surah.MushafMiniBarBus
import com.starception.submission.feature.surah.MushafMiniBarState
import com.starception.submission.feature.surah.MushafPagerView
import com.starception.submission.feature.surah.mushafReadingFontSize
import com.starception.submission.feature.surah.tajweed.TajweedAnnotation
import com.starception.submission.feature.surah.tajweed.TajweedRule
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w800dp-h844dp-mdpi", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodayQuranMushafTest {
    @get:Rule val compose = createComposeRule()
    private val busOwner = Any()

    @Test fun paginationDoesNotReserveAnInvisibleTrailingParagraphAsAContentLine() {
        var measured: TextLayoutResult? = null
        compose.setContent {
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            measured = measurer.measure(androidx.compose.ui.text.AnnotatedString(verses.first().text + "\n"),
                style = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, lineHeight = 48.sp))
        }
        compose.runOnIdle {
            val layout = requireNotNull(measured)
            val visibleBottom = com.starception.submission.feature.surah.mushafVisibleContentBottom(layout)
            assertTrue("A trailing blank paragraph must not prevent another Quran line fitting",
                visibleBottom < layout.multiParagraph.height)
            assertEquals(layout.getLineBottom(0), visibleBottom, .01f)
        }
    }

    @Test fun embeddedTranslationsUseRealParagraphBreaksWithoutStrayInlineControls() {
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            Reader(showTranslation = true, translations = mapOf(1 to "Glorify the name of your Lord, the Most High."), height = 600)
        } }
        val layout = arabicLayout()
        val text = layout.layoutInput.text.text
        assertTrue(text.contains("Glorify the name of your Lord, the Most High."))
        assertTrue(layout.layoutInput.text.paragraphStyles.isNotEmpty())
        assertFalse(text.contains('\u2028'))
        assertFalse(text.contains('\u2068'))
        assertFalse(text.contains('\u2069'))
        val translationEnd = text.indexOf("Glorify the name of your Lord, the Most High.") +
            "Glorify the name of your Lord, the Most High.".length - 1
        val nextAyah = text.indexOf("الَّذِي")
        assertTrue("The next ayah must remain on the same rendered page", nextAyah >= 0)
        val gap = layout.getLineTop(layout.getLineForOffset(nextAyah)) -
            layout.getLineBottom(layout.getLineForOffset(translationEnd))
        assertTrue("Translation paragraphs must not insert an empty Arabic-height line: gap=$gap", gap <= 4f)
    }

    @Test fun shortPagesKeepNavigationAtTheTileBottomWithoutAnUnusedRegionBelowIt() {
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) { Reader(height = 650) } }
        val readerBottom = compose.onNodeWithTag("todayQuranMushaf").fetchSemanticsNode().boundsInRoot.bottom
        val labelTop = compose.onNodeWithTag("todayQuranMushafPageNumber").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Page controls must not float upward and leave a blank region below them",
            labelTop >= readerBottom - 48f)
    }

    @Test fun pinchingTheActualMushafSurfaceCanEnlargeItsText() {
        val fontSize = mutableStateOf(41f)
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            TodayQuranMushaf(verses, emptyMap(), "uthmani_script", fontSize.value, false, false,
                emptyMap(), "justify", "en", {}, Modifier.size(360.dp, 500.dp),
                onPinchZoom = { fontSize.value = (fontSize.value * it).coerceIn(20f, 120f) })
        } }
        compose.onNodeWithTag("todayQuranMushaf").performTouchInput {
            down(0, Offset(120f, 140f))
            down(1, Offset(220f, 140f))
            moveTo(0, Offset(80f, 140f), delayMillis = 200)
            moveTo(1, Offset(260f, 140f), delayMillis = 200)
            up(0)
            up(1)
        }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertTrue("Pinch-out must increase the reading size", fontSize.value > 50f) }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun embeddedReaderLeavesArtworkVisibleInsteadOfPaintingTheThemeSurface() {
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme(surface = Color(0xFF36170E))) {
            Box(Modifier.size(360.dp, 404.dp).background(Color.Magenta)) {
                TodayQuranMushaf(verses, emptyMap(), "uthmani_script", 41f, false, false,
                    emptyMap(), "justify", "en", {})
            }
        } }
        val output = File(System.getProperty("java.io.tmpdir"), "opencode/todays-reading-transparent-regression.png")
        output.parentFile.mkdirs()
        compose.onRoot().captureRoboImage(output.absolutePath,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        val bitmap = requireNotNull(android.graphics.BitmapFactory.decodeFile(output.absolutePath))
        assertEquals("The embedded page must not cover the artwork with a solid theme surface",
            android.graphics.Color.MAGENTA, bitmap.getPixel(180, 300))
    }
    private val verses = listOf(
        "سَبِّحِ اسْمَ رَبِّكَ الْأَعْلَىٰ",
        "الَّذِي خَلَقَ فَسَوَّىٰ",
        "وَالَّذِي قَدَّرَ فَهَدَىٰ",
    ).mapIndexed { index, text -> Ayah(index + 1, index + 1, text, index + 1, 591, 87, 87, 119, 30, false) }

    @After fun releaseTestControls() {
        MushafKeyBus.unbind()
        MushafMiniBarBus.unbind(busOwner)
    }

    @Composable private fun Reader(
        showTranslation: Boolean = false,
        translations: Map<Int, String> = emptyMap(),
        annotations: Map<Int, List<TajweedAnnotation>> = emptyMap(),
        fontSize: Float = 41f,
        height: Int = 404,
        font: String = "uthmani_script",
    ) {
        TodayQuranMushaf(verses, translations, font, fontSize, showTranslation,
            annotations.isNotEmpty(), annotations, "justify", "en", {},
            Modifier.size(360.dp, height.dp).background(Color(0xFF0B1827)))
    }

    private fun arabicLayout(index: Int = 0): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onAllNodes(hasText("سَبِّحِ", substring = true), useUnmergedTree = true)[index]
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    @Test fun embeddedReaderKeepsSurahTypographyButPacksNarrowLinesBeforeWrapping() {
        val font = mutableStateOf("uthmani_script")
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            Row(Modifier.background(Color.Black)) {
                Box(Modifier.size(360.dp, 380.dp)) {
                    MushafPagerView(ayahs = verses, arabicFont = font.value, arabicFontSize = 41f,
                        showTranslation = false, showTajweed = false, tajweedAnnotations = emptyMap(),
                        showBismillah = true, textAlignment = "justify", translationCode = "en", onAyahLongPress = {})
                }
                Reader(font = font.value)
            }
        } }
        for (selection in listOf("uthmani_script", "indopak_script", "pdms_saleem")) {
            compose.runOnIdle { font.value = selection }
            val full = arabicLayout(0)
            val embedded = arabicLayout(1)
            assertEquals(full.layoutInput.style.fontFamily, embedded.layoutInput.style.fontFamily)
            assertEquals(mushafReadingFontSize(41f).sp, embedded.layoutInput.style.fontSize)
            assertEquals(full.layoutInput.style.fontSize, embedded.layoutInput.style.fontSize)
            assertEquals(full.layoutInput.style.textAlign, embedded.layoutInput.style.textAlign)
            assertEquals(full.layoutInput.style.color, embedded.layoutInput.style.color)
            assertEquals(full.layoutInput.text.text.filterNot { it == '\u2060' || it == '\u200B' },
                embedded.layoutInput.text.text.filterNot { it == '\u2060' || it == '\u200B' })
            assertTrue("Full Surah pages keep their word-to-marker binding", full.layoutInput.text.text.contains('\u2060'))
            assertFalse("Home must not move a fitting word merely to keep its ornament attached",
                embedded.layoutInput.text.text.contains('\u2060'))
            assertEquals(androidx.compose.ui.text.style.LineBreak.Simple, embedded.layoutInput.style.lineBreak)
            assertEquals(androidx.compose.ui.text.style.LineBreak.Paragraph, full.layoutInput.style.lineBreak)
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun tajweedDoesNotSplitArabicWordsIntoSeparateColourSpans() {
        val wordStart = verses.first().text.indexOf("رَبِّكَ")
        // A synthetic partial-word mask tests rendering, not the recitation rule itself.
        val rules = mapOf(1 to listOf(TajweedAnnotation(TajweedRule.IKHFA, wordStart + 1, wordStart + 4)))
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) { Reader(annotations = rules) } }
        val text = arabicLayout().layoutInput.text
        assertTrue(text.text.contains("رَبِّكَ"))
        assertTrue("Colour spans inside Arabic words break their cursive joins", text.spanStyles.none {
            it.start < wordStart + 6 && it.end > wordStart && it.item.color != Color.Unspecified
        })
        val output = File(System.getProperty("java.io.tmpdir"), "opencode/todays-reading-mushaf.png")
        output.parentFile.mkdirs()
        compose.onRoot().captureRoboImage(output.absolutePath,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    @Test fun selectedTranslationsStayWithTheirAyahsAndBismillahIsShown() {
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            Reader(showTranslation = true, translations = mapOf(1 to "Glorify the name of your Lord, the Most High."), height = 600)
        } }
        compose.onNodeWithText("Glorify the name of your Lord, the Most High.", substring = true).assertExists()
        compose.onAllNodesWithText("بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ").assertCountEquals(1)
    }

    @Test fun homeReaderDoesNotReplaceOrClearExistingVolumeKeysAndMiniBarOwners() {
        var next = 0
        var previous = 0
        val existing = MushafMiniBarState(18, "الكهف", "Al-Kahf", 2, 8)
        MushafKeyBus.bind(next = { next++ }, prev = { previous++ })
        MushafMiniBarBus.bind(busOwner, {}, {}, {}, {})
        MushafMiniBarBus.publish(busOwner, existing)
        val visible = mutableStateOf(true)
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) { if (visible.value) Reader() } }
        compose.runOnIdle {
            assertSame(existing, MushafMiniBarBus.state.value)
            assertTrue(MushafKeyBus.handleNext())
            visible.value = false
        }
        compose.runOnIdle {
            assertTrue(MushafKeyBus.handlePrev())
            assertEquals(1, next)
            assertEquals(1, previous)
            assertSame(existing, MushafMiniBarBus.state.value)
        }
    }

    @Test fun pageControlsReadTheRestOfTheSurahInsideTheTile() {
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) { Reader(fontSize = 70f, height = 220) } }
        compose.onNodeWithContentDescription("Previous Mushaf page").assertIsNotEnabled()
        val next = compose.onNodeWithContentDescription("Next Mushaf page")
        val layout = arabicLayout()
        val filler = layout.layoutInput.text.getStringAnnotations("androidx.compose.foundation.text.inlineContent", 0,
            layout.layoutInput.text.length).firstOrNull { it.item == "mushafLineFiller" }?.start ?: layout.layoutInput.text.length
        val contentLine = layout.getLineForOffset((filler - 1).coerceAtLeast(0))
        val textBottom = compose.onAllNodes(hasText("سَبِّحِ", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot.top + layout.getLineBottom(contentLine)
        assertTrue("Page controls must sit below the Quran text", next.fetchSemanticsNode().boundsInRoot.top >= textBottom)
        next.performClick()
        val label = compose.onNodeWithTag("todayQuranMushafPageNumber").fetchSemanticsNode()
            .config[SemanticsProperties.Text].first().text
        assertTrue(label.startsWith("Page 2 of "))
        compose.onNodeWithContentDescription("Previous Mushaf page").performClick()
        val restored = compose.onNodeWithTag("todayQuranMushafPageNumber").fetchSemanticsNode()
            .config[SemanticsProperties.Text].first().text
        assertTrue(restored.startsWith("Page 1 of "))
    }
}
