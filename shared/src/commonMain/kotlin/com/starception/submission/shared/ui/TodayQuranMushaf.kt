package com.starception.submission.shared.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.starception.submission.shared.quran.QuranVerse
import com.starception.submission.shared.quran.SharedTajweedAnnotation
import com.starception.submission.shared.quran.removeLeadingBismillah

/** Home and Surah details use the same Mushaf renderer, as on Android. */
@Composable
internal fun TodayQuranMushaf(
    surahNumber: Int,
    verses: List<QuranVerse>,
    arabicFont: String,
    arabicFontSize: Float,
    showTranslation: Boolean,
    textAlignment: String,
    tajweedAnnotations: Map<Int, List<SharedTajweedAnnotation>>,
    modifier: Modifier = Modifier,
) {
    if (verses.isEmpty()) return
    key(surahNumber) {
        var page by remember { mutableIntStateOf(1) }
        var pageCount by remember { mutableIntStateOf(1) }
        val content = remember(verses, surahNumber, tajweedAnnotations) {
            prepareHomeMushaf(surahNumber, verses, tajweedAnnotations)
        }
        // The embedded reader sits over artwork instead of an opaque page.
        val embeddedColors = MaterialTheme.colorScheme.copy(
            onSurface = Color.White,
            onSurfaceVariant = Color.White.copy(alpha = 0.8f),
        )
        Column(modifier.fillMaxSize().testTag("todayQuranMushaf")) {
            MaterialTheme(colorScheme = embeddedColors) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    MushafPagerView(
                        verses = content.verses,
                        arabicFont = arabicFont,
                        arabicFontSize = arabicFontSize,
                        showTranslation = showTranslation,
                        textAlignment = textAlignment,
                        showBismillah = surahNumber !in listOf(1, 9),
                        tajweedAnnotations = content.tajweed,
                        onPageChanged = { current, total -> page = current; pageCount = total },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
            Text("Page $page of $pageCount",
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .testTag("todayQuranMushafPageNumber"),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center)
        }
    }
}

internal data class HomeMushafContent(
    val verses: List<QuranVerse>,
    val tajweed: Map<Int, List<SharedTajweedAnnotation>>,
)

/** Bismillah is drawn separately; keep annotation ranges aligned with the remaining text. */
internal fun prepareHomeMushaf(
    surahNumber: Int,
    verses: List<QuranVerse>,
    tajweed: Map<Int, List<SharedTajweedAnnotation>>,
): HomeMushafContent {
    val first = verses.firstOrNull() ?: return HomeMushafContent(verses, tajweed)
    if (surahNumber == 1 || surahNumber == 9) return HomeMushafContent(verses, tajweed)
    val text = removeLeadingBismillah(first.arabicText)
    val removed = if (text.isEmpty()) first.arabicText.length else first.arabicText.indexOf(text)
    val annotations = tajweed[first.numberInSurah].orEmpty().mapNotNull { annotation ->
        if (removed < 0) return@mapNotNull null
        val start = (annotation.startIndex - removed).coerceIn(0, text.length)
        val end = (annotation.endIndex - removed).coerceIn(0, text.length)
        if (start < end) annotation.copy(startIndex = start, endIndex = end) else null
    }
    return HomeMushafContent(
        listOf(first.copy(arabicText = text)) + verses.drop(1),
        tajweed + (first.numberInSurah to annotations),
    )
}
