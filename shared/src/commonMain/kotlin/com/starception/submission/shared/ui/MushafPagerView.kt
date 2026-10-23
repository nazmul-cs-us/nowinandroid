/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.shared.quran.AyahNumberChip
import com.starception.submission.shared.quran.QuranArabicFonts
import com.starception.submission.shared.quran.QuranVerse
import com.starception.submission.shared.quran.QURAN_BISMILLAH
import com.starception.submission.shared.quran.SharedTajweedAnnotation
import com.starception.submission.shared.quran.SharedTajweedRules

/**
 * The Mushaf page view — the shared counterpart of Android's MushafPagerView.
 *
 * Ayahs group by their Madinah-mushaf page number and render as one
 * HorizontalPager page per mushaf page: a continuous Arabic flow with inline
 * rosette-style ayah markers between verses.
 */
@Composable
internal fun MushafPagerView(
    verses: List<QuranVerse>,
    arabicFont: String,
    arabicFontSize: Float,
    showTranslation: Boolean,
    textAlignment: String,
    showBismillah: Boolean = false,
    tajweedAnnotations: Map<Int, List<SharedTajweedAnnotation>>? = null,
    openingContent: (@Composable () -> Unit)? = null,
    requestedPage: Int = 1,
    onPageChanged: (current: Int, total: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val pages = remember(verses) {
        verses.groupBy { it.page }
            .toList()
            .sortedBy { it.first }
            .map { (page, pageVerses) -> MushafPage(page = page, verses = pageVerses) }
    }
    if (pages.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { pages.size })

    LaunchedEffect(requestedPage, pages.size) {
        val target = (requestedPage - 1).coerceIn(0, pages.lastIndex)
        if (target != pagerState.currentPage) pagerState.animateScrollToPage(target)
    }
    LaunchedEffect(pagerState.currentPage, pages.size) {
        onPageChanged(pagerState.currentPage + 1, pages.size)
    }

    Column(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { index ->
            MushafPageView(
                page = pages[index],
                arabicFont = arabicFont,
                arabicFontSize = arabicFontSize,
                showTranslation = showTranslation,
                textAlignment = textAlignment,
                showBismillah = showBismillah && index == 0,
                tajweedAnnotations = tajweedAnnotations,
                openingContent = openingContent.takeIf { index == 0 },
            )
        }
    }
}

/** One mushaf page with optional opening information and flowing ayah text. */
@Composable
private fun MushafPageView(
    page: MushafPage,
    arabicFont: String,
    arabicFontSize: Float,
    showTranslation: Boolean,
    textAlignment: String,
    showBismillah: Boolean,
    tajweedAnnotations: Map<Int, List<SharedTajweedAnnotation>>?,
    openingContent: (@Composable () -> Unit)?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (openingContent != null) {
            item(key = "album_info") { openingContent() }
        }
        if (showBismillah) {
            item(key = "bismillah") {
                Text(
                    text = QURAN_BISMILLAH,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    fontFamily = QuranArabicFonts.fontFamily(arabicFont),
                    fontSize = (arabicFontSize * 1.3f).sp,
                    lineHeight = (arabicFontSize * 1.55f).sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        item(key = "flow") {
            MushafPageFlow(
                page = page,
                arabicFont = arabicFont,
                arabicFontSize = arabicFontSize,
                showTranslation = showTranslation,
                textAlignment = textAlignment,
                tajweedAnnotations = tajweedAnnotations,
            )
        }
    }
}

/** Continuous Arabic text with inline ayah markers — the mushaf reading flow. */
@Composable
private fun MushafPageFlow(
    page: MushafPage,
    arabicFont: String,
    arabicFontSize: Float,
    showTranslation: Boolean,
    textAlignment: String,
    tajweedAnnotations: Map<Int, List<SharedTajweedAnnotation>>?,
) {
    val fontFamily = QuranArabicFonts.fontFamily(arabicFont)
    val chipIdPrefix = "ayah-marker-${page.page}-"
    val translationColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f)
    val annotated = remember(page, arabicFontSize, showTranslation, translationColor, tajweedAnnotations) {
        buildMushafFlowText(page, arabicFontSize, showTranslation, translationColor, tajweedAnnotations)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Column {
            Text(
                text = annotated,
                modifier = Modifier.fillMaxWidth(),
                fontFamily = fontFamily,
                fontSize = arabicFontSize.sp,
                lineHeight = (arabicFontSize * 1.55f).sp,
                textAlign = if (textAlignment == "center") TextAlign.Center else TextAlign.Justify,
                inlineContent = page.verses.associate { verse ->
                    "$chipIdPrefix${verse.numberInSurah}" to
                        InlineTextContent(
                            placeholder = androidx.compose.ui.text.Placeholder(
                                width = (arabicFontSize * 0.9f).sp,
                                height = (arabicFontSize * 0.9f).sp,
                                placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter,
                            ),
                            children = {
                                AyahNumberChip(
                                    ayahNumber = verse.numberInSurah,
                                    fontSize = (arabicFontSize * 0.42f).sp,
                                )
                            },
                        )
                },
            )
        }
    }
}

/** One typeset flow: Arabic, its marker, its translation, then the next ayah. */
internal fun buildMushafFlowText(
    page: MushafPage,
    arabicFontSize: Float,
    showTranslation: Boolean,
    translationColor: Color,
    tajweedAnnotations: Map<Int, List<SharedTajweedAnnotation>>? = null,
): AnnotatedString {
    val translationStyle = SpanStyle(
        fontFamily = FontFamily.Serif,
        fontSize = (arabicFontSize * 0.65f).sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        color = translationColor,
    )
    val translationParagraph = ParagraphStyle(
        textDirection = TextDirection.Content,
        textAlign = TextAlign.Start,
        lineHeight = (arabicFontSize * 0.65f * 1.28f).sp,
    )
    val separatorParagraph = ParagraphStyle(
        textDirection = TextDirection.Content,
        lineHeight = (arabicFontSize * 0.12f).sp,
    )
    val separatorStyle = SpanStyle(fontSize = (arabicFontSize * 0.12f).sp, fontFamily = FontFamily.Default)
    return buildAnnotatedString {
        page.verses.forEachIndexed { index, verse ->
            if (index > 0) append(" ")
            pushStringAnnotation("ayah", verse.numberInSurah.toString())
            val arabicStart = length
            append(verse.arabicText)
            tajweedAnnotations?.get(verse.numberInSurah).orEmpty().forEach { annotation ->
                val color = SharedTajweedRules.colors[annotation.ruleKey]
                val start = (arabicStart + annotation.startIndex).coerceIn(arabicStart, length)
                val end = (arabicStart + annotation.endIndex).coerceIn(start, length)
                if (color != null && start < end) addStyle(SpanStyle(color = Color(color)), start, end)
            }
            append('\u2060') // Keep the marker attached to its Arabic ayah.
            appendInlineContent("ayah-marker-${page.page}-${verse.numberInSurah}", "﴿${verse.numberInSurah}﴾")
            pop()
            if (showTranslation && verse.translation.isNotBlank()) {
                pushStringAnnotation("translation", verse.numberInSurah.toString())
                withStyle(separatorParagraph) { withStyle(separatorStyle) { append('\u2028') } }
                withStyle(translationParagraph) {
                    withStyle(translationStyle) {
                        append('\u2068') // Each language chooses its own direction within the RTL flow.
                        append(verse.translation.trim())
                        append('\u2069')
                    }
                }
                withStyle(separatorParagraph) { withStyle(separatorStyle) { append('\u2028') } }
                pop()
            }
        }
    }
}

/** Ayahs grouped onto one Madinah mushaf page. */
internal data class MushafPage(
    val page: Int,
    val verses: List<QuranVerse>,
)
