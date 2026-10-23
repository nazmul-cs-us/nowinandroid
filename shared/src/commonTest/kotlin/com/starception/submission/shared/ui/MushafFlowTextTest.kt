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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.starception.submission.shared.quran.QuranVerse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MushafFlowTextTest {
    private val page = MushafPage(1, listOf(
        QuranVerse(1, 1, 1, "الْحَمْدُ لِلَّهِ", 1, 1, "All praise belongs to Allah."),
        QuranVerse(2, 1, 2, "الرَّحْمَٰنِ الرَّحِيمِ", 1, 1, "The Most Merciful."),
    ))

    @Test
    fun translationFollowsItsArabicAndMarkerBeforeTheNextAyah() {
        val flow = buildMushafFlowText(page, 30f, true, Color.Gray)
        val text = flow.text
        assertTrue(text.indexOf("الْحَمْدُ") < text.indexOf("﴿1﴾"))
        assertTrue(text.indexOf("﴿1﴾") < text.indexOf("All praise"))
        assertTrue(text.indexOf("All praise") < text.indexOf("الرَّحْمَٰنِ"))
        assertTrue(text.indexOf("﴿2﴾") < text.indexOf("The Most Merciful"))
        assertEquals(listOf("1", "2"), flow.getStringAnnotations("translation", 0, flow.length).map { it.item })
        assertTrue(flow.spanStyles.any { it.item.fontFamily == FontFamily.Serif && it.item.fontSize.value == 19.5f })
    }

    @Test
    fun translationOffPreservesTheContinuousArabicFlow() {
        val flow = buildMushafFlowText(page, 30f, false, Color.Gray)
        assertFalse(flow.text.contains("All praise"))
        assertTrue(flow.text.contains("﴿1﴾"))
        assertTrue(flow.text.contains("﴿2﴾"))
        assertTrue(flow.getStringAnnotations("translation", 0, flow.length).isEmpty())
    }

    @Test
    fun emptyTranslationsDoNotIntroduceBlankTranslationParagraphs() {
        val noTranslation = page.copy(verses = page.verses.map { it.copy(translation = " ") })
        val flow = buildMushafFlowText(noTranslation, 30f, true, Color.Gray)
        assertFalse(flow.text.contains('\u2028'))
        assertFalse(flow.text.contains('\u2068'))
    }
}
