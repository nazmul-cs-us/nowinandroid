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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.feature.quran.Surah
import com.starception.submission.shared.quran.AyahNumberChip
import com.starception.submission.shared.quran.QuranArabicFonts
import com.starception.submission.shared.quran.QuranVerse

/**
 * The Mushaf page view — the shared counterpart of Android's MushafPagerView.
 *
 * Ayahs group by their Madinah-mushaf page number and render as one
 * HorizontalPager page per mushaf page: a continuous Arabic flow with inline
 * rosette-style ayah markers between verses, the surah header band on the
 * page where the surah starts, and a page/juz footer like the printed mushaf.
 */
@Composable
internal fun MushafPagerView(
    surah: Surah,
    verses: List<QuranVerse>,
    arabicFont: String,
    arabicFontSize: Float,
    showTranslation: Boolean,
    textAlignment: String,
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

    Column(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { index ->
            MushafPageView(
                surah = surah,
                page = pages[index],
                arabicFont = arabicFont,
                arabicFontSize = arabicFontSize,
                showTranslation = showTranslation,
                textAlignment = textAlignment,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val page = pages[pagerState.currentPage]
            Text(
                text = "Page ${page.page} · Juz ${page.verses.first().juz}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One mushaf page: header band on the opening page, flowing ayah text, footer. */
@Composable
private fun MushafPageView(
    surah: Surah,
    page: MushafPage,
    arabicFont: String,
    arabicFontSize: Float,
    showTranslation: Boolean,
    textAlignment: String,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        // The surah header band sits on the page where the surah begins.
        if (page.verses.first().numberInSurah == 1) {
            item(key = "header") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "SURAH ${surah.number}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = surah.nameArabic,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        item(key = "flow") {
            MushafPageFlow(
                page = page,
                arabicFont = arabicFont,
                arabicFontSize = arabicFontSize,
                showTranslation = showTranslation,
                textAlignment = textAlignment,
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
) {
    val fontFamily = QuranArabicFonts.fontFamily(arabicFont)
    val chipIdPrefix = "ayah-marker-${page.page}-"
    val annotated = remember(page, arabicFontSize, showTranslation) {
        buildAnnotatedString {
            page.verses.forEachIndexed { index, verse ->
                if (index > 0) append(" ")
                append(verse.arabicText)
                append(" ")
                pushStringAnnotation(tag = "ayah", annotation = verse.numberInSurah.toString())
                appendInlineContent(id = "$chipIdPrefix${verse.numberInSurah}", alternateText = "﴿${verse.numberInSurah}﴾")
                pop()
            }
        }
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
                lineHeight = (arabicFontSize * 1.7f).sp,
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
            if (showTranslation) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    page.verses.forEach { verse ->
                        if (verse.translation.isNotBlank()) {
                            Text(
                                text = "${verse.translation} (${verse.numberInSurah})",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Ayahs grouped onto one Madinah mushaf page. */
internal data class MushafPage(
    val page: Int,
    val verses: List<QuranVerse>,
)
