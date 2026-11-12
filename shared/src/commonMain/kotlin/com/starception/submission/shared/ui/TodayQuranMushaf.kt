package com.starception.submission.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
        Column(modifier.fillMaxSize().testTag("todayQuranMushaf")) {
            MushafPagerView(
                verses = content.verses,
                arabicFont = arabicFont,
                arabicFontSize = arabicFontSize,
                showTranslation = showTranslation,
                textAlignment = textAlignment,
                showBismillah = surahNumber !in listOf(1, 9),
                tajweedAnnotations = content.tajweed,
                requestedPage = page,
                onPageChanged = { current, total -> page = current; pageCount = total },
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface),
            )
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { page-- }, enabled = page > 1,
                    modifier = Modifier.semantics { contentDescription = "Previous Mushaf page" }) {
                    Text("Previous", color = Color.White.copy(alpha = if (page > 1) 1f else .38f))
                }
                Text("Page $page of $pageCount", modifier = Modifier.testTag("todayQuranMushafPageNumber"),
                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .85f), textAlign = TextAlign.Center)
                TextButton(onClick = { page++ }, enabled = page < pageCount,
                    modifier = Modifier.semantics { contentDescription = "Next Mushaf page" }) {
                    Text("Next", color = Color.White.copy(alpha = if (page < pageCount) 1f else .38f))
                }
            }
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
